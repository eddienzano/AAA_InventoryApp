package com.yourapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RejectionRecyclerAdapter
        extends RecyclerView.Adapter<RejectionRecyclerAdapter.VH> {

    private JSONArray data;
    private int tab; // Tab position to determine binding

    public RejectionRecyclerAdapter(JSONArray data, int tabPosition) {
        this.data = data;
        this.tab = tabPosition;
    }

    public void updateData(JSONArray newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_rejection, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH h, int position) {
        JSONObject o = data.optJSONObject(position);
        if (o == null) return;

        switch (tab) {

            case 0: // Tab 0: By Variety
                h.variety.setText(o.optString("variety"));
                h.total.setText(String.valueOf(o.optInt("total_stems")));

                // Hide other columns
                h.thrips.setVisibility(View.GONE);
                h.mech.setVisibility(View.GONE);
                h.botrytis.setVisibility(View.GONE);
                h.powdery.setVisibility(View.GONE);
                h.downy.setVisibility(View.GONE);
                h.others.setVisibility(View.GONE);

                if ("Total".equalsIgnoreCase(o.optString("variety"))) {
                    h.itemView.setBackgroundColor(0x30FFC107);
                } else {
                    h.itemView.setBackgroundColor(0x00000000);
                }
                break;

            case 1: // Tab 1: By Reason
                h.variety.setText(o.optString("category")); // Reason
                h.total.setText(String.valueOf(o.optInt("total_stems"))); // Total Stems
                h.thrips.setText(String.valueOf(o.optDouble("percent")) + "%"); // % Level

                JSONArray photos = o.optJSONArray("photos");
                if (photos != null && photos.length() > 0) {
                    try {
                        h.mech.setText(photos.join(", ").replace("\"","")); // show photo paths
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    h.mech.setText("-"); // no photos
                }

                // Hide unused columns
                h.botrytis.setVisibility(View.GONE);
                h.powdery.setVisibility(View.GONE);
                h.downy.setVisibility(View.GONE);
                h.others.setVisibility(View.GONE);

                if ("Total".equalsIgnoreCase(o.optString("category"))) {
                    h.itemView.setBackgroundColor(0x30FFC107);
                } else {
                    h.itemView.setBackgroundColor(0x00000000);
                }
                break;

            default: // All other tabs (full table)
                h.variety.setText(o.optString("variety"));
                h.thrips.setText(String.valueOf(o.optInt("thrips")));
                h.mech.setText(String.valueOf(o.optInt("mech")));
                h.botrytis.setText(String.valueOf(o.optInt("botrytis")));
                h.powdery.setText(String.valueOf(o.optInt("powdery")));
                h.downy.setText(String.valueOf(o.optInt("downy")));
                h.others.setText(String.valueOf(o.optInt("others")));
                h.total.setText(String.valueOf(o.optInt("total")));

                // Ensure all columns are visible
                h.thrips.setVisibility(View.VISIBLE);
                h.mech.setVisibility(View.VISIBLE);
                h.botrytis.setVisibility(View.VISIBLE);
                h.powdery.setVisibility(View.VISIBLE);
                h.downy.setVisibility(View.VISIBLE);
                h.others.setVisibility(View.VISIBLE);

                if ("Total".equalsIgnoreCase(o.optString("variety"))) {
                    h.itemView.setBackgroundColor(0x30FFC107);
                } else {
                    h.itemView.setBackgroundColor(0x00000000);
                }
                break;
        }
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.length();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView variety, thrips, mech, botrytis,
                powdery, downy, others, total;

        VH(View v) {
            super(v);
            variety   = v.findViewById(R.id.tvVariety);
            thrips    = v.findViewById(R.id.tvThrips);
            mech      = v.findViewById(R.id.tvMech);
            botrytis  = v.findViewById(R.id.tvBotrytis);
            powdery   = v.findViewById(R.id.tvPowdery);
            downy     = v.findViewById(R.id.tvDowny);
            others    = v.findViewById(R.id.tvOthers);
            total     = v.findViewById(R.id.tvTotal);
        }
    }
}
