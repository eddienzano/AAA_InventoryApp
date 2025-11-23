package com.yourapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RejectionAdapter extends RecyclerView.Adapter<RejectionAdapter.ViewHolder> {

    private List<RejectionEntry> rejectionList;

    public RejectionAdapter(List<RejectionEntry> rejectionList) {
        this.rejectionList = rejectionList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rejection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RejectionEntry entry = rejectionList.get(position);
        holder.tvVariety.setText(entry.varietyName);
//        holder.tvLengthStems.setText(entry.length + " - " + entry.stems + " stems");
        holder.tvCellTable.setText("Cell: " + entry.cellNo + " | Table: " + entry.tableNo);
        holder.tvReason.setText(entry.rejectionReasonName != null ? entry.rejectionReasonName : "");
    }

    @Override
    public int getItemCount() {
        return rejectionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvVariety, tvLengthStems, tvCellTable, tvReason;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVariety = itemView.findViewById(R.id.tvVariety);
            tvLengthStems = itemView.findViewById(R.id.tvLengthStems);
            tvCellTable = itemView.findViewById(R.id.tvCellTable);
            tvReason = itemView.findViewById(R.id.tvReason);
        }
    }
}
