package com.yourapp.boxfill;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.yourapp.sync.SyncManager;

import org.jspecify.annotations.NonNull;

public class BoxSyncWorker extends Worker {

    public BoxSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SyncManager syncManager = new SyncManager(getApplicationContext());
            syncManager.syncAvailableBoxes();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}