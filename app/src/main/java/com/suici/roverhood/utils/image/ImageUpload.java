package com.suici.roverhood.utils.image;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.ProgressBar;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class ImageUpload {

    public interface imageUploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    // Uploads a bitmap image to Firebase Storage, resizing if exceeds 1920px
    // and compressing the image to ensure it's under 1MB.
    // Shows and updates a progress bar during upload.
    public static void uploadImageToFirebase(Context context, Bitmap originalBitmap, String fileNameHint, imageUploadCallback callback) {
        Bitmap resizedBitmap = resizeIfTooLarge(originalBitmap, 1920);

        // Compress image until its size is less than 1MB
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;

        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        while (baos.toByteArray().length > 1024 * 1024 && quality > 10) {
            baos.reset();
            quality -= 5;
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }
        byte[] data = baos.toByteArray();

        // Save on Firebase Storage
        String uniqueFileName = (fileNameHint != null && !fileNameHint.isEmpty() ? fileNameHint : "image")
                + "_" + UUID.randomUUID().toString() + ".jpg";

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images/" + uniqueFileName);

        UploadTask uploadTask = storageRef.putBytes(data);

        // Logic for Upload LoadingBar
        MainActivity activity = (MainActivity) context;
        if (activity != null) {
            ProgressBar.resetProgressBar(activity.getUploadProgressBar());
            activity.getFloatingButton().setEnabled(false);

            uploadTask.addOnProgressListener(taskSnapshot -> {
                long bytesTransferred = taskSnapshot.getBytesTransferred();
                long totalBytes = taskSnapshot.getTotalByteCount();
                int progress = (int) ((100.0 * bytesTransferred) / totalBytes);

                activity.runOnUiThread(() -> {
                    ProgressBar.updateProgressBar(activity.getUploadProgressBar(), progress, 100);
                });
            });
        }

        uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    if (activity != null)
                        activity.getFloatingButton().setEnabled(true);
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private static Bitmap resizeIfTooLarge(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }

        float ratio = (float) width / height;
        int newWidth, newHeight;

        if (ratio > 1) {
            newWidth = maxSize;
            newHeight = (int) (maxSize / ratio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (maxSize * ratio);
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
}