package com.yourapp;

import android.annotation.SuppressLint;
import android.content.*;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

import android.view.inputmethod.InputMethodManager;

import android.os.Vibrator;

public class ScanToWipActivity extends AppCompatActivity {

    private Spinner farmSpinner;
    private AutoCompleteTextView buncherInput, varietyInput;
    private EditText qrInput;
    private LinearLayout qrListLayout;
    private Button submitBtn, scanBtn;

    private ArrayList<QrItem> qrItems = new ArrayList<>();
    private String selectedFarmName = "";
    private int selectedFarmId = 0;
    private int selectedBuncherId = 0;
    private int selectedVarietyId = 0;

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private BroadcastReceiver honeywellScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_to_wip);

        farmSpinner = findViewById(R.id.farmSpinner);
        buncherInput = findViewById(R.id.buncherInput);
        varietyInput = findViewById(R.id.varietyInput);
        qrInput = findViewById(R.id.qrInput);
        qrListLayout = findViewById(R.id.qrListLayout);
        submitBtn = findViewById(R.id.submitBtn);
        scanBtn = findViewById(R.id.scanBtn);

        qrInput.setEnabled(false);
        buncherInput.setEnabled(false);
        varietyInput.setEnabled(false);
        submitBtn.setEnabled(false);

        loadFarms();
        setupListeners();
        setupHoneywellScanner();
    }

    // ---------------- CAMERA SCANNER ----------------
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScannedCode(result.getContents().trim());
                }
            });

    private void startCameraScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan WIP QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    // ---------------- HONEYWELL SCANNER ----------------
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupHoneywellScanner() {
        // --- 1️⃣  Broadcast receiver (for devices sending intents)
        honeywellScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String barcodeData = null;

                    if (intent.hasExtra("data"))
                        barcodeData = intent.getStringExtra("data");
                    if (barcodeData == null && intent.hasExtra("barcode"))
                        barcodeData = intent.getStringExtra("barcode");
                    if (barcodeData == null && intent.hasExtra("decoded_string"))
                        barcodeData = intent.getStringExtra("decoded_string");
                    if (barcodeData == null && intent.hasExtra(Intent.EXTRA_TEXT))
                        barcodeData = intent.getStringExtra(Intent.EXTRA_TEXT);

                    if (barcodeData == null) {
                        Bundle extras = intent.getExtras();
                        if (extras != null) {
                            for (String key : extras.keySet()) {
                                Object val = extras.get(key);
                                if (val != null) {
                                    String s = val.toString().trim();
                                    if (!s.isEmpty()) {
                                        barcodeData = s;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (barcodeData != null) {
                        String clean = barcodeData.replaceAll("[\\r\\n]", "").trim();
                        if (!clean.isEmpty()) {
                            runOnUiThread(() -> {
                                handleScannedCode(clean);
                                qrInput.setText("");
                                qrInput.requestFocus();
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        IntentFilter honeyFilter = new IntentFilter();
        honeyFilter.addAction("com.honeywell.scan.RESULT");
        honeyFilter.addAction("com.honeywell.aidc.action.SCAN");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(honeywellScanReceiver, honeyFilter, Context.RECEIVER_EXPORTED);
        else
            registerReceiver(honeywellScanReceiver, honeyFilter);

        // --- 2️⃣  TextWatcher (for keyboard wedge mode)
        qrInput.addTextChangedListener(new TextWatcher() {
            private Timer timer = new Timer();
            private static final long DELAY = 300; // wait 300ms after last char

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) return;
                timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            String qr = qrInput.getText().toString().trim();
                            if (!qr.isEmpty()) {
                                handleScannedCode(qr);
                                qrInput.setText("");
                            }
                        });
                    }
                }, DELAY);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(honeywellScanReceiver); } catch (Exception ignored) {}
    }

    // ---------------- LISTENERS ----------------
    private void setupListeners() {
        farmSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos > 0) {
                    selectedFarmName = parent.getItemAtPosition(pos).toString();
                    selectedFarmId = pos;
                    buncherInput.setEnabled(true);
                    varietyInput.setEnabled(true);
                    setupAutocomplete();
                } else {
                    selectedFarmName = "";
                    selectedFarmId = 0;
                    buncherInput.setEnabled(false);
                    varietyInput.setEnabled(false);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        scanBtn.setOnClickListener(v -> startCameraScanner());

        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String qr = qrInput.getText().toString().trim();
                if (!qr.isEmpty()) handleScannedCode(qr);
                qrInput.setText("");
                return true;
            }
            return false;
        });

        submitBtn.setOnClickListener(v -> validateBeforeSave());

    }

    // ---------------- ADDING QRs ----------------
    private void handleScannedCode(String qr) {
        if (!qr.isEmpty() && !containsQR(qr)) {
            qrItems.add(new QrItem(qr, 10));
            addQrItemView(qr);
            submitBtn.setEnabled(true);
            Toast.makeText(this, "✅ QR Scanned: " + qr, Toast.LENGTH_SHORT).show();

            giveScanFeedback(true); // ✅ success beep + short vibration
        } else {
            Toast.makeText(this, "⚠️ Duplicate or empty QR", Toast.LENGTH_SHORT).show();

            giveScanFeedback(false); // ⚠️ error tone + longer vibration
        }
    }

    private void giveScanFeedback(boolean success) {
        try {
            // VIBRATION
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                            success ? 80 : 200,
                            success ? android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                    : android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                    ));
                } else {
                    vibrator.vibrate(success ? 80 : 200);
                }
            }

            // BEEP SOUND
            android.media.ToneGenerator toneGen =
                    new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
            if (success) {
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100);
            } else {
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 150);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private boolean containsQR(String qr) {
        for (QrItem item : qrItems) if (item.qr.equals(qr)) return true;
        return false;
    }

    private void addQrItemView(String qr) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(10, 10, 10, 10);
        item.setBackgroundColor(Color.parseColor("#F8F8F8")); // light grey background for contrast

        TextView qrText = new TextView(this);
        qrText.setText(qr);
        qrText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        qrText.setTextColor(Color.BLACK); // ✅ make text visible
        qrText.setTextSize(16);

        EditText stemsInput = new EditText(this);
        stemsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        stemsInput.setText("10");
        stemsInput.setEms(3);
        stemsInput.setBackgroundResource(android.R.drawable.edit_text); // ✅ visible edit box
        stemsInput.setTextColor(Color.BLACK);
        stemsInput.setHintTextColor(Color.GRAY);

        stemsInput.addTextChangedListener(new SimpleTextWatcher(text -> {
            for (QrItem item1 : qrItems) {
                if (item1.qr.equals(qr)) {
                    try {
                        item1.stems = Integer.parseInt(text);
                    } catch (Exception ignored) {}
                }
            }
        }));

        Button removeBtn = new Button(this);
        removeBtn.setText("X");
        removeBtn.setTextColor(Color.WHITE);
        removeBtn.setBackgroundColor(Color.RED); // ✅ clear red button
        removeBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                qrItems.removeIf(q -> q.qr.equals(qr));
            }
            qrListLayout.removeView(item);
            if (qrItems.isEmpty()) submitBtn.setEnabled(false);
        });

        item.addView(qrText);
        item.addView(stemsInput);
        item.addView(removeBtn);
        qrListLayout.addView(item);
    }

    private void highlightInvalidQRCodes(JSONObject errors) {
        try {
            for (int i = 0; i < qrListLayout.getChildCount(); i++) {
                View view = qrListLayout.getChildAt(i);
                if (view instanceof LinearLayout) {
                    LinearLayout item = (LinearLayout) view;

                    // Get the actual QR from your model list, not just the view text
                    QrItem qrItem = qrItems.get(i);
                    String qrCode = qrItem.qr;

                    TextView qrText = (TextView) item.getChildAt(0);

                    if (errors.has(qrCode)) {
                        String reason = errors.getString(qrCode);
                        item.setBackgroundColor(Color.parseColor("#f8d7da")); // light red
                        qrText.setText(qrCode + " ❌ (" + reason + ")");
                        qrText.setTextColor(Color.RED);
                    } else {
                        item.setBackgroundColor(Color.parseColor("#F8F8F8")); // normal
                        qrText.setText(qrCode);
                        qrText.setTextColor(Color.BLACK);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void loadFarms() {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "get_farms.php")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) return;

                JSONArray arr = new JSONArray(response.body().string());
                List<String> farmNames = new ArrayList<>();
                List<Integer> farmIds = new ArrayList<>();

                farmNames.add("-- Choose Farm --");
                farmIds.add(0);

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    farmNames.add(obj.getString("name"));
                    farmIds.add(obj.getInt("id"));
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            R.layout.spinner_dropdown_item, farmNames);
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

                    farmSpinner.setAdapter(adapter);

                    farmSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                            if (position > 0) {
                                selectedFarmId = farmIds.get(position);
                                selectedFarmName = farmNames.get(position);
                                buncherInput.setEnabled(true);
                                varietyInput.setEnabled(true);
                                setupAutocomplete();
                            } else {
                                selectedFarmId = 0;
                                selectedFarmName = "";
                                buncherInput.setEnabled(false);
                                varietyInput.setEnabled(false);
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "⚠️ Failed to load farms", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    // ---------------- AUTOCOMPLETE ----------------
    private void setupAutocomplete() {
        buncherInput.addTextChangedListener(new SimpleTextWatcher(this::fetchBunchers));
        varietyInput.addTextChangedListener(new SimpleTextWatcher(this::fetchVarieties));

        buncherInput.setOnItemClickListener((parent, view, position, id) -> {
            BuncherAdapter adapter = (BuncherAdapter) buncherInput.getAdapter();
            selectedBuncherId = adapter.getId(position);
        });

        varietyInput.setOnItemClickListener((parent, view, position, id) -> {
            VarietyAdapter adapter = (VarietyAdapter) varietyInput.getAdapter();
            selectedVarietyId = adapter.getId(position);
            qrInput.setEnabled(true);
        });
    }

    // ---------------- FETCH BUNCHERS ----------------
    private void fetchBunchers(String term) {
        new Thread(() -> {
            try {
                if (term.isEmpty() || selectedFarmId == 0) return;

                HttpUrl url = HttpUrl.parse(BASE_URL + "search_buncher.php")
                        .newBuilder()
                        .addQueryParameter("term", term)
                        .addQueryParameter("farmid", String.valueOf(selectedFarmId))
                        .build();

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                if (!response.isSuccessful() || response.body() == null) return;

                JSONArray arr = new JSONArray(response.body().string());
                List<String> names = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    names.add(o.getString("label"));
                    ids.add(o.getInt("value"));
                }

                runOnUiThread(() -> {
                    BuncherAdapter adapter = new BuncherAdapter(this, names, ids);
                    buncherInput.setAdapter(adapter);
                    buncherInput.showDropDown();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ---------------- FETCH VARIETIES ----------------
    private void fetchVarieties(String term) {
        new Thread(() -> {
            try {
                if (term.isEmpty() || selectedFarmName.isEmpty()) return;

                HttpUrl url = HttpUrl.parse(BASE_URL + "load_varieties.php")
                        .newBuilder()
                        .addQueryParameter("term", term)
                        .addQueryParameter("farm", selectedFarmName)
                        .build();

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                if (!response.isSuccessful() || response.body() == null) return;

                JSONArray arr = new JSONArray(response.body().string());
                List<String> names = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    // Using "VarietyName" and "VarietyId" from your PHP endpoint
                    names.add(o.getString("VarietyName") );
                    ids.add(o.getInt("VarietyId"));
                }

                runOnUiThread(() -> {
                    VarietyAdapter adapter = new VarietyAdapter(this, names, ids);
                    varietyInput.setAdapter(adapter);
                    varietyInput.showDropDown();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ---------------- VALIDATION BEFORE SAVE ----------------
    private void validateBeforeSave() {
        if (qrItems.isEmpty()) {
            Toast.makeText(this, "No QR codes to validate", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedFarmId == 0) {
            Toast.makeText(this, "Select a farm first", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONArray qrcodes = new JSONArray();
                for (QrItem item : qrItems) {
                    JSONObject qrObj = new JSONObject();
                    qrObj.put("qr", item.qr);
                    qrObj.put("stems", item.stems);
                    qrcodes.put(qrObj);
                }

                FormBody formBody = new FormBody.Builder()
                        .add("farm_id", String.valueOf(selectedFarmId))
                        .add("qrcodes", qrcodes.toString())
                        .build();

                Request request = new Request.Builder()
                        .url(BASE_URL + "wip_validate.php")
                        .post(formBody)
                        .build();

                Response response = client.newCall(request).execute();
                String responseStr = response.body() != null ? response.body().string() : "";
                System.out.println("💬 Server Response: " + responseStr);

                JSONObject json = new JSONObject(responseStr);
                String status = json.optString("status", "unknown");

                runOnUiThread(() -> {
                    if ("invalid".equalsIgnoreCase(status)) {
                        JSONObject errors = json.optJSONObject("errors");
                        if (errors != null) {
                            highlightInvalidQRCodes(errors);
                            Toast.makeText(this, "❌ Invalid QRs found. Remove them before saving.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // ✅ All valid → proceed to save
                        saveScans();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "⚠️ Validation error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }



    // ---------------- SAVE SCANS (MATCHES wip_save.php) ----------------
    private void saveScans() {
        if (qrItems.isEmpty()) {
            Toast.makeText(this, "No QR codes to save", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedBuncherId == 0 || selectedVarietyId == 0) {
            Toast.makeText(this, "Select buncher and variety first", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONArray qrcodes = new JSONArray();
                for (QrItem item : qrItems) {
                    JSONObject qrObj = new JSONObject();
                    qrObj.put("qr", item.qr);
                    qrObj.put("stems", item.stems);
                    qrcodes.put(qrObj);
                }

                FormBody formBody = new FormBody.Builder()
                        .add("buncher_id", String.valueOf(selectedBuncherId))
                        .add("variety_id", String.valueOf(selectedVarietyId))
                        .add("qrcodes", qrcodes.toString())
                        .build();

                Request request = new Request.Builder()
                        .url(BASE_URL + "wip_save.php")
                        .post(formBody)
                        .build();

                Response response = client.newCall(request).execute();
                String responseStr = response.body() != null ? response.body().string() : "";

                JSONObject json = new JSONObject(responseStr);
                String msg = json.optString("message", "No response");
                boolean success = "success".equalsIgnoreCase(json.optString("status"));

                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    if (success) {
                        qrItems.clear();
                        qrListLayout.removeAllViews();
                        submitBtn.setEnabled(false);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "⚠️ Error saving scans: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ---------------- HELPERS ----------------
    static class QrItem {
        String qr;
        int stems;
        QrItem(String qr, int stems) { this.qr = qr; this.stems = stems; }
    }

    public static class SimpleTextWatcher implements TextWatcher {
        private final OnTextChangedListener listener;
        public interface OnTextChangedListener { void onTextChanged(String text); }
        public SimpleTextWatcher(OnTextChangedListener listener) { this.listener = listener; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { listener.onTextChanged(s.toString()); }
        @Override public void afterTextChanged(Editable s) {}
    }

    public static class BuncherAdapter extends ArrayAdapter<String> {
        private final List<Integer> ids;
        public BuncherAdapter(Context context, List<String> names, List<Integer> ids) {
            super(context, R.layout.spinner_dropdown_item, names);
            this.ids = ids;
        }
        public int getId(int position) { return ids.get(position); }
    }

    public static class VarietyAdapter extends ArrayAdapter<String> {
        private final List<Integer> ids;
        public VarietyAdapter(Context context, List<String> names, List<Integer> ids) {
            super(context, R.layout.spinner_dropdown_item, names);
            this.ids = ids;
        }
        public int getId(int position) { return ids.get(position); }
    }
}
