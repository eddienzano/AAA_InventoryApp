package com.yourapp.boxfill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BoxFillAdapter extends RecyclerView.Adapter<BoxFillAdapter.ViewHolder> {

    private final List<BoxFillItem> items;

    public BoxFillAdapter(List<BoxFillItem> items) {
        this.items = items;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ViewHolder(View v) {
            super(v);
            text = v.findViewById(android.R.id.text1);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup p, int v) {
        return new ViewHolder(
                LayoutInflater.from(p.getContext())
                        .inflate(android.R.layout.simple_list_item_1, p, false)
        );
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int i) {
        h.text.setText("BOX ID: " + items.get(i).boxId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
