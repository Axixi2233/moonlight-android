/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardTouchPadButton extends keyBoardVirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        void onMove(int x, int y);

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private KeyBoardTouchPadButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, KeyBoardTouchPadButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof KeyBoardTouchPadButton) {
                ((KeyBoardTouchPadButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardTouchPadButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
        preferenceConfiguration=PreferenceConfiguration.readPreferences(context);
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    private int code;

    public void setCode(int code) {
        this.code = code;
    }

    int pressedColor = 0x805C5CAD;

    PreferenceConfiguration preferenceConfiguration;

    @Override
    protected void onElementDraw(Canvas canvas) {
        // 1. 清除背景
        canvas.drawColor(Color.TRANSPARENT);

        // 2. 初始化画笔与基础参数
        float width = getWidth();
        float height = getHeight();
        float minSide = Math.min(width, height);
        paint.setAntiAlias(true);

        // 统一画笔属性
        paint.setStrokeWidth(getDefaultStrokeWidth());

        // 计算描边内切矩形
        float strokeW = paint.getStrokeWidth();
        rect.set(strokeW, strokeW, width - strokeW, height - strokeW);

        // 设置圆角（保持一致的圆润风格）
        float cornerRadius = minSide * 0.15f;

        // 3. 绘制背景
        // 逻辑：按下时填充 pressedColor，常规时根据 isNomal() 决定填充或描边
        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        paint.setStyle(isPressed() ? Paint.Style.FILL : (isNomal() ? Paint.Style.FILL : Paint.Style.STROKE));
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 4. 绘制精致描边 (始终存在，提升边缘质感)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        paint.setColor(isPressed() ? Color.WHITE : strokeColor); // strokeColor 应在基类中统一定义
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 5. 绘制内容 (图标或文字)
        if (icon != -1) {
            // --- 图标模式 ---
            int oscOpacity = PreferenceConfiguration.readPreferences(getContext()).oscOpacity;
            Drawable d = getResources().getDrawable(isPressed() ?
                    R.mipmap.face_ps_touchpad_press : R.mipmap.face_ps_touchpad_normal);
            // 动态计算 Padding：保持图标在中间，不紧贴边缘
            int padding = (int) (minSide * 0.15f);
            d.setBounds(padding, padding, (int)width - padding, (int)height - padding);
            d.setAlpha((int) (oscOpacity * 2.55));
            d.draw(canvas);
            // 编辑模式下的额外框线
            boolean bIsEditing = virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons ||
                    virtualController.getControllerMode() == KeyBoardController.ControllerMode.ResizeButtons ||
                    virtualController.getControllerMode() == KeyBoardController.ControllerMode.DisableEnableButtons;
            if (bIsEditing) {
                paint.setColor(Color.YELLOW); // 编辑模式使用显眼色
                paint.setStrokeWidth(2);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            }
        } else if (!TextUtils.isEmpty(text)) {
            // --- 文字模式 ---
            paint.setColor(textColor);
            paint.setStyle(Paint.Style.FILL);
            paint.setFakeBoldText(true);
            paint.setTextSize(minSide * 0.1f); // 调小一点，显得更精致
            paint.setTextAlign(Paint.Align.CENTER);

            // 文字垂直居中核心逻辑
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float textOffset = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
            canvas.drawText(text, width / 2f, (height / 2f) + textOffset, paint);

            paint.setFakeBoldText(false);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onMoveCallback(int x, int y) {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onMove(x, y);
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private long originalTouchTime = 0;
    private int lastTouchX = 0;
    private int lastTouchY = 0;

    private double xFactor, yFactor;

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                xFactor = 1280 / (double) getWidth();
                yFactor = 720 / (double) getHeight();
                lastTouchX = (int) event.getX();
                lastTouchY = (int) event.getY();
                movingButton = null;
                originalTouchTime = event.getEventTime();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int deltaX = (int) (event.getX() - lastTouchX);
                int deltaY = (int) (event.getY() - lastTouchY);
                deltaX = (int) Math.round((double) Math.abs(deltaX) * xFactor);
                deltaY = (int) Math.round((double) Math.abs(deltaY) * yFactor);
                // Fix up the signs
                if (event.getX() < lastTouchX) {
                    deltaX = -deltaX;
                }
                if (event.getY() < lastTouchY) {
                    deltaY = -deltaY;
                }
                if (event.getEventTime() - originalTouchTime > 100 && !isPressed()) {
                    setPressed(true);
                    if(code==9||code==11||code==12){
                        onClickCallback();
                    }
                }
//                LimeLog.info("touchPadSensitivity"+preferenceConfiguration.touchPadSensitivity);
//                LimeLog.info("onElementTouchEvent:" + deltaX + "," + deltaY);
                onMoveCallback((int) (deltaX*0.01f*preferenceConfiguration.touchPadSensitivity), (int) (deltaY*0.01f*preferenceConfiguration.touchPadYSensitity));
                if (deltaX != 0) {
                    lastTouchX = (int) event.getX();
                }
                if (deltaY != 0) {
                    lastTouchY = (int) event.getY();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                if (event.getEventTime() - originalTouchTime <= 200) {
                    onClickCallback();
                }
                onReleaseCallback();
                invalidate();
                return true;
            }
            default: {
            }
        }
        return true;
    }
}
