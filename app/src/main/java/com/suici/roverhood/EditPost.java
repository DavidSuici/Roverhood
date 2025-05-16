package com.suici.roverhood;

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
import androidx.fragment.app.DialogFragment;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class EditPost extends DialogFragment {

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
                int secondaryColor = MaterialColors.getColor(labelAnnouncement, com.google.android.material.R.attr.colorSecondary);
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
                Toast.makeText(getContext(), "Image too tall or too wide", Toast.LENGTH_SHORT).show();
                submitPostButton.setEnabled(true);
                return;
            }

            boolean descriptionChanged = !description.equals(post.getDescription());
            boolean announcementChanged = switchAnnouncement.isChecked() != post.isAnnouncement();

            if (imageChanged) {
                StorageReference oldImageRef = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(post.getImageUrl());
                oldImageRef.delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("EditPost", "Image successfully deleted from Firebase Storage.");
                    } else {
                        Log.e("EditPost", "Failed to delete image from Firebase Storage.", task.getException());
                    }
                });

                UploadImageUtils.uploadImageToFirebase(selectedImage, "postImage", new UploadImageUtils.UploadCallback() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        updatePost(description, downloadUrl);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Log.e("EditPost", "Failed to upload image: " + e.getMessage());
                    }
                });
            } else {
                if(descriptionChanged || announcementChanged)
                    updatePost(description, post.getImageUrl());
                else  {
                    Toast.makeText(getContext(), "Nothing was changed", Toast.LENGTH_SHORT).show();
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
        DatabaseReference postRef = FirebaseDatabase
                .getInstance("https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("posts")
                .child(post.getId());

        boolean isAnnouncement = switchAnnouncement.isChecked();

        postRef.child("description").setValue(description);
        postRef.child("imageUrl").setValue(imageUrl);
        postRef.child("announcement").setValue(isAnnouncement);

        post.setDescription(description);
        post.setImageView(imagePreview);
        post.setImageUrl(imageUrl);
        post.setAnnouncement(isAnnouncement);

        RoverFeed roverFeed = (RoverFeed) activeFragment;
        if (roverFeed != null) {
            roverFeed.updatePostInUI(post);
        }
        else Log.e("EditPost", "RoverFeed null");
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}