package com.suici.roverhood.utils.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.suici.roverhood.MainActivity;
import com.suici.roverhood.presentation.ProgressBar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.function.IntPredicate;

public class ImageDownload {


    // LoadingBar logic

    private static int loadedImageCount = 0;
    private static int totalImageCount = 0;

    public static int getLoadedImageCount() { return loadedImageCount; }
    public static void setLoadedImageCount(int nr) { loadedImageCount = nr; }
    public static int getTotalImageCount() { return totalImageCount; }
    public static void setTotalImageCount(int nr) { totalImageCount = nr; }

    public static void incrementProgressBar(Context context) {
        loadedImageCount++;
        MainActivity activity = (MainActivity) context;
        if (activity != null) {
            activity.runOnUiThread(() -> {
                ProgressBar.updateProgressBar(activity.getDownloadProgressBar(), loadedImageCount, totalImageCount);
            });
        }
    }

    public static void incrementProgressBarMax(Context context) {
        totalImageCount++;
        MainActivity activity = (MainActivity) context;
        if (activity != null) {
            activity.runOnUiThread(() -> {
                ProgressBar.updateProgressBar(activity.getDownloadProgressBar(), loadedImageCount, totalImageCount);
            });
        }
    }


    // Download logic

    public interface ImageSaveCallback {
        void onSuccess(String imagePath);
        void onFailure(Exception e);
    }

    // Downloads an image from URL and saves it to the app's internal storage.
    // If the file already exists, it returns the path directly, and if the
    // image is corrupted, it will be deleted.
    public static void saveImageToInternalStorage(Context context, String imageUrl, String fileName, ImageSaveCallback callback) {
        new Thread(() -> {
            try {
                String resolvedFileName = fileName.toLowerCase().endsWith(".jpg") ? fileName : fileName + ".jpg";

                File file = new File(context.getFilesDir(), resolvedFileName);
                if (file.exists()) {
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

    // Checking an image after download for corruption signs
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
            // Running checks for continuous white or black pixels
            if (isImageCorrupted(bitmap, ImageDownload::isWhitePixel)
                    || isImageCorrupted(bitmap, ImageDownload::isBlackPixel)) {
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

    // Sometimes after download, image will be part white / black if it didn't load properly
    // Checking if the bottom 15% of image is continuous pixels
    // Also used when checking the images about to be uploaded, hence the public access.
    public static boolean isImageCorrupted(Bitmap bitmap, IntPredicate pixelCheck) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int matchPixelCount = 0;

        int threshold = (int) (height * 0.15);

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

    public static boolean isBlackPixel(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        int threshold = 10;
        return red < threshold && green < threshold && blue < threshold;
    }

    public static boolean isWhitePixel(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        int threshold = 245;
        return red > threshold && green > threshold && blue > threshold;
    }

    public static boolean isTransparentPixel(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int threshold = 10;
        return alpha < threshold;
    }
}