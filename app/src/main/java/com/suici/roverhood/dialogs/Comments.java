package com.suici.roverhood.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.databases.LocalDatabase;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.Comment;
import com.suici.roverhood.models.User;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.utils.Date;
import com.suici.roverhood.utils.Teams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Shows the comments of a post. Online: loads from Firebase (and caches locally),
// allows adding, editing own comments and deleting (own, or any for admins/organizers).
// Offline: shows the locally cached comments, read-only.
public class Comments extends DialogFragment {

    private static final int MAX_COMMENT_LENGTH = 500;
    // Non-breaking space added after the italic date so its last digit isn't clipped (same as posts)
    private static final String NBSP = String.valueOf((char) 0xA0);

    private final PostHandler postHandler;
    private final Fragment activeFragment;
    private final boolean offlinePost;

    private Context context;
    private FirebaseRepository firebaseRepository;
    private User currentUser;
    private Map<String, User> usersMap;

    private final List<Comment> comments = new ArrayList<>();
    private CommentAdapter adapter;
    private boolean isOffline;
    private boolean commentCountChanged = false;
    private String focusCommentId = null;

    private RecyclerView commentsList;
    private TextView emptyLabel;
    private TextView offlineLabel;
    private ProgressBar loadingComments;
    private View inputGroup;
    private EditText editTextComment;
    private Button sendCommentButton;

