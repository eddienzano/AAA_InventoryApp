package com.yourapp.gradedstock;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

public class SyncManager {

    public interface SyncCallback {
        void onSuccess();
        void onError(String msg);
    }

    // =========================
    // 🔥 FETCH ACTIVE STOCK
    // =========================
    public static void syncActiveStock(Context ctx, int farmId, SyncCallback cb) {

        new Thread(() -> {

            try {

                String url = "https://www.aaagrowers.co.ke/inventory/graded/sync_active_graded.php?farm_id=" + farmId;

                String json = HttpHelper.get(url);

                JSONObject obj = new JSONObject(json);

                if (!obj.getBoolean("success")) {
                    cb.onError("Sync failed");
                    return;
                }

                StockDbHelper helper = new StockDbHelper(ctx);
                SQLiteDatabase db = helper.getWritableDatabase();

                db.execSQL("DELETE FROM graded_active_qrs");
                db.execSQL("DELETE FROM varieties");

                // Buckets
                JSONArray buckets = obj.getJSONArray("buckets");

                for (int i = 0; i < buckets.length(); i++) {

                    JSONObject b = buckets.getJSONObject(i);

                    ContentValues cv = new ContentValues();
                    cv.put("qr", b.getString("qr"));
                    cv.put("serial", b.getString("serial"));
                    cv.put("bucket_name", b.getString("bucket_name"));
                    cv.put("farm_id", b.getInt("farm_id"));
                    cv.put("length", b.getInt("length"));
                    cv.put("variety_id", b.getInt("variety_id"));
                    cv.put("bunches", b.getInt("bunches"));
                    cv.put("stems_per_bunch", b.getInt("stems_per_bunch"));

                    db.insert("graded_active_qrs", null, cv);
                }

                // Varieties
                JSONArray vars = obj.getJSONArray("varieties");

                for (int i = 0; i < vars.length(); i++) {

                    JSONObject v = vars.getJSONObject(i);

                    ContentValues cv = new ContentValues();
                    cv.put("id", v.getInt("id"));
                    cv.put("name", v.getString("name"));
                    cv.put("farm_id", v.getInt("farm_id"));

                    db.insert("varieties", null, cv);
                }

                cb.onSuccess();

            } catch (Exception e) {
                cb.onError("Sync error");
            }

        }).start();
    }
}