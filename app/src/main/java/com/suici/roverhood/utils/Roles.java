package com.suici.roverhood.utils;

import com.suici.roverhood.models.User;

// User roles (users/{id}/userType).
// - PARTICIPANT: posts, comments, likes
// - ORGANIZER:   + moderation (edit/delete any post or comment), teams, users, activation codes
// - MODERATOR:   same rights as ORGANIZER, and is notified about every new post
// - ADMIN:       everything, + changing roles and creating ORGANIZER/MODERATOR/ADMIN codes;
//                also notified about every new post
// The "notified about every new post" part is in functions/index.js (notifyNewPost).
public class Roles {

    public static final String PARTICIPANT = "PARTICIPANT";
    public static final String ORGANIZER = "ORGANIZER";
    public static final String MODERATOR = "MODERATOR";
    public static final String ADMIN = "ADMIN";

    // In the order shown in the role dropdowns
    public static final String[] ALL = {PARTICIPANT, ORGANIZER, MODERATOR, ADMIN};

    public static boolean isAdmin(User user) {
        return user != null && ADMIN.equals(user.getUserType());
    }

    // Organizers, moderators and admins: moderation and management rights
    public static boolean isStaff(User user) {
        if (user == null) return false;
        String type = user.getUserType();
        return ADMIN.equals(type) || ORGANIZER.equals(type) || MODERATOR.equals(type);
    }
}
