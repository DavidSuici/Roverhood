package com.suici.roverhood.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.textfield.TextInputEditText;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// List of all created accounts: photo, name, role and team, with search. Opened from the
// search button (everyone) and the + menu (staff). Staff also get Edit and Delete; participants
// only search and tap a name to see that person's posts and profile.
// Admins can edit/delete anyone but themselves (they can edit themselves); organizers can edit
// non-admins and delete only participants.
public class ManageUsers extends DialogFragment {

    private final RoverFeed feed;

    private Context context;
    private FirebaseRepository firebaseRepository;
    private User currentUser;

    private TextView title;
    private ProgressBar loading;
    private LinearLayout list;
    private TextInputEditText search;

    private final List<User> users = new ArrayList<>();

    public ManageUsers(RoverFeed feed) {
        this.feed = feed;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_manage_users, container, false);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);
        currentUser = ((MainActivity) requireActivity()).getCurrentUser();

        title = view.findViewById(R.id.usersTitle);
        loading = view.findViewById(R.id.usersLoading);
        list = view.findViewById(R.id.usersList);
        search = view.findViewById(R.id.usersSearch);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { showUsers(); }
        });

        loadUsers();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }

    // Offline (the feed shows saved data): read-only list from the local database
    private boolean isOffline() {
        return feed != null && feed.isOfflineMode();
    }

    // Always fresh from Firebase, so edits from other phones are visible; offline from the phone
    private void loadUsers() {
        if (isOffline()) {
            applyUsers(com.suici.roverhood.databases.LocalDatabase.getInstance(context).getAllUsers());
            return;
        }
        loading.setVisibility(View.VISIBLE);
        firebaseRepository.getAllUsers(new FirebaseRepository.UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> fetched) {
                if (!isAdded()) return;
                applyUsers(fetched);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyUsers(Map<String, User> fetched) {
        users.clear();
        for (User user : fetched.values()) {
            // Accounts without a username are unused activation codes (see Activation codes)
            if (user.getUsername() != null && !user.getUsername().isEmpty()) users.add(user);
        }
        users.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
        loading.setVisibility(View.GONE);
        showUsers();
    }

    private void showUsers() {
        String query = search.getText() != null ? search.getText().toString().trim().toLowerCase() : "";
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int shown = 0;

        for (User user : users) {
            String teamLabel = Teams.label(user.getTeam());
            if (!query.isEmpty() && !user.getUsername().toLowerCase().contains(query)
                    && !Teams.name(user.getTeam()).toLowerCase().contains(query)) continue;
            shown++;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            // Photo (only if the user has one)
            String photoUrl = UserProfiles.photoUrl(user.getId());
            if (!photoUrl.isEmpty()) {
                ImageView photo = new ImageView(context);
                int size = (int) (40 * density);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMarginEnd((int) (10 * density));
                photo.setLayoutParams(params);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                UserProfiles.loadPhoto(photo, user.getId());   // rounded corners, like everywhere
                row.addView(photo);
            }

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(context);
            name.setText(user.getUsername());
            name.setTextSize(17);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            texts.addView(name);

            TextView details = new TextView(context);
            details.setText(user.getUserType() + " · " + teamLabel);
            details.setTextSize(13);
            details.setAlpha(0.7f);
            texts.addView(details);

            // Tap the person: close the list and show their posts and profile
            texts.setOnClickListener(v -> {
                dismiss();
                if (feed != null) feed.filterByUser(user.getUsername());
            });
            row.addView(texts);

            if (canEdit(user)) {
                TextView edit = new TextView(context);
                edit.setText("Edit");
                edit.setTextColor(com.google.android.material.color.MaterialColors.getColor(edit, com.google.android.material.R.attr.colorPrimary));
                edit.setTypeface(null, android.graphics.Typeface.BOLD);
                edit.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
                edit.setOnClickListener(v -> editUser(user));
                row.addView(edit);
            }

            if (canDelete(user)) {
                ImageButton delete = new ImageButton(context);
                delete.setImageResource(android.R.drawable.ic_menu_delete);
                delete.setBackgroundResource(android.R.color.transparent);
                delete.setContentDescription("Delete user");
                delete.setOnClickListener(v -> confirmDelete(user));
                row.addView(delete);
            }

            list.addView(row);
        }

        title.setText("Users (" + shown + ")" + (isOffline() ? " · offline" : ""));
    }

    private boolean isAdmin() {
        return currentUser != null && "ADMIN".equals(currentUser.getUserType());
    }

    private boolean isSelf(User user) {
        return currentUser != null && user.getId().equals(currentUser.getId());
    }

    private boolean canEdit(User user) {
        if (isOffline() || !com.suici.roverhood.utils.Roles.isStaff(currentUser)) return false;
        return isAdmin() || isSelf(user) || !"ADMIN".equals(user.getUserType());
    }

    private boolean canDelete(User user) {
        if (isOffline() || isSelf(user) || !com.suici.roverhood.utils.Roles.isStaff(currentUser)) return false;
        return isAdmin() || "PARTICIPANT".equals(user.getUserType());
    }

    private void editUser(User user) {
        EditProfile dialog = new EditProfile(user, isSelf(user), true, (oldUsername, newUsername) -> {
            if (feed != null) feed.onProfileSaved(oldUsername, newUsername);
            if (isAdded()) loadUsers();
        });
        dialog.show(getParentFragmentManager(), "editProfile");
    }

    private void confirmDelete(User user) {
        new AlertDialog.Builder(context)
                .setTitle("Delete " + user.getUsername() + "?")
                .setMessage("Everything that belongs to " + user.getUsername() + " is deleted: the account, "
                        + "profile photo, posts (with their comments and images), comments and likes. "
                        + "This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) ->
                        firebaseRepository.deleteUser(user.getId(), new FirebaseRepository.PostOperationCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(context, user.getUsername() + " deleted", Toast.LENGTH_SHORT).show();
                                loadUsers();
                                if (feed != null) feed.refreshFeedAndFilters();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (!isAdded()) return;
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
