package com.yourapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.*;

public class QualityAnalysisActivity extends AppCompatActivity {

    private Spinner spinnerFarm;
    private AutoCompleteTextView autoVariety;
    private EditText edtSampleSpace;
    private EditText edtCurrentCount;
    private EditText edtComments;

    private TextView tvCurrentReason;

    private Button btnStartAnalysis;
    private Button btnNextReason;
    private Button btnPrevReason;
    private Button btnSubmitAll;

    private CardView cardCurrentReason;
    private CardView cardSummary;

    private final List<String> analysisReasons = new ArrayList<>();
    private final HashMap<String, Integer> reasonMap = new HashMap<>();

    private final List<QualityEntry> tempEntries = new ArrayList<>();

    private final HashMap<String, Integer> farmMap = new HashMap<>();

    private int currentReasonIndex = 0;

    private int selectedVarietyId = -1;
    private String selectedVarietyName = "";
    private String selectedGreenhouseName = "";

    private final OkHttpClient client = new OkHttpClient();

    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quality_analysis);

        spinnerFarm = findViewById(R.id.spinnerFarm);
        autoVariety = findViewById(R.id.autoVariety);
        edtSampleSpace = findViewById(R.id.edtSampleSpace);

        btnStartAnalysis = findViewById(R.id.btnStartAnalysis);

        cardCurrentReason = findViewById(R.id.cardCurrentReason);

        tvCurrentReason = findViewById(R.id.tvCurrentReason);
        edtCurrentCount = findViewById(R.id.edtCurrentCount);

        btnNextReason = findViewById(R.id.btnNextReason);
        btnPrevReason = findViewById(R.id.btnPrevReason);

        cardSummary = findViewById(R.id.cardSummary);

        btnSubmitAll = findViewById(R.id.btnSubmitAll);

        edtComments = findViewById(R.id.edtComments);

        // IMPORTANT FIX
        autoVariety.setThreshold(2);

        loadFarms();
        loadAnalysisReasons();
        setupVarietyAutoComplete();

        btnStartAnalysis.setOnClickListener(v -> startAnalysisWizard());

        btnNextReason.setOnClickListener(v -> goToNextReason());

        btnPrevReason.setOnClickListener(v -> goToPrevReason());

        btnSubmitAll.setOnClickListener(v -> confirmAndSubmit());
    }

    private void loadFarms() {

        Request request = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                if (!response.isSuccessful()) return;

                try {

                    JSONArray arr = new JSONArray(response.body().string());

                    List<String> names = new ArrayList<>();

                    farmMap.clear();

                    for (int i = 0; i < arr.length(); i++) {

                        JSONObject o = arr.getJSONObject(i);

                        String name = o.getString("name");
                        int id = o.getInt("id");

                        names.add(name);

                        farmMap.put(name, id);
                    }

                    runOnUiThread(() -> {

                        if (isFinishing() || isDestroyed()) return;

                        ArrayAdapter<String> adapter =
                                new ArrayAdapter<>(
                                        QualityAnalysisActivity.this,
                                        R.layout.spinner_dropdown_item,
                                        names
                                );

                        spinnerFarm.setAdapter(adapter);
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void loadAnalysisReasons() {

        Request request = new Request.Builder()
                .url(BASE_URL + "get_analysis_reasons.php")
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                if (!response.isSuccessful()) return;

                try {

                    JSONObject root = new JSONObject(response.body().string());

                    JSONArray arr = root.getJSONArray("data");

                    analysisReasons.clear();
                    reasonMap.clear();

                    for (int i = 0; i < arr.length(); i++) {

                        JSONObject o = arr.getJSONObject(i);

                        String reason = o.getString("reason_name");

                        int id = o.getInt("id");

                        analysisReasons.add(reason);

                        reasonMap.put(reason, id);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        autoVariety.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                // IMPORTANT FIX
                if (s.length() < 2) {
                    return;
                }

                String farmName = "";

                if (spinnerFarm.getSelectedItem() != null) {
                    farmName = spinnerFarm.getSelectedItem().toString();
                }

                if (farmName.isEmpty()) return;

                Integer farmId = farmMap.get(farmName);

                if (farmId == null) return;

                loadVarieties(farmId, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

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

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {

                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }

                    String body = response.body().string();

                    JSONArray arr = new JSONArray(body);

                    List<String> displayNames = new ArrayList<>();

                    HashMap<String, JSONObject> map = new HashMap<>();

                    for (int i = 0; i < arr.length(); i++) {

                        JSONObject obj = arr.getJSONObject(i);

                        int varietyFarmId = obj.optInt("FarmId", -1);

                        if (varietyFarmId != farmId) continue;

                        String display = obj.optString("DisplayName", "");

                        if (!display.isEmpty()) {

                            displayNames.add(display);

                            map.put(display, obj);
                        }
                    }

                    runOnUiThread(() -> {

                        // IMPORTANT FIXES
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (autoVariety.getWindowToken() == null) {
                            return;
                        }

                        ArrayAdapter<String> adapter =
                                new ArrayAdapter<>(
                                        QualityAnalysisActivity.this,
                                        R.layout.autocomplete_item,
                                        displayNames
                                );

                        autoVariety.setAdapter(adapter);

                        autoVariety.setOnItemClickListener((parent, view, position, id) -> {

                            String selected = adapter.getItem(position);

                            if (selected != null) {

                                JSONObject obj = map.get(selected);

                                if (obj != null) {

                                    selectedVarietyId =
                                            obj.optInt("VarietyId", -1);

                                    selectedVarietyName =
                                            obj.optString("VarietyName", "");

                                    selectedGreenhouseName =
                                            obj.optString("GreenhouseName", "");

                                    // IMPORTANT FIX
                                    autoVariety.setText(selected, false);
                                }
                            }
                        });

                        // SAFER DROPDOWN
                        autoVariety.post(() -> {

                            if (!isFinishing()
                                    && !isDestroyed()
                                    && autoVariety.hasFocus()
                                    && autoVariety.getWindowToken() != null) {

                                try {
                                    autoVariety.showDropDown();
                                } catch (Exception ignored) {

                                }
                            }
                        });
                    });
                }

            } catch (Exception ex) {

                ex.printStackTrace();

                runOnUiThread(() -> {

                    if (!isFinishing() && !isDestroyed()) {

                        Toast.makeText(
                                QualityAnalysisActivity.this,
                                "Error loading varieties",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }

        }).start();
    }

    private void startAnalysisWizard() {

        String farmName = "";

        if (spinnerFarm.getSelectedItem() != null) {
            farmName = spinnerFarm.getSelectedItem().toString();
        }

        if (farmName.isEmpty()
                || autoVariety.getText().toString().isEmpty()) {

            Toast.makeText(
                    this,
                    "Select farm and variety first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String sample = edtSampleSpace.getText().toString();

        if (sample.isEmpty()) {

            Toast.makeText(
                    this,
                    "Enter sample space",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        int sampleSpace = Integer.parseInt(sample);

        if (sampleSpace <= 0) {

            Toast.makeText(
                    this,
                    "Sample space must be greater than 0",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        tempEntries.clear();

        currentReasonIndex = 0;

        cardCurrentReason.setVisibility(View.VISIBLE);

        btnStartAnalysis.setVisibility(View.GONE);

        showCurrentReason();
    }

    private void showCurrentReason() {

        if (currentReasonIndex >= analysisReasons.size()) {

            cardCurrentReason.setVisibility(View.GONE);

            buildAndShowSummary();

            return;
        }

        String reason = analysisReasons.get(currentReasonIndex);

        tvCurrentReason.setText(reason);

        edtCurrentCount.setText("");

        btnPrevReason.setVisibility(
                currentReasonIndex == 0
                        ? View.GONE
                        : View.VISIBLE
        );
    }

    private void goToNextReason() {

        String countStr = edtCurrentCount.getText().toString();

        int count = countStr.isEmpty()
                ? 0
                : Integer.parseInt(countStr);

        String reasonName = analysisReasons.get(currentReasonIndex);

        int reasonId = reasonMap.get(reasonName);

        QualityEntry e =
                new QualityEntry(reasonId, reasonName, count);

        if (currentReasonIndex < tempEntries.size()) {
            tempEntries.set(currentReasonIndex, e);
        } else {
            tempEntries.add(e);
        }

        currentReasonIndex++;

        showCurrentReason();
    }

    private void goToPrevReason() {

        String countStr = edtCurrentCount.getText().toString();

        int count = countStr.isEmpty()
                ? 0
                : Integer.parseInt(countStr);

        String reasonName = analysisReasons.get(currentReasonIndex);

        int reasonId = reasonMap.get(reasonName);

        QualityEntry e =
                new QualityEntry(reasonId, reasonName, count);

        if (currentReasonIndex < tempEntries.size()) {
            tempEntries.set(currentReasonIndex, e);
        } else {
            tempEntries.add(e);
        }

        if (currentReasonIndex > 0) {
            currentReasonIndex--;
        }

        showCurrentReason();

        if (currentReasonIndex < tempEntries.size()) {

            edtCurrentCount.setText(
                    String.valueOf(
                            tempEntries.get(currentReasonIndex).count
                    )
            );

        } else {

            edtCurrentCount.setText("");
        }
    }

    private void buildAndShowSummary() {

        int sampleSpace =
                Integer.parseInt(edtSampleSpace.getText().toString());

        StringBuilder sb = new StringBuilder();

        sb.append("Sample space: ")
                .append(sampleSpace)
                .append("\n\n");

        for (int i = 0; i < analysisReasons.size(); i++) {

            String rn = analysisReasons.get(i);

            QualityEntry e;

            if (i < tempEntries.size()) {

                e = tempEntries.get(i);

            } else {

                e = new QualityEntry(
                        reasonMap.get(rn),
                        rn,
                        0
                );
            }

            double pct = sampleSpace > 0
                    ? (100.0 * e.count / sampleSpace)
                    : 0.0;

            e.percentage =
                    Math.round(pct * 100.0) / 100.0;

            sb.append(rn)
                    .append(": ")
                    .append(e.count)
                    .append(" (")
                    .append(String.format("%.2f", e.percentage))
                    .append("%)\n");

            if (i < tempEntries.size()) {
                tempEntries.set(i, e);
            } else {
                tempEntries.add(e);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Summary")
                .setMessage(sb.toString())
                .setPositiveButton("OK", (d, w) ->
                        btnSubmitAll.setVisibility(View.VISIBLE))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndSubmit() {

        if (tempEntries.isEmpty()) {

            Toast.makeText(
                    this,
                    "No data to submit",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String farmName =
                spinnerFarm.getSelectedItem().toString();

        int farmId = farmMap.get(farmName);

        String variety = selectedVarietyName;

        String greenhouse = selectedGreenhouseName;

        int sampleSpace =
                Integer.parseInt(
                        edtSampleSpace.getText().toString()
                );

        String comments =
                edtComments.getText().toString();

        MultipartBody.Builder builder =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM);

        builder.addFormDataPart(
                "farm[]",
                String.valueOf(farmId)
        );

        builder.addFormDataPart(
                "farm_name",
                farmName
        );

        builder.addFormDataPart(
                "variety[]",
                variety
        );

        builder.addFormDataPart(
                "variety_id",
                String.valueOf(selectedVarietyId)
        );

        builder.addFormDataPart(
                "greenhouse[]",
                greenhouse
        );

        builder.addFormDataPart(
                "sample_space",
                String.valueOf(sampleSpace)
        );

        builder.addFormDataPart(
                "comments",
                comments
        );

        for (QualityEntry e : tempEntries) {

            builder.addFormDataPart(
                    "reason_id[]",
                    String.valueOf(e.reasonId)
            );

            builder.addFormDataPart(
                    "counts[]",
                    String.valueOf(e.count)
            );

            builder.addFormDataPart(
                    "percentages[]",
                    String.format("%.2f", e.percentage)
            );

            builder.addFormDataPart(
                    "reason_name[]",
                    e.reasonName
            );
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(BASE_URL + "submit_quality_analysis.php")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {

                runOnUiThread(() -> {

                    if (!isFinishing() && !isDestroyed()) {

                        Toast.makeText(
                                QualityAnalysisActivity.this,
                                "Submission failed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response)
                    throws IOException {

                final String resp =
                        response.body() != null
                                ? response.body().string()
                                : "";

                runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            QualityAnalysisActivity.this,
                            "Submitted: " + resp,
                            Toast.LENGTH_LONG
                    ).show();

                    tempEntries.clear();

                    btnSubmitAll.setVisibility(View.GONE);

                    btnStartAnalysis.setVisibility(View.VISIBLE);

                    cardCurrentReason.setVisibility(View.GONE);

                    edtSampleSpace.setText("");

                    edtComments.setText("");

                    autoVariety.setText("");

                    selectedVarietyId = -1;

                    selectedVarietyName = "";

                    selectedGreenhouseName = "";
                });
            }
        });
    }

    private static class QualityEntry {

        int reasonId;
        String reasonName;
        int count;
        double percentage;

        QualityEntry(int reasonId, String reasonName, int count) {

            this.reasonId = reasonId;
            this.reasonName = reasonName;
            this.count = count;
            this.percentage = 0.0;
        }
    }
}