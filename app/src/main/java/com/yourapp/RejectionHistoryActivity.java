package com.yourapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RejectionHistoryActivity extends AppCompatActivity {

    RecyclerView recycler;
    Button btnPickDate;
    OkHttpClient client = new OkHttpClient();
    List<RejectionHistoryItem> items = new ArrayList<>();
    RejectionHistoryAdapter adapter;

    Spinner spinnerFarm;
    HashMap<String, Integer> farmMap = new HashMap<>();
    int selectedFarmId = 0;


    private final String BASE_URL =
            "https://www.aaagrowers.co.ke/inventory/";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_rejection_history);

        recycler = findViewById(R.id.recyclerRejections);
        btnPickDate = findViewById(R.id.btnPickDate);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RejectionHistoryAdapter(items);
        recycler.setAdapter(adapter);

        btnPickDate.setOnClickListener(v -> pickDate());

        spinnerFarm = findViewById(R.id.spinnerFarm);
        loadFarms();

    }

    private void pickDate() {
        if (selectedFarmId == 0) {
            Toast.makeText(this, "Select farm first", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, y, m, d) -> {
                    String date = y + "-" +
                            String.format("%02d", m + 1) + "-" +
                            String.format("%02d", d);

                    loadRejections(date, selectedFarmId);
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }


    private void loadFarms() {
        Request request = new Request.Builder()
                .url(BASE_URL + "get_farms.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
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
                        farmMap.put(o.getString("name"), o.getInt("id"));
                        names.add(o.getString("name"));
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> ad = new ArrayAdapter<>(
                                RejectionHistoryActivity.this,
                                android.R.layout.simple_spinner_item,
                                names
                        );
                        ad.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item
                        );
                        spinnerFarm.setAdapter(ad);

                        spinnerFarm.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                                        selectedFarmId = farmMap.get(
                                                spinnerFarm.getSelectedItem().toString()
                                        );
                                    }

                                    @Override
                                    public void onNothingSelected(AdapterView<?> p) {
                                    }
                                }
                        );
                    });

                } catch (Exception ignored) {
                }
            }
        });
    }


    private void loadRejections(String date, int farmId) {
        Request request = new Request.Builder()
                .url(BASE_URL + "api/get_rejections_by_date.php"
                        + "?date=" + date
                        + "&farm=" + farmId)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;

                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    items.clear();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        RejectionHistoryItem r = new RejectionHistoryItem();
                        r.variety = o.optString("variety");
                        r.reason = o.optString("rejection_reason", "N/A");
                        r.stems = o.optInt("stems");
                        r.length = o.optString("length");
                        r.greenhouse = o.optString("GreenhouseName", "");
                        r.time = o.optString("submitted_at");
                        items.add(r);
                    }

                    runOnUiThread(() -> adapter.notifyDataSetChanged());

                } catch (Exception ignored) {
                }
            }
        });
    }
}

