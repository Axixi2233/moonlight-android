package com.limelight.preferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Computes one atomic preferences update; active values remain at the existing runtime keys. */
final class SettingsProfileState {
    static final int COUNT = 5;
    static final String ACTIVE = "settings_profiles_v1.active";
    private static final String PREFIX = "settings_profiles_v1.slot.";

    static int active(Map<String, ?> values) {
        Object value = values.get(ACTIVE);
        return value instanceof Integer && (int) value >= 0 && (int) value < COUNT ? (int) value : 0;
    }

    static Map<String, Object> switchTo(Map<String, ?> values, Set<String> keys, int target) {
        if (target < 0 || target >= COUNT) throw new IllegalArgumentException("Unknown profile");
        Map<String, Object> result = copy(values);
        int current = active(values);
        if (current == target) return result;
        String outgoing = prefix(current);
        String incoming = prefix(target);
        // Read the destination first, then save the current live values. Changes made in
        // the in-game quick menu are included without altering its persistence logic.
        for (String key : keys) {
            result.remove(outgoing + key);
            if (values.containsKey(key)) result.put(outgoing + key, copyValue(values.get(key)));
            result.remove(key);
            if (values.containsKey(incoming + key)) result.put(key, copyValue(values.get(incoming + key)));
        }
        result.put(ACTIVE, target);
        return result;
    }

    static Map<String, Object> resetCurrent(Map<String, ?> values, Set<String> keys) {
        Map<String, Object> result = copy(values);
        String current = prefix(active(values));
        for (String key : keys) {
            result.remove(key);
            result.remove(current + key);
        }
        // Missing values intentionally use the same XML/device defaults as a fresh setup.
        return result;
    }

    private static String prefix(int index) { return PREFIX + index + "."; }

    private static Map<String, Object> copy(Map<String, ?> values) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) result.put(entry.getKey(), copyValue(entry.getValue()));
        return result;
    }

    private static Object copyValue(Object value) {
        return value instanceof Set ? new HashSet<>((Set<?>) value) : value;
    }
}
