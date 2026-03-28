package com.limelight.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;
    // 仅作为 IME 的临时编辑缓冲区使用，组合输入不直接同步到远端
    private final Editable imeEditable = Editable.Factory.getInstance().newEditable("");
    private boolean imeInputConnectionActive;


    private boolean enableZoomAndPan = false;  // 开关变量，控制缩放和平移功能
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;
    private float posX = 0;
    private float posY = 0;

    private float initX;
    private float initY;
    private boolean initFlag;

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    public void setImeInputConnectionActive(boolean active) {
        this.imeInputConnectionActive = active;
        if (!this.imeInputConnectionActive) {
            clearImeEditable();
        }
    }

    public boolean isImeInputConnectionActive() {
        return imeInputConnectionActive;
    }

    public StreamView(Context context) {
        super(context);
        init(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context){
        // 初始化手势检测器
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return isImeInputConnectionActive();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!isImeInputConnectionActive()) {
            return super.onCreateInputConnection(outAttrs);
        }

        // 只在显式打开软键盘时把 StreamView 暂时暴露为文本编辑器，
        // 避免进入串流界面后自动弹出输入法。
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_NONE;
        int selectionStart = Selection.getSelectionStart(imeEditable);
        int selectionEnd = Selection.getSelectionEnd(imeEditable);
        if (selectionStart < 0 || selectionEnd < 0) {
            selectionStart = imeEditable.length();
            selectionEnd = imeEditable.length();
            Selection.setSelection(imeEditable, selectionEnd);
        }
        outAttrs.initialSelStart = selectionStart;
        outAttrs.initialSelEnd = selectionEnd;
        return new StreamInputConnection();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (isImeInputConnectionActive() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                setImeInputConnectionActive(false);
            }
            return super.onKeyPreIme(keyCode, event);
        }

        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
        void handleCommittedText(CharSequence text);
        void handleDeleteSurroundingText(int beforeLength, int afterLength);
    }

    public void setEnableZoomAndPan(boolean enableZoomAndPan) {
        this.enableZoomAndPan = enableZoomAndPan;
    }

    public boolean isEnableZoomAndPan() {
        return enableZoomAndPan;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(!enableZoomAndPan){
            return super.onTouchEvent(event);
        }
        if(!initFlag){
            initX=getX();
            initY=getY();
            initFlag=true;
        }
        // 同时处理缩放和拖动手势
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // 缩放过程中更新缩放比例
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(1f, Math.min(scaleFactor, 15.0f)); // 限制缩放范围
            // 设置缩放
            setScaleX(scaleFactor);
            setScaleY(scaleFactor);
            checkBounds();

            setX(posX);
            setY(posY);

            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // 处理拖动
            posX -= distanceX;
            posY -= distanceY;

            // 限制移动在父控件范围内
            checkBounds();

            // 更新视图位置
            setX(posX);
            setY(posY);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 双击复位
            scaleFactor = 1.0f;
            posX = initX;
            posY = initY;
            setScaleX(scaleFactor);
            setScaleY(scaleFactor);
            setX(posX);
            setY(posY);
            return true;
        }
    }

    private void checkBounds() {
        if(scaleFactor>1.0f){
            return;
        }
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;

        // 获取父控件的宽度和高度
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();

        // 获取 SurfaceView 缩放后的宽度和高度
        float viewWidth = getWidth() * scaleFactor;
        float viewHeight = getHeight() * scaleFactor;

        // 限制 posX 和 posY 在边界内
        posX = Math.max(0, Math.min(posX, parentWidth - viewWidth));
        posY = Math.max(0, Math.min(posY, parentHeight - viewHeight));
    }

    public float getScaleFactor() {
        return scaleFactor;
    }

    private void clearImeEditable() {
        imeEditable.clear();
        Selection.setSelection(imeEditable, 0);
    }

    private boolean hasEditableText() {
        return imeEditable.length() > 0;
    }

    private final class StreamInputConnection extends BaseInputConnection {
        private StreamInputConnection() {
            super(StreamView.this, true);
            Selection.setSelection(imeEditable, imeEditable.length());
        }

        @Override
        public Editable getEditable() {
            return imeEditable;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            boolean handled = super.commitText(text, newCursorPosition);
            if (inputCallbacks != null && text != null && text.length() > 0) {
                // 只把最终提交的文本发送给远端，避免与组合输入阶段重复。
                inputCallbacks.handleCommittedText(text);
            }
            clearImeEditable();
            return handled;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (hasEditableText()) {
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            if (inputCallbacks != null) {
                // 当本地缓冲区为空时，把删除操作映射为远端退格/前删按键。
                inputCallbacks.handleDeleteSurroundingText(beforeLength, afterLength);
            }
            return true;
        }

        @Override
        public boolean finishComposingText() {
            boolean handled = super.finishComposingText();
            if (!hasEditableText()) {
                clearImeEditable();
            }
            return handled;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (inputCallbacks == null) {
                return super.sendKeyEvent(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return inputCallbacks.handleKeyDown(event);
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                return inputCallbacks.handleKeyUp(event);
            }
            return super.sendKeyEvent(event);
        }
    }

}
