package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

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

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = RoverFeedBinding.inflate(inflater, container, false);
        activity = (MainActivity) requireActivity();

        announcementsFilter = activity.getOptionsMenu().findItem(R.id.checkable_menu);
        announcementsFilter.setVisible(true);
        announcementsSwitch = Objects.requireNonNull(announcementsFilter.getActionView()).findViewById(R.id.switch2);

        populatePosts();

        LinearLayout linearLayout = binding.getRoot().findViewById(R.id.info);
        if(announcementsSwitch.isChecked()) {
            for(Post post : announcements) {
                linearLayout.addView(post.getUserView());
                linearLayout.addView(post.getDateView());
                linearLayout.addView(post.getDescriptionView());
                linearLayout.addView(post.getImageView());
                if(post != posts.lastElement())
                    linearLayout.addView(post.getDividerView());
                else
                    linearLayout.addView(post.getEndView());
            }
        }
        else {
            for(Post post : posts) {
                linearLayout.addView(post.getUserView());
                linearLayout.addView(post.getDateView());
                linearLayout.addView(post.getDescriptionView());
                linearLayout.addView(post.getImageView());
                if(post != posts.lastElement())
                    linearLayout.addView(post.getDividerView());
                else
                    linearLayout.addView(post.getEndView());
            }
        }

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(!announcementsSwitch.isChecked()) {
            activity.getFloatingButton().setVisibility(View.VISIBLE);
        }

        binding.buttonLogOut.setOnClickListener(v -> {
            NavHostFragment.findNavController(RoverFeed.this)
                    .navigate(R.id.action_RoverFeed_to_LogIn);
        });

        binding.refreshButton.setOnClickListener(v -> {
            NavHostFragment.findNavController(RoverFeed.this)
                    .navigate(R.id.action_RoverFeed_to_loading);
        });

        activity.getFloatingButton().setOnClickListener(v -> {
            NavHostFragment.findNavController(RoverFeed.this)
                    .navigate(R.id.action_RoverFeed_to_LogIn);
        });

        announcementsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            NavHostFragment.findNavController(RoverFeed.this)
                    .navigate(R.id.action_RoverFeed_to_loading);
        });

        // Press back to refresh
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Navigate to loading screen from within RoverFeed
                        NavHostFragment.findNavController(RoverFeed.this)
                                .navigate(R.id.action_RoverFeed_to_loading);

                        // Keep the behaviour from MainActivity, with double back exit
                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        if (announcementsSwitch != null) {
            announcementsSwitch.setOnCheckedChangeListener(null); // remove listener cleanly
        }

        activity.getFloatingButton().setVisibility(View.INVISIBLE);
        announcementsFilter.setVisible(false);

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
}