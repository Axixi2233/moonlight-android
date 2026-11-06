package com.limelight.ui.gamemenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Builds supported resolution/refresh choices without inventing display modes. */
final class HostDisplayModes {
    /** One driver-advertised mode; refresh is the integer value accepted by the host. */
    static final class Mode {
        final int width;
        final int height;
        final int hz;

        Mode(int width, int height, int hz) {
            this.width = width;
            this.height = height;
            this.hz = hz;
        }
    }

    /** Deduplicate resolutions, keep the old refresh if possible, and sort by area descending. */
    static List<Mode> resolutions(List<Mode> modes, int currentHz) {
        TreeMap<String, Mode> unique = new TreeMap<>();
        for (Mode mode : modes) {
            String key = mode.width + "x" + mode.height;
            Mode previous = unique.get(key);
            if (previous == null || betterRefresh(mode.hz, previous.hz, currentHz)) {
                unique.put(key, mode);
            }
        }
        List<Mode> result = new ArrayList<>(unique.values());
        result.sort(Comparator.<Mode>comparingLong(mode -> (long) mode.width * mode.height)
                .thenComparingInt(mode -> mode.width).thenComparingInt(mode -> mode.height).reversed());
        return result;
    }

    /** List only refresh rates advertised for this resolution, highest first. */
    static List<Mode> refreshRates(List<Mode> modes, int width, int height) {
        TreeMap<Integer, Mode> rates = new TreeMap<>(Comparator.reverseOrder());
        for (Mode mode : modes) {
            if (mode.width == width && mode.height == height) {
                rates.put(mode.hz, mode);
            }
        }
        return new ArrayList<>(rates.values());
    }

    /** Prefer the highest supported rate at or below the old rate; otherwise use the lowest. */
    private static boolean betterRefresh(int candidate, int previous, int current) {
        if (candidate <= current) {
            return previous > current || candidate > previous;
        }
        return previous > current && candidate < previous;
    }
}
