package com.limelight.extensions.keyboard;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * EXTENSION DEVELOPMENT [EXT-IME-AT-SIGN] [ADDED]
 * Coverage for the Sunshine/Linux IME '@' compatibility mapping.
 */
public class ImeTextInputCompatibilityExtensionTest {
    @Test
    public void atSignDispatchesAsShiftTwo() {
        AtomicInteger dispatchedKeyCode = new AtomicInteger(KeyEvent.KEYCODE_UNKNOWN);
        AtomicInteger dispatchedModifiers = new AtomicInteger();

        boolean handled = ImeTextInputCompatibilityExtension.dispatchIfCompatible(
                "@",
                (keyCode, modifiers) -> {
                    dispatchedKeyCode.set(keyCode);
                    dispatchedModifiers.set(modifiers);
                    return true;
                });

        assertTrue(handled);
        assertEquals(KeyEvent.KEYCODE_2, dispatchedKeyCode.get());
        assertEquals(KeyboardPacket.MODIFIER_SHIFT, dispatchedModifiers.get());
    }

    @Test
    public void unrelatedTextRemainsOnExistingUtf8Path() {
        AtomicInteger dispatchCount = new AtomicInteger();

        boolean handled = ImeTextInputCompatibilityExtension.dispatchIfCompatible(
                "example",
                (keyCode, modifiers) -> {
                    dispatchCount.incrementAndGet();
                    return true;
                });

        assertFalse(handled);
        assertEquals(0, dispatchCount.get());
    }

    @Test
    public void atSignFallsBackWhenKeyStrokeCannotBeDispatched() {
        boolean handled = ImeTextInputCompatibilityExtension.dispatchIfCompatible(
                "@",
                (keyCode, modifiers) -> false);

        assertFalse(handled);
    }

    @Test
    public void multiCharacterCommitIsNotPartiallyRewritten() {
        AtomicInteger dispatchCount = new AtomicInteger();

        boolean handled = ImeTextInputCompatibilityExtension.dispatchIfCompatible(
                "name@example.com",
                (keyCode, modifiers) -> {
                    dispatchCount.incrementAndGet();
                    return true;
                });

        assertFalse(handled);
        assertEquals(0, dispatchCount.get());
    }
}
