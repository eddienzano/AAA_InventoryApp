package com.yourapp.boxfill;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;

public class FlowerDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "flower_inventory.db";
    private static final int DB_VERSION = 3;

    public FlowerDbHelper(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE farms_local (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT)");

        db.execSQL("CREATE TABLE varieties_local (" +
                "id INTEGER PRIMARY KEY," +
                "farm_id INTEGER," +
                "name TEXT)");

        db.execSQL("CREATE TABLE boxes_local (" +
                "id INTEGER PRIMARY KEY," +
                "qr_code TEXT UNIQUE," +
                "farm_id INTEGER," +
                "status TEXT," +
                "synced INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE box_contents_local (" +
                "box_id INTEGER PRIMARY KEY," +
                "variety_id INTEGER," +
                "bunches INTEGER," +
                "stems_per_bunch INTEGER," +
                "sfk TEXT," +
                "sleeve TEXT," +
                "box_type TEXT," +
                "updated_at TEXT," +
                "synced INTEGER DEFAULT 0)");




        db.execSQL("CREATE TABLE warehouse_scan_sessions_local (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "farm_id INTEGER," +
                "scan_date TEXT," +
                "started_at TEXT," +
                "ended_at TEXT," +
                "status TEXT," +
                "server_session_id INTEGER)");

        db.execSQL("CREATE TABLE warehouse_scans_local (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "local_session_id INTEGER," +
                "qr_code TEXT," +
                "scanned_at TEXT," +
                "synced INTEGER DEFAULT 0," +
                "UNIQUE(local_session_id, qr_code))");
        db.execSQL(
                "CREATE INDEX idx_session_scans " +
                        "ON warehouse_scans_local(local_session_id)"
        );

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS warehouse_scan_sessions_local (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "farm_id INTEGER," +
                    "scan_date TEXT," +
                    "started_at TEXT," +
                    "ended_at TEXT," +
                    "status TEXT," +
                    "server_session_id INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS warehouse_scans_local (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "local_session_id INTEGER," +
                    "qr_code TEXT," +
                    "scanned_at TEXT," +
                    "synced INTEGER DEFAULT 0," +
                    "UNIQUE(local_session_id, qr_code))");
        }
    }

    public void seedIfEmpty(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM farms_local", null);
        c.moveToFirst();
        if (c.getInt(0) == 0) {
            db.execSQL("INSERT INTO farms_local VALUES (1,'Simba')");
            db.execSQL("INSERT INTO farms_local VALUES (2,'Chui')");
        }
        c.close();
    }
}
