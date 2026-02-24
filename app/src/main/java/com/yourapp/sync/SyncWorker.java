package com.yourapp.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context,
                      @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d("SyncWorker", "🚀 SyncWorker STARTED");

        try {
            new SyncManager(getApplicationContext()).syncAll();
            Log.d("SyncWorker", "✅ SyncWorker FINISHED");
            return Result.success();
        } catch (Exception e) {
            Log.e("SyncWorker", "❌ SyncWorker FAILED", e);
            return Result.retry();
        }
    }
}
