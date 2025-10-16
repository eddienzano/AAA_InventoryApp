package com.yourapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class LocalDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "intake.db";
    private static final int DATABASE_VERSION = 8; // bump on schema change

    // ---------------- INTAKES ----------------
    private static final String TABLE_INTAKE = "intakes";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_QR_CODE = "qr_code";
    private static final String COLUMN_SERIAL = "serial";
    private static final String COLUMN_BUCKET = "bucket";
    private static final String COLUMN_FARM = "farm";
    private static final String COLUMN_LENGTH = "length";
    private static final String COLUMN_VARIETY_ID = "variety_id";
    private static final String COLUMN_VARIETY_NAME = "variety_name";
    private static final String COLUMN_GREENHOUSE_ID = "greenhouse_id";
    private static final String COLUMN_GREENHOUSE_NAME = "greenhouse_name";
    private static final String COLUMN_QUANTITY = "quantity";
    private static final String COLUMN_COLDROOM = "coldroom";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_BLOCK = "block";
    private static final String COLUMN_IS_SYNCED = "is_synced";

    // ---------------- OUTWARD SCANS ----------------
    private static final String TABLE_OUTWARD = "outward_scans";
    private static final String COL_OUTWARD_ID = "id";
    private static final String COL_OUTWARD_QR = "qr_code";
    private static final String COL_OUTWARD_SERIAL = "serial";
    private static final String COL_OUTWARD_BUCKET = "bucket";
    private static final String COL_OUTWARD_TIME = "scanout_time";
    private static final String COL_OUTWARD_USER = "scanout_user";
    private static final String COL_OUTWARD_INTAKE_ID = "intake_id";
    private static final String COL_OUTWARD_VARIETY_NAME = "variety_name";
    private static final String COL_OUTWARD_QUANTITY = "quantity";
    private static final String COL_OUTWARD_IS_SYNCED = "is_synced";

    // ---------------- CACHE TABLES ----------------
    private static final String TABLE_BUCKETS = "buckets_cache";
    private static final String COL_BUCKET_ID = "id";
    private static final String COL_BUCKET_SERIAL = "serial";
    private static final String COL_BUCKET_NAME = "bucket_name";
    private static final String COL_BUCKET_QR = "qr_code";
    private static final String COL_BUCKET_FARM = "farm";
    private static final String COL_BUCKET_LENGTH = "length";
    private static final String COL_BUCKET_STATUS = "status";
    private static final String COL_BUCKET_UPDATED = "updated_at";

    private static final String TABLE_VARIETIES = "varieties_cache";
    private static final String COL_VARIETY_ID = "VarietyId";
    private static final String COL_VARIETY_NAME = "VarietyName";
    private static final String COL_VARIETY_CODE = "VarietyCode";
    private static final String COL_VARIETY_FARMID = "FarmId";
    private static final String COL_VARIETY_ACTIVE = "Active";

    private static final String TABLE_GREENHOUSES = "greenhouses_cache";
    private static final String COL_GREENHOUSE_ID = "GreenhouseId";
    private static final String COL_GREENHOUSE_NAME = "GreenhouseName";
    private static final String COL_GREENHOUSE_FARMID = "FarmId";
    private static final String COL_GREENHOUSE_VARIETYID = "VarietyId";

    public LocalDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Intake
        db.execSQL("CREATE TABLE " + TABLE_INTAKE + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_QR_CODE + " TEXT UNIQUE,"
                + COLUMN_SERIAL + " TEXT,"
                + COLUMN_BUCKET + " TEXT,"
                + COLUMN_FARM + " TEXT,"
                + COLUMN_LENGTH + " TEXT,"
                + COLUMN_VARIETY_ID + " INTEGER,"
                + COLUMN_VARIETY_NAME + " TEXT,"
                + COLUMN_GREENHOUSE_ID + " INTEGER,"
                + COLUMN_GREENHOUSE_NAME + " TEXT,"
                + COLUMN_QUANTITY + " TEXT,"
                + COLUMN_COLDROOM + " TEXT,"
                + COLUMN_USER_ID + " INTEGER,"
                + COLUMN_BLOCK + " TEXT,"
                + COLUMN_IS_SYNCED + " INTEGER DEFAULT 0)");

        // Outward scans
        db.execSQL("CREATE TABLE " + TABLE_OUTWARD + "("
                + COL_OUTWARD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_OUTWARD_QR + " TEXT,"
                + COL_OUTWARD_SERIAL + " TEXT,"
                + COL_OUTWARD_BUCKET + " TEXT,"
                + COL_OUTWARD_TIME + " TEXT,"
                + COL_OUTWARD_USER + " TEXT,"
                + COL_OUTWARD_INTAKE_ID + " INTEGER,"
                + COL_OUTWARD_VARIETY_NAME + " TEXT,"
                + COL_OUTWARD_QUANTITY + " INTEGER,"
                + COL_OUTWARD_IS_SYNCED + " INTEGER DEFAULT 0)");

        // Buckets cache
        db.execSQL("CREATE TABLE " + TABLE_BUCKETS + "("
                + COL_BUCKET_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_BUCKET_SERIAL + " TEXT,"
                + COL_BUCKET_NAME + " TEXT,"
                + COL_BUCKET_QR + " TEXT UNIQUE,"
                + COL_BUCKET_FARM + " TEXT,"
                + COL_BUCKET_LENGTH + " TEXT,"
                + COL_BUCKET_STATUS + " TEXT,"
                + COL_BUCKET_UPDATED + " TEXT)");

        // Varieties cache
        db.execSQL("CREATE TABLE " + TABLE_VARIETIES + "("
                + COL_VARIETY_ID + " INTEGER PRIMARY KEY,"
                + COL_VARIETY_NAME + " TEXT,"
                + COL_VARIETY_CODE + " TEXT,"
                + COL_VARIETY_FARMID + " INTEGER,"
                + COL_VARIETY_ACTIVE + " INTEGER)");

        // Greenhouses cache
        db.execSQL("CREATE TABLE " + TABLE_GREENHOUSES + "("
                + COL_GREENHOUSE_ID + " INTEGER PRIMARY KEY,"
                + COL_GREENHOUSE_NAME + " TEXT,"
                + COL_GREENHOUSE_FARMID + " INTEGER,"
                + COL_GREENHOUSE_VARIETYID + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTAKE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_OUTWARD);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BUCKETS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VARIETIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GREENHOUSES);
        onCreate(db);
    }

    // ---------------- INTAKES ----------------
    public boolean intakeExists(String qrCode) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_INTAKE +
                        " WHERE " + COLUMN_QR_CODE + "=? AND " + COLUMN_IS_SYNCED + "=0",
                new String[]{qrCode});
        boolean exists = cursor.moveToFirst();
        cursor.close();
