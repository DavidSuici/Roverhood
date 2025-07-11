package com.suici.roverhood;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

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
import com.suici.roverhood.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    private long pressedTime;
    private User currentUser = null;
    private int lastNightMode;

    private AppBarConfiguration appBarConfiguration;
    private FloatingActionButton floatingButton;
    private Menu optionsMenu;
    private LinearProgressIndicator downloadProgressBar;
    private LinearProgressIndicator uploadProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        lastNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        AndroidThreeTen.init(this);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.LogIn, R.id.RoverFeed).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        floatingButton =  findViewById(R.id.fab);
        getFloatingButton().setVisibility(View.INVISIBLE);

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
            } else {
                // Hide toolbar completely
                binding.toolbar.setVisibility(View.GONE);
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

        // Initialize ProgressBars
        downloadProgressBar = binding.downloadProgressBar;
        downloadProgressBar.setVisibility(View.GONE);

        uploadProgressBar = binding.uploadProgressBar;
        uploadProgressBar.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionsMenu = menu;
        return true;
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
                if (item.getItemId() == R.id.likedPosts)
                    fragment.applyLikedPostsFilter();
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
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
    public User getCurrentUser() {
        return this.currentUser;
    }
}

