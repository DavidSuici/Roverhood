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

import java.util.Objects;
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

        populatePosts();

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

        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
        }

        if (activity.getCurrentUser() != null) {
            binding.username.setText(activity.getCurrentUser().username);
            binding.team.setText(activity.getCurrentUser().team);
            binding.usernameLabel.setText(activity.getCurrentUser().userType);
        }

        view.post(() -> {
            Menu optionsMenu = activity.getOptionsMenu();
            if (optionsMenu != null) {
                announcementsFilter = optionsMenu.findItem(R.id.checkable_menu);
                if (announcementsFilter != null && announcementsFilter.getActionView() != null) {
                    announcementsFilter.setVisible(true);

                    announcementsSwitch = (SwitchCompat) announcementsFilter.getActionView().findViewById(R.id.switch2);
                    if (announcementsSwitch != null) {
                        announcementsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> refreshFeed());
                        refreshFeed();  // safe to call now
                    }
                }
            }
        });

        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;
        swipeRefreshLayout.setProgressViewOffset(true, 100, 250);
        swipeRefreshLayout.setOnRefreshListener(this::refreshFeed);

        LocalDatabase dbHelper = new LocalDatabase(requireContext());
        binding.buttonLogOut.setOnClickListener(v -> {
            dbHelper.markLoggedOut();
            NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn);
        });

        if (activity.getFloatingButton() != null) {
            activity.getFloatingButton().setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_RoverFeed_to_LogIn)
            );
        }


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
        if (backCallback != null) backCallback.remove();
        super.onDestroyView();

        binding = null;
    }

    private void populatePosts() {
        Post temp = new Post(this,
                "21 martie 2025  -  00:17",
                "David",
                "Este o zi frumoasa astazi la Roverhood!",
                R.drawable.img1);

        Post temp2 = new Post(this,
                "20 martie 2025  -  15:12",
                "David",
                "Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test. Text lung de test text lung de test.",
                R.drawable.img2);

        Post temp3 = new Post(this,
                "19 martie 2025  -  14:32",
                "David",
                "Dupa atata asteptare, in sfarsit am terminat cel mai nou costum posibil!",
                R.drawable.img3);
        Post temp4 = new Post(this,
                "10 februarie 2025  -  00:00",
                "Admin",
                "S-au pornit inscrierile! La treaba! Va invitam pe toti sa completati formularul, si ne vedem curand!",
                R.drawable.title);

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
        if (announcementsSwitch.isChecked()) {
            for (Post post : announcements) {
                linearLayout.addView(post.getUserView());
                linearLayout.addView(post.getDateView());
                linearLayout.addView(post.getDescriptionView());
                linearLayout.addView(post.getImageView());
                if (post != posts.lastElement())
                    linearLayout.addView(post.getDividerView());
                else
                    linearLayout.addView(post.getEndView());
            }
        } else {
            for (Post post : posts) {
                linearLayout.addView(post.getUserView());
                linearLayout.addView(post.getDateView());
                linearLayout.addView(post.getDescriptionView());
                linearLayout.addView(post.getImageView());
                if (post != posts.lastElement())
                    linearLayout.addView(post.getDividerView());
                else
                    linearLayout.addView(post.getEndView());
            }
        }
    }

    private void refreshFeed() {
        binding.buttonLogOut.setEnabled(false); // 🔒 Disable logout during refresh

        LinearLayout postLayout = binding.getRoot().findViewById(R.id.info);
        SwipeRefreshLayout swipeRefreshLayout = binding.swipeRefresh;

        postLayout.removeAllViews();
        swipeRefreshLayout.setRefreshing(true);

        new android.os.Handler().postDelayed(() -> {
            drawPosts();
            swipeRefreshLayout.setRefreshing(false);
            binding.buttonLogOut.setEnabled(true); // ✅ Re-enable logout after refresh
        }, 500);
    }
}