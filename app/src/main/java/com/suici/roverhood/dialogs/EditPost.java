package com.suici.roverhood.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.PostHandler;
import com.suici.roverhood.R;
import com.suici.roverhood.fragments.RoverFeed;
import com.suici.roverhood.models.User;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.utils.image.ImageUpload;

public class EditPost extends DialogFragment {

    private FirebaseRepository firebaseRepository;
    private Context context;

    private EditText editTextDescription;
    private ImageView imagePreview;
    private Button submitPostButton;
    private ImageButton rotateRightButton;
    private TextView titleView;
    private SwitchCompat switchAnnouncement;
    private TextView labelAnnouncement;

    private PostHandler postHandler;
    private Fragment activeFragment;
    private Bitmap selectedImage;
    private boolean imageChanged = false;
    private int rotationTimes = 0;

    public EditPost(PostHandler postHandler, Fragment activeFragment) {
        this.postHandler = postHandler;
        this.activeFragment = activeFragment;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_post, container, false);

        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);

        // Find  all the visual elements and initialise some of them
        editTextDescription = view.findViewById(R.id.editTextDescription);
        imagePreview = view.findViewById(R.id.imagePreview);
        submitPostButton = view.findViewById(R.id.submitPostButton);
        rotateRightButton = view.findViewById(R.id.buttonRotateRight);
        titleView = view.findViewById(R.id.titleText);
        switchAnnouncement = view.findViewById(R.id.switchAnnouncement);
        labelAnnouncement = view.findViewById(R.id.labelAnnouncement);

        editTextDescription.setText(postHandler.getPost().getDescription());
        imagePreview.setImageDrawable(postHandler.getImageView().getDrawable());
        selectedImage = drawableToBitmap(postHandler.getImageView().getDrawable());

        titleView.setText("Edit post");
        submitPostButton.setText("Submit updates");
        TextInputLayout inputLayoutDescription = view.findViewById(R.id.inputLayoutDescription);
        inputLayoutDescription.setHint("Edit Post Description");
        ConstraintLayout topicSelectGroup = view.findViewById(R.id.topicSelectGroup);
        topicSelectGroup.setVisibility(View.GONE);

        User currentUser = ((MainActivity) getActivity()).getCurrentUser();

        // Logic for scrolling by dragging while editing the description
        editTextDescription.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getId() == R.id.editTextDescription) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_UP:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                }
                return false;
            }
        });

        switchAnnouncement.setChecked(postHandler.getPost().isAnnouncement());
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

        bindSubmitPostButton();

        return view;
    }

    private void bindSubmitPostButton() {
        submitPostButton.setOnClickListener(v -> {
            submitPostButton.setEnabled(false);

            // Enforce the required criteria for inputs:
            String description = editTextDescription.getText().toString().trim();
            if (description.isEmpty()) {
                editTextDescription.setError("Description required");
                submitPostButton.setEnabled(true);
                return;
            }

            if (editTextDescription.getLineCount() > 55) {
                if (editTextDescription.getError() == null
                        || "Description required".equals(editTextDescription.getError().toString()))
                    editTextDescription.setError("Exceeded text limit. Reduce by " + (editTextDescription.getLineCount() - 55) + " row(s)");
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

            // Check which element has changed, an image change requires uploading a new image,
            // while only a description or announcement change only requires changes in the Firebase Database.
            boolean descriptionChanged = !description.equals(postHandler.getPost().getDescription());
            boolean announcementChanged = switchAnnouncement.isChecked() != postHandler.getPost().isAnnouncement();

            if (imageChanged) {
                ImageUpload.uploadImageToFirebase(context, selectedImage, "postImage", new ImageUpload.imageUploadCallback() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        deleteOldImage(postHandler.getPost().getImageUrl());
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
                    updatePost(description, postHandler.getPost().getImageUrl());
                else  {
                    Toast.makeText(context, "Nothing was changed", Toast.LENGTH_SHORT).show();
                    submitPostButton.setEnabled(true);
                    return;
                }
            }
            dismiss();
        });
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // Extracts the Bitmap from the image assigned to the post
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

        firebaseRepository.updatePost(postHandler, description, imageUrl, isAnnouncement, new FirebaseRepository.PostOperationCallback() {
            @Override
            public void onSuccess() {

                postHandler.getPost().setDescription(description);
                postHandler.getPost().setImageUrl(imageUrl);
                postHandler.getPost().setAnnouncement(isAnnouncement);
                postHandler.setImageView(imagePreview);

                RoverFeed roverFeed = (RoverFeed) activeFragment;
                if (roverFeed != null) {
                    roverFeed.updatePostInUI(postHandler);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteOldImage(String imageUrl) {
        firebaseRepository.deleteImageFromStorage(imageUrl, new FirebaseRepository.PostOperationCallback() {
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