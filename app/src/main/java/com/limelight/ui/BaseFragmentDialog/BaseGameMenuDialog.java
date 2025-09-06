package com.limelight.ui.BaseFragmentDialog;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.view.View;

public class BaseGameMenuDialog extends BaseGameMenuFragmentDialog {

    private static final String KEY_LAYOUT_RES = "bottom_layout_res";
    private static final String KEY_WIDTH = "bottom_width";
    private static final String KEY_DIM = "bottom_dim";
    private static final String KEY_CANCEL_OUTSIDE = "bottom_cancel_outside";

    private FragmentManager mFragmentManager;

    private boolean mIsCancelOutside = super.getCancelOutside();

    private String mTag = super.getFragmentTag();

    private float mDimAmount = super.getDimAmount();

    private int mWidth = super.getViewSize();

    @LayoutRes
    private int mLayoutRes;

    private ViewListener mViewListener;

    public static BaseGameMenuDialog create(FragmentManager manager) {
        BaseGameMenuDialog dialog = new BaseGameMenuDialog();
        dialog.setFragmentManager(manager);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLayoutRes = savedInstanceState.getInt(KEY_LAYOUT_RES);
            mWidth = savedInstanceState.getInt(KEY_WIDTH);
            mDimAmount = savedInstanceState.getFloat(KEY_DIM);
            mIsCancelOutside = savedInstanceState.getBoolean(KEY_CANCEL_OUTSIDE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_LAYOUT_RES, mLayoutRes);
        outState.putInt(KEY_WIDTH, mWidth);
        outState.putFloat(KEY_DIM, mDimAmount);
        outState.putBoolean(KEY_CANCEL_OUTSIDE, mIsCancelOutside);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void bindView(View v) {
        if (mViewListener != null) {
            mViewListener.bindView(v);
        }
    }

    @Override
    public int getLayoutRes() {
        return mLayoutRes;
    }

    public BaseGameMenuDialog setFragmentManager(FragmentManager manager) {
        mFragmentManager = manager;
        return this;
    }

    public BaseGameMenuDialog setViewListener(ViewListener listener) {
        mViewListener = listener;
        return this;
    }

    public BaseGameMenuDialog setLayoutRes(@LayoutRes int layoutRes) {
        mLayoutRes = layoutRes;
        return this;
    }

    public BaseGameMenuDialog setCancelOutside(boolean cancel) {
        mIsCancelOutside = cancel;
        return this;
    }

    public BaseGameMenuDialog setTag(String tag) {
        mTag = tag;
        return this;
    }

    public BaseGameMenuDialog setDimAmount(float dim) {
        mDimAmount = dim;
        return this;
    }

    public BaseGameMenuDialog setWidth(int widthPx) {
        mWidth = widthPx;
        return this;
    }

    @Override
    public float getDimAmount() {
        return mDimAmount;
    }

    @Override
    public int getViewSize() {
        return mWidth;
    }

    @Override
    public boolean getCancelOutside() {
        return mIsCancelOutside;
    }

    @Override
    public String getFragmentTag() {
        return mTag;
    }

    public interface ViewListener {
        void bindView(View v);
    }

    public BaseGameMenuFragmentDialog show() {
        show(mFragmentManager);
        return this;
    }
}