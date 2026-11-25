package com.suici.roverhood.presentation;

import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
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
import com.suici.roverhood.dialogs.Comments;
import com.suici.roverhood.dialogs.EditPost;
import com.suici.roverhood.dialogs.LikesList;
import com.suici.roverhood.utils.FiltersManager;
import com.suici.roverhood.models.Post;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Date;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;

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

    private void bindTeamEmoji(TextView teamEmoji) {
        teamEmoji.setText(Teams.emoji(post.getUser().getTeam()));
    }

    // All the "bind..." methods are meant to reset the visual elements when a post is loaded,
    // or add behaviour for user interaction with specific visual elements
    private void bindEditedLabel(TextView editedLabelView) {
        if (post.getVersion() > 0) {
            editedLabelView.setVisibility(View.VISIBLE);
        } else {
            editedLabelView.setVisibility(View.GONE);
        }
    }

    private void bindTopicLabel(TextView topicView) {
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

    private void bindUserAndTeamButtons(TextView userView, TextView teamView) {
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

    // Adding the collapse / expand behaviour to description
    private void bindDescriptionToggle(TextView descriptionView, TextView seeMoreView) {

        // Setting the limit to 6 rows, and consider it collapsed
        descriptionView.setMaxLines(MAX_PREVIEW_ROWS + 1);
        seeMoreView.setVisibility(View.GONE);
        descriptionView.setMovementMethod(null);
        isExpanded = false;

        descriptionView.post(() -> {
            // If the text still exceeds the current limit, add "see more" on the last
            // line and keep links not working
            if (descriptionView.getLineCount() > MAX_PREVIEW_ROWS + 1) {
                descriptionView.setMaxLines(MAX_PREVIEW_ROWS);
                seeMoreView.setVisibility(View.VISIBLE);
            // If the text doesn't exceed the current limit, consider it expanded
            // and make links interactable, never having the option to collapse again
            } else {
                descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
                isExpanded = true;
            }
        });

        // When clicked, collapse or expand only if the text is longer than
        // the limit, links are interactable only when expanded
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

    // Width:height ratio of the collapsed image preview. On regular phones it is 1:1 (as before).
    // On wide / squarish screens (e.g. unfolded foldables) a 1:1 preview would fill the whole
    // screen, so the preview gets flatter to stay at most this fraction of the screen height.
    // Screens taller than ~2.1 x their width (most current phones, 19.5:9 / 20:9) are unaffected.
    private static final float MAX_PREVIEW_SCREEN_FRACTION = 0.48f;

    private float getPreviewRatio() {
        DisplayMetrics displayMetrics = activeFragment.getResources().getDisplayMetrics();
        float maxPreviewHeight = displayMetrics.heightPixels * MAX_PREVIEW_SCREEN_FRACTION;
        return Math.max(1f, displayMetrics.widthPixels / maxPreviewHeight);
    }

    // Returns the width / height ratio of the loaded image, or -1 if not available yet
    private float getImageRatio(ImageView imageView) {
        if (imageView.getDrawable() == null) return -1;
        int width = imageView.getDrawable().getIntrinsicWidth();
        int height = imageView.getDrawable().getIntrinsicHeight();
        if (width <= 0 || height <= 0) return -1;
        return (float) width / height;
    }

    private void setImageCollapsed(ImageView imageView, ImageButton fullScreenIcon, boolean collapsed, float previewRatio) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
        if (collapsed) {
            params.dimensionRatio = previewRatio + ":1";
            params.width = 0;
            params.height = 0;
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fullScreenIcon.setVisibility(View.VISIBLE);
        } else {
            params.dimensionRatio = null;
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            fullScreenIcon.setVisibility(View.GONE);
        }
        imageView.setLayoutParams(params);
        imageView.requestLayout();
    }

    // Adding the collapse / expand behaviour to the image
    // Images taller than the preview ratio start collapsed (cropped to the preview),
    // and clicking toggles between the preview and the full image
    private void bindImageClickToggle(ImageView imageView, ImageButton fullScreenIcon) {
        float previewRatio = getPreviewRatio();
        float imageRatio = getImageRatio(imageView);

        if (imageRatio > 0) {
            setImageCollapsed(imageView, fullScreenIcon, imageRatio < previewRatio, previewRatio);
        }

        imageView.setOnClickListener(v -> {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
            boolean isCollapsed = params.dimensionRatio != null;
            float currentPreviewRatio = getPreviewRatio();

            if (isCollapsed) {
                setImageCollapsed(imageView, fullScreenIcon, false, currentPreviewRatio);
            } else {
                float ratio = getImageRatio(imageView);
                if (ratio > 0 && ratio < currentPreviewRatio) {
                    setImageCollapsed(imageView, fullScreenIcon, true, currentPreviewRatio);
                }
            }
        });
    }

    // This flair is changing color based on the relation of the post author
    // with the logged in user: posts made by author = primary color, by team
    // member = blue accent color, by anyone else = secondary color
    private void bindFlair(View flair) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();

        if (Objects.equals(currentUser.getId(), post.getUser().getId())) {
            flairColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorPrimary);
        } else if (Objects.equals(currentUser.getTeam(), post.getUser().getTeam())) {
            flairColor = activeFragment.requireContext().getResources().getColor(R.color.blue_accent, null);
        } else {
            flairColor = MaterialColors.getColor(flair, com.google.android.material.R.attr.colorSecondary);
        }
        // Same height as the photo frame, so both right corners are rounded (with a photo the
        // frame of the same color is behind it)
        flair.setBackground(roundedRight(flair, flairColor, true));
    }

    private int flairColor;

    // Background with only the right corners rounded (top one optional)
    private static android.graphics.drawable.GradientDrawable roundedRight(View view, int color, boolean roundTop) {
        float radius = 3 * view.getResources().getDisplayMetrics().density;
        float top = roundTop ? radius : 0;
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadii(new float[]{0, 0, top, top, radius, radius, 0, 0});
        return background;
    }

    // Adds special coloring for a post which is marked as announcement
    public void bindAnnouncementFlair(View announcementFlair, View postBG, View flair, TextView userType) {
        if(post.isAnnouncement()) {
            announcementFlair.setVisibility(View.VISIBLE);
            flair.setVisibility(View.GONE);
            int amberColor = userType.getContext().getResources().getColor(R.color.announcements_amber, userType.getContext().getTheme());
            // Longer than the photo frame: with a photo its top continues the frame, so only
            // the bottom-right corner is rounded
            boolean hasPhoto = !UserProfiles.photoUrl(post.getUser().getId()).isEmpty();
            announcementFlair.setBackground(roundedRight(announcementFlair, amberColor, !hasPhoto));
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

    // Binds like checkbox button and like counter to current post
    // Initializes checkbox state based on whether the current user liked the post
    // Disables interaction if the post is offline
    // On change, updates local data and syncs status to Firebase
    private void bindLikeButton(CheckBox heartButton, View heartPadding, TextView heartNrView) {
        String currentUserId = ((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId();
        heartButton.setOnCheckedChangeListener(null);

        if (post.getLikedBy() != null)
            heartButton.setChecked(post.getLikedBy().containsKey(currentUserId));
        else
            heartButton.setChecked(false);

        // Long press on the heart or the number shows who liked the post (also offline).
        // Returning true keeps the long press from also toggling the like.
        View.OnLongClickListener showLikes = v -> {
            LikesList.show(activeFragment.requireContext(), post.getLikedBy(),
                    activeFragment instanceof RoverFeed ? (RoverFeed) activeFragment : null, null);
            return true;
        };
        heartButton.setOnLongClickListener(showLikes);
        heartPadding.setOnLongClickListener(showLikes);
        heartNrView.setOnLongClickListener(showLikes);

        if (offlinePost) {
            heartButton.setAlpha(0.5f);
            heartButton.setEnabled(false);
            // A disabled but clickable view swallows touches; let them reach heartPadding instead
            heartButton.setClickable(false);
            heartButton.setLongClickable(false);
        }
        else {
            heartButton.setAlpha(1.0f);
            heartButton.setEnabled(true);
            heartButton.setClickable(true);
            heartButton.setLongClickable(true);

            heartButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Avoiding random resets, only adding the logic for visible posts
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

    // Opens the comments of this post. Works offline too (read-only, from the local cache)
    private void bindCommentButton(View commentButton, View commentPadding, TextView commentNrView) {
        commentNrView.setText(String.valueOf(post.getCommentCount()));

        View.OnClickListener openComments = v -> {
            Comments commentsDialog = new Comments(this, activeFragment, offlinePost);
            commentsDialog.show(activeFragment.getParentFragmentManager(), "commentsDialog");
        };
        commentButton.setOnClickListener(openComments);
        commentPadding.setOnClickListener(openComments);
    }

    // Binds the options menu button of the post (hidden for offline posts):
    // Edit for the owner, Delete for the owner and staff, Report for everyone else.
    private void bindMenuButton(ImageButton menuButton) {
        User currentUser = ((MainActivity) activeFragment.requireActivity()).getCurrentUser();
        boolean isOwner = Objects.equals(post.getUser().getId(), currentUser.getId());
        // Organizers, moderators and admins can edit / delete any post
        boolean isOrganizer = com.suici.roverhood.utils.Roles.isStaff(currentUser);
        boolean isAdmin = com.suici.roverhood.utils.Roles.isAdmin(currentUser);

        // Everyone else can report the post, so the menu is shown on every online post
        boolean canReport = !isOwner;
        if ((!isOwner && !isOrganizer && !isAdmin && !canReport) || offlinePost) {
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
            popup.getMenu().findItem(R.id.action_report).setVisible(canReport);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    editPost();
                    return true;
                } else if (id == R.id.action_delete) {
                    deletePost();
                    return true;
                } else if (id == R.id.action_report) {
                    com.suici.roverhood.dialogs.Report.show(activeFragment.requireContext(),
                            post.getId(), null, post.getUser().getId(), currentUser.getId());
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

    // Loading the image into the ImageView with Glide, only doing so
    // when this post is loaded in the feed and this method is called
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

    public void loadIntoView(View itemView) {
        Log.d("test2", "load into view  -  " + post.getDescription());

        // Find views
        TextView itemUser = itemView.findViewById(R.id.user);
        TextView itemTeam = itemView.findViewById(R.id.team);
        TextView itemTeamEmoji = itemView.findViewById(R.id.teamEmoji);
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
        View itemCommentButton = itemView.findViewById(R.id.commentButton);
        View itemCommentPadding = itemView.findViewById(R.id.commentPadding);
        TextView itemCommentNr = itemView.findViewById(R.id.commentNr);

        // Populate the views
        itemUser.setText(post.getUser().getUsername());
        itemTeam.setText(Teams.name(post.getUser().getTeam()));
        itemUserType.setText(post.getUser().getUserType());
        itemDate.setText(Date.formatTimestamp(post.getDate()) + "\u00A0");
        itemDescription.setText(post.getDescription());
        itemHeartNr.setText(String.valueOf(post.getLikes()));
        itemImage.setImageDrawable(imageView.getDrawable());
        itemHeart.setChecked(post.getLikedBy().containsKey(((MainActivity) activeFragment.requireActivity()).getCurrentUser().getId()));

        bindTeamEmoji(itemTeamEmoji);
        bindEditedLabel(itemEdited);
        bindTopicLabel(itemTopic);
        bindUserAndTeamButtons(itemUser, itemTeam);
        bindDescriptionToggle(itemDescription, itemSeeMore);
        bindImageClickToggle(itemImage, itemFullScreenIcon);
        bindLikeButton(itemHeart, itemView.findViewById(R.id.heartPadding), itemHeartNr);
        bindCommentButton(itemCommentButton, itemCommentPadding, itemCommentNr);
        bindMenuButton(itemMenuButton);
        bindFlair(itemFlair);
        bindAnnouncementFlair(itemAnnouncementFlair, itemPostBg, itemFlair, itemUserType);
        bindProfilePhoto(itemView.findViewById(R.id.profilePhoto), itemFlair);
        // Tapping the photo opens the author, like tapping their name
        itemView.findViewById(R.id.profilePhoto).setOnClickListener(v -> itemUser.performClick());
    }

    // Optional profile photo of the author, framed in the color of the flair (amber for
    // announcements). Without a photo it is hidden and the post looks as before.
    private void bindProfilePhoto(ImageView photo, View flair) {
        if (!UserProfiles.loadPhoto(photo, post.getUser().getId())) return;

        int frameColor;
        if (post.isAnnouncement()) {
            frameColor = photo.getContext().getResources().getColor(R.color.announcements_amber, photo.getContext().getTheme());
        } else {
            frameColor = flairColor;
        }
        // Only the right corners are rounded: the left side continues the flair
        float radius = 5 * photo.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setColor(frameColor);
        frame.setCornerRadii(new float[]{0, 0, radius, radius, radius, radius, 0, 0});
        photo.setBackground(frame);
    }

    public boolean isImageLoaded() { return imageLoaded; }
    public ImageView getImageView() { return imageView; }
    public Post getPost() { return post; }
    public boolean isOfflinePost() { return offlinePost; }

    public void setImageView(ImageView imageView) { this.imageView = imageView; }
    public void setFragment(Fragment fragment) { this.activeFragment = fragment; }
    public void setPostVisible(boolean postVisible) { this.isPostVisible = postVisible; }
}
