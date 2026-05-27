package com.yourapp.gradedstock;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.yourapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

import com.yourapp.gradedstock.HttpHelper;

public class FarmSelectActivity extends AppCompatActivity {

    Spinner spFarm;
    Button btnSync;

    Button btnSyncScanOuts;

    Button btnGraded;
    Button btnScanOut;
    Button btnScanOutRework;

    LinearLayout operationsLayout;

    int selectedFarmId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farm_select);

        spFarm = findViewById(R.id.spFarm);
        btnSync = findViewById(R.id.btnSync);

        btnGraded = findViewById(R.id.btnGraded);
        btnScanOut = findViewById(R.id.btnScanOut);
        btnScanOutRework = findViewById(R.id.btnScanOutRework);

        btnSyncScanOuts = findViewById(R.id.btnSyncScanOuts);

        operationsLayout = findViewById(R.id.operationsLayout);

        String[] farms = {"Select Farm","Simba","Chui","Chestnut"};

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,farms);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFarm.setAdapter(adapter);

        spFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFarmId = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedFarmId = 0;
            }
        });
        btnSyncScanOuts.setOnClickListener(v -> syncScanOuts());

        btnSync.setOnClickListener(v -> {

            if(selectedFarmId==0){
                toast("Select farm first");
                return;
            }

            syncFromServer(selectedFarmId);

        });

        // Graded Intake
        btnGraded.setOnClickListener(v -> {

            Intent i = new Intent(this, GradedEntryActivity.class);
            i.putExtra("farm_id", selectedFarmId);
            startActivity(i);

        });

        // Scan Out Dispatch
        btnScanOut.setOnClickListener(v -> {

            Intent i = new Intent(this, ScanOutWipActivity.class);
            i.putExtra("farm_id", selectedFarmId);
            startActivity(i);

        });

        // Scan Out Rework
        btnScanOutRework.setOnClickListener(v -> {

            Intent i = new Intent(this, ScanOutReworkActivity.class);
            i.putExtra("farm_id", selectedFarmId);
            startActivity(i);

        });
    }

    private void syncFromServer(int farmId){

        SyncManager.syncActiveStock(this, farmId, new SyncManager.SyncCallback() {

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    toast("Sync completed");
                    operationsLayout.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> toast(msg));
            }
        });
    }

    private void toast(String m){
        Toast.makeText(this,m,Toast.LENGTH_SHORT).show();
    }

    private void syncScanOuts() {
        new Thread(() -> {
            try {
                StockDbHelper helper = new StockDbHelper(this);
                SQLiteDatabase db = helper.getWritableDatabase();

                Cursor c = db.rawQuery(
                        "SELECT * FROM graded_out_local WHERE synced=0",
                        null
                );

                JSONArray arr = new JSONArray();

                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getInt(c.getColumnIndexOrThrow("id")));
                    o.put("qr", c.getString(c.getColumnIndexOrThrow("qr")));
                    o.put("graded_stock_id", c.getInt(c.getColumnIndexOrThrow("graded_stock_id")));
                    o.put("farm_id", c.getInt(c.getColumnIndexOrThrow("farm_id")));
                    o.put("action_type", c.getString(c.getColumnIndexOrThrow("action_type")));
                    o.put("bunches_removed", c.getInt(c.getColumnIndexOrThrow("bunches_removed")));
                    o.put("scanned_at", c.getString(c.getColumnIndexOrThrow("scanned_at")));
                    arr.put(o);
                }

                c.close();

                if (arr.length() == 0) {
                    runOnUiThread(() -> toast("Nothing to sync"));
                    return;
                }

                String url = "https://www.aaagrowers.co.ke/inventory/graded/sync_scanouts.php";
                String response = HttpHelper.postJson(url, arr.toString());

                Log.d("SYNC", "Server response: " + response);

                if (response == null || response.isEmpty()) {
                    runOnUiThread(() -> toast("Sync failed: empty server response"));
                    return;
                }

                JSONObject respObj = new JSONObject(response);

                if (respObj.optBoolean("success", false)) {
                    db.execSQL("UPDATE graded_out_local SET synced=1 WHERE synced=0");
                    runOnUiThread(() -> toast("Scan outs synced"));
                } else {
                    String msg = respObj.optString("message", "Sync failed on server");
                    runOnUiThread(() -> toast(msg));
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("Sync failed"));
            }
        }).start();
    }
}