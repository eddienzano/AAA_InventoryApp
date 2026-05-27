package com.yourapp.summers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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

public class SummersScanOutActivity extends AppCompatActivity {

    TextView serialText, statusText;
    Button scanBtn, confirmBtn, submitPartialBtn;
    RecyclerView recycler;
    EditText qrInput;

    String BASE_URL = "https://www.aaagrowers.co.ke/inventory/summers/api/";
    String currentSerial = "";

    OkHttpClient client = new OkHttpClient();
    Call activeCall;

    List<BucketItem> itemList = new ArrayList<>();
    BucketAdapter adapter;

    int userId = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isProcessingQR = false;
    private long lastRequestTime = 0;
    private static final long REQUEST_DELAY_MS = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanout);

        serialText = findViewById(R.id.serial);
        statusText = findViewById(R.id.statusText);
        scanBtn = findViewById(R.id.scanBtn);
        confirmBtn = findViewById(R.id.confirmBtn);
        submitPartialBtn = findViewById(R.id.submitPartialBtn);
        recycler = findViewById(R.id.itemsRecycler);
        qrInput = findViewById(R.id.qrInput);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BucketAdapter(itemList);
        recycler.setAdapter(adapter);

        scanBtn.setOnClickListener(v -> startScanner());
        confirmBtn.setOnClickListener(v -> fullScanOut());
        submitPartialBtn.setOnClickListener(v -> submitPartial());

        confirmBtn.setEnabled(false);
        submitPartialBtn.setVisibility(View.GONE);

        /* ================= HONEYWELL INPUT ================= */
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
                        handleScan(scanned);
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
                handleScan(text);
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

    /* ================= HANDLE SCAN ================= */

    private void handleScan(String text) {

        long now = System.currentTimeMillis();

        if (isProcessingQR) return;
        if (now - lastRequestTime < REQUEST_DELAY_MS) return;

        isProcessingQR = true;
        lastRequestTime = now;

        parseQR(text);
    }

    /* ================= CAMERA ================= */

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {

                if (result.getContents() != null) {
                    handleScan(result.getContents());
                } else {
                    isProcessingQR = false;
                    statusText.setText("Scan cancelled");
                }
            });

    private void startScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Bucket");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        scanner.launch(options);
    }

    /* ================= PARSE ================= */

    private void parseQR(String text) {

        try {
            text = text.replace("\u0000", "");
            text = text.replaceAll("[^\\x20-\\x7E|:]", "");
            text = text.replaceAll("[\\r\\n]", "").trim();

            for (String p : text.split("\\|")) {

                p = p.trim().toLowerCase(Locale.ROOT);

                if (p.startsWith("serial")) {

                    String[] sp = p.split(":");

                    if (sp.length > 1) {
                        currentSerial = sp[1].trim();
                        serialText.setText("Serial: " + currentSerial);

                        clearPreviousData();
                        checkBucketAndLoadItems();
                        return;
                    }
                }
            }

            isProcessingQR = false;
            statusText.setText("Invalid QR");

        } catch (Exception e) {
            isProcessingQR = false;
            statusText.setText("QR Parse Error");
        }
    }

    private void clearPreviousData() {
        itemList.clear();
        adapter.notifyDataSetChanged();
        statusText.setText("Loading...");
        confirmBtn.setEnabled(false);
        submitPartialBtn.setVisibility(View.GONE);
    }

    /* ================= CHECK ================= */

    private void checkBucketAndLoadItems() {

        cancelActiveCall();

        HttpUrl url = HttpUrl.parse(BASE_URL + "check_bucket_status_out.php")
                .newBuilder()
                .addQueryParameter("serial", currentSerial)
                .build();

        activeCall = client.newCall(new Request.Builder().url(url).build());

        activeCall.enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    isProcessingQR = false;
                    statusText.setText("Network error");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                String res = response.body().string();

                try {
                    JSONObject json = new JSONObject(res);

                    String status = json.optString("status");
                    String message = json.optString("message");

                    if (!status.equals("ok")) {
                        runOnUiThread(() -> {
                            isProcessingQR = false;
                            statusText.setText(message);
                        });
                        return;
                    }

                    runOnUiThread(() -> statusText.setText(message));
                    loadBucketItems();

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        isProcessingQR = false;
                        statusText.setText("Response error");
                    });
                }
            }
        });
    }

    private void loadBucketItems() {

        String url = BASE_URL + "get_bucket_items.php?serial=" + currentSerial;

        client.newCall(new Request.Builder().url(url).build())
                .enqueue(new Callback() {

                    @Override public void onFailure(@NonNull Call c, @NonNull IOException e) {}

                    @Override
                    public void onResponse(@NonNull Call c, @NonNull Response r) throws IOException {

                        try {
                            JSONObject obj = new JSONObject(r.body().string());
                            JSONArray arr = obj.getJSONArray("items");

                            itemList.clear();

                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);

                                itemList.add(new BucketItem(
                                        o.getString("variety_name"),
                                        o.getInt("quantity")
                                ));
                            }

                            runOnUiThread(() -> {
                                isProcessingQR = false;
                                adapter.notifyDataSetChanged();
                                confirmBtn.setEnabled(true);
                                submitPartialBtn.setVisibility(View.VISIBLE);
                                statusText.setText("Ready (" + itemList.size() + ")");
                            });

                        } catch (Exception e) {
                            runOnUiThread(() -> isProcessingQR = false);
                        }
                    }
                });
    }

    /* ================= PARTIAL ================= */

    private void submitPartial() {

        JSONArray selected = new JSONArray();

        for (BucketItem item : adapter.getItems()) {

            if (item.enteredQty > 0 && item.enteredQty <= item.maxQty) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("variety_name", item.variety);
                    obj.put("quantity", item.enteredQty);
                    selected.put(obj);
                } catch (Exception ignored) {}
            }
        }

        if (selected.length() == 0) {
            Toast.makeText(this, "No valid quantities", Toast.LENGTH_SHORT).show();
            return;
        }

        sendBatch(selected);
    }

    private void sendBatch(JSONArray items) {

        RequestBody body = new FormBody.Builder()
                .add("serial", currentSerial)
                .add("user_id", String.valueOf(userId))
                .add("items", items.toString())
                .build();

        client.newCall(new Request.Builder()
                .url(BASE_URL + "scanout_process_batch.php")
                .post(body)
                .build()).enqueue(new Callback() {

            @Override public void onFailure(@NonNull Call c, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response r) {
                runOnUiThread(() -> {
                    Toast.makeText(SummersScanOutActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
            }
        });
    }

    /* ================= FULL ================= */

    private void fullScanOut() {

        RequestBody body = new FormBody.Builder()
                .add("serial", currentSerial)
                .add("user_id", String.valueOf(userId))
                .add("type", "COMPLETE")
                .build();

        client.newCall(new Request.Builder()
                .url(BASE_URL + "scanout_process.php")
                .post(body)
                .build()).enqueue(new Callback() {

            @Override public void onFailure(@NonNull Call c, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response r) {
                runOnUiThread(() -> {
                    Toast.makeText(SummersScanOutActivity.this, "Full scan complete", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
            }
        });
    }

    /* ================= RESET ================= */

    private void resetUI() {

        serialText.setText("Serial:");
        statusText.setText("Scan next bucket");

        itemList.clear();
        adapter.notifyDataSetChanged();

        submitPartialBtn.setVisibility(View.GONE);
        confirmBtn.setEnabled(false);

        currentSerial = "";
        isProcessingQR = false;

        qrInput.requestFocus();
    }

    private void cancelActiveCall() {
        if (activeCall != null) activeCall.cancel();
    }
}