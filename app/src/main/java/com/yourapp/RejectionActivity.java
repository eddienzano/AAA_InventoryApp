package com.yourapp;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.*;

public class RejectionActivity extends AppCompatActivity {

    private Spinner spinnerFarm, spinnerReason;
    private AutoCompleteTextView autoVariety;
    private EditText edtStems, edtCellNo, edtTableNo;
    private Button btnSubmit;

    private Spinner spinnerLength;

    private int selectedVarietyId = -1;
    private String selectedVarietyName = "";

    private ArrayAdapter<String> varietyAdapter;
    private ArrayList<String> varietyList = new ArrayList<>();
    private HashMap<String,Integer> farmMap = new HashMap<>();
    private HashMap<String,Integer> reasonMap = new HashMap<>();

    private OkHttpClient client = new OkHttpClient();
    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rejection);

        spinnerFarm = findViewById(R.id.spinnerFarm);
        spinnerReason = findViewById(R.id.spinnerReason);
        autoVariety = findViewById(R.id.autoVariety);
        edtStems = findViewById(R.id.edtStems);
        edtCellNo = findViewById(R.id.edtCellNo);
        edtTableNo = findViewById(R.id.edtTableNo);
        btnSubmit = findViewById(R.id.btnSubmit);

        varietyAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, varietyList);
        autoVariety.setAdapter(varietyAdapter);

        spinnerLength = findViewById(R.id.spinnerLength);

// Set lengths
        String[] lengths = {"40cm","50cm","60cm","70cm","80cm","90cm","100cm"};
        ArrayAdapter<String> lengthAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, lengths);
        lengthAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerLength.setAdapter(lengthAdapter);




        loadFarms();
        loadReasons();

        spinnerFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String farmName = spinnerFarm.getSelectedItem().toString();
                int farmId = farmMap.get(farmName);
                loadVarieties(farmId,""); // load all varieties initially
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        autoVariety.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String farmName = spinnerFarm.getSelectedItem().toString();
                int farmId = farmMap.get(farmName);
                loadVarieties(farmId,s.toString());
            }
        });

        btnSubmit.setOnClickListener(v -> submitForm());
    }

    private void loadFarms() {
        Request request = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
//                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
//                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        spinnerReason.setAdapter(adapter);
                    });

                } catch(Exception e){ e.printStackTrace(); }
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

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void submitForm() {
        String farmId = String.valueOf(farmMap.get(spinnerFarm.getSelectedItem().toString()));
        String variety = autoVariety.getText().toString();
        String stems = edtStems.getText().toString();
        String cellNo = edtCellNo.getText().toString();
        String tableNo = edtTableNo.getText().toString();
        String reasonId = String.valueOf(reasonMap.get(spinnerReason.getSelectedItem().toString()));
        String length = spinnerLength.getSelectedItem().toString();

        if(variety.isEmpty() || stems.isEmpty()){
            Toast.makeText(this,"Please fill required fields",Toast.LENGTH_SHORT).show();
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("farm",farmId)
                .add("variety",variety)
                .add("stems",stems)
                .add("cell_no",cellNo)
                .add("table_no",tableNo)
                .add("rejection_reason",reasonId)
                 .add("length", length) // <-- new field
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "rejection_form.php")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    new androidx.appcompat.app.AlertDialog.Builder(RejectionActivity.this)
                            .setTitle("Error")
                            .setMessage("Submission failed. Please try again.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject obj = new JSONObject(respStr);
                        if (obj.getString("status").equals("success")) {
                            // Clear fields
                            autoVariety.setText("");
                            edtStems.setText("");
                            edtCellNo.setText("");
                            edtTableNo.setText("");
                            spinnerFarm.setSelection(0);
                            spinnerReason.setSelection(0);
                            spinnerLength.setSelection(0);

                            new androidx.appcompat.app.AlertDialog.Builder(RejectionActivity.this)
                                    .setTitle("Success")
                                    .setMessage("Rejection submitted successfully at " + obj.optString("submitted_at"))
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            new androidx.appcompat.app.AlertDialog.Builder(RejectionActivity.this)
                                    .setTitle("Error")
                                    .setMessage(obj.optString("message", "An error occurred"))
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    } catch (Exception e) {
                        new androidx.appcompat.app.AlertDialog.Builder(RejectionActivity.this)
                                .setTitle("Error")
                                .setMessage("Invalid response from server")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            }
        });
    }

}
