package com.suici.roverhood.utils;

import android.content.Intent;

// Where the feed should go when a notification is tapped
public class NotificationTarget {

    // Notification types sent by the Cloud Functions
    public static final String TYPE_ANNOUNCEMENT = "announcement";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_POST_LIKES = "postLikes";
    public static final String TYPE_COMMENT_LIKES = "commentLikes";
    public static final String TYPE_TOPIC_POST = "topicPost";
    public static final String TYPE_NEW_POST = "newPost";   // any new post, for moderators and admins
    public static final String TYPE_REPORT_POST = "reportPost";       // reported post, for moderators and admins
    public static final String TYPE_REPORT_COMMENT = "reportComment"; // reported comment, same

    private static final String EXTRA_TYPE = "com.suici.roverhood.EXTRA_NOTIFICATION_TYPE";
    private static final String EXTRA_POST_ID = "com.suici.roverhood.EXTRA_POST_ID";
    private static final String EXTRA_COMMENT_ID = "com.suici.roverhood.EXTRA_COMMENT_ID";
    private static final String EXTRA_TOPIC_TITLE = "com.suici.roverhood.EXTRA_TOPIC_TITLE";
    private static final String EXTRA_USERNAME = "com.suici.roverhood.EXTRA_USERNAME";

    public final String type;
    public final String postId;
    public final String commentId;   // for comment notifications: the comment to scroll to
    public final String topicTitle;  // for topic posts: the filter to open
    public final String username;    // for reported posts: the author, whose profile opens

    public NotificationTarget(String type, String postId, String commentId, String topicTitle) {
        this(type, postId, commentId, topicTitle, null);
    }

    public NotificationTarget(String type, String postId, String commentId, String topicTitle, String username) {
        this.type = type != null ? type : TYPE_ANNOUNCEMENT;
        this.postId = postId;
        this.commentId = commentId;
        this.topicTitle = topicTitle;
        this.username = username;
    }

    public boolean opensComments() {
        return TYPE_COMMENT.equals(type) || TYPE_COMMENT_LIKES.equals(type) || TYPE_REPORT_COMMENT.equals(type);
    }

    public void writeTo(Intent intent) {
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_POST_ID, postId);
        if (commentId != null) intent.putExtra(EXTRA_COMMENT_ID, commentId);
        if (topicTitle != null) intent.putExtra(EXTRA_TOPIC_TITLE, topicTitle);
        if (username != null) intent.putExtra(EXTRA_USERNAME, username);
    }

    // Reads the target from a notification tap and removes it from the intent,
    // so it isn't handled again (e.g. after a theme change restart)
    public static NotificationTarget takeFrom(Intent intent) {
        if (intent == null) return null;
        String postId = intent.getStringExtra(EXTRA_POST_ID);
        if (postId == null) return null;

        NotificationTarget target = new NotificationTarget(
                intent.getStringExtra(EXTRA_TYPE),
                postId,
                intent.getStringExtra(EXTRA_COMMENT_ID),
                intent.getStringExtra(EXTRA_TOPIC_TITLE),
                intent.getStringExtra(EXTRA_USERNAME));

        intent.removeExtra(EXTRA_TYPE);
        intent.removeExtra(EXTRA_POST_ID);
        intent.removeExtra(EXTRA_COMMENT_ID);
        intent.removeExtra(EXTRA_TOPIC_TITLE);
        intent.removeExtra(EXTRA_USERNAME);
        return target;
    }
}
