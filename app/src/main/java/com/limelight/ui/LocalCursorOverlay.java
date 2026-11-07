package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Client-side mouse cursor.
 *
 * Sunshine only re-encodes a frame when the host desktop changes. When the
 * cursor is moved by client-driven input the desktop is otherwise static, so
 * the host-composited cursor often never reaches the stream. This overlay owns
 * that job locally: it is advanced by the very same relative deltas that are
 * forwarded to the host, so the user always sees where the pointer is.
 */
public class LocalCursorOverlay extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float cursorX = -1f;
    private float cursorY = -1f;
    private float deltaScale = 1f;
    private final float cursorHeightPx;
    private final float density;

    public LocalCursorOverlay(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        // ~14dp: close to the host pointer once the stream is scaled down onto the tablet
        cursorHeightPx = 14f * density;

        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);

        stroke.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, 0.9f * density));

        setWillNotDraw(false);
    }

    /** Absolute host pointer position, expressed in reference-stream coordinates. */
    public void setReferencePosition(float x, float y, float referenceWidth, float referenceHeight,
                                     float videoLeft, float videoTop,
                                     float videoWidth, float videoHeight) {
        if (referenceWidth <= 0f || referenceHeight <= 0f || videoWidth <= 0f || videoHeight <= 0f) {
            return;
        }
        // The host maps the packet onto its whole screen, so the local cursor has to
        // land at the same fraction of the video rect (not of the letterboxed view).
        cursorX = videoLeft + (x / referenceWidth) * videoWidth;
        cursorY = videoTop + (y / referenceHeight) * videoHeight;
        clamp();
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    /** Deltas are in stream pixels; convert them into view pixels. */
    public void setDeltaScale(float scale) {
        if (scale > 0f && !Float.isNaN(scale)) {
            deltaScale = scale;
        }
    }

    public void moveBy(float dx, float dy) {
        if (cursorX < 0f) {
            cursorX = getWidth() / 2f;
            cursorY = getHeight() / 2f;
        }
        cursorX += dx * deltaScale;
        cursorY += dy * deltaScale;
        clamp();
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    public void reset() {
        cursorX = -1f;
        cursorY = -1f;
        invalidate();
    }

    private void clamp() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float w = cursorHeightPx * 0.7f;
        float h = cursorHeightPx;
        if (cursorX < 0f) cursorX = 0f;
        if (cursorY < 0f) cursorY = 0f;
        if (cursorX > getWidth() - w) cursorX = getWidth() - w;
        if (cursorY > getHeight() - h) cursorY = getHeight() - h;
    }

    private void buildPath() {
        float s = cursorHeightPx;
        path.reset();
        path.moveTo(0f, 0f);
        path.lineTo(0f, s);
        path.lineTo(s * 0.27f, s * 0.74f);
        path.lineTo(s * 0.44f, s * 1.02f);
        path.lineTo(s * 0.58f, s * 0.96f);
        path.lineTo(s * 0.42f, s * 0.69f);
        path.lineTo(s * 0.72f, s * 0.69f);
        path.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cursorX < 0f || cursorY < 0f) {
            return;
        }
        buildPath();
        canvas.save();
        canvas.translate(cursorX, cursorY);
        canvas.drawPath(path, fill);
        canvas.drawPath(path, stroke);
        canvas.restore();
    }
}
