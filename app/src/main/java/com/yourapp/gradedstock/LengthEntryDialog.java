package com.yourapp.gradedstock;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.yourapp.R;

import java.util.HashMap;
import java.util.Locale;

public class LengthEntryDialog extends Dialog {

    private static final String TAG = "LengthEntryDialog";

    public interface Callback {
        void onSubmit(VarietyModel model);
    }

    public LengthEntryDialog(Context c,
                             VarietyModel model,
                             String farmName,
                             Callback cb) {
        super(c);
        setContentView(R.layout.dialog_length_entry);

        farmName = farmName == null ? "" : farmName.trim().toLowerCase();
        boolean isChui = farmName.contains("chui");

        Log.d(TAG, "Farm(received) = " + farmName + " | isChui = " + isChui);

        // ===== Always show base lengths =====
        setupLengthRow(R.id.len40, 40, model);
        setupLengthRow(R.id.len50, 50, model);
        setupLengthRow(R.id.len60, 60, model);
        setupLengthRow(R.id.len70, 70, model);
        setupLengthRow(R.id.len80, 80, model);

        // ===== Chui-only =====
        if (isChui) {
            setupLengthRow(R.id.len90, 90, model);
            setupLengthRow(R.id.len100, 100, model);
        } else {
            hideRow(R.id.len90);
            hideRow(R.id.len100);
        }

        // ===== Submit =====
        Button btnSubmit = findViewById(R.id.btnSubmitLengths);
        btnSubmit.setOnClickListener(v -> {

            HashMap<Integer, Integer> data = new HashMap<>();
            int[] lengths = {40, 50, 60, 70, 80, 90, 100};

            for (int len : lengths) {
                int rowId = getContext().getResources()
                        .getIdentifier("len" + len, "id", getContext().getPackageName());

                View row = findViewById(rowId);
                if (row == null || row.getVisibility() != View.VISIBLE) continue;

                EditText edt = row.findViewById(R.id.edtQty);
                if (edt == null) continue;

                String vStr = edt.getText().toString().trim();
                int qty = vStr.isEmpty() ? 0 : Integer.parseInt(vStr);

                data.put(len, qty);
                Log.d(TAG, "Collected len " + len + " = " + qty);
            }

            model.setLengths(data);
            cb.onSubmit(model);
            dismiss();
        });
    }

    // =========================
    // Helpers
    // =========================

    private void setupLengthRow(int rowId, int length, VarietyModel model) {
        View row = findViewById(rowId);
        if (row == null) {
            Log.e(TAG, "Row missing: len" + length);
            return;
        }

        row.setVisibility(View.VISIBLE);

        TextView txt = row.findViewById(R.id.txtLength);
        if (txt != null) txt.setText(length + " cm");

        EditText edt = row.findViewById(R.id.edtQty);
        int val = model.getLengths().containsKey(length)
                ? model.getLengths().get(length)
                : 0;

        edt.setText(String.valueOf(val));
        Log.d(TAG, "Prefilled len" + length + " = " + val);
    }

    private void hideRow(int rowId) {
        View row = findViewById(rowId);
        if (row != null) row.setVisibility(View.GONE);
    }
}
