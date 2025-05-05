package com.suici.roverhood;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class ImageUtils {

    static int loadedImageCount = 0;
    static int totalImageCount = 0;

    public static int getLoadedImageCount() { return loadedImageCount; }
    public static void setLoadedImageCount(int nr) {
        loadedImageCount = nr;
    }
    public static int getTotalImageCount() { return totalImageCount; }
    public static void setTotalImageCount(int nr) { totalImageCount = nr; }

    public static void incrementProgressBar() {
        loadedImageCount++;
        if (MainActivity.instance != null) {
            MainActivity activity = MainActivity.instance;
            activity.runOnUiThread(() -> {
                activity.updateProgressBar(loadedImageCount, totalImageCount);
            });
        }
    }

    public static void incrementProgressBarMax() {
        totalImageCount++;
        if (MainActivity.instance != null) {
            MainActivity activity = MainActivity.instance;
            activity.runOnUiThread(() -> {
                activity.updateProgressBar(loadedImageCount, totalImageCount);
            });
        }
    }

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    public static void uploadImageToFirebase(Bitmap bitmap, String fileNameHint, UploadCallback callback) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();

        String uniqueFileName = (fileNameHint != null && !fileNameHint.isEmpty() ? fileNameHint : "image")
                + "_" + UUID.randomUUID().toString() + ".png";

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images/" + uniqueFileName);

        UploadTask uploadTask = storageRef.putBytes(data);

        uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    // Get download URL with token
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public interface ImageSaveCallback {
        void onSuccess(String imagePath);
        void onFailure(Exception e);
    }

    public static void saveImageToInternalStorage(Context context, String imageUrl, String fileName, ImageSaveCallback callback) {
        new Thread(() -> {
            try {
                File file = new File(context.getFilesDir(), fileName);
                if (file.exists()) {
                    Log.d("ImageUtils", "File already exists, skipping download: " + fileName);
                    callback.onSuccess(file.getAbsolutePath());
                    return;
                }

                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
                    callback.onFailure(new IOException("Corrupted image"));
                    return;
                }

                FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                checkAndHandleCorruptedImage(context, fileName);

                callback.onSuccess(file.getAbsolutePath());
            } catch (Exception e) {
                Log.e("ImageUtils", "Failed to save image", e);
                callback.onFailure(e);
            }
        }).start();
    }

    private static void checkAndHandleCorruptedImage(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            Log.e("ImageUtils", "Image is corrupted after saving, deleting file: " + fileName);
            boolean deleted = file.delete();
            if (deleted) {
                Log.d("ImageUtils", "Corrupted file deleted: " + fileName);
            } else {
                Log.e("ImageUtils", "Failed to delete the corrupted file: " + fileName);
            }
        } else {
            // Optionally, run additional checks like checking for black pixels
            if (isImageCorrupted(bitmap)) {
                Log.e("ImageUtils", "Corrupted image detected, deleting file: " + fileName);
                boolean deleted = file.delete();
                if (deleted) {
                    Log.d("ImageUtils", "Corrupted file deleted: " + fileName);
                } else {
                    Log.e("ImageUtils", "Failed to delete the corrupted file: " + fileName);
                }
            }
        }
    }

    private static boolean isImageCorrupted(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int blackPixelCount = 0;

        // Calculate the threshold for 10% of the total pixels (based on height)
        // Arbitrary number, I chose 10% because if its less, the image can stay
        int threshold = (int) (height * 0.1);

        boolean foundBlackPixel = false;

        // Iterate over the pixels from the bottom row upwards (first 10% of the image)
        for (int y = height - 1; y >= height - threshold; y--) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);

                if (isBlackPixel(pixel)) {
                    blackPixelCount++;
                    foundBlackPixel = true;
                } else if (foundBlackPixel) {
                    // Stop counting if black pixels are no longer continuous
                    break;
                }
            }

            // If black pixels are counted for the first 10% of the image, and the count exceeds threshold, it's corrupted
            if (blackPixelCount >= threshold) {
                Log.d("ImageUtils", "Image is corrupted: 10% or more pixels are black and continuous.");
                return true;
            }
        }

        return false;  // Image is not corrupted
    }

    private static boolean isBlackPixel(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;

        // Check for fully transparent pixel (Alpha = 0) or pure black pixel (RGB: 0, 0, 0)
        return (alpha != 0) && (red == 0 && green == 0 && blue == 0);
    }
}