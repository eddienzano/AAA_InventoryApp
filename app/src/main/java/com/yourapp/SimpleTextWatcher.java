package com.yourapp;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Not used
    }

    @Override
    public void afterTextChanged(Editable s) {
        // Not used
    }

    @Override
    public abstract void onTextChanged(CharSequence s, int start, int before, int count);
}
