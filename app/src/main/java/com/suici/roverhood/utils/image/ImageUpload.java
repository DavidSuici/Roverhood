package com.suici.roverhood.utils.image;

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

    public static void uploadImageToFirebase(Bitmap originalBitmap, String fileNameHint, imageUploadCallback callback) {
        Bitmap resizedBitmap = resizeIfTooLarge(originalBitmap, 1920);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;

        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        while (baos.toByteArray().length > 1024 * 1024 && quality > 10) {
            baos.reset();
            quality -= 5;
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }
        byte[] data = baos.toByteArray();

        String uniqueFileName = (fileNameHint != null && !fileNameHint.isEmpty() ? fileNameHint : "image")
                + "_" + UUID.randomUUID().toString() + ".jpg";

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images/" + uniqueFileName);

        UploadTask uploadTask = storageRef.putBytes(data);

        // Logic for Upload LoadingBar
        MainActivity activity = MainActivity.instance;
        if (activity != null)
            ProgressBar.resetProgressBar(activity.getUploadProgressBar());
        uploadTask.addOnProgressListener(taskSnapshot -> {
            long bytesTransferred = taskSnapshot.getBytesTransferred();
            long totalBytes = taskSnapshot.getTotalByteCount();
            int progress = (int) ((100.0 * bytesTransferred) / totalBytes);

            activity.runOnUiThread(() -> {
                ProgressBar.updateProgressBar(activity.getUploadProgressBar(), progress, 100);
            });
        });

        if (activity != null)
            activity.getFloatingButton().setEnabled(false);

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
            return bitmap; // no need to resize
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