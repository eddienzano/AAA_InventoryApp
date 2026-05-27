package com.yourapp.gradedstock;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StockDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "gradedstock.db";
    private static final int DB_VERSION = 2;

    public StockDbHelper(Context ctx){
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db){

        /* ACTIVE BUCKETS FROM SERVER */
        db.execSQL(
                "CREATE TABLE graded_active_qrs(" +
                        "graded_stock_id INTEGER PRIMARY KEY," +  // new: link to server ID
                        "qr TEXT," +
                        "serial TEXT," +
                        "bucket_name TEXT," +
                        "farm_id INTEGER," +
                        "length INTEGER," +
                        "variety_id INTEGER," +
                        "bunches INTEGER," +
                        "stems_per_bunch INTEGER)"
        );

        /* GRADED STOCK PENDING SYNC */
        db.execSQL(
                "CREATE TABLE graded_pending(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "qr TEXT," +
                        "graded_stock_id INTEGER," +   // new
                        "serial TEXT," +
                        "bucket_name TEXT," +
                        "farm_id INTEGER," +
                        "length INTEGER," +
                        "variety_id INTEGER," +
                        "bunches INTEGER," +
                        "stems_per_bunch INTEGER," +
                        "synced INTEGER DEFAULT 0)"
        );

        /* VARIETIES */
        db.execSQL(
                "CREATE TABLE varieties(" +
                        "id INTEGER PRIMARY KEY," +
                        "name TEXT," +
                        "farm_id INTEGER)"
        );

        /* OFFLINE SCAN OUT RECORDS */
        db.execSQL(
                "CREATE TABLE graded_out_local(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "qr TEXT," +
                        "graded_stock_id INTEGER," +   // new
                        "farm_id INTEGER," +
                        "action_type TEXT," +          // COMPLETE | PARTIAL | REWORK
                        "bunches_removed INTEGER DEFAULT 0," +
                        "scanned_at TEXT," +
                        "device_id TEXT," +
                        "synced INTEGER DEFAULT 0)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion < 2){
            // Drop all old tables
            db.execSQL("DROP TABLE IF EXISTS graded_active_qrs");
            db.execSQL("DROP TABLE IF EXISTS graded_pending");
            db.execSQL("DROP TABLE IF EXISTS varieties");
            db.execSQL("DROP TABLE IF EXISTS graded_out_local");

            // Recreate tables with the updated schema
            onCreate(db);
        }
    }
}