//        db.close();
        return exists;
    }

    public long insertIntake(String qrCode, String serial, String bucket, String farm, String length,
                             Integer varietyId, String varietyName,
                             Integer greenhouseId, String greenhouseName,
                             String quantity, String coldroom, int userId, String block) {
        if (intakeExists(qrCode)) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COLUMN_QR_CODE, qrCode);
        v.put(COLUMN_SERIAL, serial);
        v.put(COLUMN_BUCKET, bucket);
        v.put(COLUMN_FARM, farm);
        v.put(COLUMN_LENGTH, length);
        v.put(COLUMN_VARIETY_ID, varietyId);
        v.put(COLUMN_VARIETY_NAME, varietyName);
        v.put(COLUMN_GREENHOUSE_ID, greenhouseId);
        v.put(COLUMN_GREENHOUSE_NAME, greenhouseName);
        v.put(COLUMN_QUANTITY, quantity);
        v.put(COLUMN_COLDROOM, coldroom);
        v.put(COLUMN_USER_ID, userId);
        v.put(COLUMN_BLOCK, block);
        v.put(COLUMN_IS_SYNCED, 0);
        long rowId = db.insert(TABLE_INTAKE, null, v);
//        db.close();
        return rowId;
    }

    public static class IntakeRecord {
        public int id, varietyId, greenhouseId, userId;
        public String qrCode, serial, bucket, farm, length, quantity;
        public String varietyName, greenhouseName, coldroom, block;
    }

    public List<IntakeRecord> getUnsyncedIntakes() {
        List<IntakeRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_INTAKE, null, COLUMN_IS_SYNCED + "=?",
                new String[]{"0"}, null, null, COLUMN_ID + " ASC");
        if (c.moveToFirst()) {
            do {
                IntakeRecord r = new IntakeRecord();
                r.id = c.getInt(c.getColumnIndexOrThrow(COLUMN_ID));
                r.qrCode = c.getString(c.getColumnIndexOrThrow(COLUMN_QR_CODE));
                r.serial = c.getString(c.getColumnIndexOrThrow(COLUMN_SERIAL));
                r.bucket = c.getString(c.getColumnIndexOrThrow(COLUMN_BUCKET));
                r.farm = c.getString(c.getColumnIndexOrThrow(COLUMN_FARM));
                r.length = c.getString(c.getColumnIndexOrThrow(COLUMN_LENGTH));
                r.varietyId = c.getInt(c.getColumnIndexOrThrow(COLUMN_VARIETY_ID));
                r.varietyName = c.getString(c.getColumnIndexOrThrow(COLUMN_VARIETY_NAME));
                r.greenhouseId = c.getInt(c.getColumnIndexOrThrow(COLUMN_GREENHOUSE_ID));
                r.greenhouseName = c.getString(c.getColumnIndexOrThrow(COLUMN_GREENHOUSE_NAME));
                r.quantity = c.getString(c.getColumnIndexOrThrow(COLUMN_QUANTITY));
                r.coldroom = c.getString(c.getColumnIndexOrThrow(COLUMN_COLDROOM));
                r.userId = c.getInt(c.getColumnIndexOrThrow(COLUMN_USER_ID));
                r.block = c.getString(c.getColumnIndexOrThrow(COLUMN_BLOCK));
                list.add(r);
            } while (c.moveToNext());
        }
        c.close();
