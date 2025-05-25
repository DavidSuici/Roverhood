package com.suici.roverhood.models;

public class Filters {
    private boolean announcementsOnly = false;
    private String username = "";
    private String team = "";
    private String topic = "";
    private boolean onlyLiked = false;
    private int minLikes = 0;
    private boolean sortByLikes = false;
    private boolean orderAscending = false;

    public Filters() {
        this.announcementsOnly = false;
        this.username = "";
        this.team = "";
        this.topic = "";
        this.onlyLiked = false;
        this.minLikes = 0;
        this.sortByLikes = false;
        this.orderAscending = false;
    }

    public Filters(boolean announcementsOnly, String username, String team, String topic, boolean onlyLiked, int minLikes, boolean sortByLikes, boolean orderAscending) {
        this.announcementsOnly = announcementsOnly;
        this.username = username;
        this.team = team;
        this.topic = topic;
        this.onlyLiked = onlyLiked;
        this.minLikes = minLikes;
        this.sortByLikes = sortByLikes;
        this.orderAscending = orderAscending;
    }

    public boolean isAnnouncementsOnly() { return announcementsOnly; }
    public void setAnnouncementsOnly(boolean announcementsOnly) { this.announcementsOnly = announcementsOnly; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public boolean isOnlyLiked() { return onlyLiked; }
    public void setOnlyLiked(boolean onlyLiked) { this.onlyLiked = onlyLiked; }

    public int getMinLikes() { return minLikes; }
    public void setMinLikes(int minLikes) { this.minLikes = minLikes; }

    public boolean isSortByLikes() { return sortByLikes; }
    public void setSortByLikes(boolean sortByLikes) { this.sortByLikes = sortByLikes; }

    public boolean isOrderAscending() { return orderAscending; }
    public void setOrderAscending(boolean orderAscending) { this.orderAscending = orderAscending; }
}
