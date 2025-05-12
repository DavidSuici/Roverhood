package com.suici.roverhood;

import android.os.Bundle;
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
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;

public class LogIn extends Fragment {

    private LogInBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = LogInBinding.inflate(inflater, container, false);

        // Perform sync with Firebase
        syncUserDB();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());

        // If any user is logged in, set current user information in textboxes
        if (localDB.getLoggedInUser() != null) {
            ((MainActivity) requireActivity()).setCurrentUser(localDB.getLoggedInUser());
            binding.usernameText.setText(((MainActivity) requireActivity()).getCurrentUser().username);
            binding.accessCodeText.setText(((MainActivity) requireActivity()).getCurrentUser().accessCode);
        }
        else {
            // Set last logged user information in textboxes
            if (localDB.getPrevLoggedInUser() != null) {
                binding.usernameText.setText(localDB.getPrevLoggedInUser().username);
                binding.accessCodeText.setText(localDB.getPrevLoggedInUser().accessCode);
            }
        }

        // If there is a loggedIn user, skip to the Feed
        view.post(() -> {
            if (localDB.getLoggedInUser() != null) {
                NavHostFragment.findNavController(LogIn.this)
                        .navigate(R.id.action_LogIn_to_RoverFeed);
            }
        });

        // LogIn when pressed enter in AccesCode textbox
        binding.accessCodeText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {

                binding.buttonLogIn.performClick();
                return true;
            }
            return false;
        });

        // LogIn - button logic
        binding.buttonLogIn.setOnClickListener(v -> {
            syncUserDB();
            String username = binding.usernameText.getText().toString().trim();
            String accessCode = binding.accessCodeText.getText().toString().trim();
            User user = localDB.getUserByUsernameAndAccessCode(username, accessCode);

            if (user != null) {
                ((MainActivity) requireActivity()).setCurrentUser(user);
                localDB.markLoggedIn(user.id);
                NavHostFragment.findNavController(LogIn.this)
                        .navigate(R.id.action_LogIn_to_RoverFeed);
            } else {
                Toast.makeText(requireContext(), "Username or AccessCode are wrong", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void syncUserDB()
    {
        // Store all users from Firebase to Local DB
        DatabaseReference usersRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");

        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());

        usersRef.get()
                .addOnSuccessListener(snapshot -> {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String userId = child.getKey();
                        User user = child.getValue(User.class);
                        user.setId(userId);

                        localDB.insertUser(user);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to connect to online Database.", Toast.LENGTH_LONG).show();
                });
    }
}