package com.suici.roverhood.presentation;

import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
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
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.dialogs.EditPost;
import com.suici.roverhood.utils.FiltersManager;
import com.suici.roverhood.models.Post;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Date;

import java.io.File;
import java.util.Objects;

public class PostHandler {
    private Fragment activeFragment;
    private Post post;
    private final boolean offlinePost;

    private ImageView imageView;
    private boolean imageLoaded = false;
    private boolean isPostVisible = false;
    private boolean isExpanded = false;

    private final int MAX_PREVIEW_ROWS = 5;


    public PostHandler(Fragment fragment, Post post, Boolean offlinePost) {
        this.activeFragment = fragment;
        this.post = post;
        this.offlinePost = offlinePost;
    }

    public void bindEditedLabel(TextView editedLabelView) {
        if (post.getVersion() > 0) {
            editedLabelView.setVisibility(View.VISIBLE);
        } else {
            editedLabelView.setVisibility(View.GONE);
        }
    }

    public void bindTopicLabel(TextView topicView) {
        if (post.getTopic() == null) {
            topicView.setVisibility(View.GONE);
        } else {
            topicView.setVisibility(View.VISIBLE);
            topicView.setText(post.getTopic().getTitle());
            if(post.isAnnouncement()) {
                topicView.setBackgroundResource(R.drawable.topic_announcement);
            } else {
                topicView.setBackgroundResource(R.drawable.topic_background);
            }

            View parentView = (View) topicView.getParent();
            parentView.post(() -> {
                int parentWidth = parentView.getWidth();
                int maxAllowedWidth = (int) (parentWidth * 0.9);
                topicView.setMaxWidth(maxAllowedWidth);
            });

            topicView.setOnClickListener(v -> {
                FiltersManager.resetFilters();
                FiltersManager.getActiveFilters().setTopic(post.getTopic().getTitle());
                FiltersManager.getActiveFilters().setOrderAscending(true);

                if (activeFragment instanceof RoverFeed) {
                    RoverFeed roverFeed = (RoverFeed) activeFragment;
                    if (!roverFeed.isLoading()) {
                        roverFeed.refreshFeed();
                    }
                }
            });
        }
    }

