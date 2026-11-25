package com.suici.roverhood;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.NotificationTarget;
import com.suici.roverhood.utils.Notifications;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    private long pressedTime;
    private User currentUser = null;
    private NotificationTarget pendingNotificationTarget = null;
    private int lastNightMode;

    private AppBarConfiguration appBarConfiguration;
    private FloatingActionButton floatingButton;
    private FloatingActionButton fabPost;
    private FloatingActionButton fabAnnouncement;
    private FloatingActionButton fabCodes;
    private FloatingActionButton fabUsers;
    private FloatingActionButton fabTeams;
    private FloatingActionButton fabReports;
    private FloatingActionButton[] menuFabs;
    public boolean isFabMenuOpen = false;
    private boolean postingDisabled = false;   // offline: Post and Announcement can't be used
    private Menu optionsMenu;
    private LinearProgressIndicator downloadProgressBar;
    private LinearProgressIndicator uploadProgressBar;

    // Must be registered before the activity starts
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    Log.d("MainActivity", "Notification permission " + (granted ? "granted" : "denied")));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifications.createChannels(this);
        Teams.init(this);
        UserProfiles.init(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        lastNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        AndroidThreeTen.init(this);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.LogIn, R.id.RoverFeed).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        floatingButton =  findViewById(R.id.fab);
        fabPost = findViewById(R.id.fabPost);
        fabAnnouncement = findViewById(R.id.fabAnnouncement);
        fabCodes = findViewById(R.id.fabCodes);
        fabUsers = findViewById(R.id.fabUsers);
        fabTeams = findViewById(R.id.fabTeams);
        fabReports = findViewById(R.id.fabReports);
        menuFabs = new FloatingActionButton[]{fabPost, fabAnnouncement, fabCodes, fabUsers, fabTeams, fabReports};
        floatingButton.setOnClickListener(v -> toggleFabMenu());
        getFloatingButton().setVisibility(View.INVISIBLE);

        // Menu buttons start hidden behind the main + button
        floatingButton.post(() -> {
            for (FloatingActionButton fab : menuFabs) {
                fab.setTranslationY(-(fab.getY() - floatingButton.getY()));
                fab.setVisibility(View.INVISIBLE);
            }
        });

        // Initialise App Bar to show logo instead of fragment name, and
        // add logic on interacting with the logo
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            View logoView = getLayoutInflater().inflate(R.layout.toolbar_logo, null);
            getSupportActionBar().setCustomView(logoView);

            logoView.setOnClickListener(v -> tryRefreshCurrentFragment());
        }

        // Only show app bar in RoverFeed fragment
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (getSupportActionBar() == null) return;

            if (destination.getId() == R.id.RoverFeed) {
                // Show toolbar with logo
                binding.toolbar.setVisibility(View.VISIBLE);
                binding.bottomFade.setVisibility(View.VISIBLE);
            } else {
                // Hide toolbar completely
                binding.toolbar.setVisibility(View.GONE);
                binding.bottomFade.setVisibility(View.GONE);
            }
        });

        // Exit app if pressed back 2 times in 2s
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long currentTime = System.currentTimeMillis();

                if (pressedTime + 2000 > currentTime) {
                    finish();
                } else {
                    Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    pressedTime = currentTime;
                }
            }
        });

        // Initialize FirebaseRepository
        FirebaseRepository.updateContext(MainActivity.this);
        FirebaseRepository.getInstance(MainActivity.this).signInAnonymously();

        // Initialize SQLite
        LocalDatabase.updateInstance(MainActivity.this);

        // Initialize ProgressBars
        downloadProgressBar = binding.downloadProgressBar;
        downloadProgressBar.setVisibility(View.GONE);

        uploadProgressBar = binding.uploadProgressBar;
        uploadProgressBar.setVisibility(View.GONE);

        handleNotificationIntent(getIntent());
    }

    // App already running and the user tapped an announcement notification
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    // Opens the target of a tapped notification: directly if the feed is showing,
    // otherwise it is kept until the feed is created (after the automatic re-login)
    private void handleNotificationIntent(Intent intent) {
        NotificationTarget target = NotificationTarget.takeFrom(intent);
        if (target == null) return;

        RoverFeed feed = getVisibleRoverFeed();
        if (feed != null) {
            feed.openNotificationTarget(target);
        } else {
            pendingNotificationTarget = target;
        }
    }

    public NotificationTarget consumePendingNotificationTarget() {
        NotificationTarget target = pendingNotificationTarget;
        pendingNotificationTarget = null;
        return target;
    }

    private RoverFeed getVisibleRoverFeed() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment == null) return null;
        for (androidx.fragment.app.Fragment fragment : navHostFragment.getChildFragmentManager().getFragments()) {
            if (fragment instanceof RoverFeed && fragment.isAdded() && fragment.getView() != null) {
                return (RoverFeed) fragment;
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionsMenu = menu;
        menuBadges.clear(); // the menu views were recreated
        return true;
    }

    private final java.util.Map<Integer, com.google.android.material.badge.BadgeDrawable> menuBadges = new java.util.HashMap<>();

    // Small number on the filter button with how many filters differ from the defaults (hidden at 0)
    public void updateFiltersBadge(int count) {
        updateMenuBadge(R.id.filters, count);
    }

    // Number of announcements not seen yet on this phone (hidden at 0)
    public void updateAnnouncementsBadge(int count) {
        updateMenuBadge(R.id.announcements, count);
    }

    @androidx.annotation.OptIn(markerClass = com.google.android.material.badge.ExperimentalBadgeUtils.class)
    private void updateMenuBadge(int itemId, int count) {
        // The menu item's view exists only after the toolbar is laid out
        binding.toolbar.post(() -> {
            if (binding.toolbar.findViewById(itemId) == null) return;
            com.google.android.material.badge.BadgeDrawable badge = menuBadges.get(itemId);
            if (badge == null) {
                if (count == 0) return;
                badge = com.google.android.material.badge.BadgeDrawable.create(this);
                badge.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                        binding.toolbar, com.google.android.material.R.attr.colorPrimary));
                badge.setBadgeTextColor(android.graphics.Color.WHITE);
                com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(badge, binding.toolbar, itemId);
                menuBadges.put(itemId, badge);
            }
            badge.setNumber(count);
            badge.setVisible(count > 0);
        });
    }

    @Override
    // Adding correspondent actions in RoverFeed fragment to each option from the app bar menu
    public boolean onOptionsItemSelected(MenuItem item) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment != null) {
            RoverFeed fragment = (RoverFeed) navHostFragment.getChildFragmentManager().getFragments().get(0);
            if (fragment != null) {
                if (item.getItemId() == R.id.filters)
                    fragment.openFiltersDialog();
                if (item.getItemId() == R.id.search)
                    fragment.openSearch();
                if (item.getItemId() == R.id.announcements)
                    fragment.applyAnnouncementsFilter();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    // Logic for clicking on Roverhood app logo from the app bar
    private void tryRefreshCurrentFragment() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment != null) {
            RoverFeed fragment = (RoverFeed) navHostFragment.getChildFragmentManager().getFragments().get(0);
            if (fragment != null) {
                fragment.refreshFeedAndFilters();
            }
        }
    }

    // Restart app if theme is changed - used to crash, or enter a state with uninitialised variables
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int newNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (newNightMode != lastNightMode) {
            Log.d("MainActivity", "Theme changed, restarting app...");
            restartApp();
        }
        lastNightMode = newNightMode;
    }

    private void restartApp() {
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }

    public void toggleFabMenu() {
        if (isFabMenuOpen) {
            hideFabMenu();
        } else {
            showFabMenu();
        }
    }

    public void showFabMenu() {
        for (FloatingActionButton fab : menuFabs) {
            boolean posting = fab == fabPost || fab == fabAnnouncement;
            boolean usable = !(posting && postingDisabled);
            fab.setEnabled(usable);
            fab.setVisibility(View.VISIBLE);
            fab.animate().translationY(0).alpha(usable ? 1f : 0.35f).setDuration(200).start();
        }
        isFabMenuOpen = true;
    }

    public void setPostingDisabled(boolean disabled) {
        postingDisabled = disabled;
    }

    public void hideFabMenu() {
        for (FloatingActionButton fab : menuFabs) {
            fab.setEnabled(false);
            float offset = fab.getY() - floatingButton.getY();
            fab.animate().translationY(-offset).alpha(0f).setDuration(200)
                    .withEndAction(() -> fab.setVisibility(View.GONE)).start();
        }
        isFabMenuOpen = false;
    }



    public LinearProgressIndicator getDownloadProgressBar() {
        return downloadProgressBar;
    }
    public LinearProgressIndicator getUploadProgressBar() {
        return uploadProgressBar;
    }
    public Menu getOptionsMenu() {
        return optionsMenu;
    }
    public FloatingActionButton getFloatingButton() {
        return floatingButton;
    }
    public FloatingActionButton getFabPost() {
        return fabPost;
    }
    public FloatingActionButton getFabAnnouncement() {
        return fabAnnouncement;
    }
    public FloatingActionButton getFabCodes() {
        return fabCodes;
    }
    public FloatingActionButton getFabUsers() {
        return fabUsers;
    }
    public FloatingActionButton getFabTeams() {
        return fabTeams;
    }
    public FloatingActionButton getFabReports() {
        return fabReports;
    }
    // Logging in subscribes this phone to announcements and to the user's personal
    // notifications (and asks for the notification permission on Android 13+);
    // logging out unsubscribes it, so the next user doesn't get the previous one's notifications
    public void setCurrentUser(User user) {
        User previousUser = this.currentUser;
        this.currentUser = user;

        String previousId = previousUser != null ? previousUser.getId() : null;
        String newId = user != null ? user.getId() : null;
        if (java.util.Objects.equals(previousId, newId)) return;

        if (previousId != null) {
            Notifications.unsubscribe(previousId);
        }
        if (newId != null) {
            Notifications.subscribe(newId);
            if (!Notifications.hasPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
    public User getCurrentUser() {
        return this.currentUser;
    }
}

