package com.limelight.ui.gamemenu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchPadView extends View {

    private static final int TOUCH_SLOP = 4;
    private static final int PRESS_FEEDBACK_ALPHA = 180;

    private float lastX, lastY;
    private boolean isMoving = false;
    private long lastDownTime = 0;

    private float cursorX, cursorY;

    private Drawable backgroundDrawable;
    private Paint cursorPaint;
    private Paint pressOverlayPaint;

    private boolean isPressedFeedback = false;

    public TouchPadViewLister viewLister;

    public void setViewLister(TouchPadViewLister viewLister) {
        this.viewLister = viewLister;
    }

    public interface TouchPadViewLister{
        // 光标事件模拟接口
        void sendMouseMove(float dx, float dy);

        void sendMouseLeft(boolean down);

        void sendMouseRight(boolean down);

        void sendMouseScroll(float distance);
    }

    public TouchPadView(Context context) {
        super(context);
        init();
    }

    public TouchPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cursorPaint = new Paint();
        cursorPaint.setColor(Color.BLACK);
        cursorPaint.setAntiAlias(true);

        pressOverlayPaint = new Paint();
        pressOverlayPaint.setColor(Color.parseColor("#565656"));

//        // 初始中心位置
//        post(() -> {
//            cursorX = getWidth() / 2f;
//            cursorY = getHeight() / 2f;
//            invalidate();
//        });
    }

    public void setBackgroundDrawable(Drawable drawable) {
        backgroundDrawable = drawable;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (backgroundDrawable != null) {
            backgroundDrawable.setBounds(0, 0, getWidth(), getHeight());
            backgroundDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.parseColor("#383838"));
        }

        // 光标
//        canvas.drawCircle(cursorX, cursorY, 15, cursorPaint);

        // 按压反馈
        if (isPressedFeedback) {
//            pressOverlayPaint.setAlpha(PRESS_FEEDBACK_ALPHA);
            canvas.drawRect(0, 0, getWidth(), getHeight(), pressOverlayPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int pointerCount = event.getPointerCount();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isPressedFeedback = true;
                invalidate();
                lastDownTime = System.currentTimeMillis();
                lastX = event.getX();
                lastY = event.getY();
                isMoving = false;
                if (viewLister != null) {
//                    viewLister.sendMouseLeft(true); // 左键按下
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastX = averageX(event);
                lastY = averageY(event);
                isMoving = false;
                if (pointerCount == 2 && viewLister != null) {
//                    viewLister.sendMouseRight(true); // 右键按下
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;

                    if (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP) {
                        cursorX += dx;
                        cursorY += dy;
                        cursorX = clamp(cursorX, 0, getWidth());
                        cursorY = clamp(cursorY, 0, getHeight());

                        invalidate();
                        if (viewLister != null) {
                            viewLister.sendMouseMove(dx, dy);
                        }
                        isMoving = true;
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                } else if (pointerCount == 2) {
                    float dy = averageY(event) - lastY;
                    if (Math.abs(dy) > TOUCH_SLOP) {
                        if (viewLister != null) {
                            viewLister.sendMouseScroll(dy);
                        }
                        lastY = averageY(event);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                isPressedFeedback = false;
                invalidate();
                if (viewLister != null) {
                    viewLister.sendMouseLeft(true);
                    viewLister.sendMouseLeft(false);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (pointerCount == 2 && viewLister != null) {
                    viewLister.sendMouseRight(true); // 右键抬起
                    viewLister.sendMouseRight(false); // 左键抬起
                }
                break;
        }
        return true;
    }


    private float averageX(MotionEvent event) {
        float sum = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getX(i);
        }
        return sum / event.getPointerCount();
    }

    private float averageY(MotionEvent event) {
        float sum = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getY(i);
        }
        return sum / event.getPointerCount();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }


}

