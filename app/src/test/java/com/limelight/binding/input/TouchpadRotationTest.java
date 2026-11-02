package com.limelight.binding.input;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TouchpadRotationTest {
    @Test public void correctsBothAxesWithoutLosingFractionalMovement() {
        float[][] expected = {{2.5f, -4f}, {-4f, -2.5f}, {-2.5f, 4f}, {4f, 2.5f}};
        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(expected[rotation][0], TouchpadRotation.x(2.5f, -4f, rotation), 0f);
            assertEquals(expected[rotation][1], TouchpadRotation.y(2.5f, -4f, rotation), 0f);
        }
    }
    @Test public void invalidPreferencesKeepOriginalDirection() {
        assertEquals(0, TouchpadRotation.normalize(-1));
        assertEquals(0, TouchpadRotation.normalize(4));
        assertEquals(3f, TouchpadRotation.x(3f, 7f, 99), 0f);
        assertEquals(7f, TouchpadRotation.y(3f, 7f, 99), 0f);
    }
}
