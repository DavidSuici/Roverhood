package com.suici.roverhood;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.suici.roverhood.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.Logger;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private FloatingActionButton floatingButton;
    private Menu optionsMenu;
    public boolean onlyAnnouncements = false;
    public boolean onFeed = false;
    private long pressedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);
        DatabaseReference ref = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("testNode");

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.LogIn, R.id.RoverFeed, R.id.loading).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        floatingButton = (FloatingActionButton) findViewById(R.id.fab);
        getFloatingButton().setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionsMenu = menu;

        if(onFeed)
        {
            MenuItem item2 = optionsMenu.findItem(R.id.checkable_menu);
            item2.setVisible(true);
        }

        MenuItem checkable = menu.findItem(R.id.checkable_menu);
        checkable.setActionView(R.layout.use_switch);
        final SwitchCompat sw = (SwitchCompat) menu.findItem(R.id.checkable_menu).getActionView().findViewById(R.id.switch2);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    onlyAnnouncements = true;

                    if(onFeed) {
                        FragmentManager fragmentManager = getSupportFragmentManager();
                        Fragment fragment = fragmentManager.getPrimaryNavigationFragment();

                        NavHostFragment.findNavController(fragment)
                                .navigate(R.id.action_RoverFeed_to_loading);
                    }
                }
                else {
                    onlyAnnouncements = false;

                    if(onFeed) {
                        FragmentManager fragmentManager = getSupportFragmentManager();
                        Fragment fragment = fragmentManager.getPrimaryNavigationFragment();

                        NavHostFragment.findNavController(fragment)
                                .navigate(R.id.action_RoverFeed_to_loading);
                    }
                }
            }
        });

        return true;
    }

    @Override
    public void onBackPressed() {

        if (pressedTime + 2000 > System.currentTimeMillis()) {
            finish();
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
            if(onFeed) {
                FragmentManager fragmentManager = getSupportFragmentManager();
                Fragment fragment = fragmentManager.getPrimaryNavigationFragment();

                NavHostFragment.findNavController(fragment)
                        .navigate(R.id.action_RoverFeed_to_loading);
            }
        }
        pressedTime = System.currentTimeMillis();
    }

    public void onLogIn(View view) {

        MenuItem item2 = optionsMenu.findItem(R.id.checkable_menu);
        item2.setVisible(true);

        final SwitchCompat sw = (SwitchCompat) optionsMenu.findItem(R.id.checkable_menu).getActionView().findViewById(R.id.switch2);
        sw.setChecked(false);
        Navigation.findNavController(view).navigate(R.id.action_LogIn_to_loading);
    }

    public void onLogOut(View view) {
        MenuItem item2 = optionsMenu.findItem(R.id.checkable_menu);
        item2.setVisible(false);

        Navigation.findNavController(view).navigate(R.id.action_RoverFeed_to_LogIn);
    }

    public void onCreatePost(View view) {
        Button temp = findViewById(R.id.buttonLogOut);
        Navigation.findNavController(temp).navigate(R.id.action_RoverFeed_to_LogIn);
        MenuItem item2 = optionsMenu.findItem(R.id.checkable_menu);
        item2.setVisible(false);
    }

    public void onRefreshFeed(View view) {
        Navigation.findNavController(view).navigate(R.id.action_RoverFeed_to_loading);
    }

    public FloatingActionButton getFloatingButton() {
        return floatingButton;
    }
}
