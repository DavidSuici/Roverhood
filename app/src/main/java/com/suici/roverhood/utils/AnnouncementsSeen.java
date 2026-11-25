package com.suici.roverhood.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

// Which announcements this phone has already shown on screen (kept between sessions).
// The announcements button shows how many loaded announcements are not seen yet, and a tap
// goes to the oldest one of them.
public class AnnouncementsSeen {

    private static final String PREFS = "announcementsSeen";
    private static final String KEY = "seenIds";

    private static Set<String> seen;

    private static synchronized Set<String> load(Context context) {
        if (seen == null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            seen = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
        }
        return seen;
    }

    public static synchronized boolean isSeen(Context context, String postId) {
        return postId != null && load(context).contains(postId);
    }

    // Returns true if it wasn't seen before (so the count changed)
    public static synchronized boolean markSeen(Context context, String postId) {
        if (postId == null || !load(context).add(postId)) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, new HashSet<>(seen)).apply();
        return true;
    }
}
