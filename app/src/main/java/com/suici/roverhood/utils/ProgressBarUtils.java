package com.suici.roverhood.utils;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.View;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class ProgressBarUtils {

    public static void updateProgressBar(LinearProgressIndicator progressBar, int currentProgress, int totalProgress) {
        if (progressBar != null) {
            progressBar.setMax(totalProgress);

            ValueAnimator progressAnimator = ValueAnimator.ofInt(progressBar.getProgress(), currentProgress);
            progressAnimator.setDuration(500);
            progressAnimator.addUpdateListener(animation ->
                    progressBar.setProgressCompat((Integer) animation.getAnimatedValue(), true)
            );

            progressAnimator.addListener(new android.animation.Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {
                    if (progressBar.getVisibility() == View.GONE && currentProgress < totalProgress) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (currentProgress >= totalProgress) {
                        new Handler().postDelayed(() -> {
                            fadeOutAndReset(progressBar);
                        }, 500);
                    }
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {}
                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {}
            });

            progressAnimator.start();
        }
    }

    private static void fadeOutAndReset(LinearProgressIndicator progressBar) {
        ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
        fadeOutAnimator.setDuration(500);
        fadeOutAnimator.addUpdateListener(animation -> {
            float alpha = (Float) animation.getAnimatedValue();
            progressBar.setAlpha(alpha);
        });

        fadeOutAnimator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {}

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                resetProgressBar(progressBar);
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {}

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {}
        });

        fadeOutAnimator.start();
    }

    public static void resetProgressBar(LinearProgressIndicator progressBar) {
        progressBar.setVisibility(View.GONE);
        progressBar.setProgress(0, false);
        progressBar.setAlpha(1f);
    }
}
