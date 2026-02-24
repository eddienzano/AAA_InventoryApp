package com.yourapp.gradedstock;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.yourapp.R;

public class ConfirmEntryActivity extends AppCompatActivity {

    VarietyModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_entry);

        model = (VarietyModel) getIntent().getSerializableExtra("variety");

        TextView txtName = findViewById(R.id.txtConfirmVariety);
        TextView txtData = findViewById(R.id.txtConfirmData);
        Button btnConfirm = findViewById(R.id.btnConfirm);

        txtName.setText(model.getName());
        txtData.setText(model.getSummary());

        btnConfirm.setOnClickListener(v -> {
            model.setCompleted(true);

            // TODO: SAVE TO DB / API HERE

            Intent i = new Intent();
            i.putExtra("variety", model);
            setResult(RESULT_OK, i);
            finish();
        });
    }
}
