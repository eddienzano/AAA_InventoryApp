package com.yourapp;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SyncScheduler {

    private static final String WORK_NAME = "inventory_sync_worker";

    public static void startSync(Context context) {
        // 1. Start an immediate one-time sync
        WorkManager.getInstance(context).enqueue(
                new androidx.work.OneTimeWorkRequest.Builder(SyncWorker.class)
                        .build()
        );

        // 2. Schedule periodic sync every 15 minutes (minimum interval for PeriodicWorkRequest)
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // sync only when online
                .build();

        PeriodicWorkRequest periodicSync = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // do not replace if already scheduled
                periodicSync
        );
    }
}
