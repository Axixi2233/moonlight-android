package com.limelight.stereo3d;

/** Shared output-packing semantics for the host and optional stereo backend. */
public final class Stereo3dOutputLayout {
    public static final int FULL_SBS = 0;
    public static final int HALF_SBS = 1;

    private Stereo3dOutputLayout() {
    }

    public static int normalize(int layout) {
        return layout == HALF_SBS ? HALF_SBS : FULL_SBS;
    }

    public static float getHorizontalPixelAspect(int layout) {
        return normalize(layout) == HALF_SBS ? 2.0f : 1.0f;
    }

    public static int calculateViewedEyeWidth(int outputWidth, int layout) {
        float storedEyeWidth = Math.max(outputWidth, 2) * 0.5f;
        return Math.round(storedEyeWidth * getHorizontalPixelAspect(layout));
    }

    public static float calculateEyeAspect(int outputWidth, int outputHeight, int layout) {
        return calculateViewedEyeWidth(outputWidth, layout)
                / (float) Math.max(outputHeight, 1);
    }
}
