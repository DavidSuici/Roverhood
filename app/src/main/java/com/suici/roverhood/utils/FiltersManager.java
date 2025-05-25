package com.suici.roverhood.utils;

import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.models.Filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FiltersManager {
    private static Filters activeFilters = new Filters();

    public static Filters getActiveFilters() {
        return activeFilters;
    }

    public static void setActiveFilters(Filters filters) {
        activeFilters = filters;
    }

    public static void resetFilters() {
        activeFilters = new Filters();
    }

    public static boolean areFiltersOrSortEnabled() {
        return activeFilters.isAnnouncementsOnly() ||
                !activeFilters.getUsername().isEmpty() ||
                !activeFilters.getTeam().isEmpty() ||
                !activeFilters.getTopic().isEmpty() ||
                activeFilters.isOnlyLiked() ||
                activeFilters.getMinLikes() > 0 ||
                activeFilters.isSortByLikes() ||
                activeFilters.isOrderAscending();
    }

    public static List<PostHandler> filterPosts(List<PostHandler> allPostHandlers, MainActivity activity) {
        List<PostHandler> filtered = new ArrayList<>();

        for (PostHandler postHandler : allPostHandlers) {
            if (!isVisibleAfterFilter(postHandler, activity))
                continue;
            filtered.add(postHandler);
        }

        if (activeFilters.isSortByLikes()) {
            filtered.sort(Comparator.comparing(handler -> handler.getPost().getLikes()));
        }

        if (activeFilters.isOrderAscending()) {
            Collections.reverse(filtered);
        }

        return filtered;
    }

    public static boolean isVisibleAfterFilter(PostHandler postHandler, MainActivity activity) {
        if (activeFilters.isAnnouncementsOnly() && !postHandler.getPost().isAnnouncement()) return false;

        if (!activeFilters.getUsername().isEmpty() &&
                !postHandler.getPost().getUser().getUsername().equalsIgnoreCase(activeFilters.getUsername()))
            return false;

        if (!activeFilters.getTeam().isEmpty() &&
                !postHandler.getPost().getUser().getTeam().equals(activeFilters.getTeam()))
            return false;

        if (!activeFilters.getTopic().isEmpty() &&
                (postHandler.getPost().getTopic() == null ||
                        !postHandler.getPost().getTopic().getTitle().equals(activeFilters.getTopic())))
            return false;

        if (activeFilters.isOnlyLiked() &&
                !postHandler.getPost().getLikedBy().containsKey(activity.getCurrentUser().getId()))
            return false;

        if (postHandler.getPost().getLikes() < activeFilters.getMinLikes()) return false;

        return true;
    }

    public static String getFiltersText() {
        StringBuilder filtersText = new StringBuilder();

        if (!activeFilters.getTopic().isEmpty()) {
            filtersText.append("Topic: ").append(activeFilters.getTopic()).append(", ");
        }
        if (!activeFilters.getUsername().isEmpty()) {
            filtersText.append("User: ").append(activeFilters.getUsername()).append(", ");
        }
        if (!activeFilters.getTeam().isEmpty()) {
            filtersText.append("Team: ").append(activeFilters.getTeam()).append(", ");
        }
        if (activeFilters.getMinLikes() > 0) {
            filtersText.append("Min Likes: ").append(activeFilters.getMinLikes()).append(", ");
        }
        if (activeFilters.isOnlyLiked()) {
            filtersText.append("Only Liked, ");
        }
        if (activeFilters.isAnnouncementsOnly()) {
            filtersText.append("Only Announcements, ");
        }

        filtersText.append("Sort by: ");
        filtersText.append(activeFilters.isSortByLikes() ? "Likes " : "Date ");
        filtersText.append(activeFilters.isOrderAscending() ? "asc." : "desc.");

        return filtersText.toString();
    }
}