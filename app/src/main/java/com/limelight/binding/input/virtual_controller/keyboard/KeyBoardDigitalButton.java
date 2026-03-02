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

import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardDigitalButton extends keyBoardVirtualControllerElement {

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

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private int iconPress=-1;
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
    private KeyBoardDigitalButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, KeyBoardDigitalButton movingButton) {
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
            if (element != this && element instanceof KeyBoardDigitalButton) {
                ((KeyBoardDigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardDigitalButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
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
    public void setIconPress(int iconPress) {
        this.iconPress = iconPress;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // 1. 基础准备
        canvas.drawColor(Color.TRANSPARENT);

        // 动态计算合适的字体大小：取宽高的最小值作为基准，防止长方形按钮文字爆框
        float minSide = Math.min(getWidth(), getHeight());
        paint.setTextSize(minSide * 0.25f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        // 2. 准备绘制区域 (内切矩形)
        float strokeW = paint.getStrokeWidth();
        rect.set(strokeW, strokeW, getWidth() - strokeW, getHeight() - strokeW);

        // 3. 绘制背景 (Pressed / Normal)
        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        paint.setStyle(isPressed() ? Paint.Style.FILL_AND_STROKE :
                isNomal() ? Paint.Style.FILL : Paint.Style.STROKE);

        if (shapeType == 1) {
            canvas.drawRoundRect(rect, 42, 42, paint);
        } else {
            canvas.drawOval(rect, paint);
        }

        // 4. 绘制描边 (Stroke)
        paint.setColor(enableSwitchDown?strokeSwicthModeColor:strokeColor);
        paint.setStyle(Paint.Style.STROKE);
        if (shapeType == 1) {
            canvas.drawRoundRect(rect, 42, 42, paint);
        } else {
            canvas.drawOval(rect, paint);
        }

        // 5. 绘制内容 (图标或文字)
        if (icon != -1) {
            // 图标缩放优化：保持 1:1 比例且居中
            int oscOpacity = PreferenceConfiguration.readPreferences(getContext()).oscOpacity;
            Drawable d = getResources().getDrawable(isPressed() ? iconPress : icon);
            int padding = (int) (minSide * 0.15f); // 间距随按钮大小缩放
            d.setBounds(padding, padding, getWidth() - padding, getHeight() - padding);
            d.setAlpha((int) (oscOpacity * 2.55));
            d.draw(canvas);
        } else if (!TextUtils.isEmpty(text)) {
            // --- 文本垂直居中优化核心逻辑 ---
            paint.setColor(textColor);
            paint.setStyle(Paint.Style.FILL); // 绘制文字通常不需要 STROKE，否则会模糊

            // 计算文字垂直居中的偏移量
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            // (bottom - top)/2 是高度一半，再减去 bottom 得到中心点到基线的距离
            float distance = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;
            float baseline = (getHeight() / 2f) + distance;

            canvas.drawText(text, getWidth() / 2f, baseline, paint);
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

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private boolean switchDown;

    private boolean enableSwitchDown;

    public void setEnableSwitchDown(boolean enableSwitchDown) {
        this.enableSwitchDown = enableSwitchDown;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                onClickCallback();

                invalidate();
                if(enableSwitchDown){
                    switchDown=!switchDown;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if(enableSwitchDown&&switchDown){
                    return true;
                }
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}
