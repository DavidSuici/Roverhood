package com.suici.roverhood.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseAccessCodeHasher;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.databinding.FragmentLogInBinding;
import com.suici.roverhood.dialogs.CreateUser;
import com.suici.roverhood.models.User;

import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;

import java.util.Map;

public class LogIn extends Fragment {

    private FragmentLogInBinding binding;
    private boolean isLoading = false;
    private boolean isLoggingIn = false;
    private FirebaseRepository firebaseRepository;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentLogInBinding.inflate(inflater, container, false);

        // Only activate after manually adding new uninitialised users with new accessCodes
        // Will stay commented unless needed.

        // FirebaseAccessCodeHasher.hashAllAccessCodes();

        // Auto-select the text when clicking on this
        setSelectAllOnFocus(binding.usernameText);
        setSelectAllOnFocus(binding.accessCodeText);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        firebaseRepository = FirebaseRepository.getInstance(requireContext());

        User loggedInUser = localDB.getLoggedInUser();
        User prevUser = localDB.getPrevLoggedInUser();

        // If any user is logged in, sets current user information in textboxes
        if (loggedInUser != null) {
            ((MainActivity) requireActivity()).setCurrentUser(loggedInUser);
            binding.usernameText.setText(loggedInUser.getUsername());
            binding.accessCodeText.setText(loggedInUser.getAccessCode());

            reLogActiveUser(loggedInUser);
        } else {
            // Sets last logged user information in textboxes
            if (prevUser != null) {
                binding.usernameText.setText(prevUser.getUsername());
                binding.accessCodeText.setText(prevUser.getAccessCode());
            }
        }

        // Starts logging in when pressed enter in AccessCode textbox
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
            if (isLoggingIn) return;

            if(!isLoading) {
                startLoadingUI();
                syncUserDB();
            }

            String username = binding.usernameText.getText().toString().trim();
            String accessCode = binding.accessCodeText.getText().toString().trim();

            // Waits 2.5s for syncUserDB to finish
            final int[] counter2 = {0};
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    counter2[0]++;
                    if (isLoading && counter2[0] < 5) {
                        new android.os.Handler().postDelayed(this, 500);
                    } else {
                        // Log into the previous User if correct data is introduced
                        User prevUser = localDB.getPrevLoggedInUser();
                        if (prevUser != null && prevUser.getUsername().equals(username)
                            && isBCryptHash(accessCode)) {
                            logIn(prevUser);
                            return;
                        }

                        // Log into the realtime User if correct data is introduced
                        User user = localDB.getUserByUsernameAndAccessCode(username, accessCode);
                        if (user != null) {
                            logIn(user);
                            return;
                        } else {
                            Toast.makeText(requireContext(), "Username or AccessCode are wrong", Toast.LENGTH_SHORT).show();
                        }

                        endLoadingUI();
                    }
                }
            }, 500);
        });

        // Opens a dialog for creating a new user
        binding.buttonCreateAccount.setOnClickListener(v -> {
            CreateUser createUserFragment = new CreateUser();
            createUserFragment.setOriginalFragment(this);
            createUserFragment.show(requireActivity().getSupportFragmentManager(), "createUserFragment");
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void startLoadingUI() {
        isLoggingIn = true;
        isLoading = true;

        binding.usernameText.setEnabled(false);
        binding.accessCodeText.setEnabled(false);
        binding.buttonCreateAccount.setEnabled(false);
        binding.buttonLogIn.setEnabled(false);
        binding.buttonLogIn.setText("");
        binding.loadingLogIn.setVisibility(View.VISIBLE);
    }

    private void endLoadingUI() {
        isLoading = false;
        isLoggingIn = false;

        binding.usernameText.setEnabled(true);
        binding.accessCodeText.setEnabled(true);
        binding.buttonCreateAccount.setEnabled(true);
        binding.buttonLogIn.setEnabled(true);
        binding.buttonLogIn.setText("Log In");
        binding.loadingLogIn.setVisibility(View.GONE);
    }

    private void logIn(User user) {
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        ((MainActivity) requireActivity()).setCurrentUser(user);
        localDB.markLoggedIn(user.getId());
        NavHostFragment.findNavController(LogIn.this)
                .navigate(R.id.action_LogIn_to_RoverFeed);
    }

    // Waits maximum of 2.5 seconds and automatically logs in the currently logged user
    private void reLogActiveUser(User loggedInUser) {
        if(!isLoading) {
            startLoadingUI();
            syncUserDB();
        }

        final int[] counter = {0};
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                counter[0]++;
                if (isLoading && counter[0] < 25) {
                    new android.os.Handler().postDelayed(this, 100);
                } else {
                    if (loggedInUser != null) {
                        NavHostFragment.findNavController(LogIn.this)
                                .navigate(R.id.action_LogIn_to_RoverFeed);
                    }
                    endLoadingUI();
                }
            }
        }, 100);
    }

    // Saves all the user information from Firebase in local database
    private void syncUserDB()
    {
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        localDB.setUsersOffline();

        firebaseRepository.getAllUsers(new FirebaseRepository.UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                if(!isLoading)
                    return;

                for (User user : users.values()) {
                    localDB.insertUser(user);
                }
                isLoading = false;
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), "Failed to connect to online Database.", Toast.LENGTH_LONG).show();
                isLoading = false;
            }
        });
    }

    public void setLogInDetails(String username, String accessCode) {
        binding.usernameText.setText(username);
        binding.accessCodeText.setText(accessCode);
        binding.buttonLogIn.performClick();
    }

    private boolean isBCryptHash(String accessCode) {
        return accessCode.startsWith("$2a$") || accessCode.startsWith("$2b$") || accessCode.startsWith("$2y$");
    }

    private void setSelectAllOnFocus(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                editText.post(editText::selectAll);
            }
        });
    }
}