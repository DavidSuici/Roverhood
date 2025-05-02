package com.suici.roverhood;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LocalDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "roverhood.db";
    public static final int DATABASE_VERSION = 7;

    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_ACCESSCODE = "accessCode";
    public static final String COLUMN_USERTYPE = "userType";
    public static final String COLUMN_TEAM = "team";

    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_USERNAME + " TEXT, " +
                    COLUMN_ACCESSCODE + " TEXT, " +
                    COLUMN_USERTYPE + " TEXT, " +
                    COLUMN_TEAM + " TEXT, " +
                    "loggedIn INTEGER DEFAULT 0);";

    public static final String TABLE_SESSION = "session";
    public static final String COLUMN_LOGGED_IN_ID = "idLoggedIn";
    public static final String COLUMN_PREV_LOGGED_IN_ID = "idPrevLoggedIn";

    private static final String CREATE_TABLE_SESSION =
            "CREATE TABLE " + TABLE_SESSION + " (" +
                    "id INTEGER PRIMARY KEY CHECK(id = 1), " +  // Fixed ID, always = 1
                    COLUMN_LOGGED_IN_ID + " TEXT, " +
                    COLUMN_PREV_LOGGED_IN_ID + " TEXT);";

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_SESSION);

        // Initialize with empty session row
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put(COLUMN_LOGGED_IN_ID, "");
        values.put(COLUMN_PREV_LOGGED_IN_ID, "");
        db.insert(TABLE_SESSION, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSION); // ← Add this line
        onCreate(db);
    }

    public void insertUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, user.id);
        values.put(COLUMN_USERNAME, user.username);
        values.put(COLUMN_ACCESSCODE, user.accessCode);
        values.put(COLUMN_USERTYPE, user.userType);
        values.put(COLUMN_TEAM, user.team);

        db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public void logAllUsers() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
                String accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
                String userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
                String team = cursor.getString(cursor.getColumnIndexOrThrow("team")); // if you added it

                Log.d("SQLiteTest", "User: " + id + ", " + username + ", " + accessCode + ", " + userType + ", " + team);
            } while (cursor.moveToNext());
        } else {
            Log.d("SQLiteTest", "No users found");
        }

        cursor.close();
        db.close();
    }

    public User getUserByUsername(String username) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_USERNAME + " = ?",
                new String[]{username},
                null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            User user = new User();
            user.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
            user.username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
            user.accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
            user.userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
            user.team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));
            cursor.close();
            db.close();
            return user;
        }

        if (cursor != null) cursor.close();
        db.close();
        return null; // Not found
    }

    public User getUserByUsernameAndAccessCode(String username, String accessCode) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_USERNAME + " = ? AND " + COLUMN_ACCESSCODE + " = ?",
                new String[]{username, accessCode},
                null, null, null
        );

        User user = null;
        if (cursor != null && cursor.moveToFirst()) {
            user = new User();
            user.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
            user.username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
            user.accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
            user.userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
            user.team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));
            cursor.close();
        }

        db.close();
        return user;
    }

    public User getUserById(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_ID + " = ?",
                new String[]{userId},
                null, null, null
        );

        User user = null;
        if (cursor != null && cursor.moveToFirst()) {
            user = new User();
            user.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
            user.username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
            user.accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
            user.userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
            user.team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));
            cursor.close();
        }

        db.close();
        return user;
    }

    public void markLoggedIn(String userid) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Step 1: Retrieve the current logged-in ID (if any)
        Cursor cursor = db.query("session", new String[]{"idLoggedIn"}, "id = 1", null, null, null, null);
        String prevId = "";
        if (cursor != null && cursor.moveToFirst()) {
            prevId = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));
            cursor.close();
        }

        // Step 2: Update session with new logged-in ID and preserve previous one
        ContentValues values = new ContentValues();
        values.put("idLoggedIn", userid);
        values.put("idPrevLoggedIn", prevId);  // ← preserves the old one

        db.update("session", values, "id = 1", null);
        db.close();
    }

    public void markLoggedOut() {
        SQLiteDatabase db = this.getWritableDatabase();

        // Step 1: Retrieve current logged-in user from session
        Cursor cursor = db.query("session", new String[]{"idLoggedIn"}, "id = 1", null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String currentLoggedIn = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));
            cursor.close();

            // Step 2: Update session table
            ContentValues values = new ContentValues();
            values.put("idPrevLoggedIn", currentLoggedIn); // Move loggedIn → prevLoggedIn
            values.put("idLoggedIn", "");                  // Clear loggedIn

            db.update("session", values, "id = 1", null);
        }

        db.close();
    }

    public User getLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        String userId = null;

        // Get idLoggedIn from session
        Cursor cursor = db.query("session", new String[]{"idLoggedIn"}, "id = 1", null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            userId = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));
            cursor.close();
        }

        db.close();

        if (userId != null) {
            return getUserById(userId); // Reuse your existing method
        } else {
            return null;
        }
    }

    public User getPrevLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        String userId = null;

        // Get idPrevLoggedIn from session
        Cursor cursor = db.query("session", new String[]{"idPrevLoggedIn"}, "id = 1", null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            userId = cursor.getString(cursor.getColumnIndexOrThrow("idPrevLoggedIn"));
            cursor.close();
        }

        db.close();

        if (userId != null) {
            return getUserById(userId);
        } else {
            return null;
        }
    }

}
