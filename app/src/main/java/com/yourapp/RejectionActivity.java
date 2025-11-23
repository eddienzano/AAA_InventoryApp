package com.yourapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.*;

public class RejectionActivity extends AppCompatActivity {

    private Spinner spinnerFarm, spinnerReason, spinnerLength;
    private AutoCompleteTextView autoVariety;
    private EditText edtStems, edtCellNo, edtTableNo;
    private Spinner spinnerCellNo, spinnerTableNo;
    private Button btnAddToList, btnSubmitAll;
    private RecyclerView recyclerView;

    private ArrayList<RejectionEntry> tempRejections = new ArrayList<>();
    private RejectionAdapter adapter;

    private HashMap<String,Integer> farmMap = new HashMap<>();
    private HashMap<String,Integer> reasonMap = new HashMap<>();

    private int selectedVarietyId = -1;
    private String selectedVarietyName = "";

    private OkHttpClient client = new OkHttpClient();
    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    private ActivityResultLauncher<Intent> photoLauncher;
    private Bitmap capturedPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rejection);

        spinnerFarm = findViewById(R.id.spinnerFarm);
        spinnerReason = findViewById(R.id.spinnerReason);
//        spinnerLength = findViewById(R.id.spinnerLength);
        autoVariety = findViewById(R.id.autoVariety);
        edtStems = findViewById(R.id.edtStems);
//        edtCellNo = findViewById(R.id.edtCellNo);
//        edtTableNo = findViewById(R.id.edtTableNo);
        btnAddToList = findViewById(R.id.btnAddToList);
        btnSubmitAll = findViewById(R.id.btnSubmitAll);
        recyclerView = findViewById(R.id.recyclerViewRejections);

        adapter = new RejectionAdapter(tempRejections);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        spinnerCellNo = findViewById(R.id.spinnerCellNo);
        spinnerTableNo = findViewById(R.id.spinnerTableNo);

        setupCells();
        setupTables();

//        setupLengths();
        loadFarms();
        loadReasons();
        setupVarietyAutoComplete();

        btnAddToList.setOnClickListener(v -> addRejectionToList());
        btnSubmitAll.setOnClickListener(v -> capturePhotoAndSubmit());

        photoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == RESULT_OK){
                capturedPhoto = (Bitmap) result.getData().getExtras().get("data");
                submitAllRejections();
            }
        });
    }

