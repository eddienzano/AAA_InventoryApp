package com.yourapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.card.MaterialCardView;
import com.yourapp.models.PriorityItem;
import com.yourapp.R;
import java.util.List;

public class PriorityAdapter extends RecyclerView.Adapter<PriorityAdapter.VH> {

    private final List<PriorityItem> items;
    private final Context ctx;

    public PriorityAdapter(Context ctx, List<PriorityItem> items) {
        this.items = items;
        this.ctx = ctx;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.priority_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PriorityItem it = items.get(position);
        h.tvTitle.setText(it.farmName + " — " + it.varietyName);
        h.tvSub.setText("Variety ID: " + it.varietyId + " • Farm ID: " + it.farmId);

        // status styling
        String s = (it.status == null) ? "Pending" : it.status;
        h.chipStatus.setText(s);

        switch (s.toLowerCase()) {
            case "completed":
                h.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light);
                h.chipStatus.setTextColor(Color.WHITE);
                break;
            case "processing":
                h.chipStatus.setChipBackgroundColorResource(android.R.color.holo_blue_light);
                h.chipStatus.setTextColor(Color.WHITE);
                break;
            default:
                h.chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light);
                h.chipStatus.setTextColor(Color.WHITE);
                break;
        }

        // populate chips
        h.chipGroup.removeAllViews();
        if (it.lengths != null) {
            for (PriorityItem.LengthRow lr : it.lengths) {
                String chipText = lr.length + " cm • N: " + lr.needed + " | R: " + lr.received;
                Chip c = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_length_chip, h.chipGroup, false);
                c.setText(chipText);

                // mark delivered vs pending
                if (lr.received >= lr.needed) {
                    c.setChipBackgroundColorResource(android.R.color.holo_green_light);
                    c.setTextColor(Color.WHITE);
                } else {
                    c.setChipBackgroundColorResource(android.R.color.darker_gray);
                    c.setTextColor(Color.WHITE);
                }
                h.chipGroup.addView(c);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        Chip chipStatus;
        ChipGroup chipGroup;
        MaterialCardView card;
        VH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvFarmVariety);
            tvSub = v.findViewById(R.id.tvSub);
            chipStatus = v.findViewById(R.id.chipStatus);
            chipGroup = v.findViewById(R.id.chipGroupLengths);
            card = v.findViewById(R.id.card);
        }
    }
}
