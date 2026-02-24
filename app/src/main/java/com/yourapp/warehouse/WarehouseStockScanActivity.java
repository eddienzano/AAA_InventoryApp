package com.yourapp.warehouse;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.yourapp.R;
import com.yourapp.boxfill.FlowerDbHelper;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class WarehouseStockScanActivity extends AppCompatActivity {

    private EditText etScanQr;
    private Spinner spinnerFarm;
    private TextView txtCount, tvRecent;
    private Button btnSync, btnCamera;
    private RecyclerView rvScans;

    private FlowerDbHelper db;
    private final List<String> scannedQrs = new ArrayList<>();
    private WarehouseScanAdapter adapter;

    private int selectedFarmId = 0;
    private final Deque<String> recentDeque = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Honeywell detection
    private long lastCharTime = 0;
    private StringBuilder scanBuffer = new StringBuilder();
    private String lastScannedValue = "";
    private long lastScannedAt = 0L;
    private static final long DOUBLE_SCAN_LOCK_MS = 2000; // 2s
    private static final long SCAN_THRESHOLD = 40;        // ms per char burst

    // Tone + vibration
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
    private Vibrator vibrator;

    // Server URL
    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/sync/warehouse_stock.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_stock_scan);

        etScanQr = findViewById(R.id.etScanQr);
        spinnerFarm = findViewById(R.id.spinnerFarm);
        txtCount = findViewById(R.id.txtCount);
        tvRecent = findViewById(R.id.tvRecent);
        btnSync = findViewById(R.id.btnSync);
        btnCamera = findViewById(R.id.btnCameraScan);
        rvScans = findViewById(R.id.rvScans);

        db = new FlowerDbHelper(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        setupRecycler();
        loadFarmsOffline();
        setupScannerWatcher();

        btnCamera.setOnClickListener(v -> startCameraScan());
        btnSync.setOnClickListener(v -> syncToServer());
    }

    // -------------------------------
    // Recycler
    // -------------------------------
    private void setupRecycler() {
        adapter = new WarehouseScanAdapter(scannedQrs);
        rvScans.setLayoutManager(new LinearLayoutManager(this));
        rvScans.setAdapter(adapter);
    }

    // -------------------------------
    // Load farms offline
    // -------------------------------
    private void loadFarmsOffline() {
        List<String> names = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        names.add("-- Select Farm --");
        ids.add(0);

        var rdb = db.getReadableDatabase();
        var c = rdb.rawQuery("SELECT id,name FROM farms_local ORDER BY name", null);
        while (c.moveToNext()) {
            ids.add(c.getInt(0));
            names.add(c.getString(1));
        }
        c.close();

        spinnerFarm.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        spinnerFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedFarmId = ids.get(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // -------------------------------
    // Honeywell scanner watcher
    // -------------------------------
    private void setupScannerWatcher() {
        etScanQr.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                long now = System.currentTimeMillis();
                String text = s.toString();

                if (now - lastCharTime < SCAN_THRESHOLD) {
                    scanBuffer.append(text);
                } else {
                    scanBuffer.setLength(0);
                    scanBuffer.append(text);
                }
                lastCharTime = now;

                // If Enter pressed
                if (text.endsWith("\n") || text.endsWith("\r")) {
                    String cleaned = text.trim();
                    etScanQr.setText("");
                    scanBuffer.setLength(0);
                    if (!cleaned.isEmpty()) submitIfAllowed(cleaned);
                    return;
                }

                // Auto-detect burst completion
                if (scanBuffer.length() >= 6) {
                    mainHandler.removeCallbacks(bufferFinish);
                    mainHandler.postDelayed(bufferFinish, 80);
                }

                if (text.length() > 128) { // safety
                    etScanQr.setText("");
                    scanBuffer.setLength(0);
                }
            }
        });
    }

    private final Runnable bufferFinish = () -> {
        String s = scanBuffer.toString().trim();
        scanBuffer.setLength(0);
        etScanQr.setText("");
        if (!s.isEmpty()) submitIfAllowed(s);
    };

    // -------------------------------
    // Prevent duplicates
    // -------------------------------
    private void submitIfAllowed(String qr) {
        long now = System.currentTimeMillis();
        if (qr.equals(lastScannedValue) && (now - lastScannedAt) < DOUBLE_SCAN_LOCK_MS) {
            showToast("Duplicate scan ignored");
            return;
        }
        lastScannedValue = qr;
        lastScannedAt = now;
        doScan(qr);
    }

    // -------------------------------
    // Add recent
    // -------------------------------
    private void addRecent(String qr, String status) {
        runOnUiThread(() -> {
            String line = qr + " — " + status;
            if (recentDeque.size() >= 10) recentDeque.removeLast();
            recentDeque.addFirst(line);

            StringBuilder sb = new StringBuilder();
            for (String s : recentDeque) sb.append(s).append("\n\n");
            tvRecent.setText(sb.toString().trim());
        });
    }

    // -------------------------------
    // Camera scan
    // -------------------------------
    private void startCameraScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan QR Code");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String content = result.getContents();
            if (content != null) submitIfAllowed(content.trim());
            else showToast("Scan cancelled");
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // -------------------------------
    // Main scan logic
    // -------------------------------
    private void doScan(String qr) {
        addRecent(qr, "saved");
        saveOffline(qr);
    }

    private void saveOffline(String qr) {
        if (selectedFarmId == 0) {
            showToast("Select farm first");
            return;
        }
        if (scannedQrs.contains(qr)) {
            showToast("Already scanned");
            return;
        }

        try {
            var wdb = db.getWritableDatabase();
            wdb.execSQL("INSERT INTO warehouse_scans_local (local_session_id, qr_code, scanned_at) VALUES (?,?,?)",
                    new Object[]{selectedFarmId, qr, now()});
        } catch (Exception e) {
            showToast("Duplicate blocked");
            return;
        }

        scannedQrs.add(0, qr);
        adapter.notifyItemInserted(0);
        txtCount.setText("Scanned: " + scannedQrs.size());

        // optional: vibrate & beep
        vibrator.vibrate(50);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP);
    }

    // -------------------------------
    // Sync logic
    // -------------------------------
    private void syncToServer() {
        new Thread(() -> {
            try {
                var rdb = db.getReadableDatabase();
                var c = rdb.rawQuery("SELECT local_session_id, qr_code, scanned_at FROM warehouse_scans_local WHERE synced=0", null);

                if (!c.moveToFirst()) {
                    c.close();
                    runOnUiThread(() -> showToast("No new scans to sync"));
                    return;
                }

                int farmId = 0;
                String scanDate = now().substring(0,10);
                String startedAt = now();
                String endedAt = now();
                var scansArray = new org.json.JSONArray();

                do {
                    farmId = c.getInt(0);
                    JSONObject scanObj = new JSONObject();
                    scanObj.put("qr_code", c.getString(1));
                    scanObj.put("scanned_at", c.getString(2));
                    scansArray.put(scanObj);
                } while (c.moveToNext());
                c.close();

                JSONObject payload = new JSONObject();
                payload.put("farm_id", farmId);
                payload.put("scan_date", scanDate);
                payload.put("started_at", startedAt);
                payload.put("ended_at", endedAt);
                payload.put("scans", scansArray);

                URL url = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json");

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                int respCode = conn.getResponseCode();
                conn.disconnect();

                if (respCode == 200) {
                    var wdb = db.getWritableDatabase();
                    wdb.execSQL("UPDATE warehouse_scans_local SET synced=1 WHERE synced=0");
                    runOnUiThread(() -> showToast("Sync successful"));
                } else runOnUiThread(() -> showToast("Sync failed: "+respCode));

            } catch (Exception e) {
                runOnUiThread(() -> showToast("Sync error: "+e.getMessage()));
            }
        }).start();
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private void showToast(String msg) {
        mainHandler.post(() -> {
            Toast t = Toast.makeText(WarehouseStockScanActivity.this, msg, Toast.LENGTH_SHORT);
            t.setGravity(Gravity.CENTER, 0, 0);
            t.show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tone.release();
    }
}