    public void bindUserAndTeamButtons(TextView userView, TextView teamView) {
        userView.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setUsername(post.getUser().getUsername());

            if (activeFragment instanceof RoverFeed) {
                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (!roverFeed.isLoading()) {
                    roverFeed.refreshFeed();
                }
            }
        });

        teamView.setOnClickListener(v -> {
            FiltersManager.resetFilters();
            FiltersManager.getActiveFilters().setTeam(post.getUser().getTeam());

            if (activeFragment instanceof RoverFeed) {
                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (!roverFeed.isLoading()) {
                    roverFeed.refreshFeed();
                }
            }
        });
    }

    public void bindDescriptionToggle(TextView descriptionView, TextView seeMoreView) {
        descriptionView.setMaxLines(MAX_PREVIEW_ROWS + 1);
        seeMoreView.setVisibility(View.GONE);
        descriptionView.setMovementMethod(null);
        isExpanded = false;

        descriptionView.post(() -> {
            if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                seeMoreView.setVisibility(View.VISIBLE);
            } else {
                descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
                isExpanded = true;
            }
        });

        View.OnClickListener toggleListener = v -> {
            if (isExpanded) {
                if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                    descriptionView.setMovementMethod(null);
                    descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                    seeMoreView.setVisibility(View.VISIBLE);
                    descriptionView.setClickable(true);
                    isExpanded = false;
                }
            } else {
                descriptionView.setMaxLines(Integer.MAX_VALUE);
                seeMoreView.setVisibility(View.GONE);
                descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
                isExpanded = true;
            }
        };

        descriptionView.setOnClickListener(toggleListener);
        seeMoreView.setOnClickListener(toggleListener);
    }

    public void bindImageClickToggle(ImageView imageView, ImageButton fullScreenIcon) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();

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
                else {
                    params.dimensionRatio = null;
                    params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
                    params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setLayoutParams(params);
                    imageView.requestLayout();
                    fullScreenIcon.setVisibility(View.GONE);
                }
            }
        }

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

    public void bindFlair(View flair) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();

        if (Objects.equals(currentUser.getId(), post.getUser().getId())) {
            int primaryColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorPrimary);
            flair.setBackgroundColor(primaryColor);
        } else if (Objects.equals(currentUser.getTeam(), post.getUser().getTeam())) {
            int accentColor = activeFragment.requireContext().getResources().getColor(R.color.blue_accent, null);
            flair.setBackgroundColor(accentColor);
        } else {
            int secondaryColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorSecondary);
            flair.setBackgroundColor(secondaryColor);
        }
    }

    public void bindAnnouncementFlair(View announcementFlair, View postBG, View flair, TextView userType) {
        if(post.isAnnouncement()) {
            announcementFlair.setVisibility(View.VISIBLE);
            flair.setVisibility(View.GONE);
            int amberColor = userType.getContext().getResources().getColor(R.color.announcements_amber, userType.getContext().getTheme());
            userType.setTextColor(amberColor);
            userType.setText("ANNOUNCEMENT");
            postBG.setBackgroundColor(amberColor);
        }
        else {
            announcementFlair.setVisibility(View.GONE);
            flair.setVisibility(View.VISIBLE);
            int defaultColor = MaterialColors.getColor(userType, com.google.android.material.R.attr.colorOnSurface);
            int primaryColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorPrimary);
            userType.setTextColor(defaultColor);
            postBG.setBackgroundColor(primaryColor);
        }
    }

    public void prepareImageView() {
        imageView = new ImageView(activeFragment.requireContext());
        imageView.setAdjustViewBounds(true);

        Object imageSource = offlinePost ? new File(post.getImageUrl()) : post.getImageUrl();

        Glide.with(activeFragment.requireContext())
                .load(imageSource)
                .signature(offlinePost ? new ObjectKey(System.currentTimeMillis()) : new ObjectKey(post.getImageUrl())) // Invalidates cache for offline
                .placeholder(R.drawable.image_not_loaded)
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

    public void bindLikeButton(CheckBox heartButton, TextView heartNrView) {
        String currentUserId = ((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId();
        heartButton.setOnCheckedChangeListener(null);

        if (post.getLikedBy() != null)
            heartButton.setChecked(post.getLikedBy().containsKey(currentUserId));
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

                Boolean isLiked = post.getLikedBy().get(currentUserId);
                if (isChecked) {
                    if (isLiked == null || !isLiked) {
                        post.incrementLikes();
                        post.getLikedBy().put(currentUserId, true);
                    }
                } else {
                    if (isLiked != null && isLiked) {
                        post.decrementLikes();
                        post.getLikedBy().remove(currentUserId);
                    }
                }
                heartNrView.setText(String.valueOf(post.getLikes()));

                FirebaseRepository firebaseRepository = FirebaseRepository.getInstance(activeFragment.requireContext());
                firebaseRepository.toggleLike(post.getId(), currentUserId, isChecked, new FirebaseRepository.PostOperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d("PostHandler", "Like status updated.");
                    }
                    @Override
                    public void onError(String errorMessage) {
                        Log.e("PostHandler", "Failed to update like status: " + errorMessage);
                    }
                });
            });
        }
    }

    public void bindMenuButton(ImageButton menuButton) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();
        boolean isOwner = Objects.equals(post.getUser().getId(), currentUser.getId());
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
            popup.getMenuInflater().inflate(R.menu.menu_post, popup.getMenu());

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
        FirebaseRepository firebaseRepository = FirebaseRepository.getInstance(activeFragment.requireContext());
        new AlertDialog.Builder(activeFragment.requireContext())
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    firebaseRepository.deletePost(post.getId(), post.getImageUrl(), new FirebaseRepository.PostOperationCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(activeFragment.requireContext(), "Post deleted", Toast.LENGTH_SHORT).show();
                            if (activeFragment instanceof RoverFeed) {
                                ((RoverFeed) activeFragment).removePostFromUI(PostHandler.this);
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
        Log.d("test2", "load into view  -  " + post.getDescription());

        // Find views
        TextView itemUser = itemView.findViewById(R.id.user);
        TextView itemTeam = itemView.findViewById(R.id.team);
        TextView itemUserType = itemView.findViewById(R.id.userType);
        TextView itemDate = itemView.findViewById(R.id.date);
        TextView itemEdited = itemView.findViewById(R.id.editedLabel);
        TextView itemTopic = itemView.findViewById(R.id.topicLabel);
        TextView itemDescription = itemView.findViewById(R.id.description);
        TextView itemSeeMore = itemView.findViewById(R.id.seeMoreLabel);
        TextView itemHeartNr = itemView.findViewById(R.id.heartNr);
        ImageView itemImage = itemView.findViewById(R.id.image);
        CheckBox itemHeart = itemView.findViewById(R.id.heart);
        View itemFlair = itemView.findViewById(R.id.flair);
        View itemAnnouncementFlair = itemView.findViewById(R.id.announcementFlair);
        View itemPostBg = itemView.findViewById(R.id.postBG);
        ImageButton itemMenuButton = itemView.findViewById(R.id.postMenuButton);
        ImageButton itemFullScreenIcon = itemView.findViewById(R.id.viewFullScreenIcon);

        // Populate the views
        itemUser.setText(post.getUser().getUsername());
        itemTeam.setText(post.getUser().getTeam());
        itemUserType.setText(post.getUser().getUserType());
        itemDate.setText(Date.formatTimestamp(post.getDate()) + "\u00A0");
        itemDescription.setText(post.getDescription());
        itemHeartNr.setText(String.valueOf(post.getLikes()));
        itemImage.setImageDrawable(imageView.getDrawable());
        itemHeart.setChecked(post.getLikedBy().containsKey(((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId()));

        bindEditedLabel(itemEdited);
        bindTopicLabel(itemTopic);
        bindUserAndTeamButtons(itemUser, itemTeam);
        bindDescriptionToggle(itemDescription, itemSeeMore);
        bindImageClickToggle(itemImage, itemFullScreenIcon);
        bindLikeButton(itemHeart, itemHeartNr);
        bindMenuButton(itemMenuButton);
        bindFlair(itemFlair);
        bindAnnouncementFlair(itemAnnouncementFlair, itemPostBg, itemFlair, itemUserType);
    }

    public boolean isImageLoaded() { return imageLoaded; }
    public ImageView getImageView() { return imageView; }
    public Post getPost() { return post; }

    public void setImageView(ImageView imageView) { this.imageView = imageView; }
    public void setFragment(Fragment fragment) { this.activeFragment = fragment; }
    public void setPostVisible(boolean postVisible) { this.isPostVisible = postVisible; }
}
