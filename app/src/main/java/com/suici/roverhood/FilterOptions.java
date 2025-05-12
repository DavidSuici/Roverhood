package com.suici.roverhood;

public class FilterOptions {
    private static boolean announcementsOnly = false;
    private static String userId = null;
    private static String teamId = null;
    private static boolean onlyLiked = false;
    private static int minLikes = 0;

    public static boolean isAnnouncementsOnly() {
        return announcementsOnly;
    }

    public static String getUserId() {
        return userId;
    }

    public static String getTeamId() {
        return teamId;
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

    public static void setUserId(String userId) {
        FilterOptions.userId = userId;
    }

    public static void setTeamId(String teamId) {
        FilterOptions.teamId = teamId;
    }

    public static void setOnlyLiked(boolean onlyLiked) {
        FilterOptions.onlyLiked = onlyLiked;
    }

    public static void setMinLikes(int minLikes) {
        FilterOptions.minLikes = minLikes;
    }

    public static void resetFilters() {
        announcementsOnly = false;
        userId = null;
        teamId = null;
        onlyLiked = false;
        minLikes = 0;
    }
}