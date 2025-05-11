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
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private Map<String, Post> postMap = new LinkedHashMap<>();
    private MainActivity activity;
    private MenuItem announcementsFilter;
    private SwitchCompat announcementsSwitch;
    private OnBackPressedCallback backCallback;
    private boolean isLoadingFirebasePosts = false;
    private boolean offlineMode = false;

    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 5;
    private boolean isLoading = true;

    private PostAdapter postAdapter;
    private List<Post> postList = new ArrayList<>();

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

                if(!isLoading)
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

        // Initialize RecyclerView
        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        postAdapter = new PostAdapter(getContext(), postList);
        recyclerView.setAdapter(postAdapter);

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
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                if (!isLoading && linearLayoutManager != null && linearLayoutManager.findLastCompletelyVisibleItemPosition() == postList.size()) {
                    if (!isLoadingFirebasePosts && !binding.swipeRefresh.isRefreshing()) {
                        isLoading = true;
                        waitThenDrawPosts();
                    }
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

    private void waitThenDrawPosts() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int totalPosts = postMap.size(); // add the filter logic here
                if (isLoadingFirebasePosts && (totalPosts<POSTS_PER_PAGE + postsLoadedCount)) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    // Filter List - TO_DO Implement fully
                    List<Post> filteredList = filterPosts(new ArrayList<>(postMap.values()));
                    Collections.reverse(filteredList);

                    //Sync current posts
                    if (!offlineMode) {
                        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());

                        int endIndex = Math.min(postsLoadedCount + POSTS_PER_PAGE, filteredList.size());
                        List<Post> sublist = filteredList.subList(postsLoadedCount, endIndex);

                        new Thread(() -> {
                            syncPostsToLocalDB(requireContext(), sublist, localDB);
                        }).start();
                    }

                    drawMorePosts(filteredList);
                }
            }
        }, 100);
    }

    private List<Post> filterPosts(List<Post> allPosts) {
        List<Post> filtered = new ArrayList<>();

        for (Post post : allPosts) {
            if (announcementsSwitch.isChecked()) {
                if (post.isAnnouncement()) {
                    filtered.add(post);
                }
            }
            else {
                filtered.add(post);
            }

        }

        return filtered;
    }

    private void drawMorePosts(List<Post> filteredList) {
        announcementsSwitch.setEnabled(false);

        int totalPosts = filteredList.size();
        if (postsLoadedCount >= totalPosts){
            postAdapter.setLoading(false);
            announcementsSwitch.setEnabled(true);
            return;
        }

        int nextLimit = Math.min(postsLoadedCount + POSTS_PER_PAGE, totalPosts);

        for (int i = postsLoadedCount; i < nextLimit; i++) {
            filteredList.get(i).createImage();
        }

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                boolean arePicturesLoading = false;
                for (int i = postsLoadedCount; i < nextLimit && i < filteredList.size(); i++) {
                    if (!filteredList.get(i).isImageLoaded()) {
                        arePicturesLoading = true;
                        break;
                    }
                }

                if (arePicturesLoading) {
                    new android.os.Handler().postDelayed(this, 400);
                } else {
                    Log.d("PostLoadingDebug", "Success! Toate postarile sunt loaded: " + String.valueOf(arePicturesLoading));
                    if (postsLoadedCount == 0 && nextLimit > 0) {
                        postList.clear();
                        postList.addAll(filteredList.subList(0, nextLimit));
                        postAdapter.notifyDataSetChanged();
                    }
                    else {
                        for (int i = postsLoadedCount; i < nextLimit && i < filteredList.size(); i++) {
                            Post post = filteredList.get(i);
                            postList.add(post);
                            postAdapter.notifyItemInserted(postList.size() - 1);
                        }
                    }

                    postsLoadedCount = nextLimit;
                    isLoading = false;
                    if (postsLoadedCount >= totalPosts) {
                        postAdapter.setLoading(false);
                    }
                    binding.buttonLogOut.setEnabled(true);
                    announcementsSwitch.setEnabled(true);
                }
            }
        }, 400);
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
                        offlineMode = false;
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

        postList.clear();
        postAdapter.notifyDataSetChanged();

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
                        offlineMode = true;
                    }
                    if(!isLoadingFirebasePosts && !postMap.isEmpty()) {
                        postsLoadedCount = 0;
                        isLoading = true;
                        waitThenDrawPosts();
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
        postAdapter.setLoading(false);
    }

    private void finishRefresh() {
        binding.swipeRefresh.setRefreshing(false);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(true);
        }
        postAdapter.setLoading(true);
    }

    private void syncPostsToLocalDB(Context context, List<Post> postsToSync, LocalDatabase localDB) {
        activity.runOnUiThread(() -> {
            if (ImageUtils.getLoadedImageCount() >= ImageUtils.getTotalImageCount()) {
                ImageUtils.setLoadedImageCount(0);
                ImageUtils.setTotalImageCount(0);
            }
        });

        for (Post post : postsToSync) {
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
}