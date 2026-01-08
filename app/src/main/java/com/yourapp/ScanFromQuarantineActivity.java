package com.yourapp;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class ScanFromQuarantineActivity extends AppCompatActivity {

    private EditText etQrInput, etQuantity;
    private Button btnSubmit, btnCameraScan;
    private RecyclerView rvQrList;

    private ArrayList<QrItemQuarantine> scanned = new ArrayList<>();
    private QrAdapterQuarantine adapter;

    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";
    private int userId = 1; // TODO: replace with actual logged-in user ID

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_from_quarantine);

        etQrInput = findViewById(R.id.etQrInput);
        etQuantity = findViewById(R.id.etQuantity);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCameraScan = findViewById(R.id.btnCameraScan);
        rvQrList = findViewById(R.id.rvQrList);

        // Initialize adapter
        adapter = new QrAdapterQuarantine(scanned);
        rvQrList.setLayoutManager(new LinearLayoutManager(this));
        rvQrList.setAdapter(adapter);

        // Honeywell / automatic QR input detection
        etQrInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String qr = s.toString().trim();
                if (!qr.isEmpty()) {
                    handleScanWithFeedback(qr);
                }
            }
        });

        // Camera scan
        btnCameraScan.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(ScanFromQuarantineActivity.this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("Scan QR Code");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.initiateScan();
        });

        // Submit
        btnSubmit.setOnClickListener(v -> submitUpdates());
    }

    /** Handle camera result **/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            handleScanWithFeedback(result.getContents());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Add scanned QR to list with duplicate check + beep & vibration **/
    private void handleScanWithFeedback(String qr) {
        for (QrItemQuarantine item : scanned) {
            if (item.qr.equals(qr)) {
                etQrInput.setText("");
                Toast.makeText(this, "Duplicate QR", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        QrItemQuarantine item = new QrItemQuarantine();
        item.qr = qr;
        scanned.add(item);
        adapter.notifyDataSetChanged();

        // Clear input for next scan
        etQrInput.setText("");

        // Beep sound
        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);

        // Vibrate (requires permission: VIBRATE)
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(100); // 100ms
    }

    /** Submit updates to server with user_id **/
    private void submitUpdates() {
        String quantityStr = etQuantity.getText().toString().trim();
        if (TextUtils.isEmpty(quantityStr)) {
            Toast.makeText(this, "Enter quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty = Integer.parseInt(quantityStr);
        if (scanned.isEmpty()) {
            Toast.makeText(this, "No scanned QRs", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray qrs = new JSONArray();
            for (QrItemQuarantine item : scanned) {
                JSONObject o = new JSONObject();
                o.put("qr", item.qr);
                o.put("quantity", qty);
                qrs.put(o);
            }

            JSONObject data = new JSONObject();
            data.put("items", qrs);
            data.put("user_id", userId); // send user_id for modified_by

            String url = BASE_URL + "scan_from_quarantine.php";

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    data,
                    response -> {
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_LONG).show();
                        scanned.clear();
                        adapter.notifyDataSetChanged();
                    },
                    error -> Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show()
            );

            queue.add(req);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Submission failed", Toast.LENGTH_LONG).show();
        }
    }
}
