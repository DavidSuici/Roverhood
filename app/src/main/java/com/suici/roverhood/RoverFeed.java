package com.suici.roverhood;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.suici.roverhood.databinding.RoverFeedBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoverFeed extends Fragment {

    private RoverFeedBinding binding;
    Map<String, Post> postMap = new LinkedHashMap<>();
    MainActivity activity;
    MenuItem announcementsFilter;
    SwitchCompat announcementsSwitch;
    private OnBackPressedCallback backCallback;
    private boolean isLoadingFirebasePosts = false;

    // partial load
    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 3;
    private boolean isLoading = false;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = RoverFeedBinding.inflate(inflater, container, false);
        activity = (MainActivity) requireActivity();

        // Refresh on back pressed when on feed, but keep the general back pressed logic as well
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);

                refreshFeed();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(requireActivity(), backCallback);



        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activity = (MainActivity) requireActivity();

        // Add post - button logic
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
            activity.getFloatingButton().setOnClickListener(v -> {
                    AddPost addPostFragment = new AddPost();
                    addPostFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
            });
        }

        // Log-out - button logic
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        binding.buttonLogOut.setOnClickListener(v -> {
            localDB.markLoggedOut();
            activity.setCurrentUser(null);
            NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn);
        });

        // Refresh on bottom
        binding.scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View scrollView = binding.scrollView.getChildAt(binding.scrollView.getChildCount() - 1);
            int diff = scrollView.getBottom() - (binding.scrollView.getHeight() + binding.scrollView.getScrollY());
            if (diff <= 100 && !isLoading) {
                if(isLoadingFirebasePosts == false) {
                    isLoading = true;
                    drawMorePosts();
                }
            }
        });

        // Announcements - check button logic
        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                announcementsFilter = optionsMenu.findItem(R.id.checkable_menu);
                if (announcementsFilter != null && announcementsFilter.getActionView() != null) {
                    announcementsFilter.setVisible(true);
                    announcementsSwitch = (SwitchCompat) announcementsFilter.getActionView().findViewById(R.id.switch2);
                    if (announcementsSwitch != null) {
                        // Refresh feed when checked
                        announcementsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> refreshFeed());
                        // Refresh feed for the first load
                        refreshFeed();
                    }
                }
            }
        });

        // User information - cosmetic logic
        if (activity.getCurrentUser() != null) {
            binding.username.setText(activity.getCurrentUser().username);
            binding.team.setText(activity.getCurrentUser().team);
            binding.usernameLabel.setText(activity.getCurrentUser().userType);
        }

        // Refresh - cosmetic logic
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;
        swipeRefreshLayout.setProgressViewOffset(true, 100, 250);
        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);
    }

    @Override
    public void onDestroyView() {
        if (announcementsSwitch != null) {
            announcementsSwitch.setOnCheckedChangeListener(null);
            announcementsSwitch = null;
        }

        if (announcementsFilter != null) {
            announcementsFilter.setVisible(false);
            announcementsFilter = null;
        }

        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.INVISIBLE);
        }

        if (backCallback != null)
            backCallback.remove();

        super.onDestroyView();
        binding = null;
    }

    private void drawMorePosts() {
        announcementsSwitch.setEnabled(false);
        binding.loadingMoreProgress.setVisibility(View.VISIBLE);
        int totalPosts = postMap.size();
        if (postsLoadedCount >= totalPosts){
            binding.loadingMoreProgress.setVisibility(View.GONE);
            return;
        }

        LinearLayout linearLayout = binding.info;
        List<Post> postList = new ArrayList<>(postMap.values());
        Collections.reverse(postList); // newest first

        int nextLimit = Math.min(postsLoadedCount + POSTS_PER_PAGE, totalPosts);

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                boolean arePicturesLoading = false;
                for (int i = postsLoadedCount; i < nextLimit; i++) {
                    if (!postList.get(i).isImageLoaded()) {
                        arePicturesLoading = true;
                        break;
                    }
                }

                if (arePicturesLoading && isLoadingFirebasePosts) {
                    new android.os.Handler().postDelayed(this, 500);
                } else {
                    for (int i = postsLoadedCount; i < nextLimit; i++) {
                        Post post = postList.get(i);
                        if (announcementsSwitch.isChecked()) {
                            if (post.isAnnouncement())
                                linearLayout.addView(post.getPostContainer());
                        } else {
                            linearLayout.addView(post.getPostContainer());
                        }
                    }

                    postsLoadedCount = nextLimit;
                    isLoading = false;
                    announcementsSwitch.setEnabled(true);
                    if (postsLoadedCount >= totalPosts) {
                        binding.loadingMoreProgress.setVisibility(View.GONE);
                    }
                }
            }
        }, 500);
    }

    private void populatePosts() {
        // Should only have one ongoing Firebase request at a time
        if(isLoadingFirebasePosts)
            return;
        else
            isLoadingFirebasePosts = true;

        DatabaseReference postsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("posts");

        DatabaseReference usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        postMap.clear();

        // Set a timeout, after which results will be dropped
        final Handler timeoutHandler = new Handler();
        boolean[] isTimeoutReached = {false};

        timeoutHandler.postDelayed(() -> {
            if (isLoadingFirebasePosts) {
                isTimeoutReached[0] = true;
                isLoadingFirebasePosts = false;
            }
        }, 10000);

        postsRef.orderByChild("date").get().addOnSuccessListener(snapshot -> {
            Log.d("FirebaseDebug", "Success! Post count: " + snapshot.getChildrenCount());
            if (isTimeoutReached[0]) return;
            // Cancel timeout if the request completes in time
            timeoutHandler.removeCallbacksAndMessages(null);

            if (!snapshot.hasChildren()) {
                isLoadingFirebasePosts = false;
                return;
            }

            List<DataSnapshot> snapshots = new ArrayList<>();
            for (DataSnapshot postSnap : snapshot.getChildren()) {
                snapshots.add(postSnap);
            }
            final int totalPosts = snapshots.size();
            int[] loadedCount = {0};

            // Fetch all posts
            for (DataSnapshot postSnap : snapshots) {
                String postId = postSnap.getKey();
                Long date = postSnap.child("date").getValue(Long.class);
                String description = postSnap.child("description").getValue(String.class);
                String imageUrl = postSnap.child("imageUrl").getValue(String.class);
                Integer likes = postSnap.child("likes").getValue(Integer.class);
                Map<String, Boolean> likedByMap = (Map<String, Boolean>) postSnap.child("likedBy").getValue();
                String userId = postSnap.child("userId").getValue(String.class);

                if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                    loadedCount[0]++;
                    continue;
                }

                // If finding the User from userId, add post to map
                usersRef.child(userId).get().addOnSuccessListener(userSnap -> {
                    User user = userSnap.getValue(User.class);
                    if (user != null) {
                        user.setId(userId);
                        Post post = new Post(RoverFeed.this, postId, date, user, description, imageUrl, likes, likedByMap, false);
                        postMap.put(post.getId(), post);
                    }
                    loadedCount[0]++;
                    if (loadedCount[0] == totalPosts) {
                        isLoadingFirebasePosts = false;
                    }
                }).addOnFailureListener(e -> {
                    loadedCount[0]++;
                    if (loadedCount[0] == totalPosts) {
                        isLoadingFirebasePosts = false;
                    }
                });
            }
        }).addOnFailureListener(e -> {
            Log.e("FirebaseDebug", "Failed to get posts", e);
            isLoadingFirebasePosts = false;
        });
    }

    private void refreshFeed() {
        disableUI();

        LinearLayout postLayout = binding.info;
        postLayout.removeAllViews();

        //TO_DO still show post if URL is empty from DB
        //TO_DO only load 10 at a time
        //TO_DO use RecyclerView instead of LinearLayout to save performance
        //TO_DO disable add posts when not online
        populatePosts();

        final int[] counter = {0};

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Increment counter each time the Runnable is executed
                counter[0]++;

                if ((isLoadingFirebasePosts) && counter[0] < 20) {
                    // Continue the recurrency, but stop after 20 iterations = 10s
                    new android.os.Handler().postDelayed(this, 500);
                } else {
                    // If timeout, go offline mode
                    if (counter[0] >= 20) {
                        Toast.makeText(requireContext(), "Displaying offline", Toast.LENGTH_LONG).show();
                        populateOfflinePosts();
                    }
                    // If posts are ready, sync them to offline in another thread
                    // TO_DO Maybe wait in refresh until this is done as well?
                    else {
                        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
                        new Thread(() -> {
                            syncPostsToLocalDB(requireContext(), postMap, localDB);
                        }).start();
                    }
                    if(!isLoadingFirebasePosts && !postMap.isEmpty()) {
                        postsLoadedCount = 0;
                        isLoading = true;
                        drawMorePosts();
                    }
                    finishRefresh();
                }
            }
        }, 500);
    }

    private void disableUI() {
        binding.buttonLogOut.setEnabled(false);
        announcementsSwitch.setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(false);
        }
    }

    private void finishRefresh() {
        binding.swipeRefresh.setRefreshing(false);
        binding.buttonLogOut.setEnabled(true);
        announcementsSwitch.setEnabled(true);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(true);
        }
    }

    private void syncPostsToLocalDB(Context context, Map<String, Post> postMap, LocalDatabase localDB) {
        activity.runOnUiThread(() -> {
            if (ImageUtils.getLoadedImageCount() >= ImageUtils.getTotalImageCount()) {
                ImageUtils.setLoadedImageCount(0);
                ImageUtils.setTotalImageCount(0);
            }
        });

        for (Post post : postMap.values()) {
            String userId = post.getUser().getId();
            String fileName = post.getId();
            String imageUrl = post.getImageUrl();

            activity.runOnUiThread(() -> {
                ImageUtils.incrementProgressBarMax();
            });

            ImageUtils.saveImageToInternalStorage(context, imageUrl, fileName, new ImageUtils.ImageSaveCallback() {
                @Override
                public void onSuccess(String imagePath) {
                    Log.d("LocalSync", "Image saved at: " + imagePath);
                    activity.runOnUiThread(() -> {
                        ImageUtils.incrementProgressBar();
                    });
                    localDB.insertPost(post.getId(), post.getDate(), post.getDescription(), imagePath, post.getLikes(), post.getLikedBy(), userId);
                }

                @Override
                public void onFailure(Exception e) {
                    activity.runOnUiThread(() -> {
                        ImageUtils.incrementProgressBar();
                    });
                    Log.e("LocalSync", "Failed to save image for post " + post.getId(), e);
                }
            });
        }
    }

    private void populateOfflinePosts () {
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        postMap = localDB.getAllOfflinePosts(RoverFeed.this, localDB.getAllUsers());
    }

    // Add empty space at the end of layout so "Add post" button is not resting on a post image, blocking it
    private View createEndView() {
        float density = requireContext().getResources().getDisplayMetrics().density;
        int dp = (int) (density);

        View endView = new View(requireContext());
        endView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100*dp)
        );

        return endView;
    }
}