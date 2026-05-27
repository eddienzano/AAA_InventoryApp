package com.yourapp.summers;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import androidx.recyclerview.widget.RecyclerView;
import com.yourapp.R;

import java.util.List;

public class BucketAdapter extends RecyclerView.Adapter<BucketAdapter.ViewHolder> {

    List<BucketItem> items;

    public BucketAdapter(List<BucketItem> items) {
        this.items = items;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView variety, max;
        EditText qty;

        public ViewHolder(View v) {
            super(v);
            variety = v.findViewById(R.id.varietyName);
            max = v.findViewById(R.id.maxQty);
            qty = v.findViewById(R.id.inputQty);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bucket, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int position) {

        BucketItem item = items.get(position);

        h.variety.setText(item.variety);
        h.max.setText("Max: " + item.maxQty);
        h.qty.setText(item.enteredQty == 0 ? "" : String.valueOf(item.enteredQty));

        h.qty.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int val = s.toString().isEmpty() ? 0 : Integer.parseInt(s.toString());
                    item.enteredQty = val;
                } catch (Exception e) {
                    item.enteredQty = 0;
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<BucketItem> getItems() {
        return items;
    }
}