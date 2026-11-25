package com.suici.roverhood.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.models.User;

import java.util.Map;

// Receives the data messages sent by the Cloud Functions (functions/index.js).
// Data-only messages always arrive here (app in foreground, background or closed),
// so the app decides what to show: nothing when nobody is logged in on this phone,
// no notification for your own announcements, and personal notifications only for
// the user they were meant for.
public class RoverhoodMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String type = data.get("type");
        String postId = data.get("postId");
        if (type == null || postId == null) return;

        User loggedInUser = LocalDatabase.getInstance(getApplicationContext()).getLoggedInUser();
        if (loggedInUser == null || loggedInUser.getId() == null) return;

        if (NotificationTarget.TYPE_ANNOUNCEMENT.equals(type)) {
            showAnnouncement(data, loggedInUser, postId);
            return;
        }

        // Personal notification: only for the user it was sent to
        if (!loggedInUser.getId().equals(data.get("targetUserId"))) return;

        NotificationTarget target = new NotificationTarget(type, postId, data.get("commentId"), data.get("topicTitle"), data.get("username"));
        String title = data.get("title");
        String body = data.get("body");
        Notifications.show(getApplicationContext(), Notifications.CHANNEL_ACTIVITY, target,
                title != null ? title : "Roverhood", body != null ? body : "");
    }

    private void showAnnouncement(Map<String, String> data, User loggedInUser, String postId) {
        if (loggedInUser.getId().equals(data.get("authorId"))) return;

        // The server sends the topic title, or "New announcement" when the post has no topic
        String topic = data.get("title");
        String author = data.get("author");
        String body = data.get("body");

        String title = (topic == null || topic.isEmpty() || topic.equals("New announcement"))
                ? "📢 New announcement"
                : "📢 Announcement: " + topic;
        if (body == null) body = "";
        if (author != null && !author.isEmpty()) body = author + ": " + body;

        NotificationTarget target = new NotificationTarget(NotificationTarget.TYPE_ANNOUNCEMENT, postId, null, null);
        Notifications.show(getApplicationContext(), Notifications.CHANNEL_ANNOUNCEMENTS, target, title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Topics are tied to the token; re-subscribe so a new token keeps receiving notifications
        Log.d("RoverhoodMessaging", "New FCM token");
        User loggedInUser = LocalDatabase.getInstance(getApplicationContext()).getLoggedInUser();
        if (loggedInUser != null) Notifications.subscribe(loggedInUser.getId());
    }
}
