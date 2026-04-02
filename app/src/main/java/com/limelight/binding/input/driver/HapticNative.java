package com.limelight.binding.input.driver;

import java.nio.ByteBuffer;

public final class HapticNative {

    static {
        System.loadLibrary("moonlight-core");
    }

    private HapticNative() {
    }

    public static native boolean nativeConnectHaptics(int fd, int ifaceId, int altSetting, byte epAddr);

    public static native boolean nativeEnableHaptics();

    public static native boolean nativeSendHapticFeedback(ByteBuffer buffer, int length, float intensityGain);

    public static native void nativeCleanupHaptics();
}
