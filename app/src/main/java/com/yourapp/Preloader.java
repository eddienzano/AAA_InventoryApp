package com.yourapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

public class Preloader {

    public interface PreloadCallback {
        void onPreloadComplete();
    }

    private final Context context;
    private final LocalDatabaseHelper localDb;
    private PreloadCallback callback;

    // Counter to track multiple preload tasks
    private final AtomicInteger tasksRemaining = new AtomicInteger(0);

    public Preloader(@NonNull Context context, @NonNull LocalDatabaseHelper localDb) {
        this.context = context;
        this.localDb = localDb;
    }

    public void setCallback(PreloadCallback callback) {
        this.callback = callback;
    }

    /** Start preloading all required data */
    public void preloadAll() {
        if (!isOnline()) {
            runOnUiThreadSafe(() ->
                    Toast.makeText(context, "App is offline. Using cached data.", Toast.LENGTH_SHORT).show()
            );
            notifyCallback(); // Stop shimmer immediately
            return;
        }

        // Online: increment task counter for each preload task
        tasksRemaining.set(2); // we have 2 tasks: varieties + greenhouses

//        preloadVarieties();
//        preloadGreenhouses();
    }

    /** Check if device is online */
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

//    private void preloadVarieties() {
//        new Thread(() -> {
//            try {
//                JSONArray arr = ApiClient.getInstance().fetchVarieties();
//                localDb.clearVarieties();
//                for (int i = 0; i < arr.length(); i++) {
//                    JSONObject obj = arr.getJSONObject(i);
//                    int id = obj.getInt("VarietyId");
//                    String name = obj.getString("VarietyName");
//                    localDb.insertVariety(id, name);
//                }
//            } catch (Exception e) {
//                runOnUiThreadSafe(() ->
//                        Toast.makeText(context, "Failed to fetch varieties. Using cached data.", Toast.LENGTH_SHORT).show()
//                );
//            } finally {
//                taskFinished();
//            }
//        }).start();
//    }

//    private void preloadGreenhouses() {
//        new Thread(() -> {
//            try {
//                JSONArray arr = ApiClient.getInstance().fetchGreenhouses();
//                localDb.clearGreenhouses();
//                for (int i = 0; i < arr.length(); i++) {
//                    JSONObject obj = arr.getJSONObject(i);
//                    int id = obj.getInt("id");
//                    int varietyId = obj.getInt("variety_id");
//                    String name = obj.getString("name");
//                    localDb.insertGreenhouse(id, varietyId, name);
//                }
//            } catch (Exception e) {
//                runOnUiThreadSafe(() ->
//                        Toast.makeText(context, "Failed to fetch greenhouses. Using cached data.", Toast.LENGTH_SHORT).show()
//                );
//            } finally {
//                taskFinished();
//            }
//        }).start();
//    }

    /** Called when a preload task finishes */
    private void taskFinished() {
        if (tasksRemaining.decrementAndGet() <= 0) {
            notifyCallback();
        }
    }

    /** Notify callback safely on UI thread */
    private void notifyCallback() {
        if (callback != null) {
            runOnUiThreadSafe(callback::onPreloadComplete);
        }
    }

    /** Helper to run code safely on the UI thread */
    private void runOnUiThreadSafe(Runnable runnable) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(runnable);
        }
    }
}
