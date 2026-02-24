package com.yourapp.stocks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

public class StockRepository {

    private static final String API_URL =
            "https://www.aaagrowers.co.ke/inventory/api/scan_receive.php";

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /* ==============================
       SAVE OFFLINE
    ============================== */
    public static void saveOffline(Context ctx, String qr) {

        StockDbHelper helper = new StockDbHelper(ctx);
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("qr_code", qr);

        long result = db.insertWithOnConflict(
                "pending_scans",
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        db.close();

        if (result == -1)
            toast(ctx, "Duplicate scan");
        else
            toast(ctx, "Saved offline ✓");
    }

    /* ==============================
       SEND ALL TO SERVER
    ============================== */
    public static void sendAll(Context ctx) {

        executor.execute(() -> {

            StockDbHelper helper = new StockDbHelper(ctx);
            SQLiteDatabase db = helper.getWritableDatabase();

            Cursor c = db.rawQuery(
                    "SELECT id, qr_code FROM pending_scans WHERE synced=0",
                    null
            );

            int success = 0;

            while (c.moveToNext()) {

                int id = c.getInt(0);
                String qr = c.getString(1);

                if (sendToServer(qr)) {

                    ContentValues cv = new ContentValues();
                    cv.put("synced", 1);

                    db.update(
                            "pending_scans",
                            cv,
                            "id=?",
                            new String[]{String.valueOf(id)}
                    );

                    success++;
                }
            }

            c.close();
            db.close();

            toast(ctx, "Uploaded " + success + " scans");
        });
    }

    /* ==============================
       API CALL
    ============================== */
    private static boolean sendToServer(String qrCode) {

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String post =
                    "qr_code=" + URLEncoder.encode(qrCode,"UTF-8") +
                            "&user_id=android_user";

            OutputStream os = conn.getOutputStream();
            os.write(post.getBytes());
            os.close();

            if (conn.getResponseCode() == 200) {

                InputStream is = conn.getInputStream();
                java.util.Scanner sc =
                        new java.util.Scanner(is).useDelimiter("\\A");

                String json = sc.hasNext()? sc.next():"";

                JSONObject obj = new JSONObject(json);
                return obj.optBoolean("success", false);
            }

        } catch (Exception ignored) {}

        return false;
    }

    /* ==============================
   RESET UNSYNCED (FORCE RESEND)
============================== */
    public static void redoUnsynced(Context ctx) {

        executor.execute(() -> {

            StockDbHelper helper = new StockDbHelper(ctx);
            SQLiteDatabase db = helper.getWritableDatabase();

            // anything not synced becomes pending again
            db.execSQL("UPDATE pending_scans SET synced = 0");

            db.close();

            toast(ctx, "All records marked for re-sync ✓");
        });
    }


    private static void toast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        );
    }
}
