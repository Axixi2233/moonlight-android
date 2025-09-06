package com.limelight.ui.floatingview;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.preferences.PreferenceConfiguration;

/**
* 自动吸附悬浮窗view
* Date: 2024/9/14 星期六
*/
public class AXFloatingMagnetView extends FrameLayout {

    public static final int MARGIN_EDGE = 13;
    private float mOriginalRawX;
    private float mOriginalRawY;
    private float mOriginalX;
    private float mOriginalY;
    private AXFloatingViewListener mAXFloatingViewListener;
    private static final int TOUCH_TIME_THRESHOLD = 150;
    private long mLastTouchDownTime;
    protected MoveAnimator mMoveAnimator;
    protected int mScreenWidth;
    private int mScreenHeight;
    private int mStatusBarHeight;
    private boolean isNearestLeft = true;
    private float mPortraitY;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable delayedAction;

    public void setFloatingViewListener(AXFloatingViewListener listener) {
        this.mAXFloatingViewListener = listener;
    }

    public AXFloatingMagnetView(Context context) {
        this(context, null);
    }

    public AXFloatingMagnetView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AXFloatingMagnetView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mMoveAnimator = new MoveAnimator();
        mStatusBarHeight = 0;
        setClickable(true);
        if(PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionAuto
                &&PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionX !=-1){
            setX(PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionX);
            setY(PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionY);
            isNearestLeft=PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionIsNearestLeft;
        }
        startDelayedAction();
//        updateSize();
    }

    // 新增部分：延迟2秒修改透明度和位置
    private void startDelayedAction() {
        delayedAction = new Runnable() {
            @Override
            public void run() {
                // 修改透明度和位置
                mMoveAnimator.stop();
                animate().alpha(0.3f).setDuration(100).start();
                float x=isNearestLeft? (float) -getWidth() /2:getX()+ (float) getWidth() /2;
                animate().translationX(x).setDuration(100).start();
                if(PreferenceConfiguration.readPreferences(getContext()).axFloatingPostionAuto){
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit()
                            .putFloat("ax_floating_postion_x",getX())
                            .apply();
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit()
                            .putFloat("ax_floating_postion_y",getY())
                            .apply();
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit()
                            .putBoolean("ax_floating_postion_isnearestleft",isNearestLeft)
                            .apply();
                }
            }
        };
        mHandler.postDelayed(delayedAction, 2000);
    }

    private void cancelDelayedAction() {
        if (delayedAction != null) {
            mHandler.removeCallbacks(delayedAction);
        }
        animate().alpha(1.0f).setDuration(100).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                changeOriginalTouchParams(event);
                updateSize();
                mMoveAnimator.stop();
                cancelDelayedAction();
                break;
            case MotionEvent.ACTION_MOVE:
                updateViewPosition(event);
                break;
            case MotionEvent.ACTION_UP:
                clearPortraitY();
                moveToEdge();
                if (isOnClickEvent()) {
                    dealClickEvent();
                }
                startDelayedAction();
                break;
        }
        return true;
    }

    protected void dealClickEvent() {
        if (mAXFloatingViewListener != null) {
            mAXFloatingViewListener.onClick(this);
        }
    }

    protected boolean isOnClickEvent() {
        return System.currentTimeMillis() - mLastTouchDownTime < TOUCH_TIME_THRESHOLD;
    }

    private void updateViewPosition(MotionEvent event) {
        setX(mOriginalX + event.getRawX() - mOriginalRawX);
        // 限制不可超出屏幕高度
        float desY = mOriginalY + event.getRawY() - mOriginalRawY;
        if (desY < mStatusBarHeight) {
            desY = mStatusBarHeight;
        }
        if (desY > mScreenHeight - getHeight()) {
            desY = mScreenHeight - getHeight();
        }
        setY(desY);
    }

    private void changeOriginalTouchParams(MotionEvent event) {
        mOriginalX = getX();
        mOriginalY = getY();
        mOriginalRawX = event.getRawX();
        mOriginalRawY = event.getRawY();
        mLastTouchDownTime = System.currentTimeMillis();
    }

    protected void updateSize() {
        ViewGroup viewGroup = (ViewGroup) getParent();
        if (viewGroup != null) {
            mScreenWidth = viewGroup.getWidth() - getWidth();
            mScreenHeight = viewGroup.getHeight();
        }
    }

    public void moveToEdge() {
        moveToEdge(isNearestLeft(), false);
    }

    public void moveToEdge(boolean isLeft, boolean isLandscape) {
        float moveDistance = isLeft ? MARGIN_EDGE : mScreenWidth - MARGIN_EDGE;
        float y = getY();
        if (!isLandscape && mPortraitY != 0) {
            y = mPortraitY;
            clearPortraitY();
        }
        mMoveAnimator.start(moveDistance, Math.min(Math.max(0, y), mScreenHeight - getHeight()));
    }

    private void clearPortraitY() {
        mPortraitY = 0;
    }

    protected boolean isNearestLeft() {
        int middle = mScreenWidth / 2;
        isNearestLeft = getX() < middle;
        return isNearestLeft;
    }

    protected class MoveAnimator implements Runnable {

        private Handler handler = new Handler(Looper.getMainLooper());
        private float destinationX;
        private float destinationY;
        private long startingTime;

        void start(float x, float y) {
            this.destinationX = x;
            this.destinationY = y;
            startingTime = System.currentTimeMillis();
            handler.post(this);
        }

        @Override
        public void run() {
            if (getRootView() == null || getRootView().getParent() == null) {
                return;
            }
            float progress = Math.min(1, (System.currentTimeMillis() - startingTime) / 400f);
            float deltaX = (destinationX - getX()) * progress;
            float deltaY = (destinationY - getY()) * progress;
            move(deltaX, deltaY);
            if (progress < 1) {
                handler.post(this);
            }
        }

        private void stop() {
            handler.removeCallbacks(this);
        }
    }

    private void move(float deltaX, float deltaY) {
        setX(getX() + deltaX);
        setY(getY() + deltaY);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getParent() != null) {
            final boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
            markPortraitY(isLandscape);
            ((ViewGroup) getParent()).post(new Runnable() {
                @Override
                public void run() {
                    updateSize();
                    moveToEdge(isNearestLeft, isLandscape);
                }
            });
        }
    }

    private void markPortraitY(boolean isLandscape) {
        if (isLandscape) {
            mPortraitY = getY();
        }
    }
}
