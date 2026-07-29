package com.limelight;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Description
 * Date: 2024-03-29
 * Time: 17:26
 */
public class SecondaryDisplayPresentation extends Presentation {

    private FrameLayout view;
    public SecondaryDisplayPresentation(Context context, Display display) {
        super(context, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view= (FrameLayout) View.inflate(getContext(),R.layout.activity_game_display,null);
        setContentView(view);
    }

    public void addView(View renderView){
        view.addView(renderView);
    }

    public boolean selectPreferredDisplayMode(int width, int height, float targetFps) {
        Display display = getDisplay();
        if (display == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return false;
        }

        Display.Mode bestMode = null;
        float bestRefreshDelta = Float.MAX_VALUE;
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != width || mode.getPhysicalHeight() != height) {
                continue;
            }
            float refreshDelta = Math.abs(mode.getRefreshRate() - targetFps);
            if (bestMode == null || refreshDelta < bestRefreshDelta
                    || (refreshDelta == bestRefreshDelta
                    && mode.getRefreshRate() > bestMode.getRefreshRate())) {
                bestMode = mode;
                bestRefreshDelta = refreshDelta;
            }
        }

        if (bestMode == null || getWindow() == null) {
            return false;
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.preferredDisplayModeId = bestMode.getModeId();
        getWindow().setAttributes(attributes);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        view.removeAllViews();
    }
}
