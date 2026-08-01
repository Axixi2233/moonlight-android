package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.limelight.utils.UiHelper;

public class GlowingBorderLayout extends FrameLayout {
    private static final int ACCENT_START = 0xFF8252FF;
    private static final int ACCENT_END = 0xFF596AFF;
    private static final int SURFACE_START = 0xFF191732;
    private static final int SURFACE_END = 0xFF090B1A;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF borderBounds = new RectF();
    private final float normalStrokeWidth = UiHelper.dpToPx(getContext(), 1);
    private final float focusedStrokeWidth = UiHelper.dpToPx(getContext(), 3);
    private final float cornerRadius = UiHelper.dpToPx(getContext(), 21);

    private LinearGradient surfaceGradient;

    public GlowingBorderLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = focusedStrokeWidth / 2f;
        borderBounds.set(inset, inset, w - inset, h - inset);
        surfaceGradient = new LinearGradient(
                0f,
                0f,
                w,
                h,
                blendColor(SURFACE_START, ACCENT_START, 0.22f),
                blendColor(SURFACE_END, ACCENT_END, 0.10f),
                Shader.TileMode.CLAMP);
    }

    @Override
    public void setSelected(boolean selected) {
        boolean changed = selected != isSelected();
        super.setSelected(selected);
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(surfaceGradient);
        canvas.drawRoundRect(borderBounds, cornerRadius, cornerRadius, paint);

        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        paint.setStyle(Paint.Style.STROKE);
        if (isPressed() || hasNavigationFocus()) {
            paint.setStrokeWidth(focusedStrokeWidth);
            paint.setShader(null);
            paint.setColor(0xFFF7F5FF);
        }
        else {
            paint.setStrokeWidth(normalStrokeWidth);
            paint.setShader(null);
            paint.setColor(0x24FFFFFF);
        }
        canvas.drawRoundRect(borderBounds, cornerRadius, cornerRadius, paint);
    }

    private boolean hasNavigationFocus() {
        if (isFocused()) {
            return true;
        }

        ViewParent parent = getParent();
        return isSelected() && parent instanceof View && ((View) parent).hasFocus();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    private static int blendColor(int base, int overlay, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * inverse + Color.red(overlay) * amount),
                Math.round(Color.green(base) * inverse + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount));
    }
}
