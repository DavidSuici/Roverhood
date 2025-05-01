package com.suici.roverhood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.suici.roverhood.databinding.RoverFeedBinding;

import java.util.Vector;

public class RoverFeed extends Fragment {

    private RoverFeedBinding binding;
    Vector<Post> posts = new Vector<Post>();
    Vector<Post> announcements = new Vector<Post>();
    MainActivity main;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = RoverFeedBinding.inflate(inflater, container, false);
        main = (MainActivity) getParentFragment().getActivity();
        main.onFeed = true;



        LinearLayout linearLayout = binding.getRoot().findViewById(R.id.info);

        populatePosts();

        if(main.onlyAnnouncements) {
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
        if(main.onlyAnnouncements) {
            main.getFloatingButton().setVisibility(View.INVISIBLE);
        } else {
            main.getFloatingButton().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        main.onFeed = false;
        main.getFloatingButton().setVisibility(View.INVISIBLE);
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