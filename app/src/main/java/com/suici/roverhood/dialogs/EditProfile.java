package com.suici.roverhood.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.suici.roverhood.R;
import com.suici.roverhood.databases.FirebaseRepository;
import com.suici.roverhood.models.User;
import com.suici.roverhood.utils.Teams;
import com.suici.roverhood.utils.UserProfiles;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.InputStream;

// Edits a user's profile: optional profile photo, the username, the team (organizers and
// admins) and the optional "User Info" fields (name, locality, local center, description,
// social accounts). Opened from the profile panel by the user themself, organizers or admins.
public class EditProfile extends DialogFragment {

    public interface OnProfileSaved {
        void onSaved(String oldUsername, String newUsername);
    }

    private static final int PICK_IMAGE_REQUEST = 1;

    private final User user;
    private final boolean editingSelf;
    private final boolean canEditTeam;   // organizers and admins
    private final OnProfileSaved onSaved;

    private TextInputLayout teamLayout;
    private MaterialAutoCompleteTextView teamInput;
    private TextInputLayout roleLayout;
    private MaterialAutoCompleteTextView roleInput;
    private boolean canEditRole = false;

    private Context context;
    private FirebaseRepository firebaseRepository;

    private ImageView photoPreview;
    private Button removePhotoButton;
    private Button saveButton;
    private TextInputLayout usernameLayout;
    private TextInputEditText usernameInput;

    private Bitmap newPhoto = null;     // chosen in this dialog, not uploaded yet
    private boolean removePhoto = false;

    public EditProfile(User user, boolean editingSelf, boolean canEditTeam, OnProfileSaved onSaved) {
        this.user = user;
        this.editingSelf = editingSelf;
        this.canEditTeam = canEditTeam;
        this.onSaved = onSaved;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_edit_profile, container, false);
        context = requireContext();
        firebaseRepository = FirebaseRepository.getInstance(context);

        photoPreview = view.findViewById(R.id.photoPreview);
        removePhotoButton = view.findViewById(R.id.removePhotoButton);
        saveButton = view.findViewById(R.id.saveProfileButton);
        usernameLayout = view.findViewById(R.id.usernameLayout);
        usernameInput = view.findViewById(R.id.usernameInput);
        View usernameNote = view.findViewById(R.id.usernameNote);
        Button choosePhotoButton = view.findViewById(R.id.choosePhotoButton);

        usernameInput.setText(user.getUsername());
        usernameNote.setVisibility(editingSelf ? View.VISIBLE : View.GONE);
        showCurrentPhoto();

        teamLayout = view.findViewById(R.id.teamLayout);
        teamInput = view.findViewById(R.id.teamInput);
        if (canEditTeam) {
            teamLayout.setVisibility(View.VISIBLE);
            teamInput.setText(user.getTeam(), false);
            teamInput.setSimpleItems(Teams.allKeys(context).toArray(new String[0]));
        }

        // Role: only someone with the ADMIN role, and not for themself (so they can't lock
        // themselves out of admin by mistake)
        roleLayout = view.findViewById(R.id.roleLayout);
        roleInput = view.findViewById(R.id.roleInput);
        com.suici.roverhood.models.User editor = ((com.suici.roverhood.MainActivity) requireActivity()).getCurrentUser();
        canEditRole = editor != null && "ADMIN".equals(editor.getUserType()) && !editingSelf;
        if (canEditRole) {
            roleLayout.setVisibility(View.VISIBLE);
            roleInput.setSimpleItems(com.suici.roverhood.utils.Roles.ALL);
            roleInput.setText(user.getUserType(), false);
        }

        createInfoFields(view.findViewById(R.id.infoFields));

        Button resetCodeButton = view.findViewById(R.id.resetAccessCodeButton);
        if (canEditTeam && !editingSelf) {
            resetCodeButton.setVisibility(View.VISIBLE);
            resetCodeButton.setOnClickListener(v -> confirmResetAccessCode(resetCodeButton));
        }

        choosePhotoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        removePhotoButton.setOnClickListener(v -> {
            newPhoto = null;
            removePhoto = true;
            showPlaceholder();
            removePhotoButton.setVisibility(View.GONE);
        });

