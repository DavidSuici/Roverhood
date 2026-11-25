package com.suici.roverhood.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.Date;

import java.security.SecureRandom;
import java.util.List;

// Organizers and admins create activation codes (a code + team + role) and see / delete the
// unused ones. Someone then creates their account with the code (Create account on login).
// Organizers and moderators can create PARTICIPANT, ORGANIZER and MODERATOR codes; only admins ADMIN codes.
public class ActivationCodes extends DialogFragment {


    private Context context;
    private FirebaseRepository firebaseRepository;
    private User currentUser;

    private TextInputLayout codeLayout;
    private TextInputEditText codeInput;
    private TextInputLayout teamLayout;
    private MaterialAutoCompleteTextView teamInput;
    private MaterialAutoCompleteTextView roleInput;
    private Button createButton;
    private View createdCodeGroup;
    private TextView createdCodeText;
    private ProgressBar unusedLoading;
    private LinearLayout unusedList;
    private TextView unusedLabel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_activation_codes, container, false);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);
        currentUser = ((MainActivity) requireActivity()).getCurrentUser();

        codeLayout = view.findViewById(R.id.codeLayout);
        codeInput = view.findViewById(R.id.codeInput);
        teamLayout = view.findViewById(R.id.codeTeamLayout);
        teamInput = view.findViewById(R.id.codeTeamInput);
        roleInput = view.findViewById(R.id.codeRoleInput);
        createButton = view.findViewById(R.id.createCodeButton);
        createdCodeGroup = view.findViewById(R.id.createdCodeGroup);
        createdCodeText = view.findViewById(R.id.createdCodeText);
        unusedLoading = view.findViewById(R.id.unusedCodesLoading);
        unusedList = view.findViewById(R.id.unusedCodesList);
        unusedLabel = view.findViewById(R.id.unusedCodesLabel);

        codeInput.setText(randomCode());
        view.findViewById(R.id.newRandomCodeButton).setOnClickListener(v -> codeInput.setText(randomCode()));

        teamInput.setSimpleItems(Teams.allKeys(context).toArray(new String[0]));
        if (currentUser != null && currentUser.getTeam() != null) teamInput.setText(currentUser.getTeam(), false);

        // Staff can create Participant, Organizer and Moderator codes; only admins also Admin codes
        boolean isAdmin = com.suici.roverhood.utils.Roles.isAdmin(currentUser);
        String[] roles = isAdmin ? com.suici.roverhood.utils.Roles.ALL : new String[]{
                com.suici.roverhood.utils.Roles.PARTICIPANT,
                com.suici.roverhood.utils.Roles.ORGANIZER,
                com.suici.roverhood.utils.Roles.MODERATOR};
        roleInput.setSimpleItems(roles);
        roleInput.setText(roles[0], false);

        createButton.setOnClickListener(v -> createCode());
        view.findViewById(R.id.copyCodeButton).setOnClickListener(v -> {
            String code = (String) createdCodeText.getTag();
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Activation code", code));
            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show();
        });

        loadUnusedCodes();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // Easy to remember: a color + a fruit, e.g. "BlueMango" (16 x 25 = 400 combinations).
    // Uniqueness among the unused codes is checked when the code is created.
    private static final String[] COLORS = {
            "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink", "Black",
            "White", "Gray", "Brown", "Gold", "Silver", "Violet", "Cyan", "Teal"
    };
    private static final String[] FRUITS = {
            "Apple", "Banana", "Cherry", "Grape", "Kiwi", "Lemon", "Lime", "Mango", "Melon",
            "Peach", "Pear", "Plum", "Berry", "Apricot", "Coconut", "Fig", "Papaya", "Guava",
            "Cranberry", "Blueberry", "Raspberry", "Strawberry", "Pineapple", "Watermelon", "Quince"
    };

    public static String randomCode() {
        SecureRandom random = new SecureRandom();
        return COLORS[random.nextInt(COLORS.length)] + FRUITS[random.nextInt(FRUITS.length)];
    }

    private void createCode() {
        String code = codeInput.getText() != null ? codeInput.getText().toString().trim() : "";
        if (code.length() < 4) {
            codeLayout.setError("At least 4 characters");
            return;
        }
        if (!code.matches("[A-Za-z0-9]+")) {
            codeLayout.setError("Only letters and numbers");
            return;
        }
        codeLayout.setError(null);

        String typedTeam = teamInput.getText().toString().trim();
        if (!Teams.allKeys(context).contains(typedTeam)) {
            teamLayout.setError("Pick one of the existing teams");
            return;
        }
        teamLayout.setError(null);
        String team = typedTeam;
        String role = roleInput.getText().toString();

        createButton.setEnabled(false);
        createButton.setText("Creating...");
        firebaseRepository.createActivationCode(code, team, role, currentUser.getId(), new FirebaseRepository.CodeCreationCallback() {
            @Override
            public void onCreated(String createdCode) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    resetCreateButton();
                    createdCodeText.setText("Created: " + createdCode);
                    createdCodeText.setTag(createdCode);
                    createdCodeGroup.setVisibility(View.VISIBLE);
                    codeInput.setText(randomCode());
                    loadUnusedCodes();
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    resetCreateButton();
                    codeLayout.setError(errorMessage);
                });
            }
        });
    }

    private void resetCreateButton() {
        createButton.setEnabled(true);
        createButton.setText("Create code");
    }

    private void loadUnusedCodes() {
        unusedLoading.setVisibility(View.VISIBLE);
        firebaseRepository.loadUnusedCodes(new FirebaseRepository.UnusedCodesCallback() {
            @Override
            public void onLoaded(List<FirebaseRepository.UnusedCode> codes) {
                if (!isAdded()) return;
                unusedLoading.setVisibility(View.GONE);
                showUnusedCodes(codes);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                unusedLoading.setVisibility(View.GONE);
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUnusedCodes(List<FirebaseRepository.UnusedCode> codes) {
        unusedLabel.setText("Unused codes (" + codes.size() + ")");
        unusedList.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (FirebaseRepository.UnusedCode unused : codes) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView codeView = new TextView(context);
            if (unused.code != null) {
                codeView.setText(unused.code);
                codeView.setTextSize(17);
                codeView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                codeView.setTextIsSelectable(true);
            } else {
                // Created before codes were kept readable: only the hash exists
                codeView.setText("Hidden (older code)");
                codeView.setTextSize(15);
                codeView.setAlpha(0.6f);
            }
            texts.addView(codeView);

            TextView details = new TextView(context);
            String date = unused.createdAt > 0 ? " · " + Date.formatTimestamp(unused.createdAt) : "";
            details.setText(Teams.label(unused.team) + " · " + unused.userType + date);
            details.setTextSize(13);
            details.setAlpha(0.7f);
            texts.addView(details);
            row.addView(texts);

            ImageButton delete = new ImageButton(context);
            delete.setImageResource(android.R.drawable.ic_menu_delete);
            delete.setBackgroundResource(android.R.color.transparent);
            delete.setContentDescription("Delete code");
            delete.setOnClickListener(v -> confirmDelete(unused));
            row.addView(delete);

            unusedList.addView(row);
        }

        if (codes.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No unused codes");
            empty.setAlpha(0.6f);
            unusedList.addView(empty);
        }
    }

    private void confirmDelete(FirebaseRepository.UnusedCode unused) {
        String label = unused.code != null ? unused.code : "this older code";
        new AlertDialog.Builder(context)
                .setTitle("Delete code")
                .setMessage("Delete " + label + " (" + Teams.name(unused.team) + ", " + unused.userType + ")? Nobody will be able to create an account with it.")
                .setPositiveButton("Delete", (dialog, which) ->
                        firebaseRepository.deleteUnusedCode(unused.userId, new FirebaseRepository.PostOperationCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(context, "Code deleted", Toast.LENGTH_SHORT).show();
                                loadUnusedCodes();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (!isAdded()) return;
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                                loadUnusedCodes();
                            }
                        }))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
