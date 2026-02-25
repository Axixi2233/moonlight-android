package com.limelight.ui.floatingview;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.limelight.R;

/**
* 悬浮窗
* Date: 2024/9/14 星期六
*/
public class AXFloatingView extends AXFloatingMagnetView {

    private final ImageView mIcon;

    public AXFloatingView(@NonNull Context context) {
        this(context, R.layout.ax_floating_view);
    }

    public AXFloatingView(@NonNull Context context, @LayoutRes int resource) {
        super(context, null);
        inflate(context, resource, this);
        mIcon = findViewById(R.id.iv_icon);
    }

    public void setIconImage(@DrawableRes int resId){
        mIcon.setImageResource(resId);
    }

    public static FrameLayout.LayoutParams getLayParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins(8, 220, params.rightMargin, params.bottomMargin);
        return params;
    }

}
