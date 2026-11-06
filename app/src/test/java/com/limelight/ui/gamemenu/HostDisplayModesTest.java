package com.limelight.ui.gamemenu;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Exercises display-mode choices independently of Android and a connected PC. */
public class HostDisplayModesTest {
    private final List<HostDisplayModes.Mode> modes = Arrays.asList(
            new HostDisplayModes.Mode(1920, 1080, 60),
            new HostDisplayModes.Mode(3840, 2160, 60),
            new HostDisplayModes.Mode(1920, 1080, 144),
            new HostDisplayModes.Mode(3840, 2160, 120),
            new HostDisplayModes.Mode(1920, 1080, 120),
            new HostDisplayModes.Mode(1920, 1080, 144));

    @Test public void resolutionsAreUniqueAndLargestFirst() {
        List<HostDisplayModes.Mode> choices = HostDisplayModes.resolutions(modes, 120);
        assertEquals(2, choices.size());
        assertEquals(3840, choices.get(0).width);
        assertEquals(1920, choices.get(1).width);
        assertEquals(120, choices.get(0).hz);
        assertEquals(120, choices.get(1).hz);
    }

    @Test public void refreshRatesAreFilteredUniqueAndDescending() {
        List<HostDisplayModes.Mode> choices = HostDisplayModes.refreshRates(modes, 1920, 1080);
        assertEquals(3, choices.size());
        assertEquals(144, choices.get(0).hz);
        assertEquals(120, choices.get(1).hz);
        assertEquals(60, choices.get(2).hz);
    }

    @Test public void unsupportedRefreshFallsBackWithoutExceedingThePreviousRate() {
        assertEquals(120, HostDisplayModes.resolutions(modes, 144).get(0).hz);
        assertEquals(60, HostDisplayModes.resolutions(modes, 90).get(0).hz);
        assertEquals(60, HostDisplayModes.resolutions(modes, 30).get(0).hz);
    }

    @Test public void equalAreaResolutionsHaveDeterministicOrder() {
        List<HostDisplayModes.Mode> choices = HostDisplayModes.resolutions(Arrays.asList(
                new HostDisplayModes.Mode(1080, 1920, 60),
                new HostDisplayModes.Mode(1920, 1080, 60)), 60);
        assertEquals(1920, choices.get(0).width);
    }

    @Test public void absentModesDoNotInventASetting() {
        assertTrue(HostDisplayModes.resolutions(Collections.emptyList(), 60).isEmpty());
        assertTrue(HostDisplayModes.refreshRates(modes, 1280, 720).isEmpty());
    }
}
