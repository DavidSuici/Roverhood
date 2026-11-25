package com.suici.roverhood.dialogs;

import android.content.Context;
import android.graphics.Color;

import com.google.android.material.color.MaterialColors;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Shows who liked a post or a comment. Usernames come from the local users table
// (synced at login), so it also works offline.
// Tapping a name filters the feed by that person, tapping the team filters by the team.
public class LikesList {

    // feed: where to apply the filters (null = names not clickable)
    // onFiltered: runs after a filter was applied, e.g. to close the comments dialog
    public static void show(Context context, Map<String, Boolean> likedBy,
                            @Nullable RoverFeed feed, @Nullable Runnable onFiltered) {
        Map<String, User> users = LocalDatabase.getInstance(context).getAllUsers();

        List<User> likers = new ArrayList<>();
        int unknownCount = 0;
        if (likedBy != null) {
            for (String userId : likedBy.keySet()) {
                User user = users.get(userId);
                if (user != null) likers.add(user);
                else unknownCount++;
            }
        }
        likers.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
        int total = likers.size() + unknownCount;

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(total == 0 ? "No likes yet" : "Liked by (" + total + ")")
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        if (total == 0) {
            builder.show();
            return;
        }

        float density = context.getResources().getDisplayMetrics().density;
        int sidePadding = (int) (24 * density);
        int rowPadding = (int) (12 * density);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(sidePadding, 0, sidePadding, 0);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(list);
        builder.setView(scrollView);
        AlertDialog dialog = builder.create();

        for (User user : likers) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Profile photo (only if they have one), rounded corners like everywhere else
            android.widget.ImageView photo = new android.widget.ImageView(context);
            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams((int) (32 * density), (int) (32 * density));
            photoParams.setMarginEnd((int) (10 * density));
            photo.setLayoutParams(photoParams);
            photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription("Profile photo");
            row.addView(photo);
            com.suici.roverhood.utils.UserProfiles.loadPhoto(photo, user.getId());

            TextView name = new TextView(context);
            name.setText(user.getUsername());
            name.setTextSize(17);
            name.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            name.setPadding(0, rowPadding, (int) (12 * density), rowPadding);
            row.addView(name);

            // Team name is smaller and faded, so it isn't mistaken for part of the username
            TextView team = new TextView(context);
            team.setText(Teams.label(user.getTeam()));
            team.setTextSize(14);
            team.setAlpha(0.6f);
            team.setPadding(0, rowPadding, 0, rowPadding);
            row.addView(team);

            if (feed != null) {
                name.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onFiltered != null) onFiltered.run();
                    feed.filterByUser(user.getUsername());
                });
                photo.setOnClickListener(v -> name.performClick());
                team.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onFiltered != null) onFiltered.run();
                    feed.filterByTeam(user.getTeam());
                });
                name.setBackgroundResource(selectableBackground(context));
                team.setBackgroundResource(selectableBackground(context));
            }
            list.addView(row);
        }

        for (int i = 0; i < unknownCount; i++) {
            TextView unknown = new TextView(context);
            unknown.setText("Unknown user");
            unknown.setTextSize(17);
            unknown.setAlpha(0.6f);
            unknown.setPadding(0, rowPadding, 0, rowPadding);
            list.addView(unknown);
        }

        dialog.show();
    }

    private static int selectableBackground(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }
}
