package com.limelight.ui.virtualmouse;

import android.os.Handler;
import android.os.Looper;

/**
 * Owns the virtual mouse state machine and guarantees that remote button/scroll state is released
 * when a gesture, overlay, window, or stream lifecycle is interrupted.
 */
public final class VirtualMouseController {
    interface Listener {
        void onVirtualMouseStateChanged();
    }

    private static final long MIDDLE_CLICK_DURATION_MS = 80L;
    private static final long SCROLL_REPEAT_INTERVAL_MS = 100L;

    private final VirtualMouseState state = new VirtualMouseState();
    private final RemoteMouseSink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private int middleClickGeneration;
    private Runnable pendingMiddleRelease;

    private final Runnable scrollRepeater = new Runnable() {
        @Override
        public void run() {
            if (!canSendInput()
                    || state.mode != VirtualMouseState.Mode.DIRECTION_SCROLL
                    || state.activeScrollDirection == VirtualMouseState.ScrollDirection.NONE) {
                return;
            }

            sendScrollTick(state.activeScrollDirection);
            handler.postDelayed(this, SCROLL_REPEAT_INTERVAL_MS);
        }
    };

    public VirtualMouseController(RemoteMouseSink sink) {
        this.sink = sink;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    boolean isEnabled() {
        return state.enabledForCurrentStream;
    }

    boolean isInputSuppressed() {
        return state.inputSuppressed;
    }

    VirtualMouseState.Mode getMode() {
        return state.mode;
    }

    float getCursorNormalizedX() {
        return state.cursorNormalizedX;
    }

    float getCursorNormalizedY() {
        return state.cursorNormalizedY;
    }

    float getCompactCenterRatioX() {
        return state.compactCenterRatioX;
    }

    float getCompactCenterRatioY() {
        return state.compactCenterRatioY;
    }

    boolean isLeftPressed() {
        return state.leftPressed;
    }

    boolean isRightPressed() {
        return state.rightPressed;
    }

    VirtualMouseState.ScrollDirection getActiveScrollDirection() {
        return state.activeScrollDirection;
    }

    void enable() {
        cancelActiveInteractions();
        state.enabledForCurrentStream = true;
        state.mode = VirtualMouseState.Mode.COMPACT;
        notifyStateChanged();
    }

    void disable() {
        cancelActiveInteractions();
        state.enabledForCurrentStream = false;
        state.mode = VirtualMouseState.Mode.HIDDEN;
        notifyStateChanged();
    }

    void setInputSuppressed(boolean suppressed) {
        if (state.inputSuppressed == suppressed) {
            if (suppressed) {
                cancelActiveInteractions();
            }
            return;
        }

        if (suppressed) {
            cancelActiveInteractions();
        }
        state.inputSuppressed = suppressed;
        notifyStateChanged();
    }

    void expand() {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.COMPACT) {
            return;
        }
        state.mode = VirtualMouseState.Mode.EXPANDED;
        notifyStateChanged();
    }

    void collapse() {
        if (!state.enabledForCurrentStream) {
            return;
        }
        cancelActiveInteractions();
        state.mode = VirtualMouseState.Mode.COMPACT;
        notifyStateChanged();
    }

    void setCompactCenterRatios(float ratioX, float ratioY) {
        state.compactCenterRatioX = clamp(ratioX, 0f, 1f);
        state.compactCenterRatioY = clamp(ratioY, 0f, 1f);
    }

    void moveCursor(float deltaXPx, float deltaYPx, float videoWidthPx, float videoHeightPx) {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.EXPANDED) {
            return;
        }

