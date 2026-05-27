package com.yourapp.network;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class NetworkManager {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private static Call activeCall;
    private static long lastRequestTime = 0;

    // 🔥 GLOBAL COOLDOWN (prevents IP bans)
    private static final long MIN_INTERVAL_MS = 1200;

    public interface Callback {
        void onSuccess(String response);
        void onFailure(String error);
    }

    public static synchronized void get(String url, Callback callback) {

        long now = System.currentTimeMillis();

        // 🚫 rate limit protection
        if (now - lastRequestTime < MIN_INTERVAL_MS) {
            callback.onFailure("Too many requests (rate limited)");
            return;
        }

        lastRequestTime = now;

        // cancel previous call (important)
        if (activeCall != null) {
            activeCall.cancel();
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        activeCall = client.newCall(request);

        activeCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onFailure(e.getMessage())
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                String body = response.body() != null ? response.body().string() : "";

                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(body)
                );
            }
        });
    }

    public static synchronized void post(RequestBody body, String url, Callback callback) {

        long now = System.currentTimeMillis();

        if (now - lastRequestTime < MIN_INTERVAL_MS) {
            callback.onFailure("Too many requests (rate limited)");
            return;
        }

        lastRequestTime = now;

        if (activeCall != null) {
            activeCall.cancel();
        }

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        activeCall = client.newCall(request);

        activeCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onFailure(e.getMessage())
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                String body = response.body() != null ? response.body().string() : "";

                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(body)
                );
            }
        });
    }
}