package com.yourapp.summers;

import android.annotation.SuppressLint;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.yourapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.*;

public class ScanBackSummerActivity extends AppCompatActivity {

    private EditText qrInput, quantityInput;

    private AutoCompleteTextView varietyDropdown;

    private Button scanBtn, submitBtn, addVarietyBtn;

    private RecyclerView varietyList;

    private VarietyAdapter adapter;

    private final List<JSONObject> selectedVarieties = new ArrayList<>();

    private TextView serial, bucket, farm, length;

    private int userId;

    private String selectedVarietyName = null;

    private boolean isSelecting = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String BASE_URL =
            "https://www.aaagrowers.co.ke/inventory/summers/api/";

    private final OkHttpClient client = new OkHttpClient();

    private BroadcastReceiver honeywellScanReceiver;

    private Call varietyCall;

    private String lastQuery = "";

    private long lastSearchTime = 0;

    private boolean isProcessing = false;

    private long lastScanTime = 0;

    private static final long SCAN_DELAY = 800;

    /* ================= CAMERA ================= */

    private final ActivityResultLauncher<ScanOptions> cameraScanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScan(result.getContents().trim());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_back_summer);

        userId = getIntent().getIntExtra("user_id", -1);

        qrInput = findViewById(R.id.qr_code);

        quantityInput = findViewById(R.id.quantity);

        varietyDropdown = findViewById(R.id.variety);

        scanBtn = findViewById(R.id.scanBtn);

        submitBtn = findViewById(R.id.submitBtn);

        addVarietyBtn = findViewById(R.id.addVarietyBtn);

        varietyList = findViewById(R.id.varietyList);

        serial = findViewById(R.id.serial);

        bucket = findViewById(R.id.bucket);

        farm = findViewById(R.id.farm);

        length = findViewById(R.id.length);

        adapter = new VarietyAdapter(selectedVarieties);

        varietyList.setLayoutManager(
                new LinearLayoutManager(this)
        );

        varietyList.setAdapter(adapter);

        setupVarietyAutocomplete();

        setupHoneywellInputListener();

