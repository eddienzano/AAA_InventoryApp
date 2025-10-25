package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainDashboardActivity extends AppCompatActivity {

    TextView welcomeText;
    Button intakeBtn, floorBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_dashboard);

        welcomeText = findViewById(R.id.welcomeText);
        intakeBtn = findViewById(R.id.intakeBtn);
        floorBtn = findViewById(R.id.floorBtn);

        // Get username from Login
        String username = getIntent().getStringExtra("username");

        if (username != null && !username.isEmpty()) {
            welcomeText.setText("Welcome, " + username + "!");
        } else {
            welcomeText.setText("Welcome!");
        }

        // Navigate to Intake (Existing DashboardActivity)
        intakeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, DashboardActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });

        // Navigate to FloorDashboard (new)
        floorBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainDashboardActivity.this, FloorDashboardActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });
    }
}
