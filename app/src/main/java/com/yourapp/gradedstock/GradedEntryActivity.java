package com.yourapp.gradedstock;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GradedEntryActivity extends AppCompatActivity {

    private static final String TAG = "GradedEntryActivity";

    private Spinner spFarm;
    private RecyclerView rvVarieties;
    private VarietyAdapter adapter;
    private final List<VarietyModel> varieties = new ArrayList<>();

    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graded_entry);
        Log.d(TAG, "onCreate");

        spFarm = findViewById(R.id.spFarm);
        rvVarieties = findViewById(R.id.rvVarieties);

        rvVarieties.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VarietyAdapter(this, varieties);
        rvVarieties.setAdapter(adapter);

        loadFarms();

        spFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String farm = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Farm selected: " + farm);
                loadVarieties(farm);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // -------------------------------
    // LOAD FARMS
    // -------------------------------
    private void loadFarms() {
        Request request = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "loadFarms failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) return;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(response.body().string());
                    List<String> farms = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        farms.add(arr.getJSONObject(i).getString("name"));
                    }
                    runOnUiThread(() -> spFarm.setAdapter(new ArrayAdapter<>(
                            GradedEntryActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            farms
                    )));
                } catch (Exception e) {
                    Log.e(TAG, "Farm JSON error", e);
                }
            }
        });
    }

    // -------------------------------
    // LOAD VARIETIES
    // -------------------------------
    private void loadVarieties(String farmName) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "fetch_varieties.php")
                .newBuilder()
                .addQueryParameter("farm", farmName)
                .build();

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "loadVarieties failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) return;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(response.body().string());
                    varieties.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        String img = o.optString("image");
                        if (!img.isEmpty() && !img.startsWith("http")) img = BASE_URL + img;
                        varieties.add(new VarietyModel(
                                o.getInt("VarietyId"),
                                o.getString("VarietyName"),
                                img
                        ));
                    }
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                } catch (Exception e) {
                    Log.e(TAG, "Variety JSON error", e);
                }
            }
        });
    }

    // -------------------------------
    // CONFIRM + SUBMIT
    // -------------------------------
    public void confirmAndSubmit(VarietyModel model) {
        if (model == null) return;

        String summary = model.getSummary();

        // Use dialog like NewIntakeActivity
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Entry")
                .setMessage(summary + "\n\nFarm: " + spFarm.getSelectedItem().toString())
                .setPositiveButton("SUBMIT", (dialog, which) -> submitToWipStock(model))
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void submitToWipStock(VarietyModel model) {
        if (model == null) return;

        Map<Integer, Integer> lengths = model.getLengths();
        if (lengths == null || lengths.isEmpty()) {
            Toast.makeText(this, "No lengths entered", Toast.LENGTH_SHORT).show();
            return;
        }

        String farm = spFarm.getSelectedItem() != null ? spFarm.getSelectedItem().toString() : "";
        if (farm.isEmpty()) {
            Toast.makeText(this, "Select a farm first", Toast.LENGTH_SHORT).show();
            return;
        }

        org.json.JSONObject payload = new org.json.JSONObject();
        try {
            payload.put("variety_id", model.getId());
            payload.put("variety_name", model.getName());
            payload.put("farm", farm);

            org.json.JSONObject lenJson = new org.json.JSONObject();
            int total = 0;
            for (Map.Entry<Integer, Integer> e : lengths.entrySet()) {
                if (e.getValue() > 0) {
                    lenJson.put(String.valueOf(e.getKey()), e.getValue());
                    total += e.getValue();
                }
            }
            if (total == 0) {
                Toast.makeText(this, "Nothing to submit", Toast.LENGTH_SHORT).show();
                return;
            }
            payload.put("lengths", lenJson);
            payload.put("total_qty", total);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                payload.put("submitted_at",
                        java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Payload build failed", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(BASE_URL + "api/submit_wip_stock.php")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(GradedEntryActivity.this, "Network error", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(GradedEntryActivity.this, "WIP stock saved", Toast.LENGTH_SHORT).show();

                        // ✅ CLEAR FIELDS LIKE NewIntakeActivity
                        model.clearLengths();
                        adapter.notifyDataSetChanged();
                        spFarm.setSelection(0);
                    } else {
                        Toast.makeText(GradedEntryActivity.this, "Server error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    // =====================================================
    // FARM
    // =====================================================
    public String getSelectedFarmName() {
        return spFarm.getSelectedItem() != null
                ? spFarm.getSelectedItem().toString()
                : "";
    }
}





