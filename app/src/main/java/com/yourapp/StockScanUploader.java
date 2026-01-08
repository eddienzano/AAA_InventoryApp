package com.yourapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockScanUploader {

    private static final String STOCK_SCAN_URL =
            "https://www.aaagrowers.co.ke/inventory/api/scan_receive.php";

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public static void upload(Context ctx, String qrCode) {
        executor.execute(() -> {
            try {
                URL url = new URL(STOCK_SCAN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                String postData =
                        "qr_code=" + URLEncoder.encode(qrCode, "UTF-8") +
                                "&user_id=" + URLEncoder.encode("intake_app", "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream is = conn.getInputStream();
                    java.util.Scanner sc =
                            new java.util.Scanner(is).useDelimiter("\\A");
                    String json = sc.hasNext() ? sc.next() : "";

                    JSONObject obj = new JSONObject(json);
                    boolean success = obj.optBoolean("success", false);
                    String msg = obj.optString("message", "Stock scan failed");

                    showToast(ctx, msg);
                }

            } catch (Exception e) {
                showToast(ctx, "Stock scan error");
            }
        });
    }

    private static void showToast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        );
    }
}
