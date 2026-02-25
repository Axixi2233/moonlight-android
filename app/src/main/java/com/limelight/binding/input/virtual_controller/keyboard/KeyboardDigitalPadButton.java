package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

public class KeyboardDigitalPadButton extends keyBoardVirtualControllerElement{

    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;
    private final RectF rect = new RectF();

    private final Paint paint = new Paint();

    protected String[] textTipValues={"W","A","S","D"};

    protected KeyboardDigitalPadButton(KeyBoardController controller, Context context, String elementId) {
        super(controller, context, elementId);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    public void setTextTipValues(String[] textTipValues) {
        this.textTipValues = textTipValues;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);

        // 1. 基础参数初始化
        float width = getWidth();
        float height = getHeight();
        float minSide = Math.min(width, height);

        // 开启抗锯齿，保证圆角平滑
        paint.setAntiAlias(true);

        // --- 文本大小优化：调小到 12% ~ 14% 左右比较灵巧 ---
        paint.setTextSize(minSide * 0.12f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true); // 保持加粗以增加识别度

        // 计算文字垂直居中偏移
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textOffset = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom;

        // 2. 坐标比例定义
        // 33.5% 和 66.5% 留出极小的中间空隙，让视觉更干净
        float p33 = width * 0.335f;
        float p66 = width * 0.665f;
        float cornerRadius = minSide * 0.10f; // 增加圆角，让按键更圆润

        // --- 步骤 1：绘制四个独立的方向块 (删除了中心矩形绘制) ---
        if(textTipValues.length<4){
            paint.setFakeBoldText(false);
            return;
        }
        // 上 (W)
        drawStyledPart(canvas, p33, DPAD_MARGIN, p66, p33,
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0, textTipValues[0], textOffset, cornerRadius);

        // 下 (S)
        drawStyledPart(canvas, p33, p66, p66, height - DPAD_MARGIN,
                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0, textTipValues[2], textOffset, cornerRadius);

        // 左 (A)
        drawStyledPart(canvas, DPAD_MARGIN, p33, p33, p66,
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0, textTipValues[1], textOffset, cornerRadius);

        // 右 (D)
        drawStyledPart(canvas, p66, p33, width - DPAD_MARGIN, p66,
                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0, textTipValues[3], textOffset, cornerRadius);

        paint.setFakeBoldText(false); // 重置画笔状态
    }

    /**
     * 优化后的独立按键绘制逻辑
     */
    private void drawStyledPart(Canvas canvas, float l, float t, float r, float b,
                                boolean isPressed, String label, float textOffset, float radius) {
        rect.set(l, t, r, b);
        // 1. 绘制按键背景
        paint.setStyle(isNomal() ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        paint.setColor(isPressed ? pressedColor : getDefaultColor());
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 2. 绘制精致描边 (始终保留细边框，增强立体感)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        // 按下时高亮描边
        paint.setColor(isPressed ? Color.WHITE : strokeColor);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 3. 绘制文字
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);

        float centerX = rect.centerX();
        float centerY = rect.centerY();

        canvas.drawText(label, centerX, centerY + textOffset, paint);
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);
        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }
}
