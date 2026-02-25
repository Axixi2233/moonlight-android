package com.limelight.ui;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.ViewGroup;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;


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
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
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
            initX = getX();
            initY = getY();
            posX = initX;
            posY = initY;
            initFlag = true;
        }
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


}
