package com.yourapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class RejectionTabFragment extends Fragment {

    private static final String ARG_TAB  = "tab";
    private static final String ARG_FARM = "farm";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO   = "to";

    private RecyclerView recycler;
    private LinearLayout headerContainer;

    public static RejectionTabFragment newInstance(int tab, String farm, String from, String to) {
        Bundle b = new Bundle();
        b.putInt(ARG_TAB, tab);
        b.putString(ARG_FARM, farm);
        b.putString(ARG_FROM, from);
        b.putString(ARG_TO, to);

        RejectionTabFragment f = new RejectionTabFragment();
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_rejection_tab, container, false);

        recycler = v.findViewById(R.id.recycler);
        headerContainer = v.findViewById(R.id.headerContainer);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        Bundle args = getArguments();
        if (args != null) {
            loadData(
                    args.getInt(ARG_TAB),
                    args.getString(ARG_FARM),
                    args.getString(ARG_FROM),
                    args.getString(ARG_TO)
            );
        }

        return v;
    }

    private void loadData(int tab, String farm, String from, String to) {

        String url = "https://www.aaagrowers.co.ke/inventory/api/rejections_report.php"
                + "?tab=" + tab
                + "&farm=" + farm
                + "&from=" + from
                + "&to=" + to;

        Request request = new Request.Builder().url(url).build();

        ApiClient.getInstance().getClient().newCall(request)
                .enqueue(new Callback() {

                    @Override
                    public void onFailure(Call call, IOException e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(),
                                            "Network error",
                                            Toast.LENGTH_SHORT).show()
                            );
                        }
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {

                        if (!response.isSuccessful() || response.body() == null) return;

                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray data = root.getJSONArray("data");

                            // Append total row for Tab 1
                            if (tab == 1 && data.length() > 0) {
                                int totalStems = 0;
                                for (int i = 0; i < data.length(); i++) {
                                    totalStems += data.optJSONObject(i).optInt("total_stems");
                                }
                                JSONObject totalRow = new JSONObject();
                                totalRow.put("category", "Total");
                                totalRow.put("total_stems", totalStems);
                                totalRow.put("percent", 100);
                                totalRow.put("photos", new JSONArray());
                                data.put(totalRow);
                            }

                            if (!isAdded() || getView() == null) return;

                            requireActivity().runOnUiThread(() -> {
                                setupHeaderAndAdapter(tab, data);
                            });

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void setupHeaderAndAdapter(int tabPosition, JSONArray data) {
        headerContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        switch (tabPosition) {
            case 0:
                inflater.inflate(R.layout.header_by_variety, headerContainer, true);
                break;

            case 1:
                inflater.inflate(R.layout.header_by_reason, headerContainer, true);
                break;

            case 2:
                inflater.inflate(R.layout.header_by_variety_by_reason, headerContainer, true);
                break;
        }

        recycler.setAdapter(new RejectionRecyclerAdapter(data, tabPosition));
    }
}
