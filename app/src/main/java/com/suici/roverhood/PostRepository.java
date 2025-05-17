package com.suici.roverhood;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostRepository {

    // Singleton instance
    private static PostRepository instance;

    private final DatabaseReference postsRef;
    private final DatabaseReference usersRef;
    private final DatabaseReference deletedPostsRef;
    private final LocalDatabase localDatabase;
    private final Context context;

    private boolean loading = false;

    public interface PostRepositoryCallback {
        void onPostsLoaded(List<Post> posts, boolean isOffline);
        void onError(String errorMessage);
    }

    public interface PostOperationCallback  {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface PostCreationCallback {
        void onPostCreated(Post post);
        void onError(String errorMessage);
    }

    public PostRepository(Context context) {
        this.context = context;
        this.postsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("posts");

        this.usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        this.deletedPostsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("deletedPosts");

        this.localDatabase = LocalDatabase.getInstance(context);
    }

    public static synchronized PostRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PostRepository(context);
        }
        return instance;
    }

    public void loadPosts(PostRepositoryCallback callback) {
        loading = true;
        List<Post> posts = new ArrayList<>();
        boolean[] isTimeoutReached = {false};

        // Set a timeout of 10 seconds
        final Handler timeoutHandler = new Handler();
        timeoutHandler.postDelayed(() -> {
            isTimeoutReached[0] = true;
            callback.onPostsLoaded(loadOfflinePosts(), true);
        }, 10000);

        postsRef.orderByChild("date").get().addOnSuccessListener(snapshot -> {
            if (isTimeoutReached[0]) return;
            timeoutHandler.removeCallbacksAndMessages(null);

            if (!snapshot.hasChildren()) {
                callback.onPostsLoaded(loadOfflinePosts(), true);
                return;
            }

            removeDeletedPostsFromLocalDB();

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
                Integer version = postSnap.child("version").getValue(Integer.class);

                if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                    loadedCount[0]++;
                    continue;
                }
                if(version == null) {
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
                        Post post = new Post(null, postId, date, user, description, imageUrl, likes, likedByMap, announcement, finalVersion,false);
                        posts.add(post);
                    }
                    loadedCount[0]++;
                    if (loadedCount[0] == totalPosts) {
                        loading = false;
                        callback.onPostsLoaded(posts, false);
                    }
                }).addOnFailureListener(e -> {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalPosts) {
                        loading = false;
                        callback.onError("Failed to load user data");
                    }
                });
            }
        }).addOnFailureListener(e -> {
            callback.onPostsLoaded(loadOfflinePosts(), true);
        });
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

    private List<Post> loadOfflinePosts() {
        Map<String, Post> offlinePosts = localDatabase.getAllOfflinePosts(null, localDatabase.getAllUsers());
        loading = false;
        return new ArrayList<>(offlinePosts.values());
    }

    public void syncPostsToLocalDB(List<Post> postsToSync) {
        ((MainActivity) context).runOnUiThread(() -> {
            if (DownloadImageUtils.getLoadedImageCount() >= DownloadImageUtils.getTotalImageCount()) {
                DownloadImageUtils.setLoadedImageCount(0);
                DownloadImageUtils.setTotalImageCount(0);
                ProgressBarUtils.resetProgressBar(((MainActivity) context).getDownloadProgressBar());
            }
        });

        for (Post post : postsToSync) {
            String userId = post.getUser().getId();
            String fileName = post.getId();
            String imageUrl = post.getImageUrl();

            ((MainActivity) context).runOnUiThread(() -> {
                DownloadImageUtils.incrementProgressBarMax();
            });

            DownloadImageUtils.saveImageToInternalStorage(context, imageUrl, fileName, new DownloadImageUtils.ImageSaveCallback() {
                @Override
                public void onSuccess(String imagePath) {
                    Log.d("LocalSync", "Image saved at: " + imagePath);
                    ((MainActivity) context).runOnUiThread(() -> {
                        DownloadImageUtils.incrementProgressBar();
                    });
                    localDatabase.insertPost(post.getId(), post.getDate(), post.getDescription(), imagePath, post.getLikes(), post.getLikedBy(), post.isAnnouncement(), userId, post.getVersion());
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e("LocalSync", "Failed to save image for post " + post.getId(), e);
                    ((MainActivity) context).runOnUiThread(() -> {
                        DownloadImageUtils.incrementProgressBar();
                    });
                }
            });
        }
    }

    public void syncSinglePostToLocalDB(Post post) {
        List<Post> singlePostList = new ArrayList<>();
        singlePostList.add(post);
        syncPostsToLocalDB(singlePostList);
    }

    public void createPost(String description, User user, String imageUrl, boolean isAnnouncement, Fragment fragment, PostCreationCallback callback) {
        String postId = postsRef.push().getKey();
        if (postId == null) {
            Log.e("PostRepository", "Failed to generate post ID");
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

        postsRef.child(postId).setValue(postMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d("PostRepository", "Post successfully saved with ID: " + postId);

                    Post post = new Post(fragment, postId, timestamp, user, description, imageUrl, 1, likedByMap, isAnnouncement, 0, false);
                    new Thread(() -> {
                        syncSinglePostToLocalDB(post);
                    }).start();
                    callback.onPostCreated(post);
                })
                .addOnFailureListener(e -> {
                    Log.e("PostRepository", "Failed to save post in Firebase.", e);
                    callback.onError("Failed to save post.");
                });
    }

    public void deletePost(String postId, String imageUrl, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(postId);
        DatabaseReference deletedPostRef = deletedPostsRef.child(postId);

        deleteImageFromStorage(imageUrl, new PostRepository.PostOperationCallback() {
            @Override
            public void onSuccess() {
                Log.d("PostRepository", "Image deleted successfully");
            }
            @Override
            public void onError(String errorMessage) {
                Log.e("PostRepository", "Error deleting image: " + errorMessage);
            }
        });

        postRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("PostRepository", "Post successfully deleted from Firebase.");
                localDatabase.deletePost(postId);

                deletedPostRef.setValue(true).addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful()) {
                        Log.d("PostRepository", "Post ID added to deletedPosts list.");
                    } else {
                        Log.e("PostRepository", "Failed to add post ID to deletedPosts list.", task2.getException());
                    }
                });

                callback.onSuccess();
            } else {
                Log.e("PostRepository", "Failed to delete post from Firebase.");
                callback.onError("Failed to delete post");
            }
        });
    }

    public void updatePost(Post post, String description, String imageUrl, boolean isAnnouncement, PostOperationCallback callback) {
        DatabaseReference postRef = postsRef.child(post.getId());

        post.incrementVersion();

        Map<String, Object> updates = new HashMap<>();
        updates.put("description", description);
        updates.put("imageUrl", imageUrl);
        updates.put("announcement", isAnnouncement);
        updates.put("version", post.getVersion());

        postRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                new Thread(() -> {
                    Post updatedPost = post;
                    updatedPost.setDescription(description);
                    updatedPost.setImageUrl(imageUrl);
                    updatedPost.setAnnouncement(isAnnouncement);

                    localDatabase.deletePost(updatedPost.getId());
                    syncSinglePostToLocalDB(updatedPost);
                }).start();

                callback.onSuccess();
                Log.d("PostRepository", "Post successfully updated.");
            } else {
                Log.e("PostRepository", "Failed to update post in Firebase.");
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
                        Log.e("PostRepository", "Transaction failed: " + error.getMessage());
                        callback.onError("Transaction failed.");
                    } else {
                        Log.e("PostRepository", "Transaction not committed (possible conflict or error)");
                        callback.onError("Transaction not committed.");
                    }
                }
            }
        });
    }

    public void deleteImageFromStorage(String imageUrl, PostOperationCallback callback) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e("PostRepository", "Image URL is empty or null, cannot delete.");
            callback.onError("Image URL is invalid.");
            return;
        }

        StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
        imageRef.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("PostRepository", "Image successfully deleted from Firebase Storage.");
                callback.onSuccess();
            } else {
                Log.e("PostRepository", "Failed to delete image from Firebase Storage.", task.getException());
                callback.onError("Failed to delete image.");
            }
        });
    }

    public boolean isLoading() { return loading; }
}
