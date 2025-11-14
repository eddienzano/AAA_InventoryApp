package com.yourapp; // change this to match your package

import android.view.View;
import android.widget.AdapterView;

/**
 * A simple listener wrapper that executes a Runnable whenever an item is selected.
 * Perfect for lambdas like: new SimpleItemSelectedListener(this::toggleScanInput)
 */
public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final Runnable onItemSelectedAction;

    public SimpleItemSelectedListener(Runnable onItemSelectedAction) {
        this.onItemSelectedAction = onItemSelectedAction;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (onItemSelectedAction != null) onItemSelectedAction.run();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // No action needed
    }
}
