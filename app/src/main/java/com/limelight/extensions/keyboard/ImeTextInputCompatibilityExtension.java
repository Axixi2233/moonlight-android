package com.limelight.extensions.keyboard;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

/**
 * EXTENSION DEVELOPMENT [EXT-IME-AT-SIGN] [ADDED]
 *
 * <p>Provides client-side compatibility mappings for text committed by an Android IME.</p>
 *
 * <p>Sunshine on Linux currently injects UTF-8 text through the Ctrl+Shift+U Unicode input
 * sequence. Some applications do not support that sequence, so an {@code @} received as UTF-8
 * can leave a visible "U+" composition instead of entering the character. Keep this workaround
 * isolated so it is easy to identify, review, or remove when Sunshine issue #5274 is resolved.</p>
 *
 * <p>The mapping below assumes a US/QWERTY host layout, matching Moonlight's normalized keyboard
 * protocol mapping: {@code @} is emitted as Shift+2. Other text continues through the existing
 * UTF-8 path unchanged.</p>
 */
public final class ImeTextInputCompatibilityExtension {
    public interface KeyStrokeDispatcher {
        boolean dispatchKeyStroke(int androidKeyCode, byte requiredModifiers);
    }

    private ImeTextInputCompatibilityExtension() {
    }

    /**
     * Attempts to replace a known-problematic single-character IME text commit with a key stroke.
     *
     * @return {@code true} only when the text was successfully dispatched as a key stroke
     */
    public static boolean dispatchIfCompatible(String text, KeyStrokeDispatcher dispatcher) {
        if (!"@".equals(text)) {
            return false;
        }

        return dispatcher.dispatchKeyStroke(
                KeyEvent.KEYCODE_2,
                KeyboardPacket.MODIFIER_SHIFT);
    }
}