//        db.close();
        return list;
    }

    public void markIntakeAsSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COLUMN_IS_SYNCED, 1);
        db.update(TABLE_INTAKE, v, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
//        db.close();
    }

    // ---------------- OUTWARD ----------------
    public long insertOutward(String qr, String serial, String bucket, String time,
                              String user, int intakeId, String varietyName, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_OUTWARD_QR, qr);
        v.put(COL_OUTWARD_SERIAL, serial);
        v.put(COL_OUTWARD_BUCKET, bucket);
        v.put(COL_OUTWARD_TIME, time);
        v.put(COL_OUTWARD_USER, user);
        v.put(COL_OUTWARD_INTAKE_ID, intakeId);
        v.put(COL_OUTWARD_VARIETY_NAME, varietyName);
        v.put(COL_OUTWARD_QUANTITY, quantity);
        v.put(COL_OUTWARD_IS_SYNCED, 0);
        long rowId = db.insert(TABLE_OUTWARD, null, v);
//        db.close();
        return rowId;
    }

    public static class OutwardRecord {
        public int id, intakeId, quantity;
        public String qr, serial, bucket, time, user, varietyName;
    }

    public List<OutwardRecord> getUnsyncedOutwards() {
        List<OutwardRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_OUTWARD, null, COL_OUTWARD_IS_SYNCED + "=?",
                new String[]{"0"}, null, null, COL_OUTWARD_ID + " ASC");
        if (c.moveToFirst()) {
            do {
                OutwardRecord r = new OutwardRecord();
                r.id = c.getInt(c.getColumnIndexOrThrow(COL_OUTWARD_ID));
                r.qr = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_QR));
                r.serial = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_SERIAL));
                r.bucket = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_BUCKET));
                r.time = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_TIME));
                r.user = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_USER));
                r.intakeId = c.getInt(c.getColumnIndexOrThrow(COL_OUTWARD_INTAKE_ID));
                r.varietyName = c.getString(c.getColumnIndexOrThrow(COL_OUTWARD_VARIETY_NAME));
                r.quantity = c.getInt(c.getColumnIndexOrThrow(COL_OUTWARD_QUANTITY));
                list.add(r);
            } while (c.moveToNext());
        }
        c.close();
