package com.suici.roverhood;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

public class RoverFeed extends Fragment {

    private RoverFeedBinding binding;
    Map<String, Post> postMap = new LinkedHashMap<>();
    MainActivity activity;
    MenuItem announcementsFilter;
    SwitchCompat announcementsSwitch;
    private OnBackPressedCallback backCallback;
    private Runnable timeoutRunnable;

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

    private void populatePosts(Runnable onFinished) {
        DatabaseReference postsRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("posts");

        DatabaseReference usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        postMap.clear();

        postsRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.hasChildren()) {
                onFinished.run(); // No posts
                return;
            }

            AtomicInteger pending = new AtomicInteger((int) snapshot.getChildrenCount());

            for (DataSnapshot postSnap : snapshot.getChildren()) {
                Long date = postSnap.child("date").getValue(Long.class);
                String description = postSnap.child("description").getValue(String.class);
                String imageUrl = postSnap.child("imageUrl").getValue(String.class);
                Integer likes = postSnap.child("likes").getValue(Integer.class);
                String userId = postSnap.child("userId").getValue(String.class);
                String postId = postSnap.getKey();

                DataSnapshot likedBySnap = postSnap.child("likedBy");
                Set<String> likedBySet = new HashSet<>();
                for (DataSnapshot child : likedBySnap.getChildren()) {
                    likedBySet.add(child.getKey());
                }

                if (date == null || description == null || imageUrl == null || likes == null || userId == null) {
                    if (pending.decrementAndGet() == 0) onFinished.run();
                    continue;
                }

                usersRef.child(userId).get().addOnSuccessListener(userSnap -> {
                    User user = userSnap.getValue(User.class);
                    if (user != null) {
                        user.setId(userId);
                        Post post = new Post(RoverFeed.this,postId, date, user, description, imageUrl, likes,likedBySet);

                        postMap.put(post.id, post);
                    }

                    if (pending.decrementAndGet() == 0) {
                        onFinished.run();
                    }
                }).addOnFailureListener(e -> {
                    if (pending.decrementAndGet() == 0) onFinished.run();
                });
            }
        }).addOnFailureListener(e -> onFinished.run());
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

    // TO_DO Known bug, refresh w/o internet, activate internet, refresh again -> crash
    private void refreshFeed() {
        disableUI();

        LinearLayout postLayout = binding.getRoot().findViewById(R.id.info);
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;

        postLayout.removeAllViews();
        swipeRefreshLayout.setRefreshing(true);

        // Cancel any previous timeout
        if (timeoutRunnable != null) {
            binding.getRoot().removeCallbacks(timeoutRunnable);
        }

        // Define and schedule new timeout
        timeoutRunnable = () -> {
            if (swipeRefreshLayout.isRefreshing()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Timeout: Some images failed to load.", Toast.LENGTH_SHORT).show();
                    finishRefresh();
                });
            }
        };
        binding.getRoot().postDelayed(timeoutRunnable, 10_000);

        populatePosts(() -> {
            if (postMap.isEmpty()) {
                finishRefresh();
                return;
            }

            AtomicInteger loadedCount = new AtomicInteger(0);
            for (Post post : postMap.values()) {
                post.loadImageInto(post.getImageView(), () -> {
                    if (loadedCount.incrementAndGet() == postMap.size()) {
                        requireActivity().runOnUiThread(() -> {
                            binding.getRoot().removeCallbacks(timeoutRunnable);
                            drawPosts();
                            finishRefresh();
                        });
                    }
                });
            }
        });
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