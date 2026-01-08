package com.yourapp;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends Activity {

    private static final String BASE_URL =
            "https://www.aaagrowers.co.ke/inventory/api/scan_receive.php";

    private static final int MAX_IMMEDIATE_RETRIES = 3;   // immediate tries per scan
    private static final long DOUBLE_SCAN_LOCK_MS = 2000; // 2s lockout window
    private static final long SCAN_THRESHOLD = 40;        // Honeywell burst threshold

    private EditText input;
    private Button btnCamera;
    private TextView tvRecent;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Tone + vibration
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
    private Vibrator vibrator;

    // Honeywell detection
    private long lastCharTime = 0;
    private StringBuilder scanBuffer = new StringBuilder();

    // Duplicate prevention
    private String lastScannedValue = "";
    private long lastScannedAt = 0L;

    // Recent scans list
    private final Deque<String> recentDeque = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set XML layout
        setContentView(R.layout.activity_scan);

        // Get system services
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Find views from XML
        input = findViewById(R.id.inputScan);
        btnCamera = findViewById(R.id.btnCameraScan);
        tvRecent = new TextView(this); // We'll attach recent scans below dynamically

        // Create ScrollView for recent scans
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 450));
        scroll.setPadding(0, 20, 0, 0);

        tvRecent.setLineSpacing(6f, 1.1f);
        scroll.addView(tvRecent);

        // Add ScrollView to root container
        LinearLayout root = findViewById(R.id.rootContainer);
        root.addView(scroll);

        // Camera scan button
        btnCamera.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setPrompt("Scan QR Code");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        });

        // Honeywell / keyboard wedge input
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(Editable s) {
                long now = System.currentTimeMillis();
                String text = s.toString();

                // Fast characters = scanner
                if (now - lastCharTime < SCAN_THRESHOLD) {
                    scanBuffer.append(text);
                } else {
                    scanBuffer.setLength(0);
                    scanBuffer.append(text);
                }
                lastCharTime = now;

                // End with Enter?
                if (text.endsWith("\n") || text.endsWith("\r")) {
                    String cleaned = text.trim();
                    input.setText("");
                    scanBuffer.setLength(0);
                    if (!cleaned.isEmpty()) submitIfAllowed(cleaned);
                    return;
                }

                // Auto-detect scan burst
                if (scanBuffer.length() >= 6) {
                    mainHandler.removeCallbacks(bufferFinish);
                    mainHandler.postDelayed(bufferFinish, 80);
                }

                if (text.length() > 128) {
                    input.setText("");
                    scanBuffer.setLength(0);
                }
            }
        });
    }


    // Scanner burst complete
    private final Runnable bufferFinish = () -> {
        String s = scanBuffer.toString().trim();
        scanBuffer.setLength(0);
        input.setText("");
        if (!s.isEmpty()) submitIfAllowed(s);
    };

    // Prevent duplicates
    private void submitIfAllowed(String qr) {
        long now = System.currentTimeMillis();
        if (qr.equals(lastScannedValue) && (now - lastScannedAt) < DOUBLE_SCAN_LOCK_MS) {
            showToast("Duplicate scan ignored");
            return;
        }
        lastScannedValue = qr;
        lastScannedAt = now;

        doScan(qr);
    }

    // Add recent scans
    private void addRecent(String qr, String status) {
        runOnUiThread(() -> {
            String line = qr + " — " + status;

            if (recentDeque.size() >= 10)
                recentDeque.removeLast();
            recentDeque.addFirst(line);

            StringBuilder sb = new StringBuilder();
            for (String s : recentDeque) {
                sb.append(s).append("\n\n");
            }
            tvRecent.setText(sb.toString().trim());
        });
    }


    // Scan flow
    private void doScan(String qrCode) {
        addRecent(qrCode, "sending…");  // now safe on UI thread

        executor.execute(() -> {
            boolean ok = tryImmediateUpload(qrCode);

            if (ok) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK);
                if (vibrator != null) vibrator.vibrate(80);

                addRecent(qrCode, "success");
            } else {
                addRecent(qrCode, "failed");
            }
        });
    }


    // Retry logic
    private boolean tryImmediateUpload(String qrCode) {
        for (int attempt = 1; attempt <= MAX_IMMEDIATE_RETRIES; attempt++) {
            try {
                URL url = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String postData =
                        "qr_code=" + URLEncoder.encode(qrCode, "UTF-8") +
                                "&user_id=" + URLEncoder.encode("android_user", "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream is = conn.getInputStream();
                    java.util.Scanner sc = new java.util.Scanner(is).useDelimiter("\\A");
                    String json = sc.hasNext() ? sc.next() : "";
                    JSONObject obj = new JSONObject(json);

                    boolean success = obj.optBoolean("success", false);
                    String msg = obj.optString("message", success ? "OK" : "Failed");

                    showToast(msg);

                    return success;
                }

            } catch (Exception ignored) {}

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    private void showToast(String msg) {
        mainHandler.post(() -> {
            Toast t = Toast.makeText(ScanActivity.this, msg, Toast.LENGTH_SHORT);
            t.setGravity(Gravity.CENTER, 0, 0);
            t.show();
        });
    }

    // ZXing result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {
            String content = result.getContents();
            if (content != null) {
                submitIfAllowed(content.trim());
            } else {
                showToast("Scan cancelled");
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tone.release();
        executor.shutdownNow();
    }
}
