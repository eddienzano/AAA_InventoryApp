package com.yourapp;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.*;

public class IntakeActivity extends AppCompatActivity {

    private LocalDatabaseHelper localDb;
    private EditText qrCodeInput, serialInput, bucketInput, farmInput, lengthInput, quantityInput;
    private Spinner coldroomSpinner;
    private AutoCompleteTextView varietyDropdown;
    private EditText blockDisplay; // Disabled EditText for greenhouse/displayname
    private Button scanBtn, submitBtn, syncBtn;

    private String username;
    private int userId;

    private Integer selectedVarietyId = null;
    private String selectedVarietyName = null;
    private String selectedGreenhouseName = null;

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    // Maps to hold dropdown ids
    private final HashMap<String, Integer> varietyMap = new HashMap<>();

    // BroadcastReceivers
    private BroadcastReceiver networkReceiver;
    private BroadcastReceiver honeywellScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intake);

        username = getIntent().getStringExtra("username");
        userId = getIntent().getIntExtra("user_id", -1);
        localDb = new LocalDatabaseHelper(this);

        // Bind UI
        qrCodeInput = findViewById(R.id.qr_code);
        serialInput = findViewById(R.id.serial);
        bucketInput = findViewById(R.id.bucket_name);
        farmInput = findViewById(R.id.farm);
        lengthInput = findViewById(R.id.length);
        quantityInput = findViewById(R.id.quantity);
        coldroomSpinner = findViewById(R.id.coldroom);
        varietyDropdown = findViewById(R.id.variety);
        blockDisplay = findViewById(R.id.block_display);
        scanBtn = findViewById(R.id.scanBtn);
        submitBtn = findViewById(R.id.submitBtn);
        syncBtn = findViewById(R.id.syncBtn);


        // ✅ Set default quantity to 100
        quantityInput.setText(getString(R.string.default_quantity));

        // Coldroom spinner
        ArrayAdapter<CharSequence> coldroomAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.coldroom_labels,
                R.layout.spinner_item
        );
        coldroomAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        coldroomSpinner.setAdapter(coldroomAdapter);