        saveButton.setOnClickListener(v -> save());
        return view;
    }

    private void confirmResetAccessCode(Button resetCodeButton) {
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Reset access code")
                .setMessage("Generate a new access code for " + user.getUsername()
                        + "? The old code will stop working.")
                .setPositiveButton("Reset", (d, which) -> {
                    resetCodeButton.setEnabled(false);
                    firebaseRepository.resetAccessCode(user.getId(), new FirebaseRepository.CodeCreationCallback() {
                        @Override
                        public void onCreated(String code) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                resetCodeButton.setEnabled(true);
                                showNewAccessCode(code);
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                resetCodeButton.setEnabled(true);
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Shown once: only the hash is stored, so the code can't be seen again later
    private void showNewAccessCode(String code) {
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("New access code")
                .setMessage(user.getUsername() + " can now log in with:\n\n" + code
                        + "\n\nCopy it now, it can't be shown again.")
                .setPositiveButton("Copy", (d, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Access code", code));
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .setCancelable(false)
                .show();
    }

    // One input per optional info field, filled with the current value
    private void createInfoFields(android.widget.LinearLayout container) {
        float density = getResources().getDisplayMetrics().density;
        for (String field : UserProfiles.INFO_FIELDS) {
            TextInputLayout layout = new TextInputLayout(context);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = (int) (8 * density);
            layout.setLayoutParams(params);
            layout.setHint(infoHint(field));

            TextInputEditText input = new TextInputEditText(layout.getContext());
            boolean isDescription = field.equals(UserProfiles.FIELD_DESCRIPTION);
            int maxLength = isDescription ? 300 : 60;
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
            if (isDescription) {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setMinLines(2);
                input.setMaxLines(6);
                layout.setCounterEnabled(true);
                layout.setCounterMaxLength(maxLength);
            } else {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | (isSocialField(field) ? 0 : android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS));
                input.setSingleLine(true);
            }
            input.setText(UserProfiles.get(user.getId(), field));
            layout.addView(input);
            container.addView(layout);
            infoInputs.put(field, input);
        }
    }

    private final java.util.Map<String, TextInputEditText> infoInputs = new java.util.LinkedHashMap<>();

    private static boolean isSocialField(String field) {
        return field.equals(UserProfiles.FIELD_INSTAGRAM) || field.equals(UserProfiles.FIELD_FACEBOOK)
                || field.equals(UserProfiles.FIELD_TIKTOK) || field.equals(UserProfiles.FIELD_DISCORD);
    }

    private static String infoHint(String field) {
        String label = UserProfiles.infoLabel(field);
        if (field.equals(UserProfiles.FIELD_REAL_NAME)) return label + " (numele real)";
        if (field.equals(UserProfiles.FIELD_DESCRIPTION)) return label + " / mesaj";
        if (field.equals(UserProfiles.FIELD_DISCORD)) return label + " (username)";
        if (isSocialField(field)) return label + " (username sau link)";
        return label;
    }

    // Info fields whose value differs from what is saved (field -> new value, "" = removed)
    private java.util.Map<String, String> changedInfo() {
        java.util.Map<String, String> changed = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, TextInputEditText> entry : infoInputs.entrySet()) {
            String value = entry.getValue().getText() != null ? entry.getValue().getText().toString().trim() : "";
            if (!value.equals(UserProfiles.get(user.getId(), entry.getKey()).trim())) {
                changed.put(entry.getKey(), value);
            }
        }
        return changed;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // No photo: the "choose picture" image, zoomed in so its margins are cut and the text is bigger
    private void showPlaceholder() {
        photoPreview.setImageResource(R.drawable.image_choose);
        photoPreview.setScaleX(1.8f);
        photoPreview.setScaleY(1.8f);
    }

    private void showPhoto() {
        photoPreview.setScaleX(1f);
        photoPreview.setScaleY(1f);
    }

    private void showCurrentPhoto() {
        String url = UserProfiles.photoUrl(user.getId());
        if (url.isEmpty()) {
            showPlaceholder();
            removePhotoButton.setVisibility(View.GONE);
        } else {
            showPhoto();
            Glide.with(this).load(url)
                    .signature(new ObjectKey(UserProfiles.photoVersion(user.getId())))
                    .centerCrop()
                    .into(photoPreview);
            removePhotoButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        try {
            Uri imageUri = data.getData();
            Bitmap raw = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
            newPhoto = rotateIfRequired(imageUri, raw);
            removePhoto = false;
            showPhoto();
            photoPreview.setImageBitmap(newPhoto);
            removePhotoButton.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e("EditProfile", "Failed to load the chosen photo", e);
            Toast.makeText(context, "Could not open this photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        String newUsername = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
        String error = UserProfiles.validateUsername(newUsername);
        if (error != null) {
            usernameLayout.setError(error);
            return;
        }
        usernameLayout.setError(null);

        String newTeam = user.getTeam();
        if (canEditTeam) {
            String typedTeam = teamInput.getText().toString().trim();
            if (!Teams.allKeys(context).contains(typedTeam)) {
                teamLayout.setError("Pick one of the existing teams");
                return;
            }
            teamLayout.setError(null);
            newTeam = typedTeam;
        }
        boolean teamChanged = !newTeam.equals(user.getTeam());
        String finalTeam = newTeam;

        String newRole = canEditRole ? roleInput.getText().toString() : user.getUserType();
        boolean roleChanged = canEditRole && !newRole.isEmpty() && !newRole.equals(user.getUserType());

        boolean usernameChanged = !newUsername.equals(user.getUsername());
        java.util.Map<String, String> infoChanges = changedInfo();
        if (!usernameChanged && !teamChanged && !roleChanged && newPhoto == null && !removePhoto && infoChanges.isEmpty()) {
            dismiss();
            return;
        }

        saveButton.setEnabled(false);
        String oldUsername = user.getUsername();

        // Order: username (it can be refused as taken), then team, role, the photo, then the info
        Runnable saveInfo = () -> {
            if (infoChanges.isEmpty()) {
                finish(oldUsername, newUsername);
                return;
            }
            firebaseRepository.updateUserInfo(user.getId(), infoChanges, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    finish(oldUsername, newUsername);
                }

                @Override
                public void onError(String errorMessage) {
                    showError(errorMessage);
                }
            });
        };

        Runnable savePhoto = () -> {
            FirebaseRepository.PostOperationCallback done = new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    saveInfo.run();
                }

                @Override
                public void onError(String errorMessage) {
                    showError(errorMessage);
                }
            };

            if (newPhoto != null) firebaseRepository.uploadProfilePhoto(user.getId(), newPhoto, done);
            else if (removePhoto) firebaseRepository.removeProfilePhoto(user.getId(), done);
            else saveInfo.run();
        };

        Runnable saveRole = () -> {
            if (!roleChanged) {
                savePhoto.run();
                return;
            }
            firebaseRepository.updateUserType(user, newRole, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    savePhoto.run();
                }

                @Override
                public void onError(String errorMessage) {
                    showError(errorMessage);
                }
            });
        };

        Runnable saveTeam = () -> {
            if (!teamChanged) {
                saveRole.run();
                return;
            }
            firebaseRepository.updateUserTeam(user, finalTeam, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    saveRole.run();
                }

                @Override
                public void onError(String errorMessage) {
                    showError(errorMessage);
                }
            });
        };

        if (usernameChanged) {
            firebaseRepository.updateUsername(user.getId(), newUsername, new FirebaseRepository.PostOperationCallback() {
                @Override
                public void onSuccess() {
                    user.setUsername(newUsername);
                    saveTeam.run();
                }

                @Override
                public void onError(String errorMessage) {
                    if (!isAdded()) return;
                    saveButton.setEnabled(true);
                    usernameLayout.setError(errorMessage);
                }
            });
        } else {
            saveTeam.run();
        }
    }

    private void finish(String oldUsername, String newUsername) {
        if (!isAdded()) return;
        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show();
        dismiss();
        if (onSaved != null) onSaved.onSaved(oldUsername, newUsername);
    }

    private void showError(String errorMessage) {
        if (!isAdded()) return;
        saveButton.setEnabled(true);
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
    }

    // Photos from the camera are often stored sideways with an EXIF rotation tag
    private Bitmap rotateIfRequired(Uri imageUri, Bitmap bitmap) {
        try (InputStream input = context.getContentResolver().openInputStream(imageUri)) {
            if (input == null) return bitmap;
            int orientation = new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                default: return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            return bitmap;
        }
    }
}
