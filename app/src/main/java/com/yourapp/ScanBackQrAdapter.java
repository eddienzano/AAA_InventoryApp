package com.yourapp;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ScanBackQrAdapter
        extends RecyclerView.Adapter<ScanBackQrAdapter.VH> {

    private final List<qrItem2> list;

    public ScanBackQrAdapter(List<qrItem2> list){
        this.list = list;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txt;

        VH(View v){
            super(v);
            txt = v.findViewById(android.R.id.text1);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent,int viewType){
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(20,20,20,20);
        tv.setTextSize(16);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h,int pos){
        qrItem2 i = list.get(pos);

        h.txt.setText(
                "✔ " + i.serial +
                        " | " + i.bucket +
                        " | " + i.length +
                        " | Qty: " + i.quantity
        );
    }

    @Override
    public int getItemCount(){
        return list.size();
    }
}