        state.cursorNormalizedX = clamp(
                state.cursorNormalizedX + deltaXPx / Math.max(videoWidthPx, 1f), 0f, 1f);
        state.cursorNormalizedY = clamp(
                state.cursorNormalizedY + deltaYPx / Math.max(videoHeightPx, 1f), 0f, 1f);
        sendCurrentAbsolutePosition(videoWidthPx, videoHeightPx);
        notifyStateChanged();
    }

    void resendCurrentAbsolutePosition(float videoWidthPx, float videoHeightPx) {
        if (state.mode == VirtualMouseState.Mode.EXPANDED) {
            sendCurrentAbsolutePosition(videoWidthPx, videoHeightPx);
        }
    }

    void pressButton(RemoteMouseSink.Button button) {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.EXPANDED || isButtonPressed(button)) {
            return;
        }
        setButtonPressed(button, true);
        sink.sendButton(button, true);
        notifyStateChanged();
    }

    void releaseButton(RemoteMouseSink.Button button) {
        if (!isButtonPressed(button)) {
            return;
        }
        setButtonPressed(button, false);
        sink.sendButton(button, false);
        notifyStateChanged();
    }

    void clickMiddleButton() {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.EXPANDED) {
            return;
        }

        cancelPendingMiddleRelease();
        final int generation = ++middleClickGeneration;
        pressButton(RemoteMouseSink.Button.MIDDLE);
        pendingMiddleRelease = new Runnable() {
            @Override
            public void run() {
                if (generation != middleClickGeneration) {
                    return;
                }
                pendingMiddleRelease = null;
                releaseButton(RemoteMouseSink.Button.MIDDLE);
            }
        };
        handler.postDelayed(pendingMiddleRelease, MIDDLE_CLICK_DURATION_MS);
    }

    boolean beginDirectionalScroll() {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.EXPANDED) {
            return false;
        }
        cancelPendingMiddleRelease();
        state.mode = VirtualMouseState.Mode.DIRECTION_SCROLL;
        state.activeScrollDirection = VirtualMouseState.ScrollDirection.NONE;
        notifyStateChanged();
        return true;
    }

    void setScrollDirection(VirtualMouseState.ScrollDirection direction) {
        if (!canSendInput() || state.mode != VirtualMouseState.Mode.DIRECTION_SCROLL
                || direction == state.activeScrollDirection) {
            return;
        }

        handler.removeCallbacks(scrollRepeater);
        state.activeScrollDirection = direction;
        if (direction != VirtualMouseState.ScrollDirection.NONE) {
            sendScrollTick(direction);
            handler.postDelayed(scrollRepeater, SCROLL_REPEAT_INTERVAL_MS);
        }
        notifyStateChanged();
    }

    void endDirectionalScroll() {
        stopDirectionalScroll();
        if (state.enabledForCurrentStream && state.mode == VirtualMouseState.Mode.DIRECTION_SCROLL) {
            state.mode = VirtualMouseState.Mode.EXPANDED;
        }
        notifyStateChanged();
    }

    void cancelActiveInteractions() {
        cancelPendingMiddleRelease();
        releaseButton(RemoteMouseSink.Button.LEFT);
        releaseButton(RemoteMouseSink.Button.RIGHT);
        releaseButton(RemoteMouseSink.Button.MIDDLE);
        stopDirectionalScroll();
        if (state.mode == VirtualMouseState.Mode.DIRECTION_SCROLL) {
            state.mode = state.enabledForCurrentStream
                    ? VirtualMouseState.Mode.EXPANDED
                    : VirtualMouseState.Mode.HIDDEN;
        }
        notifyStateChanged();
    }

    void destroy() {
        disable();
        handler.removeCallbacksAndMessages(null);
        listener = null;
    }

    private boolean canSendInput() {
        return state.enabledForCurrentStream && !state.inputSuppressed;
    }

    private void sendCurrentAbsolutePosition(float videoWidthPx, float videoHeightPx) {
        if (!canSendInput()) {
            return;
        }
        float width = Math.max(videoWidthPx, 1f);
        float height = Math.max(videoHeightPx, 1f);
        sink.sendAbsolutePosition(state.cursorNormalizedX * width,
                state.cursorNormalizedY * height, width, height);
    }

    private void sendScrollTick(VirtualMouseState.ScrollDirection direction) {
        switch (direction) {
            case UP:
                sink.sendVerticalScroll(1);
                break;
            case DOWN:
                sink.sendVerticalScroll(-1);
                break;
            case LEFT:
                sink.sendHorizontalScroll(1);
                break;
            case RIGHT:
                sink.sendHorizontalScroll(-1);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void stopDirectionalScroll() {
        handler.removeCallbacks(scrollRepeater);
        state.activeScrollDirection = VirtualMouseState.ScrollDirection.NONE;
    }

    private void cancelPendingMiddleRelease() {
        middleClickGeneration++;
        if (pendingMiddleRelease != null) {
            handler.removeCallbacks(pendingMiddleRelease);
            pendingMiddleRelease = null;
        }
    }

    private boolean isButtonPressed(RemoteMouseSink.Button button) {
        switch (button) {
            case LEFT:
                return state.leftPressed;
            case RIGHT:
                return state.rightPressed;
            case MIDDLE:
                return state.middlePressed;
            default:
                return false;
        }
    }

    private void setButtonPressed(RemoteMouseSink.Button button, boolean pressed) {
        switch (button) {
            case LEFT:
                state.leftPressed = pressed;
                break;
            case RIGHT:
                state.rightPressed = pressed;
                break;
            case MIDDLE:
                state.middlePressed = pressed;
                break;
            default:
                break;
        }
    }

    private void notifyStateChanged() {
        if (listener != null) {
            listener.onVirtualMouseStateChanged();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
