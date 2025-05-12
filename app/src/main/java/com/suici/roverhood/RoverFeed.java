package com.suici.roverhood;

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

import com.suici.roverhood.databinding.RoverFeedBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoverFeed extends Fragment {
    private RoverFeedBinding binding;
    private MainActivity activity;
    private MenuItem filterButton;
    private OnBackPressedCallback backCallback;
    private boolean offlineMode = false;

    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 10;
    private final int PREFETCH_THRESHOLD = 5;
    private boolean isLoading = true;

    private PostAdapter postAdapter;
    private List<Post> visiblePostList = new ArrayList<>();
    private List<Post> allPostList = new ArrayList<>();

    private PostRepository postRepository;

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
        postRepository = new PostRepository(requireContext());

        // Initialize RecyclerView
        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        postAdapter = new PostAdapter(getContext(), visiblePostList);
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

                if (!isLoading && linearLayoutManager != null
                        && linearLayoutManager.findLastCompletelyVisibleItemPosition()
                        >= visiblePostList.size() - PREFETCH_THRESHOLD) {
                    if (!postRepository.isLoading() && !binding.swipeRefresh.isRefreshing()) {
                        isLoading = true;
                        waitThenDrawPosts();
                    }
                }
            }
        });

        // Filters - button logic
        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                filterButton = optionsMenu.findItem(R.id.filters);
                if (filterButton != null) {
                    filterButton.setVisible(true);
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

        // Refresh feed for the first load
        FilterOptions.resetFilters();
        refreshFeed();
    }

    @Override
    public void onDestroyView() {
        if (filterButton != null) {
            filterButton.setVisible(false);
            filterButton = null;
        }

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
        filterSelectorFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
    }

    private List<Post> filterPosts(List<Post> allPosts) {
        List<Post> filtered = new ArrayList<>();

        for (Post post : allPosts) {
//            if (announcementsSwitch.isChecked()) {
//                if (post.isAnnouncement()) {
//                    filtered.add(post);
//                }
//            }
//            else {
                filtered.add(post);
//            }
        }

        return filtered;
    }

    private void waitThenDrawPosts() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int totalPosts = allPostList.size();
                if (postRepository.isLoading() && (totalPosts<POSTS_PER_PAGE + postsLoadedCount)) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    // Filter List - TO_DO Implement fully
                    List<Post> filteredList = filterPosts(allPostList);
                    Collections.reverse(filteredList);

                    //Sync current posts
                    if (!offlineMode) {
                        int endIndex = Math.min(postsLoadedCount + POSTS_PER_PAGE, filteredList.size());
                        List<Post> sublist = filteredList.subList(postsLoadedCount, endIndex);

                        new Thread(() -> { postRepository.syncPostsToLocalDB(sublist); }).start();
                    }

                    drawMorePosts(filteredList);
                }
            }
        }, 100);
    }

    private void drawMorePosts(List<Post> filteredList) {
        //announcementsSwitch.setEnabled(false);

        int totalPosts = filteredList.size();
        if (postsLoadedCount >= totalPosts){
            postAdapter.setLoading(false);
            finishDrawUI();
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
                    if(binding.swipeRefresh.isRefreshing()) {
                        finishDrawUI();
                        return;
                    }
                    // First batch of posts will refresh the list
                    if (postsLoadedCount == 0 && nextLimit > 0) {
                        visiblePostList.clear();
                        visiblePostList.addAll(filteredList.subList(0, nextLimit));
                        postAdapter.notifyDataSetChanged();
                    }
                    // Next ones will just add to existing list
                    else {
                        for (int i = postsLoadedCount; i < nextLimit && i < filteredList.size(); i++) {
                            Post post = filteredList.get(i);
                            visiblePostList.add(post);
                            postAdapter.notifyItemInserted(visiblePostList.size() - 1);
                        }
                    }

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
        //announcementsSwitch.setEnabled(true);
        binding.buttonLogOut.setEnabled(true);
    }

    private void refreshFeed() {
        startRefreshUI();

        visiblePostList.clear();
        postAdapter.notifyDataSetChanged();

        postRepository.loadPosts(new PostRepository.PostRepositoryCallback() {
            @Override
            public void onPostsLoaded(List<Post> posts, boolean isOffline) {
                if (isOffline) {
                    Toast.makeText(requireContext(), "Offline Mode", Toast.LENGTH_LONG).show();
                    Log.d("PostDebug", "Loaded in Offline Mode " + String.valueOf(posts.size()));
                } else {
                    Log.d("PostDebug", "Loaded in Online Mode " + String.valueOf(posts.size()));
                }

                offlineMode = isOffline;
                allPostList.clear();
                allPostList.addAll(posts);

                for (Post post : allPostList) {
                    post.setFragment(RoverFeed.this);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        final int[] counter = {0};

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                counter[0]++;
                // Continue the recurrency, but stop after 20 iterations = 10s
                if (postRepository.isLoading() && counter[0] < 20) {
                    new android.os.Handler().postDelayed(this, 500);
                } else {
                    if(!postRepository.isLoading()) {
                        postsLoadedCount = 0;
                        isLoading = true;
                        waitThenDrawPosts();
                    }

                    finishRefreshUI();
                }
            }
        }, 500);
    }

    private void startRefreshUI() {
        binding.buttonLogOut.setEnabled(false);
        //announcementsSwitch.setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(false);
        }
        postAdapter.setLoading(false);
    }

    private void finishRefreshUI() {
        binding.swipeRefresh.setRefreshing(false);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(true);
        }
        postAdapter.setLoading(true);
    }
}