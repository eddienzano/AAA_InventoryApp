package com.yourapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.*;

public class RejectActivity extends AppCompatActivity {

    private Spinner spinnerFarm;
    private AutoCompleteTextView autoVariety;
    private Button btnStartRejections, btnNextReason, btnSubmitAll;
    private EditText edtCurrentStems;
    private CardView cardCurrentReason;
    private CardView cardComments;
    private EditText edtComments;
    private Button btnCommentsNext;

    private Button btnPrevReason;

    private ArrayList<RejectionEntry2> tempRejections = new ArrayList<>();
    private int currentReasonIndex = 0;
    private List<String> rejectionReasons = new ArrayList<>();
    private HashMap<String, Integer> farmMap = new HashMap<>();
    private HashMap<String, Integer> reasonMap = new HashMap<>();

    private int selectedVarietyId = -1;
    private String selectedVarietyName = "";
    private String selectedGreenhouseName = "";
    private String rejectionComments = "";

    private OkHttpClient client = new OkHttpClient();
    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ActivityResultLauncher<Intent> photoLauncher;
    private Bitmap capturedPhoto;

    private ProgressDialog progressDialog;

    // NEW: queue processing guards
    private volatile boolean processingQueue = false;
    private final Set<String> inflight = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reject);

        spinnerFarm = findViewById(R.id.spinnerFarm);
        autoVariety = findViewById(R.id.autoVariety);
        btnStartRejections = findViewById(R.id.btnStartRejections);
        cardCurrentReason = findViewById(R.id.cardCurrentReason);
        edtCurrentStems = findViewById(R.id.edtCurrentStems);
        btnNextReason = findViewById(R.id.btnNextReason);
        btnSubmitAll = findViewById(R.id.btnSubmitAll);
        cardComments = findViewById(R.id.cardComments);
        edtComments = findViewById(R.id.edtComments);
        btnCommentsNext = findViewById(R.id.btnCommentsNext);

        btnCommentsNext.setOnClickListener(v -> showSummaryDialog());

        btnPrevReason = findViewById(R.id.btnPrevReason);

        btnPrevReason.setOnClickListener(v -> goToPrevReason());


        loadFarms();
        loadReasons();
        setupVarietyAutoComplete();

        btnStartRejections.setOnClickListener(v -> startRejectionWizard());
        btnNextReason.setOnClickListener(v -> goToNextReason());
        btnSubmitAll.setOnClickListener(v -> submitAllRejections());

        photoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        capturedPhoto = (Bitmap) result.getData().getExtras().get("data");
                        submitAllRejections();
                    }
                }
        );

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Submitting Pending Batches");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        // Retry pending batches at startup
        retryPendingBatches();

        // Listen for network changes
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> retryPendingBatches());
                }
            });
        }
    }

    private void loadFarms() {
        Request request = new Request.Builder().url(BASE_URL + "get_farms.php").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    List<String> names = new ArrayList<>();
                    farmMap.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        farmMap.put(obj.getString("name"), obj.getInt("id"));
                        names.add(obj.getString("name"));
                    }
                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(RejectActivity.this,
                                R.layout.spinner_dropdown_item, names);
                        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        spinnerFarm.setAdapter(adapter);
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private void loadReasons() {
        Request request = new Request.Builder().url(BASE_URL + "get_reasons.php").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if(!response.isSuccessful()) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    rejectionReasons.clear();
                    reasonMap.clear();
                    for(int i=0;i<arr.length();i++){
                        JSONObject obj = arr.getJSONObject(i);
                        rejectionReasons.add(obj.getString("category"));
                        reasonMap.put(obj.getString("category"), obj.getInt("id"));
                    }
                } catch(Exception e){ e.printStackTrace(); }
            }
        });
    }

    private void setupVarietyAutoComplete() {
        autoVariety.setEnabled(false);
        spinnerFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVarietyId = -1;
                selectedVarietyName = "";
                selectedGreenhouseName = "";
                autoVariety.setText("");
                autoVariety.setEnabled(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        autoVariety.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String farmName = (spinnerFarm.getSelectedItem() != null) ? spinnerFarm.getSelectedItem().toString() : "";
                if (farmName.isEmpty()) return;
                Integer farmId = farmMap.get(farmName);
                if (farmId == null) return;
                loadVarieties(farmId, s.toString());
            }
        });
    }

    private void loadVarieties(int farmId, String term) {
        new Thread(() -> {
            try {
                HttpUrl url = HttpUrl.parse(BASE_URL + "search_variety.php")
                        .newBuilder()
                        .addQueryParameter("term", term.trim())
                        .addQueryParameter("farm", String.valueOf(farmId))
                        .build();

                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    JSONArray arr = new JSONArray(response.body().string());
                    List<String> displayNames = new ArrayList<>();
                    HashMap<String, JSONObject> map = new HashMap<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj.optInt("FarmId",-1) != farmId) continue;
                        String display = obj.optString("DisplayName", "");
                        if (!display.isEmpty()) {
                            displayNames.add(display);
                            map.put(display, obj);
                        }
                    }
                    runOnUiThread(() -> {
                        autoVariety.setAdapter(null);
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(RejectActivity.this, R.layout.autocomplete_item, displayNames);
                        autoVariety.setAdapter(adapter);
                        autoVariety.showDropDown();
                        autoVariety.setOnItemClickListener((parent, view, position, id) -> {
                            String selected = adapter.getItem(position);
                            if (selected != null) {
                                JSONObject obj = map.get(selected);
                                if (obj != null) {
                                    selectedVarietyId = obj.optInt("VarietyId",-1);
                                    selectedVarietyName = obj.optString("VarietyName","");
                                    selectedGreenhouseName = obj.optString("GreenhouseName","");
                                    autoVariety.setText(selected);
                                }
                            }
                        });
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startRejectionWizard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            if(spinnerFarm.getSelectedItem() == null || autoVariety.getText().toString().isEmpty()) {
                Toast.makeText(this,"Select farm and variety first",Toast.LENGTH_SHORT).show();
                return;
            }
        }
        currentReasonIndex = 0;
        cardCurrentReason.setVisibility(View.VISIBLE);
        btnStartRejections.setVisibility(Button.GONE);
        showCurrentReason();
    }

    private void showCurrentReason() {
        if (currentReasonIndex >= rejectionReasons.size()) {
            cardCurrentReason.setVisibility(View.GONE);
            cardComments.setVisibility(View.VISIBLE);
            return;
        }

        ((TextView)findViewById(R.id.tvCurrentReason))
                .setText(rejectionReasons.get(currentReasonIndex));

        // restore previous value if exists
        if (currentReasonIndex < tempRejections.size()) {
            edtCurrentStems.setText(
                    String.valueOf(tempRejections.get(currentReasonIndex).stems)
            );
        } else {
            edtCurrentStems.setText("");
        }

        // show/hide Back button
        btnPrevReason.setVisibility(
                currentReasonIndex == 0 ? View.GONE : View.VISIBLE
        );
    }


    private void goToNextReason() {
        String stemsStr = edtCurrentStems.getText().toString();
        int stems = stemsStr.isEmpty() ? 0 : Integer.parseInt(stemsStr);

        String farmName = spinnerFarm.getSelectedItem().toString();
        int farmId = farmMap.get(farmName);
        String reasonName = rejectionReasons.get(currentReasonIndex);
        int reasonId = reasonMap.get(reasonName);

        RejectionEntry2 entry = new RejectionEntry2(
                farmName, farmId,
                selectedVarietyName, selectedVarietyId,
                stems,
                reasonName, reasonId,
                selectedGreenhouseName
        );

        if (currentReasonIndex < tempRejections.size()) {
            tempRejections.set(currentReasonIndex, entry);
        } else {
            tempRejections.add(entry);
        }

        currentReasonIndex++;
        showCurrentReason();
    }

    private void goToPrevReason() {
        // save current value before going back
        String stemsStr = edtCurrentStems.getText().toString();
        int stems = stemsStr.isEmpty() ? 0 : Integer.parseInt(stemsStr);

        String farmName = spinnerFarm.getSelectedItem().toString();
        int farmId = farmMap.get(farmName);
        String reasonName = rejectionReasons.get(currentReasonIndex);
        int reasonId = reasonMap.get(reasonName);

        RejectionEntry2 entry = new RejectionEntry2(
                farmName, farmId,
                selectedVarietyName, selectedVarietyId,
                stems,
                reasonName, reasonId,
                selectedGreenhouseName
        );

        if (currentReasonIndex < tempRejections.size()) {
            tempRejections.set(currentReasonIndex, entry);
        }

        if (currentReasonIndex > 0) {
            currentReasonIndex--;
        }

        showCurrentReason();
    }


    private void showSummaryDialog() {
        rejectionComments = edtComments.getText().toString();
        StringBuilder summary = new StringBuilder();
        for (RejectionEntry2 r : tempRejections) {
            summary.append(r.rejectionReasonName).append(": ").append(r.stems).append(" stems\n");
        }
        summary.append("\nComments:\n").append(rejectionComments);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Rejections")
                .setMessage(summary.toString())
                .setPositiveButton("OK", (dialog, which) -> submitAllRejections())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ----------------- NETWORK-SAFE SUBMISSION -----------------
    private void submitAllRejections() {
        if (tempRejections.isEmpty()) {
            Toast.makeText(this, "No rejections to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitAll.setEnabled(false);

        String batchId = UUID.randomUUID().toString();
        JSONObject batch = new JSONObject();
        try {
            batch.put("batch_id", batchId);
            batch.put("comments", rejectionComments);
            JSONArray arr = new JSONArray();
            for (RejectionEntry2 r : tempRejections) {
                JSONObject obj = new JSONObject();
                obj.put("farm_id", r.farmId);
                obj.put("variety_name", r.varietyName);
                obj.put("stems", r.stems);
                obj.put("greenhouse", r.greenhouseName);
                obj.put("reason_id", r.rejectionReasonId);
                arr.put(obj);
            }
            batch.put("entries", arr);
        } catch (Exception e) { e.printStackTrace(); }

        addBatchToQueue(batch);
        tempRejections.clear();
        rejectionComments = "";
        edtComments.setText("");
        cardComments.setVisibility(View.GONE);
        btnStartRejections.setVisibility(View.VISIBLE);

        // Attempt to process queue (serially)
        retryPendingBatches();
    }

    // ------------------ QUEUE LOGIC WITH PROGRESS ------------------------
    private void addBatchToQueue(JSONObject batch) {
        try {
            SharedPreferences prefs = getSharedPreferences("reject_queue", MODE_PRIVATE);
            JSONArray queue = new JSONArray(prefs.getString("queue","[]"));
            queue.put(batch);
            prefs.edit().putString("queue", queue.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Start processing queue serially. If already processing, return immediately.
     */
    private void retryPendingBatches() {
        // prevent overlapping runs
        if (processingQueue) return;
        processingQueue = true;

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("reject_queue", MODE_PRIVATE);
                JSONArray queue = new JSONArray(prefs.getString("queue","[]"));
                int total = queue.length();
                if (total == 0) {
                    runOnUiThread(() -> {
                        btnSubmitAll.setEnabled(true);
                        progressDialog.dismiss();
                    });
                    processingQueue = false;
                    return;
                }

                // progress reset
                runOnUiThread(() -> {
                    progressDialog.setMax(total);
                    progressDialog.setProgress(0);
                    progressDialog.show();
                });

                // process serially
                AtomicInteger processed = new AtomicInteger(0);
                for (int i = 0; i < queue.length(); i++) {
                    JSONObject batch = queue.getJSONObject(i);
                    String batchId = batch.optString("batch_id", "");
                    if (batchId.isEmpty()) {
                        // skip malformed
                        processed.incrementAndGet();
                        final int p = processed.get();
                        runOnUiThread(() -> progressDialog.setProgress(p));
                        continue;
                    }

                    // if already inflight (another thread or run) skip it
                    if (inflight.contains(batchId)) {
                        // skip — but count as processed for progress UI
                        processed.incrementAndGet();
                        final int p = processed.get();
                        runOnUiThread(() -> progressDialog.setProgress(p));
                        continue;
                    }

                    // mark inflight and submit synchronously (but using okhttp async to preserve UI responsiveness).
                    inflight.add(batchId);

                    // Submit and wait for success/failure via latch-style using a blocking object.
                    final Object lock = new Object();
                    final boolean[] finished = {false};

                    submitBatchFromQueue(batch, new SubmitCallback() {
                        @Override
                        public void onSuccess() {
                            // remove from queue on success
                            removeBatchLocally(batchId);
                            inflight.remove(batchId);
                            processed.incrementAndGet();
                            final int p = processed.get();
                            runOnUiThread(() -> progressDialog.setProgress(p));
                            synchronized (lock) {
                                finished[0] = true;
                                lock.notify();
                            }
                        }

                        @Override
                        public void onFailure() {
                            // keep the batch in queue, remove inflight marker, show toast
                            inflight.remove(batchId);
                            processed.incrementAndGet(); // still advance progress so dialog continues
                            final int p = processed.get();
                            runOnUiThread(() -> {
                                progressDialog.setProgress(p);
                                Toast.makeText(RejectActivity.this,
                                        "Batch " + batchId + " failed, will retry later", Toast.LENGTH_SHORT).show();
                            });
                            synchronized (lock) {
                                finished[0] = true;
                                lock.notify();
                            }
                        }
                    });

                    // wait for callback to mark finished before next batch
                    synchronized (lock) {
                        while (!finished[0]) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                // Interrupted - continue to next
                                break;
                            }
                        }
                    }
                }

                // finished processing all batches
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    btnSubmitAll.setEnabled(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    btnSubmitAll.setEnabled(true);
                });
            } finally {
                processingQueue = false;
            }
        }).start();
    }

    /**
     * New submit that accepts a callback. Does not remove the batch from queue itself,
     * leaving that to the successful callback so there's no race.
     */
    private interface SubmitCallback {
        void onSuccess();
        void onFailure();
    }

    private void submitBatchFromQueue(JSONObject batchObj, SubmitCallback cb) {
        try {
            String batchId = batchObj.optString("batch_id");
            JSONArray entriesArr = batchObj.optJSONArray("entries");
            String comments = batchObj.optString("comments","");

            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            builder.addFormDataPart("batch_id", batchId);
            builder.addFormDataPart("comments", comments);

            if (entriesArr != null) {
                for (int j=0;j<entriesArr.length();j++){
                    JSONObject r = entriesArr.getJSONObject(j);
                    builder.addFormDataPart("farm[]", String.valueOf(r.optInt("farm_id",0)));
                    builder.addFormDataPart("variety[]", r.optString("variety_name",""));
                    builder.addFormDataPart("stems[]", String.valueOf(r.optInt("stems",0)));
                    builder.addFormDataPart("greenhouse[]", r.optString("greenhouse",""));
                    builder.addFormDataPart("rejection_reason[]", String.valueOf(r.optInt("reason_id",0)));
                }
            }

            RequestBody requestBody = builder.build();
            Request request = new Request.Builder()
                    .url(BASE_URL + "rejection_form.php")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    cb.onFailure();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // treat non-success as failure so batch remains for retry
                    if (!response.isSuccessful()) {
                        cb.onFailure();
                        return;
                    }
                    // we got success: call onSuccess
                    cb.onSuccess();
                }
            });

        } catch (Exception e){
            e.printStackTrace();
            cb.onFailure();
        }
    }

    private void removeBatchLocally(String batchId) {
        try {
            SharedPreferences prefs = getSharedPreferences("reject_queue", MODE_PRIVATE);
            JSONArray queue = new JSONArray(prefs.getString("queue","[]"));
            JSONArray newQueue = new JSONArray();
            for (int i=0;i<queue.length();i++){
                JSONObject obj = queue.getJSONObject(i);
                if (!obj.optString("batch_id","").equals(batchId)) newQueue.put(obj);
            }
            prefs.edit().putString("queue", newQueue.toString()).apply();
        } catch (Exception e){ e.printStackTrace(); }
    }

}
