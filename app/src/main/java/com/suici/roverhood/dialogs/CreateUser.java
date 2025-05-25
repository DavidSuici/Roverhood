package com.suici.roverhood.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.annotation.NonNull;

import com.suici.roverhood.fragments.LogIn;
import com.suici.roverhood.R;
import com.suici.roverhood.models.User;
import com.suici.roverhood.databases.FirebaseRepository;

import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateUser extends DialogFragment {

    private Context context;
    private FirebaseRepository firebaseRepository;
    private LogIn originalFragment;

    private EditText editTextUsername;
    private EditText editTextAccessCode;
    private Button createUserButton;
    private ProgressBar loadingCreateUser;

    private boolean isLoading = false;
    private Map<String, String> accessCodes = new HashMap<>();
    private List<String> takenUsernames = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_create_user, container, false);
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        context = getContext();
        firebaseRepository = FirebaseRepository.getInstance(context);

        editTextUsername = view.findViewById(R.id.username_text);
        editTextAccessCode = view.findViewById(R.id.access_code_text);
        createUserButton = view.findViewById(R.id.buttonCreateAccount);
        loadingCreateUser = view.findViewById(R.id.loadingCreateUser);

        createUserButton.setOnClickListener(v -> {
            startLoadingUI();

            String newUsername = editTextUsername.getText().toString();

            if (newUsername.isEmpty()) {
                Toast.makeText(context, "Username is empty", Toast.LENGTH_SHORT).show();
                endLoadingUI();
                return;
            }

            if (newUsername.length() > 15) {
                Toast.makeText(context, "Username is " + (newUsername.length() - 15) + " characters too long", Toast.LENGTH_SHORT).show();
                endLoadingUI();
                return;
            }

            if (newUsername.length() < 5) {
                Toast.makeText(context, "Username is " + (5 - newUsername.length()) + " characters too short", Toast.LENGTH_SHORT).show();
                endLoadingUI();
                return;
            }

            if (!newUsername.matches("^[a-zA-Z0-9]*$")) {
                Toast.makeText(context, "Username can only contain letters and numbers", Toast.LENGTH_SHORT).show();
                endLoadingUI();
                return;
            }

            getAvailableAccessCodes();
            final int[] counter2 = {0};
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    counter2[0]++;
                    if (isLoading && counter2[0] < 5) {
                        new android.os.Handler().postDelayed(this, 500);
                    } else {
                        if(isLoading) {
                            Toast.makeText(requireContext(), "Failed to load available Access Codes", Toast.LENGTH_SHORT).show();
                            endLoadingUI();
                            return;
                        }

                        if (takenUsernames.contains(newUsername.toLowerCase())) {
                            Toast.makeText(context, "Username already taken", Toast.LENGTH_SHORT).show();
                            endLoadingUI();
                            return;
                        }

                        String inputAccessCode = editTextAccessCode.getText().toString().trim();
                        String matchedUserId = null;

                        for (Map.Entry<String, String> entry : accessCodes.entrySet()) {
                            String userId = entry.getKey();
                            String hashedAccessCode = entry.getValue();

                            if (BCrypt.checkpw(inputAccessCode, hashedAccessCode)) {
                                matchedUserId = userId;
                                break;
                            }
                        }

                        if (matchedUserId == null) {
                            Toast.makeText(context, "Invalid accessCode", Toast.LENGTH_SHORT).show();
                            endLoadingUI();
                        } else {
                            firebaseRepository.setUsernameIfNewUser(matchedUserId, newUsername, new FirebaseRepository.PostOperationCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(context, "Account created", Toast.LENGTH_SHORT).show();
                                    updateLogInDetails();
                                    endLoadingUI();
                                    dismiss();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                                    endLoadingUI();
                                }
                            });
                        }
                    }
                }
            }, 500);
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Need this or the fragment wont load
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void setOriginalFragment(LogIn logIn) {
        this.originalFragment = logIn;
    }

    private void startLoadingUI() {
        isLoading = true;
        editTextUsername.setEnabled(false);
        editTextAccessCode.setEnabled(false);
        createUserButton.setEnabled(false);
        createUserButton.setText("");
        loadingCreateUser.setVisibility(View.VISIBLE);
    }

    private void endLoadingUI() {
        isLoading = false;
        editTextUsername.setEnabled(true);
        editTextAccessCode.setEnabled(true);
        createUserButton.setEnabled(true);
        createUserButton.setText("Create User");
        loadingCreateUser.setVisibility(View.GONE);
    }

    private void getAvailableAccessCodes() {
        accessCodes.clear();
        takenUsernames.clear();

        firebaseRepository.getAllUsers(new FirebaseRepository.UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                if(!isLoading)
                    return;

                for (User user : users.values()) {
                    if (user.getUsername() == null || user.getUsername().isEmpty()) {
                        accessCodes.put(user.getId(), user.getAccessCode());
                    } else {
                        takenUsernames.add(user.getUsername().toLowerCase());
                    }
                }

                Log.d("AvailableUsers", "There are " + accessCodes.size() + " available access codes.");
                Log.d("AvailableUsers", "There are " + takenUsernames.size() + " taken usernames.");
                isLoading = false;
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), "Failed to connect to online Database.", Toast.LENGTH_LONG).show();
                isLoading = false;
            }
        });
    }

    private void updateLogInDetails() {
        String newUsername = editTextUsername.getText().toString();
        String newAccessCode = editTextAccessCode.getText().toString();
        originalFragment.setLogInDetails(newUsername, newAccessCode);
    }
}