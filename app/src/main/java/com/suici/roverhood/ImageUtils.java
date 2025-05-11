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
import java.util.function.IntPredicate;

public class ImageUtils {

    //imparte in download upload si loadingBar

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

    public static void uploadImageToFirebase(Bitmap originalBitmap, String fileNameHint, UploadCallback callback) {
        // Resize if needed
        Bitmap resizedBitmap = resizeIfTooLarge(originalBitmap, 1920);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;

        // Compress to JPEG (PNG ignores quality)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        while (baos.toByteArray().length > 1024 * 1024 && quality > 10) {
            baos.reset(); // clear previous data
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

        uploadTask
                .addOnSuccessListener(taskSnapshot -> {
                    // Get download URL with token
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

    public interface ImageSaveCallback {
        void onSuccess(String imagePath);
        void onFailure(Exception e);
    }

    public static void saveImageToInternalStorage(Context context, String imageUrl, String fileName, ImageSaveCallback callback) {
        new Thread(() -> {
            try {
                String resolvedFileName = fileName.toLowerCase().endsWith(".jpg") ? fileName : fileName + ".jpg";

                File file = new File(context.getFilesDir(), resolvedFileName);
                if (file.exists()) {
                    Log.d("ImageUtils", "File already exists, skipping download: " + resolvedFileName);
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

                FileOutputStream fos = context.openFileOutput(resolvedFileName, Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();

                checkAndHandleCorruptedImage(context, resolvedFileName);

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
            // Optionally, run additional checks like checking for white pixels
            if (isImageCorrupted(bitmap, ImageUtils::isWhitePixel)
                    || isImageCorrupted(bitmap, ImageUtils::isBlackPixel)) {
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

    private static boolean isImageCorrupted(Bitmap bitmap, IntPredicate pixelCheck) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int matchPixelCount = 0;

        int threshold = (int) (height * 0.15);  // 15% of height

        for (int y = height - 1; y >= height - threshold; y--) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);

                if (pixelCheck.test(pixel)) {
                    matchPixelCount++;
                } else {
                    return false;
                }
            }
        }

        return matchPixelCount >= threshold;
    }

    private static boolean isBlackPixel(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        int threshold = 10;
        return red < threshold && green < threshold && blue < threshold;
    }

    private static boolean isWhitePixel(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        int threshold = 245;
        return red > threshold && green > threshold && blue > threshold;
    }
}