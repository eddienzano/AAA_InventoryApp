package com.yourapp.warehouse;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WarehouseScanAdapter
        extends RecyclerView.Adapter<WarehouseScanAdapter.VH> {

    private final List<String> items;

    public WarehouseScanAdapter(List<String> items) {
        this.items = items;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {

        TextView tv = new TextView(parent.getContext());
        tv.setPadding(24, 20, 24, 20);
        tv.setTextSize(16f);

        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        holder.tv.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv;

        VH(TextView itemView) {
            super(itemView);
            tv = itemView;
        }
    }
}
