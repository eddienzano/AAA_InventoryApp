package com.yourapp;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.yourapp.adapter.PriorityConfirmationAdapter;
import com.yourapp.models.PriorityLength;
import com.yourapp.models.PriorityRequestItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PriorityConfirmationActivity extends AppCompatActivity {

    private Spinner spinnerManager;
    private RecyclerView recycler;
    private PriorityConfirmationAdapter adapter;
    private List<PriorityRequestItem> items = new ArrayList<>();
    private List<String> managers = new ArrayList<>();
    private String currentManager = "Martin"; // TODO: dynamically get logged-in manager
    private MaterialButton btnSubmit;
    private final OkHttpClient client = new OkHttpClient();
    private final String BASE_URL = "https://www.aaagrowers.co.ke/inventory/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_priority_confirmation);

        spinnerManager = findViewById(R.id.spinnerManager);
        recycler = findViewById(R.id.recycler);
        btnSubmit = findViewById(R.id.btnSubmit);

        recycler.setLayoutManager(new LinearLayoutManager(this));

        loadManagers();

        spinnerManager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentManager = managers.get(position);
                loadPriorityByManager(currentManager);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSubmit.setOnClickListener(v -> submitConfirmation());
    }

    private void loadManagers() {
        Request request = new Request.Builder()
                .url(BASE_URL + "priority/get_managers.php")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(PriorityConfirmationActivity.this, "Failed to load managers", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) return;
                String resp = response.body().string();

                try {
                    JSONArray arr = new JSONArray(resp);
                    managers.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        managers.add(arr.getString(i));
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapterSp = new ArrayAdapter<>(
                                PriorityConfirmationActivity.this,
                                android.R.layout.simple_spinner_item,
                                managers
                        );
                        adapterSp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerManager.setAdapter(adapterSp);

                        if (!managers.isEmpty()) {
                            currentManager = managers.get(0);
                            spinnerManager.setSelection(0);
                            loadPriorityByManager(currentManager);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(PriorityConfirmationActivity.this, "Failed to parse managers", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void loadPriorityByManager(String manager) {
        String url = BASE_URL + "priority/get_priority_by_manager.php?manager=" + manager;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(PriorityConfirmationActivity.this, "Failed to load priority list", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) return;
                String resp = response.body().string();

                try {
                    JSONObject obj = new JSONObject(resp);
                    JSONArray arr = obj.optJSONArray("data");
                    if (arr == null) arr = new JSONArray();

                    items.clear();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject farmVariety = arr.getJSONObject(i);

                        PriorityRequestItem pi = new PriorityRequestItem();
                        pi.id = farmVariety.optInt("id"); // NEW: properly set ID
                        pi.farmName = farmVariety.optString("farmname");
                        pi.varietyName = farmVariety.optString("varietyname");
                        pi.status = farmVariety.optString("status");

                        JSONArray lengthsArr = farmVariety.optJSONArray("lengths_data");
                        if (lengthsArr == null) lengthsArr = new JSONArray();

                        List<PriorityLength> lengths = new ArrayList<>();
                        for (int j = 0; j < lengthsArr.length(); j++) {
                            JSONObject l = lengthsArr.getJSONObject(j);

                            int length = l.optInt("length");
                            int needed = l.optInt("needed");
                            int received = l.optInt("received");

                            PriorityLength pl = new PriorityLength();
                            pl.length = length;
                            pl.needed = needed;
                            pl.received = received;
                            pl.editable = (needed - received) > 0;
                            pl.zero = (needed - received) <= 0;

                            lengths.add(pl);
                        }

                        pi.lengths = lengths;
                        items.add(pi);
                    }

                    runOnUiThread(() -> {
                        adapter = new PriorityConfirmationAdapter(PriorityConfirmationActivity.this, items, currentManager);
                        recycler.setAdapter(adapter);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(PriorityConfirmationActivity.this, "Failed to parse priority list", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void submitConfirmation() {
        try {
            JSONArray arr = new JSONArray();
            for (PriorityRequestItem p : items) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    obj.put("confirmed_qty", p.confirmedByManager.getOrDefault(currentManager, 0));
                }
                arr.put(obj);
            }

            JSONObject payload = new JSONObject();
            payload.put("manager", currentManager);
            payload.put("entries", arr);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(BASE_URL + "priority/confirm_priority.php")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(PriorityConfirmationActivity.this, "Submission failed", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() ->
                            Toast.makeText(PriorityConfirmationActivity.this, "Confirmation submitted!", Toast.LENGTH_SHORT).show()
                    );
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error building JSON: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
