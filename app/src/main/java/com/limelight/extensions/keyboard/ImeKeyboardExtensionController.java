package com.limelight.extensions.keyboard;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.limelight.R;

/**
 * EXTENSION DEVELOPMENT [EXT-IME-ACCESSORY-BAR] [ADDED]
 *
 * <p>Adds PC shortcut keys immediately above a docked Android IME.</p>
 *
 * <p>This controller is intentionally self-contained. It mounts its own view at runtime,
 * observes IME visibility without changing the stream layout, and reports Android key codes
 * through a small callback so the existing input pipeline remains untouched.</p>
 */
public final class ImeKeyboardExtensionController {
    public interface KeyDispatcher {
        void dispatchKey(int keyCode, boolean down);
    }

    private static final int MIN_DOCKED_IME_HEIGHT_DP = 100;

    private final Activity activity;
    private final FrameLayout host;
    private final KeyDispatcher keyDispatcher;
    private final View keyboardBar;
    private final Rect visibleDisplayFrame = new Rect();
    private final SparseArray<View> momentaryKeyViews = new SparseArray<>();
    private final SparseBooleanArray momentaryKeyStates = new SparseBooleanArray();
    private final int minimumDockedImeHeightPx;
    private final int originalSoftInputMode;

    private final SparseArray<View> latchedKeyViews = new SparseArray<>();
    private final SparseBooleanArray latchedKeyStates = new SparseBooleanArray();
    private boolean destroyed;
    private boolean lastVisible;
    private int lastBottomMargin = Integer.MIN_VALUE;

    private final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener =
            this::updateKeyboardBarPosition;

