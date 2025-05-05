package com.suici.roverhood;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.suici.roverhood.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.ProgressBar;

import com.jakewharton.threetenabp.AndroidThreeTen;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private long pressedTime;

    private FloatingActionButton floatingButton;
    private Menu optionsMenu;
    public User currentUser = null;
    private int lastNightMode = Configuration.UI_MODE_NIGHT_NO;
    private ProgressBar progressBar;

    public static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        lastNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        AndroidThreeTen.init(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.LogIn, R.id.RoverFeed).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        floatingButton =  findViewById(R.id.fab);
        getFloatingButton().setVisibility(View.INVISIBLE);

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

        // Initialize ProgressBar
        progressBar = binding.progressBar;  // Reference the ProgressBar here
        progressBar.setVisibility(View.GONE);  // Initially set it to GONE, show when needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem checkable = menu.findItem(R.id.checkable_menu);
        checkable.setActionView(R.layout.use_switch);
        optionsMenu = menu;
        return true;
    }

    // Restart app if theme is changed - used to crash, or enter e state with uninitialised variables
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

    public void updateProgressBar(int currentProgress, int totalProgress) {
        if (progressBar != null && progressBar instanceof LinearProgressIndicator) {
            LinearProgressIndicator linearProgressBar = (LinearProgressIndicator) progressBar;

            linearProgressBar.setMax(totalProgress);

            ValueAnimator progressAnimator = ValueAnimator.ofInt(linearProgressBar.getProgress(), currentProgress);
            progressAnimator.setDuration(500);
            progressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    linearProgressBar.setProgressCompat((Integer) animation.getAnimatedValue(), true);
                }
            });

            progressAnimator.addListener(new android.animation.Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {
                    if (linearProgressBar.getVisibility() == View.GONE && currentProgress < totalProgress) {
                        linearProgressBar.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (currentProgress >= totalProgress) {
                        new Handler().postDelayed(() -> {
                            ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
                            fadeOutAnimator.setDuration(500);
                            fadeOutAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    float alpha = (Float) animation.getAnimatedValue();
                                    linearProgressBar.setAlpha(alpha);
                                }
                            });

                            fadeOutAnimator.addListener(new android.animation.Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(android.animation.Animator animation) {
                                }

                                @Override
                                public void onAnimationEnd(android.animation.Animator animation) {
                                    linearProgressBar.setVisibility(View.GONE);
                                }

                                @Override
                                public void onAnimationCancel(android.animation.Animator animation) {
                                }

                                @Override
                                public void onAnimationRepeat(android.animation.Animator animation) {
                                }
                            });

                            fadeOutAnimator.start();

                        }, 500);
                    }
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    // Handle animation cancellation if needed
                }

                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {
                    // Handle animation repeat if needed
                }
            });

            // Start the progress animation
            progressAnimator.start();
        }
    }
}

