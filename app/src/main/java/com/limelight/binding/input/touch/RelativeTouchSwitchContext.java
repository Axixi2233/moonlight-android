package com.limelight.binding.input.touch;

import android.view.View;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

public class RelativeTouchSwitchContext implements TouchContext {
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled;
    private boolean confirmedMove;
    private double xFactor, yFactor;

    private final NvConnection conn;
    private final int actionIndex;
    private final View targetView;
    private final PreferenceConfiguration prefConfig;
    private final boolean clickEnabled; // 新增：是否启用点击

    private static final int TAP_MOVEMENT_THRESHOLD = 20;
    private static final int TAP_TIME_THRESHOLD = 150;

    public RelativeTouchSwitchContext(NvConnection conn, int actionIndex,
                                      int referenceWidth, int referenceHeight,
                                      View view, PreferenceConfiguration prefConfig,
                                      boolean clickEnabled)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.targetView = view;
        this.prefConfig = prefConfig;
        this.clickEnabled = clickEnabled;

        this.xFactor = (double) referenceWidth / targetView.getWidth();
        this.yFactor = (double) referenceHeight / targetView.getHeight();
    }

    @Override
    public int getActionIndex() { return actionIndex; }

    private boolean isWithinTapBounds(int touchX, int touchY) {
        return Math.abs(touchX - originalTouchX) <= TAP_MOVEMENT_THRESHOLD &&
                Math.abs(touchY - originalTouchY) <= TAP_MOVEMENT_THRESHOLD;
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger) {
        if (actionIndex != 0) return true;

        originalTouchX = lastTouchX = eventX;
        originalTouchY = lastTouchY = eventY;

        if (isNewFinger) {
            originalTouchTime = eventTime;
            cancelled = confirmedMove = false;
        }
        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
        // 如果禁用了点击，或者不是主手指，直接返回
        if (cancelled || actionIndex != 0 || !clickEnabled) return;

        long timeDelta = eventTime - originalTouchTime;
        if (!confirmedMove && timeDelta <= TAP_TIME_THRESHOLD && isWithinTapBounds(eventX, eventY)) {
            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
        }
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        if (cancelled || actionIndex != 0) return true;

        if (eventX != lastTouchX || eventY != lastTouchY) {
            if (!confirmedMove && !isWithinTapBounds(eventX, eventY)) {
                confirmedMove = true;
            }

            int deltaX = (int) Math.round((eventX - lastTouchX) * xFactor);
            int deltaY = (int) Math.round((eventY - lastTouchY) * yFactor);

            if (deltaX != 0 || deltaY != 0) {
                if (prefConfig.absoluteMouseMode) {
                    conn.sendMouseMoveAsMousePosition((short) deltaX, (short) deltaY,
                            (short) targetView.getWidth(), (short) targetView.getHeight());
                } else {
                    // 应用你提供的灵敏度配置
                    conn.sendMouseMove(
                            (short) (deltaX * prefConfig.mouseTouchPadSensitityX * 0.01f),
                            (short) (deltaY * prefConfig.mouseTouchPadSensitityY * 0.01f)
                    );
                }
                lastTouchX = eventX;
                lastTouchY = eventY;
            }
        }
        return true;
    }

    @Override
    public void cancelTouch() {
        cancelled = true;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setPointerCount(int pointerCount) {
        // 多指逻辑已完全剥离
    }
}