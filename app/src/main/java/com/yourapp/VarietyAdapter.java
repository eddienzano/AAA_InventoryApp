package com.yourapp;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.List;

public class VarietyAdapter extends ArrayAdapter<String> {
    public VarietyAdapter(Context context, List<String> varieties) {
        super(context, android.R.layout.simple_dropdown_item_1line, varieties);
    }
}
