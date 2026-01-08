package com.yourapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RejectionHistoryAdapter
        extends RecyclerView.Adapter<RejectionHistoryAdapter.VH> {

    private final List<RejectionHistoryItem> data;

    public RejectionHistoryAdapter(List<RejectionHistoryItem> data) {
        this.data = data;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView vVariety, vReason, vDetails, vTime;
        VH(View v) {
            super(v);
            vVariety = v.findViewById(R.id.txtVariety);
            vReason  = v.findViewById(R.id.txtReason);
            vDetails = v.findViewById(R.id.txtDetails);
            vTime    = v.findViewById(R.id.txtTime);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.row_rejection_history, p, false));
    }

    @Override
    public void onBindViewHolder(VH h, int i) {
        RejectionHistoryItem r = data.get(i);
        h.vVariety.setText(r.variety);
        h.vReason.setText("Reason: " + r.reason);
        h.vDetails.setText(
                r.stems + " stems | " + r.length + "cm | " + r.greenhouse
        );
        h.vTime.setText(r.time);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
