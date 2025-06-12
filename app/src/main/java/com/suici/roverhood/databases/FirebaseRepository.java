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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.models.Topic;
import com.suici.roverhood.models.User;
import com.suici.roverhood.models.Post;
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

    private final DatabaseReference postsRef;
    private final DatabaseReference topicsRef;
    private final DatabaseReference usersRef;
    private final DatabaseReference deletedPostsRef;
    private final LocalDatabase localDatabase;
    private Context context;

    private boolean loading = false;
    private boolean topicsLoaded = false;

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

    public void loadPosts( boolean isOffline, boolean isOfflineUser, PostRepositoryCallback callback) {
        loading = true;
        List<PostHandler> postHandlers = new ArrayList<>();
        boolean[] isTimeoutReached = {false};
        int timeLimit = isOffline ? 2000 : 10000;

        if (isOfflineUser) {
            loading = false;
            isTimeoutReached[0] = true;
            callback.onPostsLoaded(loadOfflinePosts(), true);
        }

        // Set a timeout of 10s or 2s if already offline
        final Handler timeoutHandler = new Handler();
        timeoutHandler.postDelayed(() -> {
            loading = false;
            isTimeoutReached[0] = true;
            callback.onPostsLoaded(loadOfflinePosts(), true);
        }, timeLimit);

        topicsLoaded = false;
        fetchAllTopics();

        new Thread(() -> {
            int retryCount = 0;
            while (!topicsLoaded && retryCount < timeLimit/100) { // 10 seconds max
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e("FirebaseRepository", "Wait loop interrupted", e);
                }
                retryCount++;
            }

            postsRef.orderByChild("date").get().addOnSuccessListener(snapshot -> {
                if (isTimeoutReached[0]) {
                    loading = false;
                    return;
                }
                timeoutHandler.removeCallbacksAndMessages(null);

                removeDeletedPostsFromLocalDB();
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

                    if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                        loadedCount[0]++;
                        continue;
                    }
                    if (version == null) {
                        version = 0;
                    }
                    Integer finalVersion = version;

                    Integer localVersion = localDatabase.getPostVersion(postId);
                    if (localVersion != null) {
                        if (!version.equals(localVersion)) {
                            Log.d("PostSync", "Post " + postId + " is outdated. Removing from Local DB.");
                            localDatabase.deletePost(postId);
                        }
                    }

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
                            PostHandler postHandler = new PostHandler(null, post, false);
                            postHandlers.add(postHandler);
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == totalPosts) {
                            loading = false;
                            callback.onPostsLoaded(postHandlers, false);
                        }
                    }).addOnFailureListener(e -> {
                        loadedCount[0]++;
                        if (loadedCount[0] == totalPosts) {
                            loading = false;
                            callback.onPostsLoaded(postHandlers, false);
                        }
                    });
                }
            }).addOnFailureListener(e -> {
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
        Map<String, Boolean> likedByMap = new HashMap<>();
        likedByMap.put(user.getId(), true);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("date", timestamp);
        postMap.put("description", description);
        postMap.put("imageUrl", imageUrl);
        postMap.put("likedBy", likedByMap);
        postMap.put("likes", 1);
        postMap.put("announcement", isAnnouncement);
        postMap.put("userId", user.getId());
        if (topic != null) {
            postMap.put("topicId", topic.getId());
        }

        postsRef.child(postId).setValue(postMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseRepository", "Post successfully saved with ID: " + postId);

                    Post post = new Post(postId, user, timestamp, topic, description, imageUrl, 1, likedByMap, isAnnouncement, 0);
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

                deletedPostRef.setValue(true).addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful()) {
                        Log.d("FirebaseRepository", "Post ID added to deletedPosts list.");
                    } else {
                        Log.e("FirebaseRepository", "Failed to add post ID to deletedPosts list.", task2.getException());
                    }
                });

                callback.onSuccess();
            } else {
                Log.e("FirebaseRepository", "Failed to delete post from Firebase.");
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

    public void updatePost(PostHandler postHandler, String description, String imageUrl, boolean isAnnouncement, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(postHandler.getPost().getId());

        postHandler.getPost().incrementVersion();

        Map<String, Object> updates = new HashMap<>();
        updates.put("description", description);
        updates.put("imageUrl", imageUrl);
        updates.put("announcement", isAnnouncement);
        updates.put("version", postHandler.getPost().getVersion());

        postRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                new Thread(() -> {
                    postHandler.getPost().setDescription(description);
                    postHandler.getPost().setImageUrl(imageUrl);
                    postHandler.getPost().setAnnouncement(isAnnouncement);

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

    public void savePostsToLocalDB(List<PostHandler> postsToSave) {
        ((MainActivity) context).runOnUiThread(() -> {
            if (ImageDownload.getLoadedImageCount() >= ImageDownload.getTotalImageCount()) {
                ImageDownload.setLoadedImageCount(0);
                ImageDownload.setTotalImageCount(0);
                ProgressBar.resetProgressBar(((MainActivity) context).getDownloadProgressBar());
            }
        });

        for (PostHandler postHandler : postsToSave) {
            String fileName = postHandler.getPost().getId();
            String imageUrl = postHandler.getPost().getImageUrl();

            ((MainActivity) context).runOnUiThread(() -> {
                ImageDownload.incrementProgressBarMax(context);
            });

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
                }
            }
        });
    }

    // TOPIC MANAGEMENT

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

                Log.d("FirebaseRepository", "All topics pre-fetched: " + Topic.getAllTopics().size());
                topicsLoaded = true;
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
        }

        Log.d("removeDeletedTopics", "Finished removing unused topics. Total removed: " + unusedTopics.size());
    }

    // USER MANAGEMENT

    public void getAllUsers(UsersCallback callback) {
        signInAnonymously();
        usersRef.get()
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
                    callback.onError("Failed to fetch users from Firebase.");
                });
    }

    public void setUsernameIfNewUser(String userId, String newUsername, PostOperationCallback callback) {
        signInAnonymously();
        DatabaseReference userRef = usersRef.child(userId);

        userRef.child("username").get().addOnSuccessListener(snapshot -> {
            String existingUsername = snapshot.getValue(String.class);

            if (existingUsername == null || existingUsername.trim().isEmpty()) {
                userRef.child("username").setValue(newUsername)
                        .addOnSuccessListener(unused -> {
                            Log.d("FirebaseRepository", "Username set for user: " + userId);
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

    public boolean isLoading() { return loading; }
}
