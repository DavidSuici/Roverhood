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
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;

import java.util.Objects;

public class LogIn extends Fragment {

    private LogInBinding binding;

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
        if(((MainActivity) requireActivity()).getCurrentUser() != null) {
            binding.usernameText.setText(((MainActivity) requireActivity()).getCurrentUser().username);
            binding.accessCodeText.setText(((MainActivity) requireActivity()).getCurrentUser().accessCode);
        }
        else
            if(dbHelper.getPrevLoggedInUser() != null) {
                binding.usernameText.setText(dbHelper.getPrevLoggedInUser().username);
                binding.accessCodeText.setText(dbHelper.getPrevLoggedInUser().accessCode);
            }

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

        binding.accessCodeText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {

                binding.buttonLogIn.performClick();  // Simulate button click
                return true; // Handled
            }
            return false;
        });

        binding.buttonLogIn.setOnClickListener(v -> {
            String username = binding.usernameText.getText().toString().trim();
            String accessCode = binding.accessCodeText.getText().toString().trim();
            User user = dbHelper.getUserByUsernameAndAccessCode(username, accessCode);
            ((MainActivity) requireActivity()).setCurrentUser(user);
            if (user != null) {
                dbHelper.markLoggedIn(user.id);
                NavHostFragment.findNavController(LogIn.this)
                        .navigate(R.id.action_LogIn_to_RoverFeed);
            } else {
                Toast.makeText(requireContext(), "Username or AccessCode wrong", Toast.LENGTH_SHORT).show();
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