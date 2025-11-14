package com.yourapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.*;

public class ScanOutReworkActivity extends AppCompatActivity {

    private EditText qrInput;
    private Button scanBtn;
    private LinearLayout historyList;
    private TextView resultBox;

    // ✅ POINT THIS TO THE REWORK SAVE PHP
    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ApiClient apiClient;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_out_wip); // reuse same layout

        qrInput = findViewById(R.id.qrInput);
        scanBtn = findViewById(R.id.scanBtn);
        resultBox = findViewById(R.id.resultBox);
        historyList = findViewById(R.id.historyList);

        apiClient = ApiClient.getInstance();

        // ✅ Honeywell / keyboard scanner support
        addHoneywellTextWatcher();

        // ✅ Camera scanner
        scanBtn.setOnClickListener(v -> startQRScanner());

        // ✅ Handle Enter key (manual scan)
        qrInput.setOnEditorActionListener((v, actionId, event) -> {
            String qr = qrInput.getText().toString().trim();
            if (!qr.isEmpty()) {
                processScan(qr);
                qrInput.setText("");
            }
            return true;
        });
    }

    private void addHoneywellTextWatcher() {
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
                if (s.length() > 6) handler.postDelayed(processScanRunnable, 300);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scanned = result.getContents().trim();
                    if (!scanned.isEmpty()) processScan(scanned);
                }
            });

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan WIP QR Code for Rework");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void processScan(String qrCode) {
        if (qrCode.isEmpty()) return;

        // Hide keyboard if visible
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(qrInput.getWindowToken(), 0);

        RequestBody body = new FormBody.Builder()
                .add("qr", qrCode)
                .build();

        // ✅ CALL THE REWORK PHP SCRIPT
        Request request = new Request.Builder()
                .url(BASE_URL + "wip_out_rework_save.php")
                .post(body)
                .build();

        apiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> showResult("❌ Network error. Try again.", false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resp = response.body().string();
                try {
                    JSONObject json = new JSONObject(resp);
                    String status = json.optString("status");
                    String message = json.optString("message");

                    runOnUiThread(() -> {
                        boolean success = "success".equals(status);
                        showResult(message, success);
                        if (success) addHistoryItem(qrCode);
                        triggerFeedback(success);
                        qrInput.requestFocus();
                    });
                } catch (JSONException e) {
                    runOnUiThread(() -> showResult("❌ Invalid server response.", false));
                }
            }
        });
    }

    private void showResult(String message, boolean success) {
        resultBox.setText(message);
        resultBox.setBackgroundResource(success ? R.drawable.bg_result_success : R.drawable.bg_result_error);
        resultBox.setAlpha(1f);
        resultBox.animate().alpha(1f).setDuration(200).start();
    }

    @SuppressLint("SetTextI18n")
    private void addHistoryItem(String qr) {
        TextView item = new TextView(this);
        item.setText(qr + "   •   " + getTimeNow());
        item.setPadding(12, 8, 12, 8);
        item.setBackgroundResource(R.drawable.bg_history_item);

        historyList.addView(item, 0); // add on top
    }

    private String getTimeNow() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void triggerFeedback(boolean success) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(success ? 100 : 300,
                        success ? VibrationEffect.DEFAULT_AMPLITUDE : 100));
            else
                vibrator.vibrate(success ? 100 : 300);
        }
    }
}