//        setupHoneywellScanner();
//
//        setupManualTrigger();

        scanBtn.setOnClickListener(v -> startCameraScanner());

        addVarietyBtn.setOnClickListener(v -> addVariety());

        submitBtn.setOnClickListener(v -> submitManual());
    }

    private void setupHoneywellInputListener() {

        qrInput.addTextChangedListener(new TextWatcher() {

            private Runnable runnable;

            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {}

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {

                if(runnable != null){
                    handler.removeCallbacks(runnable);
                }

                runnable = () -> {

                    String scanned =
                            qrInput.getText()
                                    .toString()
                                    .trim();

                    if(!scanned.isEmpty()){

                        Log.d("HONEYWELL_SCAN", scanned);

                        handleScan(scanned);
                    }
                };

                /*
                 * Wait briefly for scanner to finish typing
                 */
                handler.postDelayed(runnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /* ================= CAMERA ================= */

    private void startCameraScanner() {

        ScanOptions options = new ScanOptions();

        options.setPrompt("Scan QR Code");

        options.setBeepEnabled(true);

        options.setOrientationLocked(false);

        cameraScanner.launch(options);
    }

    /* ================= HONEYWELL ================= */

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupHoneywellScanner() {

        honeywellScanReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                String data = intent.getStringExtra("data");

                if(data == null){
                    data = intent.getStringExtra("com.honeywell.scan.data");
                }
            }
        };

        IntentFilter filter = new IntentFilter();

        filter.addAction("com.honeywell.scan.RESULT");

        filter.addAction("com.honeywell.aidc.action.SCAN");

        registerReceiver(honeywellScanReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(honeywellScanReceiver);
        } catch (Exception ignored) {}
    }

    /* ================= SCAN ================= */

    private void handleScan(String qr) {

        Log.d("SCANNER_DATA", qr);

        long now = System.currentTimeMillis();

        if (isProcessing) return;

        if (now - lastScanTime < SCAN_DELAY) return;

        isProcessing = true;

        lastScanTime = now;

        qrInput.setText(qr);

        processQr(qr);
    }

    /* ================= QR PROCESS ================= */

    private void processQr(String qr) {

        Map<String, String> data = parseQr(qr);

        if (!data.containsKey("serial")) {

            Toast.makeText(this,
                    "Invalid QR",
                    Toast.LENGTH_SHORT).show();

            isProcessing = false;

            return;
        }

        serial.setText("Serial: " + data.get("serial"));

        bucket.setText("Bucket: " + data.get("bucket_name"));

        farm.setText("Farm: " + data.get("farm"));

        length.setText("Length: " + data.get("length"));

        Toast.makeText(this,
                "QR Loaded",
                Toast.LENGTH_SHORT).show();

        isProcessing = false;
    }

    /* ================= ADD VARIETY ================= */

    private void addVariety() {

        String variety =
                varietyDropdown.getText().toString().trim();

        String qty =
                quantityInput.getText().toString().trim();

        if (variety.isEmpty() || qty.isEmpty()) {

            Toast.makeText(this,
                    "Enter variety and quantity",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        try {

            JSONObject obj = new JSONObject();

            obj.put("variety_name", variety);

            obj.put("quantity", qty);

            selectedVarieties.add(obj);

            adapter.notifyDataSetChanged();

            varietyDropdown.setText("");

            quantityInput.setText("");

        } catch (Exception ignored) {}
    }

    /* ================= SUBMIT ================= */

    private void submitManual() {

        String qr = qrInput.getText().toString().trim();

        if (qr.isEmpty()) {

            Toast.makeText(this,
                    "Scan QR first",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        if (selectedVarieties.isEmpty()) {

            Toast.makeText(this,
                    "Add varieties first",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        sendToServer(qr);
    }

    private void sendToServer(String qr) {

        Map<String, String> qrData = parseQr(qr);

        JSONArray arr = new JSONArray(selectedVarieties);

        RequestBody body = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            body = new FormBody.Builder()

                    .add("serial",
                            qrData.getOrDefault("serial", ""))

                    .add("bucket_name",
                            qrData.getOrDefault("bucket_name", ""))

                    .add("farm",
                            qrData.getOrDefault("farm", ""))

                    .add("length",
                            qrData.getOrDefault("length", ""))

                    .add("user_id",
                            String.valueOf(userId))

                    .add("source", "floor")

                    .add("varieties_json",
                            arr.toString())

                    .build();
        }

        Request request = new Request.Builder()
                .url(BASE_URL + "scan_back_summer_process.php")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call,
                                  @NonNull IOException e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                ScanBackSummerActivity.this,
                                "Network Error",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call,
                                   @NonNull Response response)
                    throws IOException {

                String res = response.body().string();

                runOnUiThread(() -> {

                    try {

                        JSONObject json =
                                new JSONObject(res);

                        Toast.makeText(
                                ScanBackSummerActivity.this,
                                json.optString("message"),
                                Toast.LENGTH_LONG
                        ).show();

                        if (json.optString("status")
                                .equals("success")) {

                            selectedVarieties.clear();

                            adapter.notifyDataSetChanged();

                            clearForm();
                        }

                    } catch (Exception e) {

                        Toast.makeText(
                                ScanBackSummerActivity.this,
                                "Invalid response",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });
    }

    /* ================= AUTOCOMPLETE ================= */

    private void setupVarietyAutocomplete() {

        varietyDropdown.setThreshold(2);

        HashMap<String, JSONObject> map = new HashMap<>();

        varietyDropdown.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {}

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {}

            @Override
            public void afterTextChanged(Editable s) {

                if (isSelecting) return;

                String term =
                        s.toString().trim();

                if (term.length() < 2) return;

                long now =
                        System.currentTimeMillis();

                if (now - lastSearchTime < 500)
                    return;

                if (term.equals(lastQuery))
                    return;

                lastQuery = term;

                lastSearchTime = now;

                fetchVariety(term, map);
            }
        });

        varietyDropdown.setOnItemClickListener((parent,
                                                view,
                                                position,
                                                id) -> {

            isSelecting = true;

            String selected =
                    (String) parent.getItemAtPosition(position);

            JSONObject obj = map.get(selected);

            if (obj != null) {

                selectedVarietyName =
                        obj.optString("Variety");

                varietyDropdown.setText(
                        selectedVarietyName,
                        false
                );
            }

            varietyDropdown.dismissDropDown();

            handler.postDelayed(() ->
                    isSelecting = false, 300);
        });
    }

    private void fetchVariety(String term,
                              HashMap<String, JSONObject> map) {

        String url =
                HttpUrl.parse(BASE_URL +
                                "search_summer_variety.php")
                        .newBuilder()
                        .addQueryParameter("term", term)
                        .build()
                        .toString();

        if (varietyCall != null) {
            varietyCall.cancel();
        }

        Request request =
                new Request.Builder()
                        .url(url)
                        .build();

        varietyCall =
                client.newCall(request);

        varietyCall.enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call,
                                  @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call,
                                   @NonNull Response response)
                    throws IOException {

                String res =
                        response.body().string();

                try {

                    JSONArray arr =
                            new JSONArray(res);

                    List<String> list =
                            new ArrayList<>();

                    map.clear();

                    for (int i = 0; i < arr.length(); i++) {

                        JSONObject o =
                                arr.getJSONObject(i);

                        String name =
                                o.optString("Variety");

                        list.add(name);

                        map.put(name, o);
                    }

                    runOnUiThread(() -> {

                        ArrayAdapter<String> adapter =
                                new ArrayAdapter<String>(
                                        ScanBackSummerActivity.this,
                                        android.R.layout.simple_dropdown_item_1line,
                                        list
                                ) {

                                    @NonNull
                                    @Override
                                    public View getView(
                                            int position,
                                            View convertView,
                                            @NonNull ViewGroup parent) {

                                        TextView tv =
                                                (TextView) super.getView(
                                                        position,
                                                        convertView,
                                                        parent
                                                );

                                        tv.setTextColor(
                                                android.graphics.Color.BLACK
                                        );

                                        return tv;
                                    }
                                };

                        varietyDropdown.setAdapter(adapter);

                        varietyDropdown.showDropDown();
                    });

                } catch (Exception ignored) {}
            }
        });
    }

    /* ================= QR PARSER ================= */

    private Map<String, String> parseQr(String qr) {

        Map<String, String> map = new HashMap<>();

        try {

            qr = qr.replace("\n", "|")
                    .replace("\r", "|")
                    .trim();

            String[] parts = qr.split("\\|");

            for(String part : parts){

                String clean = part.trim();

                if(clean.toLowerCase().startsWith("serial:")){

                    map.put(
                            "serial",
                            clean.substring(clean.indexOf(":") + 1).trim()
                    );

                } else if(clean.toLowerCase().startsWith("bucket:")){

                    map.put(
                            "bucket_name",
                            clean.substring(clean.indexOf(":") + 1).trim()
                    );

                } else if(clean.toLowerCase().startsWith("farm:")){

                    map.put(
                            "farm",
                            clean.substring(clean.indexOf(":") + 1).trim()
                    );

                } else if(clean.toLowerCase().startsWith("length:")){

                    map.put(
                            "length",
                            clean.substring(clean.indexOf(":") + 1).trim()
                    );
                }
            }

        } catch (Exception e) {

            Log.e("QR_PARSE", e.toString());
        }

        return map;
    }

    /* ================= CLEAR ================= */

    private void clearForm() {

        qrInput.setText("");

        quantityInput.setText("");

        varietyDropdown.setText("");

        serial.setText("Serial:");

        bucket.setText("Bucket:");

        farm.setText("Farm:");

        length.setText("Length:");

        selectedVarieties.clear();

        adapter.notifyDataSetChanged();
    }

    /* ================= MANUAL INPUT ================= */

    private void setupManualTrigger() {

        qrInput.setOnEditorActionListener((v,
                                           actionId,
                                           event) -> {

            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null &&
                            event.getKeyCode() ==
                                    KeyEvent.KEYCODE_ENTER)) {

                String qr =
                        qrInput.getText()
                                .toString()
                                .trim();

                handleScan(qr);

                return true;
            }

            return false;
        });
    }
}