package com.yourapp;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.List;

public class BuncherAdapter extends ArrayAdapter<String> {
    public BuncherAdapter(Context context, List<String> bunchers) {
        super(context, android.R.layout.simple_dropdown_item_1line, bunchers);
    }
}
