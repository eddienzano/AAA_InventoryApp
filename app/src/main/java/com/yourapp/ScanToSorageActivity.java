package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ScanToSorageActivity extends AppCompatActivity {

    private boolean isProcessing = false;

    private EditText qrInput;
    private Button scanBtn;
    private TableLayout tableLayout;

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ApiClient apiClient;
    private int userId; // ✅ Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_out);

        qrInput = findViewById(R.id.qr_code_out);
        scanBtn = findViewById(R.id.scanBtnOut);
        tableLayout = findViewById(R.id.scanoutTable);

        apiClient = ApiClient.getInstance();

        // ✅ Get user_id from intent (passed from LoginActivity)
        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);

        // Camera scanner button
        scanBtn.setOnClickListener(v -> startQRScanner());

        // ✅ Honeywell EDA51 keyboard wedge support
        addHoneywellTextWatcher();

        // Optional: editor action + focus change
        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String scanned = qrInput.getText().toString().trim();
            if (!scanned.isEmpty()) {
                qrInput.setText("");
                processScan(scanned);
            }
            return true;
        });

        qrInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String scanned = qrInput.getText().toString().trim();
                if (!scanned.isEmpty()) {
                    qrInput.setText("");
                    processScan(scanned);
                }
            }
        });

        loadLastScans();
    }

    private void addHoneywellTextWatcher() {
        final Handler handler = new Handler();
        final Runnable processScanRunnable = () -> {
            String code = qrInput.getText().toString().trim();
            if (!code.isEmpty()) {
                processScan(code);
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
                    processScan(scanned);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void processScan(String qrCode) {

        if (qrCode.isEmpty()) return;

        // 🚫 prevent multiple requests
        if (isProcessing) return;
        isProcessing = true;

        RequestBody body = new FormBody.Builder()
                .add("qr_code", qrCode)
                .add("user_id", String.valueOf(userId))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "api/scan_storage_process.php")
                .post(body)
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ScanToSorageActivity.this,
                            "Network error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    isProcessing = false; // ✅ unlock
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String resp = response.body().string();

                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp);
                        String message = json.optString("message");

                        Toast.makeText(ScanToSorageActivity.this,
                                message,
                                Toast.LENGTH_LONG).show();

                    } catch (JSONException e) {
                        Toast.makeText(ScanToSorageActivity.this,
                                "Invalid response",
                                Toast.LENGTH_SHORT).show();
                    }

                    qrInput.requestFocus();
                    isProcessing = false; // ✅ unlock after server reply
                });
            }
        });
    }


    private void loadLastScans() {
        Request request = new Request.Builder()
                .url(BASE_URL + "scanout_last10.php")
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