    public Comments(PostHandler postHandler, Fragment activeFragment, boolean offlinePost) {
        this.postHandler = postHandler;
        this.activeFragment = activeFragment;
        this.offlinePost = offlinePost;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_comments, container, false);

        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);
        currentUser = ((MainActivity) requireActivity()).getCurrentUser();
        usersMap = LocalDatabase.getInstance(context).getAllUsers();

        commentsList = view.findViewById(R.id.commentsList);
        emptyLabel = view.findViewById(R.id.emptyLabel);
        offlineLabel = view.findViewById(R.id.offlineLabel);
        loadingComments = view.findViewById(R.id.loadingComments);
        inputGroup = view.findViewById(R.id.inputGroup);
        editTextComment = view.findViewById(R.id.editTextComment);
        sendCommentButton = view.findViewById(R.id.sendCommentButton);

        adapter = new CommentAdapter();
        commentsList.setLayoutManager(new LinearLayoutManager(context));
        commentsList.setAdapter(adapter);

        inputGroup.setVisibility(View.GONE);
        sendCommentButton.setOnClickListener(v -> sendComment());

        // Panel is at most 80% of the screen; tapping outside it closes the dialog
        View panel = view.findViewById(R.id.commentsPanel);
        ConstraintLayout.LayoutParams panelParams = (ConstraintLayout.LayoutParams) panel.getLayoutParams();
        panelParams.matchConstraintMaxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        panel.setLayoutParams(panelParams);
        view.findViewById(R.id.commentsRoot).setOnClickListener(v -> dismiss());

        // Keep the newest comments in view when the keyboard shrinks the list
        commentsList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t < ob - ot) scrollToBottom();
        });

        loadComments();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null || getDialog().getWindow() == null) return;

        // Full screen transparent window: with adjustResize it shrinks above the keyboard,
        // and the panel inside (max 80% of the screen) shrinks with it, keeping the input visible
        Window window = getDialog().getWindow();
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Refresh the counter under the comment icon in the feed
        if (commentCountChanged && activeFragment instanceof RoverFeed) {
            ((RoverFeed) activeFragment).updatePostInUI(postHandler);
        }
    }

    private void loadComments() {
        loadingComments.setVisibility(View.VISIBLE);
        emptyLabel.setVisibility(View.GONE);

        String postId = postHandler.getPost().getId();
        firebaseRepository.loadComments(postId, offlinePost, (loadedComments, offline) -> {
            if (!isAdded()) return;

            isOffline = offline;
            comments.clear();
            comments.addAll(loadedComments);
            adapter.notifyDataSetChanged();

            // Online result is the source of truth for the counter
            if (!offline && postHandler.getPost().getCommentCount() != comments.size()) {
                postHandler.getPost().setCommentCount(comments.size());
                commentCountChanged = true;
            }

            loadingComments.setVisibility(View.GONE);
            offlineLabel.setVisibility(offline ? View.VISIBLE : View.GONE);
            inputGroup.setVisibility(offline ? View.GONE : View.VISIBLE);
            updateEmptyLabel();
            if (!focusComment()) scrollToBottom();
        });
    }

    // Opened from a notification: scroll to that comment and briefly highlight it
    public void setFocusCommentId(String commentId) {
        this.focusCommentId = commentId;
    }

    private boolean focusComment() {
        if (focusCommentId == null) return false;
        for (int i = 0; i < comments.size(); i++) {
            if (focusCommentId.equals(comments.get(i).getId())) {
                int position = i;
                focusCommentId = null;
                commentsList.scrollToPosition(position);
                commentsList.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = commentsList.findViewHolderForAdapterPosition(position);
                    if (holder == null) return;
                    View item = holder.itemView;
                    item.animate().alpha(0.3f).setDuration(250).withEndAction(() ->
                            item.animate().alpha(1f).setDuration(250).withEndAction(() ->
                                    item.animate().alpha(0.3f).setDuration(250).withEndAction(() ->
                                            item.animate().alpha(1f).setDuration(250).start()
                                    ).start()
                            ).start()
                    ).start();
                }, 300);
                return true;
            }
        }
        focusCommentId = null;
        return false;
    }

    private void sendComment() {
        String text = editTextComment.getText().toString().trim();
        if (text.isEmpty()) {
            editTextComment.setError("Comment cannot be empty");
            return;
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            editTextComment.setError("Comment is " + (text.length() - MAX_COMMENT_LENGTH) + " characters too long");
            return;
        }

        sendCommentButton.setEnabled(false);
        firebaseRepository.addComment(postHandler.getPost().getId(), currentUser.getId(), text,
                new FirebaseRepository.CommentCreationCallback() {
                    @Override
                    public void onCommentCreated(Comment comment) {
                        if (!isAdded()) return;
                        comments.add(comment);
                        adapter.notifyItemInserted(comments.size() - 1);
                        postHandler.getPost().setCommentCount(postHandler.getPost().getCommentCount() + 1);
                        commentCountChanged = true;

                        editTextComment.setText("");
                        sendCommentButton.setEnabled(true);
                        updateEmptyLabel();
                        scrollToBottom();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded()) return;
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                        sendCommentButton.setEnabled(true);
                    }
                });
    }

    // "Date: 24 November 2026 - 23:51  ·  Change"
    private CharSequence dateLabel(long seconds) {
        String label = "Date: ";
        String change = "  ·  Change";
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(
                label + Date.formatTimestamp(seconds) + change);
        text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.ForegroundColorSpan(com.google.android.material.color.MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorPrimary, 0)),
                text.length() - change.length(), text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void showEditDialog(Comment comment) {
        TextInputEditText input = new TextInputEditText(context);
        input.setText(comment.getText());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_COMMENT_LENGTH)});
        input.setMaxLines(6);
        input.setSelection(input.getText() != null ? input.getText().length() : 0);

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout wrapper = new android.widget.LinearLayout(context);
        wrapper.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrapper.setPadding(padding, padding / 2, padding, 0);
        wrapper.addView(input);

        // Staff (organizers, moderators, admins) can also change the comment's date
        long[] newDate = {-1};   // -1 = unchanged
        if (com.suici.roverhood.utils.Roles.isStaff(currentUser)) {
            TextView dateButton = new TextView(context);
            dateButton.setPadding(0, padding / 2, 0, padding / 4);
            dateButton.setTextSize(15);
            dateButton.setBackgroundResource(android.R.drawable.list_selector_background);
            dateButton.setText(dateLabel(comment.getDate()));
            dateButton.setOnClickListener(v -> Date.pickDateTime(context,
                    newDate[0] != -1 ? newDate[0] : comment.getDate(), picked -> {
                        newDate[0] = picked == comment.getDate() ? -1 : picked;
                        dateButton.setText(dateLabel(picked));
                    }));
            wrapper.addView(dateButton);
        }

        AlertDialog editDialog = new AlertDialog.Builder(context)
                .setTitle("Edit comment")
                .setView(wrapper)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create();

        // Overriding the click so the dialog stays open on validation errors
        editDialog.setOnShowListener(d -> editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newText = input.getText() != null ? input.getText().toString().trim() : "";
            if (newText.isEmpty()) {
                input.setError("Comment cannot be empty");
                return;
            }
            if (newText.equals(comment.getText()) && newDate[0] == -1) {
                Toast.makeText(context, "Nothing was changed", Toast.LENGTH_SHORT).show();
                return;
            }

            Long dateToSave = newDate[0] != -1 ? newDate[0] : null;
            firebaseRepository.editComment(comment, newText, dateToSave, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    if (!isAdded()) return;
                    if (dateToSave != null) {
                        // The order may change: comments are shown oldest first
                        comments.sort((a, b) -> Long.compare(a.getDate(), b.getDate()));
                        adapter.notifyDataSetChanged();
                    } else {
                        int index = comments.indexOf(comment);
                        if (index != -1) adapter.notifyItemChanged(index);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (!isAdded()) return;
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
            editDialog.dismiss();
        }));

        editDialog.show();
    }

    private void confirmDelete(Comment comment) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Comment")
                .setMessage("Are you sure you want to delete this comment?")
                .setPositiveButton("Yes", (dialog, which) ->
                        firebaseRepository.deleteComment(comment, new FirebaseRepository.PostOperationCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                int index = comments.indexOf(comment);
                                if (index != -1) {
                                    comments.remove(index);
                                    adapter.notifyItemRemoved(index);
                                }
                                postHandler.getPost().setCommentCount(postHandler.getPost().getCommentCount() - 1);
                                commentCountChanged = true;
                                updateEmptyLabel();
                                Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (!isAdded()) return;
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private RoverFeed feed() {
        return activeFragment instanceof RoverFeed ? (RoverFeed) activeFragment : null;
    }

    private void closeComments() {
        if (isAdded()) dismiss();
    }

    private void updateEmptyLabel() {
        emptyLabel.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void scrollToBottom() {
        if (!comments.isEmpty()) {
            commentsList.scrollToPosition(comments.size() - 1);
        }
    }


    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

        class CommentViewHolder extends RecyclerView.ViewHolder {
            final TextView user, teamEmoji, date, edited, text, heartNr;
            final ImageButton menuButton, heart;
            final android.widget.ImageView photo;

            CommentViewHolder(View itemView) {
                super(itemView);
                photo = itemView.findViewById(R.id.commentPhoto);
                user = itemView.findViewById(R.id.commentUser);
                teamEmoji = itemView.findViewById(R.id.commentTeamEmoji);
                date = itemView.findViewById(R.id.commentDate);
                edited = itemView.findViewById(R.id.commentEdited);
                text = itemView.findViewById(R.id.commentText);
                menuButton = itemView.findViewById(R.id.commentMenuButton);
                heart = itemView.findViewById(R.id.commentHeart);
                heartNr = itemView.findViewById(R.id.commentHeartNr);
            }
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
            return new CommentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            Comment comment = comments.get(position);
            User author = usersMap.get(comment.getUserId());

            holder.user.setText(author != null ? author.getUsername() : "Unknown user");
            holder.teamEmoji.setText(author != null ? Teams.label(author.getTeam()) : "");
            com.suici.roverhood.utils.UserProfiles.loadPhoto(holder.photo, comment.getUserId());

            // Tapping the author or their team closes the comments and filters the feed
            if (author != null && feed() != null) {
                holder.user.setOnClickListener(v -> {
                    closeComments();
                    feed().filterByUser(author.getUsername());
                });
                holder.teamEmoji.setOnClickListener(v -> {
                    closeComments();
                    feed().filterByTeam(author.getTeam());
                });
                holder.photo.setOnClickListener(v -> holder.user.performClick());
            } else {
                holder.photo.setOnClickListener(null);
                holder.user.setOnClickListener(null);
                holder.teamEmoji.setOnClickListener(null);
            }
            // Trailing non-breaking space keeps the italic last digit from being clipped (same as posts)
            holder.date.setText(Date.formatTimestamp(comment.getDate()) + NBSP);
            holder.edited.setVisibility(comment.isEdited() ? View.VISIBLE : View.GONE);
            holder.text.setText(comment.getText());
            bindHeart(holder, comment);

            boolean isOwner = Objects.equals(comment.getUserId(), currentUser.getId());
            boolean isModerator = com.suici.roverhood.utils.Roles.isStaff(currentUser);
            boolean canEdit = isOwner;
            boolean canDelete = isOwner || isModerator;
            boolean canReport = !isOwner;

            if (isOffline || (!canEdit && !canDelete && !canReport)) {
                holder.menuButton.setVisibility(View.GONE);
                holder.menuButton.setOnClickListener(null);
                return;
            }

            holder.menuButton.setVisibility(View.VISIBLE);
            holder.menuButton.setOnClickListener(v -> {
                androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(context, holder.menuButton);
                popup.getMenuInflater().inflate(R.menu.menu_post, popup.getMenu());
                popup.getMenu().findItem(R.id.action_edit).setVisible(canEdit);
                popup.getMenu().findItem(R.id.action_delete).setVisible(canDelete);
                popup.getMenu().findItem(R.id.action_report).setVisible(canReport);

                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.action_edit) {
                        showEditDialog(comment);
                        return true;
                    } else if (item.getItemId() == R.id.action_delete) {
                        confirmDelete(comment);
                        return true;
                    } else if (item.getItemId() == R.id.action_report) {
                        Report.show(context, comment.getPostId() != null ? comment.getPostId()
                                        : postHandler.getPost().getId(),
                                comment.getId(), comment.getUserId(), currentUser.getId());
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        // Like works like on posts: anyone (including the author) can like, not possible offline.
        // The UI updates immediately and is reverted if Firebase rejects the change.
        // Long press on the heart or the number shows who liked (also offline).
        private void bindHeart(CommentViewHolder holder, Comment comment) {
            String currentUserId = currentUser.getId();
            boolean liked = comment.isLikedBy(currentUserId);

            holder.heart.setImageResource(liked ? R.drawable.heart_fill : R.drawable.heart_outline);
            holder.heartNr.setText(String.valueOf(comment.getLikes()));
            holder.heart.setAlpha(isOffline ? 0.5f : 1.0f);

            View.OnLongClickListener showLikes = v -> {
                LikesList.show(context, comment.getLikedBy(), feed(), Comments.this::closeComments);
                return true;
            };
            holder.heart.setOnLongClickListener(showLikes);
            holder.heartNr.setOnLongClickListener(showLikes);

            holder.heart.setOnClickListener(v -> {
                if (isOffline) return;
                boolean newLiked = !comment.isLikedBy(currentUserId);
                setLiked(comment, currentUserId, newLiked);
                notifyCommentChanged(comment);

                firebaseRepository.toggleCommentLike(comment, currentUserId, newLiked, new FirebaseRepository.PostOperationCallback() {
                    @Override
                    public void onSuccess() { }

                    @Override
                    public void onError(String errorMessage) {
                        setLiked(comment, currentUserId, !newLiked);
                        if (!isAdded()) return;
                        notifyCommentChanged(comment);
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private void setLiked(Comment comment, String userId, boolean liked) {
            if (liked) comment.getLikedBy().put(userId, true);
            else comment.getLikedBy().remove(userId);
        }

        private void notifyCommentChanged(Comment comment) {
            int index = comments.indexOf(comment);
            if (index != -1) notifyItemChanged(index);
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }
    }
}
