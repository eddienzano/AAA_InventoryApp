package com.yourapp;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 🔹 Singleton instance
    private static ApiClient instance;

    // 🔹 Single static OkHttp client, shared everywhere
    private final OkHttpClient client = new OkHttpClient();

    private String baseUrl = "https://www.aaagrowers.co.ke/inventory/"; // default
    private String authToken = null;

    // Private constructor (singleton pattern)
    private ApiClient() {}

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void init(String url, String token) {
        this.baseUrl = url;
        this.authToken = token;
    }

    public OkHttpClient getClient() {
        return client;
    }

    /** Generic POST returning JSONObject */
    public JSONObject postJson(String endpoint, JSONObject payload) {
        RequestBody body = RequestBody.create(payload.toString(), JSON);

        Request.Builder builder = new Request.Builder()
                .url(baseUrl + endpoint)
                .post(body)
                .addHeader("X-Requested-With", "XMLHttpRequest");
        if (authToken != null) builder.addHeader("Authorization", "Bearer " + authToken);

        Request request = builder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "POST failed: " + endpoint + " code: " +
                        (response != null ? response.code() : "null"));
                return null;
            }
            String resp = response.body().string();
            return new JSONObject(resp);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "POST Exception: " + endpoint, e);
            return null;
        }
    }

    /** Generic GET returning JSONArray */
    public JSONArray getJsonArray(String endpoint) {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + endpoint)
                .get();
        if (authToken != null) builder.addHeader("Authorization", "Bearer " + authToken);

        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return new JSONArray(response.body().string());
            } else {
                Log.e(TAG, "Error fetching " + endpoint + ": " + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception fetching " + endpoint, e);
        }
        return new JSONArray();
    }

    // ---------------- HIGH-LEVEL HELPERS ----------------

    public boolean uploadIntake(JSONObject payload) {
        JSONObject resp = postJson("intake_process.php", payload);
        return resp != null && "success".equalsIgnoreCase(resp.optString("status"));
    }

    public boolean uploadOutwardScan(JSONObject payload) {
        JSONObject resp = postJson("outward_process.php", payload);
        return resp != null && "success".equalsIgnoreCase(resp.optString("status"));
    }

//    public JSONArray fetchBuckets() {
//        return getJsonArray("get_buckets.php");
    }

//    public JSONArray fetchVarieties() {
//        return getJsonArray("get_varieties.php");
//    }

//    public JSONArray fetchGreenhouses() {
//        return getJsonArray("get_greenhouses.php?format=json");
//    }
//}
