package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class AxiCollisionLoadingView extends View {

    private static final int COLOR_BODY = Color.parseColor("#766DB6");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_YELLOW = Color.parseColor("#FCCC5D");
    private static final int COLOR_SHADOW = Color.parseColor("#524381");

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yellowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private ValueAnimator animator;
    private float progress; // 0..1

    public AxiCollisionLoadingView(Context context) {
        super(context);
        init();
    }

    public AxiCollisionLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AxiCollisionLoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(COLOR_BODY);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(COLOR_WHITE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(COLOR_WHITE);

        yellowPaint.setStyle(Paint.Style.FILL);
        yellowPaint.setColor(COLOR_YELLOW);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(COLOR_SHADOW);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = dpInt(72);
        int desiredH = dpInt(48);
        int measuredW = resolveSize(desiredW, widthMeasureSpec);
        int measuredH = resolveSize(desiredH, heightMeasureSpec);
        setMeasuredDimension(measuredW, measuredH);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            start();
        } else {
            stop();
        }
    }

    public void start() {
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(920);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        float centerX = w / 2f;
        float centerY = h / 2f + dp(1.8f);

        Motion left = buildMotion(progress, true, centerX);
        Motion right = buildMotion(progress, false, centerX);

        drawShadow(canvas, left.cx, centerY + dp(10f), left.shadowScale, 0.18f);
        drawShadow(canvas, right.cx, centerY + dp(10f), right.shadowScale, 0.14f);

        // 先画右边半透明角色，再画左边主角色，层级更接近原图
        drawBlob(canvas, right, centerY, 0.72f);
        drawBlob(canvas, left, centerY, 1.00f);
    }

    private Motion buildMotion(float t, boolean isLeft, float centerX) {
        float restOffset = dp(12f);
        float impactOffset = dp(4f);
        float overshootOffset = dp(13.5f);

        float offset;
        if (t < 0.12f) {
            offset = restOffset;
        } else if (t < 0.42f) {
            float p = easeInOut(phase(t, 0.12f, 0.42f));
            offset = lerp(restOffset, impactOffset, p);
        } else if (t < 0.54f) {
            offset = impactOffset;
        } else if (t < 0.82f) {
            float p = easeOutBack(phase(t, 0.54f, 0.82f));
            offset = lerp(impactOffset, overshootOffset, p);
        } else {
            float p = easeInOut(phase(t, 0.82f, 1f));
            offset = lerp(overshootOffset, restOffset, p);
        }

        float squash = 0f;
        if (t >= 0.40f && t <= 0.58f) {
            squash = (float) Math.sin(Math.PI * phase(t, 0.40f, 0.58f));
        }

        float rebound = 0f;
        if (t >= 0.54f && t <= 0.82f) {
            rebound = (float) Math.sin(Math.PI * phase(t, 0.54f, 0.82f));
        }

        float scaleX = 1f - 0.16f * squash + 0.04f * rebound;
        float scaleY = 1f + 0.14f * squash - 0.03f * rebound;
        float shadowScale = 1f + 0.18f * squash - 0.06f * rebound;

        float rotation;
        if (t < 0.12f) {
            rotation = 0f;
        } else if (t < 0.42f) {
            float p = easeInOut(phase(t, 0.12f, 0.42f));
            rotation = lerp(0f, isLeft ? 6f : -6f, p);
        } else if (t < 0.54f) {
            float p = phase(t, 0.42f, 0.54f);
            rotation = lerp(isLeft ? 6f : -6f, isLeft ? -4f : 4f, p);
        } else if (t < 0.82f) {
            float p = easeOutBack(phase(t, 0.54f, 0.82f));
            rotation = lerp(isLeft ? -4f : 4f, isLeft ? 2f : -2f, p);
        } else {
            float p = easeInOut(phase(t, 0.82f, 1f));
            rotation = lerp(isLeft ? 2f : -2f, 0f, p);
        }

        Motion m = new Motion();
        m.cx = isLeft ? centerX - offset : centerX + offset;
        m.scaleX = scaleX;
        m.scaleY = scaleY;
        m.rotation = rotation;
        m.shadowScale = shadowScale;
        return m;
    }

    private void drawShadow(Canvas canvas, float cx, float cy, float scaleX, float alpha) {
        int a = Math.round(255 * alpha);
        shadowPaint.setAlpha(a);

        float sw = dp(15.5f) * scaleX;
        float sh = dp(4.0f);

        rect.set(cx - sw, cy - sh, cx + sw, cy + sh);
        canvas.drawOval(rect, shadowPaint);
    }

    private void drawBlob(Canvas canvas, Motion m, float centerY, float alpha) {
        int a = Math.round(255 * alpha);

        float bodyW = dp(24.5f);
        float bodyH = dp(15.5f);
        float radius = dp(7.8f);
        float stroke = dp(2.35f);
        float earR = dp(2.8f);
        float eyeR = dp(2.25f);

        bodyPaint.setAlpha(a);
        strokePaint.setAlpha(a);
        whitePaint.setAlpha(a);
        yellowPaint.setAlpha(a);

        strokePaint.setStrokeWidth(stroke);

        canvas.save();
        canvas.translate(m.cx, centerY);
        canvas.rotate(m.rotation);
        canvas.scale(m.scaleX, m.scaleY);

        float bodyTop = -bodyH / 2f + dp(2.8f);
        float bodyBottom = bodyTop + bodyH;

        // 耳朵
        float earY = bodyTop - dp(1.8f);
        canvas.drawCircle(-bodyW * 0.19f, earY, earR, whitePaint);
        canvas.drawCircle(bodyW * 0.19f, earY, earR, whitePaint);

        // 主体
        rect.set(-bodyW / 2f, bodyTop, bodyW / 2f, bodyBottom);
        canvas.drawRoundRect(rect, radius, radius, bodyPaint);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        // 眼睛
        float eyeY = bodyTop + bodyH * 0.58f;
        canvas.drawCircle(-bodyW * 0.18f, eyeY, eyeR, whitePaint);
        canvas.drawCircle(bodyW * 0.18f, eyeY, eyeR, yellowPaint);

        canvas.restore();
    }

    private static class Motion {
        float cx;
        float scaleX;
        float scaleY;
        float rotation;
        float shadowScale;
    }

    private float phase(float t, float start, float end) {
        if (t <= start) return 0f;
        if (t >= end) return 1f;
        return (t - start) / (end - start);
    }

    private float lerp(float start, float end, float p) {
        return start + (end - start) * p;
    }

    private float easeInOut(float x) {
        if (x < 0.5f) {
            return 4f * x * x * x;
        }
        float k = -2f * x + 2f;
        return 1f - (k * k * k) / 2f;
    }

    private float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private int dpInt(float value) {
        return Math.round(dp(value));
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            start();
        } else {
            stop();
        }
    }
}