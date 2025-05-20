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

import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.databinding.FragmentRoverFeedBinding;
import com.suici.roverhood.dialogs.AddPost;
import com.suici.roverhood.dialogs.FilterSelector;
import com.suici.roverhood.models.Filters;
import com.suici.roverhood.models.Post;
import com.suici.roverhood.models.PostAdapter;
import com.suici.roverhood.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoverFeed extends Fragment {
    private FragmentRoverFeedBinding binding;
    private MainActivity activity;
    private MenuItem filterButton;
    private OnBackPressedCallback backCallback;
    private boolean offlineMode = false;

    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 10;
    private final int PREFETCH_THRESHOLD = 5;
    private boolean isLoading = true;

    private PostAdapter postAdapter;
    RecyclerView recyclerView;
    private List<Post> visiblePostList = new ArrayList<>();
    private List<Post> allPostList = new ArrayList<>();

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
                    Filters.resetFilters();
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
        postAdapter = new PostAdapter(getContext(), visiblePostList);
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

        // Refresh on bottom
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                if (!isLoading && linearLayoutManager != null
                        && linearLayoutManager.findLastCompletelyVisibleItemPosition()
                        >= visiblePostList.size() - PREFETCH_THRESHOLD) {
                    if (!firebaseRepository.isLoading() && !binding.swipeRefresh.isRefreshing()) {
                        isLoading = true;
                        waitThenDrawPosts();
                    }
                }
            }
        });

        // Filters - all buttons logic
        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                filterButton = optionsMenu.findItem(R.id.filters);
                if (filterButton != null) {
                    filterButton.setVisible(true);
                }
            }
        });

        binding.clearFiltersButton.setOnClickListener(v -> {
            Filters.resetFilters();
            Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show();
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        binding.username.setOnClickListener(v -> {
            Filters.resetFilters();
            Filters.setUsername(activity.getCurrentUser().getUsername());
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        binding.team.setOnClickListener(v -> {
            Filters.resetFilters();
            Filters.setTeam(activity.getCurrentUser().getTeam());
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
        Filters.resetFilters();
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
        filterSelectorFragment.setOriginalFeed(this);
        filterSelectorFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
    }

    private void waitThenDrawPosts() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int totalPosts = allPostList.size();
                if (firebaseRepository.isLoading() && (totalPosts<POSTS_PER_PAGE + postsLoadedCount)) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    List<Post> filteredList = Filters.filterPosts(allPostList, activity);
                    Collections.reverse(filteredList);

                    //Sync current posts
                    if (!offlineMode) {
                        int startIndex = Math.min(postsLoadedCount, filteredList.size());
                        int endIndex = Math.min(postsLoadedCount + POSTS_PER_PAGE, filteredList.size());
                        List<Post> sublist = filteredList.subList(startIndex, endIndex);

                        new Thread(() -> { firebaseRepository.syncPostsToLocalDB(sublist); }).start();
                    }

                    drawMorePosts(filteredList);
                }
            }
        }, 100);
    }

    private void drawMorePosts(List<Post> filteredList) {
        if(filterButton != null)
            filterButton.setEnabled(false);
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
        if(filterButton != null)
            filterButton.setEnabled(true);
        binding.buttonLogOut.setEnabled(true);
    }

    public void refreshFeed() {
        startRefreshUI();

        postAdapter.detachAllViews(recyclerView);
        visiblePostList.clear();
        postAdapter.notifyDataSetChanged();

        User currentUser = activity.getCurrentUser();

        firebaseRepository.loadPosts(offlineMode, currentUser.isOfflineUser(), new FirebaseRepository.PostRepositoryCallback() {
            @Override
            public void onPostsLoaded(List<Post> posts, boolean isOffline) {
                if (isOffline) {
                    if(!offlineMode) {
                        Toast.makeText(requireContext(), "Offline Mode", Toast.LENGTH_SHORT).show();
                        binding.offlineLabel.setVisibility(View.VISIBLE);
                    }

                    Log.d("PostDebug", "Loaded in Offline Mode " + String.valueOf(posts.size()));
                } else {
                    if(offlineMode) {
                        Toast.makeText(requireContext(), "Online Mode", Toast.LENGTH_SHORT).show();
                        binding.offlineLabel.setVisibility(View.GONE);
                    }
                    firebaseRepository.removeUnusedTopics(posts);
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
                if (firebaseRepository.isLoading() && counter[0] < 20) {
                    new android.os.Handler().postDelayed(this, 500);
                } else {
                    if(!firebaseRepository.isLoading()) {
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
        if(filterButton != null)
            filterButton.setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(false);
        }
        postAdapter.setLoading(false);

        if (Filters.areFiltersOrSortEnabled()) {
            binding.filtersLayout.setVisibility(View.VISIBLE);
            binding.filterList.setText(Filters.getFiltersText());
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

    public void removePostFromUI(Post post) {
        int index = visiblePostList.indexOf(post);
        if (index != -1) {
            visiblePostList.remove(index);
            postAdapter.notifyItemRemoved(index);
        }
    }

    public void updatePostInUI(Post post) {
        int index = visiblePostList.indexOf(post);
        if (index != -1) {
            visiblePostList.set(index, post);
            postAdapter.notifyItemChanged(index);
        }
    }

    public void addPostToUI(Post post) {
        if (Filters.isVisibleAfterFilter(post, activity)) {
            int index = visiblePostList.indexOf(post);

            if (index == -1 && (!binding.swipeRefresh.isRefreshing() && !isLoading)) {
                if (Filters.isOrderAscending()) {
                    visiblePostList.add(post);
                    postAdapter.notifyItemInserted(visiblePostList.size() - 1);
                    recyclerView.scrollToPosition(visiblePostList.size()-1);
                } else {
                    visiblePostList.add(0, post);
                    postAdapter.notifyItemInserted(0);
                    recyclerView.scrollToPosition(0);
                }
            }
        }
    }

    public boolean isLoading() { return isLoading; }
}