package com.suici.roverhood;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.suici.roverhood.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private long pressedTime;

    private FloatingActionButton floatingButton;
    private Menu optionsMenu;
    public User currentUser = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

