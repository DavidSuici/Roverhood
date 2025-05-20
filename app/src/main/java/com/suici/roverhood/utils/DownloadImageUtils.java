package com.suici.roverhood.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.suici.roverhood.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.function.IntPredicate;

public class DownloadImageUtils {

    // Logic for Download LoadingBar
    static int loadedImageCount = 0;
    static int totalImageCount = 0;

    public static int getLoadedImageCount() { return loadedImageCount; }
    public static void setLoadedImageCount(int nr) { loadedImageCount = nr; }
    public static int getTotalImageCount() { return totalImageCount; }
    public static void setTotalImageCount(int nr) { totalImageCount = nr; }

    public static void incrementProgressBar() {
        loadedImageCount++;
        if (MainActivity.instance != null) {
            MainActivity activity = MainActivity.instance;
            activity.runOnUiThread(() -> {
                ProgressBarUtils.updateProgressBar(activity.getDownloadProgressBar(), loadedImageCount, totalImageCount);
            });
        }
    }

    public static void incrementProgressBarMax() {
        totalImageCount++;
        if (MainActivity.instance != null) {
            MainActivity activity = MainActivity.instance;
            activity.runOnUiThread(() -> {
                ProgressBarUtils.updateProgressBar(activity.getDownloadProgressBar(), loadedImageCount, totalImageCount);
            });
        }
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
            if (isImageCorrupted(bitmap, DownloadImageUtils::isWhitePixel)
                    || isImageCorrupted(bitmap, DownloadImageUtils::isBlackPixel)) {
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