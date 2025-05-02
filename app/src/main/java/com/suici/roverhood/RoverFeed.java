package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.suici.roverhood.databinding.RoverFeedBinding;

import java.util.Vector;

public class RoverFeed extends Fragment {

    private RoverFeedBinding binding;
    Vector<Post> posts = new Vector<Post>();
    Vector<Post> announcements = new Vector<Post>();
    MainActivity activity;
    MenuItem announcementsFilter;
    SwitchCompat announcementsSwitch;
    private OnBackPressedCallback backCallback;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = RoverFeedBinding.inflate(inflater, container, false);
        activity = (MainActivity) requireActivity();

        // Temporary posts creation method - TO_DO get posts from Firebase
        populatePosts();

        // Refresh on back pressed when on feed, but keep the general back pressed logic as well
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);

                refreshFeed();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(requireActivity(), backCallback);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activity = (MainActivity) requireActivity();

        // Add post - button logic
        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
            activity.getFloatingButton().setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn)
            );
        }

        // Log-out - button logic
        LocalDatabase dbHelper = new LocalDatabase(requireContext());
        binding.buttonLogOut.setOnClickListener(v -> {
            dbHelper.markLoggedOut();
            activity.setCurrentUser(null);
            NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn);
        });

        // Announcements - check button logic
        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                announcementsFilter = optionsMenu.findItem(R.id.checkable_menu);
                if (announcementsFilter != null && announcementsFilter.getActionView() != null) {
                    announcementsFilter.setVisible(true);
                    announcementsSwitch = (SwitchCompat) announcementsFilter.getActionView().findViewById(R.id.switch2);
                    if (announcementsSwitch != null) {
                        // Refresh feed when checked
                        announcementsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> refreshFeed());
                        // Refresh feed for the first load
                        refreshFeed();
                    }
                }
            }
        });

        // User information - cosmetic logic
        if (activity.getCurrentUser() != null) {
            binding.username.setText(activity.getCurrentUser().username);
            binding.team.setText(activity.getCurrentUser().team);
            binding.usernameLabel.setText(activity.getCurrentUser().userType);
        }

        // Refresh - cosmetic logic
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;
        swipeRefreshLayout.setProgressViewOffset(true, 100, 250);
        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);
    }

    @Override
    public void onDestroyView() {
        if (announcementsSwitch != null) {
            announcementsSwitch.setOnCheckedChangeListener(null);
            announcementsSwitch = null;
        }

        if (announcementsFilter != null) {
            announcementsFilter.setVisible(false);
            announcementsFilter = null;
        }

        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.INVISIBLE);
        }

        if (backCallback != null)
            backCallback.remove();

        super.onDestroyView();
        binding = null;
    }

    private void populatePosts() {
        Post temp = new Post(this,
                "21 martie 2025  -  00:17",
                new User("David Suici", "1234", "PARTICIPANT", "EagleWatchers", "user001"),
                "Este o zi frumoasa astazi la Roverhood!",
                R.drawable.img1,
                1);

        Post temp2 = new Post(this,
                "20 martie 2025  -  15:12",
                new User("David Suici", "1234", "PARTICIPANT", "EagleWatchers", "user001"),
                "Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test.",
                R.drawable.img2,
                12345);

        Post temp3 = new Post(this,
                "19 martie 2025  -  14:32",
                new User("David Suici", "1234", "PARTICIPANT", "EagleWatchers", "user001"),
                "Dupa atata asteptare, in sfarsit am terminat cel mai nou costum posibil!",
                R.drawable.img3,
                200);
        Post temp4 = new Post(this,
                "10 februarie 2025  -  00:00",
                new User("admin", "admin", "ORGANIZER", "The Admins", "user002"),
                "S-au pornit inscrierile! La treaba! Va invitam pe toti sa completati formularul, si ne vedem curand!",
                R.drawable.title,
                5);

        posts.add(temp);
        posts.add(temp2);
        posts.add(temp3);
        posts.add(temp4);

        for(Post post : posts) {
            if(post.isAnnouncement())
                announcements.add(post);
        }
    }
    private void drawPosts() {
        LinearLayout linearLayout = binding.getRoot().findViewById(R.id.info);
        // TO_DO add proper filters so the if else is removed
        if (announcementsSwitch.isChecked()) {
            for (Post post : announcements)
                linearLayout.addView(post.getPostContainer());
            linearLayout.addView(createEndView());
        } else {
            for (Post post : posts)
                linearLayout.addView(post.getPostContainer());
            linearLayout.addView(createEndView());
        }
    }

    private void refreshFeed() {
        // Disable buttons during refresh
        binding.buttonLogOut.setEnabled(false);
        announcementsSwitch.setEnabled(false);
        activity.getFloatingButton().setEnabled(false);

        LinearLayout postLayout = binding.getRoot().findViewById(R.id.info);
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;

        // Remove everything in the layout and start refresh animation
        postLayout.removeAllViews();
        swipeRefreshLayout.setRefreshing(true);

        //TO_DO Reload posts here

        // Let the refresh for at least 0.5s
        // TO_DO only wait the difference until 0.5s so far, after reloading posts
        new android.os.Handler().postDelayed(() -> {
            drawPosts();
            swipeRefreshLayout.setRefreshing(false);

            // Enable buttons after refresh
            binding.buttonLogOut.setEnabled(true);
            announcementsSwitch.setEnabled(true);
            activity.getFloatingButton().setEnabled(true);
        }, 500);
    }

    // Add empty space at the end of layout so "Add post" button is not resting on a post image, blocking it
    private View createEndView() {
        float density = requireContext().getResources().getDisplayMetrics().density;
        int dp = (int) (density);

        View endView = new View(requireContext());
        endView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100*dp)
        );

        return endView;
    }
}