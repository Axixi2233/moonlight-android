package com.limelight.haptics;

public interface PcmHapticsBackend {
    interface Callback {
        default void onUsbPermissionPromptStarting() {
        }

        default void onUsbPermissionPromptCompleted() {
        }

        default void onAvailabilityChanged(boolean active) {
        }

        default void onSessionLogEvent(String level, String category, String message) {
        }
    }

    boolean isActive();

    boolean handlesDevice(int vendorId, int productId);

    default String getActiveDeviceDisplayName() {
        return "";
    }

    default String getActiveProtocolDisplayName() {
        return "";
    }

    void start();

    boolean submitRumble(int vendorId, int productId,
                         int lowFrequencyMotor, int highFrequencyMotor);

    boolean submitPcm(byte[] pcm, int sampleRate, int channelCount, float gain);

    void stop();

    void destroy();
}
