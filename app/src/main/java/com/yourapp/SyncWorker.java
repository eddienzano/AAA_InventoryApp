package com.yourapp;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private final SyncManager syncManager;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        // 🔹 Initialize ApiClient once with base URL and optional token
        ApiClient.getInstance().init("https://www.aaagrowers.co.ke/inventory/", null);

        // 🔹 SyncManager now only needs Context (it fetches ApiClient internally)
        syncManager = new SyncManager(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Starting sync worker...");

            syncManager.syncLocalToServer();
            syncManager.syncServerToLocal();

            Log.d(TAG, "Sync worker completed successfully.");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Sync worker failed", e);
            return Result.retry();
        }
    }
}
