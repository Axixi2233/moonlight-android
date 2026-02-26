package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.limelight.R;
import com.limelight.utils.UiHelper;

public class GlowingBorderLayout extends FrameLayout {
    private Paint mPaint;
    private SweepGradient mSweepGradient;
    private Matrix mMatrix;
    private float mRotateDegrees = 0f;
    private RectF mRectF = new RectF();
    private ValueAnimator mAnimator;
    private float mStrokeWidth = 15f; // 稍微加粗一点点，视觉效果更好
    private float mCornerRadius = UiHelper.dpToPx(getContext(),21);

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

        mMatrix = new Matrix();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mAnimator = ValueAnimator.ofFloat(0f, 360f);
        mAnimator.setDuration(4500);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(animation -> {
            mRotateDegrees = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        // 当焦点改变时，同步更新选中状态以触发表亮
        setSelected(gainFocus);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 留出边框位置
        mRectF.set(mStrokeWidth / 2f, mStrokeWidth / 2f, w - mStrokeWidth / 2f, h - mStrokeWidth / 2f);

        int[] colors = {0x008899FF, 0xFF8899FF, 0xFFFFFFFF, 0xFF8899FF, 0x008899FF};
        float[] positions = {0f, 0.47f, 0.5f, 0.53f, 1f};
        mSweepGradient = new SweepGradient(w / 2f, h / 2f, colors, positions);
    }

    @Override
    public void setSelected(boolean selected) {
        boolean changed = selected != isSelected();
        super.setSelected(selected);
        if (changed) {
            if (selected) {
                if (!mAnimator.isRunning()) mAnimator.start();
            } else {
                mAnimator.cancel();
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // onDraw 只负责画最底层的背景
        if (getWidth() == 0 || getHeight() == 0) return;

        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(getResources().getColor(R.color.pc_app_item_bg_color));
        canvas.drawRoundRect(mRectF, mCornerRadius, mCornerRadius, mPaint);

        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 1. 先让系统绘制子 View
        super.dispatchDraw(canvas);

        // 2. 绘制跑马灯边框 (确保画在子 View 的上面)
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        if (isSelected() || isFocused() || isPressed()) {
            mMatrix.setRotate(mRotateDegrees, getWidth() / 2f, getHeight() / 2f);
            mSweepGradient.setLocalMatrix(mMatrix);
            mPaint.setShader(mSweepGradient);
            canvas.drawRoundRect(mRectF, mCornerRadius, mCornerRadius, mPaint);
        } else {
            // 未聚焦时的普通边框
            mPaint.setStrokeWidth(mStrokeWidth*0.7f);
            mPaint.setShader(null);
            mPaint.setColor(getResources().getColor(R.color.pc_app_item_stoke_color));
            canvas.drawRoundRect(mRectF, mCornerRadius, mCornerRadius, mPaint);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 不拦截子 View 的任何触摸，也不拦截自己的触摸
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 关键：返回 false 告诉系统：“我没处理这个点击，请交给底层的 GridView 处理”
        return false;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        // 检查现在是否处于 选中(Selected)、聚焦(Focused) 或 按下(Pressed) 状态
        boolean shouldRun = isSelected() || isFocused() || isPressed();
        if (shouldRun) {
            if (!mAnimator.isRunning()) {
                mAnimator.start();
            }
        } else {
            // 如果没有被选中，停止动画并重绘（去掉边框）
            if (mAnimator.isRunning()) {
                mAnimator.cancel();
            }
        }
        // 强制触发 dispatchDraw 重新绘制
        invalidate();
    }
}