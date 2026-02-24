package com.yourapp.gradedstock;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;

import java.util.List;

public class VarietyAdapter extends RecyclerView.Adapter<VarietyAdapter.VH> {

    private final Activity activity;
    private final List<VarietyModel> list;

    public VarietyAdapter(Activity activity, List<VarietyModel> list) {
        this.activity = activity;
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_variety_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VarietyModel model = list.get(position);

        h.txtName.setText(model.getName());

        if (model.isCompleted()) {
            h.txtStatus.setText("Completed");
            h.txtStatus.setTextColor(0xFF4CAF50);
            h.txtSummary.setText(model.getSummary());
            h.img.setAlpha(0.5f);
        } else {
            h.txtStatus.setText("Pending");
            h.txtStatus.setTextColor(0xFFFF9800);
            h.txtSummary.setText("");
            h.img.setAlpha(1f);
        }

        h.itemView.setOnClickListener(v -> {
            if (model.isCompleted()) {
                Toast.makeText(activity,
                        "Already submitted for this shift",
                        Toast.LENGTH_SHORT).show();
                return;
            }


            GradedEntryActivity act = (GradedEntryActivity) activity;
            String farmName = act.getSelectedFarmName();

            if (model.getLengths().isEmpty()) {
                model.getLengths().put(40, 0);
                model.getLengths().put(50, 0);
                model.getLengths().put(60, 0);
                model.getLengths().put(70, 0);
                model.getLengths().put(80, 0);

                if (farmName != null && farmName.toLowerCase().contains("chui")) {
                    model.getLengths().put(90, 0);
                    model.getLengths().put(100, 0);
                }
            }

            new LengthEntryDialog(
                    activity,
                    model,
                    farmName,
                    updatedModel -> {
                        act.confirmAndSubmit(updatedModel);
                        notifyItemChanged(position);
                    }
            ).show();
        });



    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView txtName, txtStatus, txtSummary;

        VH(View v) {
            super(v);
            img = v.findViewById(R.id.imgVariety);
            txtName = v.findViewById(R.id.txtVarietyName);
            txtStatus = v.findViewById(R.id.txtStatus);
            txtSummary = v.findViewById(R.id.txtSummary);
        }
    }
}
