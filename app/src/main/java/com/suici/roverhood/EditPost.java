package com.suici.roverhood;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

public class EditPost extends DialogFragment {

    private PostRepository postRepository;
    private Context context;

    private EditText editTextDescription;
    private ImageView imagePreview;
    private Button submitPostButton;
    private ImageButton rotateRightButton;
    private TextView titleView;
    private SwitchCompat switchAnnouncement;
    private TextView labelAnnouncement;

    private Post post;
    private Fragment activeFragment;
    private Bitmap selectedImage;
    private boolean imageChanged = false;
    private int rotationTimes = 0;

    public EditPost(Post post, Fragment activeFragment) {
        this.post = post;
        this.activeFragment = activeFragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_post, container, false);

        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        context = requireContext();
        postRepository = PostRepository.getInstance(context);

        editTextDescription = view.findViewById(R.id.editTextDescription);
        imagePreview = view.findViewById(R.id.imagePreview);
        submitPostButton = view.findViewById(R.id.submitPostButton);
        rotateRightButton = view.findViewById(R.id.buttonRotateRight);
        titleView = view.findViewById(R.id.titleText);
        switchAnnouncement = view.findViewById(R.id.switchAnnouncement);
        labelAnnouncement = view.findViewById(R.id.labelAnnouncement);

        editTextDescription.setText(post.getDescription());
        imagePreview.setImageDrawable(post.getImageView().getDrawable());
        selectedImage = drawableToBitmap(post.getImageView().getDrawable());

        titleView.setText("Edit post");
        submitPostButton.setText("Submit updates");
        TextInputLayout inputLayoutDescription = view.findViewById(R.id.inputLayoutDescription);
        inputLayoutDescription.setHint("Edit Post Description");
        ConstraintLayout topicSelectGroup = view.findViewById(R.id.topicSelectGroup);
        topicSelectGroup.setVisibility(View.GONE);

        User currentUser = ((MainActivity) getActivity()).getCurrentUser();

        switchAnnouncement.setChecked(post.isAnnouncement());
        if ("ORGANIZER".equals(currentUser.getUserType())
                || "ADMIN".equals(currentUser.getUserType())) {
            switchAnnouncement.setVisibility(View.VISIBLE);
            labelAnnouncement.setVisibility(View.VISIBLE);
            if (switchAnnouncement.isChecked()) {
                int secondaryColor = MaterialColors.getColor(labelAnnouncement, com.google.android.material.R.attr.colorSecondary);
                labelAnnouncement.setTextColor(secondaryColor);
            }
        }
        else {
            switchAnnouncement.setVisibility(View.GONE);
            labelAnnouncement.setVisibility(View.GONE);
        }

        switchAnnouncement.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                int secondaryColor = ContextCompat.getColor(context, R.color.light_purple);
                labelAnnouncement.setTextColor(secondaryColor);
            } else {
                int defaultColor = MaterialColors.getColor(labelAnnouncement, com.google.android.material.R.attr.colorOnSurface);
                labelAnnouncement.setTextColor(defaultColor);
            }
        });

        rotateRightButton.setOnClickListener(v -> {
            if (selectedImage != null) {
                rotationTimes++;
                if (rotationTimes >=4) {
                    rotationTimes = 0;
                    imageChanged = false;
                }
                else {
                    imageChanged = true;
                }
                selectedImage = rotateBitmap(selectedImage);
                imagePreview.setImageBitmap(selectedImage);
            }
        });

        submitPostButton.setOnClickListener(v -> {
            submitPostButton.setEnabled(false);

            String description = editTextDescription.getText().toString().trim();
            if (description.isEmpty()) {
                editTextDescription.setError("Description required");
                submitPostButton.setEnabled(true);
                return;
            }

            int width = selectedImage.getWidth();
            int height = selectedImage.getHeight();
            float ratio = (float) width / height;
            if (ratio < 0.45f || ratio > 6.0f) {
                Toast.makeText(context, "Image too tall or too wide", Toast.LENGTH_SHORT).show();
                submitPostButton.setEnabled(true);
                return;
            }

            boolean descriptionChanged = !description.equals(post.getDescription());
            boolean announcementChanged = switchAnnouncement.isChecked() != post.isAnnouncement();

            if (imageChanged) {
                UploadImageUtils.uploadImageToFirebase(selectedImage, "postImage", new UploadImageUtils.UploadCallback() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        deleteOldImage(post.getImageUrl());
                        updatePost(description, downloadUrl);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Log.e("EditPost", "Failed to upload image: " + e.getMessage());
                        Toast.makeText(context, "Failed to update post.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                if(descriptionChanged || announcementChanged)
                    updatePost(description, post.getImageUrl());
                else  {
                    Toast.makeText(context, "Nothing was changed", Toast.LENGTH_SHORT).show();
                    submitPostButton.setEnabled(true);
                    return;
                }
            }
            dismiss();
        });

        return view;
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private void updatePost(String description, String imageUrl) {
        boolean isAnnouncement = switchAnnouncement.isChecked();

        postRepository.updatePost(post, description, imageUrl, isAnnouncement, new PostRepository.PostOperationCallback() {
            @Override
            public void onSuccess() {

                post.setDescription(description);
                post.setImageUrl(imageUrl);
                post.setAnnouncement(isAnnouncement);
                post.setImageView(imagePreview);

                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (roverFeed != null) {
                    roverFeed.updatePostInUI(post);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteOldImage(String imageUrl) {
        postRepository.deleteImageFromStorage(imageUrl, new PostRepository.PostOperationCallback() {
            @Override
            public void onSuccess() {
                Log.d("EditPost", "Image deleted successfully");
            }
            @Override
            public void onError(String errorMessage) {
                Log.e("EditPost", "Error deleting image: " + errorMessage);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}