package com.yourapp;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.yourapp.adapter.PriorityAdapter;
import com.yourapp.models.PriorityItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PriorityActivity extends AppCompatActivity {

    private static final String API_URL = "https://www.aaagrowers.co.ke/inventory/priority/get_priority.php";
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private ShimmerFrameLayout shimmer;
    private View errorLayout;
    private TextView errorMsg;
    private MaterialButton retryBtn;
    private PriorityAdapter adapter;
    private List<PriorityItem> items = new ArrayList<>();

    private final Handler handler = new Handler();
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            fetchData();
            handler.postDelayed(this, 10_000); // 10 seconds
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_priority);

        recycler = findViewById(R.id.recycler);
        swipe = findViewById(R.id.swipeRefresh);
        shimmer = findViewById(R.id.shimmer);
        errorLayout = findViewById(R.id.errorLayout);
        errorMsg = findViewById(R.id.errorMsg);
        retryBtn = findViewById(R.id.retryBtn);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PriorityAdapter(this, items);
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> fetchData());

        retryBtn.setOnClickListener(v -> fetchData());

        // initial load
        showLoading(true);
        fetchData();

        // start auto-refresh
        handler.postDelayed(refresher, 10_000);
    }

    private void showLoading(boolean loading) {
        if (loading) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
            recycler.setVisibility(View.GONE);
            errorLayout.setVisibility(View.GONE);
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void fetchData() {
        swipe.setRefreshing(true);
        errorLayout.setVisibility(View.GONE);

        StringRequest req = new StringRequest(Request.Method.GET, API_URL,
                response -> {
                    swipe.setRefreshing(false);
                    parseResponse(response);
                },
                error -> {
                    swipe.setRefreshing(false);
                    if (items.isEmpty()) {
                        // show error only if no data
                        errorLayout.setVisibility(View.VISIBLE);
                        errorMsg.setText("Unable to load priority list.\n" + error.getMessage());
                    }
                });

        Volley.newRequestQueue(this).add(req);
    }

    private void parseResponse(String resp) {
        try {
            if (items.isEmpty()) showLoading(true);

            JSONObject o = new JSONObject(resp);
            boolean ok = o.optBoolean("success", false);
            if (!ok) {
                if (items.isEmpty()) {
                    errorLayout.setVisibility(View.VISIBLE);
                    errorMsg.setText("Server returned error");
                }
                showLoading(false);
                return;
            }

            JSONArray arr = o.optJSONArray("data");
            items.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.getJSONObject(i);
                    PriorityItem pi = new PriorityItem();
                    pi.farmId = it.optInt("farm_id");
                    pi.farmName = it.optString("farmname");
                    pi.varietyId = it.optInt("variety_id");
                    pi.varietyName = it.optString("varietyname");
                    pi.status = it.optString("status");

                    JSONArray lengths = it.optJSONArray("lengths");
                    List<PriorityItem.LengthRow> lrlist = new ArrayList<>();
                    if (lengths != null) {
                        for (int j = 0; j < lengths.length(); j++) {
                            JSONObject lr = lengths.getJSONObject(j);
                            PriorityItem.LengthRow row = new PriorityItem.LengthRow();
                            row.length = lr.optInt("length");
                            row.needed = lr.optInt("needed");
                            row.received = lr.optInt("received");
                            lrlist.add(row);
                        }
                    }
                    pi.lengths = lrlist;
                    items.add(pi);
                }
            }

            adapter.notifyDataSetChanged();
            showLoading(false);
        } catch (Exception ex) {
            ex.printStackTrace();
            if (items.isEmpty()) {
                errorLayout.setVisibility(View.VISIBLE);
                errorMsg.setText("Failed to parse server response.");
            }
            showLoading(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(refresher, 10_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refresher);
    }
}
