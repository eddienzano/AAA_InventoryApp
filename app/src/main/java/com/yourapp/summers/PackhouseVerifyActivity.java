package com.yourapp.summers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class PackhouseVerifyActivity extends AppCompatActivity {

    TextView serial, bucket, farm, length, statusText;
    RecyclerView recycler;
    Button scanBtn, submitBtn;
    EditText qrInput;

    List<JSONObject> intakeList = new ArrayList<>();
    VerifyAdapter adapter;

    String BASE_URL = "https://www.aaagrowers.co.ke/inventory/summers/api/";

    String currentSerial = "";
    String currentBatchId = "";

    private final OkHttpClient client = new OkHttpClient();
    private Call activeCall;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final long REQUEST_DELAY_MS = 800;
    private long lastRequestTime = 0;

    private boolean isProcessingQR = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packhouse_verify);

        serial = findViewById(R.id.serial);
        bucket = findViewById(R.id.bucket);
        farm = findViewById(R.id.farm);
        length = findViewById(R.id.length);
        statusText = findViewById(R.id.statusText);

        recycler = findViewById(R.id.recycler);
        scanBtn = findViewById(R.id.scanBtn);
        submitBtn = findViewById(R.id.submitBtn);

        qrInput = findViewById(R.id.qr_code);

        adapter = new VerifyAdapter(intakeList);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        scanBtn.setOnClickListener(v -> startScanner());
        submitBtn.setOnClickListener(v -> submitVerification());

        /* ================= HONEYWELL LISTENER ================= */
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

        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String text = qrInput.getText().toString().trim();

            if (!text.isEmpty()) {
                parseQR(text);
                qrInput.setText("");
            }
            return true;
        });

        qrInput.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        qrInput.requestFocus();
    }

    /* ================= CAMERA SCANNER ================= */

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {

                if (result.getContents() != null) {
                    parseQR(result.getContents());
                } else {
                    isProcessingQR = false;
                    statusText.setText("Scan cancelled");
                }
            });

    private void startScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        scanner.launch(options);
    }

    /* ================= QR PARSER ================= */

    private void parseQR(String text) {

        long now = System.currentTimeMillis();
        if (isProcessingQR) return;
        if (now - lastRequestTime < REQUEST_DELAY_MS) return;

        isProcessingQR = true;
        lastRequestTime = now;

        try {

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

            if (serialVal.isEmpty()) {
                Toast.makeText(this, "Invalid QR content", Toast.LENGTH_SHORT).show();
                isProcessingQR = false;
                return;
            }

            currentSerial = serialVal;

            clearPreviousData();

            serial.setText("Serial: " + serialVal);
            bucket.setText("Bucket: " + bucketVal);
            farm.setText("Farm: " + farmVal);
            length.setText("Length: " + lengthVal + " CM");

            fetchBatchData();

        } catch (Exception e) {
            isProcessingQR = false;
            statusText.setText("QR Parse Error");
        }
    }

    private void clearPreviousData() {
        intakeList.clear();
        adapter.notifyDataSetChanged();
        statusText.setText("Loading new scan...");
        currentBatchId = "";
        submitBtn.setEnabled(false); // 🔥 disable until confirmed
    }

    /* ================= FETCH ================= */

    private void fetchBatchData() {

        cancelActiveCall();

        HttpUrl url = HttpUrl.parse(BASE_URL + "get_summer_intake.php")
                .newBuilder()
                .addQueryParameter("serial", currentSerial)
                .build();

        Request request = new Request.Builder().url(url).build();

        activeCall = client.newCall(request);

        activeCall.enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    isProcessingQR = false;
                    statusText.setText("Network Error");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String body = response.body().string();

                try {
                    JSONObject json = new JSONObject(body);

                    if (!json.optString("status").equals("success")) {
                        runOnUiThread(() -> {
                            isProcessingQR = false;
                            statusText.setText(json.optString("message"));
                        });
                        return;
                    }

                    boolean isVerified = json.optInt("verified", 0) == 1;

                    if (isVerified) {
                        runOnUiThread(() -> {
                            statusText.setText("❌ Already Verified");
                            intakeList.clear();
                            adapter.notifyDataSetChanged();
                            submitBtn.setEnabled(false);
                            isProcessingQR = false;
                        });
                        return;
                    }

                    currentBatchId = json.getString("batch_id");
                    JSONArray arr = json.getJSONArray("data");

                    intakeList.clear();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        int qty = obj.optInt("quantity", 0);
                        obj.put("original_qty", qty);
                        obj.put("verified_qty", qty);

                        intakeList.add(obj);
                    }

                    runOnUiThread(() -> {
                        isProcessingQR = false;
                        statusText.setText("Loaded: " + intakeList.size());
                        adapter.notifyDataSetChanged();
                        submitBtn.setEnabled(true); // 🔥 enable only if valid
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        isProcessingQR = false;
                        statusText.setText("Parse Error");
                    });
                }
            }
        });
    }

    /* ================= SUBMIT ================= */

    private void submitVerification() {

        JSONArray arr = new JSONArray(intakeList);

        RequestBody body = new FormBody.Builder()
                .add("serial", currentSerial)
                .add("batch_id", currentBatchId)
                .add("varieties_json", arr.toString())
                .add("user_id", "1")
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "summers_verify_process.php")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(PackhouseVerifyActivity.this,
                                "Network Failed",
                                Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String body = response.body().string();

                try {
                    JSONObject json = new JSONObject(body);

                    runOnUiThread(() -> {

                        if (json.optString("status").equals("success")) {

                            Toast.makeText(PackhouseVerifyActivity.this,
                                    "✅ Verification Complete",
                                    Toast.LENGTH_LONG).show();

                            statusText.setText("Status: Verified");
                            submitBtn.setEnabled(false);

                            intakeList.clear();
                            adapter.notifyDataSetChanged();

                        } else {

                            Toast.makeText(PackhouseVerifyActivity.this,
                                    "❌ " + json.optString("message"),
                                    Toast.LENGTH_LONG).show();

                            statusText.setText(json.optString("message"));
                        }

                        isProcessingQR = false;
                    });

                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(PackhouseVerifyActivity.this,
                                    "Parse Error",
                                    Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    private void cancelActiveCall() {
        if (activeCall != null) activeCall.cancel();
    }
}