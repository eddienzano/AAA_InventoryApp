package com.yourapp.sync;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final String BASE_URL = "https://www.aaagrowers.co.ke/inventory";

    // =============================
    // Fire-and-forget POST with JSONArray
    // =============================
    public boolean post(String path, JSONArray payload) {
        return postRaw(path, payload.toString());
    }

    // =============================
    // Fire-and-forget POST with JSONObject
    // =============================
    public boolean postObject(String path, JSONObject payload) {
        return postRaw(path, payload.toString());
    }

    // =============================
    // POST a JSONObject and return server-generated ID
    // =============================
    public int postForId(String path, JSONObject payload) throws Exception {

        HttpURLConnection conn = null;

        try {
            URL url = new URL(BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            // send payload
            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();

            // read response
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + sb.toString());
            }

            JSONObject resp = new JSONObject(sb.toString());
            if (!resp.has("session_id")) {
                throw new Exception("Missing session_id in response");
            }

            return resp.getInt("session_id");

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =============================
    // Internal POST helper
    // =============================
    private boolean postRaw(String path, String body) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();

            // drain response
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while (br.readLine() != null) {}
                br.close();
            }

            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e(TAG, "POST failed: " + path, e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
