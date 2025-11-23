package com.yourapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.*;

public class RejectActivity extends AppCompatActivity {

//    private Spinner spinnerFarm, spinnerCellNo, spinnerTableNo;
    private Spinner spinnerFarm;
    private AutoCompleteTextView autoVariety;
    private Button btnStartRejections, btnNextReason, btnSubmitAll;
    private EditText edtCurrentStems;
    private CardView cardCurrentReason;

    private ArrayList<RejectionEntry2> tempRejections = new ArrayList<>();
    private int currentReasonIndex = 0;
    private List<String> rejectionReasons = new ArrayList<>();
    private HashMap<String, Integer> farmMap = new HashMap<>();
    private HashMap<String, Integer> reasonMap = new HashMap<>();

    private int selectedVarietyId = -1;
    private String selectedVarietyName = "";

    private OkHttpClient client = new OkHttpClient();
    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ActivityResultLauncher<Intent> photoLauncher;
    private Bitmap capturedPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reject);

        spinnerFarm = findViewById(R.id.spinnerFarm);
//        spinnerCellNo = findViewById(R.id.spinnerCellNo);
//        spinnerTableNo = findViewById(R.id.spinnerTableNo);
        autoVariety = findViewById(R.id.autoVariety);
        btnStartRejections = findViewById(R.id.btnStartRejections);
        cardCurrentReason = findViewById(R.id.cardCurrentReason);
        edtCurrentStems = findViewById(R.id.edtCurrentStems);
        btnNextReason = findViewById(R.id.btnNextReason);
        btnSubmitAll = findViewById(R.id.btnSubmitAll);

//        setupCells();
//        setupTables();
        loadFarms();
        loadReasons();
        setupVarietyAutoComplete();

        btnStartRejections.setOnClickListener(v -> startRejectionWizard());
        btnNextReason.setOnClickListener(v -> goToNextReason());
//        btnSubmitAll.setOnClickListener(v -> capturePhotoAndSubmit());
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
    }

//    private void setupCells() {
//        List<String> cells = new ArrayList<>();
//        for (int row = 1; row <= 4; row++) {
//            for (char col = 'A'; col <= 'C'; col++) cells.add(row + "" + col);
//        }
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, cells);
//        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
//        spinnerCellNo.setAdapter(adapter);
//    }

//    private void setupTables() {
//        List<String> tables = new ArrayList<>();
//        for (int i = 1; i <= 8; i++) tables.add(String.valueOf(i));
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, tables);
//        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
//        spinnerTableNo.setAdapter(adapter);
//    }

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
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(RejectActivity.this, R.layout.spinner_dropdown_item, names);
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
        autoVariety.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String farmName = (spinnerFarm.getSelectedItem() != null) ? spinnerFarm.getSelectedItem().toString() : "";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    if(farmName.isEmpty()) return;
                }
                int farmId = farmMap.get(farmName);
                loadVarieties(farmId, s.toString());
            }
        });
    }

    private void loadVarieties(int farmId, String term) {
        new Thread(() -> {
            try {
                HttpUrl url = HttpUrl.parse(BASE_URL + "load_varieties.php")
                        .newBuilder()
                        .addQueryParameter("term", term)
                        .addQueryParameter("farm", String.valueOf(farmId))
                        .build();

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) return;

                JSONArray arr = new JSONArray(response.body().string());
                List<String> names = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    names.add(o.getString("VarietyName"));
                    ids.add(o.getInt("VarietyId"));
                }

                runOnUiThread(() -> {
                    VarietyAdapter3 adapter = new VarietyAdapter3(RejectActivity.this, names, ids);
                    autoVariety.setAdapter(adapter);
                    autoVariety.showDropDown();
                    autoVariety.setOnItemClickListener((parent, view, position, id) -> {
                        selectedVarietyId = adapter.getVarietyId(position);
                        selectedVarietyName = adapter.getItem(position);
                    });
                });
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
        if(currentReasonIndex >= rejectionReasons.size()) {
            cardCurrentReason.setVisibility(View.GONE);
            btnSubmitAll.setVisibility(Button.VISIBLE);
            return;
        }
        String reason = rejectionReasons.get(currentReasonIndex);
        ((TextView)findViewById(R.id.tvCurrentReason)).setText(reason);
        edtCurrentStems.setText("");
    }

    private void goToNextReason() {
        String stemsStr = edtCurrentStems.getText().toString();
        int stems = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            stems = stemsStr.isEmpty() ? 0 : Integer.parseInt(stemsStr);
        }

        String farmName = spinnerFarm.getSelectedItem().toString();
        int farmId = farmMap.get(farmName);
//        String cellNo = spinnerCellNo.getSelectedItem().toString();
//        String tableNo = spinnerTableNo.getSelectedItem().toString();
        int reasonId = reasonMap.get(rejectionReasons.get(currentReasonIndex));

        RejectionEntry2 entry = new RejectionEntry2(
                farmName, farmId,
                selectedVarietyName, selectedVarietyId,
                stems,
                rejectionReasons.get(currentReasonIndex), reasonId
        );
        tempRejections.add(entry);
        currentReasonIndex++;
        showCurrentReason();
    }

//    private void capturePhotoAndSubmit() {
//        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        photoLauncher.launch(cameraIntent);
//    }

    private void submitAllRejections() {
        if (tempRejections.isEmpty()) {
            Toast.makeText(this, "No rejections to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        for (RejectionEntry2 r : tempRejections) {
            builder.addFormDataPart("farm[]", String.valueOf(r.farmId));
            builder.addFormDataPart("variety[]", r.varietyName);
            builder.addFormDataPart("stems[]", String.valueOf(r.stems));
//            builder.addFormDataPart("cell_no[]", r.cellNo);
//            builder.addFormDataPart("table_no[]", r.tableNo);
            builder.addFormDataPart("rejection_reason[]", String.valueOf(r.rejectionReasonId));
        }

        // 🔥 REMOVED camera/photo upload section completely
        // No photo will be sent now

        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(BASE_URL + "rejection_form.php")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(RejectActivity.this, "Submission failed", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    Toast.makeText(RejectActivity.this, "Rejections submitted successfully", Toast.LENGTH_SHORT).show();
                    tempRejections.clear();
                    btnSubmitAll.setVisibility(Button.GONE);
                    btnStartRejections.setVisibility(Button.VISIBLE);
                });
            }
        });
    }

}
