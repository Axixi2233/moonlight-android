package com.limelight.ui.gamemenu;

/** Tracks a requested capture switch until the host reports the actual new capture target. */
final class HostDisplayCaptureState {
    enum Result { IDLE, WAITING, COMPLETE, UNAVAILABLE, TIMED_OUT }

    private String pendingId = "";
    private long requestedAt;

    /** Ignore repeated clicks and screens which are inactive or already being captured. */
    boolean request(String id, boolean active, boolean capturing, long now) {
        if (id.isEmpty() || !active || capturing || isPending()) return false;
        pendingId = id;
        requestedAt = now;
        return true;
    }

    /** An accepted HTTP request is insufficient: completion requires the captured device ID. */
    Result observe(String capturedId, boolean targetActive, long now) {
        if (!isPending()) return Result.IDLE;
        if (pendingId.equals(capturedId)) {
            failed();
            return Result.COMPLETE;
        }
        if (!targetActive) {
            failed();
            return Result.UNAVAILABLE;
        }
        if (now - requestedAt >= 10000) {
            failed();
            return Result.TIMED_OUT;
        }
        return Result.WAITING;
    }

    boolean isPending() {
        return !pendingId.isEmpty();
    }

    String pendingId() {
        return pendingId;
    }

    /** Release the pending target on an API error, disconnection or completion. */
    void failed() {
        pendingId = "";
    }
}
