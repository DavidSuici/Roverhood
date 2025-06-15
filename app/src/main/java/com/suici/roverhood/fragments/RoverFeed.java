package com.suici.roverhood.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.databinding.FragmentRoverFeedBinding;
import com.suici.roverhood.dialogs.AddPost;
import com.suici.roverhood.dialogs.FilterSelector;
import com.suici.roverhood.utils.FiltersManager;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.presentation.PostAdapter;
import com.suici.roverhood.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoverFeed extends Fragment {
    private FragmentRoverFeedBinding binding;
    private MainActivity activity;
    private MenuItem filterButton;
    private MenuItem likedPostsButton;
    private MenuItem announcementsButton;
    private OnBackPressedCallback backCallback;
    private boolean offlineMode = false;

    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 10;
    private final int PREFETCH_THRESHOLD = 5;
    private boolean isLoading = true;

    private PostAdapter postAdapter;
    private RecyclerView recyclerView;
    private List<PostHandler> visiblePostHandlerList = new ArrayList<>();
    private List<PostHandler> allPostHandlerList = new ArrayList<>();

    private FirebaseRepository firebaseRepository;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentRoverFeedBinding.inflate(inflater, container, false);
        activity = (MainActivity) requireActivity();

        // Refresh on back pressed when on feed, but keep the general back pressed logic as well
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);

                if(!binding.swipeRefresh.isRefreshing() && !isLoading) {
                    FiltersManager.resetFilters();
                    refreshFeed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(requireActivity(), backCallback);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activity = (MainActivity) requireActivity();
        firebaseRepository = FirebaseRepository.getInstance(requireContext());

        // Initialize RecyclerView
        recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        postAdapter = new PostAdapter(getContext(), visiblePostHandlerList);
        recyclerView.setAdapter(postAdapter);

        // Add post - button logic
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
            activity.getFloatingButton().setOnClickListener(v -> {
                    AddPost addPostFragment = new AddPost();
                    addPostFragment.setOriginalFeed(this);
                    addPostFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
            });
        }

        // Log-out - button logic
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        binding.buttonLogOut.setOnClickListener(v -> {
            if(!binding.swipeRefresh.isRefreshing() && !isLoading){
                localDB.markLoggedOut();
                activity.setCurrentUser(null);
                NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn);
            }
        });

        // Add more posts on reaching the bottom of the feed
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                if (!isLoading && linearLayoutManager != null
                        && linearLayoutManager.findLastCompletelyVisibleItemPosition()
                        >= visiblePostHandlerList.size() - PREFETCH_THRESHOLD) {
                    if (!firebaseRepository.isLoading() && !binding.swipeRefresh.isRefreshing()) {
                        isLoading = true;
                        waitThenDrawPosts();
                    }
                }
            }
        });

        // Filters - finding all buttons and adding their logic
        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                filterButton = optionsMenu.findItem(R.id.filters);
                likedPostsButton = optionsMenu.findItem(R.id.likedPosts);
                announcementsButton = optionsMenu.findItem(R.id.announcements);
            }
        });

        binding.clearFiltersButton.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show();
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        binding.username.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setUsername(activity.getCurrentUser().getUsername());
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        binding.team.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setTeam(activity.getCurrentUser().getTeam());
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        // User information - cosmetic logic
        if (activity.getCurrentUser() != null) {
            binding.username.setText(activity.getCurrentUser().getUsername());
            binding.team.setText(activity.getCurrentUser().getTeam());
            binding.usernameLabel.setText(activity.getCurrentUser().getUserType());
        }

        // Refresh - cosmetic logic
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;
        swipeRefreshLayout.setProgressViewOffset(true, 100, 250);
        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);

        // Refresh feed for the first load
        binding.recyclerView.requestFocus();
        FiltersManager.resetFilters();
        refreshFeed();
    }

    @Override
    public void onDestroyView() {
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.INVISIBLE);
        }

        if (backCallback != null)
            backCallback.remove();

        super.onDestroyView();
        binding = null;
    }

    public void openFiltersDialog() {
        FilterSelector filterSelectorFragment = new FilterSelector();
        filterSelectorFragment.setOriginalFeed(this);
        filterSelectorFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
    }

    public void applyLikedPostsFilter() {
        if(!binding.swipeRefresh.isRefreshing() && !isLoading) {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setOnlyLiked(true);
            refreshFeed();
        }
    }

    public void applyAnnouncementsFilter() {
        if(!binding.swipeRefresh.isRefreshing() && !isLoading) {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setAnnouncementsOnly(true);
            refreshFeed();
        }
    }

    public void refreshFeedAndFilters() {
        if(!binding.swipeRefresh.isRefreshing() && !isLoading) {
            FiltersManager.resetFilters();
            refreshFeed();
        }
    }

    // Waits if posts are still loading, then filters the list and displays them
    private void waitThenDrawPosts() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (firebaseRepository.isLoading()) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    List<PostHandler> filteredList = FiltersManager.filterPosts(allPostHandlerList, activity);
                    Collections.reverse(filteredList);

                    // Save locally the last loaded batch of posts
                    if (!offlineMode) {
                        int startIndex = Math.min(postsLoadedCount, filteredList.size());
                        int endIndex = Math.min(postsLoadedCount + POSTS_PER_PAGE, filteredList.size());
                        List<PostHandler> sublist = filteredList.subList(startIndex, endIndex);

                        new Thread(() -> { firebaseRepository.savePostsToLocalDB(sublist); }).start();
                    }

                    drawMorePosts(filteredList);
                }
            }
        }, 100);
    }

    // Displays the next batch of posts when their image is done loading
    private void drawMorePosts(List<PostHandler> filteredList) {
        if(filterButton != null)
            filterButton.setEnabled(false);

        // Finish if there are no more posts to display
        int totalPosts = filteredList.size();
        if (postsLoadedCount >= totalPosts){
            postAdapter.setLoading(false);
            finishDrawUI();
            return;
        }

        int nextLimit = Math.min(postsLoadedCount + POSTS_PER_PAGE, totalPosts);

        // Start loading the image for the next batch of posts
        for (int i = postsLoadedCount; i < nextLimit; i++) {
            filteredList.get(i).prepareImageView();
        }

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                // Wait for all posts to have their image loaded
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
                    if(binding.swipeRefresh.isRefreshing()) {
                        finishDrawUI();
                        return;
                    }

                    // First batch of posts will refresh the visible posts list
                    if (postsLoadedCount == 0 && nextLimit > 0) {
                        visiblePostHandlerList.clear();
                        visiblePostHandlerList.addAll(filteredList.subList(0, nextLimit));
                        postAdapter.notifyDataSetChanged();
                    }
                    // Next ones will just add to the existing list
                    else {
                        for (int i = postsLoadedCount; i < nextLimit && i < filteredList.size(); i++) {
                            PostHandler postHandler = filteredList.get(i);
                            visiblePostHandlerList.add(postHandler);
                            postAdapter.notifyItemInserted(visiblePostHandlerList.size() - 1);
                        }
                    }

                    // When all posts have been loaded, disable the spinning loading at the end of the feed
                    postsLoadedCount = nextLimit;
                    if (postsLoadedCount >= totalPosts) {
                        postAdapter.setLoading(false);
                    }

                    finishDrawUI();
                }
            }
        }, 400);
    }

    private void finishDrawUI() {
        isLoading = false;
        if(filterButton != null)
            filterButton.setEnabled(true);
        binding.buttonLogOut.setEnabled(true);
    }

    // Main refresh logic, triggered by pulling down from the top of
    // the feed or pressing back while on the feed.
    public void refreshFeed() {
        startRefreshUI();

        // Update the RecyclerView-related information
        postAdapter.detachAllViews(recyclerView);
        visiblePostHandlerList.clear();
        postAdapter.notifyDataSetChanged();

        User currentUser = activity.getCurrentUser();

        firebaseRepository.loadPosts(offlineMode, currentUser.isOfflineUser(), new FirebaseRepository.PostRepositoryCallback() {
            @Override
            public void onPostsLoaded(List<PostHandler> postHandlers, boolean isOffline) {
                // Change the mode from offline to online and vice-versa based on
                // the callback from loading the posts from Firebase
                if (isOffline) {
                    if(!offlineMode) {
                        Toast.makeText(requireContext(), "Offline Mode", Toast.LENGTH_SHORT).show();
                        binding.offlineLabel.setVisibility(View.VISIBLE);
                    }
                    Log.d("PostDebug", "Loaded in Offline Mode " + String.valueOf(postHandlers.size()));
                } else {
                    if(offlineMode) {
                        Toast.makeText(requireContext(), "Online Mode", Toast.LENGTH_SHORT).show();
                        binding.offlineLabel.setVisibility(View.GONE);
                    }
                    firebaseRepository.deleteUnusedTopics(postHandlers);
                    Log.d("PostDebug", "Loaded in Online Mode " + String.valueOf(postHandlers.size()));
                }

                offlineMode = isOffline;
                allPostHandlerList.clear();
                allPostHandlerList.addAll(postHandlers);

                for (PostHandler postHandler : allPostHandlerList) {
                    postHandler.setFragment(RoverFeed.this);
                }
            }
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        // Wait until the Firebase is done loading and after that start
        // loading the first batch of posts
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                if (firebaseRepository.isLoading()) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    postsLoadedCount = 0;
                    isLoading = true;
                    waitThenDrawPosts();
                    finishRefreshUI();
                }
            }
        }, 500);
    }

    private void startRefreshUI() {
        binding.buttonLogOut.setEnabled(false);
        if(filterButton != null)
            filterButton.setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(false);
        }
        postAdapter.setLoading(false);

        if (FiltersManager.areFiltersOrSortEnabled()) {
            binding.filtersLayout.setVisibility(View.VISIBLE);
            binding.filterList.setText(FiltersManager.getFiltersText());
        }
        else
            binding.filtersLayout.setVisibility(View.GONE);
    }

    private void finishRefreshUI() {
        binding.swipeRefresh.setRefreshing(false);
        if (activity.getFloatingButton() != null && !offlineMode) {
            activity.getFloatingButton().setEnabled(true);
        }
        postAdapter.setLoading(true);
    }

    public void removePostFromUI(PostHandler postHandler) {
        int index = visiblePostHandlerList.indexOf(postHandler);
        if (index != -1) {
            visiblePostHandlerList.remove(index);
            postAdapter.notifyItemRemoved(index);
        }
    }

    public void updatePostInUI(PostHandler postHandler) {
        int index = visiblePostHandlerList.indexOf(postHandler);
        if (index != -1) {
            visiblePostHandlerList.set(index, postHandler);
            postAdapter.notifyItemChanged(index);
        }
    }

    public void addPostToUI(PostHandler postHandler) {
        if (FiltersManager.isVisibleAfterFilter(postHandler, activity)) {
            int index = visiblePostHandlerList.indexOf(postHandler);

            if (index == -1 && (!binding.swipeRefresh.isRefreshing() && !isLoading)) {
                if (FiltersManager.getActiveFilters().isOrderAscending()) {
                    visiblePostHandlerList.add(postHandler);
                    postAdapter.notifyItemInserted(visiblePostHandlerList.size() - 1);
                    recyclerView.scrollToPosition(visiblePostHandlerList.size()-1);
                } else {
                    visiblePostHandlerList.add(0, postHandler);
                    postAdapter.notifyItemInserted(0);
                    recyclerView.scrollToPosition(0);
                }
            }
        }
    }

    public boolean isLoading() { return isLoading; }
}