package com.limelight.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.limelight.R;

public final class HomePageIndicatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float selectedWidth;
    private final float dotSize;
    private final float gap;
    private int count;
    private int selectedIndex;

    public HomePageIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        selectedWidth = 22f * density;
        dotSize = 6f * density;
        gap = 7f * density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setPageState(int count, int selectedIndex) {
        this.count = Math.max(0, count);
        this.selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, count - 1)));
        setVisibility(count > 1 ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (count <= 1) {
            return;
        }

        float totalWidth = selectedWidth + (count - 1) * dotSize + (count - 1) * gap;
        float x = (getWidth() - totalWidth) / 2f;
        float y = (getHeight() - dotSize) / 2f;
        float radius = dotSize / 2f;

        for (int index = 0; index < count; index++) {
            boolean selected = index == selectedIndex;
            float width = selected ? selectedWidth : dotSize;
            paint.setColor(getResources().getColor(
                    selected ? R.color.home_accent_bright : R.color.home_soft_border));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + dotSize), radius, radius, paint);
            x += width + gap;
        }
    }
}
