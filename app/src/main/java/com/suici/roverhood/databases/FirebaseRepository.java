package com.suici.roverhood.databases;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.models.Comment;
import com.suici.roverhood.models.Topic;
import com.suici.roverhood.models.User;
import com.suici.roverhood.models.Post;
import com.suici.roverhood.utils.NetworkStatus;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;
import android.graphics.Bitmap;
import com.suici.roverhood.utils.image.ImageDownload;
import com.suici.roverhood.presentation.ProgressBar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class FirebaseRepository {

    // Singleton instance
    private static FirebaseRepository instance;

    private static final String DATABASE_URL = "https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app";

    private final DatabaseReference postsRef;
    private final DatabaseReference topicsRef;
    private final DatabaseReference usersRef;
    private final DatabaseReference deletedPostsRef;
    private final DatabaseReference commentsRef;
    private final DatabaseReference teamsRef;
    private final DatabaseReference userInfoRef;
    private final DatabaseReference rootRef;
    private final LocalDatabase localDatabase;
    private Context context;

    private boolean loading = false;
    private int loadGeneration = 0;
    private boolean topicsLoaded = false;


    // Callback Interfaces

    public interface PostRepositoryCallback {
        void onPostsLoaded(List<PostHandler> postHandlers, boolean isOffline);
        void onError(String errorMessage);
    }

    public interface PostOperationCallback  {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface PostCreationCallback {
        void onPostCreated(PostHandler postHandler);
        void onError(String errorMessage);
    }

    public interface TopicCreationCallback {
        void onTopicCreated(Topic topic);
        void onError(String errorMessage);
    }

    public interface UsersCallback {
        void onUsersFetched(Map<String, User> users);
        void onError(String errorMessage);
    }

    public interface CommentsCallback {
        void onCommentsLoaded(List<Comment> comments, boolean isOffline);
    }

    public interface CommentCreationCallback {
        void onCommentCreated(Comment comment);
        void onError(String errorMessage);
    }

    public FirebaseRepository(Context context) {
        this.context = context;
        this.postsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("posts");

        this.topicsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("topics");

        this.usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        this.deletedPostsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("deletedPosts");

        this.commentsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("comments");

        this.teamsRef = FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference("teams");

        this.userInfoRef = FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference("userInfo");

        this.rootRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference();

        this.localDatabase = LocalDatabase.getInstance(context);
    }

    public static synchronized FirebaseRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseRepository(context);
        }
        return instance;
    }

    public static synchronized void updateContext(Context newContext) {
        if (instance != null && newContext != null) {
            instance.context = newContext;
        }
    }


    // ONLINE POST MANAGEMENT

    // Loads posts from Firebase or local database depending on connectivity,
    // synchronizes topics and user data, and returns the result via callback.
    // Falls back to offline data if timeout is reached or connection fails.
    public void loadPosts( boolean isOffline, boolean isOfflineUser, PostRepositoryCallback callback) {
        loading = true;
        List<PostHandler> postHandlers = new ArrayList<>();
        boolean[] isTimeoutReached = {false};

        // Each call gets a generation number; results of an older call (e.g. its timeout firing
        // during a newer refresh) are ignored, so they can't switch the feed back to offline
        final int generation = ++loadGeneration;

        // Coming back from offline mode with internet available: Firebase may be waiting in its
        // reconnect backoff (up to ~30s), so force an immediate reconnect and give it the full
        // online time limit. Without internet keep the short limit, so offline data shows fast.
        if (isOffline && !isOfflineUser && NetworkStatus.isOnline(context)) {
            FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
            database.goOffline();
            database.goOnline();
            isOffline = false;
        }
        // No internet at all (e.g. the app opened in airplane mode): no point waiting 10s for
        // the server, show the offline data after the short limit
        int timeLimit = (isOffline || !NetworkStatus.isOnline(context)) ? 2000 : 10000;

        // Offline user without internet: only local data, no online attempt and no timeout
        // (the timeout used to fire later and load the offline feed a second time)
        if (isOfflineUser) {
            loading = false;
            callback.onPostsLoaded(loadOfflinePosts(), true);
            return;
        }

        // Set a timeout of 10s or 2s if already offline
        final Handler timeoutHandler = new Handler();
        timeoutHandler.postDelayed(() -> {
            if (generation != loadGeneration) return;
            loading = false;
            isTimeoutReached[0] = true;
            callback.onPostsLoaded(loadOfflinePosts(), true);
        }, timeLimit);

        // Start fetching topics
        topicsLoaded = false;
        fetchAllTopics();

        // Using thread to sequentially wait for topics to load before fetching posts
        new Thread(() -> {
            int retryCount = 0;
            while (!topicsLoaded && retryCount < timeLimit/100) { // 10 or 2 seconds max
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e("FirebaseRepository", "Wait loop interrupted", e);
                }
                retryCount++;
            }

            // All topics have been loaded, start fetching posts
            postsRef.orderByChild("date").get().addOnSuccessListener(snapshot -> {
                // Too late (already fell back to offline) or replaced by a newer refresh
                if (isTimeoutReached[0] || generation != loadGeneration) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    return;
                }
                timeoutHandler.removeCallbacksAndMessages(null);

                removeDeletedPostsFromLocalDB();
                syncAllCommentsToLocalDB();
                localDatabase.refreshTopics(Topic.getAllTopics());

                List<DataSnapshot> snapshots = new ArrayList<>();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    snapshots.add(postSnap);
                }

                final int totalPosts = snapshots.size();
                int[] loadedCount = {0};

                for (DataSnapshot postSnap : snapshots) {
                    String postId = postSnap.getKey();
                    Long date = postSnap.child("date").getValue(Long.class);
                    String description = postSnap.child("description").getValue(String.class);
                    String imageUrl = postSnap.child("imageUrl").getValue(String.class);
                    Integer likes = postSnap.child("likes").getValue(Integer.class);
                    Map<String, Boolean> likedByMap = (Map<String, Boolean>) postSnap.child("likedBy").getValue();
                    Boolean announcement = postSnap.child("announcement").getValue(Boolean.class);
                    String userId = postSnap.child("userId").getValue(String.class);
                    String topicId = postSnap.child("topicId").getValue(String.class);
                    Integer version = postSnap.child("version").getValue(Integer.class);
                    Integer commentCountValue = postSnap.child("commentCount").getValue(Integer.class);
                    int commentCount = commentCountValue != null ? commentCountValue : 0;

                    if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                        loadedCount[0]++;
                        continue;
                    }

                    // Removing outdated posts from local storage, to be downloaded later again
                    if (version == null) {
                        version = 0;
                    }
                    Integer finalVersion = version;

                    Integer localVersion = localDatabase.getPostVersion(postId);
                    if (localVersion != null) {
                        if (!version.equals(localVersion)) {
                            Log.d("FirebaseRepository", "Post " + postId + " is outdated. Removing from Local DB.");
                            localDatabase.deletePost(postId);
                        } else {
                            localDatabase.updatePostCommentCount(postId, commentCount);
                        }
                    }

                    // Fetch the user of this post, and add the post to the list
                    usersRef.child(userId).get().addOnSuccessListener(userSnap -> {
                        User user = userSnap.getValue(User.class);
                        if (user != null) {
                            user.setId(userId);
                            Topic fetchedTopic = null;

                            if (topicId != null) {
                                fetchedTopic = Topic.getAllTopics().stream()
                                        .filter(t -> topicId.equals(t.getId()))
                                        .findFirst()
                                        .orElse(null);
                            }

                            Post post = new Post(postId, user, date, fetchedTopic, description, imageUrl, likes, likedByMap, announcement, finalVersion);
                            post.setCommentCount(commentCount);
                            PostHandler postHandler = new PostHandler(null, post, false);
                            postHandlers.add(postHandler);
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == totalPosts && generation == loadGeneration) {
                            loading = false;
                            callback.onPostsLoaded(postHandlers, false);
                        }
                    }).addOnFailureListener(e -> {
                        loadedCount[0]++;
                        if (loadedCount[0] == totalPosts && generation == loadGeneration) {
                            loading = false;
                            callback.onPostsLoaded(postHandlers, false);
                        }
                    });
                }
            }).addOnFailureListener(e -> {
                timeoutHandler.removeCallbacksAndMessages(null);
                if (isTimeoutReached[0] || generation != loadGeneration) return;
                loading = false;
                callback.onPostsLoaded(loadOfflinePosts(), true);
            });
        }).start();
    }

    public void createPost(String description, User user, String imageUrl, boolean isAnnouncement, Topic topic, Fragment fragment, PostCreationCallback callback) {
        String postId = postsRef.push().getKey();
        if (postId == null) {
            Log.e("FirebaseRepository", "Failed to generate post ID");
            callback.onError("Post creation failed.");
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("date", timestamp);
        postMap.put("description", description);
        postMap.put("imageUrl", imageUrl);
        postMap.put("likes", 0);
        postMap.put("announcement", isAnnouncement);
        postMap.put("userId", user.getId());
        if (topic != null) {
            postMap.put("topicId", topic.getId());
        }

        postsRef.child(postId).setValue(postMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseRepository", "Post successfully saved with ID: " + postId);

                    Post post = new Post(postId, user, timestamp, topic, description, imageUrl, 0, null, isAnnouncement, 0);
                    PostHandler postHandler = new PostHandler(fragment, post, false);

                    new Thread(() -> {
                        saveSinglePostToLocalDB(postHandler);
                    }).start();
                    callback.onPostCreated(postHandler);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to save post in Firebase.", e);
                    callback.onError("Failed to save post.");
                });
    }

    public void deletePost(String postId, String imageUrl, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(postId);
        DatabaseReference deletedPostRef = deletedPostsRef.child(postId);

        deleteImageFromStorage(imageUrl, new FirebaseRepository.PostOperationCallback() {
            @Override
            public void onSuccess() {
                Log.d("FirebaseRepository", "Image deleted successfully");
            }
            @Override
            public void onError(String errorMessage) {
                Log.e("FirebaseRepository", "Error deleting image: " + errorMessage);
            }
        });

        postRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FirebaseRepository", "Post successfully deleted from Firebase.");
                localDatabase.deletePost(postId);
                localDatabase.deleteCommentsOfPost(postId);

                commentsRef.child(postId).removeValue().addOnCompleteListener(task3 -> {
                    if (!task3.isSuccessful()) {
                        Log.e("FirebaseRepository", "Failed to delete comments of post " + postId, task3.getException());
                    }
                });

                deletedPostRef.setValue(true).addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful()) {
                        Log.d("FirebaseRepository", "Post ID added to deletedPosts list.");
                    } else {
                        Log.e("FirebaseRepository", "Failed to add post ID to deletedPosts list.", task2.getException());
                    }
                });

                callback.onSuccess();
            } else {
                callback.onError("Failed to delete post");
            }
        });
    }

    public void deleteImageFromStorage(String imageUrl, PostOperationCallback callback) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e("FirebaseRepository", "Image URL is empty or null, cannot delete.");
            callback.onError("Image URL is invalid.");
            return;
        }

        StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
        imageRef.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FirebaseRepository", "Image successfully deleted from Firebase Storage.");
                callback.onSuccess();
            } else {
                Log.e("FirebaseRepository", "Failed to delete image from Firebase Storage.", task.getException());
                callback.onError("Failed to delete image.");
            }
        });
    }

    // newDate: staff only, the post's new date in seconds (null = unchanged)
    public void updatePost(PostHandler postHandler, String description, String imageUrl, boolean isAnnouncement, Long newDate, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(postHandler.getPost().getId());

        postHandler.getPost().incrementVersion();

        Map<String, Object> updates = new HashMap<>();
        updates.put("description", description);
        updates.put("imageUrl", imageUrl);
        updates.put("announcement", isAnnouncement);
        updates.put("version", postHandler.getPost().getVersion());
        if (newDate != null) updates.put("date", newDate);

        postRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Using thread to sequentially wait for post to be deleted before saving it again
                new Thread(() -> {
                    postHandler.getPost().setDescription(description);
                    postHandler.getPost().setImageUrl(imageUrl);
                    postHandler.getPost().setAnnouncement(isAnnouncement);
                    if (newDate != null) postHandler.getPost().setDate(newDate);

                    localDatabase.deletePost(postHandler.getPost().getId());
                    saveSinglePostToLocalDB(postHandler);
                }).start();

                callback.onSuccess();
                Log.d("FirebaseRepository", "Post successfully updated.");
            } else {
                Log.e("FirebaseRepository", "Failed to update post in Firebase.");
                callback.onError("Failed to update post.");
            }
        });
    }

    // Using Firebase transaction to update safely the number of likes and list of users who liked
    public void toggleLike(String postId, String currentUserId, boolean isChecked, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(postId);

        postRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer currentLikes = currentData.child("likes").getValue(Integer.class);
                if (currentLikes == null) currentLikes = 0;

                Map<String, Boolean> currentLikedBy = (Map<String, Boolean>) currentData.child("likedBy").getValue();
                if (currentLikedBy == null) currentLikedBy = new HashMap<>();

                Boolean isLiked = currentLikedBy.get(currentUserId);

                if (isChecked) {
                    if (isLiked == null || !isLiked) {
                        currentLikes++;
                        currentLikedBy.put(currentUserId, true);
                    }
                } else {
                    if (isLiked != null && isLiked) {
                        currentLikes--;
                        currentLikedBy.remove(currentUserId);
                    }
                }

                currentData.child("likes").setValue(currentLikes);
                currentData.child("likedBy").setValue(currentLikedBy);

                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (committed && currentData != null) {
                    Integer newLikes = currentData.child("likes").getValue(Integer.class);
                    Map<String, Boolean> newLikedBy = (Map<String, Boolean>) currentData.child("likedBy").getValue();

                    if (newLikedBy == null)
                        newLikedBy = new HashMap<>();
                    if (newLikes != null) {
                        localDatabase.updatePostLikes(postId, newLikes, newLikedBy);
                        callback.onSuccess();
                    } else {
                        callback.onError("Failed to fetch updated like data.");
                    }
                } else {
                    if (error != null) {
                        Log.e("FirebaseRepository", "Transaction failed: " + error.getMessage());
                        callback.onError("Transaction failed.");
                    } else {
                        Log.e("FirebaseRepository", "Transaction not committed (possible conflict or error)");
                        callback.onError("Transaction not committed.");
                    }
                }
            }
        });
    }


    // OFFLINE POST MANAGEMENT

    private List<PostHandler> loadOfflinePosts() {
        Topic.clearTopics();
        Topic.getAllTopics().addAll(localDatabase.getAllTopics().values());

        Map<String, PostHandler> offlinePosts = localDatabase.getAllOfflinePosts(null, localDatabase.getAllUsers(), localDatabase.getAllTopics());
        loading = false;
        return new ArrayList<>(offlinePosts.values());
    }

    // Saves posts and their images to local storage.
    // Updates a progress bar on the UI thread as each image is saved.
    public void savePostsToLocalDB(List<PostHandler> postsToSave) {
        // Resetting the progress bar if its done loading from previous tasks
        ((MainActivity) context).runOnUiThread(() -> {
            if (ImageDownload.getLoadedImageCount() >= ImageDownload.getTotalImageCount()) {
                ImageDownload.setLoadedImageCount(0);
                ImageDownload.setTotalImageCount(0);
                ProgressBar.resetProgressBar(((MainActivity) context).getDownloadProgressBar());
            }
        });

        for (PostHandler postHandler : postsToSave) {
            if(localDatabase.isPostChanged(postHandler.getPost().getId(), postHandler.getPost().getVersion())) {
                String fileName = postHandler.getPost().getId();
                String imageUrl = postHandler.getPost().getImageUrl();

                //TO_DO only increment and save the ones that are needed
                ((MainActivity) context).runOnUiThread(() -> {
                    ImageDownload.incrementProgressBarMax(context);
                });

                // Downloads each image, and saves posts with local path instead of URL
                ImageDownload.saveImageToInternalStorage(context, imageUrl, fileName, new ImageDownload.ImageSaveCallback() {
                    @Override
                    public void onSuccess(String imagePath) {
                        Log.d("LocalSync", "Image saved at: " + imagePath);
                        ((MainActivity) context).runOnUiThread(() -> {
                            ImageDownload.incrementProgressBar(context);
                        });

                        Post adjustedPost = new Post(
                                postHandler.getPost().getId(),
                                postHandler.getPost().getUser(),
                                postHandler.getPost().getDate(),
                                postHandler.getPost().getTopic(),
                                postHandler.getPost().getDescription(),
                                imagePath,
                                postHandler.getPost().getLikes(),
                                postHandler.getPost().getLikedBy(),
                                postHandler.getPost().isAnnouncement(),
                                postHandler.getPost().getVersion()
                        );
                        adjustedPost.setCommentCount(postHandler.getPost().getCommentCount());

                        localDatabase.insertPost(adjustedPost);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e("LocalSync", "Failed to save image for post " + postHandler.getPost().getId(), e);
                        ((MainActivity) context).runOnUiThread(() -> {
                            ImageDownload.incrementProgressBar(context);
                        });
                    }
                });
            }
        }
    }

    public void saveSinglePostToLocalDB(PostHandler postHandler) {
        List<PostHandler> singlePostHandlerList = new ArrayList<>();
        singlePostHandlerList.add(postHandler);
        savePostsToLocalDB(singlePostHandlerList);
    }

    private void removeDeletedPostsFromLocalDB() {
        deletedPostsRef.get().addOnSuccessListener(snapshot2 -> {
            if (snapshot2.exists()) {
                for (DataSnapshot postSnapshot : snapshot2.getChildren()) {
                    localDatabase.deletePost(postSnapshot.getKey());
                    localDatabase.deleteCommentsOfPost(postSnapshot.getKey());
                }
            }
        });
    }


    // TOPIC MANAGEMENT

    // Fetches all topics from Firebase on a single background thread.
    // Executed this way to preserve the sequential logic in loadPosts().
    public void fetchAllTopics() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DataSnapshot snapshot = Tasks.await(topicsRef.get());

                Topic.clearTopics();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Topic topic = dataSnapshot.getValue(Topic.class);
                    if (topic != null) {
                        topic.setId(dataSnapshot.getKey());
                        Topic.addTopic(topic);
                    }
                }

                topicsLoaded = true;
                Log.d("FirebaseRepository", "All topics pre-fetched: " + Topic.getAllTopics().size());
            } catch (Exception e) {
                Log.e("FirebaseRepository", "Failed to fetch topics", e);
            }
        });
    }

    public void createTopic(String title, FirebaseRepository.TopicCreationCallback callback) {
        String topicId = topicsRef.push().getKey();
        if (topicId == null) {
            Log.e("FirebaseRepository", "Failed to generate topic ID");
            callback.onError("Topic creation failed.");
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> topicMap = new HashMap<>();
        topicMap.put("title", title);
        topicMap.put("creationTime", timestamp);

        topicsRef.child(topicId).setValue(topicMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseRepository", "Topic successfully created with ID: " + topicId);
                    Topic newTopic = new Topic(topicId, title, timestamp);
                    Topic.addTopic(newTopic);
                    callback.onTopicCreated(newTopic);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Failed to create topic.");
                });
    }

    // Deletes all topics that are not assigned to any existing post.
    // Removes them both from the local database and from Firebase.
    public void deleteUnusedTopics(List<PostHandler> postHandlers) {
        List<String> usedTopicIds = new ArrayList<>();
        for (PostHandler postHandler : postHandlers) {
            if (postHandler.getPost().getTopic() != null) {
                if (!usedTopicIds.contains(postHandler.getPost().getTopic().getId())) {
                    usedTopicIds.add(postHandler.getPost().getTopic().getId());
                }
            }
        }

        List<Topic> allTopics = Topic.getAllTopics();
        List<Topic> unusedTopics = new ArrayList<>();
        for (Topic topic : allTopics) {
            if (!usedTopicIds.contains(topic.getId())) {
                unusedTopics.add(topic);
            }
        }

        for (Topic topic : unusedTopics) {
            localDatabase.removeTopic(topic.getId());
            topicsRef.child(topic.getId()).removeValue();
            Topic.deleteTopic(topic);
        }

        Log.d("removeDeletedTopics", "Finished removing unused topics. Total removed: " + unusedTopics.size());
    }


    // COMMENT MANAGEMENT
    // Structure: comments/{postId}/{commentId} = { userId, text, date, edited }
    // posts/{postId}/commentCount is kept in sync with multi-path updates

    // Loads the comments of a post from Firebase and caches them locally.
    // Falls back to the local copy if offline, on failure or after a 5s timeout.
    public void loadComments(String postId, boolean isOffline, CommentsCallback callback) {
        if (isOffline) {
            callback.onCommentsLoaded(localDatabase.getCommentsOfPost(postId), true);
            return;
        }

        boolean[] finished = {false};
        Handler timeoutHandler = new Handler(android.os.Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (finished[0]) return;
            finished[0] = true;
            callback.onCommentsLoaded(localDatabase.getCommentsOfPost(postId), true);
        }, 5000);

        ensureSignedIn(() -> commentsRef.child(postId).get()
                .addOnSuccessListener(snapshot -> {
                    if (finished[0]) return;
                    finished[0] = true;
                    timeoutHandler.removeCallbacksAndMessages(null);

                    List<Comment> comments = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Comment comment = parseComment(child, postId);
                        if (comment != null) comments.add(comment);
                    }
                    comments.sort((a, b) -> Long.compare(a.getDate(), b.getDate()));

                    localDatabase.replaceCommentsOfPost(postId, comments);
                    localDatabase.updatePostCommentCount(postId, comments.size());
                    callback.onCommentsLoaded(comments, false);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to load comments", e);
                    if (finished[0]) return;
                    finished[0] = true;
                    timeoutHandler.removeCallbacksAndMessages(null);
                    callback.onCommentsLoaded(localDatabase.getCommentsOfPost(postId), true);
                }),
                () -> {
                    if (finished[0]) return;
                    finished[0] = true;
                    timeoutHandler.removeCallbacksAndMessages(null);
                    callback.onCommentsLoaded(localDatabase.getCommentsOfPost(postId), true);
                });
    }

    // Returns null for incomplete entries (e.g. an edit that raced with a delete)
    private Comment parseComment(DataSnapshot child, String postId) {
        String userId = child.child("userId").getValue(String.class);
        String text = child.child("text").getValue(String.class);
        Long date = child.child("date").getValue(Long.class);
        Boolean edited = child.child("edited").getValue(Boolean.class);

        if (userId == null || text == null || date == null) return null;

        Map<String, Boolean> likedBy = new HashMap<>();
        for (DataSnapshot like : child.child("likedBy").getChildren()) {
            if (like.getKey() != null) likedBy.put(like.getKey(), true);
        }
        return new Comment(child.getKey(), postId, userId, text, date, edited != null && edited, likedBy);
    }

    // Like = set likedBy/{userId}, unlike = remove it. Idempotent, so no transaction is needed.
    // The comments rule rejects it if the comment was deleted meanwhile.
    public void toggleCommentLike(Comment comment, String userId, boolean liked, PostOperationCallback callback) {
        DatabaseReference likeRef = commentsRef.child(comment.getPostId()).child(comment.getId())
                .child("likedBy").child(userId);

        ensureSignedIn(() -> (liked ? likeRef.setValue(true) : likeRef.removeValue())
                .addOnSuccessListener(unused -> {
                    localDatabase.insertComment(comment);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to update comment like", e);
                    callback.onError("Failed to update like.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Mirrors the whole comments tree into the local DB on every online feed refresh,
    // so deleted (e.g. moderated) and edited comments are updated on every phone,
    // even for posts whose comments were never opened. The tree is small (text only).
    private void syncAllCommentsToLocalDB() {
        commentsRef.get()
                .addOnSuccessListener(snapshot -> {
                    List<Comment> allComments = new ArrayList<>();
                    for (DataSnapshot postComments : snapshot.getChildren()) {
                        for (DataSnapshot child : postComments.getChildren()) {
                            Comment comment = parseComment(child, postComments.getKey());
                            if (comment != null) allComments.add(comment);
                        }
                    }
                    localDatabase.replaceAllComments(allComments);
                    Log.d("FirebaseRepository", "Synced " + allComments.size() + " comments to local DB");
                })
                .addOnFailureListener(e -> Log.e("FirebaseRepository", "Failed to sync comments", e));
    }

    // Adds the comment and increments the post's commentCount in one atomic update
    public void addComment(String postId, String userId, String text, CommentCreationCallback callback) {
        String commentId = commentsRef.child(postId).push().getKey();
        if (commentId == null) {
            callback.onError("Failed to add comment.");
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> commentMap = new HashMap<>();
        commentMap.put("userId", userId);
        commentMap.put("text", text);
        commentMap.put("date", timestamp);
        commentMap.put("edited", false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("comments/" + postId + "/" + commentId, commentMap);
        updates.put("posts/" + postId + "/commentCount", ServerValue.increment(1));

        ensureSignedIn(() -> rootRef.updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    Comment comment = new Comment(commentId, postId, userId, text, timestamp, false, null);
                    localDatabase.insertComment(comment);
                    callback.onCommentCreated(comment);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to add comment", e);
                    callback.onError("Failed to add comment.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // newDate: staff only, the comment's new date in seconds (null = unchanged). Changing only
    // the date doesn't mark the comment as edited.
    public void editComment(Comment comment, String newText, Long newDate, PostOperationCallback callback) {
        boolean textChanged = !newText.equals(comment.getText());
        Map<String, Object> updates = new HashMap<>();
        if (textChanged) {
            updates.put("text", newText);
            updates.put("edited", true);
        }
        if (newDate != null) updates.put("date", newDate);

        ensureSignedIn(() -> commentsRef.child(comment.getPostId()).child(comment.getId()).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (textChanged) {
                        comment.setText(newText);
                        comment.setEdited(true);
                    }
                    if (newDate != null) comment.setDate(newDate);
                    localDatabase.insertComment(comment);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to edit comment", e);
                    callback.onError("Failed to edit comment.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Removes the comment and decrements the post's commentCount in one atomic update
    public void deleteComment(Comment comment, PostOperationCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("comments/" + comment.getPostId() + "/" + comment.getId(), null);
        updates.put("posts/" + comment.getPostId() + "/commentCount", ServerValue.increment(-1));

        ensureSignedIn(() -> rootRef.updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    localDatabase.deleteComment(comment.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to delete comment", e);
                    callback.onError("Failed to delete comment.");
                }),
                () -> callback.onError("Could not connect to server."));
    }


    // TEAM MANAGEMENT
    // teams/{teamKey} = { name, emoji }, editable by admins; see utils/Teams

    // Downloads team names/emojis and stores them locally; onUpdated runs only if it succeeded
    public void fetchTeams(Runnable onUpdated) {
        ensureSignedIn(() -> teamsRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, String[]> teams = new HashMap<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String name = child.child("name").getValue(String.class);
                        String emoji = child.child("emoji").getValue(String.class);
                        teams.put(child.getKey(), new String[]{name != null ? name : "", emoji != null ? emoji : ""});
                    }
                    Teams.replaceAll(context, teams);
                    onUpdated.run();
                })
                .addOnFailureListener(e -> Log.e("FirebaseRepository", "Failed to fetch teams", e)),
                () -> Log.e("FirebaseRepository", "Failed to fetch teams: not signed in"));
    }

    // Deletes a team's name/emoji entry. Callers make sure nobody (and no unused code) is in it.
    // Deletes a team together with its unused activation codes, in one atomic update.
    // Refuses if (on fresh data) someone has already created an account in the team.
    public void deleteTeam(String teamKey, PostOperationCallback callback) {
        ensureSignedIn(() -> usersRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> updates = new HashMap<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (!teamKey.equals(child.child("team").getValue(String.class))) continue;
                        String username = child.child("username").getValue(String.class);
                        if (username != null && !username.isEmpty()) {
                            callback.onError(username + " is in this team now, so it can't be deleted.");
                            return;
                        }
                        updates.put("users/" + child.getKey(), null);
                        updates.put("pendingCodes/" + child.getKey(), null);
                    }
                    updates.put("teams/" + teamKey, null);
                    rootRef.updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                Teams.remove(context, teamKey);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("FirebaseRepository", "Failed to delete team", e);
                                callback.onError("Failed to delete the team.");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to read team members", e);
                    callback.onError("Could not connect to server.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Creates a team (also used to rename it / change its emoji)
    public void updateTeam(String teamKey, String name, String emoji, PostOperationCallback callback) {
        Map<String, Object> team = new HashMap<>();
        team.put("name", name);
        team.put("emoji", emoji);

        ensureSignedIn(() -> teamsRef.child(teamKey).setValue(team)
                .addOnSuccessListener(unused -> {
                    Teams.set(context, teamKey, name, emoji);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to update team", e);
                    callback.onError("Failed to update the team.");
                }),
                () -> callback.onError("Could not connect to server."));
    }


    // USER PROFILES
    // userInfo/{userId} = optional fields (photoUrl, photoVersion, ...); see utils/UserProfiles.
    // The photo is stored in Storage at profilePics/{userId}.jpg, so a new one replaces the old one.

    // Downloads all profiles and stores them locally; onUpdated runs only if it succeeded
    public void fetchUserInfo(Runnable onUpdated) {
        ensureSignedIn(() -> userInfoRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Map<String, String>> all = new HashMap<>();
                    for (DataSnapshot user : snapshot.getChildren()) {
                        Map<String, String> fields = new HashMap<>();
                        for (DataSnapshot field : user.getChildren()) {
                            Object value = field.getValue();
                            if (value != null) fields.put(field.getKey(), String.valueOf(value));
                        }
                        all.put(user.getKey(), fields);
                    }
                    UserProfiles.replaceAll(context, all);
                    preloadProfilePhotos(all);
                    onUpdated.run();
                })
                .addOnFailureListener(e -> Log.e("FirebaseRepository", "Failed to fetch user info", e)),
                () -> Log.e("FirebaseRepository", "Failed to fetch user info: not signed in"));
    }

    // Downloads every profile photo into Glide's disk cache (same key as when they are shown),
    // so photos also appear offline, even ones never seen before. Small: 512px JPEGs.
    private void preloadProfilePhotos(Map<String, Map<String, String>> all) {
        for (Map.Entry<String, Map<String, String>> entry : all.entrySet()) {
            String url = entry.getValue().get(UserProfiles.FIELD_PHOTO_URL);
            if (url == null || url.isEmpty()) continue;
            String version = entry.getValue().get(UserProfiles.FIELD_PHOTO_VERSION);
            com.bumptech.glide.Glide.with(context.getApplicationContext())
                    .load(url)
                    .signature(new com.bumptech.glide.signature.ObjectKey(version != null ? version : ""))
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                    .preload();
        }
    }

    // Crops the photo to a centered square, scales it to 512px and uploads it
    public void uploadProfilePhoto(String userId, Bitmap photo, PostOperationCallback callback) {
        int size = Math.min(photo.getWidth(), photo.getHeight());
        Bitmap square = Bitmap.createBitmap(photo, (photo.getWidth() - size) / 2, (photo.getHeight() - size) / 2, size, size);
        Bitmap scaled = size > 512 ? Bitmap.createScaledBitmap(square, 512, 512, true) : square;

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
        byte[] data = out.toByteArray();

        StorageReference photoRef = FirebaseStorage.getInstance().getReference().child("profilePics/" + userId + ".jpg");

        ensureSignedIn(() -> photoRef.putBytes(data)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) throw task.getException();
                    return photoRef.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    String version = String.valueOf(System.currentTimeMillis());
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(UserProfiles.FIELD_PHOTO_URL, uri.toString());
                    updates.put(UserProfiles.FIELD_PHOTO_VERSION, version);

                    userInfoRef.child(userId).updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_URL, uri.toString());
                                UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_VERSION, version);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("FirebaseRepository", "Failed to save photo info", e);
                                callback.onError("Failed to save the profile photo.");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to upload profile photo", e);
                    callback.onError("Failed to upload the profile photo.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Saves the given "User Info" fields (field -> value); an empty value deletes the field
    public void updateUserInfo(String userId, Map<String, String> fields, PostOperationCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            updates.put(entry.getKey(), value == null || value.isEmpty() ? null : value);
        }

        ensureSignedIn(() -> userInfoRef.child(userId).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    for (Map.Entry<String, String> entry : fields.entrySet()) {
                        UserProfiles.setField(context, userId, entry.getKey(), entry.getValue());
                    }
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to update user info", e);
                    callback.onError("Failed to save the profile information.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    public void removeProfilePhoto(String userId, PostOperationCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(UserProfiles.FIELD_PHOTO_URL, null);
        updates.put(UserProfiles.FIELD_PHOTO_VERSION, null);

        ensureSignedIn(() -> userInfoRef.child(userId).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_URL, null);
                    UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_VERSION, null);
                    // The file itself is not needed anymore; failing to delete it is harmless
                    FirebaseStorage.getInstance().getReference().child("profilePics/" + userId + ".jpg").delete();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to remove profile photo", e);
                    callback.onError("Failed to remove the profile photo.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Deletes a user and everything that belongs to them, in one atomic update:
    // account, profile (photo included), their posts (with the comments on them and the post
    // images), their comments on other posts (commentCount adjusted), and their likes on posts
    // and comments (likes count adjusted). Deleted posts are added to deletedPosts and comments
    // are re-synced on refresh, so other phones also remove them from their offline copy.
    public void deleteUser(String userId, PostOperationCallback callback) {
        ensureSignedIn(() -> Tasks.whenAllSuccess(postsRef.get(), commentsRef.get())
                .addOnSuccessListener(results -> {
                    DataSnapshot posts = (DataSnapshot) results.get(0);
                    DataSnapshot comments = (DataSnapshot) results.get(1);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("users/" + userId, null);
                    updates.put("userInfo/" + userId, null);
                    updates.put("pendingCodes/" + userId, null);

                    // Their posts: remove the post, its comments and image; mark as deleted
                    java.util.Set<String> deletedPostIds = new java.util.HashSet<>();
                    List<String> imagesToDelete = new ArrayList<>();
                    for (DataSnapshot post : posts.getChildren()) {
                        if (!userId.equals(post.child("userId").getValue(String.class))) continue;
                        String postId = post.getKey();
                        deletedPostIds.add(postId);
                        updates.put("posts/" + postId, null);
                        updates.put("comments/" + postId, null);
                        updates.put("deletedPosts/" + postId, true);
                        String imageUrl = post.child("imageUrl").getValue(String.class);
                        if (imageUrl != null && !imageUrl.isEmpty()) imagesToDelete.add(imageUrl);
                    }

                    // Their likes on other posts
                    for (DataSnapshot post : posts.getChildren()) {
                        String postId = post.getKey();
                        if (deletedPostIds.contains(postId)) continue;
                        if (post.child("likedBy").hasChild(userId)) {
                            updates.put("posts/" + postId + "/likedBy/" + userId, null);
                            updates.put("posts/" + postId + "/likes", ServerValue.increment(-1));
                        }
                    }

                    // Their comments on other posts, and their likes on other comments
                    for (DataSnapshot postComments : comments.getChildren()) {
                        String postId = postComments.getKey();
                        if (deletedPostIds.contains(postId)) continue;
                        int removedComments = 0;
                        for (DataSnapshot comment : postComments.getChildren()) {
                            String path = "comments/" + postId + "/" + comment.getKey();
                            if (userId.equals(comment.child("userId").getValue(String.class))) {
                                updates.put(path, null);
                                removedComments++;
                            } else if (comment.child("likedBy").hasChild(userId)) {
                                updates.put(path + "/likedBy/" + userId, null);
                            }
                        }
                        if (removedComments > 0) {
                            updates.put("posts/" + postId + "/commentCount", ServerValue.increment(-removedComments));
                        }
                    }

                    rootRef.updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                localDatabase.deleteUser(userId);
                                localDatabase.deleteCommentsOfUser(userId);
                                for (String postId : deletedPostIds) {
                                    localDatabase.deletePost(postId);
                                    localDatabase.deleteCommentsOfPost(postId);
                                }
                                UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_URL, null);
                                UserProfiles.setField(context, userId, UserProfiles.FIELD_PHOTO_VERSION, null);

                                // Files in Storage; failing to delete one is harmless
                                FirebaseStorage.getInstance().getReference().child("profilePics/" + userId + ".jpg").delete();
                                for (String imageUrl : imagesToDelete) {
                                    try {
                                        FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl).delete();
                                    } catch (IllegalArgumentException e) {
                                        Log.e("FirebaseRepository", "Invalid image URL " + imageUrl, e);
                                    }
                                }
                                Log.d("FirebaseRepository", "Deleted user " + userId + " with " + deletedPostIds.size() + " posts");
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("FirebaseRepository", "Failed to delete user", e);
                                callback.onError("Failed to delete the user.");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to read data for user deletion", e);
                    callback.onError("Could not connect to server.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Moves a user to another team (existing or new). Only organizers/admins can do it (app side).
    public void updateUserTeam(User user, String team, PostOperationCallback callback) {
        ensureSignedIn(() -> usersRef.child(user.getId()).child("team").setValue(team)
                .addOnSuccessListener(unused -> {
                    user.setTeam(team);
                    localDatabase.insertUser(user);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to update team", e);
                    callback.onError("Failed to change the team.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Reports a post (commentId null) or a comment to the moderators and admins; the Cloud
    // Function notifyReport sends them a notification. One report per person and item: the key
    // is {reporterId}_{postId or commentId}, so reporting again doesn't notify twice.
    public void reportContent(String postId, String commentId, String reportedUserId, String reporterId,
                              String reason, PostOperationCallback callback) {
        String item = commentId != null ? commentId : postId;
        DatabaseReference reportRef = rootRef.child("reports").child(reporterId + "_" + item);

        ensureSignedIn(() -> reportRef.get()
                .addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        callback.onError("You already reported this.");
                        return;
                    }
                    Map<String, Object> report = new HashMap<>();
                    report.put("postId", postId);
                    if (commentId != null) report.put("commentId", commentId);
                    report.put("reportedUserId", reportedUserId);
                    report.put("reporterId", reporterId);
                    if (reason != null && !reason.isEmpty()) report.put("reason", reason);
                    report.put("createdAt", System.currentTimeMillis() / 1000L);

                    reportRef.setValue(report)
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e -> {
                                Log.e("FirebaseRepository", "Failed to report", e);
                                callback.onError("Failed to send the report.");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to check report", e);
                    callback.onError("Failed to send the report.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Changes the role (PARTICIPANT / ORGANIZER / MODERATOR / ADMIN); only admins can do this
    public void updateUserType(User user, String userType, PostOperationCallback callback) {
        ensureSignedIn(() -> usersRef.child(user.getId()).child("userType").setValue(userType)
                .addOnSuccessListener(unused -> {
                    user.setUserType(userType);
                    localDatabase.insertUser(user);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to update role", e);
                    callback.onError("Failed to change the role.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Changes the username after checking (on fresh data) that nobody else uses it.
    // The user logs in with the new username afterwards.
    public void updateUsername(String userId, String newUsername, PostOperationCallback callback) {
        getAllUsers(new UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                for (User other : users.values()) {
                    if (!other.getId().equals(userId) && other.getUsername() != null
                            && other.getUsername().equalsIgnoreCase(newUsername)) {
                        callback.onError("Username already taken");
                        return;
                    }
                }

                usersRef.child(userId).child("username").setValue(newUsername)
                        .addOnSuccessListener(unused -> {
                            User user = users.get(userId);
                            if (user != null) {
                                user.setUsername(newUsername);
                                localDatabase.insertUser(user);
                            }
                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e("FirebaseRepository", "Failed to update username", e);
                            callback.onError("Failed to change the username.");
                        });
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }


    // ACTIVATION CODES
    // A new account starts as an "unused" user: users/{id} = { accessCode (BCrypt hash), team,
    // userType, username: "" }. Whoever creates an account with the code fills in the username.
    // For codes created from the app, the readable code is also kept in pendingCodes/{id}, so
    // organizers can see it in "Unused codes"; it is deleted as soon as the account is created.
    // Older codes only exist as hashes, so they are listed without the code.

    public static class UnusedCode {
        public final String userId;
        public final String code;      // null for older codes (only the hash exists)
        public final String team;
        public final String userType;
        public final long createdAt;   // 0 when unknown

        UnusedCode(String userId, String code, String team, String userType, long createdAt) {
            this.userId = userId;
            this.code = code;
            this.team = team;
            this.userType = userType;
            this.createdAt = createdAt;
        }
    }

    public interface UnusedCodesCallback {
        void onLoaded(List<UnusedCode> codes);
        void onError(String errorMessage);
    }

    public interface CodeCreationCallback {
        void onCreated(String code);
        void onError(String errorMessage);
    }

    // Gives an existing account a new generated access code (e.g. they forgot theirs). The code
    // differs from every unused activation code; only its hash is stored, like for any account.
    // The old code stops working; devices already logged in stay logged in.
    public void resetAccessCode(String userId, CodeCreationCallback callback) {
        ensureSignedIn(() -> rootRef.child("pendingCodes").get()
                .addOnSuccessListener(snapshot -> {
                    java.util.Set<String> unused = new java.util.HashSet<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String existing = child.child("code").getValue(String.class);
                        if (existing != null) unused.add(existing.toLowerCase());
                    }
                    String code = null;
                    for (int i = 0; i < 100 && code == null; i++) {
                        String candidate = com.suici.roverhood.dialogs.ActivationCodes.randomCode();
                        if (!unused.contains(candidate.toLowerCase())) code = candidate;
                    }
                    if (code == null) {
                        callback.onError("Could not generate a new code, try again.");
                        return;
                    }

                    String newCode = code;
                    Executors.newSingleThreadExecutor().execute(() -> {
                        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(newCode, org.mindrot.jbcrypt.BCrypt.gensalt(12));
                        usersRef.child(userId).child("accessCode").setValue(hash)
                                .addOnSuccessListener(unused2 -> callback.onCreated(newCode))
                                .addOnFailureListener(e -> {
                                    Log.e("FirebaseRepository", "Failed to reset access code", e);
                                    callback.onError("Failed to reset the access code.");
                                });
                    });
                })
                .addOnFailureListener(e -> callback.onError("Could not connect to server.")),
                () -> callback.onError("Could not connect to server."));
    }

    public void createActivationCode(String code, String team, String userType, String createdBy, CodeCreationCallback callback) {
        DatabaseReference pendingCodesRef = rootRef.child("pendingCodes");

        ensureSignedIn(() -> pendingCodesRef.get()
                .addOnSuccessListener(snapshot -> {
                    // Unused codes must be unique, ignoring case (older hashed codes can't be
                    // compared cheaply). Matching an already used code doesn't matter: logging in
                    // needs the username too.
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (code.equalsIgnoreCase(child.child("code").getValue(String.class))) {
                            callback.onError("This code already exists");
                            return;
                        }
                    }

                    String userId = usersRef.push().getKey();
                    if (userId == null) {
                        callback.onError("Failed to create the code.");
                        return;
                    }

                    // BCrypt is slow on purpose (same cost as the existing codes), so off the main thread
                    Executors.newSingleThreadExecutor().execute(() -> {
                        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(code, org.mindrot.jbcrypt.BCrypt.gensalt(12));

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("users/" + userId + "/accessCode", hash);
                        updates.put("users/" + userId + "/team", team);
                        updates.put("users/" + userId + "/userType", userType);
                        updates.put("users/" + userId + "/username", "");
                        updates.put("pendingCodes/" + userId + "/code", code);
                        updates.put("pendingCodes/" + userId + "/createdAt", System.currentTimeMillis() / 1000L);
                        updates.put("pendingCodes/" + userId + "/createdBy", createdBy);

                        rootRef.updateChildren(updates)
                                .addOnSuccessListener(unused -> callback.onCreated(code))
                                .addOnFailureListener(e -> {
                                    Log.e("FirebaseRepository", "Failed to create activation code", e);
                                    callback.onError("Failed to create the code.");
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to read pending codes", e);
                    callback.onError("Could not connect to server.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    public void loadUnusedCodes(UnusedCodesCallback callback) {
        getAllUsers(new UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                rootRef.child("pendingCodes").get()
                        .addOnSuccessListener(pending -> {
                            List<UnusedCode> codes = new ArrayList<>();
                            for (User user : users.values()) {
                                if (user.getUsername() != null && !user.getUsername().isEmpty()) continue;
                                DataSnapshot info = pending.child(user.getId());
                                Long createdAt = info.child("createdAt").getValue(Long.class);
                                codes.add(new UnusedCode(user.getId(),
                                        info.child("code").getValue(String.class),
                                        user.getTeam(), user.getUserType(),
                                        createdAt != null ? createdAt : 0));
                            }
                            // Newest first, older codes without a date at the end
                            codes.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                            callback.onLoaded(codes);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("FirebaseRepository", "Failed to read pending codes", e);
                            callback.onError("Could not connect to server.");
                        });
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    // Deletes an unused code (the empty account and its readable code). Re-checks first that
    // nobody created an account with it in the meantime.
    public void deleteUnusedCode(String userId, PostOperationCallback callback) {
        ensureSignedIn(() -> usersRef.child(userId).child("username").get()
                .addOnSuccessListener(snapshot -> {
                    String username = snapshot.getValue(String.class);
                    if (username != null && !username.isEmpty()) {
                        callback.onError("This code was already used by " + username);
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("users/" + userId, null);
                    updates.put("pendingCodes/" + userId, null);
                    rootRef.updateChildren(updates)
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e -> {
                                Log.e("FirebaseRepository", "Failed to delete code", e);
                                callback.onError("Failed to delete the code.");
                            });
                })
                .addOnFailureListener(e -> callback.onError("Could not connect to server.")),
                () -> callback.onError("Could not connect to server."));
    }


    // USER MANAGEMENT

    public void getAllUsers(UsersCallback callback) {
        ensureSignedIn(() -> usersRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, User> usersMap = new HashMap<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        User user = child.getValue(User.class);
                        if (user != null) {
                            user.setId(child.getKey());
                            usersMap.put(user.getId(), user);
                        }
                    }
                    callback.onUsersFetched(usersMap);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseRepository", "Failed to fetch users", e);
                    callback.onError("Could not connect to server.");
                }),
                () -> callback.onError("Could not connect to server."));
    }

    // Sets the username for a user only if it hasn't been set already
    public void setUsernameIfNewUser(String userId, String newUsername, PostOperationCallback callback) {
        ensureSignedIn(() -> setUsernameIfNewUserSignedIn(userId, newUsername, callback),
                () -> callback.onError("Could not connect to server."));
    }

    private void setUsernameIfNewUserSignedIn(String userId, String newUsername, PostOperationCallback callback) {
        DatabaseReference userRef = usersRef.child(userId);

        userRef.child("username").get().addOnSuccessListener(snapshot -> {
            String existingUsername = snapshot.getValue(String.class);

            if (existingUsername == null || existingUsername.trim().isEmpty()) {
                userRef.child("username").setValue(newUsername)
                        .addOnSuccessListener(unused -> {
                            Log.d("FirebaseRepository", "Username set for user: " + userId);
                            // The code is used now: drop its readable copy, only the hash stays
                            rootRef.child("pendingCodes").child(userId).removeValue();
                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e("FirebaseRepository", "Failed to set username", e);
                            callback.onError("Failed to set username.");
                        });
            } else {
                Log.d("FirebaseRepository", "Username already exists for user: " + userId);
                callback.onError("Username already exists.");
            }
        }).addOnFailureListener(e -> {
            Log.e("FirebaseRepository", "Failed to fetch current username", e);
            callback.onError("Failed to fetch current username.");
        });
    }


    // OTHERS

    // Performs anonymous sign-in using Firebase Authentication
    public void signInAnonymously() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            Log.d("Auth", "Signed in as: " + user.getUid());
                        } else {
                            Log.e("Auth", "Anonymous sign-in failed", task.getException());
                        }
                    });
        }
    }

    // Runs onReady only after an (anonymous) Firebase user exists, so requests aren't sent unauthenticated
    private void ensureSignedIn(Runnable onReady, Runnable onFailure) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            onReady.run();
            return;
        }
        auth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("Auth", "Signed in as: " + auth.getCurrentUser().getUid());
                        onReady.run();
                    } else {
                        Log.e("Auth", "Anonymous sign-in failed", task.getException());
                        onFailure.run();
                    }
                });
    }

    public boolean isLoading() { return loading; }
}
