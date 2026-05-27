package com.yourapp.summers;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.journeyapps.barcodescanner.*;
import com.yourapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import okhttp3.*;

public class SummersIntakeActivity extends AppCompatActivity {

    TextView serial, bucket, farm, length, statusText;

    EditText qrInput;
    AutoCompleteTextView varietyInput;
    EditText quantityInput;
    Button scanBtn, submitBtn, addVarietyBtn;

    RecyclerView varietyList;

    boolean isBucketBlocked = false;

    List<JSONObject> selectedVarieties = new ArrayList<>();
    VarietyAdapter adapter;

    String BASE_URL = "https://www.aaagrowers.co.ke/inventory/summers/api/";

    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final long REQUEST_DELAY = 800;
    private long lastRequestTime = 0;

    private boolean isProcessingQR = false;
    private Call activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summers_intake);

        qrInput = findViewById(R.id.qr_code);
        serial = findViewById(R.id.serial);
        bucket = findViewById(R.id.bucket);
        farm = findViewById(R.id.farm);
        length = findViewById(R.id.length);
        statusText=findViewById(R.id.statusText);

        varietyInput = findViewById(R.id.varietyInput);
        quantityInput = findViewById(R.id.quantityInput);

        scanBtn = findViewById(R.id.scanBtn);
        submitBtn = findViewById(R.id.submitBtn);
        addVarietyBtn = findViewById(R.id.addVarietyBtn);

        varietyList = findViewById(R.id.varietyList);

        adapter = new VarietyAdapter(selectedVarieties);
        varietyList.setLayoutManager(new LinearLayoutManager(this));
        varietyList.setAdapter(adapter);

        scanBtn.setOnClickListener(v -> startScanner());
        addVarietyBtn.setOnClickListener(v -> addVariety());
        submitBtn.setOnClickListener(v -> submitData());

        // ================= HONEYWELL LISTENER =================
        qrInput.addTextChangedListener(new android.text.TextWatcher() {

            private Runnable runnable;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                handler.removeCallbacks(runnable);

                runnable = () -> {

                    String scanned = qrInput.getText().toString().trim();

                    if (!scanned.isEmpty()) {

                        Log.d("HONEYWELL", "SCAN: " + scanned);

                        parseQR(scanned);

                        qrInput.setText("");
                    }
                };

                handler.postDelayed(runnable, 250);
            }

            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // ENTER / MANUAL INPUT
        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String input = qrInput.getText().toString().trim();
            if (!input.isEmpty()) {
                parseQR(input);
                qrInput.setText("");
            }
            return true;
        });

        qrInput.requestFocus();

        setupVarietyAutocomplete();
    }

    @Override
    protected void onResume() {
        super.onResume();
        qrInput.requestFocus();
    }

    // ================= CAMERA SCANNER =================

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    parseQR(result.getContents());
                }
            });

    private void startScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Summer QR");
        scanner.launch(options);
    }

    // ================= QR PARSER =================

    private void parseQR(String text) {

        if (isProcessingQR) return;

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < REQUEST_DELAY) return;

        isProcessingQR = true;
        lastRequestTime = now;

        try {

            clearBucketUI(); // ✅ MOVE HERE (VERY IMPORTANT)

            text = text.replace("\u0000", "");
            text = text.replaceAll("[^\\x20-\\x7E|:]", "");
            text = text.replaceAll("[\\r\\n]", "").trim();

            String serialVal = "", bucketVal = "", farmVal = "", lengthVal = "";

            for (String p : text.split("\\|")) {

                p = p.trim().toLowerCase(Locale.ROOT);

                if (p.startsWith("serial")) {
                    String[] sp = p.split(":");
                    if (sp.length > 1) serialVal = sp[1].trim();
                }

                if (p.startsWith("bucket")) {
                    String[] sp = p.split(":");
                    if (sp.length > 1) bucketVal = sp[1].trim();
                }

                if (p.startsWith("farm")) {
                    String[] sp = p.split(":");
                    if (sp.length > 1) farmVal = sp[1].trim();
                }

                if (p.startsWith("length")) {
                    String[] sp = p.split(":");
                    if (sp.length > 1) lengthVal = sp[1].trim();
                }
            }

            if (serialVal.isEmpty() || farmVal.isEmpty() || lengthVal.isEmpty()) {
                Toast.makeText(this, "Invalid QR content", Toast.LENGTH_SHORT).show();
                isProcessingQR = false;
                return;
            }

            // ✅ NOW populate AFTER clearing
            serial.setText("Serial: " + serialVal);
            bucket.setText("Bucket: " + bucketVal);
            farm.setText("Farm: " + farmVal);
            length.setText("Length: " + lengthVal + " CM");

            checkBucketStatus(serialVal);
            varietyInput.requestFocus();

        } catch (Exception e) {
            isProcessingQR = false;
            Toast.makeText(this, "QR Parse Error", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= AUTOCOMPLETE =================

    private long lastSearchTime = 0;
    private String lastQuery = "";

    private void setupVarietyAutocomplete() {

        varietyInput.setThreshold(3);

        varietyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}

            @Override
            public void afterTextChanged(android.text.Editable s) {

                String term = s.toString().trim();
                if (term.length() < 3) return;

                long now = System.currentTimeMillis();
                if (now - lastSearchTime < 600) return;
                if (term.equals(lastQuery)) return;

                lastQuery = term;
                lastSearchTime = now;

                String url = BASE_URL + "search_summer_variety.php?term=" + term;

                makeGetRequest(url, new SimpleCallback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONArray arr = new JSONArray(response);
                            List<String> names = new ArrayList<>();

                            for (int i = 0; i < arr.length(); i++) {
                                names.add(arr.getJSONObject(i).getString("Variety"));
                            }

                            runOnUiThread(() -> {
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                        SummersIntakeActivity.this,
                                        android.R.layout.simple_dropdown_item_1line,
                                        names
                                );
                                varietyInput.setAdapter(adapter);
                            });

                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onFail(String error) {}
                });
            }
        });
    }

    private void makeGetRequest(String url, SimpleCallback callback) {

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < REQUEST_DELAY) return;

        lastRequestTime = now;

        if (activeCall != null) activeCall.cancel();

        Request request = new Request.Builder().url(url).build();

        activeCall = client.newCall(request);

        activeCall.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                callback.onSuccess(response.body().string());
            }
        });
    }

    interface SimpleCallback {
        void onSuccess(String response);
        void onFail(String error);
    }

    // ================= CHECK BUCKET =================

    private void checkBucketStatus(String serialVal) {

        String url = BASE_URL + "check_bucket_status.php?serial=" + serialVal;

        Log.d("BUCKET_CHECK", "Requesting: " + url);

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BUCKET_CHECK", "FAILED", e);

                runOnUiThread(() -> {
                    isProcessingQR = false;
                    statusText.setText("❌ Network Error");
                    statusText.setTextColor(Color.RED);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String body = response.body().string();
                Log.d("BUCKET_CHECK", "RESPONSE: " + body);

                try {
                    JSONObject json = new JSONObject(body);

                    String status = json.optString("status");
                    String message = json.optString("message");

                    runOnUiThread(() -> {

                        isProcessingQR = false;

                        if ("blocked".equals(status)) {

                            isBucketBlocked = true;

                            statusText.setText("❌ Bucket In Use");
                            statusText.setTextColor(Color.RED);

                            submitBtn.setEnabled(false);
                            addVarietyBtn.setEnabled(false);
                            varietyInput.setEnabled(false);
                            quantityInput.setEnabled(false);

                            clearBucketUI(); // 🔥 IMPORTANT

                            Toast.makeText(SummersIntakeActivity.this,
                                    message,
                                    Toast.LENGTH_LONG).show();

                        } else {

                            isBucketBlocked = false;

                            statusText.setText("✅ Ready for Intake");
                            statusText.setTextColor(Color.GREEN);

                            submitBtn.setEnabled(true);
                            addVarietyBtn.setEnabled(true);
                            varietyInput.setEnabled(true);
                            quantityInput.setEnabled(true);
                        }
                    });

                } catch (Exception e) {
                    Log.e("BUCKET_CHECK", "PARSE ERROR", e);

                    runOnUiThread(() -> {
                        isProcessingQR = false;
                        statusText.setText("❌ Response Error");
                        statusText.setTextColor(Color.RED);
                    });
                }
            }
        });
    }

    private void clearBucketUI() {
        serial.setText("Serial:");
        bucket.setText("Bucket:");
        farm.setText("Farm:");
        length.setText("Length:");

        selectedVarieties.clear();
        adapter.notifyDataSetChanged();
    }

    private void verifyBeforeSubmit(Runnable onSuccess) {

        String serialVal = serial.getText().toString()
                .replace("Serial:", "")
                .trim();

        String url = BASE_URL + "check_bucket_status.php?serial=" + serialVal;

        Log.d("VERIFY", "Checking before submit: " + url);

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(SummersIntakeActivity.this,
                                "Validation failed (network)",
                                Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String body = response.body().string();
                Log.d("VERIFY", body);

                try {
                    JSONObject json = new JSONObject(body);
                    String status = json.optString("status");

                    runOnUiThread(() -> {

                        if ("blocked".equals(status)) {

                            isBucketBlocked = true;

                            statusText.setText("❌ Bucket Became Blocked");
                            statusText.setTextColor(Color.RED);

                            submitBtn.setEnabled(false);

                            Toast.makeText(SummersIntakeActivity.this,
                                    "Bucket is blocked. Cannot submit.",
                                    Toast.LENGTH_LONG).show();

                            return;
                        }

                        isBucketBlocked = false;
                        onSuccess.run();
                    });

                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(SummersIntakeActivity.this,
                                    "Validation error",
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    // ================= ADD VARIETY =================

    private void addVariety() {

        if (isBucketBlocked) {
            Toast.makeText(this, "Bucket is blocked", Toast.LENGTH_SHORT).show();
            return;
        }

        String variety = varietyInput.getText().toString().trim();
        String qty = quantityInput.getText().toString().trim();

        if (TextUtils.isEmpty(variety) || TextUtils.isEmpty(qty)) {
            Toast.makeText(this, "Enter variety & quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject obj = new JSONObject();
            obj.put("variety_name", variety);
            obj.put("quantity", qty);

            selectedVarieties.add(obj);
            adapter.notifyDataSetChanged();

            varietyInput.setText("");
            quantityInput.setText("");

        } catch (Exception ignored) {}
    }

    // ================= SUBMIT =================

    private void submitData() {

        if (selectedVarieties.isEmpty()) {
            Toast.makeText(this, "Add varieties first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isBucketBlocked || !submitBtn.isEnabled()) {
            Toast.makeText(this, "Bucket is blocked", Toast.LENGTH_LONG).show();
            return;
        }

        verifyBeforeSubmit(() -> {
            actuallySubmit();
        });
    }
    private void actuallySubmit() {
        // ✅ CLEAN VALUES PROPERLY
        String cleanSerial = serial.getText().toString().replace("Serial:", "").trim();
        String cleanBucket = bucket.getText().toString().replace("Bucket:", "").trim();
        String cleanFarm = farm.getText().toString().replace("Farm:", "").trim();
        String cleanLength = length.getText().toString()
                .replace("Length:", "")
                .replace("CM", "")
                .trim();

        JSONArray arr = new JSONArray(selectedVarieties);

        RequestBody body = new FormBody.Builder()
                .add("serial", cleanSerial)
                .add("bucket_name", cleanBucket)
                .add("farm", cleanFarm)
                .add("length", cleanLength)
                .add("user_id", "1")
                .add("varieties_json", arr.toString())
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "summers_intake_process.php")
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(SummersIntakeActivity.this,
                                "Network Failed",
                                Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String res = response.body().string();

                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(res);

                        String status = json.optString("status");
                        String message = json.optString("message");

                        Toast.makeText(SummersIntakeActivity.this,
                                message,
                                Toast.LENGTH_LONG).show();

                        if (status.equals("success")) {
                            selectedVarieties.clear();
                            adapter.notifyDataSetChanged();
                        }

                    } catch (Exception e) {
                        Toast.makeText(SummersIntakeActivity.this,
                                "Invalid response",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}