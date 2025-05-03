package com.suici.roverhood;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Post {
    Fragment activeFragment;
    Long date;
    User user;
    String id;
    String description;
    String imageUrl;
    int likes;
    private Set<String> likedBy = new HashSet<>();

    int dp = 0;
    ConstraintLayout postContainer;
    private TextView heartNrView;
    private ImageView imageView;

    public Post(Fragment fragment, String id, Long date, User user, String description, String imageUrl, int likes, Set<String> likedBy) {
        this.id = id;
        this.activeFragment = fragment;
        this.date = date;
        this.user = user;
        this.description = description;
        this.imageUrl = imageUrl;
        this.likes = likes;
        this.likedBy = likedBy;

        createPostContainer();
    }

    public View createUserView() {
        TextView userView = new TextView(activeFragment.requireContext());
        userView.setText(this.user.username);
        userView.setTypeface(userView.getTypeface(), Typeface.BOLD);
        userView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); // Set font size

        // Add color to the Username
        TypedValue typedValue = new TypedValue();
        Context context = activeFragment.requireContext();
        context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        int[] textColorAttr = new int[]{android.R.attr.textColorPrimary};
        TypedArray a = context.obtainStyledAttributes(typedValue.data, textColorAttr);
        int color = a.getColor(0, -1);
        a.recycle();

        userView.setTextColor(color);
        userView.setId(View.generateViewId());

        return userView;
    }

    public View createTeamView() {
        TextView teamView = new TextView(activeFragment.requireContext());
        teamView.setText(this.user.team);
        teamView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); // Set font size
        teamView.setId(View.generateViewId());

        return teamView;
    }

    public View createUserTypeView() {
        TextView userTypeView = new TextView(activeFragment.requireContext());
        userTypeView.setText(this.user.userType);
        userTypeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); // Set font size
        userTypeView.setId(View.generateViewId());

        return userTypeView;
    }

    public View createDateView() {
        TextView dateView = new TextView(activeFragment.requireContext());
        dateView.setText(DateUtil.formatTimestamp(this.date));
        dateView.setTypeface(dateView.getTypeface(), Typeface.ITALIC);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateView.setLayoutParams(params);
        dateView.setId(View.generateViewId());

        return dateView;
    }

    public View createDescriptionView() {
        TextView descriptionView = new TextView(activeFragment.requireContext());
        descriptionView.setText(this.description);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionView.setLayoutParams(params);
        descriptionView.setId(View.generateViewId());

        return descriptionView;
    }

    public View createImageView() {
        imageView = new ImageView(activeFragment.requireContext());
        imageView.setAdjustViewBounds(true);
        imageView.setId(View.generateViewId());

        return imageView;
    }

    public View createShadowView() {
        View shadowView = new View(activeFragment.requireContext());
        shadowView.setBackgroundResource(R.drawable.fade_bottom);
        ConstraintLayout.LayoutParams shadowParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,15 * dp);
        shadowView.setLayoutParams(shadowParams);
        shadowView.setId(View.generateViewId());

        return shadowView;
    }

    public View createHeartButton() {
        CheckBox heartButton = new CheckBox(activeFragment.requireContext());
        heartButton.setButtonDrawable(R.drawable.heart_layer); // Your heart icon

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        heartButton.setLayoutParams(params);
        heartButton.setId(View.generateViewId());

        String currentUserId = ((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId();
        heartButton.setChecked(likedBy.contains(currentUserId)); // Initially check if liked

        heartButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update likedBy list in database
            DatabaseReference likedByRef = FirebaseDatabase
                    .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("posts")
                    .child(id) // id is postId (make sure this is set correctly)
                    .child("likedBy")
                    .child(currentUserId);

            if (isChecked) {
                likedByRef.setValue(true);
            } else {
                likedByRef.removeValue();
            }

            // Update likes counter transactionally
            DatabaseReference likesRef = FirebaseDatabase
                    .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("posts")
                    .child(id)
                    .child("likes");

            likesRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
                @NonNull
                @Override
                public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    Integer currentLikes = currentData.getValue(Integer.class);
                    if (currentLikes == null) currentLikes = 0;

                    if (isChecked) {
                        currentData.setValue(currentLikes + 1);
                    } else {
                        currentData.setValue(Math.max(currentLikes - 1, 0));
                    }

                    return com.google.firebase.database.Transaction.success(currentData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                    if (committed && currentData != null) {
                        Integer newLikes = currentData.getValue(Integer.class);
                        if (newLikes != null) {
                            likes = newLikes;
                            heartNrView.setText(String.valueOf(newLikes));
                        }
                    }
                }
            });
        });

        return heartButton;
    }

    public View createHeartNr() {
        heartNrView = new TextView(activeFragment.getActivity());
        heartNrView.setText(String.valueOf(likes));
        heartNrView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); // Set font size
        heartNrView.setId(View.generateViewId());

        return heartNrView;
    }

    public void createPostContainer() {
        float density = activeFragment.getActivity().getResources().getDisplayMetrics().density;
        dp = (int) (density);

        postContainer = new ConstraintLayout(activeFragment.requireContext());
        postContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View userView = createUserView();
        View teamView = createTeamView();
        View userTypeView = createUserTypeView();
        View dateView = createDateView();
        View descView = createDescriptionView();
        View imageView = createImageView();
        View shadowView = createShadowView();
        View heartButton = createHeartButton();
        View heartNr = createHeartNr();

        postContainer.addView(userView);
        postContainer.addView(teamView);
        postContainer.addView(userTypeView);
        postContainer.addView(dateView);
        postContainer.addView(descView);
        postContainer.addView(imageView);
        postContainer.addView(shadowView);
        postContainer.addView(heartButton);
        postContainer.addView(heartNr);

        ConstraintSet set = new ConstraintSet();
        set.clone(postContainer);

        set.connect(userView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 20*dp);
        set.connect(userView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 8*dp);

        set.connect(teamView.getId(), ConstraintSet.BOTTOM, userView.getId(), ConstraintSet.BOTTOM, 0*dp);
        set.connect(teamView.getId(), ConstraintSet.START, userView.getId(), ConstraintSet.END, 8*dp);

        set.connect(userTypeView.getId(), ConstraintSet.BOTTOM, userView.getId(), ConstraintSet.TOP, -5*dp);
        set.connect(userTypeView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 9*dp);

        set.connect(dateView.getId(), ConstraintSet.TOP, userView.getId(), ConstraintSet.BOTTOM, 4*dp);
        set.connect(dateView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 8*dp);
        set.connect(dateView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 8 * dp);

        set.connect(descView.getId(), ConstraintSet.TOP, dateView.getId(), ConstraintSet.BOTTOM, 8*dp);
        set.connect(descView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 8*dp);
        set.connect(descView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 8 * dp);

        set.constrainWidth(imageView.getId(), ConstraintSet.MATCH_CONSTRAINT);
        set.connect(imageView.getId(), ConstraintSet.TOP, descView.getId(), ConstraintSet.BOTTOM, 8 * dp);
        set.connect(imageView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(imageView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);

        set.connect(shadowView.getId(), ConstraintSet.TOP, imageView.getId(), ConstraintSet.BOTTOM, 0);
        set.connect(shadowView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(shadowView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);

        set.connect(heartButton.getId(), ConstraintSet.TOP, userView.getId(), ConstraintSet.TOP, 0*dp);
        set.connect(heartButton.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 30 * dp);

        set.connect(heartNr.getId(), ConstraintSet.TOP, heartButton.getId(), ConstraintSet.BOTTOM, 1 *dp);
        set.connect(heartNr.getId(), ConstraintSet.START, heartButton.getId(), ConstraintSet.START);
        set.connect(heartNr.getId(), ConstraintSet.END, heartButton.getId(), ConstraintSet.END);

        set.applyTo(postContainer);
    }

    public void loadImageInto(ImageView imageView, Runnable onReady) {
        Glide.with(activeFragment.requireContext())
                .load(imageUrl)
                .placeholder(R.drawable.title)
                .override(Target.SIZE_ORIGINAL)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        imageView.setImageDrawable(resource);
                        if (onReady != null) onReady.run();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    public ConstraintLayout getPostContainer() {
        return postContainer;
    }

    // TO_DO Change when proper filters are implemented
    public boolean isAnnouncement() {
        return Objects.equals(user.username, "admin");
    }

    public ImageView getImageView() { return imageView; }

    public boolean isLikedBy(String userId) {
        return likedBy.contains(userId);
    }

    public void addLike(String userId) {
        likedBy.add(userId);
    }

    public void removeLike(String userId) {
        likedBy.remove(userId);
    }
}
