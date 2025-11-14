package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    private MaterialButton btnScanIn, btnScanOut, btnSync, btnLogout, btnIntake;
    private MaterialButton btnScanBackColdroom;
    private ShimmerFrameLayout shimmerOverlay;
    private TextView txtUserInfo;

    private int userId;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Bind UI elements
        btnScanIn = findViewById(R.id.btnScanIn);
        btnIntake = findViewById(R.id.btnIntake);
        btnScanOut = findViewById(R.id.btnScanOut);
        btnSync = findViewById(R.id.btnSync);
        btnLogout = findViewById(R.id.btnLogout);
        shimmerOverlay = findViewById(R.id.shimmerOverlay);
        txtUserInfo = findViewById(R.id.txtUserInfo);
        btnScanBackColdroom = findViewById(R.id.btnScanBackColdroom);

        // Get values passed from LoginActivity
        Intent intent = getIntent();
        userId = intent.getIntExtra("user_id", -1);
        username = intent.getStringExtra("username");

        // Show user info
        txtUserInfo.setText("User: " + username + " (ID: " + userId + ")");

        // Start shimmer then immediately stop it (no preloading)
        shimmerOverlay.setVisibility(View.VISIBLE);
        shimmerOverlay.startShimmer();
        shimmerOverlay.stopShimmer();
        shimmerOverlay.setVisibility(View.GONE);

        // Set button actions
        btnScanIn.setOnClickListener(v -> {
            Intent in = new Intent(DashboardActivity.this, IntakeActivity.class);
            in.putExtra("user_id", userId);
            in.putExtra("username", username);
            startActivity(in);
        });

        // Set button actions
        btnIntake.setOnClickListener(v -> {
            Intent in = new Intent(DashboardActivity.this, NewIntakeActivity.class);
            in.putExtra("user_id", userId);
            in.putExtra("username", username);
            startActivity(in);
        });

        btnScanOut.setOnClickListener(v -> {
            Intent out = new Intent(DashboardActivity.this, ScanOutActivity.class);
            out.putExtra("user_id", userId);
            out.putExtra("username", username);
            startActivity(out);
        });

        btnScanBackColdroom.setOnClickListener(v -> {
            Intent back = new Intent(DashboardActivity.this, ScanBackColdroomActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnSync.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
        });

        btnLogout.setOnClickListener(v -> finish());
    }
}
