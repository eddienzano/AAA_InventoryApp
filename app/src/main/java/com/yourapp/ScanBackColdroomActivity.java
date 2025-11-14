package com.yourapp;

import android.annotation.SuppressLint;
import android.content.*;
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
import java.util.*;
import java.util.regex.*;

import okhttp3.*;

public class ScanBackColdroomActivity extends AppCompatActivity {

    private EditText qrCodeInput, quantityInput;
    private Spinner coldroomSpinner;
    private AutoCompleteTextView varietyDropdown;
    private Button scanBtn, submitBtn;

    private String username;
    private int userId;

    private Integer selectedVarietyId = null;
    private String selectedVarietyName = null;

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private BroadcastReceiver honeywellScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_back_coldroom);

        username = getIntent().getStringExtra("username");
        userId = getIntent().getIntExtra("user_id", -1);

        qrCodeInput = findViewById(R.id.qr_code);
        quantityInput = findViewById(R.id.quantity);
        coldroomSpinner = findViewById(R.id.coldroom);
        varietyDropdown = findViewById(R.id.variety);
        scanBtn = findViewById(R.id.scanBtn);
        submitBtn = findViewById(R.id.submitBtn);

        // Coldroom spinner
        ArrayAdapter<CharSequence> coldroomAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.coldroom_labels,
                R.layout.spinner_item
        );
        coldroomAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        coldroomSpinner.setAdapter(coldroomAdapter);
        coldroomSpinner.setSelection(1); // Default Cold Room 1

        setupQRParsing();
        setupVarietyAutocomplete();

        scanBtn.setOnClickListener(v -> startQRScanner());
        submitBtn.setOnClickListener(v -> submitScanBack());

        setupHoneywellScanner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(honeywellScanReceiver); } catch (Exception ignored) {}
    }

    /** QR SCANNER **/
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    qrCodeInput.setText(result.getContents().trim());
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
        qrCodeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                qrCodeInput.setText(qrCodeInput.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    /** VARIETY AUTOCOMPLETE **/
    /** VARIETY AUTOCOMPLETE **/
    private void setupVarietyAutocomplete() {
        varietyDropdown.setThreshold(2); // Start suggestions after typing 2 chars

        HashMap<String, JSONObject> varietyMap = new HashMap<>();

        // Show dropdown automatically as user types
        varietyDropdown.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String term = s.toString().trim();
                selectedVarietyId = null;
                selectedVarietyName = null;

                if (term.length() >= 2) {
                    fetchVarietySuggestions(term, varietyMap);
                }
            }
        });

        // When the user taps a variety from dropdown
        varietyDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDisplay = (String) parent.getItemAtPosition(position);
            JSONObject obj = varietyMap.get(selectedDisplay);
            if (obj != null) {
                selectedVarietyId = obj.optInt("VarietyId");
                selectedVarietyName = obj.optString("VarietyName");
            }
        });

        // Force keyboard to appear when tapping the field
        varietyDropdown.setOnClickListener(v -> varietyDropdown.showDropDown());
    }

    /** FETCH VARIETY SUGGESTIONS FROM SERVER **/
    private void fetchVarietySuggestions(String term, HashMap<String, JSONObject> varietyMap) {
        int pos = coldroomSpinner.getSelectedItemPosition();
        String[] farmNames = getResources().getStringArray(R.array.coldroom_labels);
        String farmName = (pos >= 0 && pos < farmNames.length) ? farmNames[pos] : "";

        HttpUrl url = HttpUrl.parse(BASE_URL + "search_variety_coldroom.php")
                .newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("farm", farmName)
                .build();

        Request request = new Request.Builder().url(url).build();
        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ScanBackColdroomActivity.this,
                        "Error fetching varieties", Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    List<String> names = new ArrayList<>();
                    varietyMap.clear();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String display = obj.getString("VarietyName");
                        names.add(display);
                        varietyMap.put(display, obj);
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(ScanBackColdroomActivity.this,
                                R.layout.autocomplete_item, names);
                        varietyDropdown.setAdapter(adapter);
                        if (!varietyDropdown.getText().toString().isEmpty()) varietyDropdown.showDropDown();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(ScanBackColdroomActivity.this,
                            "Invalid server response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** SUBMIT **/
    private void submitScanBack() {
        String qrCode = qrCodeInput.getText().toString().trim();
        String quantity = quantityInput.getText().toString().trim();
        int pos = coldroomSpinner.getSelectedItemPosition();
        String[] coldroomValues = getResources().getStringArray(R.array.coldroom_values);
        String coldroomValue = (pos >= 0 && pos < coldroomValues.length) ? coldroomValues[pos] : "";

        if (qrCode.isEmpty() || quantity.isEmpty() || coldroomValue.isEmpty() || selectedVarietyId == null) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject varietyObj = new JSONObject();
        try {
            varietyObj.put("VarietyId", selectedVarietyId);
            varietyObj.put("VarietyName", selectedVarietyName);
        } catch (Exception e) { e.printStackTrace(); }

        sendToServer(qrCode, varietyObj, quantity, coldroomValue, userId);
    }

    /** SEND TO SERVER **/
    private void sendToServer(String qrCode, JSONObject varietyObj,
                              String quantity, String coldroom, int userId) {

        Map<String, String> qrData = parseQr(qrCode);
        String serial = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            serial = qrData.getOrDefault("serial", "");
        }
        String bucketName = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bucketName = qrData.getOrDefault("bucket_name", "");
        }
        String farm = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            farm = qrData.getOrDefault("farm", "");
        }
        String length = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            length = qrData.getOrDefault("length", "");
        }

        FormBody.Builder bodyBuilder = new FormBody.Builder()
                .add("qr_code", qrCode)
                .add("serial", serial)
                .add("bucket_name", bucketName)
                .add("farm", farm)
                .add("length", length)
                .add("quantity", quantity)
                .add("coldroom", coldroom)
                .add("user_id", String.valueOf(userId));

        try {
            Iterator<String> keys = varietyObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                bodyBuilder.add(key, varietyObj.optString(key));
            }
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = bodyBuilder.build();

        Request request = new Request.Builder()
                .url(BASE_URL + "scan_back_coldroom_process.php")
                .post(body)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();

        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ScanBackColdroomActivity.this,
                        "Failed to send. Check network.", Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String status = json.optString("status");
                    String message = json.optString("message");

                    runOnUiThread(() -> {
                        if ("success".equals(status)) {
                            new AlertDialog.Builder(ScanBackColdroomActivity.this)
                                    .setTitle("Success")
                                    .setMessage(message)
                                    .setPositiveButton("OK", (d, w) -> clearForm())
                                    .show();
                        } else {
                            Toast.makeText(ScanBackColdroomActivity.this,
                                    "Server error: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(ScanBackColdroomActivity.this,
                            "Invalid server response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** PARSE QR **/
    private Map<String, String> parseQr(String qrText) {
        Map<String, String> map = new HashMap<>();
        Pattern pattern = Pattern.compile("Serial: *([^|]+)\\| *Bucket: *([^|]+)\\| *Farm: *([^|]+)\\| *Length: *([^|]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(qrText);
        if (matcher.find()) {
            map.put("serial", matcher.group(1).trim());
            map.put("bucket_name", matcher.group(2).trim());
            map.put("farm", matcher.group(3).trim());
            map.put("length", matcher.group(4).trim());
        }
        return map;
    }

    private void clearForm() {
        qrCodeInput.setText("");
        varietyDropdown.setText("");
        quantityInput.setText("");
//        coldroomSpinner.setSelection(0);
        selectedVarietyId = null;
        selectedVarietyName = null;
    }

    /** HONEYWELL SCANNER **/
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupHoneywellScanner() {
        honeywellScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String barcodeData = intent.getStringExtra("data");
                    if (barcodeData == null) return;
                    qrCodeInput.setText(barcodeData.trim());
                } catch (Exception e) { e.printStackTrace(); }
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
}
