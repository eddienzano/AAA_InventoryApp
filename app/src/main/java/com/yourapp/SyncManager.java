package com.yourapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class SyncManager {

    private static final String TAG = "SyncManager";

    private final Context context;
    private final LocalDatabaseHelper dbHelper;
    private final ApiClient apiClient;

    public SyncManager(Context context) {
        this.context = context;
        this.dbHelper = new LocalDatabaseHelper(context);
        this.apiClient = ApiClient.getInstance(); // 🔹 Singleton
    }

    // ------------------- SYNC LOCAL TO SERVER -------------------
    public void syncLocalToServer() {
        List<LocalDatabaseHelper.IntakeRecord> unsyncedIntakes = dbHelper.getUnsyncedIntakes();
        List<LocalDatabaseHelper.OutwardRecord> unsyncedOutwards = dbHelper.getUnsyncedOutwards();

        // Upload intakes
        for (LocalDatabaseHelper.IntakeRecord r : unsyncedIntakes) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("qr_code", r.qrCode);
                payload.put("serial", r.serial);
                payload.put("bucket", r.bucket);
                payload.put("farm", r.farm);
                payload.put("length", r.length);
                payload.put("variety_id", r.varietyId);
                payload.put("variety_name", r.varietyName);
                payload.put("greenhouse_id", r.greenhouseId);
                payload.put("greenhouse_name", r.greenhouseName);
                payload.put("quantity", r.quantity);
                payload.put("coldroom", r.coldroom);
                payload.put("user_id", r.userId);
                payload.put("block", r.block);

                boolean success = apiClient.uploadIntake(payload);
                if (success) dbHelper.markIntakeAsSynced(r.id);

            } catch (Exception e) {
                Log.e(TAG, "Error uploading intake: " + r.id, e);
            }
        }

        // Upload outward scans
        for (LocalDatabaseHelper.OutwardRecord r : unsyncedOutwards) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("qr_code", r.qr);
                payload.put("serial", r.serial);
                payload.put("bucket", r.bucket);
                payload.put("intake_id", r.intakeId);
                payload.put("scanout_time", r.time);
                payload.put("scanout_user", r.user);
                payload.put("quantity", r.quantity);
                payload.put("variety_name", r.varietyName);

                boolean success = apiClient.uploadOutwardScan(payload);
                if (success) dbHelper.markOutwardAsSynced(r.id);

            } catch (Exception e) {
                Log.e(TAG, "Error uploading outward scan: " + r.id, e);
            }
        }
    }

    // ------------------- SYNC SERVER TO LOCAL -------------------
    public void syncServerToLocal() {
        try {
            // 1. Buckets
//            JSONArray buckets = apiClient.fetchBuckets();
//            dbHelper.clearBuckets();
//            for (int i = 0; i < buckets.length(); i++) {
//                JSONObject b = buckets.getJSONObject(i);
//                dbHelper.upsertBucket(
//                        b.getString("serial"),
//                        b.getString("bucket_name"),
//                        b.getString("qr_code"),
//                        b.getString("farm"),
//                        b.getString("length"),
//                        b.getString("status"),
//                        b.getString("updated_at")
//                );
//            }

            // 2. Varieties
//            JSONArray varieties = apiClient.fetchVarieties();
//            dbHelper.clearVarieties();
//            for (int i = 0; i < varieties.length(); i++) {
//                JSONObject v = varieties.getJSONObject(i);
//                dbHelper.upsertVariety(
//                        v.getInt("VarietyId"),
//                        v.getString("VarietyName"),
//                        v.getString("VarietyCode"),
//                        v.getInt("FarmId"),
//                        v.getInt("Active")
//                );
//            }

            // 3. Greenhouses
//            JSONArray greenhouses = apiClient.fetchGreenhouses();
//            dbHelper.clearGreenhouses();
//            for (int i = 0; i < greenhouses.length(); i++) {
//                JSONObject g = greenhouses.getJSONObject(i);
//                dbHelper.upsertGreenhouse(
//                        g.getInt("GreenhouseId"),
//                        g.getString("GreenhouseName"),
//                        g.getInt("FarmId"),
//                        g.getInt("VarietyId")
//                );
//            }

        } catch (Exception e) {
            Log.e(TAG, "Error syncing server to local", e);
        }
    }

    // ------------------- OFFLINE VALIDATION -------------------
    public boolean isBucketAvailable(String qrCode) {
        return dbHelper.isBucketAvailable(qrCode);
    }
}
