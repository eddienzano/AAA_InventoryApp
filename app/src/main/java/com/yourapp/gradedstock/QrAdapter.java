package com.yourapp.gradedstock;

import android.view.*;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yourapp.R;

import java.util.ArrayList;

public class QrAdapter extends RecyclerView.Adapter<QrAdapter.Holder>{

    ArrayList<QrItem> list;

    public QrAdapter(ArrayList<QrItem> l){
        list=l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType){

        View v= LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_graded_qr,parent,false);

        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h,int i){

        QrItem item=list.get(i);

        h.bucket.setText(item.bucket_name);
        h.length.setText(item.length+" cm");
        h.stems.setText(item.bunches+" x "+item.stems);
    }

    @Override
    public int getItemCount(){
        return list.size();
    }

    static class Holder extends RecyclerView.ViewHolder{

        TextView bucket,length,stems;

        Holder(View v){

            super(v);

            bucket=v.findViewById(R.id.txtBucket);
            length=v.findViewById(R.id.txtLength);
            stems=v.findViewById(R.id.txtStems);
        }
    }
}