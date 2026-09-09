package com.limelight.preferences;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class SettingsProfileStateTest {
    private final Set<String> keys = new HashSet<>(Arrays.asList("resolution", "bitrate", "hdr", "layout", "position"));

    @Test public void existingSettingsBecomeAAndNewProfilesStartEmpty() {
        Map<String, Object> values = new HashMap<>();
        values.put("resolution", "1920x1080");
        values.put("bitrate", 20000);
        assertEquals(0, SettingsProfileState.active(values));
        Map<String, Object> b = SettingsProfileState.switchTo(values, keys, 1);
        assertEquals(1, SettingsProfileState.active(b));
        assertFalse(b.containsKey("resolution"));
        Map<String, Object> a = SettingsProfileState.switchTo(b, keys, 0);
        assertEquals("1920x1080", a.get("resolution"));
        assertEquals(20000, a.get("bitrate"));
        assertFalse(values.containsKey(SettingsProfileState.ACTIVE));
    }

    @Test public void allFiveSlotsPreserveChangesIncludingRuntimeEdits() {
        Map<String, Object> values = new HashMap<>();
        for (int slot = 0; slot < 5; slot++) {
            values = SettingsProfileState.switchTo(values, keys, slot);
            values.put("bitrate", (slot + 1) * 10000);
            values.put("hdr", slot % 2 == 0);
            values.put("position", slot + 0.5f);
        }
        for (int slot = 0; slot < 5; slot++) {
            values = SettingsProfileState.switchTo(values, keys, slot);
            assertEquals((slot + 1) * 10000, values.get("bitrate"));
            assertEquals(slot % 2 == 0, values.get("hdr"));
            assertEquals(slot + 0.5f, values.get("position"));
        }
        values.put("bitrate", 123456); // A quick-menu write to the current live keys.
        values = SettingsProfileState.switchTo(values, keys, 0);
        values = SettingsProfileState.switchTo(values, keys, 4);
        assertEquals(123456, values.get("bitrate"));
    }

    @Test public void resetCurrentDoesNotChangeOtherSlotsOrUnrelatedData() {
        Map<String, Object> values = new HashMap<>();
        values.put("host_identity", "keep");
        values.put("log_count", 8L);
        values.put("bitrate", 20000);
        values = SettingsProfileState.switchTo(values, keys, 1);
        values.put("bitrate", 60000);
        values = SettingsProfileState.resetCurrent(values, keys);
        assertEquals(1, SettingsProfileState.active(values));
        assertFalse(values.containsKey("bitrate"));
        assertEquals("keep", values.get("host_identity"));
        assertEquals(8L, values.get("log_count"));
        values = SettingsProfileState.switchTo(values, keys, 0);
        assertEquals(20000, values.get("bitrate"));
        values = SettingsProfileState.switchTo(values, keys, 1);
        assertFalse(values.containsKey("bitrate"));
    }

    @Test public void deletedValueDoesNotReappearFromAnOldSnapshot() {
        Map<String, Object> values = new HashMap<>();
        values.put("hdr", true);
        values = SettingsProfileState.switchTo(values, keys, 1);
        values = SettingsProfileState.switchTo(values, keys, 0);
        values.remove("hdr");
        values = SettingsProfileState.switchTo(values, keys, 1);
        values = SettingsProfileState.switchTo(values, keys, 0);
        assertFalse(values.containsKey("hdr"));
    }

    @Test public void sameSlotIsANoOpAndInvalidSlotIsRejected() {
        Map<String, Object> values = new HashMap<>();
        values.put("bitrate", 12345);
        assertEquals(values, SettingsProfileState.switchTo(values, keys, 0));
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileState.switchTo(values, keys, -1));
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileState.switchTo(values, keys, 5));
    }

    @Test public void stringSetsAreCopiedAndTypesPreserved() {
        Set<String> layout = new HashSet<>(Arrays.asList("one", "two"));
        Map<String, Object> values = new HashMap<>();
        values.put("layout", layout);
        Map<String, Object> b = SettingsProfileState.switchTo(values, keys, 1);
        layout.clear();
        Map<String, Object> a = SettingsProfileState.switchTo(b, keys, 0);
        assertEquals(new HashSet<>(Arrays.asList("one", "two")), a.get("layout"));
    }
}
