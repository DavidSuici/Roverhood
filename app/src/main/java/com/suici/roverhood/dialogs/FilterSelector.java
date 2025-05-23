package com.suici.roverhood.dialogs;

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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.suici.roverhood.models.Filters;
import com.suici.roverhood.R;
import com.suici.roverhood.RoverFeed;
import com.suici.roverhood.models.Topic;
import com.suici.roverhood.models.User;
import com.suici.roverhood.databases.LocalDatabase;

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
    private List<String> validUsernames;

    private TextView ascendingLabel;
    private TextView descendingLabel;
    private TextView likesLabel;
    private TextView dateLabel;

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
    private SwitchCompat switchSortByLikes;
    private SwitchCompat switchOrderAscending;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_filter_selector, container, false);
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        isChanged = false;

        userFilter = view.findViewById(R.id.userFilter);
        teamDropdown = view.findViewById(R.id.teamDropdown);
        topicDropdown = view.findViewById(R.id.topicDropdown);
        minLikesInput = view.findViewById(R.id.minLikesInput);
        switchOnlyLiked = view.findViewById(R.id.switchOnlyLiked);
        switchAnnouncements = view.findViewById(R.id.switchAnnouncements);
        saveFiltersButton = view.findViewById(R.id.saveFiltersButton);
        switchSortByLikes = view.findViewById(R.id.switchSortByLikes);
        switchOrderAscending = view.findViewById(R.id.switchOrderAscending);

        clearFiltersButton = view.findViewById(R.id.clearFiltersButton);
        clearUsernameButton = view.findViewById(R.id.clearUsernameButton);
        clearTeamButton = view.findViewById(R.id.clearTeamButton);
        clearTopicButton = view.findViewById(R.id.clearTopicButton);
        clearLikesButton = view.findViewById(R.id.clearLikesButton);

        teamLabel = view.findViewById(R.id.labelTeam);
        ascendingLabel = view.findViewById(R.id.labelOrderAscending);
        descendingLabel = view.findViewById(R.id.labelOrderDescending);
        likesLabel = view.findViewById(R.id.labelSortByLikes);
        dateLabel = view.findViewById(R.id.labelSortByDate);

        userFilter.setText(Filters.getUsername());
        teamDropdown.setText(Filters.getTeam());
        topicDropdown.setText(Filters.getTopic());
        minLikesInput.setText(String.valueOf(Filters.getMinLikes()));
        switchOnlyLiked.setChecked(Filters.isOnlyLiked());
        switchAnnouncements.setChecked(Filters.isAnnouncementsOnly());
        switchSortByLikes.setChecked(Filters.isSortByLikes());
        switchOrderAscending.setChecked(Filters.isOrderAscending());

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

        if (switchSortByLikes.isChecked()) {
            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
            int defaultColor = MaterialColors.getColor(dateLabel, com.google.android.material.R.attr.colorOnSurface);
            likesLabel.setTextColor(secondaryColor);
            dateLabel.setTextColor(defaultColor);
        } else {
            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
            int defaultColor = MaterialColors.getColor(likesLabel, com.google.android.material.R.attr.colorOnSurface);
            dateLabel.setTextColor(secondaryColor);
            likesLabel.setTextColor(defaultColor);
        }

        if (switchOrderAscending.isChecked()) {
            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
            int defaultColor = MaterialColors.getColor(descendingLabel, com.google.android.material.R.attr.colorOnSurface);
            ascendingLabel.setTextColor(secondaryColor);
            descendingLabel.setTextColor(defaultColor);
        } else {
            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
            int defaultColor = MaterialColors.getColor(ascendingLabel, com.google.android.material.R.attr.colorOnSurface);
            descendingLabel.setTextColor(secondaryColor);
            ascendingLabel.setTextColor(defaultColor);
        }

        saveFiltersButton.setOnClickListener(v -> {
            String newUserFilter = userFilter.getText().toString();
            String newTeamFilter = teamDropdown.getText().toString();
            String newTopicFilter = topicDropdown.getText().toString();
            String minLikesText = minLikesInput.getText().toString();
            int newMinLikes = minLikesText.isEmpty() ? 0 : Integer.parseInt(minLikesText);
            boolean newOnlyLiked = switchOnlyLiked.isChecked();
            boolean newAnnouncementsOnly = switchAnnouncements.isChecked();
            boolean newSortByLikes = switchSortByLikes.isChecked();
            boolean newOrderAscending = switchOrderAscending.isChecked();

            isChanged = !newUserFilter.equalsIgnoreCase(Filters.getUsername()) ||
                    !newTeamFilter.equals(Filters.getTeam()) ||
                    !newTopicFilter.equals(Filters.getTopic()) ||
                    newMinLikes != Filters.getMinLikes() ||
                    newOnlyLiked != Filters.isOnlyLiked() ||
                    newAnnouncementsOnly != Filters.isAnnouncementsOnly() ||
                    newSortByLikes != Filters.isSortByLikes() ||
                    newOrderAscending != Filters.isOrderAscending();

            if (!newUserFilter.isEmpty() && validUsernames.stream().noneMatch(
                    name -> name.equalsIgnoreCase(newUserFilter))) {
                userFilter.setError("There is no user with this username");
                return;
            }

            if (isChanged) {
                Filters.setUsername(newUserFilter);
                Filters.setTeam(newTeamFilter);
                Filters.setTopic(newTopicFilter);
                Filters.setMinLikes(newMinLikes);
                Filters.setOnlyLiked(newOnlyLiked);
                Filters.setAnnouncementsOnly(newAnnouncementsOnly);
                Filters.setSortByLikes(newSortByLikes);
                Filters.setOrderAscending(newOrderAscending);

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
            switchSortByLikes.setChecked(false);
            switchOrderAscending.setChecked(false);
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

        switchSortByLikes.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
                int defaultColor = MaterialColors.getColor(dateLabel, com.google.android.material.R.attr.colorOnSurface);
                likesLabel.setTextColor(secondaryColor);
                dateLabel.setTextColor(defaultColor);
            } else {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
                int defaultColor = MaterialColors.getColor(likesLabel, com.google.android.material.R.attr.colorOnSurface);
                dateLabel.setTextColor(secondaryColor);
                likesLabel.setTextColor(defaultColor);
            }
        });

        switchOrderAscending.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
                int defaultColor = MaterialColors.getColor(descendingLabel, com.google.android.material.R.attr.colorOnSurface);
                ascendingLabel.setTextColor(secondaryColor);
                descendingLabel.setTextColor(defaultColor);
            } else {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.light_purple);
                int defaultColor = MaterialColors.getColor(ascendingLabel, com.google.android.material.R.attr.colorOnSurface);
                descendingLabel.setTextColor(secondaryColor);
                ascendingLabel.setTextColor(defaultColor);
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
        validUsernames = usernames;

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