package com.suici.roverhood.models;

import java.util.HashMap;
import java.util.Map;

public class Post {
    private String id;
    private User user;
    private Long date;
    private Topic topic;
    private String description;
    private String imageUrl;
    private int likes;
    private Map<String, Boolean> likedBy;
    private boolean announcement;
    private int version;

    public Post(String id, User user, Long date, Topic topic, String description, String imageUrl, int likes, Map<String, Boolean> likedBy, Boolean announcement, int version) {
        this.id = id;
        this.user = user;
        this.date = date;
        this.topic = topic;
        this.description = description;
        this.imageUrl = imageUrl;
        this.likes = likes;
        this.likedBy = likedBy != null ? likedBy : new HashMap<>();
        this.announcement = announcement;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getDate() { return date; }
    public void setDate(Long date) { this.date = date; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }
    public void incrementLikes() { this.likes++; }
    public void decrementLikes() { this.likes--; }

    public Map<String, Boolean> getLikedBy() { return likedBy; }
    public void setLikedBy(Map<String, Boolean> likedBy) { this.likedBy = likedBy; }

    public boolean isAnnouncement() { return announcement; }
    public void setAnnouncement(boolean announcement) { this.announcement = announcement; }

    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }
}
