package com.yourapp.gradedstock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yourapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Intent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScanOutWipActivity extends AppCompatActivity {

    EditText qrInput;
    Button scanBtn;
    LinearLayout historyList;
    TextView resultBox;

    int farmId;

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_out_wip);

        farmId = getIntent().getIntExtra("farm_id",0);

        qrInput = findViewById(R.id.qrInput);
        scanBtn = findViewById(R.id.scanBtn);
        historyList = findViewById(R.id.historyList);
        resultBox = findViewById(R.id.resultBox);

        addScannerWatcher();

        qrInput.setOnEditorActionListener((v,a,e)->{

            String qr = qrInput.getText().toString().trim();

            if(!qr.isEmpty()){
                processScan(qr);
                qrInput.setText("");
            }

            return true;

        });
        scanBtn.setOnClickListener(v -> {

            IntentIntegrator integrator = new IntentIntegrator(ScanOutWipActivity.this);

            integrator.setPrompt("Scan Bucket QR");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();

        });

        Button btnSyncNow = findViewById(R.id.btnSyncNow);

        btnSyncNow.setOnClickListener(v -> {
            syncAll();
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        syncAll();
    }

    private void syncAll() {

        new Thread(() -> {

            try {

                runOnUiThread(() -> showResult("Syncing...", true));

                // STEP 1: PUSH scan outs
                boolean pushOk = syncScanOutsInternal();

                if (!pushOk) {
                    runOnUiThread(() -> showResult("Push failed. Try again.", false));
                    return;
                }

                // STEP 2: PULL latest buckets
                boolean pullOk = syncActiveBuckets();

                if (pullOk) {
                    runOnUiThread(() -> showResult("Sync complete ✓", true));
                } else {
                    runOnUiThread(() -> showResult("Pull failed", false));
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showResult("Sync error", false));
            }

        }).start();
    }

    private boolean syncScanOutsInternal() {

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

            if (arr.length() == 0) return true; // nothing to push

            String url = "https://www.aaagrowers.co.ke/inventory/graded/sync_scanouts.php";
            String response = HttpHelper.postJson(url, arr.toString());

            if (response == null || response.isEmpty()) return false;

            JSONObject respObj = new JSONObject(response);

            if (respObj.optBoolean("success", false)) {
                db.execSQL("UPDATE graded_out_local SET synced=1 WHERE synced=0");
                return true;
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean syncActiveBuckets() {

        try {

            String url = "https://www.aaagrowers.co.ke/inventory/graded/sync_active_graded.php?farm_id=" + farmId;

            String json = HttpHelper.get(url);

            JSONObject obj = new JSONObject(json);

            if (!obj.getBoolean("success")) return false;

            StockDbHelper helper = new StockDbHelper(this);
            SQLiteDatabase db = helper.getWritableDatabase();

            // SAFE now because push already done
            db.execSQL("DELETE FROM graded_active_qrs");

            JSONArray buckets = obj.getJSONArray("buckets");

            for (int i = 0; i < buckets.length(); i++) {

                JSONObject b = buckets.getJSONObject(i);

                ContentValues cv = new ContentValues();

                cv.put("qr", b.getString("qr"));
                cv.put("serial", b.getString("serial"));
                cv.put("bucket_name", b.getString("bucket_name"));
                cv.put("farm_id", b.getInt("farm_id"));
                cv.put("length", b.getInt("length"));
                cv.put("variety_id", b.getInt("variety_id"));
                cv.put("bunches", b.getInt("bunches"));
                cv.put("stems_per_bunch", b.getInt("stems_per_bunch"));

                db.insert("graded_active_qrs", null, cv);
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void addScannerWatcher(){

        Runnable r = ()->{

            String qr = qrInput.getText().toString().trim();

            if(!qr.isEmpty()){
                processScan(qr);
                qrInput.setText("");
            }

        };

        qrInput.addTextChangedListener(new android.text.TextWatcher(){

            public void beforeTextChanged(CharSequence s,int a,int b,int c){}

            public void onTextChanged(CharSequence s,int a,int b,int c){

                handler.removeCallbacks(r);

                if(s.length()>6)
                    handler.postDelayed(r,300);

            }

            public void afterTextChanged(android.text.Editable e){}

        });

    }

    private void processScan(String qr){

        if(!bucketExists(qr)){

            showResult("Bucket not found",false);
            return;

        }

        showDispatchDialog(qr);

    }

    private boolean bucketExists(String qr){

        StockDbHelper helper = new StockDbHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT qr FROM graded_active_qrs WHERE qr=?",
                new String[]{qr}
        );

        boolean exists = c.moveToFirst();

        c.close();

        return exists;

    }

    private void showDispatchDialog(String qr){
        int remaining = getBunchesForQr(qr); // reads current remaining from SQLite

        if(remaining <= 0){
            showResult("No remaining bunches to dispatch", false);
            return;
        }

        String[] options = {"Partial Dispatch (" + remaining + " remaining)","Complete Dispatch"};

        new AlertDialog.Builder(this)
                .setTitle("Dispatch Type")
                .setItems(options,(d,w)->{
                    if(w==0)
                        askPartial(qr, remaining); // pass remaining to partial dispatch
                    else {
                        saveOffline(qr, "COMPLETE", remaining); // dispatch everything left
                    }
                })
                .show();
    }

    private void askPartial(String qr, int maxQty){
        EditText input = new EditText(this);
        input.setHint("Enter bunches removed (max "+maxQty+")");

        new AlertDialog.Builder(this)
                .setTitle("Partial Dispatch")
                .setView(input)
                .setPositiveButton("Save",(d,w)->{
                    int qty = Integer.parseInt(input.getText().toString());
                    if(qty > maxQty){
                        Toast.makeText(this,"Cannot remove more than remaining",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveOffline(qr,"PARTIAL",qty);
                })
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void saveOffline(String qr, String action, int qty){
        StockDbHelper helper = new StockDbHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();

        int stockId = getGradedStockIdForQr(qr);

        // Insert into offline history
        ContentValues cv = new ContentValues();
        cv.put("qr", qr);
        cv.put("graded_stock_id", stockId);
        cv.put("farm_id", farmId);
        cv.put("action_type", action);
        cv.put("bunches_removed", qty);
        cv.put("scanned_at", getTime());
        cv.put("synced", 0);

        db.insert("graded_out_local", null, cv);

        // Update remaining in local table
        int remaining = getBunchesForQr(qr);
        int newRemaining = Math.max(0, remaining - qty);

        ContentValues updateCv = new ContentValues();
        updateCv.put("bunches", newRemaining); // <-- remaining now
        db.update("graded_active_qrs", updateCv, "qr=?", new String[]{qr});

        addHistoryItem(qr);
        showResult("Saved offline ✓", true);
    }

    // helper to get the stock ID
    private int getGradedStockIdForQr(String qr) {
        int stockId = 0;
        StockDbHelper helper = new StockDbHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT graded_stock_id FROM graded_active_qrs WHERE qr=?",
                new String[]{qr}
        );

        if (c.moveToFirst()) {
            stockId = c.getInt(c.getColumnIndexOrThrow("graded_stock_id"));
        }

        c.close();
        return stockId;
    }

    private void addHistoryItem(String qr){

        TextView t = new TextView(this);

        t.setText(qr+" • "+getTime());

        historyList.addView(t,0);

    }

    private void showResult(String msg,boolean ok){

        resultBox.setText(msg);

    }

    private String getTime(){

        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

    }

    private int getBunchesForQr(String qr) {
        int bunches = 0;
        StockDbHelper helper = new StockDbHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT bunches FROM graded_active_qrs WHERE qr=?",
                new String[]{qr}
        );

        if (c.moveToFirst()) {
            bunches = c.getInt(c.getColumnIndexOrThrow("bunches"));
        }

        c.close();
        return bunches;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if(result != null){

            if(result.getContents() != null){

                String qr = result.getContents();

                processScan(qr);

            }else{

                Toast.makeText(this,"Scan cancelled",Toast.LENGTH_SHORT).show();

            }

        }else{

            super.onActivityResult(requestCode,resultCode,data);

        }
    }

}