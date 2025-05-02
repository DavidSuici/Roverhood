package com.suici.roverhood;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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

        LocalDatabase dbHelper = new LocalDatabase(requireContext());
        usersRef.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot child : snapshot.getChildren()) {
                String userId = child.getKey();
                User user = child.getValue(User.class);
                user.setId(userId);
                dbHelper.insertUser(user);
            }
        });
        dbHelper.logAllUsers();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LocalDatabase dbHelper = new LocalDatabase(requireContext());
        if (dbHelper.getLoggedInUser() != null)
            ((MainActivity) requireActivity()).setCurrentUser(dbHelper.getLoggedInUser());
        if(((MainActivity) requireActivity()).getCurrentUser() != null)
            binding.editTextText.setText(((MainActivity) requireActivity()).getCurrentUser().username);
        else
            if(dbHelper.getPrevLoggedInUser() != null)
                binding.editTextText.setText(dbHelper.getPrevLoggedInUser().username);

        view.post(() -> {
            User currentUser = ((MainActivity) requireActivity()).getCurrentUser();
            User dbUser = dbHelper.getLoggedInUser();

            if (currentUser != null && dbUser != null &&
                    currentUser.username != null && dbUser.username != null &&
                    currentUser.username.equals(dbUser.username)) {

                NavHostFragment.findNavController(LogIn.this)
                        .navigate(R.id.action_LogIn_to_RoverFeed);
            }
        });

        binding.buttonLogIn.setOnClickListener(v -> {
            String username = binding.editTextText.getText().toString().trim();
            User user = dbHelper.getUserByUsername(username);
            ((MainActivity) requireActivity()).setCurrentUser(user);
            if (user != null) {
                dbHelper.markLoggedIn(user.id);
                NavHostFragment.findNavController(LogIn.this)
                        .navigate(R.id.action_LogIn_to_RoverFeed);
            } else {
                Toast.makeText(requireContext(), "Username or Password wrong", Toast.LENGTH_SHORT).show();
            }

        });
    }

    @Override
    public void onDestroyView() {
        MainActivity activity = (MainActivity) requireActivity();
        Menu menu = activity.getOptionsMenu();
        if (menu != null) {
            MenuItem item = menu.findItem(R.id.checkable_menu);
            if (item != null && item.getActionView() != null) {
                SwitchCompat announcementsFilter = item.getActionView().findViewById(R.id.switch2);
                if (announcementsFilter != null) {
                    announcementsFilter.setChecked(false);
                }
            }
        }

        super.onDestroyView();
        binding = null;
    }
}