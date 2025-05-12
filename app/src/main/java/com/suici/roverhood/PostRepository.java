package com.suici.roverhood;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostRepository {

    private final DatabaseReference postsRef;
    private final DatabaseReference usersRef;
    private final LocalDatabase localDatabase;
    private final Context context;

    private boolean loading = false;

    public interface PostRepositoryCallback {
        void onPostsLoaded(List<Post> posts, boolean isOffline);
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

        this.localDatabase = LocalDatabase.getInstance(context);
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

                if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                    loadedCount[0]++;
                    continue;
                }

                usersRef.child(userId).get().addOnSuccessListener(userSnap -> {
                    User user = userSnap.getValue(User.class);
                    if (user != null) {
                        user.setId(userId);
                        Post post = new Post(null, postId, date, user, description, imageUrl, likes, likedByMap, announcement, false);
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
                    localDatabase.insertPost(post.getId(), post.getDate(), post.getDescription(), imagePath, post.getLikes(), post.getLikedBy(), post.isAnnouncement(), userId);
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

    public boolean isLoading() { return loading; }
}
