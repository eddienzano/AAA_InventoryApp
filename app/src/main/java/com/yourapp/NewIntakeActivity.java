package com.yourapp;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

public class NewIntakeActivity extends AppCompatActivity {


    private Spinner spinnerColdroom;
    private AutoCompleteTextView etVariety;
    private EditText etQrInput;
    private Button btnSubmit, btnCameraScan;
    private RecyclerView rvQrList;

    private ArrayList<QrItem> scannedQRCodes = new ArrayList<>();
    private QrAdapter qrAdapter;

    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";
    private Integer selectedVarietyId = null;
    private String selectedVarietyName = null;
    private String selectedGreenhouseName = null;

    private BroadcastReceiver honeywellScanReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_intake);

        spinnerColdroom = findViewById(R.id.spinnerColdroom);
        etVariety = findViewById(R.id.etVariety);
        etQrInput = findViewById(R.id.etQrInput);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCameraScan = findViewById(R.id.btnCameraScan);
        rvQrList = findViewById(R.id.rvQrList);

        // 1️⃣ Coldroom spinner setup
        ArrayAdapter<CharSequence> coldroomAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.coldroom_labels,
                R.layout.spinner_item
        );
        coldroomAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerColdroom.setAdapter(coldroomAdapter);

// Optional: set default
        spinnerColdroom.setSelection(1);

        // 2️⃣ RecyclerView setup
        qrAdapter = new QrAdapter(scannedQRCodes);
        rvQrList.setLayoutManager(new LinearLayoutManager(this));
        rvQrList.setAdapter(qrAdapter);

        // Force enable for testing
        etQrInput.setEnabled(true);

        // 3️⃣ Coldroom + variety selection enables scanning
        spinnerColdroom.setOnItemSelectedListener(new SimpleItemSelectedListener(this::toggleScanInput));
        etVariety.setOnItemClickListener((parent, view, position, id) -> toggleScanInput());

        // 4️⃣ QR text input listener (Honeywell-ready)
        etQrInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                handleQrScan(etQrInput.getText().toString());
                return true;
            }
            return false;
        });
        // 3️⃣ Add this after the OnKeyListener:
        etQrInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().contains("|")) { // or whatever pattern your QR uses
                    handleQrScan(s.toString().trim());
                    etQrInput.setText("");
                }
            }
        });

        // 5️⃣ Camera scanner
        btnCameraScan.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(NewIntakeActivity.this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("Scan QR Code");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.initiateScan();
        });

        // 6️⃣ Autocomplete setup
        setupVarietySearch();

        setupHoneywellScanner();

        // 7️⃣ Submit button
        btnSubmit.setOnClickListener(v -> submitIntake());
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupHoneywellScanner() {
        honeywellScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String barcodeData = null;

                    // Honeywell scanners may send data under different keys
                    if (intent.hasExtra("data")) {
                        barcodeData = intent.getStringExtra("data");
                    } else if (intent.hasExtra("barcode")) {
                        barcodeData = intent.getStringExtra("barcode");
                    } else if (intent.hasExtra("decoded_string")) {
                        barcodeData = intent.getStringExtra("decoded_string");
                    }

                    if (!TextUtils.isEmpty(barcodeData)) {
                        final String scannedData = barcodeData.trim();

                        runOnUiThread(() -> {
                            // ✅ Process the scan immediately
                            handleQrScan(scannedData);

                            // Optional short confirmation
                            Toast.makeText(NewIntakeActivity.this,
                                    "✅ Scan received: " + scannedData,
                                    Toast.LENGTH_SHORT).show();

                            // Reset and focus input
                            etQrInput.setText("");
                            etQrInput.requestFocus();
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(NewIntakeActivity.this,
                            "Scan error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        // Register receiver for Honeywell intents
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.honeywell.scan.RESULT");
        filter.addAction("com.honeywell.aidc.action.SCAN");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(honeywellScanReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(honeywellScanReceiver, filter);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(honeywellScanReceiver); } catch (Exception ignored) {}
    }

    /** Enable/disable scanning **/
    private void toggleScanInput() {
        // Enable scanning once a coldroom is selected (ignore variety for enabling)
        boolean enable = spinnerColdroom.getSelectedItemPosition() > 0;
        etQrInput.setEnabled(enable);

        if (enable) {
            etQrInput.requestFocus();
        } else {
            etQrInput.clearFocus();
        }
    }


    /** Handle QR scans **/
    private void handleQrScan(String qr) {
        if (TextUtils.isEmpty(qr)) return;

        for (QrItem item : scannedQRCodes) {
            if (item.qr.equals(qr)) {
                Toast.makeText(this, "Duplicate QR: " + qr, Toast.LENGTH_SHORT).show();
                etQrInput.setText("");
                return;
            }
        }

        QrItem parsed = parseQR(qr);
        if (parsed == null) {
            Toast.makeText(this, "❌ Invalid QR format", Toast.LENGTH_SHORT).show();
            etQrInput.setText("");
            return;
        }

        scannedQRCodes.add(parsed);
        qrAdapter.notifyDataSetChanged();
        etQrInput.setText("");

        // ✅ validate just this one QR
        validateSingleQR(parsed);
    }

    private void validateSingleQR(QrItem qrItem) {
        try {
            JSONArray qrArray = new JSONArray();
            JSONObject obj = new JSONObject();
            obj.put("qr", qrItem.qr);
            qrArray.put(obj);

            JSONObject postData = new JSONObject();
            postData.put("qrcodes", qrArray);

            String url = BASE_URL + "intake_validate.php";
            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                    response -> {
                        if ("invalid".equals(response.optString("status"))) {
                            JSONObject errors = response.optJSONObject("errors");
                            if (errors != null && errors.has(qrItem.qr)) {
                                qrItem.isInvalid = true;
                                qrItem.errorMessage = errors.optString(qrItem.qr);
                                qrAdapter.notifyDataSetChanged();
                                Toast.makeText(this, "❌ Invalid: " + qrItem.errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            qrItem.isInvalid = false;
                            qrItem.errorMessage = "";
                            qrAdapter.notifyDataSetChanged();
                        }
                    },
                    error -> Toast.makeText(this, "Validation error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            queue.add(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /** Parse QR **/
    private QrItem parseQR(String qr) {
        try {
            String[] parts = qr.split("\\|");
            HashMap<String, String> map = new HashMap<>();
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) map.put(kv[0].trim().toLowerCase(), kv[1].trim());
            }
            QrItem item = new QrItem();
            item.qr = qr;
            item.serial = map.get("serial");
            item.bucket_name = map.get("bucket");
            item.farm = map.get("farm");
            item.length = map.get("length");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                item.stems = Integer.parseInt(map.getOrDefault("stems", "100"));
            }
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    /** Validate QRs via PHP **/
    private void validateQRCodes(ArrayList<QrItem> qrItems) {
        try {
            JSONArray qrArray = new JSONArray();
            for (QrItem item : qrItems) {
                JSONObject obj = new JSONObject();
                obj.put("qr", item.qr);
                qrArray.put(obj);
            }

            JSONObject postData = new JSONObject();
            postData.put("qrcodes", qrArray);

            String url = BASE_URL + "intake_validate.php";
            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                    response -> {
                        if ("invalid".equals(response.optString("status"))) {
                            JSONObject errors = response.optJSONObject("errors");
                            if (errors != null) {
                                JSONArray keys = errors.names();
                                if (keys != null) {
                                    for (int i = 0; i < keys.length(); i++) {
                                        try {
                                            String qr = keys.getString(i);
                                            String msg = errors.getString(qr);
                                            for (QrItem item : qrItems) {
                                                if (item.qr.equals(qr)) {
                                                    item.isInvalid = true;
                                                    item.errorMessage = msg;
                                                }
                                            }
                                        } catch (JSONException ignored) {}
                                    }
                                }
                            }
                            qrAdapter.notifyDataSetChanged();
                            Toast.makeText(this, "Some QRs invalid", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "All QRs valid!", Toast.LENGTH_SHORT).show();
                        }
                    },
                    error -> Toast.makeText(this, "Validation error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            queue.add(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Autocomplete setup **/
    private void setupVarietySearch() {
        etVariety.setThreshold(2);
        etVariety.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 1) {
                    String term = s.toString().trim();
                    String farm = ""; // optional: replace with actual farm field if available

                    HttpUrl url = HttpUrl.parse(BASE_URL + "search_variety.php")
                            .newBuilder()
                            .addQueryParameter("term", term)
                            .addQueryParameter("farm", farm)
                            .build();

                    okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                    ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            runOnUiThread(() ->
                                    Toast.makeText(NewIntakeActivity.this,
                                            "Error fetching varieties",
                                            Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                String resp = response.body().string();
                                JSONArray arr = new JSONArray(resp);
                                List<String> names = new ArrayList<>();
                                HashMap<String, JSONObject> map = new HashMap<>();

                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.getJSONObject(i);
                                    String display = obj.getString("DisplayName");
                                    names.add(display);
                                    map.put(display, obj);
                                }

                                runOnUiThread(() -> {
                                    ArrayAdapter<String> adapter =
                                            new ArrayAdapter<>(NewIntakeActivity.this,
                                                    R.layout.autocomplete_item, names);
                                    etVariety.setAdapter(adapter);

                                    etVariety.setOnItemClickListener((parent, view, position, id) -> {
                                        String selected = adapter.getItem(position);
                                        if (selected != null) {
                                            JSONObject obj = map.get(selected);
                                            if (obj != null) {
                                                selectedVarietyId = obj.optInt("VarietyId");
                                                selectedVarietyName = obj.optString("VarietyName");
                                                selectedGreenhouseName = obj.optString("GreenhouseName");
                                            }
                                        }
                                    });
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                runOnUiThread(() ->
                                        Toast.makeText(NewIntakeActivity.this,
                                                "Error parsing variety data",
                                                Toast.LENGTH_SHORT).show());
                            }
                        }
                    });
                }
            }
        });
    }

    /** Fetch varieties like IntakeActivity **/
    private void fetchVarietySuggestions(String term) {
        String farm = ""; // optional: adjust if you have farm input
        HttpUrl url = HttpUrl.parse(BASE_URL + "search_variety.php")
                .newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("farm", farm)
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();

        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(NewIntakeActivity.this, "Error fetching varieties", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String resp = response.body().string();
                    JSONArray arr = new JSONArray(resp);
                    List<String> names = new ArrayList<>();
                    HashMap<String, JSONObject> map = new HashMap<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String display = obj.getString("DisplayName");
                        names.add(display);
                        map.put(display, obj);
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(NewIntakeActivity.this,
                                R.layout.autocomplete_item, names);
                        etVariety.setAdapter(adapter);
                        etVariety.setOnItemClickListener((parent, view, position, id) -> {
                            String selected = adapter.getItem(position);
                            if (selected != null) {
                                JSONObject obj = map.get(selected);
                                if (obj != null) {
                                    selectedVarietyId = obj.optInt("VarietyId");
                                    selectedVarietyName = obj.optString("VarietyName");
                                    selectedGreenhouseName = obj.optString("GreenhouseName");
                                    Toast.makeText(NewIntakeActivity.this,
                                            "Selected: " + selectedVarietyName,
                                            Toast.LENGTH_SHORT).show();
                                    toggleScanInput();
                                }
                            }
                        });
                    });
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(NewIntakeActivity.this, "Error parsing variety data", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** Submit intake **/
    private void submitIntake() {
        if (selectedVarietyId == null) {
            Toast.makeText(this, "Please select a flower variety", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spinnerColdroom.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Please select a coldroom", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scannedQRCodes.isEmpty()) {
            Toast.makeText(this, "No QR codes scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText etStems = findViewById(R.id.etStems);
        String stemsInput = etStems.getText().toString().trim();
        if (TextUtils.isEmpty(stemsInput)) {
            Toast.makeText(this, "Please enter number of stems", Toast.LENGTH_SHORT).show();
            return;
        }
        int stems = Integer.parseInt(stemsInput);

        JSONArray qrArray = new JSONArray();
        try {
            for (QrItem item : scannedQRCodes) {
                JSONObject obj = new JSONObject();
                obj.put("qr", item.qr);
                obj.put("serial", item.serial);
                obj.put("bucket_name", item.bucket_name);
                obj.put("farm", item.farm);
                obj.put("length", item.length);
                qrArray.put(obj);
            }

            JSONObject postData = new JSONObject();
            postData.put("qrcodes", qrArray);
            postData.put("stems", stems);

            int pos = spinnerColdroom.getSelectedItemPosition();
            String[] coldroomValues = getResources().getStringArray(R.array.coldroom_values);
            String coldroomValue = (pos >= 0 && pos < coldroomValues.length) ? coldroomValues[pos] : "";
            postData.put("coldroom", coldroomValue);
            postData.put("variety_id", selectedVarietyId);
            postData.put("variety_name", selectedVarietyName);
            postData.put("block", selectedGreenhouseName);
            postData.put("user_id", 123); // TODO: replace with actual user session

            String url = BASE_URL + "new_intake_process.php";
            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                    response -> {
                        String status = response.optString("status");
                        String message = response.optString("message");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        if ("success".equals(status)) {
                            scannedQRCodes.clear();
                            qrAdapter.notifyDataSetChanged();
//                            btnSubmit.setEnabled(false);
                        }
                    },
                    error -> Toast.makeText(this, "Submission error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            queue.add(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** Camera result **/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            etQrInput.setText(result.getContents());
            handleQrScan(result.getContents());
        }
    }

    /** Model **/
    public static class QrItem {
        public String qr, serial, bucket_name, farm, length;
        public int stems;
        public boolean isInvalid = false;
        public String errorMessage = "";
    }
}
