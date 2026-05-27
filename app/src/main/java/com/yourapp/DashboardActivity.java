package com.yourapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.yourapp.stocks.ScanActivity;
import com.yourapp.summers.PackhouseVerifyActivity;
import com.yourapp.summers.SummersScanOutActivity;


public class DashboardActivity extends AppCompatActivity {

    private MaterialButton btnSummersOut, btnScanIn, btnSummers, btnScanOut, btnSync, btnLogout, btnWrongScan, btnSFQ, btnStock, btnIntake, btnDampscan, btnStorageScan;
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
//        btnScanIn = findViewById(R.id.btnScanIn);
        btnIntake = findViewById(R.id.btnIntake);
        btnScanOut = findViewById(R.id.btnScanOut);
        btnStock= findViewById(R.id.btnStock);
        btnSync = findViewById(R.id.btnSync);
        btnSFQ = findViewById(R.id.btnSFQ);
        btnLogout = findViewById(R.id.btnLogout);
        shimmerOverlay = findViewById(R.id.shimmerOverlay);
        txtUserInfo = findViewById(R.id.txtUserInfo);
        btnScanBackColdroom = findViewById(R.id.btnScanBackColdroom);
        btnWrongScan = findViewById(R.id.btnWrongScan);
        btnDampscan = findViewById(R.id.btnDampscan);
        btnStorageScan = findViewById(R.id.btnStorageScan);
        btnSummers=findViewById(R.id.btnSummers);
        btnSummersOut = findViewById(R.id.btnSummersOut);


        // Get values passed from LoginActivity
        Intent intent = getIntent();
        String username = getIntent().getStringExtra("username");
        int userId = getIntent().getIntExtra("user_id", -1);

        // Show user info
        txtUserInfo.setText("User: " + username + " (ID: " + userId + ")");

        // Start shimmer then immediately stop it (no preloading)
        shimmerOverlay.setVisibility(View.VISIBLE);
        shimmerOverlay.startShimmer();
        shimmerOverlay.stopShimmer();
        shimmerOverlay.setVisibility(View.GONE);

        // Set button actions
//        btnScanIn.setOnClickListener(v -> {
//            Intent in = new Intent(DashboardActivity.this, IntakeActivity.class);
//            in.putExtra("user_id", userId);
//            in.putExtra("username", username);
//            startActivity(in);
//        });

        btnSummersOut.setOnClickListener(v -> {
            Intent out = new Intent(
                    DashboardActivity.this,
                    SummersScanOutActivity.class
            );
            out.putExtra("user_id", userId);
            out.putExtra("username", username);
            startActivity(out);
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

        btnWrongScan.setOnClickListener(v -> {
            Intent back = new Intent(DashboardActivity.this, WrongScanActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnSync.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, ScanToQuarantineActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnSFQ.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, ScanFromQuarantineActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnStock.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, ScanActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnDampscan.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, ScanToDampActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnStorageScan.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, ScanToSorageActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnSummers.setOnClickListener(v -> {
            // TODO: implement SQLite sync functionality later
            Intent back = new Intent(DashboardActivity.this, PackhouseVerifyActivity.class);
            back.putExtra("user_id", userId);
            back.putExtra("username", username);
            startActivity(back);
        });

        btnLogout.setOnClickListener(v -> finish());
    }
}
