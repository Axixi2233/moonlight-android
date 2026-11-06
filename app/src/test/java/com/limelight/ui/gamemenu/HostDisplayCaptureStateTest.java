package com.limelight.ui.gamemenu;

import org.junit.Test;
import static org.junit.Assert.*;

/** Verify asynchronous capture acknowledgements without requiring two physical monitors. */
public class HostDisplayCaptureStateTest {
    @Test public void waitsForActualCaptureInsteadOfTreatingAcceptanceAsCompletion() {
        HostDisplayCaptureState state = new HostDisplayCaptureState();
        assertTrue(state.request("second", true, false, 100));
        assertEquals(HostDisplayCaptureState.Result.WAITING, state.observe("first", true, 500));
        assertEquals("second", state.pendingId());
        assertEquals(HostDisplayCaptureState.Result.COMPLETE, state.observe("second", true, 600));
        assertFalse(state.isPending());
        assertEquals(HostDisplayCaptureState.Result.IDLE, state.observe("second", true, 700));
    }

    @Test public void ignoresInactiveOrAlreadyCapturedMonitorsAndRepeatedClicks() {
        HostDisplayCaptureState state = new HostDisplayCaptureState();
        assertFalse(state.request("", true, false, 100));
        assertFalse(state.request("offline", false, false, 100));
        assertFalse(state.request("first", true, true, 100));
        assertTrue(state.request("second", true, false, 100));
        assertFalse(state.request("third", true, false, 101));
        assertEquals("second", state.pendingId());
    }

    @Test public void releasesThePendingTargetAfterFailureSoTheUserCanRetry() {
        HostDisplayCaptureState state = new HostDisplayCaptureState();
        assertTrue(state.request("second", true, false, 100));
        state.failed();
        assertFalse(state.isPending());
        assertTrue(state.request("second", true, false, 200));
    }

    @Test public void detectsHotUnplugWhileTheHostIsSwitching() {
        HostDisplayCaptureState state = new HostDisplayCaptureState();
        state.request("second", true, false, 100);
        assertEquals(HostDisplayCaptureState.Result.UNAVAILABLE, state.observe("first", false, 200));
        assertFalse(state.isPending());
    }

    @Test public void timesOutWithoutClaimingTheTargetWasCaptured() {
        HostDisplayCaptureState state = new HostDisplayCaptureState();
        state.request("second", true, false, 100);
        assertEquals(HostDisplayCaptureState.Result.WAITING, state.observe("first", true, 10099));
        assertEquals(HostDisplayCaptureState.Result.TIMED_OUT, state.observe("first", true, 10100));
        assertFalse(state.isPending());
        assertTrue(state.request("second", true, false, 10200));
    }
}
