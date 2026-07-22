package com.limelight.ui.video;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.limelight.R;

/**
 * Transparent touch layer used only while the session-local video zoom mode is enabled.
 */
public final class VideoZoomGestureOverlay extends View {
    public interface TapListener {
        void onVideoTap(float x, float y);
    }

    private VideoZoomController controller;
    private TapListener tapListener;
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private boolean modeEnabled;
    private boolean inputSuppressed;
    private boolean scaling;

    public VideoZoomGestureOverlay(Context context) {
        this(context, null);
    }

    public VideoZoomGestureOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoZoomGestureOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        setClickable(true);
        setVisibility(GONE);
        setContentDescription(context.getString(R.string.video_zoom_accessibility));
    }

    public void bind(VideoZoomController controller, TapListener tapListener) {
        this.controller = controller;
        this.tapListener = tapListener;
    }

    public boolean isModeEnabled() {
        return modeEnabled;
    }

    public void setModeEnabled(boolean enabled) {
        if (modeEnabled == enabled) {
            updateVisibility();
            return;
        }

        modeEnabled = enabled;
        scaling = false;
        if (!enabled && controller != null) {
            controller.resetUserTransform();
        }
        updateVisibility();
    }

    public void setInputSuppressed(boolean suppressed) {
        inputSuppressed = suppressed;
        if (suppressed) {
            scaling = false;
        }
        updateVisibility();
    }

    public void resetTransform() {
        if (controller != null) {
            controller.resetUserTransform();
        }
    }

    public void refreshAfterHostSizeChanged() {
        if (controller != null) {
            controller.refreshAfterHostSizeChanged();
        }
    }

    public void destroy() {
        setModeEnabled(false);
        controller = null;
        tapListener = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!modeEnabled || inputSuppressed || controller == null) {
            return false;
        }

        // Physical mice and other pointer devices continue through the existing input path.
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return false;
        }

        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            scaling = false;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateVisibility() {
        setVisibility(modeEnabled && !inputSuppressed ? VISIBLE : GONE);
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent first, MotionEvent current,
                                float distanceX, float distanceY) {
            if (scaling || scaleGestureDetector.isInProgress()) {
                return true;
            }
            controller.panBy(-distanceX, -distanceY);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            controller.resetUserTransform();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (!modeEnabled || inputSuppressed || scaling || scaleGestureDetector.isInProgress()) {
                return false;
            }
            performClick();
            if (tapListener != null) {
                tapListener.onVideoTap(event.getX(), event.getY());
            }
            return true;
        }
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            scaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            controller.scaleBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaling = false;
        }
    }
}
