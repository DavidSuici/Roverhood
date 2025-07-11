package com.suici.roverhood.databases;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.suici.roverhood.models.Post;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.models.Topic;
import com.suici.roverhood.models.User;

import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    public static final int DATABASE_VERSION = 27;

    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_ACCESSCODE = "accessCode";
    public static final String COLUMN_USERTYPE = "userType";
    public static final String COLUMN_TEAM = "team";
    public static final String COLUMN_OFFLINE_USER = "offlineUser";

    private static final String CREATE_TABLE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_USERNAME + " TEXT, " +
                    COLUMN_ACCESSCODE + " TEXT, " +
                    COLUMN_USERTYPE + " TEXT, " +
                    COLUMN_TEAM + " TEXT, " +
                    COLUMN_OFFLINE_USER + " INTEGER DEFAULT 0, " +
                    "loggedIn INTEGER DEFAULT 0);";

    public static final String TABLE_SESSION = "session";
    public static final String COLUMN_CURRENT_USER_ID = "idLoggedIn";
    public static final String COLUMN_LAST_USER_ID = "idPrevLoggedIn";

    private static final String CREATE_TABLE_SESSION =
            "CREATE TABLE " + TABLE_SESSION + " (" +
                    "id INTEGER PRIMARY KEY CHECK(id = 1), " +  // Fixed ID, always = 1
                    COLUMN_CURRENT_USER_ID + " TEXT, " +
                    COLUMN_LAST_USER_ID + " TEXT);";

    public static final String TABLE_POSTS = "posts";
    public static final String COLUMN_POST_ID = "postId";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_TOPIC_ID = "topicId";
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
                    COLUMN_TOPIC_ID + " TEXT, " +
                    COLUMN_DESCRIPTION + " TEXT, " +
                    COLUMN_IMAGE_PATH + " TEXT, " +
                    COLUMN_LIKES + " INTEGER, " +
                    COLUMN_LIKED_BY + " TEXT, " +
                    COLUMN_ANNOUNCEMENT + " INTEGER DEFAULT 0, " +
                    COLUMN_USERID + " TEXT, " +
                    COLUMN_VERSION + " INTEGER DEFAULT 0);";

    public static final String TABLE_TOPICS = "topics";
    public static final String COLUMN_TOPIC_TITLE = "title";
    public static final String COLUMN_TOPIC_CREATION_TIME = "creationTime";

    private static final String CREATE_TABLE_TOPICS =
            "CREATE TABLE " + TABLE_TOPICS + " (" +
                    COLUMN_TOPIC_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_TOPIC_TITLE + " TEXT, " +
                    COLUMN_TOPIC_CREATION_TIME + " INTEGER);";

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TABLE_SESSION);
        db.execSQL(CREATE_TABLE_POSTS);
        db.execSQL(CREATE_TABLE_TOPICS);

        // Initialize with empty session row
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put(COLUMN_CURRENT_USER_ID, "");
        values.put(COLUMN_LAST_USER_ID, "");
        db.insert(TABLE_SESSION, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TOPICS);
        onCreate(db);
    }


    // USER TABLE ACTIONS

    public void insertUser(User user) {
        if (user.getId() == null || user.getId().isEmpty() ||
                user.getUsername() == null || user.getUsername().isEmpty() ||
                user.getAccessCode() == null || user.getAccessCode().isEmpty() ||
                user.getUserType() == null || user.getUserType().isEmpty() ||
                user.getTeam() == null || user.getTeam().isEmpty()) {
            return;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_ID, user.getId());
        values.put(COLUMN_USERNAME, user.getUsername());
        values.put(COLUMN_ACCESSCODE, user.getAccessCode());
        values.put(COLUMN_USERTYPE, user.getUserType());
        values.put(COLUMN_TEAM, user.getTeam());
        values.put(COLUMN_OFFLINE_USER, 0);

        db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void setUsersOffline() {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_OFFLINE_USER, 1);
        db.update(TABLE_USERS, values, null, null);
    }

    public User getUserByUsernameAndAccessCode(String username, String accessCode) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COLUMN_USERNAME + " = ?",
                new String[]{username},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE));
                if (BCrypt.checkpw(accessCode, storedHash)) {
                    User user = new User();
                    user.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)));
                    user.setAccessCode(storedHash);
                    user.setUserType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE)));
                    user.setTeam(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM)));
                    user.setOfflineUser(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OFFLINE_USER)) == 1);
                    return user;
                }
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
                user.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)));
                user.setAccessCode(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE)));
                user.setUserType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE)));
                user.setTeam(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM)));
                user.setOfflineUser(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OFFLINE_USER)) == 1);

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
                    User user = new User();
                    user.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                    user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)));
                    user.setAccessCode(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCESSCODE)));
                    user.setUserType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERTYPE)));
                    user.setTeam(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEAM)));
                    user.setOfflineUser(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OFFLINE_USER)) == 1);

                    usersMap.put(user.getId(), user);
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
                TABLE_SESSION ,
                new String[]{COLUMN_CURRENT_USER_ID},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                prevId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_USER_ID));
            }
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_CURRENT_USER_ID, userid);
        values.put(COLUMN_LAST_USER_ID, prevId);
        db.update(TABLE_SESSION , values, "id = 1", null);
    }

    public void markLoggedOut() {
        SQLiteDatabase db = this.getWritableDatabase();

        try (Cursor cursor = db.query(
                TABLE_SESSION ,
                new String[]{COLUMN_CURRENT_USER_ID},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String currentLoggedIn = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_USER_ID));

                ContentValues values = new ContentValues();
                values.put(COLUMN_LAST_USER_ID, currentLoggedIn);
                values.put(COLUMN_CURRENT_USER_ID, "");
                db.update(TABLE_SESSION , values, "id = 1", null);
            }
        }
    }

    public User getLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SESSION ,
                new String[]{COLUMN_CURRENT_USER_ID},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String userId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_USER_ID));
                return getUserById(userId);
            }
        }
        return null;
    }

    public User getPrevLoggedInUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SESSION ,
                new String[]{COLUMN_LAST_USER_ID},
                "id = 1",
                null,
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                String userId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_USER_ID));
                return getUserById(userId);
            }
        }
        return null;
    }


    // POSTS TABLE ACTIONS

    public void insertPost(Post post) {
        SQLiteDatabase db = this.getWritableDatabase();

        String likedByString = serializeLikedBy(post.getLikedBy());

        ContentValues values = new ContentValues();
        values.put(COLUMN_POST_ID, post.getId());
        values.put(COLUMN_DATE, post.getDate());
        values.put(COLUMN_TOPIC_ID, post.getTopic() != null ? post.getTopic().getId() : null);
        values.put(COLUMN_DESCRIPTION, post.getDescription());
        values.put(COLUMN_IMAGE_PATH, post.getImageUrl());
        values.put(COLUMN_LIKES, post.getLikes());
        values.put(COLUMN_LIKED_BY, likedByString);
        values.put(COLUMN_ANNOUNCEMENT, post.isAnnouncement() ? 1 : 0);
        values.put(COLUMN_USERID, post.getUser().getId());
        values.put(COLUMN_VERSION, post.getVersion());

        db.insertWithOnConflict(TABLE_POSTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String serializeLikedBy(Map<String, Boolean> likedBy) {
        StringBuilder likedByString = new StringBuilder();

        for (String userId : likedBy.keySet()) {
            if (likedByString.length() > 0) {
                likedByString.append(",");
            }
            likedByString.append(userId);
        }

        return likedByString.toString();
    }

    private static Map<String, Boolean> deserializeLikedBy(String likedByString) {
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

    private boolean deleteFileFromPath(String path) {
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

    public Map<String, PostHandler> getAllOfflinePosts(Fragment fragment, Map<String, User> usersMap, Map<String, Topic> topicMap) {
        SQLiteDatabase db = this.getReadableDatabase();
        Map<String, PostHandler> postsMap = new LinkedHashMap<>();

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
                    String topicId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TOPIC_ID));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION));
                    String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH));
                    int likes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LIKES));
                    String likedByString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LIKED_BY));
                    int announcement = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ANNOUNCEMENT));
                    String userId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERID));
                    int version = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VERSION));

                    Map<String, Boolean> likedBy = deserializeLikedBy(likedByString);
                    User user = usersMap.get(userId);
                    Topic topic = topicMap.get(topicId);

                    if (user != null) {
                        Post post = new Post(
                                postId,
                                user,
                                date,
                                topic,
                                description,
                                imagePath,
                                likes,
                                likedBy,
                                announcement == 1,
                                version
                        );

                        PostHandler postHandler = new PostHandler(fragment, post, true);
                        postsMap.put(postId, postHandler);
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


    // TOPICS TABLE ACTIONS

    public Map<String, Topic> getAllTopics() {
        SQLiteDatabase db = this.getReadableDatabase();
        Map<String, Topic> topicsMap = new LinkedHashMap<>();

        try (Cursor cursor = db.query(
                TABLE_TOPICS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_TOPIC_ID  + " ASC"
        )) {
            if (cursor.moveToFirst()) {
                do {
                    String topicId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TOPIC_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TOPIC_TITLE));
                    long creationTime = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TOPIC_CREATION_TIME));

                    Topic topic = new Topic(topicId, title, creationTime);
                    topicsMap.put(topicId, topic);
                } while (cursor.moveToNext());
            }
        }
        return topicsMap;
    }

    public void refreshTopics(List<Topic> newTopics) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TOPICS, null, null);

        ContentValues values = new ContentValues();
        for (Topic topic : newTopics) {
            values.put(COLUMN_TOPIC_ID, topic.getId());
            values.put(COLUMN_TOPIC_TITLE, topic.getTitle());
            values.put(COLUMN_TOPIC_CREATION_TIME, topic.getCreationTime());
            db.insert(TABLE_TOPICS, null, values);
            values.clear();
        }
    }

    public void removeTopic(String topicId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TOPICS, COLUMN_TOPIC_ID + " = ?", new String[]{topicId});
    }
}
