package com.suici.roverhood.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// Optional profile information of each user, stored in Firebase at userInfo/{userId}.
// Every field is optional; only the filled ones are shown. Today it holds the profile photo
// (photoUrl + photoVersion, the photo itself is in Storage at profilePics/{userId}.jpg);
// more fields (local center, links, age, ...) can be added as new keys without changes here.
// A copy is kept in SharedPreferences so the info also shows offline.
public class UserProfiles {

    public static final String FIELD_PHOTO_URL = "photoUrl";
    public static final String FIELD_PHOTO_VERSION = "photoVersion";

    // Optional "User Info" fields, filled in from Edit profile and shown in the profile panel
    public static final String FIELD_REAL_NAME = "realName";
    public static final String FIELD_LOCALITY = "locality";
    public static final String FIELD_LOCAL_CENTER = "localCenter";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_INSTAGRAM = "instagram";
    public static final String FIELD_FACEBOOK = "facebook";
    public static final String FIELD_TIKTOK = "tiktok";
    public static final String FIELD_DISCORD = "discord";

    // All info fields, in the order they are shown
    public static final String[] INFO_FIELDS = {
            FIELD_REAL_NAME, FIELD_LOCALITY, FIELD_LOCAL_CENTER, FIELD_DESCRIPTION,
            FIELD_INSTAGRAM, FIELD_FACEBOOK, FIELD_TIKTOK, FIELD_DISCORD
    };

    private static final String PREFS = "userProfiles";
    private static final String PREFS_KEY = "userInfoJson";

    // userId -> (field -> value)
    private static final Map<String, Map<String, String>> profiles = new HashMap<>();

