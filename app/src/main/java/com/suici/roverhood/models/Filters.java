package com.suici.roverhood.models;

import com.suici.roverhood.MainActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Filters {
    private static boolean announcementsOnly = false;
    private static String username = "";
    private static String team = "";
    private static String topic = "";
    private static boolean onlyLiked = false;
    private static int minLikes = 0;
    private static boolean sortByLikes = false;
    private static boolean orderAscending = false;

    public static boolean isAnnouncementsOnly() {
        return announcementsOnly;
    }
    public static String getUsername() { return username; }
    public static String getTeam() {
        return team;
    }
    public static String getTopic() { return topic; }
    public static boolean isOnlyLiked() {
        return onlyLiked;
    }
    public static int getMinLikes() {
        return minLikes;
    }
    public static boolean isSortByLikes() { return sortByLikes; }
    public static boolean isOrderAscending() { return orderAscending; }

    public static void setAnnouncementsOnly(boolean announcementsOnly) {
        Filters.announcementsOnly = announcementsOnly;
    }
    public static void setUsername(String username) {
        Filters.username = username;
    }
    public static void setTeam(String team) {
        Filters.team = team;
    }
    public static void setTopic(String topic) { Filters.topic = topic; }
    public static void setOnlyLiked(boolean onlyLiked) {
        Filters.onlyLiked = onlyLiked;
    }
    public static void setMinLikes(int minLikes) {
        Filters.minLikes = minLikes;
    }
    public static void setSortByLikes(boolean sortByLikes) { Filters.sortByLikes = sortByLikes; }
    public static void setOrderAscending(boolean orderAscending) { Filters.orderAscending = orderAscending; }
    public static void resetFilters() {
        announcementsOnly = false;
        username = "";
        team = "";
        topic = "";
        onlyLiked = false;
        minLikes = 0;
        sortByLikes = false;
        orderAscending = false;
    }
    public static boolean areFiltersOrSortEnabled() {
        return announcementsOnly ||
                !username.isEmpty() ||
                !team.isEmpty() ||
                !topic.isEmpty() ||
                onlyLiked ||
                minLikes > 0 ||
                sortByLikes ||
                orderAscending;
    }

    public static List<Post> filterPosts(List<Post> allPosts, MainActivity activity) {
        List<Post> filtered = new ArrayList<>();

        for (Post post : allPosts) {
            if (announcementsOnly && !post.isAnnouncement()) {
                continue;
            }

            if (!username.isEmpty() && !post.getUser().getUsername().equalsIgnoreCase(username)) {
                continue;
            }

            if (!team.isEmpty() && !post.getUser().getTeam().equals(team)) {
                continue;
            }

            if (!topic.isEmpty() && (post.getTopic() == null || !post.getTopic().getTitle().equals(topic))) {
                continue;
            }

            Map<String, Boolean> likedBy =  post.getLikedBy();
            if (onlyLiked && !(likedBy.containsKey(activity.getCurrentUser().getId()))) {
                continue;
            }

            if (post.getLikes() < minLikes) {
                continue;
            }

            filtered.add(post);
        }

        if (sortByLikes) {
            filtered.sort(Comparator.comparing(Post::getLikes));
        }

        if (orderAscending) {
            Collections.reverse(filtered);
        }

        return filtered;
    }

    public static String getFiltersText() {
        StringBuilder filtersText = new StringBuilder();

        if (!topic.isEmpty()) {
            filtersText.append("Topic: ").append(topic).append(", ");
        }
        if (!username.isEmpty()) {
            filtersText.append("User: ").append(username).append(", ");
        }
        if (!team.isEmpty()) {
            filtersText.append("Team: ").append(team).append(", ");
        }
        if (minLikes > 0) {
            filtersText.append("Min Likes: ").append(minLikes).append(", ");
        }
        if (onlyLiked) {
            filtersText.append("Only Liked, ");
        }
        if (announcementsOnly) {
            filtersText.append("Only Announcements, ");
        }

        filtersText.append("Sort by: ");
        if (sortByLikes) {
            filtersText.append("Likes ");
        }
        else {
            filtersText.append("Date ");
        }
        if (orderAscending) {
            filtersText.append("asc. ");
        }
        else {
            filtersText.append("desc. ");
        }

        return filtersText.toString();
    }

    public static boolean isVisibleAfterFilter(Post post, MainActivity activity) {
        if (announcementsOnly && !post.isAnnouncement()) {
            return false;
        }
        if (!username.isEmpty() && !post.getUser().getUsername().equals(username)) {
            return false;
        }
        if (!team.isEmpty() && !post.getUser().getTeam().equals(team)) {
            return false;
        }
        if (!topic.isEmpty() && (post.getTopic() == null || !post.getTopic().getTitle().equals(topic))) {
            return false;
        }
        Map<String, Boolean> likedBy = post.getLikedBy();
        if (onlyLiked && !(likedBy.containsKey(activity.getCurrentUser().getId()))) {
            return false;
        }
        if (post.getLikes() < minLikes) {
            return false;
        }

        return true;
    }
}