package com.yourapp.summers;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import java.util.List;

public class VarietyAdapter extends RecyclerView.Adapter<VarietyAdapter.ViewHolder> {

    List<JSONObject> list;

    public VarietyAdapter(List<JSONObject> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject obj = list.get(position);

        holder.title.setText(obj.optString("variety_name"));
        holder.subtitle.setText("Qty: " + obj.optString("quantity"));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle;

        ViewHolder(View v) {
            super(v);
            title = v.findViewById(android.R.id.text1);
            subtitle = v.findViewById(android.R.id.text2);
        }
    }
}