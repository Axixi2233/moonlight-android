package com.limelight.ui.video;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

/**
 * Owns the session-local transform applied to the active video surfaces. The transform is split
 * into a user-controlled zoom/pan component and an external offset used by presentation features
 * such as virtual-mouse edge avoidance.
 */
public final class VideoZoomController {
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 10.0f;
    private static final float SCALE_EPSILON = 0.001f;

    private final View coordinateView;
    private final View systemVideoView;
    private final View processedVideoView;
    private final RectF baseVideoRect = new RectF();
    private final int[] coordinateLocation = new int[2];
    private final int[] videoLocation = new int[2];

    private float scale = MIN_SCALE;
    private float panX;
    private float panY;
    private float externalOffsetX;
    private float externalOffsetY;

    public VideoZoomController(View coordinateView, View systemVideoView, View processedVideoView) {
        this.coordinateView = coordinateView;
        this.systemVideoView = systemVideoView;
        this.processedVideoView = processedVideoView;
        applyTransform();
    }

    public float getScale() {
        return scale;
    }

    public boolean isAtRest() {
        return Math.abs(scale - MIN_SCALE) < SCALE_EPSILON
                && Math.abs(panX) < 0.5f
                && Math.abs(panY) < 0.5f;
    }

    public void scaleBy(float scaleFactor, float focusX, float focusY) {
        if (Float.isNaN(scaleFactor) || Float.isInfinite(scaleFactor)
                || scaleFactor <= 0f || !updateBaseVideoRect()) {
            return;
        }

        float oldScale = scale;
        float newScale = clamp(oldScale * scaleFactor, MIN_SCALE, MAX_SCALE);
        if (Math.abs(newScale - oldScale) < SCALE_EPSILON) {
            return;
        }

        // Preserve the content point currently under the gesture focus.
        float contentX = (focusX - baseVideoRect.left - panX - externalOffsetX) / oldScale;
        float contentY = (focusY - baseVideoRect.top - panY - externalOffsetY) / oldScale;
        panX = focusX - baseVideoRect.left - externalOffsetX - contentX * newScale;
        panY = focusY - baseVideoRect.top - externalOffsetY - contentY * newScale;
        scale = newScale;

        clampPanToVideoBounds();
        applyTransform();
    }

    public void panBy(float deltaX, float deltaY) {
        if (scale <= MIN_SCALE + SCALE_EPSILON || !updateBaseVideoRect()) {
            return;
        }

        panX += deltaX;
        panY += deltaY;
        clampPanToVideoBounds();
        applyTransform();
    }

    public void resetUserTransform() {
        scale = MIN_SCALE;
        panX = 0f;
        panY = 0f;
        applyTransform();
    }

    public void setExternalOffset(float offsetX, float offsetY) {
        if (Math.abs(externalOffsetX - offsetX) < 0.5f
                && Math.abs(externalOffsetY - offsetY) < 0.5f) {
            return;
        }

        externalOffsetX = offsetX;
        externalOffsetY = offsetY;
        applyTransform();
    }

    public void refreshAfterHostSizeChanged() {
        if (!updateBaseVideoRect()) {
            return;
        }
        clampPanToVideoBounds();
        applyTransform();
    }

    public void getBaseVideoRect(RectF outRect) {
        if (!updateBaseVideoRect()) {
            outRect.set(0f, 0f, Math.max(coordinateView.getWidth(), 1),
                    Math.max(coordinateView.getHeight(), 1));
            return;
        }
        outRect.set(baseVideoRect);
    }

    /**
     * Maps a point in the gesture overlay coordinate space back to the untransformed video.
     * Returns false when the point is outside the currently visible transformed video content.
     */
    public boolean mapPointToNormalizedVideo(float x, float y, PointF outPoint) {
        if (!updateBaseVideoRect() || baseVideoRect.width() <= 0f || baseVideoRect.height() <= 0f) {
            return false;
        }

        float localX = (x - baseVideoRect.left - panX - externalOffsetX) / scale;
        float localY = (y - baseVideoRect.top - panY - externalOffsetY) / scale;
        if (localX < 0f || localY < 0f
                || localX > baseVideoRect.width() || localY > baseVideoRect.height()) {
            return false;
        }

        outPoint.set(clamp(localX / baseVideoRect.width(), 0f, 1f),
                clamp(localY / baseVideoRect.height(), 0f, 1f));
        return true;
    }

    public void destroy() {
        scale = MIN_SCALE;
        panX = 0f;
        panY = 0f;
        externalOffsetX = 0f;
        externalOffsetY = 0f;
        applyTransform();
    }

    private boolean updateBaseVideoRect() {
        View videoView = getActiveVideoView();
        if (videoView == null || coordinateView.getWidth() <= 0 || coordinateView.getHeight() <= 0
                || videoView.getWidth() <= 0 || videoView.getHeight() <= 0) {
            return false;
        }

        ViewParent videoParent = videoView.getParent();
        if (videoParent == coordinateView) {
            float left = videoView.getLeft();
            float top = videoView.getTop();
            baseVideoRect.set(left, top, left + videoView.getWidth(), top + videoView.getHeight());
            return true;
        }

        coordinateView.getLocationInWindow(coordinateLocation);
        videoView.getLocationInWindow(videoLocation);
        float left = videoLocation[0] - coordinateLocation[0] - panX - externalOffsetX;
        float top = videoLocation[1] - coordinateLocation[1] - panY - externalOffsetY;
        baseVideoRect.set(left, top, left + videoView.getWidth(), top + videoView.getHeight());
        return true;
    }

    private void clampPanToVideoBounds() {
        if (scale <= MIN_SCALE + SCALE_EPSILON) {
            scale = MIN_SCALE;
            panX = 0f;
            panY = 0f;
            return;
        }

        float minimumPanX = baseVideoRect.width() * (1f - scale);
        float minimumPanY = baseVideoRect.height() * (1f - scale);
        panX = clamp(panX, minimumPanX, 0f);
        panY = clamp(panY, minimumPanY, 0f);
    }

    private View getActiveVideoView() {
        return processedVideoView != null ? processedVideoView : systemVideoView;
    }

    private void applyTransform() {
        applyTransform(systemVideoView);
        if (processedVideoView != null && processedVideoView != systemVideoView) {
            applyTransform(processedVideoView);
        }
    }

    private void applyTransform(View view) {
        if (view == null) {
            return;
        }
        view.setPivotX(0f);
        view.setPivotY(0f);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationX(panX + externalOffsetX);
        view.setTranslationY(panY + externalOffsetY);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
