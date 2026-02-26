package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class keyAnalogStickFree extends keyBoardVirtualControllerElement {

    public final static long timeoutDoubleClick = 350;
    public final static long timeoutDeadzone = 150;

    private float radius_complete = 0;
    private float radius_analog_stick = 0;
    private float radius_dead_zone = 0;

    private float relative_x = 0;
    private float relative_y = 0;
    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private boolean bIsFingerOnScreen = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;

    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private int touchID = -1;
    private float touchStartX;
    private float touchStartY;

    protected String strStickSide = "摇杆";
    protected String[] textTipValues = {"▲", "◀", "▼", "▶"};

    private enum STICK_STATE { NO_MOVEMENT, MOVED_IN_DEAD_ZONE, MOVED_ACTIVE }
    private enum CLICK_STATE { SINGLE, DOUBLE }

    public interface AnalogStickListener {
        void onMovement(float x, float y);
        void onClick();
        void onDoubleClick();
        void onRevoke();
    }

    public keyAnalogStickFree(KeyBoardController controller, Context context, String elementId) {
        super(controller, context, elementId);
        paint.setSubpixelText(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float baseSize = getCorrectWidth() / 2f;
        // 严格遵循原版比例计算
        radius_complete = (getCorrectWidth() / 2f) * 0.6f - (2 * getDefaultStrokeWidth());
        radius_dead_zone = (getCorrectWidth() / 2f) * 0.3f;
        radius_analog_stick = (getCorrectWidth() / 2f) * 0.2f;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    // --- 核心手感算法：沿用原版角度计算 ---
    private static double getAngle(float way_x, float way_y) {
        if (way_x == 0) return way_y < 0 ? Math.PI : 0;
        if (way_y == 0) return way_x > 0 ? Math.PI * 1.5 : Math.PI * 0.5;

        if (way_x > 0) {
            return way_y < 0 ? 1.5 * Math.PI + Math.atan(-way_y / way_x) : Math.PI + Math.atan(way_x / way_y);
        } else {
            return way_y > 0 ? 0.5 * Math.PI + Math.atan(way_y / -way_x) : Math.atan(-way_x / -way_y);
        }
    }

    //未触摸状态是否绘制
    private boolean isDrawNormal=true;

    public keyAnalogStickFree setDrawNormal(boolean drawNormal) {
        isDrawNormal = drawNormal;
        return this;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);

        // 1. 编辑模式
        if (virtualController.getControllerMode() != KeyBoardController.ControllerMode.Active) {
            drawEditMode(canvas);
            return;
        }

        if(!bIsFingerOnScreen&&!isDrawNormal){
            return;
        }
        // 2. 确定绘制中心 (未按下时在控件中心，按下时在起点)
        float cX = bIsFingerOnScreen ? touchStartX : getWidth() / 2f;
        float cY = bIsFingerOnScreen ? touchStartY : getHeight() / 2f;

        // 3. 绘制外圆和背景
        if(isDrawNormal){
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(strokeColor);
            canvas.drawCircle(cX, cY, radius_complete, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getDefaultColor());
            canvas.drawCircle(cX, cY, radius_complete, paint);
        }

        // 4. 绘制方向字符
        if(textTipValues.length>3){
            drawDirectionText(canvas, cX, cY);
        }

        // 5. 绘制摇杆球 (计算位置完全同步原版逻辑)
        float sX = bIsFingerOnScreen ? position_stick_x : cX;
        float sY = bIsFingerOnScreen ? position_stick_y : cY;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        canvas.drawCircle(sX, sY, radius_analog_stick, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(strokeColor);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        canvas.drawCircle(sX, sY, radius_analog_stick, paint);
    }

    private void drawEditMode(Canvas canvas) {
        paint.setColor(getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRoundRect(2, 2, getWidth() - 2, getHeight() - 2, 20, 20, paint);

        paint.setColor(textColor);
        paint.setTextSize(Math.min(getWidth(), getHeight()) / 6f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(strStickSide, getWidth() / 2f, getHeight() / 2f + (fm.bottom - fm.top) / 4, paint);
    }

    private void drawDirectionText(Canvas canvas, float cX, float cY) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius_complete * 0.35f);
        paint.setColor(textColor);
        paint.setFakeBoldText(true);
        float offset = radius_complete * 0.72f;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float fix = (fm.bottom - fm.top) / 2 - fm.bottom;
        canvas.drawText(textTipValues[0], cX, cY - offset + fix, paint);
        canvas.drawText(textTipValues[2], cX, cY + offset + fix, paint);
        canvas.drawText(textTipValues[1], cX - offset, cY + fix, paint);
        canvas.drawText(textTipValues[3], cX + offset, cY + fix, paint);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!bIsFingerOnScreen) {
                    touchID = event.getPointerId(actionIndex);
                    touchStartX = event.getX(actionIndex);
                    touchStartY = event.getY(actionIndex);
                    bIsFingerOnScreen = true;
                    setPressed(true);

                    // 原版点击逻辑
                    stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
                    if (System.currentTimeMillis() - timeLastClick <= timeoutDoubleClick) {
                        notifyOnDoubleClick();
                    } else {
                        notifyOnClick();
                    }
                    timeLastClick = System.currentTimeMillis();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) == touchID) {
                        updatePositionInternal(event.getX(i), event.getY(i), event.getEventTime());
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (event.getPointerId(actionIndex) == touchID) {
                    bIsFingerOnScreen = false;
                    setPressed(false);
                    stick_state = STICK_STATE.NO_MOVEMENT;
                    notifyOnRevoke();
                    notifyOnMovement(0, 0);
                    touchID = -1;
                }
                break;
        }
        invalidate();
        return true;
    }

    private void updatePositionInternal(float tx, float ty, long eventTime) {
        // 1. 计算相对位移 (遵循原版)
        relative_x = -(touchStartX - tx);
        relative_y = -(touchStartY - ty);

        // 2. 计算极坐标 (遵循原版)
        movement_radius = Math.sqrt(relative_x * relative_x + relative_y * relative_y);
        movement_angle = getAngle(relative_x, relative_y);

        // 3. 边界锁定
        float complete = radius_complete - radius_analog_stick;
        if (movement_radius > complete) {
            movement_radius = complete;
        }

        // 4. 计算摇杆球视觉坐标 (Sin/Cos 映射确保手感丝滑)
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        position_stick_x = touchStartX - correlated_x;
        position_stick_y = touchStartY - correlated_y;

        // 5. 状态机切换
        stick_state = (stick_state == STICK_STATE.MOVED_ACTIVE ||
                eventTime - timeLastClick > timeoutDeadzone ||
                movement_radius > radius_dead_zone) ?
                STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;

        // 6. 归一化回调 (关键：保持 Limelight 协议一致性)
        if (stick_state == STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }

    public keyAnalogStickFree setTextTipValues(String[] textTipValues) {
        this.textTipValues = textTipValues;
        return this;
    }

    public void addAnalogStickListener(AnalogStickListener l) { listeners.add(l); }
    private void notifyOnMovement(float x, float y) { for (AnalogStickListener l : listeners) l.onMovement(x, y); }
    private void notifyOnClick() { for (AnalogStickListener l : listeners) l.onClick(); }
    private void notifyOnDoubleClick() { for (AnalogStickListener l : listeners) l.onDoubleClick(); }
    private void notifyOnRevoke() { for (AnalogStickListener l : listeners) l.onRevoke(); }
}