    public static synchronized void init(Context context) {
        profiles.clear();
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFS_KEY, null);
        if (json == null) return;
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> userIds = root.keys();
            while (userIds.hasNext()) {
                String userId = userIds.next();
                JSONObject fields = root.getJSONObject(userId);
                Map<String, String> map = new HashMap<>();
                Iterator<String> keys = fields.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, fields.optString(key, ""));
                }
                profiles.put(userId, map);
            }
        } catch (JSONException e) {
            Log.e("UserProfiles", "Failed to read cached profiles", e);
        }
    }

    public static synchronized void replaceAll(Context context, Map<String, Map<String, String>> all) {
        profiles.clear();
        profiles.putAll(all);
        save(context);
    }

    // Sets (or removes, when value is null/empty) one field of one user
    public static synchronized void setField(Context context, String userId, String field, String value) {
        Map<String, String> fields = profiles.get(userId);
        if (fields == null) {
            fields = new HashMap<>();
            profiles.put(userId, fields);
        }
        if (value == null || value.isEmpty()) fields.remove(field);
        else fields.put(field, value);
        save(context);
    }

    public static synchronized String get(String userId, String field) {
        if (userId == null) return "";
        Map<String, String> fields = profiles.get(userId);
        if (fields == null) return "";
        String value = fields.get(field);
        return value != null ? value : "";
    }

    public static String photoUrl(String userId) {
        return get(userId, FIELD_PHOTO_URL);
    }

    // Changes every time the photo is replaced, so cached images are refreshed
    public static String photoVersion(String userId) {
        return get(userId, FIELD_PHOTO_VERSION);
    }

    // Label shown for each info field (in the profile panel and in Edit profile)
    public static String infoLabel(String field) {
        switch (field) {
            case FIELD_REAL_NAME: return "Nume";
            case FIELD_LOCALITY: return "Localitate";
            case FIELD_LOCAL_CENTER: return "Centru local";
            case FIELD_DESCRIPTION: return "Descriere";
            case FIELD_INSTAGRAM: return "Instagram";
            case FIELD_FACEBOOK: return "Facebook";
            case FIELD_TIKTOK: return "TikTok";
            case FIELD_DISCORD: return "Discord";
            default: return field;
        }
    }

    // A social account can be typed as a username (with or without @) or pasted as a link.
    // Returns the link to open, or null for Discord (it has no public profile page by username).
    public static String socialLink(String field, String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        String handle = trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
        switch (field) {
            case FIELD_INSTAGRAM: return "https://www.instagram.com/" + handle;
            case FIELD_FACEBOOK: return "https://www.facebook.com/" + handle;
            case FIELD_TIKTOK: return "https://www.tiktok.com/@" + handle;
            default: return null;
        }
    }

    // Text shown for a social account: a pasted link is shortened to the network's name
    private static String socialText(String field, String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return "open profile";
        if (field.equals(FIELD_DISCORD) || field.equals(FIELD_FACEBOOK)) return trimmed;
        return trimmed.startsWith("@") ? trimmed : "@" + trimmed;
    }

    private static boolean isSocial(String field) {
        return field.equals(FIELD_INSTAGRAM) || field.equals(FIELD_FACEBOOK)
                || field.equals(FIELD_TIKTOK) || field.equals(FIELD_DISCORD);
    }

    // Fills the "User Info" section of the profile panel with the fields that were filled in.
    // Social accounts open in the browser / app; Discord is copied to the clipboard.
    // Returns false if the user has no info at all.
    public static boolean fillInfo(android.widget.LinearLayout container, String userId) {
        Context context = container.getContext();
        container.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int linkColor = com.google.android.material.color.MaterialColors.getColor(
                container, com.google.android.material.R.attr.colorPrimary);
        boolean any = false;
        boolean socialStarted = false;

        for (String field : INFO_FIELDS) {
            String value = get(userId, field).trim();
            if (value.isEmpty()) continue;

            // A small gap before the description and before the social accounts (groups)
            boolean isDescription = field.equals(FIELD_DESCRIPTION);
            boolean firstSocial = isSocial(field) && !socialStarted;
            if (isSocial(field)) socialStarted = true;
            int gap = any && (isDescription || firstSocial) ? (int) (10 * density) : 0;
            any = true;

            android.widget.TextView row = new android.widget.TextView(context);
            row.setTextSize(15);
            row.setPadding(0, (int) (3 * density) + gap, 0, (int) (3 * density));
            // The description is shown as plain text, without "Descriere:"
            String label = isDescription ? "" : infoLabel(field) + ": ";
            String shown = isSocial(field) ? socialText(field, value) : value;
            android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(label + shown);
            text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (isSocial(field)) {
                text.setSpan(new android.text.style.ForegroundColorSpan(linkColor),
                        label.length(), text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                String link = socialLink(field, value);
                row.setBackgroundResource(android.R.drawable.list_selector_background);
                row.setOnClickListener(v -> {
                    if (link != null) {
                        try {
                            context.startActivity(new android.content.Intent(
                                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)));
                        } catch (Exception e) {
                            android.widget.Toast.makeText(context, "Could not open the link", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Discord", value));
                        android.widget.Toast.makeText(context, "Discord username copied", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
            row.setText(text);
            container.addView(row);
        }

        if (!any) {
            android.widget.TextView empty = new android.widget.TextView(context);
            empty.setText("Nu s-au adăugat informații.");
            empty.setTextSize(14);
            empty.setAlpha(0.6f);
            empty.setTypeface(null, android.graphics.Typeface.ITALIC);
            container.addView(empty);
        }
        return any;
    }

    // Slight rounding of the profile photo's corners (posts and comments)
    private static final float PHOTO_CORNER_DP = 3f;

    // Shows the user's photo (cropped, slightly rounded corners) or hides the view if they have none.
    // Returns true if there is a photo.
    public static boolean loadPhoto(android.widget.ImageView view, String userId) {
        String url = userId != null ? photoUrl(userId) : "";
        if (url.isEmpty()) {
            com.bumptech.glide.Glide.with(view).clear(view);
            view.setVisibility(android.view.View.GONE);
            return false;
        }
        view.setVisibility(android.view.View.VISIBLE);
        int radius = Math.round(PHOTO_CORNER_DP * view.getResources().getDisplayMetrics().density);
        com.bumptech.glide.Glide.with(view)
                .load(url)
                .signature(new com.bumptech.glide.signature.ObjectKey(photoVersion(userId)))
                .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                        new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                .into(view);
        return true;
    }

    // The photo large, over a dark background; tap anywhere (or Back) to close it
    public static void showPhotoLarge(Context context, String userId) {
        String url = photoUrl(userId);
        if (url.isEmpty()) return;

        android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xE6000000);
        image.setContentDescription("Profile photo");
        image.setOnClickListener(v -> dialog.dismiss());
        PinchZoom.attach(image);
        dialog.setContentView(image);
        com.bumptech.glide.Glide.with(context)
                .load(url)
                .signature(new com.bumptech.glide.signature.ObjectKey(photoVersion(userId)))
                .into(image);
        dialog.show();
    }

    private static void save(Context context) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Map<String, String>> entry : profiles.entrySet()) {
                JSONObject fields = new JSONObject();
                for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                    fields.put(field.getKey(), field.getValue());
                }
                root.put(entry.getKey(), fields);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(PREFS_KEY, root.toString()).apply();
        } catch (JSONException e) {
            Log.e("UserProfiles", "Failed to save profiles", e);
        }
    }

    // Same rules as when creating an account (CreateUser)
    public static String validateUsername(String username) {
        if (username.isEmpty()) return "Username is empty";
        if (username.length() > 15) return "Username is " + (username.length() - 15) + " characters too long";
        if (username.length() < 5) return "Username is " + (5 - username.length()) + " characters too short";
        if (!username.matches("[A-Za-z0-9_.]+")) return "Only letters, numbers, underscore and dot";
        return null;
    }
}
