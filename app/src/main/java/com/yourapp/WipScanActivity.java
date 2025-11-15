package com.yourapp;


import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class WipScanActivity extends AppCompatActivity {

    EditText etQr;
    Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wip_scan);

        etQr = findViewById(R.id.etQr);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> {
            String qr = etQr.getText().toString().trim();
            if (qr.isEmpty()) {
                showAlert("Error", "Please scan a QR code.");
            } else {
                sendQrToServer(qr);
            }
        });
    }

    private void sendQrToServer(String qr) {
        String url = "https://www.aaagrowers.co.ke/inventory/wip_validate.php";

        StringRequest request = new StringRequest(Request.Method.POST, url,
                response -> {
                    // The response will either be a SweetAlert redirect (HTML)
                    // or you can adjust PHP to return JSON for Android
                    parseServerResponse(response);
                },
                error -> showAlert("Error", "Network error: " + error.getMessage())
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("qr", qr);
                return params;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private void parseServerResponse(String response) {
        // If you modify PHP to return JSON, parse like:
        // {"status":"success","message":"QR Scan has been recorded."}
        if (response.contains("Saved Successfully")) {
            showAlert("Success", "QR Scan has been recorded.");
        } else if (response.contains("already been scanned")) {
            showAlert("Error", "This QR has already been scanned.");
        } else if (response.contains("not active")) {
            showAlert("Error", "QR not found or not active.");
        } else {
            showAlert("Error", "Unknown server response.");
        }
    }

    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> etQr.setText(""))
                .show();
    }

    @Override
    protected Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        params.put("qr", qr);
        params.put("api", "1"); // ensures PHP returns JSON
        return params;
    }
}

