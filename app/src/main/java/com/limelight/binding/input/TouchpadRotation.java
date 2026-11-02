package com.limelight.binding.input;

/** Counter-clockwise correction in screen coordinates (positive Y points down). */
public final class TouchpadRotation {
    private TouchpadRotation() {}
    public static int normalize(int rotation) {
        return rotation >= 0 && rotation <= 3 ? rotation : 0;
    }
    public static float x(float x, float y, int rotation) {
        switch (normalize(rotation)) {
            case 1: return y;
            case 2: return -x;
            case 3: return -y;
            default: return x;
        }
    }
    public static float y(float x, float y, int rotation) {
        switch (normalize(rotation)) {
            case 1: return -x;
            case 2: return -y;
            case 3: return x;
            default: return y;
        }
    }
}
