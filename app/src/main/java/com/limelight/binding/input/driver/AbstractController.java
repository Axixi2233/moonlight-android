package com.limelight.binding.input.driver;

import com.limelight.nvstream.jni.MoonBridge;

public abstract class AbstractController {

    private final int deviceId;
    private final int vendorId;
    private final int productId;

    private UsbDriverListener listener;

    protected int buttonFlags, supportedButtonFlags;
    protected float leftTrigger, rightTrigger;
    protected float rightStickX, rightStickY;
    protected float leftStickX, leftStickY;
    protected short capabilities;
    protected byte type;
    protected float gyroX, gyroY, gyroZ;
    protected float accelX, accelY, accelZ;
    public int getControllerId() {
        return deviceId;
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getProductId() {
        return productId;
    }

    public int getSupportedButtonFlags() {
        return supportedButtonFlags;
    }

    public short getCapabilities() {
        return capabilities;
    }

    public byte getType() {
        return type;
    }

    protected void setButtonFlag(int buttonFlag, int data) {
        if (data != 0) {
            buttonFlags |= buttonFlag;
        }
        else {
            buttonFlags &= ~buttonFlag;
        }
    }

    protected void reportInput() {
        listener.reportControllerState(deviceId, buttonFlags, leftStickX, leftStickY,
                rightStickX, rightStickY, leftTrigger, rightTrigger);
    }

    public abstract boolean start();
    public abstract void stop();

    // New method to report motion events
    protected void reportMotion() {
        listener.reportControllerMotion(deviceId, MoonBridge.LI_MOTION_TYPE_GYRO, gyroX, gyroY, gyroZ);
        listener.reportControllerMotion(deviceId, MoonBridge.LI_MOTION_TYPE_ACCEL, accelX, accelY, accelZ);
    }

    public AbstractController(int deviceId, UsbDriverListener listener, int vendorId, int productId) {
        this.deviceId = deviceId;
        this.listener = listener;
        this.vendorId = vendorId;
        this.productId = productId;
    }

    public abstract void rumble(short lowFreqMotor, short highFreqMotor);

    public abstract void rumbleTriggers(short leftTrigger, short rightTrigger);

    public boolean hasAdvancedAudioHapticsSupport() {
        return false;
    }

    public boolean startAdvancedAudioHaptics() {
        return false;
    }

    public void stopAdvancedAudioHaptics() {
    }

    public boolean isAdvancedAudioHapticsActive() {
        return false;
    }

    public boolean submitAdvancedAudioHapticsFrame(byte[] frame, float intensityGain) {
        return false;
    }

    protected void notifyDeviceRemoved() {
        listener.deviceRemoved(this);
    }

    protected void notifyDeviceAdded() {
        listener.deviceAdded(this);
    }

    public void sendCommand(byte[] data){};
}
