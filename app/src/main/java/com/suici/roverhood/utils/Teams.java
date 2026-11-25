package com.suici.roverhood.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// Display name and emoji of each team (patrol).
// Users keep their team key (e.g. "Topaz") in the database; admins can change how a team is
// shown (name and emoji), stored in Firebase at teams/{key} = { name, emoji }.
// A copy is kept in SharedPreferences so names and emojis also show offline.
public class Teams {

    private static final String PREFS = "teams";
    private static final String PREFS_KEY = "teamsJson";

    private static final Map<String, String> DEFAULT_EMOJIS = new HashMap<>();
    static {
        DEFAULT_EMOJIS.put("topaz", "⚜️");
        DEFAULT_EMOJIS.put("emerald", "🍏");
        DEFAULT_EMOJIS.put("sapphire", "💙");
    }

    // key -> { name, emoji } as edited by admins
    private static final Map<String, String[]> custom = new HashMap<>();

    public static synchronized void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(PREFS_KEY, null);
        custom.clear();
        if (json == null) return;
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject team = root.getJSONObject(key);
                custom.put(key, new String[]{team.optString("name", ""), team.optString("emoji", "")});
            }
        } catch (JSONException e) {
            Log.e("Teams", "Failed to read cached teams", e);
        }
    }

    // Replaces all custom names/emojis with the ones from Firebase
    public static synchronized void replaceAll(Context context, Map<String, String[]> teams) {
        custom.clear();
        custom.putAll(teams);
        save(context);
    }

    public static synchronized void set(Context context, String key, String name, String emoji) {
        custom.put(key, new String[]{name, emoji});
        save(context);
    }

    public static synchronized void remove(Context context, String key) {
        custom.remove(key);
        save(context);
    }

    private static void save(Context context) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, String[]> entry : custom.entrySet()) {
                JSONObject team = new JSONObject();
                team.put("name", entry.getValue()[0]);
                team.put("emoji", entry.getValue()[1]);
                root.put(entry.getKey(), team);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(PREFS_KEY, root.toString()).apply();
        } catch (JSONException e) {
            Log.e("Teams", "Failed to save teams", e);
        }
    }

    public static synchronized String name(String key) {
        if (key == null) return "";
        String[] team = custom.get(key);
        if (team != null && !team[0].isEmpty()) return team[0];
        return key;
    }

    public static synchronized String emoji(String key) {
        if (key == null) return "";
        String[] team = custom.get(key);
        if (team != null && !team[1].isEmpty()) return team[1];
        String fallback = DEFAULT_EMOJIS.get(key.toLowerCase());
        return fallback != null ? fallback : "";
    }

    // All team keys known on this phone: teams of synced users plus teams edited by admins
    public static synchronized java.util.List<String> allKeys(Context context) {
        java.util.TreeSet<String> keys = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (com.suici.roverhood.models.User user :
                com.suici.roverhood.databases.LocalDatabase.getInstance(context).getAllUsers().values()) {
            if (user.getTeam() != null && !user.getTeam().isEmpty()) keys.add(user.getTeam());
        }
        keys.addAll(custom.keySet());
        return new java.util.ArrayList<>(keys);
    }

    // A team key is used as a key in Firebase (teams/{key}), so no . # $ [ ] /
    public static String validateTeamKey(String team) {
        if (team.length() < 2) return "Team name is too short";
        if (team.length() > 20) return "Team name is too long";
        if (!team.matches("[\\p{L}0-9 _-]+")) return "Only letters, numbers, spaces, - and _";
        return null;
    }

    // True if the text is exactly one emoji. One emoji can be several code points (skin tones,
    // flags, ZWJ sequences like families), so it counts user-perceived characters (grapheme
    // clusters) and checks that the cluster is made of emoji symbols and their modifiers only.
    public static boolean isSingleEmoji(String text) {
        if (text == null || text.isEmpty()) return false;

        android.icu.text.BreakIterator iterator = android.icu.text.BreakIterator.getCharacterInstance();
        iterator.setText(text);
        int first = iterator.first();
        int end = iterator.next();
        if (end == android.icu.text.BreakIterator.DONE || iterator.next() != android.icu.text.BreakIterator.DONE) {
            return false; // empty or more than one character
        }

        int firstCodePoint = text.codePointAt(first);
        if (Character.getType(firstCodePoint) != Character.OTHER_SYMBOL) return false;

        for (int i = first; i < end; ) {
            int codePoint = text.codePointAt(i);
            int type = Character.getType(codePoint);
            boolean allowed = type == Character.OTHER_SYMBOL      // emoji, regional indicators
                    || type == Character.MODIFIER_SYMBOL          // skin tones
                    || type == Character.NON_SPACING_MARK         // variation selector (U+FE0F)
                    || type == Character.ENCLOSING_MARK
                    || type == Character.FORMAT;                  // zero width joiner, tags
            if (!allowed) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    // Team name followed by its emoji, e.g. "Topaz ⚜️"
    public static String label(String key) {
        if (key == null || key.isEmpty()) return "";
        String emoji = emoji(key);
        return emoji.isEmpty() ? name(key) : name(key) + " " + emoji;
    }
}
