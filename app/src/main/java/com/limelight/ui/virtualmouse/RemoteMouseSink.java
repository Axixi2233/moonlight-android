package com.limelight.ui.virtualmouse;

/**
 * Minimal adapter between the virtual mouse UI and the active streaming session.
 */
public interface RemoteMouseSink {
    enum Button {
        LEFT,
        RIGHT,
        MIDDLE
    }

    void sendAbsolutePosition(float x, float y, float referenceWidth, float referenceHeight);

    void sendButton(Button button, boolean pressed);

    void sendVerticalScroll(int ticks);

    void sendHorizontalScroll(int ticks);
}
