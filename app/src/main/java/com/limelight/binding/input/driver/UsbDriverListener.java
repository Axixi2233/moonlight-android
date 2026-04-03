package com.limelight.binding.input.driver;

public interface UsbDriverListener {
    void reportControllerState(int controllerId, int buttonFlags,
                               float leftStickX, float leftStickY,
                               float rightStickX, float rightStickY,
                               float leftTrigger, float rightTrigger);
    void reportControllerMotion(int controllerId, byte motionType, float motionX, float motionY, float motionZ);
    void reportControllerTouchpadEvent(int controllerId, byte eventType, int pointerId,
                                       float x, float y, float pressure);
    void deviceRemoved(AbstractController controller);
    void deviceAdded(AbstractController controller);
}
