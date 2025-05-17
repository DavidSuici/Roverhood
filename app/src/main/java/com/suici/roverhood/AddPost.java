package com.suici.roverhood;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
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
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

public class AddPost extends DialogFragment {

    private Context context;
    private PostRepository postRepository;
    private RoverFeed originalFeed;

    private EditText editTextDescription;
    private ImageView imagePreview;
    private Button submitPostButton;
    private Bitmap selectedImage;
    private ImageButton rotateRightButton;

    private SwitchCompat switchAnnouncement;
    private TextView labelAnnouncement;

    private final int PICK_IMAGE_REQUEST = 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_post, container, false);

        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        context = getContext();
        postRepository = PostRepository.getInstance(context);

        editTextDescription = view.findViewById(R.id.editTextDescription);
        imagePreview = view.findViewById(R.id.imagePreview);
        submitPostButton = view.findViewById(R.id.submitPostButton);
        rotateRightButton = view.findViewById(R.id.buttonRotateRight);

        switchAnnouncement = view.findViewById(R.id.switchAnnouncement);
        labelAnnouncement = view.findViewById(R.id.labelAnnouncement);

        User currentUser = ((MainActivity) getActivity()).getCurrentUser();

        switchAnnouncement.setChecked(false);
        if ("ORGANIZER".equals(currentUser.getUserType())
                || "ADMIN".equals(currentUser.getUserType())) {
            switchAnnouncement.setVisibility(View.VISIBLE);
            labelAnnouncement.setVisibility(View.VISIBLE);
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

        imagePreview.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        rotateRightButton.setOnClickListener(v -> {
            if (selectedImage != null) {
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

            if (selectedImage == null) {
                Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show();
                submitPostButton.setEnabled(true);
                return;
            }

            if (currentUser == null) {
                Toast.makeText(context, "You're logged out, log in again", Toast.LENGTH_SHORT).show();
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

            UploadImageUtils.uploadImageToFirebase(selectedImage, "postImage", new UploadImageUtils.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    savePost(description, currentUser, downloadUrl);
                }
                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show();
                }
            });

            dismiss();
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK && data != null) {
            try {
                selectedImage = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), data.getData());
                imagePreview.setImageBitmap(selectedImage);
            } catch (Exception e) {
                Log.e("AddPost", "Failed to load image from gallery", e);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Need this or the fragment wont load
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void setOriginalFeed(RoverFeed roverFeed) {
        this.originalFeed = roverFeed;
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void savePost(String description, User user, String imageUrl) {
        boolean isAnnouncement = switchAnnouncement.isChecked();
        postRepository.createPost(description, user, imageUrl, isAnnouncement, originalFeed, new PostRepository.PostCreationCallback() {
            @Override
            public void onPostCreated(Post post) {
                post.setImageView(imagePreview);
                originalFeed.addPostToUI(post);
                Toast.makeText(context, "Post created successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
}