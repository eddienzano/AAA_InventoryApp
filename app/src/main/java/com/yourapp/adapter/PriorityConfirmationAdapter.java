package com.yourapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;
import com.yourapp.models.PriorityLength;
import com.yourapp.models.PriorityRequestItem;

import java.util.List;

public class PriorityConfirmationAdapter extends RecyclerView.Adapter<PriorityConfirmationAdapter.ViewHolder> {

    private final Context context;
    private final List<PriorityRequestItem> items;
    private final String currentManager;

    public PriorityConfirmationAdapter(Context context, List<PriorityRequestItem> items, String currentManager) {
        this.context = context;
        this.items = items;
        this.currentManager = currentManager;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_priority_confirmation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PriorityRequestItem pi = items.get(position);

        holder.txtFarm.setText(pi.farmName);
        holder.txtVariety.setText(pi.varietyName);
        holder.txtStatus.setText(pi.status);

        holder.lengthsContainer.removeAllViews();

        for (PriorityLength pl : pi.lengths) {
            View lengthView = LayoutInflater.from(context)
                    .inflate(R.layout.item_priority_length, holder.lengthsContainer, false);

            TextView txtLength = lengthView.findViewById(R.id.txtLength);
            TextView txtNeeded = lengthView.findViewById(R.id.txtNeeded);
            TextView txtReceived = lengthView.findViewById(R.id.txtReceived);
            EditText editConfirmed = lengthView.findViewById(R.id.editConfirmed);

            txtLength.setText(pl.length + " cm");
            txtNeeded.setText(String.valueOf(pl.needed));
            txtReceived.setText(String.valueOf(pl.received));

            if (pl.zero) { // fully delivered
                editConfirmed.setText("0");
                editConfirmed.setEnabled(false);
                editConfirmed.setTextColor(Color.GRAY);
            } else if (pl.editable) {
                int val = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val = pi.confirmedByManager.getOrDefault(currentManager, 0);
                }
                editConfirmed.setText(val > 0 ? String.valueOf(val) : "_");
                editConfirmed.setEnabled(true);
                editConfirmed.setTextColor(Color.BLACK);

                editConfirmed.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        int v = 0;
                        try { v = Integer.parseInt(s.toString()); } catch (NumberFormatException ignored) {}
                        pi.confirmedByManager.put(currentManager, v);
                    }
                });
            } else {
                editConfirmed.setText(String.valueOf(pl.received));
                editConfirmed.setEnabled(false);
                editConfirmed.setTextColor(Color.GRAY);
            }

            holder.lengthsContainer.addView(lengthView);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtFarm, txtVariety, txtStatus;
        LinearLayout lengthsContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtFarm = itemView.findViewById(R.id.txtFarm);
            txtVariety = itemView.findViewById(R.id.txtVariety);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            lengthsContainer = itemView.findViewById(R.id.lengthsContainer);
        }
    }
}
