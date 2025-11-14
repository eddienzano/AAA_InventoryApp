package com.yourapp;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class QrAdapter extends RecyclerView.Adapter<QrAdapter.ViewHolder> {

    private ArrayList<NewIntakeActivity.QrItem> qrList;

    public QrAdapter(ArrayList<NewIntakeActivity.QrItem> qrList) {
        this.qrList = qrList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_qr, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NewIntakeActivity.QrItem item = qrList.get(position);

        holder.tvQr.setText(item.qr);
        holder.etStems.setText(String.valueOf(item.stems));

        // Remove button
        holder.btnRemove.setOnClickListener(v -> {
            qrList.remove(holder.getAdapterPosition());
            notifyItemRemoved(holder.getAdapterPosition());
            notifyItemRangeChanged(holder.getAdapterPosition(), qrList.size());
        });

        // Stems input
        holder.etStems.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int stems = Integer.parseInt(s.toString());
                    if (stems <= 0) stems = 1;
                    item.stems = stems;
                } catch (NumberFormatException e) {
                    item.stems = 1;
                }
            }
        });

        // Dynamic invalid highlight
        if (item.isInvalid) {
            holder.itemView.setBackgroundColor(holder.itemView.getResources().getColor(android.R.color.holo_red_light));
        } else {
            holder.itemView.setBackgroundColor(holder.itemView.getResources().getColor(android.R.color.white));
        }
    }

    @Override
    public int getItemCount() {
        return qrList.size();
    }

    public void markInvalid(String qr, String errorMsg) {
        for (NewIntakeActivity.QrItem item : qrList) {
            if (item.qr.equals(qr)) {
                item.isInvalid = true;
                item.errorMessage = errorMsg;
                break;
            }
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvQr;
        EditText etStems;
        ImageButton btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQr = itemView.findViewById(R.id.tvQr);
            etStems = itemView.findViewById(R.id.etStems);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