//    private void setupLengths() {
//        String[] lengths = {"40cm","50cm","60cm","70cm","80cm","90cm","100cm"};
//        ArrayAdapter<String> lengthAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, lengths);
//        lengthAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
//        spinnerLength.setAdapter(lengthAdapter);
//    }

    private void setupCells() {
        // Example: 1A, 1B, 1C, 2A, 2B, 2C ...
        List<String> cells = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {      // adjust max row
            for (char col = 'A'; col <= 'C'; col++) { // adjust max column
                cells.add(row + "" + col);
            }
        }
        ArrayAdapter<String> cellAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, cells);
        cellAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerCellNo.setAdapter(cellAdapter);
    }

    private void setupTables() {
        List<String> tables = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {  // adjust max table number
            tables.add(String.valueOf(i));
        }
        ArrayAdapter<String> tableAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, tables);
        tableAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerTableNo.setAdapter(tableAdapter);
    }

    private void loadFarms() {
        Request request = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    ArrayList<String> names = new ArrayList<>();
                    farmMap.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        int id = obj.getInt("id");
                        String name = obj.getString("name");
                        farmMap.put(name, id);
                        names.add(name);
                    }
                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(RejectionActivity.this, R.layout.spinner_dropdown_item, names);
                        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        spinnerFarm.setAdapter(adapter);
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private void loadReasons() {
        Request request = new Request.Builder()
                .url(BASE_URL + "get_reasons.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if(!response.isSuccessful()) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    ArrayList<String> names = new ArrayList<>();
                    reasonMap.clear();
                    for(int i=0;i<arr.length();i++){
                        JSONObject obj = arr.getJSONObject(i);
                        int id = obj.getInt("id");
                        String name = obj.getString("category");
                        names.add(name);
                        reasonMap.put(name,id);
                    }
                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(RejectionActivity.this, R.layout.spinner_dropdown_item, names);
                        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        spinnerReason.setAdapter(adapter);
                    });
                } catch(Exception e){ e.printStackTrace(); }
            }
        });
    }

    private void setupVarietyAutoComplete() {
        autoVariety.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String farmName = (spinnerFarm.getSelectedItem() != null) ? spinnerFarm.getSelectedItem().toString() : "";
                if(farmName.isEmpty()) return;
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
                    VarietyAdapter2 adapter = new VarietyAdapter2(RejectionActivity.this, names, ids);
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

    private void addRejectionToList() {
        if(spinnerFarm.getSelectedItem() == null || spinnerReason.getSelectedItem() == null){
            Toast.makeText(this,"Please select farm and reason", Toast.LENGTH_SHORT).show();
            return;
        }

        String farmName = spinnerFarm.getSelectedItem().toString();
        int farmId = farmMap.get(farmName);
        String variety = autoVariety.getText().toString();
        if(variety.isEmpty() || selectedVarietyId == -1){
            Toast.makeText(this,"Select a valid variety", Toast.LENGTH_SHORT).show();
            return;
        }

//        String length = spinnerLength.getSelectedItem().toString();
        String stemsStr = edtStems.getText().toString();
        String cellNo = spinnerCellNo.getSelectedItem().toString();
        String tableNo = spinnerTableNo.getSelectedItem().toString();
        int stems = stemsStr.isEmpty() ? 0 : Integer.parseInt(stemsStr);

        int reasonId = reasonMap.get(spinnerReason.getSelectedItem().toString());
        String reasonName = spinnerReason.getSelectedItem().toString();

        RejectionEntry entry = new RejectionEntry(
                farmName, farmId,
                variety, selectedVarietyId,
                 stems, cellNo, tableNo,
                reasonName, reasonId
        );

        tempRejections.add(entry);
        adapter.notifyItemInserted(tempRejections.size() - 1);

        // clear inputs
//        autoVariety.setText("");
        edtStems.setText("");
//        edtCellNo.setText("");
//        edtTableNo.setText("");
        spinnerReason.setSelection(0);
    }

    private void capturePhotoAndSubmit() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        photoLauncher.launch(cameraIntent);
    }

    private void submitAllRejections() {
        if(tempRejections.isEmpty()){
            Toast.makeText(this,"No rejections to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for(RejectionEntry r : tempRejections){
            builder.addFormDataPart("farm[]", String.valueOf(r.farmId));
            builder.addFormDataPart("variety[]", r.varietyName);
//            builder.addFormDataPart("length[]", r.length);
            builder.addFormDataPart("stems[]", String.valueOf(r.stems));
          builder.addFormDataPart("cell_no[]", r.cellNo);
            builder.addFormDataPart("table_no[]", r.tableNo);
            builder.addFormDataPart("rejection_reason[]", String.valueOf(r.rejectionReasonId));
        }

        if(capturedPhoto != null){
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            capturedPhoto.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            builder.addFormDataPart("photo","bunch.jpg",
                    RequestBody.create(bos.toByteArray(), MediaType.parse("image/jpeg")));
        }

        RequestBody requestBody = builder.build();
        Request request = new Request.Builder().url(BASE_URL+"rejection_form.php").post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(RejectionActivity.this,"Submission failed",Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    Toast.makeText(RejectionActivity.this,"Rejections submitted successfully",Toast.LENGTH_SHORT).show();
                    tempRejections.clear();
                    adapter.notifyDataSetChanged();
                    capturedPhoto = null;
                });
            }
        });
    }
}
