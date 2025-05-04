package com.suici.roverhood;

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

import java.util.LinkedHashMap;
import java.util.Map;

public class RoverFeed extends Fragment {

    private RoverFeedBinding binding;
    Map<String, Post> postMap = new LinkedHashMap<>();
    MainActivity activity;
    MenuItem announcementsFilter;
    SwitchCompat announcementsSwitch;
    private OnBackPressedCallback backCallback;
    private boolean isLoadingFirebasePosts = false;

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
            activity.getFloatingButton().setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn)
            );
        }

        // Log-out - button logic
        LocalDatabase dbHelper = new LocalDatabase(requireContext());
        binding.buttonLogOut.setOnClickListener(v -> {
            dbHelper.markLoggedOut();
            activity.setCurrentUser(null);
            NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn);
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

    private void drawPosts() {
        LinearLayout linearLayout = binding.getRoot().findViewById(R.id.info);
        // TO_DO add proper filters

            for (Post post : postMap.values())
                if (announcementsSwitch.isChecked()) {
                    if (post.isAnnouncement())
                        linearLayout.addView(post.getPostContainer());
                }
                else
                    linearLayout.addView(post.getPostContainer());
            linearLayout.addView(createEndView());
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

        postsRef.get().addOnSuccessListener(snapshot -> {
            if (isTimeoutReached[0]) return;
            // Cancel timeout if the request completes in time
            timeoutHandler.removeCallbacksAndMessages(null);

            if (!snapshot.hasChildren()) {
                isLoadingFirebasePosts = false;
                return;
            }

            // Fetch all posts
            for (DataSnapshot postSnap : snapshot.getChildren()) {
                String postId = postSnap.getKey();
                Long date = postSnap.child("date").getValue(Long.class);
                String description = postSnap.child("description").getValue(String.class);
                String imageUrl = postSnap.child("imageUrl").getValue(String.class);
                Integer likes = postSnap.child("likes").getValue(Integer.class);
                Map<String, Boolean> likedByMap = (Map<String, Boolean>) postSnap.child("likedBy").getValue();
                String userId = postSnap.child("userId").getValue(String.class);

                if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                    continue;
                }

                // If finding the User from userId, add post to map
                usersRef.child(userId).get().addOnSuccessListener(userSnap -> {
                    User user = userSnap.getValue(User.class);
                    if (user != null) {
                        user.setId(userId);
                        Post post = new Post(RoverFeed.this,postId, date, user, description, imageUrl, likes, likedByMap, false);

                        postMap.put(post.getId(), post);
                    }
                });
            }
            isLoadingFirebasePosts = false;
        }).addOnFailureListener(e -> {
            isLoadingFirebasePosts = false;
        });
    }

    private void refreshFeed() {
        disableUI();

        LinearLayout postLayout = binding.getRoot().findViewById(R.id.info);
        postLayout.removeAllViews();

        populatePosts();

        final int[] counter = {0};

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Increment counter each time the Runnable is executed
                counter[0]++;

                boolean arePicturesLoading = false;
                for (Post post : postMap.values()) {
                    if (!post.isImageLoaded()) {
                        arePicturesLoading = true;
                        break;
                    }
                }
                if (postMap.isEmpty())
                    arePicturesLoading = true;

                if ((isLoadingFirebasePosts || arePicturesLoading) && counter[0] < 20) {
                    // Continue the recurrency, but stop after 20 iterations = 10s
                    new android.os.Handler().postDelayed(this, 500);
                } else {
                    // If timeout, go offline mode
                    if (counter[0] >= 20) {
                        Toast.makeText(requireContext(), "Displaying offline", Toast.LENGTH_LONG).show();
                        populateOfflinePosts();
                    }
                    // If posts are ready, sync them to offline
                    else {
                        syncPostsToLocalDB();
                    }
                    drawPosts();
                    finishRefresh();
                }
            }
        }, 500);  // Initial delay
    }

    private void disableUI() {
        binding.buttonLogOut.setEnabled(false);
        announcementsSwitch.setEnabled(false);
        activity.getFloatingButton().setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
    }

    private void finishRefresh() {
        binding.swipeRefresh.setRefreshing(false);
        binding.buttonLogOut.setEnabled(true);
        announcementsSwitch.setEnabled(true);
        activity.getFloatingButton().setEnabled(true);
    }

    private void syncPostsToLocalDB() {
        LocalDatabase localDB = new LocalDatabase(requireContext());

        activity.updateProgressBar(0,postMap.size());
        ImageUtils.setLoadedImageCount(0);
        ImageUtils.setTotalImageCount(postMap.size());

        for (Post post : postMap.values()) {
            // Save the image to internal storage
            String imagePath = ImageUtils.saveImageToInternalStorage(requireContext(), post.getImageUrl(), post.getId());
            Log.d("ImageUtils", "Image saved at: " + imagePath);

            // Save the post with imagePath to local DB
            String userId = post.getUser().getId();
            localDB.insertPost(post.getId(), post.getDate(), post.getDescription(), imagePath, post.getLikes(), post.getLikedBy(), userId);
        }
    }

    private void populateOfflinePosts () {
        LocalDatabase localDB = new LocalDatabase(requireContext());
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