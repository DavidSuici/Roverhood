package com.suici.roverhood.utils;

import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.models.Filters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FiltersManager {
    private static Filters activeFilters = new Filters();

    // "Filter info bar" setting (the bar under the banner listing the active filters).
    // Off by default, remembered between sessions.
    private static final String PREFS = "roverhood_settings";
    private static final String KEY_INFO_BAR = "filterInfoBar";

    public static boolean isInfoBarEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_INFO_BAR, false);
    }

    public static void setInfoBarEnabled(android.content.Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_INFO_BAR, enabled).apply();
    }

    // The bar is shown only if the setting is on and some filter/sort differs from the defaults
    public static boolean showInfoBar(android.content.Context context) {
        return areFiltersOrSortEnabled() && isInfoBarEnabled(context);
    }

    public static Filters getActiveFilters() {
        return activeFilters;
    }

    public static void setActiveFilters(Filters filters) {
        activeFilters = filters;
    }

    public static void resetFilters() {
        activeFilters = new Filters();
    }

    // How many filters / sort options differ from the defaults (shown as a badge on the filter button)
    public static int activeFilterCount() {
        int count = 0;
        if (activeFilters.isAnnouncementsOnly()) count++;
        if (!activeFilters.getUsername().isEmpty()) count++;
        if (!activeFilters.getTeam().isEmpty()) count++;
        if (!activeFilters.getTopic().isEmpty()) count++;
        if (activeFilters.isOnlyLiked()) count++;
        if (activeFilters.getMinLikes() > 0) count++;
        if (activeFilters.isSortByLikes()) count++;
        if (activeFilters.isOrderAscending()) count++;
        return count;
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

        // Returns the posts in display order: newest / most liked first, or the opposite
        // when ascending. Likes ties are ordered by date.
        Comparator<PostHandler> byDate = Comparator.comparing(handler -> handler.getPost().getDate());
        Comparator<PostHandler> order = activeFilters.isSortByLikes()
                ? Comparator.<PostHandler, Integer>comparing(handler -> handler.getPost().getLikes()).thenComparing(byDate)
                : byDate;
        filtered.sort(activeFilters.isOrderAscending() ? order : order.reversed());

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

        if (postHandler.getPost().getLikes() < activeFilters.getMinLikes())
            return false;

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
            filtersText.append("Team: ").append(Teams.name(activeFilters.getTeam())).append(", ");
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