package com.suici.roverhood;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageUtils {

    public static String saveImageToInternalStorage(Context context, String imageUrl, String fileName) {
        // Start the download and save process asynchronously
        DownloadImageTask task = new DownloadImageTask(context, fileName);
        task.execute(imageUrl);
        return context.getFilesDir().getAbsolutePath() + File.separator + fileName;
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, Boolean> {
        private final Context context;
        private final String fileName;

        public DownloadImageTask(Context context, String fileName) {
            this.context = context.getApplicationContext();
            this.fileName = fileName;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String imageUrl = params[0];
            return downloadImage(imageUrl, context, fileName);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Log.d("ImageUtils", "Image downloaded and saved successfully.");
                // After saving, check for corruption
                // OMG that java.net.SocketException was a pain, solved with reopening the file after write and check for corruption again
                checkAndHandleCorruptedImage(context, fileName);
            } else {
                Log.e("ImageUtils", "Failed to download or save the image.");
            }
        }

        private boolean downloadImage(String imageUrl, Context context, String fileName) {
            // Check if the file already exists
            File file = new File(context.getFilesDir(), fileName);
            if (file.exists()) {
                Log.d("ImageUtils", "File already exists, skipping download: " + fileName);
                return true;
            }

            try {
                // Open the URL and try to download the image
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                // Check if the Bitmap is valid
                if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
                    Log.e("ImageUtils", "Failed to decode image or image is corrupted");
                    return false;  // If the Bitmap is invalid, return false
                }

                // Save the image to internal storage
                FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                return true;
            } catch (IOException e) {
                Log.e("ImageUtils", "Error downloading or saving image", e);
                return false;
            }
        }

        private void checkAndHandleCorruptedImage(Context context, String fileName) {
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

        private boolean isImageCorrupted(Bitmap bitmap) {
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

        private boolean isBlackPixel(int pixel) {
            int alpha = (pixel >> 24) & 0xff;
            int red = (pixel >> 16) & 0xff;
            int green = (pixel >> 8) & 0xff;
            int blue = (pixel) & 0xff;

            // Check for fully transparent pixel (Alpha = 0) or pure black pixel (RGB: 0, 0, 0)
            return (alpha != 0) && (red == 0 && green == 0 && blue == 0);
        }
    }
}