package com.suici.roverhood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FilterOptions {
    private static boolean announcementsOnly = false;
    private static String username = "";
    private static String team = "";
    private static boolean onlyLiked = false;
    private static int minLikes = 0;

    public static boolean isAnnouncementsOnly() {
        return announcementsOnly;
    }
    public static String getUsername() { return username; }
    public static String getTeam() {
        return team;
    }
    public static boolean isOnlyLiked() {
        return onlyLiked;
    }
    public static int getMinLikes() {
        return minLikes;
    }

    public static void setAnnouncementsOnly(boolean announcementsOnly) {
        FilterOptions.announcementsOnly = announcementsOnly;
    }
    public static void setUsername(String username) {
        FilterOptions.username = username;
    }
    public static void setTeam(String team) {
        FilterOptions.team = team;
    }
    public static void setOnlyLiked(boolean onlyLiked) {
        FilterOptions.onlyLiked = onlyLiked;
    }
    public static void setMinLikes(int minLikes) {
        FilterOptions.minLikes = minLikes;
    }
    public static void resetFilters() {
        announcementsOnly = false;
        username = "";
        team = "";
        onlyLiked = false;
        minLikes = 0;
    }
    public static boolean areFiltersEnabled() {
        return announcementsOnly ||
                !username.isEmpty() ||
                !team.isEmpty() ||
                onlyLiked ||
                minLikes > 0;
    }

    public static List<Post> filterPosts(List<Post> allPosts, MainActivity activity) {
        List<Post> filtered = new ArrayList<>();

        for (Post post : allPosts) {
            if (announcementsOnly && !post.isAnnouncement()) {
                continue;
            }

            if (!username.isEmpty() && !post.getUser().getUsername().equals(username)) {
                continue;
            }

            if (!team.isEmpty() && !post.getUser().getTeam().equals(team)) {
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

        return filtered;
    }

    public static String getFiltersText() {
        StringBuilder filtersText = new StringBuilder();

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

        if (filtersText.length() > 0) {
            filtersText.setLength(filtersText.length() - 2);
        } else {
            filtersText.append("No Filters");
        }

        return filtersText.toString();
    }
}