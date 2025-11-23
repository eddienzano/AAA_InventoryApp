package com.yourapp;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filterable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class VarietyAdapter3 extends ArrayAdapter<String> implements Filterable {

    private final List<Integer> varietyIds;

    public VarietyAdapter3(@NonNull Context context, @NonNull List<String> varietyNames, @NonNull List<Integer> varietyIds) {
        super(context, android.R.layout.simple_dropdown_item_1line, varietyNames);
        this.varietyIds = varietyIds;
    }

    public int getVarietyId(int position) {
        return varietyIds.get(position);
    }

    @Nullable
    @Override
    public String getItem(int position) {
        return super.getItem(position);
    }
}