// ✅ Set default to "Cold Room 1"
        coldroomSpinner.setSelection(1);


        // QR parsing
        setupQRParsing();

        // Scan button
        scanBtn.setOnClickListener(v -> startQRScanner());

        // Submit button
        submitBtn.setOnClickListener(v -> submitIntake());

        // Sync button
        syncBtn.setOnClickListener(v -> {
            syncUnsynced();
            Toast.makeText(this, "Syncing unsynced records...", Toast.LENGTH_SHORT).show();
        });

        // Honeywell scanner
        setupHoneywellScanner();

        // AutoSync on network reconnect
        setupNetworkReceiver();

        // ✅ Autocomplete for variety (replaces old loadVarieties/loadGreenhouses)
        setupVarietyAutocomplete();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(honeywellScanReceiver); } catch (Exception ignored) {}
    }

    /** -------------------------
     * QR Scanner & Parsing
     * -------------------------
     */
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String clean = result.getContents();
                    qrCodeInput.setText(clean);
                    parseQR(clean);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void setupQRParsing() {
        qrCodeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) parseQR(qrCodeInput.getText().toString().trim());
        });

        qrCodeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                String input = qrCodeInput.getText().toString().replaceAll("[\\r\\n]", "").trim();
                if (!input.isEmpty()) parseQR(input);
                return true;
            }
            return false;
        });

        qrCodeInput.addTextChangedListener(new TextWatcher() {
            private final long DEBOUNCE_MS = 250;
            private final Runnable checkRunnable = () -> {
                String text = qrCodeInput.getText().toString().replaceAll("[\\r\\n]", "").trim();
                if (!text.isEmpty()) parseQR(text);
            };

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                qrCodeInput.removeCallbacks(checkRunnable);
                qrCodeInput.postDelayed(checkRunnable, DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void parseQR(String qrText) {
        try {
            String[] parts = qrText.split("\\|");
            for (String part : parts) {
                if (part.contains("Serial:")) serialInput.setText(part.replace("Serial:", "").trim());
                if (part.contains("Bucket:")) bucketInput.setText(part.replace("Bucket:", "").trim());
                if (part.contains("Farm:")) farmInput.setText(part.replace("Farm:", "").trim());
                if (part.contains("Length:")) lengthInput.setText(part.replace("Length:", "").trim());
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid QR format", Toast.LENGTH_SHORT).show();
        }
    }

    /** -------------------------
     * Variety Autocomplete
     * -------------------------
     */
    private void setupVarietyAutocomplete() {
        varietyDropdown.setThreshold(2); // minimum 2 chars before search
        varietyDropdown.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String term = s.toString().trim();
                if (term.length() >= 2) fetchVarietySuggestions(term);
            }
        });
    }

    private void fetchVarietySuggestions(String term) {
        String farm = farmInput.getText().toString().trim();
        HttpUrl url = HttpUrl.parse(BASE_URL + "search_variety.php")
                .newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("farm", farm)
                .build();

        Request request = new Request.Builder().url(url).build();
        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(IntakeActivity.this, "Error fetching varieties", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String resp = response.body().string();
                    JSONArray arr = new JSONArray(resp);
                    List<String> names = new ArrayList<>();
                    HashMap<String, JSONObject> map = new HashMap<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String display = obj.getString("DisplayName");
                        names.add(display);
                        map.put(display, obj);
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(IntakeActivity.this, R.layout.autocomplete_item, names);
                        varietyDropdown.setAdapter(adapter);
                        varietyDropdown.setOnItemClickListener((parent, view, position, id) -> {
                            String selected = adapter.getItem(position);
                            if (selected != null) {
                                JSONObject obj = map.get(selected);
                                if (obj != null) {
                                    selectedVarietyId = obj.optInt("VarietyId");
                                    selectedVarietyName = obj.optString("VarietyName");
                                    selectedGreenhouseName = obj.optString("GreenhouseName");
                                    // Fill fields
                                    blockDisplay.setText(selectedGreenhouseName);
                                }
                            }
                        });
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(IntakeActivity.this, "Error parsing variety data", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** -------------------------
     * Submit Intake
     * -------------------------
     */
    private void submitIntake() {
        String qrCode = qrCodeInput.getText().toString().trim();
        String serial = serialInput.getText().toString().trim();
        String bucket = bucketInput.getText().toString().trim();
        String farm = farmInput.getText().toString().trim();
        String length = lengthInput.getText().toString().trim();
        String quantity = quantityInput.getText().toString().trim();

        int pos = coldroomSpinner.getSelectedItemPosition();
        String[] coldroomValues = getResources().getStringArray(R.array.coldroom_values);
        String coldroomValue = (pos >= 0 && pos < coldroomValues.length) ? coldroomValues[pos] : "";

        if (selectedVarietyId == null || selectedGreenhouseName == null ||
                quantity.isEmpty() || coldroomValue.isEmpty()) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save locally first
        long rowId = localDb.insertIntake(
                qrCode, serial, bucket, farm, length,
                selectedVarietyId, selectedVarietyName,
                0, selectedGreenhouseName, // greenhouseId not used, block = greenhouse
                quantity, coldroomValue, userId, selectedGreenhouseName
        );

        // Send to server
        sendToServer(rowId, qrCode, serial, bucket, farm, length, quantity,
                selectedVarietyId, selectedVarietyName,
                0, selectedGreenhouseName,
                coldroomValue, userId);
    }

    /** -------------------------
     * Honeywell Scanner
     * -------------------------
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupHoneywellScanner() {
        honeywellScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String barcodeData = null;

                    // Try common named extras first
                    if (intent.hasExtra("data")) {
                        Object o = intent.getStringExtra("data");
                        if (o != null) barcodeData = o.toString();
                    }
                    if (barcodeData == null && intent.hasExtra("barcode")) {
                        Object o = intent.getStringExtra("barcode");
                        if (o != null) barcodeData = o.toString();
                    }
                    if (barcodeData == null && intent.hasExtra("decoded_string")) {
                        Object o = intent.getStringExtra("decoded_string");
                        if (o != null) barcodeData = o.toString();
                    }
                    if (barcodeData == null && intent.hasExtra(Intent.EXTRA_TEXT)) {
                        Object o = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (o != null) barcodeData = o.toString();
                    }

                    // Fallback: inspect extras for any string/byte[] value
                    if (barcodeData == null) {
                        Bundle extras = intent.getExtras();
                        if (extras != null) {
                            for (String key : extras.keySet()) {
                                Object val = extras.get(key);
                                if (val == null) continue;
                                if (val instanceof String) {
                                    String s = ((String) val).trim();
                                    if (!s.isEmpty()) { barcodeData = s; break; }
                                } else if (val instanceof byte[]) {
                                    byte[] b = (byte[]) val;
                                    if (b.length > 0) {
                                        String s = new String(b).trim();
                                        if (!s.isEmpty()) { barcodeData = s; break; }
                                    }
                                } else {
                                    String s = val.toString().trim();
                                    if (!s.isEmpty()) { barcodeData = s; break; }
                                }
                            }
                        }
                    }

                    if (barcodeData != null) {
                        String clean = barcodeData.replaceAll("[\\r\\n]", "").trim();
                        if (!clean.isEmpty()) {
                            // Put into the same EditText the camera uses and parse it the same way
                            qrCodeInput.setText(clean);
                            parseQR(clean);
                        }
                    }
                } catch (Exception ex) {
                    // swallow unexpected payloads to avoid crashing
                    ex.printStackTrace();
                }
            }
        };


        IntentFilter honeyFilter = new IntentFilter();
        honeyFilter.addAction("com.honeywell.scan.RESULT");
        honeyFilter.addAction("com.honeywell.aidc.action.SCAN");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(honeywellScanReceiver, honeyFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(honeywellScanReceiver, honeyFilter);
        }
    }

    /** -------------------------
     * Network Auto-Sync
     * -------------------------
     */
    private void setupNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.isConnected()) {
                    syncUnsynced();
                }
            }
        };
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /** -------------------------
     * Server communication & sync
     * -------------------------
     */
    private void sendToServer(long localRowId,
                              String qrCode, String serial, String bucket, String farm, String length, String quantity,
                              int varietyId, String varietyName,
                              int greenhouseId, String greenhouseName,
                              String coldroomValue, int userId) {

        RequestBody body = new FormBody.Builder()
                .add("qr_code", qrCode)
                .add("serial", serial)
                .add("bucket_name", bucket)
                .add("farm", farm)
                .add("length", length)
                .add("variety_id", String.valueOf(varietyId))
                .add("variety_name", varietyName)
                .add("greenhouse_id", String.valueOf(greenhouseId))
                .add("greenhouse_name", greenhouseName)
                .add("quantity", quantity)
                .add("coldroom", coldroomValue)
                .add("user_id", String.valueOf(userId))
                .add("block", greenhouseName)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "intake_process.php")
                .post(body)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();

        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(IntakeActivity.this, "Saved offline. Will sync later.", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resp = response.body().string();
                try {
                    JSONObject json = new JSONObject(resp);
                    String status = json.optString("status");
                    String message = json.optString("message");

                    runOnUiThread(() -> {
                        if (status.equals("success")) {
                            localDb.markIntakeAsSynced((int) localRowId);
                            new AlertDialog.Builder(IntakeActivity.this)
                                    .setTitle("Success")
                                    .setMessage(message)
                                    .setPositiveButton("OK", (d, w) -> clearForm())
                                    .show();
                        } else {
                            Toast.makeText(IntakeActivity.this, "Server Error: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(IntakeActivity.this, "Invalid server response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void syncUnsynced() {
        List<LocalDatabaseHelper.IntakeRecord> unsynced = localDb.getUnsyncedIntakes();
        if (unsynced.isEmpty()) return;

        for (LocalDatabaseHelper.IntakeRecord r : unsynced) {
            sendToServer(r.id, r.qrCode, r.serial, r.bucket, r.farm, r.length, r.quantity,
                    r.varietyId, r.varietyName, 0, r.greenhouseName, r.coldroom, r.userId);
        }
    }

    private void clearForm() {
        qrCodeInput.setText("");
        serialInput.setText("");
        bucketInput.setText("");
        farmInput.setText("");
        lengthInput.setText("");
        quantityInput.setText(getString(R.string.default_quantity));
        varietyDropdown.setText("");
        blockDisplay.setText("");
        coldroomSpinner.setSelection(1);

        selectedVarietyId = null;
        selectedVarietyName = null;
        selectedGreenhouseName = null;

        qrCodeInput.requestFocus();
    }
}
