package com.suici.roverhood;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Post {
    private Fragment activeFragment;
    private Long date;
    private User user;
    private String id;
    private Topic topic;
    private String description;
    private String imageUrl;
    private int likes;
    private Map<String, Boolean> likedBy;
    private boolean announcement;
    private boolean offlinePost;
    private int version;

    private ImageView imageView;
    private boolean imageLoaded = false;
    private boolean isPostVisible = false;
    private boolean isExpanded = false;

    private final int MAX_PREVIEW_ROWS = 5;


    public Post(Fragment fragment, String id, Long date, User user, Topic topic, String description, String imageUrl, int likes, Map<String, Boolean> likedBy, Boolean announcement, int version, Boolean offlinePost) {
        this.id = id;
        this.activeFragment = fragment;
        this.date = date;
        this.user = user;
        this.topic = topic;
        this.description = description;
        this.imageUrl = imageUrl;
        this.likes = likes;
        this.likedBy = likedBy != null ? likedBy : new HashMap<>();
        this.announcement = announcement;
        this.version = version;
        this.offlinePost = offlinePost;
    }

    public void setTopicLabel(TextView topicView) {
        if (topic == null) {
            topicView.setVisibility(View.GONE);
        } else {
            topicView.setVisibility(View.VISIBLE);
            topicView.setText(topic.getTitle());
            if(announcement) {
                topicView.setBackgroundResource(R.drawable.topic_announcement);
            } else {
                topicView.setBackgroundResource(R.drawable.topic_background);
            }

            topicView.setOnClickListener(v -> {
                FilterOptions.resetFilters();
                FilterOptions.setTopic(topic.getTitle());
                FilterOptions.setOrderAscending(true);

                if (activeFragment instanceof RoverFeed) {
                    RoverFeed roverFeed = (RoverFeed) activeFragment;
                    if (!roverFeed.isLoading()) {
                        roverFeed.refreshFeed();
                    }
                }
            });
        }
    }

    public void loadUserAndTeamButtons(TextView userView, TextView teamView) {
        userView.setOnClickListener(v -> {
            FilterOptions.resetFilters();
            FilterOptions.setUsername(user.getUsername());

            if (activeFragment instanceof RoverFeed) {
                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (!roverFeed.isLoading()) {
                    roverFeed.refreshFeed();
                }
            }
        });

        teamView.setOnClickListener(v -> {
            FilterOptions.resetFilters();
            FilterOptions.setTeam(user.getTeam());

            if (activeFragment instanceof RoverFeed) {
                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (!roverFeed.isLoading()) {
                    roverFeed.refreshFeed();
                }
            }
        });
    }

    public void loadDescriptionButton(TextView descriptionView, TextView seeMoreView) {
        descriptionView.setMaxLines(Integer.MAX_VALUE);
        descriptionView.setEllipsize(null);
        seeMoreView.setVisibility(View.GONE);
        isExpanded = true;

        descriptionView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                descriptionView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                    descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                    descriptionView.setEllipsize(TextUtils.TruncateAt.END);
                    seeMoreView.setVisibility(View.VISIBLE);
                    isExpanded = false;
                }
            }
        });

        descriptionView.setOnClickListener(v -> {
            if (isExpanded) {
                if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                    descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                    descriptionView.setEllipsize(TextUtils.TruncateAt.END);
                    seeMoreView.setVisibility(View.VISIBLE);
                    isExpanded = false;
                }
            } else {
                descriptionView.setMaxLines(Integer.MAX_VALUE);
                descriptionView.setEllipsize(null);
                seeMoreView.setVisibility(View.GONE);
                isExpanded = true;
            }
        });

        seeMoreView.setOnClickListener(v -> {
            if (isExpanded) {
                if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                    descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                    descriptionView.setEllipsize(TextUtils.TruncateAt.END);
                    seeMoreView.setVisibility(View.VISIBLE);
                    isExpanded = false;
                }
            } else {
                descriptionView.setMaxLines(Integer.MAX_VALUE);
                descriptionView.setEllipsize(null);
                seeMoreView.setVisibility(View.GONE);
                isExpanded = true;
            }
        });
    }

    public void loadImageClickEvent(ImageView imageView, ImageButton fullScreenIcon) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
        params.dimensionRatio = null;
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
        params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setLayoutParams(params);
        imageView.requestLayout();
        fullScreenIcon.setVisibility(View.GONE);

        imageView.post(() -> {
            if (imageView.getDrawable() != null) {
                int width = imageView.getDrawable().getIntrinsicWidth();
                int height = imageView.getDrawable().getIntrinsicHeight();

                if (width > 0 && height > 0) {
                    float ratio = (float) width / height;
                    if (ratio < 1) {
                        params.dimensionRatio = "1:1";
                        params.width = 0;
                        params.height = 0;
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setLayoutParams(params);
                        imageView.requestLayout();
                        fullScreenIcon.setVisibility(View.VISIBLE);
                    }

                }
            }
        });

        imageView.setOnClickListener(v -> {
            if ("1:1".equals(params.dimensionRatio)) {
                params.dimensionRatio = null;
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                fullScreenIcon.setVisibility(View.GONE);
            } else {
                int width = imageView.getDrawable().getIntrinsicWidth();
                int height = imageView.getDrawable().getIntrinsicHeight();
                float ratio = (float) width / height;
                if (ratio < 1) {
                    params.dimensionRatio = "1:1";
                    params.width = 0;
                    params.height = 0;
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    fullScreenIcon.setVisibility(View.VISIBLE);
                }
            }

            imageView.setLayoutParams(params);
            imageView.requestLayout();
        });
    }

    public void setFlair(View flair) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();

        if (Objects.equals(currentUser.getId(), user.getId())) {
            int primaryColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorPrimary);
            flair.setBackgroundColor(primaryColor);
        } else if (Objects.equals(currentUser.getTeam(), user.getTeam())) {
            int accentColor = activeFragment.requireContext().getResources().getColor(R.color.blue_accent, null);
            flair.setBackgroundColor(accentColor);
        } else {
            int secondaryColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorSecondary);
            flair.setBackgroundColor(secondaryColor);
        }
    }

    public void setAnnouncementFlair(View announcementFlair, View announcementBG, View flair, TextView userType) {
        if(isAnnouncement()) {
            announcementFlair.setVisibility(View.VISIBLE);
            announcementBG.setVisibility(View.VISIBLE);
            flair.setVisibility(View.GONE);
            int amberColor = userType.getContext().getResources().getColor(R.color.announcements_amber, userType.getContext().getTheme());
            userType.setTextColor(amberColor);
            userType.setText("ANNOUNCEMENT");
        }
        else {
            announcementFlair.setVisibility(View.GONE);
            announcementBG.setVisibility(View.GONE);
            flair.setVisibility(View.VISIBLE);
            int defaultColor = MaterialColors.getColor(userType, com.google.android.material.R.attr.colorOnSurface);
            userType.setTextColor(defaultColor);
        }
    }

    public void createImage() {
        imageView = new ImageView(activeFragment.requireContext());
        imageView.setAdjustViewBounds(true);

        Object imageSource = offlinePost ? new File(imageUrl) : imageUrl;

        Glide.with(activeFragment.requireContext())
                .load(imageSource)
                .signature(offlinePost ? new ObjectKey(System.currentTimeMillis()) : new ObjectKey(imageUrl)) // Invalidates cache for offline
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
        heartButton.setOnCheckedChangeListener(null);

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
                if (!isPostVisible) return;

                PostRepository postRepository = PostRepository.getInstance(activeFragment.requireContext());
                postRepository.toggleLike(id, currentUserId, isChecked, new PostRepository.PostOperationCallback() {
                    @Override
                    public void onSuccess() {
                        Boolean isLiked = likedBy.get(currentUserId);
                        if (isChecked) {
                            if (isLiked == null || !isLiked) {
                                likes++;
                                likedBy.put(currentUserId, true);
                            }
                        } else {
                            if (isLiked != null && isLiked) {
                                likes--;
                                likedBy.remove(currentUserId);
                            }
                        }
                        heartNrView.setText(String.valueOf(likes));
                    }
                    @Override
                    public void onError(String errorMessage) {
                        Log.e("Post", "Failed to update like status: " + errorMessage);
                    }
                });
            });
        }
    }

    public void loadMenuButton(ImageButton menuButton) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();
        boolean isOwner = Objects.equals(user.getId(), currentUser.getId());
        boolean isOrganizer = "ORGANIZER".equals(currentUser.getUserType());
        boolean isAdmin = "ADMIN".equals(currentUser.getUserType());

        if ((!isOwner && !isOrganizer && !isAdmin) || offlinePost) {
            menuButton.setVisibility(View.GONE);
            return;
        } else {
            menuButton.setVisibility(View.VISIBLE);
        }

        menuButton.setOnClickListener(view -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(activeFragment.requireContext(), menuButton);
            popup.getMenuInflater().inflate(R.menu.post_menu, popup.getMenu());

            popup.getMenu().findItem(R.id.action_edit).setVisible(isOwner);
            popup.getMenu().findItem(R.id.action_delete).setVisible(isOwner || isAdmin || isOrganizer);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    editPost();
                    return true;
                } else if (id == R.id.action_delete) {
                    deletePost();
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    private void editPost() {
        EditPost editPostFragment = new EditPost(this, activeFragment);
        editPostFragment.show(activeFragment.getParentFragmentManager(), "editPostFragment");
    }

    private void deletePost() {
        PostRepository postRepository = PostRepository.getInstance(activeFragment.requireContext());
        new AlertDialog.Builder(activeFragment.requireContext())
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    postRepository.deletePost(id, imageUrl, new PostRepository.PostOperationCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(activeFragment.requireContext(), "Post deleted", Toast.LENGTH_SHORT).show();
                            if (activeFragment instanceof RoverFeed) {
                                ((RoverFeed) activeFragment).removePostFromUI(Post.this);
                            }
                        }
                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(activeFragment.requireContext(), "Failed to delete post", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    public void loadIntoView(View itemView) {
        // Find views
        TextView itemUser = itemView.findViewById(R.id.user);
        TextView itemTeam = itemView.findViewById(R.id.team);
        TextView itemUserType = itemView.findViewById(R.id.userType);
        TextView itemDate = itemView.findViewById(R.id.date);
        TextView itemTopic = itemView.findViewById(R.id.topicLabel);
        TextView itemDescription = itemView.findViewById(R.id.description);
        TextView itemSeeMore = itemView.findViewById(R.id.seeMoreLabel);
        TextView itemHeartNr = itemView.findViewById(R.id.heartNr);
        ImageView itemImage = itemView.findViewById(R.id.image);
        CheckBox itemHeart = itemView.findViewById(R.id.heart);
        View flair = itemView.findViewById(R.id.flair);
        View announcementFlair = itemView.findViewById(R.id.announcementFlair);
        View announcementBG = itemView.findViewById(R.id.announcementBG);
        ImageButton menuButton = itemView.findViewById(R.id.postMenuButton);
        ImageButton fullScreenIcon = itemView.findViewById(R.id.viewFullScreenIcon);

        // Populate the views
        itemUser.setText(this.getUser().getUsername());
        itemTeam.setText(this.getUser().getTeam());
        itemUserType.setText(this.getUser().getUserType());
        itemDate.setText(DateUtils.formatTimestamp(this.getDate()));
        itemDescription.setText(this.getDescription());
        itemHeartNr.setText(String.valueOf(this.getLikes()));
        itemImage.setImageDrawable(imageView.getDrawable());
        itemHeart.setChecked(likedBy.containsKey(((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId()));

        setTopicLabel(itemTopic);
        loadUserAndTeamButtons(itemUser, itemTeam);
        loadDescriptionButton(itemDescription, itemSeeMore);
        loadImageClickEvent(itemImage, fullScreenIcon);
        loadLikeButton(itemHeart, itemHeartNr);
        loadMenuButton(menuButton);
        setFlair(flair);
        setAnnouncementFlair(announcementFlair, announcementBG, flair, itemUserType);
    }

    public boolean isAnnouncement() { return announcement; }
    public boolean isImageLoaded() { return imageLoaded; }

    public ImageView getImageView() { return imageView; }
    public void setImageView(ImageView imageView) { this.imageView = imageView; }
    public void setFragment(Fragment fragment) { this.activeFragment = fragment; }
    public boolean isPostVisible() { return isPostVisible; }
    public void setPostVisible(boolean postVisible) { this.isPostVisible = postVisible; }
    public void setAnnouncement(boolean announcement) {this.announcement = announcement; }

    public Long getDate() { return date; }
    public void setDate(Long date) { this.date = date; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }
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
    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }
}
