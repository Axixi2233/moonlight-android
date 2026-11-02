package com.limelight.binding.input;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static com.limelight.binding.input.StylusInputRouter.*;

public class StylusInputRouterTest {
    private final StylusInputRouter router = new StylusInputRouter();
    private final List<String> events = new ArrayList<>();
    private boolean penSupported = true;
    private final Sink sink = (transport, action) -> {
        events.add(transport + ":" + action);
        return transport != Transport.PEN || penSupported;
    };

    private void send(Action action, int mode, TouchMode touch) {
        router.handle(action, mode, touch, sink);
    }

    @Test public void unsupportedPenFallsBackForWholeContact() {
        penSupported = false;
        send(Action.DOWN, NATIVE_PEN, TouchMode.TRACKPAD);
        penSupported = true;
        send(Action.MOVE, NATIVE_PEN, TouchMode.TRACKPAD);
        send(Action.UP, NATIVE_PEN, TouchMode.TRACKPAD);
        assertEquals("[PEN:DOWN, MOUSE:DOWN, MOUSE:MOVE, MOUSE:UP]", events.toString());
    }

    @Test public void forcedMouseNeverSendsPen() {
        send(Action.HOVER, MOUSE, TouchMode.MULTITOUCH);
        send(Action.DOWN, MOUSE, TouchMode.MULTITOUCH);
        router.cancel(sink);
        send(Action.UP, MOUSE, TouchMode.MULTITOUCH);
        assertEquals("[MOUSE:HOVER, MOUSE:LEAVE, MOUSE:DOWN, MOUSE:CANCEL]", events.toString());
    }

    @Test public void contactEndsHoverBeforeDown() {
        send(Action.HOVER, NATIVE_PEN, TouchMode.DISABLED);
        send(Action.DOWN, NATIVE_PEN, TouchMode.DISABLED);
        send(Action.HOVER, NATIVE_PEN, TouchMode.DISABLED);
        assertEquals("[PEN:HOVER, PEN:LEAVE, PEN:DOWN]", events.toString());
    }

    @Test public void followTouchRoutesContactsAndSuppressesTrackpadHover() {
        send(Action.HOVER, FOLLOW_TOUCH, TouchMode.TRACKPAD);
        send(Action.DOWN, FOLLOW_TOUCH, TouchMode.TRACKPAD);
        send(Action.UP, FOLLOW_TOUCH, TouchMode.TRACKPAD);
        send(Action.DOWN, FOLLOW_TOUCH, TouchMode.MULTITOUCH);
        router.cancel(sink);
        send(Action.DOWN, FOLLOW_TOUCH, TouchMode.DISABLED);
        assertEquals("[TOUCH_CONTEXT:DOWN, TOUCH_CONTEXT:UP, TOUCH_SCREEN:DOWN, TOUCH_SCREEN:CANCEL, PEN:DOWN]", events.toString());
    }

    @Test public void cancelledContactDoesNotRestartUntilDown() {
        send(Action.DOWN, NATIVE_PEN, TouchMode.ABSOLUTE);
        router.cancel(sink);
        send(Action.MOVE, MOUSE, TouchMode.ABSOLUTE);
        send(Action.UP, MOUSE, TouchMode.ABSOLUTE);
        send(Action.DOWN, MOUSE, TouchMode.ABSOLUTE);
        assertEquals("[PEN:DOWN, PEN:CANCEL, MOUSE:DOWN]", events.toString());
    }

    @Test public void modeChangesCannotSplitAnActiveStroke() {
        send(Action.DOWN, NATIVE_PEN, TouchMode.ABSOLUTE);
        send(Action.UP, MOUSE, TouchMode.ABSOLUTE);
        assertEquals("[PEN:DOWN, PEN:UP]", events.toString());
    }

    @Test public void unsupportedHoverStaysMouseUntilLeave() {
        penSupported = false;
        send(Action.HOVER, NATIVE_PEN, TouchMode.ABSOLUTE);
        penSupported = true;
        send(Action.HOVER, NATIVE_PEN, TouchMode.ABSOLUTE);
        send(Action.LEAVE, NATIVE_PEN, TouchMode.ABSOLUTE);
        send(Action.DOWN, NATIVE_PEN, TouchMode.ABSOLUTE);
        assertEquals("[PEN:HOVER, MOUSE:HOVER, MOUSE:HOVER, MOUSE:LEAVE, PEN:DOWN]", events.toString());
    }

    @Test public void sideButtonDoesNotEndContactAndCancelIsIdempotent() {
        send(Action.DOWN, MOUSE, TouchMode.ABSOLUTE);
        send(Action.BUTTON, MOUSE, TouchMode.ABSOLUTE);
        router.cancel(sink);
        router.cancel(sink);
        assertEquals("[MOUSE:DOWN, MOUSE:BUTTON, MOUSE:CANCEL]", events.toString());
    }

    @Test public void invalidSavedModeUsesNativePen() {
        assertEquals(NATIVE_PEN, normalizeMode(-1));
        assertEquals(NATIVE_PEN, normalizeMode(3));
        assertEquals(FOLLOW_TOUCH, normalizeMode(FOLLOW_TOUCH));
        assertEquals(MOUSE, normalizeMode(MOUSE));
    }
}
