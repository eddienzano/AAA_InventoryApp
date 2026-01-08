package com.yourapp;


import android.app.DatePickerDialog;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.time.LocalDate;

public class RejectionReportsActivity extends AppCompatActivity {

    private Spinner spinnerFarm;
    private EditText etFromDate, etToDate;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    private String farmId = "";
    private String fromDate;
    private String toDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rejection_reports);

        spinnerFarm = findViewById(R.id.spinnerFarm);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        setupFarmSpinner();
        setupDates();

        viewPager.setAdapter(new RejectionPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(getTabTitle(position))
        ).attach();
    }

    private void setupFarmSpinner() {
        String[] farms = {"All Farms", "Simba", "Chui"};
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, farms);
        spinnerFarm.setAdapter(adapter);

        spinnerFarm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                farmId = pos == 0 ? "" : String.valueOf(pos); // 1 Simba, 2 Chui
                refreshTabs();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupDates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fromDate = toDate = LocalDate.now().toString();
        }

        etFromDate.setText(fromDate);
        etToDate.setText(toDate);

        etFromDate.setOnClickListener(v -> pickDate(true));
        etToDate.setOnClickListener(v -> pickDate(false));
    }

    private void pickDate(boolean isFrom) {
        LocalDate today = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            today = LocalDate.now();
        }
        DatePickerDialog dlg = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dlg = new DatePickerDialog(this,
                    (view, y, m, d) -> {
                        String date = String.format("%04d-%02d-%02d", y, m + 1, d);
                        if (isFrom) {
                            fromDate = date;
                            etFromDate.setText(date);
                        } else {
                            toDate = date;
                            etToDate.setText(date);
                        }
                        refreshTabs();
                    },
                    today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()
            );
        }
        dlg.show();
    }

    private void refreshTabs() {
        ((RejectionPagerAdapter) viewPager.getAdapter()).updateFilters(farmId, fromDate, toDate);
    }

    private String getTabTitle(int pos) {
        switch (pos) {
            case 0: return "By Variety";
            case 1: return "By Reason";
            case 2: return "Variety & Reason";
            case 3: return "%";
            case 4: return "By Time";
            case 5: return "Comments";
            case 6: return "Greenhouse";
            default: return "";
        }
    }
}
