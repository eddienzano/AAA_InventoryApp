package com.yourapp.boxfill;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.yourapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BoxConfirmActivity extends AppCompatActivity {

    private static final String SYNC_URL =
            "https://www.aaagrowers.co.ke/inventory/sync/boxfills_pull.php";

    private TextView tvResult;
    private FlowerDbHelper dbHelper;
    private ToneGenerator tone;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText etScannerInput;
    private final Handler handler = new Handler();
    private final Runnable processScanRunnable = () -> {
        String code = etScannerInput.getText().toString().trim();
        if (!code.isEmpty()) {
            handleScan(code);
            etScannerInput.setText("");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_box_confirm);

        tvResult = findViewById(R.id.tvResult);
        dbHelper = new FlowerDbHelper(this);
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        findViewById(R.id.btnScanConfirm).setOnClickListener(v -> startCameraScan());

        // Auto sync on load
        startBackgroundSync();

        // Honeywell scanner support
        etScannerInput = findViewById(R.id.etScannerInput);
        etScannerInput.requestFocus();

        // EditorAction for Enter key
        etScannerInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                handler.removeCallbacks(processScanRunnable);
                processScanRunnable.run();
                return true;
            }
            return false;
        });

        // TextWatcher for Honeywell keyboard wedge
        etScannerInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(processScanRunnable);
                if (s.length() > 6) { // adjust depending on QR length
                    handler.postDelayed(processScanRunnable, 300);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // ----------------------------
    // INTERNET CHECK
    // ----------------------------
    private boolean hasInternet() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }

    // ----------------------------
    // BACKGROUND SYNC
    // ----------------------------
    private void startBackgroundSync() {
        if (!hasInternet()) {
            toast("Offline mode");
            return;
        }

        executor.execute(() -> {
            int syncedCount = 0; // counter
            try {
                SharedPreferences prefs = getSharedPreferences("sync", MODE_PRIVATE);
                String lastSync = prefs.getString("box_sync_time", "1970-01-01 00:00:00");
                String urlStr = SYNC_URL + "?since=" + URLEncoder.encode(lastSync, "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    toast("Error syncing boxes: HTTP " + conn.getResponseCode());
                    return;
                }

                InputStream is = conn.getInputStream();
                Scanner sc = new Scanner(is).useDelimiter("\\A");
                String json = sc.hasNext() ? sc.next() : "";

                JSONObject obj = new JSONObject(json);
                JSONArray records = obj.getJSONArray("records");
                String serverTime = obj.getString("server_time");

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();

                for (int i = 0; i < records.length(); i++) {
                    JSONObject r = records.getJSONObject(i);
                    String qr = r.getString("qr_code");
                    int farmId = r.getInt("farm_id");

                    db.execSQL("INSERT OR IGNORE INTO boxes_local (qr_code,farm_id,status) VALUES (?,?, 'FILLED')",
                            new Object[]{qr, farmId});

                    Cursor c = db.rawQuery("SELECT id FROM boxes_local WHERE qr_code=?", new String[]{qr});
                    if (!c.moveToFirst()) { c.close(); continue; }
                    long boxId = c.getLong(0);
                    c.close();

                    db.execSQL(
                            "INSERT OR REPLACE INTO box_contents_local " +
                                    "(box_id,variety_id,bunches,stems_per_bunch,sfk,sleeve,box_type,updated_at) " +
                                    "VALUES (?,?,?,?,?,?,?,?)",
                            new Object[]{
                                    boxId,
                                    r.getInt("variety_id"),
                                    r.getInt("bunches"),
                                    r.getInt("stems_per_bunch"),
                                    r.optString("sfk"),
                                    r.optString("sleeve"),
                                    r.optString("box_type"),
                                    r.getString("updated_at")
                            }
                    );

                    syncedCount++; // increment counter
                }

                db.setTransactionSuccessful();
                db.endTransaction();
                db.close();

                // save sync time
                prefs.edit().putString("box_sync_time", serverTime).apply();

                // show toast with number of records synced
                toast(syncedCount + " record" + (syncedCount == 1 ? "" : "s") + " synced ✓");

            } catch (Exception e) {
                e.printStackTrace();
                toast("Error syncing boxes");
            }
        });
    }

    // ----------------------------
    // ZXING CAMERA SCAN
    // ----------------------------
    private void startCameraScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan Box");
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            handleScan(result.getContents().trim());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // ----------------------------
    // HANDLE QR
    // ----------------------------
    private void handleScan(String raw) {
        // Clear the previous result immediately
        tvResult.setText("");

        try {
            JSONObject obj = new JSONObject(raw);
            if (!obj.has("b")) {
                fail("Invalid QR");
                return;
            }
            long boxId = obj.getLong("b");
            loadBoxDetails(boxId);
        } catch (Exception e) {
            fail("Invalid QR format");
        }
    }

    // ----------------------------
    // LOCAL LOOKUP ONLY
    // ----------------------------
    private void loadBoxDetails(long boxId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT b.id,b.status,f.name,v.name,bc.bunches,bc.stems_per_bunch," +
                        "bc.sfk,bc.sleeve,bc.box_type,bc.updated_at " +
                        "FROM boxes_local b " +
                        "LEFT JOIN box_contents_local bc ON bc.box_id=b.id " +
                        "LEFT JOIN farms_local f ON f.id=b.farm_id " +
                        "LEFT JOIN varieties_local v ON v.id=bc.variety_id " +
                        "WHERE b.id=?",
                new String[]{String.valueOf(boxId)}
        );

        if (!c.moveToFirst()) { c.close(); fail("Box not found locally"); return; }

        String result =
                "BOX ID: " + c.getLong(0) + "\n\n" +
                        "Status: " + c.getString(1) + "\n" +
                        "Farm: " + c.getString(2) + "\n\n" +
                        "Variety: " + c.getString(3) + "\n" +
                        "Bunches: " + c.getInt(4) + "\n" +
                        "Stems/Bunch: " + c.getInt(5) + "\n" +
                        "SFK: " + c.getString(6) + "\n" +
                        "Sleeve: " + c.getString(7) + "\n" +
                        "Box Type: " + c.getString(8) + "\n\n" +
                        "Updated: " + c.getString(9);

        c.close();
        success(result);
    }

    // ----------------------------
    // TONE & TOAST
    // ----------------------------
    private void success(String txt) {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        tvResult.setText(txt);
    }

    private void fail(String msg) {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 150);
        tvResult.setText(msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }
}