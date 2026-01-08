package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class WrongScanActivity extends AppCompatActivity {

    private EditText qrInput;
    private Button scanBtn;
    private TableLayout tableLayout;

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ApiClient apiClient;
    private int userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_scan);

        qrInput = findViewById(R.id.qr_code_wrong);
        scanBtn = findViewById(R.id.scanBtnWrong);
        tableLayout = findViewById(R.id.wrongScanTable);

        apiClient = ApiClient.getInstance();

        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);

        scanBtn.setOnClickListener(v -> startQRScanner());
        addHoneywellTextWatcher();

        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String scanned = qrInput.getText().toString().trim();
            if (!scanned.isEmpty()) {
                qrInput.setText("");
                processWrongScan(scanned);
            }
            return true;
        });

        qrInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String scanned = qrInput.getText().toString().trim();
                if (!scanned.isEmpty()) {
                    qrInput.setText("");
                    processWrongScan(scanned);
                }
            }
        });

        loadLastWrongScans();
    }

    private void addHoneywellTextWatcher() {
        final Handler handler = new Handler();
        final Runnable processScanRunnable = () -> {
            String code = qrInput.getText().toString().trim();
            if (!code.isEmpty()) {
                processWrongScan(code);
                qrInput.setText("");
            }
        };

        qrInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(processScanRunnable);
                if (s.length() > 6) {
                    handler.postDelayed(processScanRunnable, 300);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scanned = result.getContents();
                    qrInput.setText("");
                    processWrongScan(scanned);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void processWrongScan(String qrCode) {
        if (qrCode.isEmpty()) return;

        RequestBody body = new FormBody.Builder()
                .add("qr_code", qrCode)
                .add("user_id", String.valueOf(userId))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "wrong_scan_process.php")
                .post(body)
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(WrongScanActivity.this, "Network error", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resp = response.body().string();
                try {
                    JSONObject json = new JSONObject(resp);
                    String status = json.optString("status");
                    String message = json.optString("message");
                    String bucketName = json.optString("bucket_name");
                    String variety = json.optString("variety");

                    runOnUiThread(() -> {
                        Toast.makeText(
                                WrongScanActivity.this,
                                message + "\nBucket: " + bucketName + "\nVariety: " + variety,
                                Toast.LENGTH_LONG
                        ).show();
                        qrInput.requestFocus();
                        if ("success".equals(status)) {
                            loadLastWrongScans();
                        }
                    });
                } catch (JSONException e) {
                    runOnUiThread(() ->
                            Toast.makeText(WrongScanActivity.this, "Invalid response", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }


    private void loadLastWrongScans() {
        Request request = new Request.Builder()
                .url(BASE_URL + "wrong_scan_last10.php") // 🔹 optional endpoint to load last wrong scans
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String html = response.body().string();
                runOnUiThread(() -> {
                    tableLayout.removeAllViews();
                    // TODO: parse HTML or use WebView if needed
                });
            }
        });
    }
}
