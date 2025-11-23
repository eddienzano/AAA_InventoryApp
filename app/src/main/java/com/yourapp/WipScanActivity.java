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

import org.json.JSONObject;

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

        // Honeywell auto-scan
        addHoneywellTextWatcher();

        // Submit button
        btnSubmit.setOnClickListener(v -> {
            String qr = etQr.getText().toString().trim();
            if (qr.isEmpty()) {
                showAlert("Error", "Please scan a QR code.");
            } else {
                sendQrToServer(qr);
            }
        });

        // Open Camera button
        btnCamera.setOnClickListener(v -> startQRScanner());

        // Optional: editor action + focus change
        etQr.setOnEditorActionListener((v, actionId, event) -> {
            String scanned = etQr.getText().toString().trim();
            if (!scanned.isEmpty()) {
                etQr.setText("");
                sendQrToServer(scanned);
            }
            return true;
        });

        etQr.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String scanned = etQr.getText().toString().trim();
                if (!scanned.isEmpty()) {
                    etQr.setText("");
                    sendQrToServer(scanned);
                }
            }
        });
    }

    // Honeywell EDA auto-scan support
    private void addHoneywellTextWatcher() {
        final Handler handler = new Handler();
        final Runnable processScanRunnable = () -> {
            String code = etQr.getText().toString().trim();
            if (!code.isEmpty()) {
                etQr.setText("");
                sendQrToServer(code);
            }
        };

        etQr.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(processScanRunnable);
                if (s.length() > 6) { // threshold
                    handler.postDelayed(processScanRunnable, 30);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // Camera scanner using JourneyApps
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scanned = result.getContents();
                    etQr.setText("");
                    sendQrToServer(scanned);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    // Send QR to server (validation + save)
    private void sendQrToServer(String qr) {
        String url = "https://www.aaagrowers.co.ke/inventory/wip_validate.php";

        StringRequest request = new StringRequest(
                Request.Method.POST,
                url,
                response -> {
                    android.util.Log.d(TAG, "Validation response JSON:\n" + response);
                    parseServerResponse(response);
                },
                error -> {
                    android.util.Log.e(TAG, "Validation network error: " + error.toString());
                    showAlert("Error", "Network error: " + error.toString());
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("qr", qr);
                params.put("api", "1");
                android.util.Log.d(TAG, "Validation payload sent:\n" + params.toString());
                return params;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void parseServerResponse(String response) {
        try {
            JSONObject obj = new JSONObject(response);
            String status = obj.getString("status");
            String message = obj.getString("message");

            if (status.equals("success")) {
                JSONObject data = obj.getJSONObject("data");

                String qr = data.getString("qr"); // ID only
                String stems = data.optString("stems", "");
                String length = data.optString("length", "");
                String farm = data.optString("farm", "");
                String varietyId = data.getString("variety_id");
                String buncherId = data.getString("buncher_id");

                saveToServer(qr, stems, length, farm, varietyId, buncherId);
            } else {
                showAlert("Error", message);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Validation JSON parse error: " + e.toString());
            showAlert("Error", "Invalid validation response");
        }
    }

    private void saveToServer(String qr, String stems, String length, String farm, String varietyId, String buncherId) {
        String url = "https://www.aaagrowers.co.ke/inventory/wip_save.php";

        StringRequest request = new StringRequest(
                Request.Method.POST,
                url,
                response -> {
                    android.util.Log.d(TAG, "Save response JSON:\n" + response);
                    try {
                        JSONObject obj = new JSONObject(response);
                        showAlert(
                                obj.getString("status").equals("success") ? "Success" : "Error",
                                obj.getString("message")
                        );
                    } catch (Exception ex) {
                        android.util.Log.e(TAG, "Save JSON parse error: " + ex.toString());
                        showAlert("Error", "Invalid save response");
                    }
                },
                error -> {
                    android.util.Log.e(TAG, "Save network error: " + error.toString());
                    showAlert("Error", "Network error: " + error.toString());
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("qr", qr);
                params.put("stems", stems);
                params.put("length", length);
                params.put("farm", farm);
                params.put("variety_id", varietyId);
                params.put("buncher_id", buncherId);
                params.put("api", "1");
                android.util.Log.d(TAG, "Save payload sent:\n" + params.toString());
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
