package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class FloorDashboardActivity extends AppCompatActivity {

    TextView floorWelcomeText;
    Button scanToWipBtn, scanToBoxBtn, scanOutWipBtn, scanOutReworkBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floor_dashboard);

        floorWelcomeText = findViewById(R.id.floorWelcomeText);
        scanToWipBtn = findViewById(R.id.scanToWipBtn);
        scanToBoxBtn = findViewById(R.id.scanToBoxBtn);
        scanOutWipBtn = findViewById(R.id.scanOutWipBtn);
        scanOutReworkBtn = findViewById(R.id.scanOutReworkBtn);

        String username = getIntent().getStringExtra("username");
        floorWelcomeText.setText("Floor Dashboard - " + username);

        // Button actions - link to your scanning activities
        scanToWipBtn.setOnClickListener(v -> {
            Intent intent = new Intent(FloorDashboardActivity.this, ScanToWipActivity.class);
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
    }
}
