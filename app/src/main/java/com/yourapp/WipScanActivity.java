package com.yourapp;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.HashMap;
import java.util.Map;

public class WipScanActivity extends AppCompatActivity {

    private static final String TAG = "WipScanDebug";

    private EditText etQr;
    private Button btnSubmit, btnCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wip_scan);

        etQr = findViewById(R.id.etQr);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCamera = findViewById(R.id.btnCamera);

        addHoneywellTextWatcher();

        btnSubmit.setOnClickListener(v -> scanQrFromField());
        btnCamera.setOnClickListener(v -> startQRScanner());

        etQr.setOnEditorActionListener((v, actionId, event) -> { scanQrFromField(); return true; });
        etQr.setOnFocusChangeListener((v, hasFocus) -> { if(!hasFocus) scanQrFromField(); });
    }

    private void scanQrFromField() {
        String qr = etQr.getText().toString().trim();
        if (!qr.isEmpty()) {
            etQr.setText("");
            validateAndSaveQr(qr);
        } else {
            showAlert("Error", "Please scan a QR code.");
        }
    }

    private void addHoneywellTextWatcher() {
        final Handler handler = new Handler();
        final Runnable processScanRunnable = () -> scanQrFromField();

        etQr.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(processScanRunnable);
                if (s.length() > 6) handler.postDelayed(processScanRunnable, 30);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    etQr.setText("");
                    validateAndSaveQr(result.getContents());
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    /** Validate and save QR in one step with full logging */
    private void validateAndSaveQr(String qr) {
        String url = "https://www.aaagrowers.co.ke/inventory/wip/wip_validate.php";

        // Log every attempt in Logcat
        android.util.Log.d(TAG, "Attempting scan for QR: " + qr);

        StringRequest request = new StringRequest(
                Request.Method.POST,
                url,
                response -> {
                    android.util.Log.d(TAG, "Server response for QR " + qr + ":\n" + response);
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(response);
                        String status = obj.getString("status");
                        String message = obj.getString("message");

                        // Show alert
                        showAlert(status.equals("success") ? "Success" : "Error", message);

                        // Log outcome
                        android.util.Log.d(TAG, "QR " + qr + " scan " + status.toUpperCase() + ": " + message);

                    } catch (Exception e) {
                        android.util.Log.e(TAG, "JSON parse error for QR " + qr + ": " + e.toString());
                        showAlert("Error", "Invalid response from server");
                    }
                },
                error -> {
                    // Full Volley error logging
                    android.util.Log.e(TAG, "Volley network error for QR " + qr, error);

                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        String body = "";
                        try { body = new String(error.networkResponse.data, "UTF-8"); }
                        catch (Exception e) { body = "Unable to parse response body"; }
                        android.util.Log.e(TAG, "HTTP Status: " + statusCode + " | Response body: " + body);
                    }

                    showAlert("Error", "Network error: " + error.toString());
                }
        ) {
            @Override
            protected Map<String,String> getParams() {
                Map<String,String> params = new HashMap<>();
                params.put("qr", qr);
                params.put("api", "1");
                return params;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> etQr.setText(""))
                .show();
    }
}
