package com.yourapp.gradedstock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import android.content.Intent;

import com.yourapp.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class GradedEntryActivity extends AppCompatActivity {

    EditText etQrInput;
    Button btnSubmit, btnSync, btnCameraScan;
    RecyclerView rvList;

    ArrayList<QrItem> scanned = new ArrayList<>();
    QrAdapter adapter;

    int farmId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graded_entry);

        etQrInput = findViewById(R.id.etQrInput);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnSync = findViewById(R.id.btnSync);
        btnCameraScan = findViewById(R.id.btnCameraScan);
        rvList = findViewById(R.id.rvList);

        if (getIntent().hasExtra("farm_id"))
            farmId = getIntent().getIntExtra("farm_id", 1);

        adapter = new QrAdapter(scanned);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);

        etQrInput.requestFocus();
        etQrInput.setShowSoftInputOnFocus(false);

        btnCameraScan.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setPrompt("Scan Bucket QR");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        });

        etQrInput.addTextChangedListener(new android.text.TextWatcher() {

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(android.text.Editable s) {

                String qr = s.toString().trim();

                if (qr.length() < 10) return;

                etQrInput.setText("");
                handleScan(qr);
            }
        });

        btnSubmit.setOnClickListener(v -> saveOffline());
        btnSync.setOnClickListener(v -> syncServer());
    }

    // =========================
    // 🔥 MAIN FIX ENTRY POINT
    // =========================
    private void handleScan(String qr){

        // 1. Already scanned in current session
        if (existsInMemory(qr)) {
            toast("Already scanned (in list)");
            return;
        }

        // 2. Already saved locally but not synced
        if (isPendingDuplicate(qr)) {
            toast("Already saved (not yet synced)");
            return;
        }

        // 3. Already active in system
        if (isActiveBucket(qr)) {
            toast("Bucket already active");
            return;
        }

        QrItem item = parseQR(qr);

        if (item == null) {
            toast("Invalid QR");
            return;
        }

        showDialog(item);
    }

    // =========================
    // ✅ MEMORY CHECK
    // =========================
    private boolean existsInMemory(String qr){
        for(QrItem i : scanned){
            if(i.qr.equals(qr)) return true;
        }
        return false;
    }

    // =========================
    // ✅ LOCAL DB CHECK (UNSYNCED ONLY)
    // =========================
    private boolean isPendingDuplicate(String qr){

        SQLiteDatabase db = new StockDbHelper(this).getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT qr FROM graded_pending WHERE qr=? AND synced=0",
                new String[]{qr}
        );

        boolean exists = c.moveToFirst();
        c.close();

        return exists;
    }

    // =========================
    // EXISTING CHECK
    // =========================
    private boolean isActiveBucket(String qr){

        SQLiteDatabase db = new StockDbHelper(this).getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT qr FROM graded_active_qrs WHERE qr=?",
                new String[]{qr}
        );

        boolean exist = c.moveToFirst();
        c.close();

        return exist;
    }

    private QrItem parseQR(String qr){

        try{
            Pattern pattern = Pattern.compile(
                    "Serial:\\s*([^|]+)\\|\\s*Bucket:\\s*([^|]+)\\|\\s*Farm:\\s*([^|]+)\\|\\s*Length:\\s*(\\d+)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = pattern.matcher(qr);

            if(!matcher.find()) return null;

            QrItem item = new QrItem();
            item.qr = qr;
            item.serial = matcher.group(1).trim();
            item.bucket_name = matcher.group(2).trim();
            item.length = Integer.parseInt(matcher.group(4).trim());

            return item;

        }catch(Exception e){
            return null;
        }
    }

    private void showDialog(QrItem item){

        View v = getLayoutInflater().inflate(R.layout.dialog_grading, null);

        Spinner spVariety = v.findViewById(R.id.spinnerVariety);
        EditText etBunches = v.findViewById(R.id.etBunches);
        EditText etStems = v.findViewById(R.id.etStems);

        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();

        SQLiteDatabase db = new StockDbHelper(this).getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT id,name FROM varieties WHERE farm_id=? ORDER BY name",
                new String[]{String.valueOf(farmId)}
        );

        while(c.moveToNext()){
            ids.add(c.getInt(0));
            names.add(c.getString(1));
        }

        c.close();

        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVariety.setAdapter(ad);

        new AlertDialog.Builder(this)
                .setTitle("Enter Grading")
                .setView(v)
                .setPositiveButton("OK",(d,w)->{

                    if(TextUtils.isEmpty(etBunches.getText()) ||
                            TextUtils.isEmpty(etStems.getText())){
                        toast("Enter all values");
                        return;
                    }

                    item.variety_id = ids.get(spVariety.getSelectedItemPosition());
                    item.bunches = Integer.parseInt(etBunches.getText().toString());
                    item.stems = Integer.parseInt(etStems.getText().toString());

                    scanned.add(item);
                    adapter.notifyItemInserted(scanned.size()-1);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveOffline(){

        if(scanned.isEmpty()){
            toast("Nothing to save");
            return;
        }

        SQLiteDatabase db = new StockDbHelper(this).getWritableDatabase();

        for(QrItem i:scanned){

            ContentValues cv = new ContentValues();

            cv.put("qr", i.qr);
            cv.put("serial", i.serial);
            cv.put("bucket_name", i.bucket_name);
            cv.put("farm_id", farmId);
            cv.put("length", i.length);
            cv.put("variety_id", i.variety_id);
            cv.put("bunches", i.bunches);
            cv.put("stems_per_bunch", i.stems);
            cv.put("synced", 0);

            db.insert("graded_pending", null, cv);
        }

        scanned.clear();
        adapter.notifyDataSetChanged();

        toast("Saved offline");
    }

    private void syncServer(){

        SQLiteDatabase db = new StockDbHelper(this).getReadableDatabase();

        Cursor c = db.rawQuery("SELECT * FROM graded_pending WHERE synced=0", null);

        JSONArray arr = new JSONArray();

        while(c.moveToNext()){
            try{
                JSONObject o = new JSONObject();
                o.put("qr", c.getString(c.getColumnIndexOrThrow("qr")));
                o.put("serial", c.getString(c.getColumnIndexOrThrow("serial")));
                o.put("bucket_name", c.getString(c.getColumnIndexOrThrow("bucket_name")));
                o.put("farm_id", c.getInt(c.getColumnIndexOrThrow("farm_id")));
                o.put("length", c.getInt(c.getColumnIndexOrThrow("length")));
                o.put("variety_id", c.getInt(c.getColumnIndexOrThrow("variety_id")));
                o.put("bunches", c.getInt(c.getColumnIndexOrThrow("bunches")));
                o.put("stems_per_bunch", c.getInt(c.getColumnIndexOrThrow("stems_per_bunch")));

                arr.put(o);

            } catch(Exception e){
                e.printStackTrace();
            }
        }

        c.close();

        if(arr.length()==0){
            toast("Nothing to sync");
            return;
        }

        JSONObject payload = new JSONObject();

        try{
            payload.put("records", arr);
        }catch(Exception e){}

        new Thread(() -> {
            try{
                String res = HttpHelper.postJson(
                        "https://www.aaagrowers.co.ke/inventory/graded/sync_graded_upload.php",
                        payload.toString()
                );

                JSONObject r = new JSONObject(res);

                if(r.getString("status").equals("success")){

                    db.execSQL("UPDATE graded_pending SET synced=1 WHERE synced=0");

                    // 🔥 NEW: REFRESH ACTIVE STOCK
                    SyncManager.syncActiveStock(this, farmId, new SyncManager.SyncCallback() {

                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> toast("Synced + refreshed stock"));
                        }

                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> toast("Synced but refresh failed"));
                        }
                    });
                }
            }catch(Exception e){
                runOnUiThread(() -> toast("Sync error"));
            }
        }).start();
    }

    private void toast(String m){
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if(result != null){
            if(result.getContents() != null){
                handleScan(result.getContents());
            }else{
                toast("Scan cancelled");
            }
        }else{
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}