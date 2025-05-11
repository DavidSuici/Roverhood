package com.suici.roverhood;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Post {
    private final Fragment activeFragment;
    private Long date;
    private User user;
    private String id;
    private String description;
    private String imageUrl;
    private int likes;
    private Map<String, Boolean> likedBy;
    private boolean offlinePost;

    private ImageView imageView;
    private boolean imageLoaded = false;


    public Post(Fragment fragment, String id, Long date, User user, String description, String imageUrl, int likes, Map<String, Boolean> likedBy, Boolean offlinePost) {
        this.id = id;
        this.activeFragment = fragment;
        this.date = date;
        this.user = user;
        this.description = description;
        this.imageUrl = imageUrl;
        this.likes = likes;
        this.likedBy = likedBy != null ? likedBy : new HashMap<>();
        this.offlinePost = offlinePost;
    }

    // will have to move the logic
    public void createImage() {
        imageView = new ImageView(activeFragment.requireContext());
        imageView.setAdjustViewBounds(true);

        Object imageSource = offlinePost ? new File(imageUrl) : imageUrl;

        Glide.with(activeFragment.requireContext())
                .load(imageSource)
                .placeholder(R.drawable.img_not_loaded)
                .override(Target.SIZE_ORIGINAL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        imageLoaded = true;
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imageLoaded = true;
                        return false;
                    }
                })
                .into(imageView);
    }

    public void loadLikeButton(CheckBox heartButton, TextView heartNrView) {
        String currentUserId = ((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId();
        if (likedBy != null)
            heartButton.setChecked(likedBy.containsKey(currentUserId));
        else
            heartButton.setChecked(false);

        if (offlinePost) {
            heartButton.setAlpha(0.9f);
            heartButton.setEnabled(false);
        }
        else {
            heartButton.setAlpha(1.0f);
            heartButton.setEnabled(true);

            heartButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                DatabaseReference postRef = FirebaseDatabase
                        .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                        .getReference("posts")
                        .child(id);

                postRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Integer currentLikes = currentData.child("likes").getValue(Integer.class);
                        if (currentLikes == null) currentLikes = 0;

                        Map<String, Boolean> currentLikedBy = (Map<String, Boolean>) currentData.child("likedBy").getValue();
                        if (currentLikedBy == null) currentLikedBy = new HashMap<>();

                        Boolean isLiked = currentLikedBy.get(currentUserId);

                        if (isChecked) {
                            if (isLiked == null || !isLiked) {
                                currentLikes++;
                                currentLikedBy.put(currentUserId, true);
                            }
                        } else {
                            if (isLiked != null && isLiked) {
                                currentLikes--;
                                currentLikedBy.remove(currentUserId);
                            }
                        }

                        currentData.child("likes").setValue(currentLikes);
                        currentData.child("likedBy").setValue(currentLikedBy);

                        return com.google.firebase.database.Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        if (committed && currentData != null) {
                            Integer newLikes = currentData.child("likes").getValue(Integer.class);
                            if (newLikes != null) {
                                likes = newLikes;
                                heartNrView.setText(String.valueOf(newLikes));
                            }

                            Map<String, Boolean> newLikedBy = (Map<String, Boolean>) currentData.child("likedBy").getValue();
                            if (newLikedBy != null) {
                                likedBy = newLikedBy;
                            }

                            LocalDatabase localDB = LocalDatabase.getInstance(activeFragment.requireContext());
                            localDB.updatePostLikes(id, likes, likedBy);
                        } else {
                            if (error != null) {
                                Log.e("TransactionError", "Transaction failed: " + error.getMessage());
                            } else {
                                Log.e("TransactionError", "Transaction not committed (possible conflict or error)");
                            }
                        }
                    }
                });
            });
        }
    }

    public void loadIntoView(View itemView) {
        // Find views
        TextView itemUser = itemView.findViewById(R.id.user);
        TextView itemTeam = itemView.findViewById(R.id.team);
        TextView itemUserType = itemView.findViewById(R.id.userType);
        TextView itemDate = itemView.findViewById(R.id.date);
        TextView itemDescription = itemView.findViewById(R.id.description);
        TextView itemHeartNr = itemView.findViewById(R.id.heartNr);
        ImageView itemImage = itemView.findViewById(R.id.image);
        CheckBox itemHeart = itemView.findViewById(R.id.heart);

        // Populate the views
        itemUser.setText(this.getUser().getUsername());
        itemTeam.setText(this.getUser().getTeam());
        itemUserType.setText(this.getUser().getUserType());
        itemDate.setText(DateUtil.formatTimestamp(this.getDate()));
        itemDescription.setText(this.getDescription());
        itemHeartNr.setText(String.valueOf(this.getLikes()));
        itemImage.setImageDrawable(imageView.getDrawable());
        loadLikeButton(itemHeart, itemHeartNr);
    }

    // TO_DO Change when proper filters are implemented
    public boolean isAnnouncement() {
        return Objects.equals(user.username, "admin");
    }
    public boolean isImageLoaded() { return imageLoaded; }

    public Long getDate() { return date; }
    public void setDate(Long date) { this.date = date; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }
    public Map<String, Boolean> getLikedBy() { return likedBy; }
    public void setLikedBy(Map<String, Boolean> likedBy) { this.likedBy = likedBy; }
}
