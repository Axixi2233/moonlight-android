package com.limelight.haptics;

public final class NoOpPcmHapticsBackend implements PcmHapticsBackend {
    public static final NoOpPcmHapticsBackend INSTANCE = new NoOpPcmHapticsBackend();

    private NoOpPcmHapticsBackend() {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean handlesDevice(int vendorId, int productId) {
        return false;
    }

    @Override
    public void start() {
    }

    @Override
    public boolean submitRumble(int vendorId, int productId,
                                int lowFrequencyMotor, int highFrequencyMotor) {
        return false;
    }

    @Override
    public boolean submitPcm(byte[] pcm, int sampleRate, int channelCount, float gain) {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public void destroy() {
    }
}
