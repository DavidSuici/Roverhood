package com.suici.roverhood.models;

import java.util.ArrayList;
import java.util.List;

public class Topic {
    private String title;
    private long creationTime;
    private String id;

    private static final List<Topic> allTopics = new ArrayList<>();

    public Topic() {
    }

    public Topic(String id, String title, long creationTime) {
        this.id = id;
        this.title = title;
        this.creationTime = creationTime;
    }

    public static List<Topic> getAllTopics() {
        return allTopics;
    }

    public static void addTopic(Topic topic) {
        if (!allTopics.contains(topic)) {
            allTopics.add(topic);
        }
    }

    public static void clearTopics() {
        allTopics.clear();
    }

    public static Topic findTopicByTitle(String title) {
        for (Topic topic : allTopics) {
            if (topic.getTitle().equalsIgnoreCase(title)) {
                return topic;
            }
        }
        return null;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }
}