//        db.close();
        return list;
    }

    public void markOutwardAsSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_OUTWARD_IS_SYNCED, 1);
        db.update(TABLE_OUTWARD, v, COL_OUTWARD_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    // ---------------- VARIETIES CACHE ----------------
    public void clearVarieties() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_VARIETIES, null, null);
//        db.close();
    }

    public void upsertVariety(int id, String name, String code, int farmId, int active) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_VARIETY_ID, id);
        v.put(COL_VARIETY_NAME, name);
        v.put(COL_VARIETY_CODE, code);
        v.put(COL_VARIETY_FARMID, farmId);
        v.put(COL_VARIETY_ACTIVE, active);
        db.insertWithOnConflict(TABLE_VARIETIES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
//        db.close();
    }

    // ✅ Wrapper for old code: maps to upsert
    public void insertVariety(int id, String name) {
        upsertVariety(id, name, "", 0, 1);  // default code, farmId=0, active=1
    }

    public List<Variety> getAllVarieties() {
        List<Variety> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_VARIETIES, null, null, null, null, null, COL_VARIETY_NAME + " ASC");
        if (c.moveToFirst()) {
            do {
                list.add(new Variety(
                        c.getInt(c.getColumnIndexOrThrow(COL_VARIETY_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_VARIETY_NAME))
                ));
            } while (c.moveToNext());
        }
        c.close();
//        db.close();
        return list;
    }

    public static class Variety {
        public int id;
        public String name;
        public Variety(int id, String name) { this.id = id; this.name = name; }
    }

    // ---------------- GREENHOUSES CACHE ----------------
    public void clearGreenhouses() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_GREENHOUSES, null, null);
//        db.close();
    }

    public void clearGreenhouses(int varietyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_GREENHOUSES, COL_GREENHOUSE_VARIETYID + "=?", new String[]{String.valueOf(varietyId)});
//        db.close();
    }

    public void upsertGreenhouse(int id, String name, int farmId, int varietyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_GREENHOUSE_ID, id);
        v.put(COL_GREENHOUSE_NAME, name);
        v.put(COL_GREENHOUSE_FARMID, farmId);
        v.put(COL_GREENHOUSE_VARIETYID, varietyId);
        db.insertWithOnConflict(TABLE_GREENHOUSES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
//        db.close();
    }

    // ✅ Wrapper for old code: maps to upsert
    public void insertGreenhouse(int id, int varietyId, String name) {
        upsertGreenhouse(id, name, 0, varietyId); // default farmId=0
    }

    public List<Greenhouse> getAllGreenhouses(int varietyId) {
        List<Greenhouse> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_GREENHOUSES, null,
                COL_GREENHOUSE_VARIETYID + "=?", new String[]{String.valueOf(varietyId)},
                null, null, COL_GREENHOUSE_NAME + " ASC");
        if (c.moveToFirst()) {
            do {
                list.add(new Greenhouse(
                        c.getInt(c.getColumnIndexOrThrow(COL_GREENHOUSE_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_GREENHOUSE_NAME))
                ));
            } while (c.moveToNext());
        }
        c.close();
//        db.close();
        return list;
    }

    public static class Greenhouse {
        public int id;
        public String name;
        public Greenhouse(int id, String name) { this.id = id; this.name = name; }
    }

    // ---------------- BUCKETS CACHE ----------------
    public void clearBuckets() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BUCKETS, null, null);
//        db.close();
    }

    public void upsertBucket(String serial, String bucketName, String qrCode,
                             String farm, String length, String status, String updatedAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_BUCKET_SERIAL, serial);
        v.put(COL_BUCKET_NAME, bucketName);
        v.put(COL_BUCKET_QR, qrCode);
        v.put(COL_BUCKET_FARM, farm);
        v.put(COL_BUCKET_LENGTH, length);
        v.put(COL_BUCKET_STATUS, status);
        v.put(COL_BUCKET_UPDATED, updatedAt);

        db.insertWithOnConflict(TABLE_BUCKETS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
//        db.close();
    }

    public boolean isBucketAvailable(String qrCode) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_BUCKETS,
                new String[]{COL_BUCKET_QR},
                COL_BUCKET_QR + "=?",
                new String[]{qrCode},
                null, null, null);

        boolean exists = (c.getCount() > 0);
        c.close();
//        db.close();
        return exists;
    }
}
