package com.suici.roverhood;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AddPost extends DialogFragment {

    private Context context;
    private FirebaseRepository firebaseRepository;
    private RoverFeed originalFeed;

    private EditText editTextDescription;
    private ImageView imagePreview;
    private Button submitPostButton;
    private Bitmap selectedImage;
    private ImageButton rotateRightButton;
    private MaterialAutoCompleteTextView topicDropdown;
    private EditText editTextTopic;
    private TextView clearTopicButton;


    private SwitchCompat switchAnnouncement;
    private TextView labelAnnouncement;

    private final int PICK_IMAGE_REQUEST = 1;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_post, container, false);

        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        context = getContext();
        firebaseRepository = FirebaseRepository.getInstance(context);

        editTextDescription = view.findViewById(R.id.editTextDescription);
        imagePreview = view.findViewById(R.id.imagePreview);
        submitPostButton = view.findViewById(R.id.submitPostButton);
        rotateRightButton = view.findViewById(R.id.buttonRotateRight);
        topicDropdown = view.findViewById(R.id.topicDropdown);
        editTextTopic = view.findViewById(R.id.editTextTopic);
        clearTopicButton = view.findViewById(R.id.clearTopicButton);

        switchAnnouncement = view.findViewById(R.id.switchAnnouncement);
        labelAnnouncement = view.findViewById(R.id.labelAnnouncement);

        topicDropdown.setText(FilterOptions.getTopic());
        User currentUser = ((MainActivity) getActivity()).getCurrentUser();
        populateTopicOptions();

        clearTopicButton.setOnClickListener(v -> {
            topicDropdown.setText("");
            editTextTopic.setText("");
        });

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
                int secondaryColor = ContextCompat.getColor(context, R.color.light_purple);
                labelAnnouncement.setTextColor(secondaryColor);
                topicDropdown.setVisibility(View.GONE);
                editTextTopic.setVisibility(View.VISIBLE);
                topicDropdown.setText("");
                editTextTopic.setText("");
            } else {
                int defaultColor = MaterialColors.getColor(labelAnnouncement, com.google.android.material.R.attr.colorOnSurface);
                labelAnnouncement.setTextColor(defaultColor);
                topicDropdown.setVisibility(View.VISIBLE);
                editTextTopic.setVisibility(View.GONE);
                topicDropdown.setText("");
                editTextTopic.setText("");
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
            Topic topic = Topic.findTopicByTitle(topicDropdown.getText().toString().trim());
            String newTopic = editTextTopic.getText().toString().trim();

            if (Topic.findTopicByTitle(newTopic) != null) {
                Toast.makeText(context, "Topic already exists", Toast.LENGTH_SHORT).show();
                submitPostButton.setEnabled(true);
                return;
            }

            if (newTopic.length() > 50) {
                Toast.makeText(context, "Topic " + (newTopic.length() - 50) + " characters too long", Toast.LENGTH_SHORT).show();
                submitPostButton.setEnabled(true);
                return;
            }

            if (description.isEmpty()) {
                editTextDescription.setError("Description required");
                submitPostButton.setEnabled(true);
                return;
            }

            if (editTextDescription.getLineCount() > 45) {
                if (editTextDescription.getError() == null
                    || "Description required".equals(editTextDescription.getError().toString()))
                    editTextDescription.setError((editTextDescription.getLineCount() - 45) + " too many lines");
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
                    if(switchAnnouncement.isChecked() && !newTopic.isEmpty()) {
                        savePostAndNewTopic(description, currentUser, downloadUrl, newTopic);
                    }
                    else {
                        savePost(description, currentUser, downloadUrl, topic);
                    }
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

    private void savePost(String description, User user, String imageUrl, Topic topic) {
        boolean isAnnouncement = switchAnnouncement.isChecked();
        firebaseRepository.createPost(description, user, imageUrl, isAnnouncement, topic, originalFeed, new FirebaseRepository.PostCreationCallback() {
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

    private void savePostAndNewTopic(String description, User user, String imageUrl, String newTopicTitle) {
        boolean isAnnouncement = switchAnnouncement.isChecked();
        firebaseRepository.createTopic(newTopicTitle, new FirebaseRepository.TopicCreationCallback() {
            @Override
            public void onTopicCreated(Topic topic) {
                firebaseRepository.createPost(description, user, imageUrl, isAnnouncement, topic, originalFeed, new FirebaseRepository.PostCreationCallback() {
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
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateTopicOptions() {
        List<Topic> sortedTopics = Topic.getAllTopics().stream()
                .sorted((t1, t2) -> Long.compare(t2.getCreationTime(), t1.getCreationTime()))
                .collect(Collectors.toList());

        List<String> topicTitles = sortedTopics.stream()
                .map(Topic::getTitle)
                .collect(Collectors.toList());

        ArrayAdapter<String> topicsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(topicTitles));
        topicDropdown.setAdapter(topicsAdapter);

        topicDropdown.setOnClickListener(v -> topicDropdown.showDropDown());
        topicDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                topicDropdown.showDropDown();
            }
        });
    }
}