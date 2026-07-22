package com.limelight.ui.virtualmouse;

/**
 * Session-scoped state. A new Game activity creates a new instance, so none of this is persisted.
 */
final class VirtualMouseState {
    enum Mode {
        HIDDEN,
        COMPACT,
        EXPANDED,
        DIRECTION_SCROLL
    }

    enum ScrollDirection {
        NONE,
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    boolean enabledForCurrentStream;
    Mode mode = Mode.HIDDEN;
    boolean inputSuppressed;

    float cursorNormalizedX = 0.5f;
    float cursorNormalizedY = 0.5f;
    float compactCenterRatioX = 0.84f;
    float compactCenterRatioY = 0.34f;

    boolean leftPressed;
    boolean rightPressed;
    boolean middlePressed;
    ScrollDirection activeScrollDirection = ScrollDirection.NONE;
}
