package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.limelight.utils.UiHelper;

/**
 * Description
 */
public class RoundedImageView extends android.support.v7.widget.AppCompatImageView {

    public RoundedImageView(Context context) {
        super(context);
    }

    public RoundedImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoundedImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    private Paint paint;
    private Path clipPath;
    private RectF rectF;
    private int radius;
    private int bgColor;

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        clipPath = new Path();
        rectF = new RectF();
        radius = UiHelper.dpToPx(getContext(),12);
        bgColor = 0xFFFFFF;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        invalidate();
    }

    public void setBgColor(int bgColor) {
        this.bgColor = bgColor;
        paint.setColor(bgColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        clipPath.reset();
        rectF.set(0, 0, getWidth(), getHeight());
        clipPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawRect(rectF, paint);
        super.onDraw(canvas);
    }

}
