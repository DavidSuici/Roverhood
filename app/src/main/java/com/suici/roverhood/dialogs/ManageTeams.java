package com.suici.roverhood.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.color.MaterialColors;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Teams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Teams for organizers and admins: each team with its emoji, name and members.
// Tap a team to see its members, tap a member to edit them (same rules as the Users list).
// Teams can be created, renamed (name/emoji) and deleted, but only when no account is in them;
// deleting a team also deletes its unused activation codes.
public class ManageTeams extends DialogFragment {

    private final RoverFeed feed;

    private Context context;
    private FirebaseRepository firebaseRepository;
    private User currentUser;

    private TextView title;
    private ProgressBar loading;
    private LinearLayout list;

    // team key -> members (accounts with a username) / number of unused codes
    private final Map<String, List<User>> members = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Integer> unusedCodes = new HashMap<>();
    private final java.util.Set<String> expanded = new java.util.HashSet<>();

    public ManageTeams(RoverFeed feed) {
        this.feed = feed;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_manage_teams, container, false);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);
        currentUser = ((MainActivity) requireActivity()).getCurrentUser();

        title = view.findViewById(R.id.teamsTitle);
        loading = view.findViewById(R.id.teamsLoading);
        list = view.findViewById(R.id.teamsList);
        view.findViewById(R.id.newTeamButton).setOnClickListener(v -> showNewTeamDialog());

        loadTeams();
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

    // Fresh team names/emojis and users from Firebase
    private void loadTeams() {
        loading.setVisibility(View.VISIBLE);
        firebaseRepository.fetchTeams(() -> { });
        firebaseRepository.getAllUsers(new FirebaseRepository.UsersCallback() {
            @Override
            public void onUsersFetched(Map<String, User> users) {
                if (!isAdded()) return;
                members.clear();
                unusedCodes.clear();
                for (String key : Teams.allKeys(context)) members.put(key, new ArrayList<>());

                for (User user : users.values()) {
                    String team = user.getTeam();
                    if (team == null || team.isEmpty()) continue;
                    if (user.getUsername() == null || user.getUsername().isEmpty()) {
                        unusedCodes.merge(team, 1, Integer::sum);
                        members.computeIfAbsent(team, k -> new ArrayList<>());
                    } else {
                        members.computeIfAbsent(team, k -> new ArrayList<>()).add(user);
                    }
                }
                for (List<User> teamMembers : members.values()) {
                    teamMembers.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                }
                loading.setVisibility(View.GONE);
                showTeams();
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showTeams() {
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        title.setText("Teams (" + members.size() + ")");

        for (Map.Entry<String, List<User>> entry : members.entrySet()) {
            String team = entry.getKey();
            List<User> teamMembers = entry.getValue();
            int codes = unusedCodes.getOrDefault(team, 0);

            // Team row: emoji, name, counts, Edit, Delete (only when empty)
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

            TextView emoji = new TextView(context);
            emoji.setText(Teams.emoji(team));
            emoji.setTextSize(24);
            emoji.setMinWidth((int) (36 * density));
            row.addView(emoji);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(context);
            name.setText(Teams.name(team));
            name.setTextSize(18);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            texts.addView(name);

            TextView details = new TextView(context);
            String countText = teamMembers.size() + (teamMembers.size() == 1 ? " member" : " members");
            if (codes > 0) countText += " · " + codes + (codes == 1 ? " unused code" : " unused codes");
            details.setText(countText);
            details.setTextSize(13);
            details.setAlpha(0.7f);
            texts.addView(details);
            row.addView(texts);

            TextView edit = new TextView(context);
            edit.setText("Edit");
            edit.setTextColor(MaterialColors.getColor(edit, com.google.android.material.R.attr.colorPrimary));
            edit.setTypeface(null, android.graphics.Typeface.BOLD);
            edit.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
            edit.setOnClickListener(v -> {
                if (feed != null) feed.showEditTeamDialog(team, () -> { if (isAdded()) showTeams(); });
            });
            row.addView(edit);

            if (teamMembers.isEmpty()) {
                ImageButton delete = new ImageButton(context);
                delete.setImageResource(android.R.drawable.ic_menu_delete);
                delete.setBackgroundResource(android.R.color.transparent);
                delete.setContentDescription("Delete team");
                delete.setOnClickListener(v -> confirmDelete(team, codes));
                row.addView(delete);
            }

            row.setOnClickListener(v -> {
                if (!expanded.remove(team)) expanded.add(team);
                showTeams();
            });
            list.addView(row);

            // Members (when the team is expanded)
            if (expanded.contains(team)) {
                if (teamMembers.isEmpty()) {
                    TextView empty = new TextView(context);
                    empty.setText(codes > 0 ? "No members yet (only unused codes)" : "No members");
                    empty.setAlpha(0.6f);
                    empty.setPadding((int) (36 * density), 0, 0, (int) (8 * density));
                    list.addView(empty);
                }
                for (User member : teamMembers) {
                    TextView memberRow = new TextView(context);
                    memberRow.setText(member.getUsername() + "   " + member.getUserType());
                    memberRow.setTextSize(15);
                    memberRow.setPadding((int) (36 * density), (int) (6 * density), 0, (int) (6 * density));
                    if (canEdit(member)) {
                        memberRow.setOnClickListener(v -> editMember(member));
                        memberRow.setTextColor(MaterialColors.getColor(memberRow, com.google.android.material.R.attr.colorPrimary));
                    } else {
                        memberRow.setAlpha(0.6f);
                    }
                    list.addView(memberRow);
                }
            }
        }
    }

    private boolean canEdit(User user) {
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getUserType());
        boolean isSelf = currentUser != null && user.getId().equals(currentUser.getId());
        return isAdmin || isSelf || !"ADMIN".equals(user.getUserType());
    }

    private void editMember(User member) {
        boolean isSelf = currentUser != null && member.getId().equals(currentUser.getId());
        EditProfile dialog = new EditProfile(member, isSelf, true, (oldUsername, newUsername) -> {
            if (feed != null) feed.onProfileSaved(oldUsername, newUsername);
            if (isAdded()) loadTeams();
        });
        dialog.show(getParentFragmentManager(), "editProfile");
    }

    private void showNewTeamDialog() {
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding / 2, padding, 0);

        EditText nameInput = new EditText(context);
        nameInput.setHint("Team name");
        nameInput.setSingleLine(true);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        form.addView(nameInput);

        EditText emojiInput = new EditText(context);
        emojiInput.setHint("Emoji");
        emojiInput.setSingleLine(true);
        emojiInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        form.addView(emojiInput);

        // Optional: activation codes for the new team's members (PARTICIPANT), created right away
        EditText codesInput = new EditText(context);
        codesInput.setHint("Activation codes to create (optional, max 50)");
        codesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codesInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        form.addView(codesInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("New team")
                .setView(form)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        // Overriding the click so the dialog stays open on validation errors
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String emoji = emojiInput.getText().toString().trim();

            String error = Teams.validateTeamKey(name);
            if (error != null) {
                nameInput.setError(error);
                return;
            }
            for (String existing : members.keySet()) {
                if (existing.equalsIgnoreCase(name)) {
                    nameInput.setError("This team already exists");
                    return;
                }
            }
            if (!Teams.isSingleEmoji(emoji)) {
                emojiInput.setError("Pick exactly one emoji");
                return;
            }
            String codesText = codesInput.getText().toString().trim();
            int codesCount = codesText.isEmpty() ? 0 : Integer.parseInt(codesText);
            if (codesCount > 50) {
                codesInput.setError("At most 50 codes at once");
                return;
            }

            firebaseRepository.updateTeam(name, name, emoji, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    if (!isAdded()) return;
                    Toast.makeText(context, "Team " + name + " created", Toast.LENGTH_SHORT).show();
                    members.put(name, new ArrayList<>());
                    showTeams();
                    if (codesCount > 0) createCodesForTeam(name, codesCount);
                }

                @Override
                public void onError(String errorMessage) {
                    if (!isAdded()) return;
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
            dialog.dismiss();
        }));
        dialog.show();
    }

    // Creates the codes one after another (each one is hashed, which takes a moment), then
    // shows them all with a "Copy all" button. They also appear in Activation codes.
    private void createCodesForTeam(String team, int count) {
        List<String> created = new ArrayList<>();
        AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("Creating codes")
                .setMessage("0 / " + count)
                .setCancelable(false)
                .show();

        Runnable[] next = new Runnable[1];
        int[] collisions = {0};
        next[0] = () -> {
            if (created.size() >= count) {
                progress.dismiss();
                showCreatedCodes(team, created);
                return;
            }
            firebaseRepository.createActivationCode(ActivationCodes.randomCode(), team, "PARTICIPANT",
                    currentUser.getId(), new FirebaseRepository.CodeCreationCallback() {
                        @Override
                        public void onCreated(String code) {
                            created.add(code);
                            requireActivity().runOnUiThread(() -> {
                                progress.setMessage(created.size() + " / " + count);
                                next[0].run();
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // A random code collided with an unused one: just try another
                            // (limited, in case almost all combinations are taken)
                            if (errorMessage.contains("already exists") && ++collisions[0] <= 50) {
                                requireActivity().runOnUiThread(next[0]);
                                return;
                            }
                            requireActivity().runOnUiThread(() -> {
                                progress.dismiss();
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                                if (!created.isEmpty()) showCreatedCodes(team, created);
                            });
                        }
                    });
        };
        next[0].run();
    }

    private void showCreatedCodes(String team, List<String> codes) {
        if (!isAdded()) return;
        unusedCodes.put(team, unusedCodes.getOrDefault(team, 0) + codes.size());
        showTeams();

        String text = String.join("\n", codes);
        new AlertDialog.Builder(context)
                .setTitle(codes.size() + " codes for " + Teams.name(team))
                .setMessage(text)
                .setPositiveButton("Copy all", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                            "Activation codes", Teams.name(team) + ":\n" + text));
                    Toast.makeText(context, "Codes copied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void confirmDelete(String team, int codes) {
        String message = codes == 0
                ? "The team has no members."
                : "The team has no members. Its " + (codes == 1 ? "unused code" : codes + " unused codes")
                        + " will also be deleted, so nobody will be able to create an account with "
                        + (codes == 1 ? "it." : "them.");
        new AlertDialog.Builder(context)
                .setTitle("Delete " + Teams.name(team) + "?")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) ->
                        firebaseRepository.deleteTeam(team, new FirebaseRepository.PostOperationCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(context, "Team deleted", Toast.LENGTH_SHORT).show();
                                members.remove(team);
                                unusedCodes.remove(team);
                                expanded.remove(team);
                                showTeams();
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
