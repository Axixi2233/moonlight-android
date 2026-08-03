package com.limelight.stereo3d;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class Stereo3dOutputLayoutTest {
    @Test
    public void fullAndHalf1080HaveSameViewedEyeAspect() {
        float full = Stereo3dOutputLayout.calculateEyeAspect(
                3840, 1080, Stereo3dOutputLayout.FULL_SBS);
        float half = Stereo3dOutputLayout.calculateEyeAspect(
                1920, 1080, Stereo3dOutputLayout.HALF_SBS);

        assertEquals(1920, Stereo3dOutputLayout.calculateViewedEyeWidth(
                3840, Stereo3dOutputLayout.FULL_SBS));
        assertEquals(1920, Stereo3dOutputLayout.calculateViewedEyeWidth(
                1920, Stereo3dOutputLayout.HALF_SBS));
        assertEquals(16f / 9f, full, 0.0001f);
        assertEquals(full, half, 0.0001f);
    }

    @Test
    public void fullAndHalf1200HaveSameViewedEyeAspect() {
        float full = Stereo3dOutputLayout.calculateEyeAspect(
                3840, 1200, Stereo3dOutputLayout.FULL_SBS);
        float half = Stereo3dOutputLayout.calculateEyeAspect(
                1920, 1200, Stereo3dOutputLayout.HALF_SBS);

        assertEquals(1920, Stereo3dOutputLayout.calculateViewedEyeWidth(
                3840, Stereo3dOutputLayout.FULL_SBS));
        assertEquals(1920, Stereo3dOutputLayout.calculateViewedEyeWidth(
                1920, Stereo3dOutputLayout.HALF_SBS));
        assertEquals(1.6f, full, 0.0001f);
        assertEquals(full, half, 0.0001f);
    }
}
