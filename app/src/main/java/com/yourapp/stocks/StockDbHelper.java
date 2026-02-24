package com.yourapp.stocks;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StockDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "stockscan.db";
    private static final int DB_VERSION = 1;

    public StockDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE pending_scans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "qr_code TEXT UNIQUE," +
                        "synced INTEGER DEFAULT 0," +
                        "scan_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
