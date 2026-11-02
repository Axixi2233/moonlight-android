package com.limelight.binding.input;

public final class GyroActivation {
    private GyroActivation() {}
    public static short reportRate(short hostRate, boolean localMappingEnabled) {
        return (short) Math.min(200, Math.max(localMappingEnabled ? 100 : 0, hostRate));
    }
    public static int normalize(int mode) {
        return mode >= 0 && mode <= 2 ? mode : 0;
    }
    public static boolean isActive(int mode, short left, short right) {
        switch (normalize(mode)) {
            case 1: return (left & 0xFF) >= 200;
            case 2: return (right & 0xFF) >= 200;
            default: return true;
        }
    }
}
