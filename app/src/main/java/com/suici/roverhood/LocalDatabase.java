package com.suici.roverhood;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalDatabase extends SQLiteOpenHelper {

    private static LocalDatabase instance;

    public static synchronized LocalDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDatabase(context.getApplicationContext());
        }
        return instance;
    }

    public static final String DATABASE_NAME = "roverhood.db";
    public static final int DATABASE_VERSION = 20;

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

    public static final String TABLE_POSTS = "posts";
    public static final String COLUMN_POST_ID = "postId";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_IMAGE_PATH = "imagePath";
    public static final String COLUMN_LIKES = "likes";
    public static final String COLUMN_LIKED_BY = "likedBy";
    public static final String COLUMN_ANNOUNCEMENT = "announcement";
    public static final String COLUMN_USERID = "userId";
    public static final String COLUMN_VERSION = "version";

    private static final String CREATE_TABLE_POSTS =
            "CREATE TABLE " + TABLE_POSTS + " (" +
                    COLUMN_POST_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_DATE + " INTEGER, " +
                    COLUMN_DESCRIPTION + " TEXT, " +
                    COLUMN_IMAGE_PATH + " TEXT, " +
                    COLUMN_LIKES + " INTEGER, " +
                    COLUMN_LIKED_BY + " TEXT, " +
                    COLUMN_ANNOUNCEMENT + " INTEGER DEFAULT 0, " +
                    COLUMN_USERID + " TEXT, " +
                    COLUMN_VERSION + " INTEGER DEFAULT 0);";

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_SESSION);
        db.execSQL(CREATE_TABLE_POSTS);

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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        onCreate(db);
    }

    // USER TABLE ACTIONS

    public void insertUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, user.id);
        values.put(COLUMN_USERNAME, user.username);
        values.put(COLUMN_ACCESSCODE, user.accessCode);
        values.put(COLUMN_USERTYPE, user.userType);
        values.put(COLUMN_TEAM, user.team);

        db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public User getUserByUsernameAndAccessCode(String username, String accessCode) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_USERNAME + " = ? AND " + COLUMN_ACCESSCODE + " = ?",
                new String[]{username, accessCode},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                User user = new User();
                user.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
                user.username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
                user.accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
                user.userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
                user.team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));
                return user;
            }
        }
        return null;
    }

    public User getUserById(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_ID + " = ?",
                new String[]{userId},
                null, null, null
        )) {
            if (cursor.moveToFirst()) {
                User user = new User();
                user.id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
                user.username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
                user.accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
                user.userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
                user.team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));
                return user;
            }
        }
        return null;
    }

    public Map<String, User> getAllUsers() {
        SQLiteDatabase db = this.getReadableDatabase();
        Map<String, User> usersMap = new LinkedHashMap<>();

        try (Cursor cursor = db.query(
                TABLE_USERS,
                null,
                null,
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                do {
                    String userId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    String username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME));
                    String accessCode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
                    String userType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE));
                    String team = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM));

                    User user = new User();
                    user.id = userId;
                    user.username = username;
                    user.accessCode = accessCode;
                    user.userType = userType;
                    user.team = team;

                    usersMap.put(userId, user);
                } while (cursor.moveToNext());
            }
        }
        return usersMap;
    }

    // SESSION TABLE ACTIONS

    public void markLoggedIn(String userid) {
        SQLiteDatabase db = this.getWritableDatabase();

        String prevId = "";
        try (Cursor cursor = db.query(
                "session",
                new String[]{"idLoggedIn"},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                prevId = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));
            }
        }

        ContentValues values = new ContentValues();
        values.put("idLoggedIn", userid);
        values.put("idPrevLoggedIn", prevId);
        db.update("session", values, "id = 1", null);
    }

    public void markLoggedOut() {
        SQLiteDatabase db = this.getWritableDatabase();

        try (Cursor cursor = db.query(
                "session",
                new String[]{"idLoggedIn"},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String currentLoggedIn = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));

                ContentValues values = new ContentValues();
                values.put("idPrevLoggedIn", currentLoggedIn);
                values.put("idLoggedIn", "");
                db.update("session", values, "id = 1", null);
            }
        }
    }

    public User getLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                "session",
                new String[]{"idLoggedIn"},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String userId = cursor.getString(cursor.getColumnIndexOrThrow("idLoggedIn"));
                return getUserById(userId);
            }
        }
        return null;
    }

    public User getPrevLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                "session",
                new String[]{"idPrevLoggedIn"},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String userId = cursor.getString(cursor.getColumnIndexOrThrow("idPrevLoggedIn"));
                return getUserById(userId);
            }
        }
        return null;
    }

    // POSTS TABLE ACTIONS

    public void insertPost(String postId, Long date, String description, String imagePath, int likes, Map<String, Boolean> likedBy, boolean announcement, String userId, int version) {
        SQLiteDatabase db = this.getWritableDatabase();

        String likedByString = serializeLikedBy(likedBy);

        ContentValues values = new ContentValues();
        values.put(COLUMN_POST_ID, postId);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_DESCRIPTION, description);
        values.put(COLUMN_IMAGE_PATH, imagePath);
        values.put(COLUMN_LIKES, likes);
        values.put(COLUMN_LIKED_BY, likedByString);
        values.put(COLUMN_ANNOUNCEMENT, announcement ? 1 : 0);
        values.put(COLUMN_USERID, userId);
        values.put(COLUMN_VERSION, version);

        db.insertWithOnConflict(TABLE_POSTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String serializeLikedBy(Map<String, Boolean> likedBy) {
        StringBuilder likedByString = new StringBuilder();

        for (String userId : likedBy.keySet()) {
            if (likedByString.length() > 0) {
                likedByString.append(",");
            }
            likedByString.append(userId);
        }

        return likedByString.toString();
    }

    public static Map<String, Boolean> deserializeLikedBy(String likedByString) {
        Map<String, Boolean> likedBy = new HashMap<>();

        String[] userIds = likedByString.split(",");

        for (String userId : userIds) {
            likedBy.put(userId, true);
        }

        return likedBy;
    }

    public void deletePost(String postId) {
        SQLiteDatabase db = this.getWritableDatabase();

        String imagePath = null;
        try (Cursor cursor = db.query(
                TABLE_POSTS,
                new String[]{COLUMN_IMAGE_PATH},
                COLUMN_POST_ID + " = ?",
                new String[]{postId},
                null, null, null
        )) {
            if (cursor.moveToFirst()) {
                imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH));
            }
        }

        db.delete(TABLE_POSTS, COLUMN_POST_ID + " = ?", new String[]{postId});
        if (imagePath != null) {
            boolean isDeleted = deleteFileFromPath(imagePath);
            if (isDeleted) {
                Log.d("LocalDelete", "Image deleted from local storage successfully.");
            } else {
                Log.e("LocalDelete", "Failed to delete image from local storage or it does not exist.");
            }
        }
    }

    public boolean deleteFileFromPath(String path) {
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public Integer getPostVersion(String postId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_POSTS,
                new String[]{COLUMN_VERSION},
                COLUMN_POST_ID + " = ?",
                new String[]{postId},
                null, null, null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VERSION));
            }
        }
        return null;
    }

    public Map<String, Post> getAllOfflinePosts(Fragment fragment, Map<String, User> usersMap) {
        SQLiteDatabase db = this.getReadableDatabase();
        Map<String, Post> postsMap = new LinkedHashMap<>();

        try (Cursor cursor = db.query(
                TABLE_POSTS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_DATE + " ASC"
        )) {
            if (cursor.moveToFirst()) {
                do {
                    String postId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_POST_ID));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION));
                    String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH));
                    int likes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LIKES));
                    String likedByString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LIKED_BY));
                    int announcement = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ANNOUNCEMENT));
                    String userId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERID));
                    int version = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VERSION));

                    Map<String, Boolean> likedBy = deserializeLikedBy(likedByString);
                    User user = usersMap.get(userId);

                    if (user != null) {
                        Post post = new Post(
                                fragment,
                                postId,
                                date,
                                user,
                                description,
                                imagePath,
                                likes,
                                likedBy,
                                announcement == 1,
                                version,
                                true
                        );
                        postsMap.put(postId, post);
                    }
                } while (cursor.moveToNext());
            }
        }
        return postsMap;
    }

    public void updatePostLikes(String postId, int newLikes, Map<String, Boolean> newLikedBy) {
        SQLiteDatabase db = this.getWritableDatabase();

        String likedByString = serializeLikedBy(newLikedBy);

        ContentValues values = new ContentValues();
        values.put(COLUMN_LIKES, newLikes);
        values.put(COLUMN_LIKED_BY, likedByString);

        String selection = COLUMN_POST_ID + " = ?";
        String[] selectionArgs = { postId };

        db.update(TABLE_POSTS, values, selection, selectionArgs);
    }
}
