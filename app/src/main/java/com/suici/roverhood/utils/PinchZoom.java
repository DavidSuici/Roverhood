package com.suici.roverhood.utils;

import android.graphics.Color;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

// Pinch to zoom on an image (post images, profile photos), like in most social apps:
// while two fingers pinch, a copy of the image is zoomed / moved over the whole screen;
// when the fingers are lifted it animates back into place. Taps still work as before.
public class PinchZoom implements View.OnTouchListener {

    private static final float MAX_SCALE = 5f;

    private final ImageView source;
    private final ScaleGestureDetector detector;

    private FrameLayout root;
    private ImageView overlay;
    private View dim;
    private float scale = 1f;
    private float startFocusX, startFocusY;

    public static void attach(ImageView image) {
        if (image == null) return;
        image.setOnTouchListener(new PinchZoom(image));
    }

    private PinchZoom(ImageView source) {
        this.source = source;
        this.detector = new ScaleGestureDetector(source.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                return start(d.getFocusX(), d.getFocusY());
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                if (overlay == null) return false;
                scale = Math.max(1f, Math.min(MAX_SCALE, scale * d.getScaleFactor()));
                overlay.setScaleX(scale);
                overlay.setScaleY(scale);
                overlay.setTranslationX(d.getFocusX() - startFocusX);
                overlay.setTranslationY(d.getFocusY() - startFocusY);
                dim.setAlpha(Math.min(0.6f, (scale - 1f) * 0.6f));
                return true;
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // A second finger: don't let the list scroll or the dialog react, it's a pinch
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            ViewParent parent = v.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }
        detector.onTouchEvent(event);

        if (overlay != null) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) end();
            return true;   // while zooming, the image doesn't also get a click
        }
        return false;
    }

    private boolean start(float focusX, float focusY) {
        if (overlay != null || source.getDrawable() == null) return false;
        View rootView = source.getRootView();
        if (!(rootView instanceof FrameLayout)) return false;
        root = (FrameLayout) rootView;

        // Cancel the tap that started with the first finger
        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        source.onTouchEvent(cancel);
        cancel.recycle();

        int[] sourcePos = new int[2];
        int[] rootPos = new int[2];
        source.getLocationInWindow(sourcePos);
        root.getLocationInWindow(rootPos);

        dim = new View(source.getContext());
        dim.setBackgroundColor(Color.BLACK);
        dim.setAlpha(0f);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new ImageView(source.getContext());
        overlay.setImageDrawable(source.getDrawable());
        overlay.setScaleType(source.getScaleType());
        overlay.setBackground(source.getBackground() != null ? source.getBackground().getConstantState() != null
                ? source.getBackground().getConstantState().newDrawable() : null : null);
        overlay.setPadding(source.getPaddingLeft(), source.getPaddingTop(), source.getPaddingRight(), source.getPaddingBottom());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(source.getWidth(), source.getHeight());
        params.leftMargin = sourcePos[0] - rootPos[0];
        params.topMargin = sourcePos[1] - rootPos[1];
        root.addView(overlay, params);
        overlay.setPivotX(focusX);
        overlay.setPivotY(focusY);

        source.setAlpha(0f);
        scale = 1f;
        startFocusX = focusX;
        startFocusY = focusY;
        return true;
    }

    private void end() {
        ImageView zoomed = overlay;
        View shade = dim;
        FrameLayout host = root;
        overlay = null;
        dim = null;
        if (zoomed == null) return;

        shade.animate().alpha(0f).setDuration(200).start();
        zoomed.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(200)
                .withEndAction(() -> {
                    source.setAlpha(1f);
                    host.removeView(zoomed);
                    host.removeView(shade);
                }).start();
    }
}
