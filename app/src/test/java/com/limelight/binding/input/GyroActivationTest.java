package com.limelight.binding.input;
import org.junit.Test;
import static org.junit.Assert.*;

public class GyroActivationTest {
    @Test public void localMappingSurvivesHostDisableAndRestoresHostRateWhenDisabled() {
        assertEquals(100, GyroActivation.reportRate((short) 0, true));
        assertEquals(100, GyroActivation.reportRate((short) 50, true));
        assertEquals(150, GyroActivation.reportRate((short) 150, true));
        assertEquals(0, GyroActivation.reportRate((short) 0, false));
        assertEquals(50, GyroActivation.reportRate((short) 50, false));
        assertEquals(200, GyroActivation.reportRate((short) 500, true));
    }
    @Test public void onlySelectedTriggerActivatesGyro() {
        assertTrue(GyroActivation.isActive(0, (short)0, (short)0));
        assertFalse(GyroActivation.isActive(1, (short)199, (short)255));
        assertTrue(GyroActivation.isActive(1, (short)200, (short)0));
        assertFalse(GyroActivation.isActive(2, (short)255, (short)199));
        assertTrue(GyroActivation.isActive(2, (short)0, (short)200));
        assertTrue(GyroActivation.isActive(2, (short)-1, (short)-1));
        assertFalse(GyroActivation.isActive(2, (short)255, (short)0));
    }
    @Test public void invalidModeDefaultsToAlways() {
        assertEquals(0, GyroActivation.normalize(-1));
        assertEquals(0, GyroActivation.normalize(3));
    }
}
