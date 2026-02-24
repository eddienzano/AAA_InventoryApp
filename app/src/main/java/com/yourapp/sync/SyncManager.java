package com.yourapp.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.yourapp.boxfill.FlowerDbHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SyncManager {

    private static final String TAG = "SyncManager";

    private final FlowerDbHelper db;
    private final ApiClient api;

    public SyncManager(Context context) {
        db = new FlowerDbHelper(context);
        api = new ApiClient();
    }

    // ==========================================================
    // ENTRY POINT
    // ==========================================================
    public void syncAll() {
        syncBoxFills();          // existing logic
        syncWarehouseScans();    // new logic
    }

    /* ==========================================================
       BOX FILL SYNC (UNCHANGED)
       ========================================================== */
    private void syncBoxFills() {

        SQLiteDatabase rdb = db.getReadableDatabase();

        Cursor c = rdb.rawQuery(
                "SELECT " +
                        "c.rowid AS local_id, " +
                        "b.qr_code, b.farm_id, " +
                        "c.variety_id, c.bunches, c.stems_per_bunch, " +
                        "c.sfk, c.sleeve, c.box_type " +
                        "FROM box_contents_local c " +
                        "JOIN boxes_local b ON b.id = c.box_id " +
                        "WHERE c.synced = 0",
                null
        );

        Log.d(TAG, "📦 BoxFill rows to sync = " + c.getCount());

        JSONArray payload = new JSONArray();
        List<Integer> syncedRowIds = new ArrayList<>();

        try {
            while (c.moveToNext()) {

                int rowId = c.getInt(0);

                JSONObject o = new JSONObject();
                o.put("qr_code", c.getString(1));
                o.put("farm_id", c.getInt(2));
                o.put("variety_id", c.getInt(3));
                o.put("bunches", c.getInt(4));
                o.put("stems_per_bunch", c.getInt(5));
                o.put("sfk", c.getString(6));
                o.put("sleeve", c.getString(7));
                o.put("box_type", c.getString(8));

                payload.put(o);
                syncedRowIds.add(rowId);
            }

            if (payload.length() == 0) return;

            boolean ok = api.post("/sync/boxfills.php", payload);

            if (!ok) {
                Log.w(TAG, "❌ Box fill sync failed");
                return;
            }

            SQLiteDatabase wdb = db.getWritableDatabase();
            wdb.beginTransaction();

            try {
                for (int id : syncedRowIds) {
                    wdb.execSQL(
                            "UPDATE box_contents_local SET synced=1 WHERE rowid=?",
                            new Object[]{id}
                    );
                }
                wdb.setTransactionSuccessful();
                Log.d(TAG, "✅ BoxFill sync success (" + syncedRowIds.size() + ")");
            } finally {
                wdb.endTransaction();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Box fill sync error", e);
        } finally {
            c.close();
        }
    }

    /* ==========================================================
       WAREHOUSE SCAN SYNC (OFFLINE → SERVER)
       ========================================================== */
//    private void syncWarehouseScans() {
//
//        SQLiteDatabase rdb = db.getReadableDatabase();
//
//        // 🛡 Ensure tables exist (safe on older installs)
//        Cursor check = rdb.rawQuery(
//                "SELECT name FROM sqlite_master WHERE type='table' AND name='warehouse_scan_sessions_local'",
//                null
//        );
//
//        if (!check.moveToFirst()) {
//            check.close();
//            Log.d(TAG, "ℹ️ Warehouse tables not found – skipping warehouse sync");
//            return;
//        }
//        check.close();
//
//        Cursor sessions = rdb.rawQuery(
//                "SELECT * FROM warehouse_scan_sessions_local " +
//                        "WHERE status='CLOSED' AND server_session_id IS NULL",
//                null
//        );
//
//        while (sessions.moveToNext()) {
//
//            long localSessionId = sessions.getLong(
//                    sessions.getColumnIndexOrThrow("id")
//            );
//
//            try {
//                // 1️⃣ CREATE SESSION ON SERVER
//                JSONObject sessionPayload = new JSONObject();
//                sessionPayload.put(
//                        "farm_id",
//                        sessions.getInt(sessions.getColumnIndexOrThrow("farm_id"))
//                );
//                sessionPayload.put(
//                        "scan_date",
//                        sessions.getString(sessions.getColumnIndexOrThrow("scan_date"))
//                );
//
//                int serverSessionId = api.postForId(
//                        "/warehouse/start_session.php",
//                        sessionPayload
//                );
//
//                // 2️⃣ UPLOAD SCANS
//                uploadWarehouseScans(localSessionId, serverSessionId);
//
//                // 3️⃣ CLOSE SESSION ON SERVER
//                api.postObject(
//                        "/warehouse/close_session.php",
//                        new JSONObject().put("session_id", serverSessionId)
//                );
//
//                // 4️⃣ MARK LOCAL SESSION AS SYNCED
//                ContentValues v = new ContentValues();
//                v.put("status", "SYNCED");
//                v.put("server_session_id", serverSessionId);
//
//                db.getWritableDatabase().update(
//                        "warehouse_scan_sessions_local",
//                        v,
//                        "id=?",
//                        new String[]{String.valueOf(localSessionId)}
//                );
//
//                Log.d(TAG, "✅ Warehouse session synced: local=" +
//                        localSessionId + " server=" + serverSessionId);
//
//            } catch (Exception e) {
//                Log.e(TAG, "❌ Warehouse sync failed (session " + localSessionId + ")", e);
//            }
//        }
//
//        sessions.close();
//    }


    private void syncWarehouseScans() {

        SQLiteDatabase rdb = db.getReadableDatabase();

        Cursor sessions = rdb.rawQuery(
                "SELECT * FROM warehouse_scan_sessions_local " +
                        "WHERE status='CLOSED' AND status!='SYNCED'",
                null
        );

        while (sessions.moveToNext()) {

            long localSessionId =
                    sessions.getLong(sessions.getColumnIndexOrThrow("id"));

            try {
                JSONObject payload = new JSONObject();

                payload.put("farm_id",
                        sessions.getInt(sessions.getColumnIndexOrThrow("farm_id")));
                payload.put("scan_date",
                        sessions.getString(sessions.getColumnIndexOrThrow("scan_date")));
                payload.put("started_at",
                        sessions.getString(sessions.getColumnIndexOrThrow("started_at")));
                payload.put("ended_at",
                        sessions.getString(sessions.getColumnIndexOrThrow("ended_at")));

                // =========================
                // Attach scans
                // =========================
                JSONArray scans = new JSONArray();

                Cursor c = rdb.rawQuery(
                        "SELECT qr_code, scanned_at " +
                                "FROM warehouse_scans_local " +
                                "WHERE local_session_id=?",
                        new String[]{String.valueOf(localSessionId)}
                );

                while (c.moveToNext()) {
                    JSONObject s = new JSONObject();
                    s.put("qr_code", c.getString(0));
                    s.put("scanned_at", c.getString(1));
                    scans.put(s);
                }
                c.close();

                if (scans.length() == 0) {
                    Log.w(TAG, "⚠️ No scans for session " + localSessionId);
                    continue;
                }

                payload.put("scans", scans);

                // =========================
                // POST to correct endpoint
                // =========================
                boolean ok = api.postObject(
                        "/sync/warehouse_stock.php",
                        payload
                );


                if (!ok) throw new Exception("Warehouse sync failed");

                // =========================
                // Mark session as SYNCED
                // =========================
                ContentValues v = new ContentValues();
                v.put("status", "SYNCED");

                db.getWritableDatabase().update(
                        "warehouse_scan_sessions_local",
                        v,
                        "id=?",
                        new String[]{String.valueOf(localSessionId)}
                );

                Log.d(TAG, "✅ Warehouse session synced: " + localSessionId);

            } catch (Exception e) {
                Log.e(TAG, "❌ Warehouse sync failed for session " + localSessionId, e);
            }
        }

        sessions.close();
    }

    /* ==========================================================
       UPLOAD SCANS FOR ONE SESSION
       ========================================================== */
    private void uploadWarehouseScans(long localSessionId, int serverSessionId) throws Exception {

        SQLiteDatabase rdb = db.getReadableDatabase();

        Cursor c = rdb.rawQuery(
                "SELECT qr_code FROM warehouse_scans_local " +
                        "WHERE local_session_id=? AND synced=0",
                new String[]{String.valueOf(localSessionId)}
        );

        JSONArray payload = new JSONArray();

        while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("session_id", serverSessionId);
            o.put("qr_code", c.getString(0));
            payload.put(o);
        }

        c.close();

        if (payload.length() == 0) return;

        boolean ok = api.post("/warehouse/upload_scans.php", payload);

        if (ok) {
            db.getWritableDatabase().execSQL(
                    "UPDATE warehouse_scans_local SET synced=1 WHERE local_session_id=?",
                    new Object[]{localSessionId}
            );
        }
    }
}
