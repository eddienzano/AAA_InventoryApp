package com.yourapp;

import android.content.Context;
import android.widget.ArrayAdapter;
import java.util.List;

public class VarietyAdapter2 extends ArrayAdapter<String> {

    private final List<Integer> ids;

    public VarietyAdapter2(Context context, List<String> names, List<Integer> ids) {
        super(context, R.layout.spinner_dropdown_item, names);
        this.ids = ids;
    }

    public int getVarietyId(int position) {
        return ids.get(position);
    }
}
