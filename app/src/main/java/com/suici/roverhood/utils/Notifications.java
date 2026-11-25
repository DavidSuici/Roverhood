package com.suici.roverhood.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;

// Push notifications. The Cloud Functions (functions/index.js) send data messages:
// - to the topic "announcements" for new announcements (everyone logged in)
// - to the topic "user_<id>" for comments, likes and posts on your topic (only you)
// RoverhoodMessagingService receives them and shows them with show().
public class Notifications {

    public static final String CHANNEL_ANNOUNCEMENTS = "announcements";
    public static final String CHANNEL_ACTIVITY = "activity";
    public static final String TOPIC_ANNOUNCEMENTS = "announcements";

    // Safe to call multiple times; users can turn each channel off from the phone settings
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel announcements = new NotificationChannel(
                CHANNEL_ANNOUNCEMENTS, "Announcements", NotificationManager.IMPORTANCE_HIGH);
        announcements.setDescription("New announcements posted in Roverhood");
        manager.createNotificationChannel(announcements);

        NotificationChannel activity = new NotificationChannel(
                CHANNEL_ACTIVITY, "Activity", NotificationManager.IMPORTANCE_DEFAULT);
        activity.setDescription("Comments and likes on your posts, and new posts on your topics");
        manager.createNotificationChannel(activity);
    }

    // Must match userTopic() in functions/index.js
    public static String userTopic(String userId) {
        return "user_" + userId.replaceAll("[^a-zA-Z0-9\\-_.~%]", "_");
    }

    public static void subscribe(String userId) {
        subscribeToTopic(TOPIC_ANNOUNCEMENTS);
        if (userId != null) subscribeToTopic(userTopic(userId));
    }

    public static void unsubscribe(String userId) {
        unsubscribeFromTopic(TOPIC_ANNOUNCEMENTS);
        if (userId != null) unsubscribeFromTopic(userTopic(userId));
    }

    private static void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener(task -> Log.d("Notifications",
                        "Subscribe to " + topic + ": " + (task.isSuccessful() ? "ok" : "failed")));
    }

    private static void unsubscribeFromTopic(String topic) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener(task -> Log.d("Notifications",
                        "Unsubscribe from " + topic + ": " + (task.isSuccessful() ? "ok" : "failed")));
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Small icon in the status bar / notification, by type (Android draws it as a white silhouette)
    private static int iconFor(String type) {
        switch (type) {
            case NotificationTarget.TYPE_POST_LIKES:
            case NotificationTarget.TYPE_COMMENT_LIKES:
                return R.drawable.heart_fill;
            case NotificationTarget.TYPE_COMMENT:
                return R.drawable.icon_comments;
            case NotificationTarget.TYPE_REPORT_POST:
            case NotificationTarget.TYPE_REPORT_COMMENT:
                return R.drawable.icon_report;
            case NotificationTarget.TYPE_TOPIC_POST:
            case NotificationTarget.TYPE_NEW_POST:
                return R.drawable.icon_post_add;
            default:
                return R.drawable.icon_announcements;
        }
    }

    public static void show(Context context, String channel, NotificationTarget target, String title, String body) {
        if (!hasPermission(context)) return;
        createChannels(context);

        // Tapping opens the feed on the target (filter, post, comments).
        // SINGLE_TOP reuses the running app (onNewIntent); otherwise it starts and re-logs the user.
        // A request code per notification keeps each one's extras separate.
        String key = target.type + ":" + target.postId + ":" + target.commentId;
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        target.writeTo(intent);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean isAnnouncement = CHANNEL_ANNOUNCEMENTS.equals(channel);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(iconFor(target.type))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(isAnnouncement ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), builder.build());
        } catch (SecurityException e) {
            Log.e("Notifications", "Notification permission missing", e);
        }
    }
}
