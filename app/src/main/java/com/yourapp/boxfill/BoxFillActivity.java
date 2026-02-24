package com.yourapp.boxfill;

import android.annotation.SuppressLint;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.yourapp.sync.SyncWorker;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;
import com.yourapp.VarietyAdapter;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class BoxFillActivity extends AppCompatActivity {

    private static final String HONEYWELL_ACTION = "com.honeywell.scan.RESULT";

    private Spinner spinnerFarm, spinnerSfk, spinnerSleeve, spinnerBoxType;
    private AutoCompleteTextView varietyInput;
    private EditText etBunches, etStems, etScanSink;
    private Button btnSave;
    private RecyclerView rv;

    private int selectedFarmId = 0;
    private int selectedVarietyId = 0;

    private final List<BoxFillItem> scannedBoxes = new ArrayList<>();
    private BoxFillAdapter adapter;
    private FlowerDbHelper dbHelper;

    private BroadcastReceiver honeywellReceiver;

    private long lastScanTs = 0;

    private final Set<Long> scannedBoxIds = new HashSet<>();


    // =====================================================
    // LIFECYCLE
    // =====================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_box_fill);

        dbHelper = new FlowerDbHelper(this);

        spinnerFarm    = findViewById(R.id.spinnerFarm);
        spinnerSfk     = findViewById(R.id.spinnerSFK);
        spinnerSleeve  = findViewById(R.id.spinnerSleeve);
        spinnerBoxType = findViewById(R.id.spinnerBoxType);
        varietyInput   = findViewById(R.id.varietyInput);
        etBunches      = findViewById(R.id.etBunches);
        etStems        = findViewById(R.id.etStems);
        etScanSink     = findViewById(R.id.etScanSink);
        btnSave        = findViewById(R.id.btnSaveBox);
        rv             = findViewById(R.id.rvScannedBoxes);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BoxFillAdapter(scannedBoxes);
        rv.setAdapter(adapter);


        Button btnSyncNow = findViewById(R.id.btnSyncNow);

        btnSyncNow.setOnClickListener(v -> triggerManualSync());


        setupStaticSpinners();
        setupVarietyAutocomplete();
        loadFarmsOffline();
        setupHoneywellScanner();
        setupScanSink();   // ⭐ IMPORTANT

        varietyInput.setEnabled(false);

        btnSave.setOnClickListener(v -> saveBoxesOffline());

//        findViewById(R.id.btnScanBox).setOnClickListener(v -> {
//            etScanSink.setText("");
//            etScanSink.requestFocus();
//            Toast.makeText(this, "Scan box now", Toast.LENGTH_SHORT).show();
//        });

        findViewById(R.id.btnScanBox).setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(BoxFillActivity.this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan Box QR");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.initiateScan();
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {

            String qr = result.getContents().trim();

            // Feed into same pipeline as Honeywell
            etScanSink.setText(qr);
            handleScan(qr);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerHoneywell();
        etScanSink.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterHoneywell();
    }

    private void triggerManualSync() {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest =
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueue(syncRequest);

        Toast.makeText(
                this,
                "Sync started… will run in background",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =====================================================
    // SCAN SINK — KEYBOARD WEDGE (COPIED MODEL)
    // =====================================================
    private void setupScanSink() {

        // Disable soft keyboard
        etScanSink.setShowSoftInputOnFocus(false);

        // Listen for manual typing

        etScanSink.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter =
                    actionId == EditorInfo.IME_ACTION_DONE ||
                            (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);

            if (!isEnter) return false;

            String scan = etScanSink.getText().toString().trim();
            etScanSink.setText("");

            if (!scan.isEmpty()) handleScan(scan);
            return true;
        });


        // Optional: trigger on any text change (real-time)
        etScanSink.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String scan = s.toString().trim();
                if (!scan.isEmpty()) {
                    etScanSink.setText("");  // clear immediately
                    handleScan(scan);
                }
            }
        });
    }



    // =====================================================
    // QR SCAN HANDLING (UNCHANGED)
    // =====================================================
    private void handleScan(String raw) {

        if (raw == null || raw.isEmpty()) {
            feedback(false);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTs < 300) return; // time debounce
        lastScanTs = now;

        try {
            JSONObject obj = new JSONObject(raw);

            if (!obj.has("b")) {
                feedback(false);
                Toast.makeText(this, "QR missing box ID", Toast.LENGTH_SHORT).show();
                return;
            }

            long boxId = obj.getLong("b");

            // HARD debounce per box
            if (scannedBoxIds.contains(boxId)) {
                feedback(false);
                Toast.makeText(this, "Box already scanned", Toast.LENGTH_SHORT).show();
                return;
            }


            scannedBoxIds.add(boxId);
            scannedBoxes.add(new BoxFillItem(boxId, raw));

            adapter.notifyItemInserted(scannedBoxes.size() - 1);
            rv.scrollToPosition(scannedBoxes.size() - 1);

            feedback(true);
            moveFocusAfterScan();


        } catch (Exception e) {
            feedback(false);
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
        }
    }

    // =====================================================
    // FARMS (OFFLINE)
    // =====================================================
    private void loadFarmsOffline() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id,name FROM farms_local ORDER BY name", null);

        List<String> names = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();

        names.add("-- Select Farm --");
        ids.add(0);

        while (c.moveToNext()) {
            ids.add(c.getInt(0));
            names.add(c.getString(1));
        }
        c.close();

        spinnerFarm.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names
        ));

        spinnerFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedFarmId = ids.get(pos);
                selectedVarietyId = 0;
                varietyInput.setText("");
                varietyInput.setEnabled(pos > 0);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // =====================================================
    // VARIETIES (OFFLINE)
    // =====================================================
    private void setupVarietyAutocomplete() {

        varietyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (s.length() >= 1) fetchVarietiesOffline(s.toString());
            }
        });

        varietyInput.setOnItemClickListener((parent, view, position, id) -> {
            String name = (String) parent.getItemAtPosition(position);
            selectedVarietyId = resolveVarietyIdByName(name);
        });
    }


    private void fetchVarietiesOffline(String term) {
        if (selectedFarmId == 0) return;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT name FROM varieties_local WHERE farm_id=? AND name LIKE ?",
                new String[]{String.valueOf(selectedFarmId), "%" + term + "%"}
        );

        List<String> names = new ArrayList<>();
        while (c.moveToNext()) names.add(c.getString(0));
        c.close();

        if (names.isEmpty()) return;

        VarietyAdapter adapter = new VarietyAdapter(this, names);
        varietyInput.setAdapter(adapter);

        // ✅ Only show dropdown if more than one OR first load
        if (!varietyInput.isPopupShowing()) {
            varietyInput.showDropDown();
        }
    }


    private int resolveVarietyIdByName(String name) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id FROM varieties_local WHERE name=? AND farm_id=? LIMIT 1",
                new String[]{name, String.valueOf(selectedFarmId)}
        );

        int id = 0;
        if (c.moveToFirst()) id = c.getInt(0);
        c.close();
        return id;
    }

    // =====================================================
    // SAVE OFFLINE
    // =====================================================
    private void saveBoxesOffline() {

        if (scannedBoxes.isEmpty()) {
            Toast.makeText(this, "No boxes scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFarmId == 0 || selectedVarietyId == 0) {
            Toast.makeText(this, "Select farm and variety", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etBunches.getText().toString().isEmpty() ||
                etStems.getText().toString().isEmpty()) {
            Toast.makeText(this, "Enter bunches and stems", Toast.LENGTH_SHORT).show();
            return;
        }

        int bunches = Integer.parseInt(etBunches.getText().toString());
        int stems   = Integer.parseInt(etStems.getText().toString());

        String sfk     = spinnerSfk.getSelectedItem().toString();
        String sleeve  = spinnerSleeve.getSelectedItem().toString();
        String boxType = spinnerBoxType.getSelectedItem().toString();

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        db.beginTransaction();
        try {
            for (BoxFillItem item : scannedBoxes) {

                db.execSQL(
                        "INSERT OR REPLACE INTO boxes_local " +
                                "(id, qr_code, farm_id, status, synced) VALUES (?,?,?,?,0)",
                        new Object[]{item.boxId, item.qrRaw, selectedFarmId, "FILLED"}
                );


                db.execSQL(
                        "INSERT OR REPLACE INTO box_contents_local " +
                                "(box_id, variety_id, bunches, stems_per_bunch, sfk, sleeve, box_type, updated_at, synced) " +
                                "VALUES (?,?,?,?,?,?,?,?,0)"
                        ,
                        new Object[]{
                                item.boxId,
                                selectedVarietyId,
                                bunches,
                                stems,
                                sfk,
                                sleeve,
                                boxType,
                                now
                        }
                );
            }

            db.setTransactionSuccessful();

//            scannedBoxes.clear();
//            adapter.notifyDataSetChanged();
//            etBunches.setText("");
//            etStems.setText("");
//            etScanSink.requestFocus();

            scannedBoxes.clear();
            scannedBoxIds.clear();
            adapter.notifyDataSetChanged();
            etBunches.setText("");
            etStems.setText("");
            etScanSink.requestFocus();


            Toast.makeText(this, "Saved offline ✔", Toast.LENGTH_LONG).show();

        } finally {
            db.endTransaction();
        }
    }

    private void moveFocusAfterScan() {

        if (etBunches.getText().toString().isEmpty()) {
            etBunches.requestFocus();
            return;
        }

        if (etStems.getText().toString().isEmpty()) {
            etStems.requestFocus();
            return;
        }

        // If both are filled, go back to scan
        etScanSink.requestFocus();
    }


    // =====================================================
    // STATIC SPINNERS
    // =====================================================
    private void setupStaticSpinners() {

        spinnerSfk.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"WHITE", "BLACK"}));

        spinnerSleeve.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"BELLISSIMA", "FALL", "SLEEVELESS", "CLEAR"}));

        spinnerBoxType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "BELLISSIMA ZIM",
                        "BELLISSIMA COLOMBIA",
                        "BELLISSIMA QTR"
                }));
    }

    // =====================================================
    // FEEDBACK
    // =====================================================
    private void feedback(boolean ok) {
        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        tg.startTone(ok ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_NACK, 120);
    }

    // =====================================================
    // HONEYWELL INTENT
    // =====================================================
    private void setupHoneywellScanner() {
        honeywellReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String qrData = null;

                    // Common Honeywell extras
                    String[] keys = {
                            "data_string", "data", "SCAN_DATA", "barcode", "decoded_string",
                            Intent.EXTRA_TEXT, "com.honeywell.decode.data"
                    };

                    for (String key : keys) {
                        if (intent.hasExtra(key)) {
                            Object val = intent.getExtras().get(key);
                            if (val instanceof String) {
                                qrData = ((String) val).trim();
                            } else if (val instanceof byte[]) {
                                qrData = new String((byte[]) val).trim();
                            } else if (val != null) {
                                qrData = val.toString().trim();
                            }
                            if (qrData != null && !qrData.isEmpty()) break;
                        }
                    }

                    // Fallback: inspect all extras
                    if (qrData == null) {
                        Bundle extras = intent.getExtras();
                        if (extras != null) {
                            for (String key : extras.keySet()) {
                                Object val = extras.get(key);
                                if (val == null) continue;
                                if (val instanceof String && !((String) val).trim().isEmpty()) {
                                    qrData = ((String) val).trim();
                                    break;
                                } else if (val instanceof byte[] && ((byte[]) val).length > 0) {
                                    qrData = new String((byte[]) val).trim();
                                    break;
                                } else if (!val.toString().trim().isEmpty()) {
                                    qrData = val.toString().trim();
                                    break;
                                }
                            }
                        }
                    }

                    if (qrData != null && !qrData.isEmpty()) {
                        qrData = qrData.replaceAll("[\\r\\n]", "").trim();

                        // Feed the scan into EditText (optional, for UI)
                        etScanSink.setText(qrData);

                        // Immediately trigger scan handling
                        handleScan(qrData);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerHoneywell() {
        IntentFilter f = new IntentFilter(HONEYWELL_ACTION);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(honeywellReceiver, f, Context.RECEIVER_EXPORTED);
        else
            registerReceiver(honeywellReceiver, f);
    }

    private void unregisterHoneywell() {
        try { unregisterReceiver(honeywellReceiver); }
        catch (Exception ignored) {}
    }
}