    public ImeKeyboardExtensionController(Activity activity, FrameLayout host,
                                          KeyDispatcher keyDispatcher) {
        this.activity = activity;
        this.host = host;
        this.keyDispatcher = keyDispatcher;
        this.minimumDockedImeHeightPx = Math.round(
                MIN_DOCKED_IME_HEIGHT_DP * activity.getResources().getDisplayMetrics().density);
        this.originalSoftInputMode = activity.getWindow().getAttributes().softInputMode;

        keyboardBar = LayoutInflater.from(activity).inflate(
                R.layout.extension_ime_keyboard_bar, host, false);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.BOTTOM;
        keyboardBar.setLayoutParams(layoutParams);
        keyboardBar.setVisibility(View.GONE);

        bindMomentaryKey(R.id.extension_key_escape, KeyEvent.KEYCODE_ESCAPE);
        bindMomentaryKey(R.id.extension_key_tab, KeyEvent.KEYCODE_TAB);
        bindMomentaryKey(R.id.extension_key_unknown, KeyEvent.KEYCODE_UNKNOWN);
        bindMomentaryKey(R.id.extension_key_home, KeyEvent.KEYCODE_MOVE_HOME);
        bindMomentaryKey(R.id.extension_key_up, KeyEvent.KEYCODE_DPAD_UP);
        bindMomentaryKey(R.id.extension_key_end, KeyEvent.KEYCODE_MOVE_END);
        bindMomentaryKey(R.id.extension_key_page_up, KeyEvent.KEYCODE_PAGE_UP);

        bindLatchedKey(R.id.extension_key_cmd, KeyEvent.KEYCODE_META_LEFT);
        bindLatchedKey(R.id.extension_key_ctrl, KeyEvent.KEYCODE_CTRL_LEFT);
        bindLatchedKey(R.id.extension_key_alt, KeyEvent.KEYCODE_ALT_LEFT);
        bindMomentaryKey(R.id.extension_key_left, KeyEvent.KEYCODE_DPAD_LEFT);
        bindMomentaryKey(R.id.extension_key_down, KeyEvent.KEYCODE_DPAD_DOWN);
        bindMomentaryKey(R.id.extension_key_right, KeyEvent.KEYCODE_DPAD_RIGHT);
        bindMomentaryKey(R.id.extension_key_page_down, KeyEvent.KEYCODE_PAGE_DOWN);

        host.addView(keyboardBar);
        host.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        int resizeSoftInputMode = (originalSoftInputMode
                & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        activity.getWindow().setSoftInputMode(resizeSoftInputMode);

        host.post(this::updateKeyboardBarPosition);
    }

    private void bindMomentaryKey(int viewId, int keyCode) {
        View keyView = keyboardBar.findViewById(viewId);
        momentaryKeyViews.put(keyCode, keyView);
        keyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!momentaryKeyStates.get(keyCode)) {
                            momentaryKeyStates.put(keyCode, true);
                            view.setSelected(true);
                            keyDispatcher.dispatchKey(keyCode, true);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (momentaryKeyStates.get(keyCode)) {
                            momentaryKeyStates.delete(keyCode);
                            view.setSelected(false);
                            keyDispatcher.dispatchKey(keyCode, false);
                        }
                        view.performClick();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        if (momentaryKeyStates.get(keyCode)) {
                            momentaryKeyStates.delete(keyCode);
                            view.setSelected(false);
                            keyDispatcher.dispatchKey(keyCode, false);
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    private void bindLatchedKey(int viewId, int keyCode) {
        View keyView = keyboardBar.findViewById(viewId);
        latchedKeyViews.put(keyCode, keyView);
        keyView.setOnClickListener(view -> {
            boolean latched = !latchedKeyStates.get(keyCode);
            if (latched) {
                latchedKeyStates.put(keyCode, true);
            }
            else {
                latchedKeyStates.delete(keyCode);
            }

            view.setSelected(latched);
            keyDispatcher.dispatchKey(keyCode, latched);
        });
    }

    private void updateKeyboardBarPosition() {
        if (destroyed || !host.isAttachedToWindow()) {
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        decorView.getWindowVisibleDisplayFrame(visibleDisplayFrame);

        int[] decorLocation = new int[2];
        int[] hostLocation = new int[2];
        decorView.getLocationOnScreen(decorLocation);
        host.getLocationOnScreen(hostLocation);

        int decorBottom = decorLocation[1] + decorView.getHeight();
        int hostBottom = hostLocation[1] + host.getHeight();
        int keyboardTop = visibleDisplayFrame.bottom;
        int obscuredHeight = Math.max(0, decorBottom - keyboardTop);

        boolean imeVisible = obscuredHeight >= minimumDockedImeHeightPx;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = host.getRootWindowInsets();
            if (insets != null) {
                int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
                // A floating or split IME does not expose a usable bottom edge. In that case,
                // leave this docked accessory hidden instead of placing it at the wrong edge.
                imeVisible = insets.isVisible(WindowInsets.Type.ime())
                        && imeHeight >= minimumDockedImeHeightPx;
                if (imeVisible) {
                    // WindowMetrics retains the full activity bounds even when adjustResize has
                    // already shortened the content view. This avoids applying the IME height
                    // twice on devices that honor adjustResize in fullscreen mode.
                    Rect windowBounds = activity.getWindowManager()
                            .getCurrentWindowMetrics().getBounds();
                    keyboardTop = windowBounds.bottom - imeHeight;
                }
            }
        }

        int bottomMargin = imeVisible ? Math.max(0, hostBottom - keyboardTop) : 0;
        applyVisibilityAndMargin(imeVisible, bottomMargin);
    }

    private void applyVisibilityAndMargin(boolean visible, int bottomMargin) {
        if (visible != lastVisible) {
            lastVisible = visible;
            keyboardBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                releasePressedKeys();
            }
        }

        if (visible && bottomMargin != lastBottomMargin) {
            lastBottomMargin = bottomMargin;
            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) keyboardBar.getLayoutParams();
            layoutParams.bottomMargin = bottomMargin;
            keyboardBar.setLayoutParams(layoutParams);
        }
    }

    private void releaseLatchedModifiers() {
        for (int index = latchedKeyStates.size() - 1; index >= 0; index--) {
            int keyCode = latchedKeyStates.keyAt(index);
            View keyView = latchedKeyViews.get(keyCode);
            if (keyView != null) {
                keyView.setSelected(false);
            }
            keyDispatcher.dispatchKey(keyCode, false);
        }
        latchedKeyStates.clear();
    }

    public void releasePressedKeys() {
        for (int index = momentaryKeyStates.size() - 1; index >= 0; index--) {
            int keyCode = momentaryKeyStates.keyAt(index);
            View keyView = momentaryKeyViews.get(keyCode);
            if (keyView != null) {
                keyView.setSelected(false);
            }
            keyDispatcher.dispatchKey(keyCode, false);
        }
        momentaryKeyStates.clear();
        releaseLatchedModifiers();
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;

        releasePressedKeys();
        ViewTreeObserver observer = host.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(globalLayoutListener);
        }
        host.removeView(keyboardBar);
        activity.getWindow().setSoftInputMode(originalSoftInputMode);
    }
}
