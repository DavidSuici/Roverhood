package com.suici.roverhood.models;

import java.util.HashMap;
import java.util.Map;

public class Comment {
    private String id;
    private String postId;
    private String userId;
    private String text;
    private long date;
    private boolean edited;
    // The number of likes is the size of this map, so they can't get out of sync
    private Map<String, Boolean> likedBy;

    public Comment(String id, String postId, String userId, String text, long date, boolean edited, Map<String, Boolean> likedBy) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.text = text;
        this.date = date;
        this.edited = edited;
        this.likedBy = likedBy != null ? likedBy : new HashMap<>();
    }

    public String getId() { return id; }
    public String getPostId() { return postId; }
    public String getUserId() { return userId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }

    public Map<String, Boolean> getLikedBy() { return likedBy; }
    public int getLikes() { return likedBy.size(); }
    public boolean isLikedBy(String userId) { return likedBy.containsKey(userId); }
}
