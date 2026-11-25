// Roverhood notifications (Cloud Functions, 2nd gen, europe-west1 like the database).
//
// All messages are data-only: the Android app (RoverhoodMessagingService) builds the
// notification itself, so it can skip your own announcements and open the right screen.
//
// - notifyAnnouncement:  new announcement      -> topic "announcements" (everyone)
// - notifyTopicPost:     new post on a topic    -> creator of the topic (first announcement on it)
// - notifyComment:       new comment            -> author of the post
// - notifyPostLikes:     post likes 5, 10, 15.. -> author of the post
// - notifyCommentLikes:  comment likes 5, 10..  -> author of the comment
// - notifyNewPost:       any new post           -> every MODERATOR and ADMIN (not the author)
// - notifyReport:        reported post/comment  -> every ORGANIZER, MODERATOR and ADMIN (not the reporter)
//
// Personal notifications go to the topic "user_<userId>", which the app subscribes to at login.
// Like milestones are remembered in /notified so likes going 5 -> 4 -> 5 notify only once.

const { onValueCreated, onValueWritten } = require("firebase-functions/v2/database");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");

const DATABASE_URL = "https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app";
const LIKES_STEP = 5;

initializeApp({ databaseURL: DATABASE_URL });

const trigger = (ref) => ({
  ref,
  instance: "roverhoodapp-default-rtdb",
  region: "europe-west1", // must match the database location
});

// FCM data messages are limited to 4 KB; the full text is in the app anyway
function excerpt(text, max = 300) {
  const value = String(text || "");
  return value.length > max ? value.slice(0, max - 3) + "..." : value;
}

// Must match Notifications.userTopic() in the app
function userTopic(userId) {
  return "user_" + String(userId).replace(/[^a-zA-Z0-9\-_.~%]/g, "_");
}

async function getUsername(userId) {
  if (!userId) return "";
  const snap = await getDatabase().ref(`users/${userId}/username`).get();
  return snap.exists() ? String(snap.val()) : "";
}

async function getTopicTitle(topicId) {
  if (!topicId) return "";
  const snap = await getDatabase().ref(`topics/${topicId}/title`).get();
  return snap.exists() ? String(snap.val()) : "";
}

async function sendToUser(userId, data) {
  // FCM data values must all be strings
  const payload = { targetUserId: String(userId) };
  for (const [key, value] of Object.entries(data)) payload[key] = String(value ?? "");

  await getMessaging().send({
    topic: userTopic(userId),
    data: payload,
    android: { priority: "high" },
  });
}

// Returns true only the first time a milestone is reached (5, 10, ...), using a transaction
async function claimMilestone(path, count) {
  if (count <= 0 || count % LIKES_STEP !== 0) return false;
  const result = await getDatabase().ref(path).transaction((current) => {
    if (current !== null && current >= count) return; // already notified, abort
    return count;
  });
  return result.committed;
}

exports.notifyAnnouncement = onValueCreated(trigger("/posts/{postId}"), async (event) => {
  const post = event.data.val();
  if (!post || post.announcement !== true) return;

  const title = (await getTopicTitle(post.topicId)) || "New announcement";
  const author = await getUsername(post.userId);

  await getMessaging().send({
    topic: "announcements",
    data: {
      type: "announcement",
      postId: event.params.postId,
      authorId: String(post.userId || ""),
      author,
      title,
      body: excerpt(post.description),
    },
    android: { priority: "high" },
  });

  logger.info(`Announcement notification sent for post ${event.params.postId}`);
});

exports.notifyTopicPost = onValueCreated(trigger("/posts/{postId}"), async (event) => {
  const post = event.data.val();
  const postId = event.params.postId;
  if (!post || !post.topicId || !post.userId) return;

  // The topic "belongs" to the author of the first announcement that used it
  const postsSnap = await getDatabase().ref("posts").get();
  let owner = null;
  postsSnap.forEach((child) => {
    const other = child.val();
    if (child.key === postId || !other || other.topicId !== post.topicId || other.announcement !== true) return;
    if (!owner || (other.date || 0) < (owner.date || 0)) owner = other;
  });

  if (!owner || !owner.userId || owner.userId === post.userId) return;

  // Moderators and admins already get every new post (notifyNewPost) and every announcement,
  // so they don't also get "new post on your topic"
  const ownerTypeSnap = await getDatabase().ref(`users/${owner.userId}/userType`).get();
  const ownerType = ownerTypeSnap.exists() ? String(ownerTypeSnap.val()) : "";
  if (ownerType === "MODERATOR" || ownerType === "ADMIN") return;

  const topicTitle = await getTopicTitle(post.topicId);
  const author = await getUsername(post.userId);

  await sendToUser(owner.userId, {
    type: "topicPost",
    postId,
    topicTitle,
    title: `📌 New post on your topic: ${topicTitle}`,
    body: `${author}: ${excerpt(post.description)}`,
  });

  logger.info(`Topic post notification sent to ${owner.userId} for post ${postId}`);
});

