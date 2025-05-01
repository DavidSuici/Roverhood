package com.suici.roverhood;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.suici.roverhood.databinding.LogInBinding;
import com.suici.roverhood.databinding.RoverFeedBinding;

import java.util.Objects;

public class LogIn extends Fragment {

    private LogInBinding binding;
    boolean loggedIn = false;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = LogInBinding.inflate(inflater, container, false);

        DatabaseReference usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        usersRef.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot child : snapshot.getChildren()) {
                String userId = child.getKey();
                User user = child.getValue(User.class);
                Log.d("FirebaseTest", "User ID: " + userId + ", Name: " + user.username);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (loggedIn) {
            // Delay navigation until view is ready
            view.post(() -> {
                if (NavHostFragment.findNavController(LogIn.this)
                        .getCurrentDestination().getId() == R.id.LogIn) {
                    NavHostFragment.findNavController(LogIn.this)
                            .navigate(R.id.action_LogIn_to_loading);
                }
            });
        }

        binding.buttonLogIn.setOnClickListener(v -> {
            NavHostFragment.findNavController(LogIn.this)
                    .navigate(R.id.action_LogIn_to_loading);
        });
    }

    @Override
    public void onDestroyView() {
        MainActivity activity = (MainActivity) requireActivity();
        SwitchCompat announcementsFilter = Objects.requireNonNull(activity.getOptionsMenu()
                        .findItem(R.id.checkable_menu).getActionView())
                        .findViewById(R.id.switch2);
        announcementsFilter.setChecked(false);

        super.onDestroyView();
        binding = null;
    }
}