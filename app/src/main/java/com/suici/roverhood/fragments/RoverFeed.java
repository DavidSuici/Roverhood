package com.suici.roverhood.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;
import com.suici.roverhood.dialogs.EditProfile;
import com.suici.roverhood.dialogs.ActivationCodes;
import com.suici.roverhood.dialogs.ManageUsers;
import com.suici.roverhood.dialogs.ManageTeams;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
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
import com.suici.roverhood.dialogs.AddAnnouncement;
import com.suici.roverhood.dialogs.AddPost;
import com.suici.roverhood.dialogs.FilterSelector;
import com.suici.roverhood.utils.FiltersManager;
import com.suici.roverhood.utils.NetworkStatus;
import com.suici.roverhood.utils.NotificationTarget;
import com.suici.roverhood.dialogs.Comments;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.presentation.PostAdapter;
import com.suici.roverhood.models.Post;
import com.suici.roverhood.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoverFeed extends Fragment {
    private FragmentRoverFeedBinding binding;
    private MainActivity activity;
    private MenuItem filterButton;
    private MenuItem announcementsButton;
    private OnBackPressedCallback backCallback;
    private boolean offlineMode = false;

    private int postsLoadedCount = 0;
    private final int POSTS_PER_PAGE = 10;
    private final int PREFETCH_THRESHOLD = 5;
    private boolean isLoading = true;
    private boolean bannerHidden = false;
    private NotificationTarget notificationTarget = null; // set when opened from a notification

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
        postAdapter.setHeaderBinder(this::bindHeaderPanels);
        postAdapter.setOnAnnouncementSeen(this::updateAnnouncementsBadge);
        recyclerView.setAdapter(postAdapter);

        // Add post - button logic
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
            if ("PARTICIPANT".equalsIgnoreCase(activity.getCurrentUser().getUserType())) {
                activity.getFloatingButton().setOnClickListener(v -> {
                    AddPost addPostFragment = new AddPost();
                    addPostFragment.setOriginalFeed(this);
                    addPostFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
                });
            }
            else {
                activity.getFloatingButton().setOnClickListener(v -> activity.toggleFabMenu());
                activity.getFabPost().setOnClickListener(v -> {
                    AddPost addPostFragment = new AddPost();
                    addPostFragment.setOriginalFeed(this);
                    addPostFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
                    activity.hideFabMenu();
                });

                activity.getFabCodes().setOnClickListener(v -> {
                    ActivationCodes.newInstance(offlineMode).show(activity.getSupportFragmentManager(), "activationCodes");
                    activity.hideFabMenu();
                });

                activity.getFabUsers().setOnClickListener(v -> {
                    new ManageUsers(this).show(activity.getSupportFragmentManager(), "manageUsers");
                    activity.hideFabMenu();
                });

                activity.getFabTeams().setOnClickListener(v -> {
                    new ManageTeams(this).show(activity.getSupportFragmentManager(), "manageTeams");
                    activity.hideFabMenu();
                });

                activity.getFabReports().setOnClickListener(v -> {
                    new com.suici.roverhood.dialogs.ManageReports(this).show(activity.getSupportFragmentManager(), "manageReports");
                    activity.hideFabMenu();
                });

                activity.getFabAnnouncement().setOnClickListener(v -> {
                    AddAnnouncement addAnnouncementFragment = new AddAnnouncement();
                    addAnnouncementFragment.setOriginalFeed(this);
                    addAnnouncementFragment.show(activity.getSupportFragmentManager(), "addAnnouncementFragment");
                    activity.hideFabMenu();
                });
            }
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
                        >= visiblePostHandlerList.size() + 1 - PREFETCH_THRESHOLD) {
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
                announcementsButton = optionsMenu.findItem(R.id.announcements);
            }
        });

        binding.clearFiltersButton.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show();
            if(!binding.swipeRefresh.isRefreshing() && !isLoading)
                refreshFeed();
        });

        // Hide Banner Logic
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                View banner = binding.banner;
                View filters = binding.filtersLayout;

                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                int firstVisibleItemPosition = 0;

                if (layoutManager instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                    firstVisibleItemPosition = ((androidx.recyclerview.widget.LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                }

                if (firstVisibleItemPosition == 0 && bannerHidden) {
                    banner.animate().translationY(0).setDuration(100).start();
                    filters.animate().translationY(0).setDuration(100).start();
                    bannerHidden = false;
                    return;
                }

                if (dy > 25 && !bannerHidden) {
                    if (banner.getTranslationY() == 0) {
                        banner.animate().translationY(-banner.getHeight()).setDuration(100).start();
                        filters.animate().translationY(-banner.getHeight()).setDuration(100).start();
                        bannerHidden = true;
                    }
                } else if (dy < -25 && bannerHidden) {
                    if (banner.getTranslationY() != 0) {
                        banner.animate().translationY(0).setDuration(100).start();
                        filters.animate().translationY(0).setDuration(100).start();
                        bannerHidden = false;
                    }
                }
            }
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

            binding.team.setText(Teams.label(activity.getCurrentUser().getTeam()));
            binding.usernameLabel.setText(activity.getCurrentUser().getUserType());
        }
        bindBannerPhoto();

        // Refresh - cosmetic logic
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;
        swipeRefreshLayout.setProgressViewOffset(true, 100, 250);
        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);

        // Refresh feed for the first load. If the app was opened from a notification,
        // start in that notification's filter, focused on its post.
        // The list keeps focus itself, so it isn't handed to the first clickable view
        // (the first post's name), which would show it highlighted as if selected
        binding.recyclerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        binding.recyclerView.setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            binding.recyclerView.setDefaultFocusHighlightEnabled(false);
        }
        binding.recyclerView.requestFocus();
        FiltersManager.resetFilters();
        NotificationTarget target = activity.consumePendingNotificationTarget();
        if (target != null) {
            notificationTarget = target;
            applyNotificationFilter(target);
        }
        refreshFeed();
    }

    // Filters used from other places (comments, likes list, team panel). Ignored while the
    // feed is refreshing, same as tapping a username or team on a post.
    public void filterByUser(String username) {
        if (binding == null || binding.swipeRefresh.isRefreshing() || isLoading) return;
        FiltersManager.resetFilters();
        FiltersManager.getActiveFilters().setUsername(username);
        refreshFeed();
    }

    public void filterByTeam(String team) {
        if (binding == null || binding.swipeRefresh.isRefreshing() || isLoading) return;
        FiltersManager.resetFilters();
        FiltersManager.getActiveFilters().setTeam(team);
        refreshFeed();
    }

    // Top of the feed: the profile panel when filtered by a user, otherwise the team panel
    // when filtered by a team (each hides itself when its filter isn't active)
    private void bindHeaderPanels(View header) {
        boolean userFilter = !FiltersManager.getActiveFilters().getUsername().isEmpty();
        bindUserPanel(header, userFilter);
        if (userFilter) {
            header.findViewById(R.id.teamPanel).setVisibility(View.GONE);
        } else {
            bindTeamPanel(header);
        }
    }

    // USER PANEL
    // Shown at the top of the feed when it is filtered by a user: photo (if any), role, name,
    // team (tap to filter by team) and Edit for the user themself, organizers and admins.
    // Only filled-in information is shown.
    private void bindUserPanel(View header, boolean visible) {
        View panel = header.findViewById(R.id.userPanel);
        User profileUser = visible ? findUserByUsername(FiltersManager.getActiveFilters().getUsername()) : null;
        if (profileUser == null) {
            panel.setVisibility(View.GONE);
            return;
        }
        panel.setVisibility(View.VISIBLE);

        ImageView photo = header.findViewById(R.id.userPanelPhoto);
        TextView role = header.findViewById(R.id.userPanelRole);
        TextView name = header.findViewById(R.id.userPanelName);
        TextView team = header.findViewById(R.id.userPanelTeam);
        TextView editButton = header.findViewById(R.id.userPanelEdit);

        UserProfiles.loadPhoto(photo, profileUser.getId());
        photo.setOnClickListener(v -> UserProfiles.showPhotoLarge(requireContext(), profileUser.getId()));
        com.suici.roverhood.utils.PinchZoom.attach(photo);

        UserProfiles.fillInfo(header.findViewById(R.id.userPanelInfo), profileUser.getId());

        role.setText(profileUser.getUserType());
        name.setText(profileUser.getUsername());
        team.setText(Teams.label(profileUser.getTeam()));
        team.setOnClickListener(v -> filterByTeam(profileUser.getTeam()));

        User currentUser = activity.getCurrentUser();
        boolean isSelf = currentUser != null && profileUser.getId().equals(currentUser.getId());
        boolean isModerator = com.suici.roverhood.utils.Roles.isStaff(currentUser);
        if ((isSelf || isModerator) && !offlineMode) {
            editButton.setVisibility(View.VISIBLE);
            editButton.setOnClickListener(v -> {
                EditProfile dialog = new EditProfile(profileUser, isSelf, isModerator, this::onProfileSaved);
                dialog.show(getParentFragmentManager(), "editProfile");
            });
        } else {
            editButton.setVisibility(View.GONE);
        }
    }

    private User findUserByUsername(String username) {
        for (User user : LocalDatabase.getInstance(requireContext()).getAllUsers().values()) {
            if (user.getUsername().equalsIgnoreCase(username)) return user;
        }
        return null;
    }

    // After a profile edit: keep showing the same person (under the new name if renamed),
    // update the logged-in user if they renamed themself, and reload the posts
    public void onProfileSaved(String oldUsername, String newUsername) {
        if (binding == null) return;
        User currentUser = activity.getCurrentUser();
        if (currentUser != null && currentUser.getUsername().equalsIgnoreCase(oldUsername)) {
            currentUser.setUsername(newUsername);
            binding.username.setText(newUsername);
            // The team may have changed too (organizers/admins editing themselves)
            User saved = LocalDatabase.getInstance(requireContext()).getUserById(currentUser.getId());
            if (saved != null) {
                currentUser.setTeam(saved.getTeam());
                binding.team.setText(Teams.label(saved.getTeam()));
            }
        }
        FiltersManager.getActiveFilters().setUsername(newUsername);
        bindBannerPhoto();
        if (!binding.swipeRefresh.isRefreshing() && !isLoading) {
            refreshFeed();
        } else {
            onTeamsChanged();
        }
    }

    // TEAM PANEL
    // Shown at the top of the feed (scrolling with the posts) when it is filtered by a team:
    // name and emoji (editable by admins), members (tap to filter by that person), then the posts.
    private void bindTeamPanel(View header) {
        View panel = header.findViewById(R.id.teamPanel);
        String team = FiltersManager.getActiveFilters().getTeam();
        if (team.isEmpty()) {
            panel.setVisibility(View.GONE);
            return;
        }
        panel.setVisibility(View.VISIBLE);

        TextView emojiView = header.findViewById(R.id.teamPanelEmoji);
        TextView nameView = header.findViewById(R.id.teamPanelName);
        TextView editButton = header.findViewById(R.id.teamPanelEdit);
        TextView membersLabel = header.findViewById(R.id.teamPanelMembersLabel);
        ChipGroup membersGroup = header.findViewById(R.id.teamPanelMembers);

        emojiView.setText(Teams.emoji(team));
        nameView.setText(Teams.name(team));

        // Only admins, organizers and moderators can rename the team or change its emoji (needs internet)
        User currentUser = activity.getCurrentUser();
        boolean canEdit = com.suici.roverhood.utils.Roles.isStaff(currentUser);
        if (canEdit && !offlineMode) {
            editButton.setVisibility(View.VISIBLE);
            editButton.setOnClickListener(v -> showEditTeamDialog(team, null));
            emojiView.setOnClickListener(v -> showEditTeamDialog(team, null));
        } else {
            editButton.setVisibility(View.GONE);
            emojiView.setOnClickListener(null);
            emojiView.setClickable(false);
        }

        List<User> members = new ArrayList<>();
        for (User user : LocalDatabase.getInstance(requireContext()).getAllUsers().values()) {
            if (team.equals(user.getTeam())) members.add(user);
        }
        members.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));

        membersLabel.setText("Members (" + members.size() + ")");
        membersGroup.removeAllViews();
        for (User member : members) {
            Chip chip = new Chip(requireContext());
            chip.setText(member.getUsername());
            chip.setOnClickListener(v -> filterByUser(member.getUsername()));
            membersGroup.addView(chip);
        }
    }

    // Rename a team or change its emoji (organizers/admins). afterSave may be null.
    public void showEditTeamDialog(String team, Runnable afterSave) {
        int padding = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding / 2, padding, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Team name");
        nameInput.setSingleLine(true);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        nameInput.setText(Teams.name(team));
        form.addView(nameInput);

        EditText emojiInput = new EditText(requireContext());
        emojiInput.setHint("Emoji");
        emojiInput.setSingleLine(true);
        emojiInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        emojiInput.setText(Teams.emoji(team));
        form.addView(emojiInput);

        // Live validation: exactly one emoji
        emojiInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String value = s.toString().trim();
                emojiInput.setError(value.isEmpty() || Teams.isSingleEmoji(value) ? null : "Only one emoji allowed");
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit team")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        // Overriding the click so the dialog stays open on validation errors
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String emoji = emojiInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("Name cannot be empty");
                return;
            }
            if (emoji.isEmpty()) {
                emojiInput.setError("Pick an emoji");
                return;
            }
            if (!Teams.isSingleEmoji(emoji)) {
                emojiInput.setError("Only one emoji allowed");
                return;
            }

            firebaseRepository.updateTeam(team, name, emoji, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    if (binding == null) return;
                    Toast.makeText(requireContext(), "Team updated", Toast.LENGTH_SHORT).show();
                    onTeamsChanged();
                    if (afterSave != null) afterSave.run();
                }

                @Override
                public void onError(String errorMessage) {
                    if (binding == null) return;
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
            dialog.dismiss();
        }));
        dialog.show();
    }

    // Team names/emojis or profiles changed: refresh the banner, the filters bar, the panels and the posts
    private void onTeamsChanged() {
        if (binding == null) return;
        if (activity.getCurrentUser() != null) {
            binding.team.setText(Teams.label(activity.getCurrentUser().getTeam()));
        }
        bindBannerPhoto();
        if (FiltersManager.areFiltersOrSortEnabled()) {
            binding.filterList.setText(FiltersManager.getFiltersText());
        }
        postAdapter.notifyItemRangeChanged(0, visiblePostHandlerList.size() + 1);
    }

    // The logged in user's photo in the banner (hidden if they have none); tap opens their profile
    private void bindBannerPhoto() {
        if (binding == null) return;
        User currentUser = activity.getCurrentUser();
        if (currentUser == null) {
            binding.bannerPhoto.setVisibility(View.GONE);
            return;
        }
        UserProfiles.loadPhoto(binding.bannerPhoto, currentUser.getId());
        binding.bannerPhoto.setOnClickListener(v -> filterByUser(currentUser.getUsername()));
    }

    // On every online refresh: make the offline users table match Firebase (new accounts,
    // renames, team changes, deletions), which also clears the "offline user" flag left by a
    // login without internet. If the logged-in account was deleted, log out.
    private void syncUsersFromServer() {
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        firebaseRepository.getAllUsers(new FirebaseRepository.UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                if (binding == null) return;
                localDB.syncUsers(users);
                Log.d("RoverFeed", "Users synced: " + users.size());

                User currentUser = activity.getCurrentUser();
                if (currentUser == null) return;
                currentUser.setOfflineUser(false);

                User fresh = users.get(currentUser.getId());
                if (fresh == null) {
                    Toast.makeText(requireContext(), "Your account was deleted", Toast.LENGTH_LONG).show();
                    localDB.markLoggedOut();
                    activity.setCurrentUser(null);
                    NavHostFragment.findNavController(RoverFeed.this).navigate(R.id.action_RoverFeed_to_LogIn);
                    return;
                }

                // A role changed by an admin is saved locally by syncUsers and applies the next
                // time the app is opened (no restart in the middle of using it)

                // Renamed or moved to another team from another phone
                if (!fresh.getUsername().equals(currentUser.getUsername())
                        || !java.util.Objects.equals(fresh.getTeam(), currentUser.getTeam())) {
                    currentUser.setUsername(fresh.getUsername());
                    currentUser.setTeam(fresh.getTeam());
                    binding.username.setText(fresh.getUsername());
                    binding.team.setText(Teams.label(fresh.getTeam()));
                }
                onTeamsChanged();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e("RoverFeed", "Users sync failed: " + errorMessage);
            }
        });
    }

    // Called when a notification is tapped while the feed is showing.
    // Waits for any running refresh, then opens the notification's filter on its post.
    public void openNotificationTarget(NotificationTarget target) {
        notificationTarget = target;
        new android.os.Handler().post(new Runnable() {
            @Override
            public void run() {
                if (binding == null) return;
                if (isLoading || binding.swipeRefresh.isRefreshing()) {
                    new android.os.Handler().postDelayed(this, 200);
                    return;
                }
                FiltersManager.resetFilters();
                applyNotificationFilter(target);
                refreshFeed();
            }
        });
    }

    // Which feed each notification opens:
    // - announcement: announcements only
    // - comment on / likes of your post: your posts
    // - likes of your comment: whole feed (the post may belong to someone else)
    // - post on your topic: that topic, oldest first (same as tapping a topic label)
    // - any new post (moderators and admins): whole feed
    // - reported post: the author's profile; reported comment: whole feed, comments opened on it
    private void applyNotificationFilter(NotificationTarget target) {
        switch (target.type) {
            case NotificationTarget.TYPE_ANNOUNCEMENT:
                FiltersManager.getActiveFilters().setAnnouncementsOnly(true);
                break;
            case NotificationTarget.TYPE_COMMENT:
            case NotificationTarget.TYPE_POST_LIKES:
                if (activity.getCurrentUser() != null)
                    FiltersManager.getActiveFilters().setUsername(activity.getCurrentUser().getUsername());
                break;
            case NotificationTarget.TYPE_REPORT_POST:
                // The author's profile, with the reported post highlighted
                if (target.username != null && !target.username.isEmpty())
                    FiltersManager.getActiveFilters().setUsername(target.username);
                break;
            case NotificationTarget.TYPE_TOPIC_POST:
                if (target.topicTitle != null && !target.topicTitle.isEmpty()) {
                    FiltersManager.getActiveFilters().setTopic(target.topicTitle);
                    FiltersManager.getActiveFilters().setOrderAscending(true);
                }
                break;
            default:
                break;
        }
    }

    // After each batch of posts is drawn: if a post should be focused and is visible,
    // scroll it just below the banner, briefly highlight it and open its comments if
    // needed. If it isn't loaded yet, load the next batch; if it doesn't exist, say so.
    private void tryFocusPendingPost(boolean allPostsLoaded) {
        if (notificationTarget == null || binding == null) return;
        NotificationTarget target = notificationTarget;
        for (int i = 0; i < visiblePostHandlerList.size(); i++) {
            PostHandler postHandler = visiblePostHandlerList.get(i);
            if (target.postId.equals(postHandler.getPost().getId())) {
                notificationTarget = null;
                int position = i + 1; // position 0 is the header space of the adapter

                int offset = binding.banner.getHeight();
                if (binding.filtersLayout.getVisibility() == View.VISIBLE)
                    offset += binding.filtersLayout.getHeight();

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) layoutManager.scrollToPositionWithOffset(position, offset);
                recyclerView.postDelayed(() -> highlightPost(position), 300);

                if (target.opensComments()) {
                    recyclerView.postDelayed(() -> {
                        if (binding == null) return;
                        Comments commentsDialog = new Comments(postHandler, this, postHandler.isOfflinePost());
                        commentsDialog.setFocusCommentId(target.commentId);
                        commentsDialog.show(getParentFragmentManager(), "commentsDialog");
                    }, 900);
                }
                return;
            }
        }

        if (allPostsLoaded) {
            notificationTarget = null;
            Toast.makeText(requireContext(), "This post is no longer available", Toast.LENGTH_SHORT).show();
        } else {
            isLoading = true;
            waitThenDrawPosts();
        }
    }

    private void highlightPost(int position) {
        if (binding == null) return;
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        View item = holder.itemView;
        item.animate().alpha(0.35f).setDuration(250).withEndAction(() ->
                item.animate().alpha(1f).setDuration(250).withEndAction(() ->
                        item.animate().alpha(0.35f).setDuration(250).withEndAction(() ->
                                item.animate().alpha(1f).setDuration(250).start()
                        ).start()
                ).start()
        ).start();
    }

    @Override
    public void onDestroyView() {
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.INVISIBLE);
        }

        if (backCallback != null)
            backCallback.remove();

        if (activity.isFabMenuOpen)
            activity.hideFabMenu();

        super.onDestroyView();
        binding = null;
    }

    public void openFiltersDialog() {
        FilterSelector filterSelectorFragment = new FilterSelector();
        filterSelectorFragment.setOriginalFeed(this);
        filterSelectorFragment.show(activity.getSupportFragmentManager(), "addPostFragment");
    }

    // Search button: the users list (staff also get Edit / Delete there; participants only
    // search and tap someone to see their profile)
    public void openSearch() {
        new com.suici.roverhood.dialogs.ManageUsers(this).show(activity.getSupportFragmentManager(), "manageUsers");
    }

    // Announcements not seen yet on this phone (your own count as seen), oldest first
    private List<PostHandler> unseenAnnouncements() {
        List<PostHandler> unseen = new ArrayList<>();
        User currentUser = activity.getCurrentUser();
        for (PostHandler postHandler : allPostHandlerList) {
            Post post = postHandler.getPost();
            if (!post.isAnnouncement()) continue;
            if (currentUser != null && post.getUser() != null && currentUser.getId().equals(post.getUser().getId())) continue;
            if (com.suici.roverhood.utils.AnnouncementsSeen.isSeen(requireContext(), post.getId())) continue;
            unseen.add(postHandler);
        }
        unseen.sort((a, b) -> Long.compare(dateOf(a), dateOf(b)));
        return unseen;
    }

    private static long dateOf(PostHandler postHandler) {
        Long date = postHandler.getPost().getDate();
        return date != null ? date : 0;
    }

    private void updateAnnouncementsBadge() {
        if (binding == null) return;
        activity.updateAnnouncementsBadge(unseenAnnouncements().size());
    }

    // Announcements button: with unseen announcements, show the announcements and go to the
    // oldest one not seen yet; otherwise just show the announcements
    public void applyAnnouncementsFilter() {
        List<PostHandler> unseen = unseenAnnouncements();
        if (!unseen.isEmpty()) {
            openNotificationTarget(new NotificationTarget(NotificationTarget.TYPE_ANNOUNCEMENT,
                    unseen.get(0).getPost().getId(), null, null));
            return;
        }
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
                    // Already in display order (see FiltersManager.filterPosts)
                    List<PostHandler> filteredList = FiltersManager.filterPosts(allPostHandlerList, activity);

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
            tryFocusPendingPost(true);
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
                            postAdapter.notifyItemInserted(visiblePostHandlerList.size());
                        }
                    }

                    // When all posts have been loaded, disable the spinning loading at the end of the feed
                    postsLoadedCount = nextLimit;
                    if (postsLoadedCount >= totalPosts) {
                        postAdapter.setLoading(false);
                    }

                    finishDrawUI();
                    tryFocusPendingPost(postsLoadedCount >= totalPosts);
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

        // A user who logged in without internet is an "offline user". When the phone has internet
        // again, still try online instead of staying offline for the whole session.
        boolean stayOfflineUser = currentUser != null && currentUser.isOfflineUser()
                && !NetworkStatus.isOnline(requireContext());

        if(currentUser != null)
            firebaseRepository.loadPosts(offlineMode, stayOfflineUser, new FirebaseRepository.PostRepositoryCallback() {
                @Override
                public void onPostsLoaded(List<PostHandler> postHandlers, boolean isOffline) {
                    if (!isOffline) {
                        syncUsersFromServer();
                        refreshStaffCaches();
                        firebaseRepository.fetchTeams(RoverFeed.this::onTeamsChanged);
                        firebaseRepository.fetchUserInfo(RoverFeed.this::onTeamsChanged);
                    }

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

                    // The add button follows the mode; normally finishRefreshUI() sets it, but if
                    // the refresh UI already finished, update it here so it can't stay disabled
                    activity.setPostingDisabled(isOffline);
                    if (activity.getFloatingButton() != null && !binding.swipeRefresh.isRefreshing()) {
                        activity.getFloatingButton().setEnabled(isAddButtonUsable());
                    }

                    allPostHandlerList.clear();
                    allPostHandlerList.addAll(postHandlers);
                    updateAnnouncementsBadge();

                    for (PostHandler postHandler : allPostHandlerList) {
                        postHandler.setFragment(RoverFeed.this);
                    }

                    postsLoadedCount = 0;
                    isLoading = true;
                    waitThenDrawPosts();
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
                    finishRefreshUI();
                }
            }
        }, 500);
    }

    private void startRefreshUI() {
        binding.buttonLogOut.setEnabled(false);
        postAdapter.setShowNoPosts(false);

        if(filterButton != null)
            filterButton.setEnabled(false);
        binding.swipeRefresh.setRefreshing(true);
        
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setEnabled(false);
            if (activity.isFabMenuOpen)
                activity.hideFabMenu();
        }
        postAdapter.setLoading(false);

        activity.updateFiltersBadge(FiltersManager.activeFilterCount());
        if (FiltersManager.showInfoBar(requireContext())) {
            binding.filtersLayout.setVisibility(View.VISIBLE);
            binding.filterList.setText(FiltersManager.getFiltersText());
            updateFiltersBannerSize();
        }
        else {
            binding.filtersLayout.setVisibility(View.GONE);
        }

        if (binding.banner.getTranslationY() != 0) {
            binding.banner.animate().translationY(0).setDuration(100).start();
            binding.filtersLayout.animate().translationY(0).setDuration(100).start();
            bannerHidden = false;
        }
    }

    private void updateFiltersBannerSize() {
        ConstraintLayout filtersLayout = binding.filtersLayout;

        filtersLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                postAdapter.setFiltersHeight(filtersLayout.getHeight());
                filtersLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void finishRefreshUI() {
        binding.swipeRefresh.setRefreshing(false);
        if (activity.getFloatingButton() != null && isAddButtonUsable()) {
            activity.getFloatingButton().setEnabled(true);
        }
        // Nothing to show: say so under the profile / team panel and don't leave the
        // "loading more" spinner running
        boolean noPosts = FiltersManager.filterPosts(allPostHandlerList, activity).isEmpty();
        postAdapter.setShowNoPosts(noPosts);
        postAdapter.setLoading(!noPosts);
    }

    public void removePostFromUI(PostHandler postHandler) {
        int index = visiblePostHandlerList.indexOf(postHandler);
        if (index != -1) {
            visiblePostHandlerList.remove(index);
            postAdapter.notifyItemRemoved(index + 1);

            postsLoadedCount--;
            allPostHandlerList.remove(postHandler);
            firebaseRepository.deleteUnusedTopics(allPostHandlerList);
        }
    }

    public void updatePostInUI(PostHandler postHandler) {
        int index = visiblePostHandlerList.indexOf(postHandler);
        if (index != -1) {
            visiblePostHandlerList.set(index, postHandler);
            postAdapter.notifyItemChanged(index + 1);
        }
    }

    public void addPostToUI(PostHandler postHandler) {
        if (FiltersManager.isVisibleAfterFilter(postHandler, activity)) {
            int index = visiblePostHandlerList.indexOf(postHandler);

            if (index == -1 && (!binding.swipeRefresh.isRefreshing() && !isLoading)) {
                // add for sort by likes
                if (FiltersManager.getActiveFilters().isOrderAscending()) {
                    visiblePostHandlerList.add(postHandler);
                    postAdapter.notifyItemInserted(visiblePostHandlerList.size());
                    recyclerView.scrollToPosition(visiblePostHandlerList.size());
                } else {
                    visiblePostHandlerList.add(0, postHandler);
                    postAdapter.notifyItemInserted(1);
                    recyclerView.scrollToPosition(1);
                }
                postsLoadedCount++;
                allPostHandlerList.add(postHandler);
            }
        }
    }

    public boolean isLoading() { return isLoading; }
    // True while the feed shows the offline (saved) data: the staff lists then open read-only
    public boolean isOfflineMode() { return offlineMode; }

    // Staff: keep the reports and the unused codes saved on the phone (loading them saves them),
    // so their lists also open offline, even if they were never opened online on this phone
    private void refreshStaffCaches() {
        if (!com.suici.roverhood.utils.Roles.isStaff(activity.getCurrentUser())) return;
        firebaseRepository.loadReports(new FirebaseRepository.ReportsCallback() {
            @Override public void onLoaded(List<FirebaseRepository.Report> reports) { }
            @Override public void onError(String errorMessage) { }
        });
        firebaseRepository.loadUnusedCodes(new FirebaseRepository.UnusedCodesCallback() {
            @Override public void onLoaded(List<FirebaseRepository.UnusedCode> codes) { }
            @Override public void onError(String errorMessage) { }
        });
    }

    // The + button: online for everyone; offline only for staff, whose menu then opens the
    // read-only lists (Reports, Codes, Users, Teams) while Post and Announcement stay disabled
    private boolean isAddButtonUsable() {
        return !offlineMode || com.suici.roverhood.utils.Roles.isStaff(activity.getCurrentUser());
    }
}