package com.yourapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.yourapp.boxfill.BoxConfirmActivity;


import androidx.appcompat.app.AppCompatActivity;
import com.yourapp.boxfill.BoxFillActivity;
import com.yourapp.summers.ScanBackSummerActivity;
import com.yourapp.warehouse.WarehouseStockScanActivity;
import com.yourapp.gradedstock.*;

public class FloorDashboardActivity extends AppCompatActivity {

    TextView floorWelcomeText;
    Button newscanToWipBtn, SummerBack, scanToWipBtn, gradedBtn, scanToBoxBtn, scanOutWipBtn,rejectBtn, scanOutReworkBtn, scanStockBtn, boxConfirm;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floor_dashboard);

        floorWelcomeText = findViewById(R.id.floorWelcomeText);
       rejectBtn = findViewById(R.id.rejectBtn);
        scanToBoxBtn = findViewById(R.id.scanToBoxBtn);
        scanOutWipBtn = findViewById(R.id.scanOutWipBtn);
        scanOutReworkBtn = findViewById(R.id.scanOutReworkBtn);
        newscanToWipBtn = findViewById(R.id.newscanToWipBtn);
        gradedBtn=findViewById(R.id.gradedBtn);
        scanStockBtn=findViewById(R.id.scanStockBtn);
        boxConfirm =findViewById(R.id.boxConfirm);
        SummerBack =findViewById(R.id.SummerBack);


        String username = getIntent().getStringExtra("username");
        floorWelcomeText.setText("Floor Dashboard - " + username);

        // Button actions - link to your scanning activities
        gradedBtn.setOnClickListener(v -> {
            Intent intent = new Intent(
                    FloorDashboardActivity.this,
                    FarmSelectActivity.class
            );
            startActivity(intent);
        });


        // Button actions - link to your scanning activities
        newscanToWipBtn.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, WipScanActivity.class);
            startActivity(intent);
        });

        SummerBack.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, ScanBackSummerActivity.class);
            startActivity(intent);
        });

        scanToBoxBtn.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, ScanToBoxActivity.class);
            startActivity(intent);
        });

        scanOutWipBtn.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, ScanOutWipActivity.class);
            startActivity(intent);
        });

        scanOutReworkBtn.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, ScanOutReworkActivity.class);
            startActivity(intent);
        });


      rejectBtn.setOnClickListener(v -> {
        Intent intent = new Intent(FloorDashboardActivity.this, RejectionReportsActivity.class);
        startActivity(intent);
    });

        scanToBoxBtn.setOnClickListener(v -> {
            Intent intent = new Intent(
                    FloorDashboardActivity.this,
                    BoxFillActivity.class
            );
            startActivity(intent);
        });

        scanStockBtn.setOnClickListener(v -> {
            Intent intent = new Intent(
                    FloorDashboardActivity.this,
                    WarehouseStockScanActivity.class
            );
            startActivity(intent);
        });

        boxConfirm.setOnClickListener(v -> {
            Intent intent = new Intent(
                    FloorDashboardActivity.this,
                    BoxConfirmActivity.class
            );
            startActivity(intent);
        });




    }
}
