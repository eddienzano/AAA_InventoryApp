package com.yourapp.gradedstock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.yourapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Intent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ScanOutReworkActivity extends AppCompatActivity {

    EditText qrInput;
    LinearLayout historyList;
    TextView resultBox;

    Button scanBtn;



    int farmId;

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_out_rework);

        farmId = getIntent().getIntExtra("farm_id",0);

        qrInput = findViewById(R.id.qrInput);
        historyList = findViewById(R.id.historyList);
        resultBox = findViewById(R.id.resultBox);

        qrInput = findViewById(R.id.qrInput);
        scanBtn = findViewById(R.id.scanBtn);
        historyList = findViewById(R.id.historyList);
        resultBox = findViewById(R.id.resultBox);



        addScannerWatcher();

        scanBtn.setOnClickListener(v -> {

            IntentIntegrator integrator = new IntentIntegrator(ScanOutReworkActivity.this);

            integrator.setPrompt("Scan Bucket QR");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();

        });

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


        saveOffline(qr);

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

    private void saveOffline(String qr){

        StockDbHelper helper = new StockDbHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();

    /* -----------------------------
       GET CURRENT BUNCHES
    ------------------------------ */

        int bunches = 0;

        Cursor c = db.rawQuery(
                "SELECT bunches FROM graded_active_qrs WHERE qr=?",
                new String[]{qr}
        );

        if(c.moveToFirst()){
            bunches = c.getInt(0);
        }

        c.close();

        if(bunches <= 0){
            showResult("Bucket already empty", false);
            return;
        }

    /* -----------------------------
       SAVE REWORK ACTION
    ------------------------------ */

        ContentValues cv = new ContentValues();

        cv.put("qr", qr);
        cv.put("farm_id", farmId);
        cv.put("action_type", "REWORK");
        cv.put("bunches_removed", bunches); // REAL VALUE
        cv.put("scanned_at", getTime());
        cv.put("synced", 0);

        db.insert("graded_out_local", null, cv);

    /* -----------------------------
       MARK BUCKET EMPTY LOCALLY
    ------------------------------ */

        ContentValues update = new ContentValues();
        update.put("bunches", 0);

        db.update(
                "graded_active_qrs",
                update,
                "qr=?",
                new String[]{qr}
        );

    /* -----------------------------
       UI
    ------------------------------ */

        addHistoryItem(qr + " • " + bunches + " bunches");

        showResult("Sent to Rework ✓ ("+bunches+" bunches)", true);
    }

    private void addHistoryItem(String qr){

        TextView t = new TextView(this);

        t.setText(qr+" • "+getTime());

        historyList.addView(t,0);

    }

    private void showResult(String msg, boolean ok){

        resultBox.setVisibility(TextView.VISIBLE);
        resultBox.setText(msg);

    }

    private String getTime(){

        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

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