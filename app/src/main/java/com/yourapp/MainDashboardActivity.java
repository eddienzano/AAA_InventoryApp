package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainDashboardActivity extends AppCompatActivity {

    TextView welcomeText;
    Button intakeBtn, floorBtn, qcBtn1, qcBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        welcomeText = findViewById(R.id.welcomeText);
        intakeBtn = findViewById(R.id.intakeBtn);
        floorBtn = findViewById(R.id.floorBtn);
        qcBtn = findViewById(R.id.qcBtn);
        qcBtn1 = findViewById(R.id.qcBtn1);


        int userId = getIntent().getIntExtra("user_id", -1);

        // Get username from Login
        String username = getIntent().getStringExtra("username");

        if (username != null && !username.isEmpty()) {
            welcomeText.setText("Welcome, " + username + " (ID: " + userId + ")!");
        } else {
            welcomeText.setText("Welcome!");
        }

        intakeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, DashboardActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        floorBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, FloorDashboardActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        qcBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, RejectionActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        qcBtn1.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, RejectActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

    }
}
