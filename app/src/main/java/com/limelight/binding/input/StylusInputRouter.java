package com.limelight.binding.input;

/** Keeps each pen contact/hover on one transport until it ends or is cancelled. */
public final class StylusInputRouter {
    public static final int FOLLOW_TOUCH = 0;
    public static final int NATIVE_PEN = 1;
    public static final int MOUSE = 2;
    public enum Action { DOWN, MOVE, UP, CANCEL, HOVER, LEAVE, BUTTON }
    public enum TouchMode { DISABLED, ABSOLUTE, TRACKPAD, MULTITOUCH }
    public enum Transport { PEN, MOUSE, TOUCH_SCREEN, TOUCH_CONTEXT }
    public interface Sink {
        /** False means the host does not support this transport. */
        boolean send(Transport transport, Action action);
    }

    private Transport contact;
    private Transport hover;

    public static int normalizeMode(int mode) {
        return mode >= FOLLOW_TOUCH && mode <= MOUSE ? mode : NATIVE_PEN;
    }

    private Transport select(int mode, TouchMode touch, boolean hovering) {
        if (mode == MOUSE) return Transport.MOUSE;
        if (mode != FOLLOW_TOUCH || touch == TouchMode.DISABLED) return Transport.PEN;
        if (hovering) return touch == TouchMode.ABSOLUTE ? Transport.MOUSE : null;
        return touch == TouchMode.MULTITOUCH ? Transport.TOUCH_SCREEN : Transport.TOUCH_CONTEXT;
    }

    public void handle(Action action, int mode, TouchMode touch, Sink sink) {
        if (action == Action.CANCEL) {
            cancel(sink);
        } else if (action == Action.DOWN) {
            endHover(sink);
            if (contact != null) sink.send(contact, Action.CANCEL);
            contact = select(mode, touch, false);
            if (!sink.send(contact, action) &&
                    (contact == Transport.PEN || contact == Transport.TOUCH_SCREEN)) {
                contact = Transport.MOUSE;
                sink.send(contact, action);
            }
        } else if (action == Action.MOVE || action == Action.UP) {
            if (contact != null) {
                sink.send(contact, action);
                if (action == Action.UP) contact = null;
            }
        } else if (action == Action.LEAVE) {
            endHover(sink);
        } else if (action == Action.BUTTON && contact != null) {
            sink.send(contact, action);
        } else if (contact == null) {
            if (hover == null) hover = select(mode, touch, true);
            if (hover != null && !sink.send(hover, action) && hover == Transport.PEN) {
                hover = Transport.MOUSE;
                sink.send(hover, action);
            }
        }
    }

    private void endHover(Sink sink) {
        if (hover != null) sink.send(hover, Action.LEAVE);
        hover = null;
    }

    public void cancel(Sink sink) {
        if (contact != null) sink.send(contact, Action.CANCEL);
        if (hover != null) sink.send(hover, Action.CANCEL);
        contact = null;
        hover = null;
    }
}
