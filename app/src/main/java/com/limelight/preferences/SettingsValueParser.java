package com.limelight.preferences;

import java.math.BigDecimal;
import java.util.Locale;

/** Validate text before values reach stream configuration or persistent storage. */
final class SettingsValueParser {
    static int parseBitrateMbps(String text) {
        return parseNumber(text, 1000, 500, 99999000);
    }

    static int parseNumber(String text, int divisor, int min, int max) {
        try {
            int value = new BigDecimal(text.trim().replace(',', '.'))
                    .multiply(BigDecimal.valueOf(divisor)).intValueExact();
            if (value < min || value > max) throw new IllegalArgumentException("Out of range");
            return value;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid precision or overflow", e);
        }
    }

    static String parseResolution(String text) {
        String[] dimensions = text.toLowerCase(Locale.ROOT).replace('×', 'x').split("x", -1);
        if (dimensions.length != 2) throw new IllegalArgumentException("Expected width x height");
        int width = Integer.parseInt(dimensions[0].trim());
        int height = Integer.parseInt(dimensions[1].trim());
        if (width < 2 || width > 16384 || height < 2 || height > 16384 || width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        return width + "x" + height;
    }

    private SettingsValueParser() {}
}