exports.notifyComment = onValueCreated(trigger("/comments/{postId}/{commentId}"), async (event) => {
  const comment = event.data.val();
  const { postId, commentId } = event.params;
  if (!comment || !comment.userId) return;

  const postAuthorSnap = await getDatabase().ref(`posts/${postId}/userId`).get();
  const postAuthorId = postAuthorSnap.exists() ? String(postAuthorSnap.val()) : null;
  if (!postAuthorId || postAuthorId === comment.userId) return;

  const author = await getUsername(comment.userId);

  await sendToUser(postAuthorId, {
    type: "comment",
    postId,
    commentId,
    title: "💬 New comment on your post",
    body: `${author}: ${excerpt(comment.text)}`,
  });

  logger.info(`Comment notification sent to ${postAuthorId} for post ${postId}`);
});

exports.notifyPostLikes = onValueWritten(trigger("/posts/{postId}/likes"), async (event) => {
  const before = Number(event.data.before.val()) || 0;
  const after = Number(event.data.after.val()) || 0;
  const { postId } = event.params;
  if (after <= before) return;

  if (!(await claimMilestone(`notified/postLikes/${postId}`, after))) return;

  const postSnap = await getDatabase().ref(`posts/${postId}`).get();
  const post = postSnap.val();
  if (!post || !post.userId) return;

  await sendToUser(post.userId, {
    type: "postLikes",
    postId,
    title: `❤️ Your post reached ${after} likes`,
    body: excerpt(post.description, 150),
  });

  logger.info(`Post likes notification (${after}) sent to ${post.userId} for post ${postId}`);
});

exports.notifyCommentLikes = onValueWritten(trigger("/comments/{postId}/{commentId}/likedBy"), async (event) => {
  const before = event.data.before.exists() ? event.data.before.numChildren() : 0;
  const after = event.data.after.exists() ? event.data.after.numChildren() : 0;
  const { postId, commentId } = event.params;
  if (after <= before) return;

  if (!(await claimMilestone(`notified/commentLikes/${postId}_${commentId}`, after))) return;

  const commentSnap = await getDatabase().ref(`comments/${postId}/${commentId}`).get();
  const comment = commentSnap.val();
  if (!comment || !comment.userId) return;

  await sendToUser(comment.userId, {
    type: "commentLikes",
    postId,
    commentId,
    title: `❤️ Your comment reached ${after} likes`,
    body: excerpt(comment.text, 150),
  });

  logger.info(`Comment likes notification (${after}) sent to ${comment.userId} for comment ${commentId}`);
});

// Every new (non-announcement) post -> every MODERATOR and ADMIN, except its author.
// Announcements are skipped: everyone already gets those through notifyAnnouncement.
// The role is read at send time, so a role change applies immediately.
exports.notifyNewPost = onValueCreated(trigger("/posts/{postId}"), async (event) => {
  const post = event.data.val();
  const postId = event.params.postId;
  if (!post || !post.userId || post.announcement === true) return;

  const usersSnap = await getDatabase().ref("users").get();
  const recipients = [];
  usersSnap.forEach((child) => {
    const user = child.val();
    if (!user || !user.username || child.key === post.userId) return;
    if (user.userType === "MODERATOR" || user.userType === "ADMIN") recipients.push(child.key);
  });
  if (recipients.length === 0) return;

  const author = await getUsername(post.userId);
  const topicTitle = await getTopicTitle(post.topicId);
  const title = topicTitle ? `📝 New post: ${topicTitle}` : "📝 New post";

  await Promise.all(recipients.map((userId) => sendToUser(userId, {
    type: "newPost",
    postId,
    title,
    body: `${author}: ${excerpt(post.description)}`,
  })));

  logger.info(`New post notification sent to ${recipients.length} moderators/admins for post ${postId}`);
});

// A post or comment was reported (/reports/{reporterId}_{itemId}, written by the app)
// -> every MODERATOR and ADMIN except the reporter. Tapping opens the author's profile with
// the post highlighted (posts) or the comments scrolled to the comment (comments).
exports.notifyReport = onValueCreated(trigger("/reports/{reportId}"), async (event) => {
  const report = event.data.val();
  if (!report || !report.postId || !report.reporterId) return;
  const isComment = !!report.commentId;

  const usersSnap = await getDatabase().ref("users").get();
  const recipients = [];
  usersSnap.forEach((child) => {
    const user = child.val();
    if (!user || !user.username || child.key === report.reporterId) return;
    if (user.userType === "ORGANIZER" || user.userType === "MODERATOR" || user.userType === "ADMIN") recipients.push(child.key);
  });
  if (recipients.length === 0) return;

  const path = isComment
    ? `comments/${report.postId}/${report.commentId}/text`
    : `posts/${report.postId}/description`;
  const textSnap = await getDatabase().ref(path).get();
  const text = textSnap.exists() ? String(textSnap.val()) : "";

  const reporter = await getUsername(report.reporterId);
  const reported = await getUsername(report.reportedUserId);
  const reason = report.reason ? ` (${excerpt(report.reason, 100)})` : "";

  await Promise.all(recipients.map((userId) => sendToUser(userId, {
    type: isComment ? "reportComment" : "reportPost",
    postId: report.postId,
    commentId: report.commentId || "",
    username: reported,
    title: isComment ? `🚩 Comment by ${reported} reported` : `🚩 Post by ${reported} reported`,
    body: `${reporter}${reason}: ${excerpt(text, 200)}`,
  })));

  logger.info(`Report notification sent to ${recipients.length} staff for ${event.params.reportId}`);
});
