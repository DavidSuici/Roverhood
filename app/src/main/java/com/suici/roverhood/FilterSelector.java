package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeSet;

public class FilterSelector extends DialogFragment {

    private RoverFeed originalFeed;
    private Button saveFiltersButton;
    private boolean isChanged = false;
    private TextView teamLabel;

    private TextView clearFiltersButton;
    private TextView clearUsernameButton;
    private TextView clearTeamButton;
    private TextView clearTopicButton;
    private TextView clearLikesButton;

    private AutoCompleteTextView userFilter;
    private MaterialAutoCompleteTextView teamDropdown;
    private MaterialAutoCompleteTextView topicDropdown;
    private TextInputEditText minLikesInput;
    private SwitchCompat switchOnlyLiked;
    private SwitchCompat switchAnnouncements;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.filter_selector, container, false);
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        isChanged = false;

        userFilter = view.findViewById(R.id.userFilter);
        teamDropdown = view.findViewById(R.id.teamDropdown);
        topicDropdown = view.findViewById(R.id.topicDropdown);
        minLikesInput = view.findViewById(R.id.minLikesInput);
        switchOnlyLiked = view.findViewById(R.id.switchOnlyLiked);
        switchAnnouncements = view.findViewById(R.id.switchAnnouncements);
        saveFiltersButton = view.findViewById(R.id.saveFiltersButton);

        clearFiltersButton = view.findViewById(R.id.clearFiltersButton);
        clearUsernameButton = view.findViewById(R.id.clearUsernameButton);
        clearTeamButton = view.findViewById(R.id.clearTeamButton);
        clearTopicButton = view.findViewById(R.id.clearTopicButton);
        clearLikesButton = view.findViewById(R.id.clearLikesButton);

        teamLabel = view.findViewById(R.id.labelTeam);

        userFilter.setText(FilterOptions.getUsername());
        teamDropdown.setText(FilterOptions.getTeam());
        topicDropdown.setText(FilterOptions.getTopic());
        minLikesInput.setText(String.valueOf(FilterOptions.getMinLikes()));
        switchOnlyLiked.setChecked(FilterOptions.isOnlyLiked());
        switchAnnouncements.setChecked(FilterOptions.isAnnouncementsOnly());

        setSelectAllOnFocus(userFilter);
        setSelectAllOnFocus(minLikesInput);
        populateFilterOptions();
        if (switchAnnouncements.isChecked()) {
            teamDropdown.setText("");
            teamDropdown.setEnabled(false);
            teamLabel.setEnabled(false);
        } else {
            teamDropdown.setEnabled(true);
            teamLabel.setEnabled(true);
        }

        saveFiltersButton.setOnClickListener(v -> {
            String newUserFilter = userFilter.getText().toString();
            String newTeamFilter = teamDropdown.getText().toString();
            String newTopicFilter = topicDropdown.getText().toString();
            String minLikesText = minLikesInput.getText().toString();
            int newMinLikes = minLikesText.isEmpty() ? 0 : Integer.parseInt(minLikesText);
            boolean newOnlyLiked = switchOnlyLiked.isChecked();
            boolean newAnnouncementsOnly = switchAnnouncements.isChecked();

            isChanged = !newUserFilter.equals(FilterOptions.getUsername()) ||
                    !newTeamFilter.equals(FilterOptions.getTeam()) ||
                    !newTopicFilter.equals(FilterOptions.getTopic()) ||
                    newMinLikes != FilterOptions.getMinLikes() ||
                    newOnlyLiked != FilterOptions.isOnlyLiked() ||
                    newAnnouncementsOnly != FilterOptions.isAnnouncementsOnly();

            if (isChanged) {
                FilterOptions.setUsername(newUserFilter);
                FilterOptions.setTeam(newTeamFilter);
                FilterOptions.setTopic(newTopicFilter);
                FilterOptions.setMinLikes(newMinLikes);
                FilterOptions.setOnlyLiked(newOnlyLiked);
                FilterOptions.setAnnouncementsOnly(newAnnouncementsOnly);

                if (originalFeed != null) {
                    originalFeed.refreshFeed();
                }
            }

            dismiss();
        });

        clearFiltersButton.setOnClickListener(v -> {
            userFilter.setText("");
            teamDropdown.setText("");
            topicDropdown.setText("");
            minLikesInput.setText("0");
            switchOnlyLiked.setChecked(false);
            switchAnnouncements.setChecked(false);
        });

        clearUsernameButton.setOnClickListener(v -> {
            userFilter.setText("");
        });

        clearTeamButton.setOnClickListener(v -> {
            teamDropdown.setText("");
        });

        clearTopicButton.setOnClickListener(v -> {
            topicDropdown.setText("");
        });

        clearLikesButton.setOnClickListener(v -> {
            minLikesInput.setText("0");
        });

        switchAnnouncements.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                teamDropdown.setText("");
                teamDropdown.setEnabled(false);
                teamLabel.setEnabled(false);
            } else {
                teamDropdown.setEnabled(true);
                teamLabel.setEnabled(true);
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void setOriginalFeed(RoverFeed roverFeed) {
        this.originalFeed = roverFeed;
    }

    private void setSelectAllOnFocus(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                editText.post(editText::selectAll);
            }
        });
    }

    private void populateFilterOptions() {
        LocalDatabase localDB = LocalDatabase.getInstance(requireContext());
        Map<String, User> users = localDB.getAllUsers();

        List<String> usernames = users.values().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());

        Set<String> teams = users.values().stream()
                .map(User::getTeam)
                .filter(team -> team != null && !team.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> topics = Topic.getAllTopics().stream()
                .map(Topic::getTitle)
                .collect(Collectors.toCollection(TreeSet::new));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, usernames);
        userFilter.setAdapter(adapter);

        ArrayAdapter<String> teamAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(teams));
        teamDropdown.setAdapter(teamAdapter);

        ArrayAdapter<String> topicsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(topics));
        topicDropdown.setAdapter(topicsAdapter);

        teamDropdown.setOnClickListener(v -> teamDropdown.showDropDown());
        teamDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                teamDropdown.showDropDown();
            }
        });

        topicDropdown.setOnClickListener(v -> topicDropdown.showDropDown());
        topicDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                topicDropdown.showDropDown();
            }
        });
    }
}