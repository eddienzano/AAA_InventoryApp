package com.yourapp.summers;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;

import org.json.JSONObject;

import java.util.List;

public class VerifyAdapter extends RecyclerView.Adapter<VerifyAdapter.ViewHolder> {

    private final List<JSONObject> list;

    public VerifyAdapter(List<JSONObject> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_verify_variety, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        JSONObject obj = list.get(position);

        String name = obj.optString("variety_name");
        int originalQty = obj.optInt("original_qty");
        int verifiedQty = obj.optInt("verified_qty");

        holder.varietyName.setText(name);
        holder.originalQty.setText("Original: " + originalQty);

        // IMPORTANT: remove watcher before setting text
        if (holder.watcher != null) {
            holder.verifiedQty.removeTextChangedListener(holder.watcher);
        }

        holder.verifiedQty.setText(String.valueOf(verifiedQty));

        // COLOR STATE
        updateColor(holder, originalQty, verifiedQty);

        // NEW WATCHER
        holder.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {

                int adapterPos = holder.getBindingAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;

                JSONObject item = list.get(adapterPos);

                try {
                    int newQty = 0;

                    if (!s.toString().isEmpty()) {
                        newQty = Integer.parseInt(s.toString());
                    }

                    item.put("verified_qty", newQty);

                    int original = item.optInt("original_qty");

                    updateColor(holder, original, newQty);

                } catch (Exception ignored) {}
            }
        };

        holder.verifiedQty.addTextChangedListener(holder.watcher);

        // DELETE
        holder.deleteBtn.setOnClickListener(v -> {

            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            list.remove(pos);
            notifyItemRemoved(pos);
        });

        // animation (safe lightweight)
        holder.itemView.setAlpha(0f);
        holder.itemView.animate().alpha(1f).setDuration(200).start();
    }

    private void updateColor(ViewHolder holder, int original, int verified) {
        if (original != verified) {
            holder.container.setBackgroundColor(Color.parseColor("#FFCDD2"));
        } else {
            holder.container.setBackgroundColor(Color.parseColor("#C8E6C9"));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView varietyName, originalQty;
        EditText verifiedQty;
        Button deleteBtn;
        LinearLayout container;

        TextWatcher watcher;

        ViewHolder(View itemView) {
            super(itemView);

            container = (LinearLayout) itemView;
            varietyName = itemView.findViewById(R.id.varietyName);
            originalQty = itemView.findViewById(R.id.originalQty);
            verifiedQty = itemView.findViewById(R.id.verifiedQty);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}