package com.limelight.ui.virtualmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.limelight.R;

/**
 * Full-screen, draw-only virtual mouse overlay. Empty space returns false on ACTION_DOWN so the
 * existing stream touch and on-screen-controller paths continue to receive their own gestures.
 */
public final class VirtualMouseOverlay extends View implements VirtualMouseController.Listener {
    public interface PresentationHost {
        void getBaseVideoRect(RectF outRect);

        void setVirtualMouseVideoOffset(float offsetXPx, float offsetYPx);
    }

    private static final int TARGET_NONE = 0;
    private static final int TARGET_COMPACT = 1;
    private static final int TARGET_LEFT_BUTTON = 2;
    private static final int TARGET_RIGHT_BUTTON = 3;
    private static final int TARGET_WHEEL = 4;
    private static final int TARGET_POINTER_AREA = 5;
    private static final int TARGET_CLOSE = 6;

    private static final long WHEEL_LONG_PRESS_MS = 350L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointerPath = new Path();
    private final Path iconPath = new Path();

    private final RectF baseVideoRect = new RectF();
    private final RectF compactRect = new RectF();
    private final RectF panelRect = new RectF();
    private final RectF leftButtonRect = new RectF();
    private final RectF rightButtonRect = new RectF();
    private final RectF pointerAreaRect = new RectF();
    private final RectF wheelRect = new RectF();
    private final RectF wheelHitRect = new RectF();
    private final RectF closeRect = new RectF();
    private final RectF closeHitRect = new RectF();
    private final RectF safeRect = new RectF();
    private final RectF mouseIconRect = new RectF();
    private final RectF crossRect = new RectF();
    private final RectF activeDirectionRect = new RectF();

    private final SparseIntArray pointerTargets = new SparseIntArray();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final float density;
    private final int touchSlop;

    private VirtualMouseController controller;
    private PresentationHost presentationHost;

    private float compactSize;
    private float panelWidth;
    private float panelHeight;
    private float crossSize;
    private float pointerPanelGap;
    private float panelVerticalGap;
    private float closeSize;
    private float closeGap;
    private float wheelWidth;
    private float wheelHeight;
    private float pointerWidth;
    private float pointerHeight;
    private float pointerEdgeMargin;
    private float scrollDeadZone;
    private float minimumHitSize;

    private float compactCenterX;
    private float compactCenterY;
    private float compactDownX;
    private float compactDownY;
    private float compactStartX;
    private float compactStartY;
    private boolean compactDragging;

    private int pointerAreaPointerId = MotionEvent.INVALID_POINTER_ID;
    private float pointerLastX;
    private float pointerLastY;
    private boolean pointerAreaPressed;

    private int wheelPointerId = MotionEvent.INVALID_POINTER_ID;
    private float wheelOriginX;
    private float wheelOriginY;
    private float wheelCurrentX;
    private float wheelCurrentY;
    private float wheelMaximumDistance;
    private boolean wheelLongPressTriggered;

    private float crossCenterX;
    private float crossCenterY;
    private float cursorX;
    private float cursorY;
    private float appliedVideoOffsetX;
    private float appliedVideoOffsetY;

    private final Runnable wheelLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (controller == null || wheelPointerId == MotionEvent.INVALID_POINTER_ID
                    || pointerTargets.get(wheelPointerId, TARGET_NONE) != TARGET_WHEEL) {
                return;
            }

            if (controller.beginDirectionalScroll()) {
                wheelLongPressTriggered = true;
                crossCenterX = wheelOriginX;
                crossCenterY = wheelOriginY;
                updateScrollDirection(wheelCurrentX, wheelCurrentY);
                updateGeometry();
                invalidate();
            }
        }
    };

    public VirtualMouseOverlay(Context context) {
        this(context, null);
    }

    public VirtualMouseOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VirtualMouseOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        initPaints();
        setFocusable(false);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_hidden));
    }

    public void bind(VirtualMouseController controller, PresentationHost presentationHost) {
        if (this.controller != null) {
            this.controller.setListener(null);
        }
        this.controller = controller;
        this.presentationHost = presentationHost;
        controller.setListener(this);
        updateResponsiveMetrics(getWidth(), getHeight());
        restoreCompactCenterFromRatios();
        updateGeometry();
        updateContentDescription();
        invalidate();
    }

    public boolean isEnabledForCurrentStream() {
        return controller != null && controller.isEnabled();
    }

    public void toggleEnabledForCurrentStream() {
        if (controller == null) {
            return;
        }
        clearLocalGestureTracking();
        if (controller.isEnabled()) {
            controller.disable();
        }
        else {
            bringToFront();
            controller.enable();
        }
    }

    public void setInputSuppressed(boolean suppressed) {
        if (controller == null) {
            return;
        }
        if (suppressed) {
            clearLocalGestureTracking();
        }
        controller.setInputSuppressed(suppressed);
    }

    public void cancelActiveInteractions() {
        clearLocalGestureTracking();
        if (controller != null) {
            controller.cancelActiveInteractions();
        }
    }

    public void refreshAfterHostSizeChanged() {
        if (controller != null && controller.isEnabled()) {
            bringToFront();
        }
        cancelActiveInteractions();
        updateResponsiveMetrics(getWidth(), getHeight());
        restoreCompactCenterFromRatios();
        updateGeometry();
        if (controller != null && controller.getMode() == VirtualMouseState.Mode.EXPANDED) {
            getBaseVideoRect();
            controller.resendCurrentAbsolutePosition(baseVideoRect.width(), baseVideoRect.height());
        }
        invalidate();
    }

    public void destroy() {
        clearLocalGestureTracking();
        if (controller != null) {
            controller.destroy();
            controller = null;
        }
        applyVideoOffset(0f, 0f);
        presentationHost = null;
    }

    @Override
    public void onVirtualMouseStateChanged() {
        if (controller == null) {
            return;
        }
        if (!controller.isEnabled() || controller.isInputSuppressed()) {
            clearLocalGestureTracking();
        }
        updateGeometry();
        updateContentDescription();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw != 0 || oldh != 0) {
            cancelActiveInteractions();
        }
        updateResponsiveMetrics(w, h);
        restoreCompactCenterFromRatios();
        updateGeometry();
        if (controller != null && controller.getMode() == VirtualMouseState.Mode.EXPANDED) {
            post(new Runnable() {
                @Override
                public void run() {
                    refreshAfterHostSizeChanged();
                }
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelActiveInteractions();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelActiveInteractions();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (controller == null || !controller.isEnabled() || controller.isInputSuppressed()) {
            return;
        }

        switch (controller.getMode()) {
            case COMPACT:
                drawCompact(canvas);
                break;
            case EXPANDED:
                drawExpanded(canvas);
                break;
            case DIRECTION_SCROLL:
                drawDirectionScroll(canvas);
                break;
            case HIDDEN:
            default:
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (controller == null || !controller.isEnabled() || controller.isInputSuppressed()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int actionIndex = event.getActionIndex();
                boolean handled = handlePointerDown(event, actionIndex);
                return handled || pointerTargets.size() > 0;
            }
            case MotionEvent.ACTION_MOVE:
                if (pointerTargets.size() == 0) {
                    return false;
                }
                handlePointerMove(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pointerTargets.size() == 0) {
                    return false;
                }
                handlePointerUp(event, event.getActionIndex(), false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelActiveInteractions();
                return pointerTargets.size() > 0;
            default:
                return pointerTargets.size() > 0;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (controller != null && controller.getMode() == VirtualMouseState.Mode.COMPACT
                && !controller.isInputSuppressed()) {
            controller.expand();
            updateGeometry();
            getBaseVideoRect();
            controller.resendCurrentAbsolutePosition(baseVideoRect.width(), baseVideoRect.height());
        }
        return true;
    }

    private boolean handlePointerDown(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        if (controller.getMode() == VirtualMouseState.Mode.COMPACT) {
            if (!compactRect.contains(x, y) || findPointerForTarget(TARGET_COMPACT) != MotionEvent.INVALID_POINTER_ID) {
                return false;
            }
            pointerTargets.put(pointerId, TARGET_COMPACT);
            compactDownX = x;
            compactDownY = y;
            compactStartX = compactCenterX;
            compactStartY = compactCenterY;
            compactDragging = false;
            return true;
        }

        if (controller.getMode() != VirtualMouseState.Mode.EXPANDED) {
            return false;
        }

        int target = hitTestExpanded(x, y);
        if (target == TARGET_NONE || findPointerForTarget(target) != MotionEvent.INVALID_POINTER_ID) {
            return false;
        }
        if (target == TARGET_POINTER_AREA && pointerAreaPointerId != MotionEvent.INVALID_POINTER_ID) {
            return false;
        }
        if (target == TARGET_WHEEL && wheelPointerId != MotionEvent.INVALID_POINTER_ID) {
            return false;
        }

        pointerTargets.put(pointerId, target);
        switch (target) {
            case TARGET_LEFT_BUTTON:
                controller.pressButton(RemoteMouseSink.Button.LEFT);
                break;
            case TARGET_RIGHT_BUTTON:
                controller.pressButton(RemoteMouseSink.Button.RIGHT);
                break;
            case TARGET_POINTER_AREA:
                pointerAreaPointerId = pointerId;
                pointerLastX = x;
                pointerLastY = y;
                pointerAreaPressed = true;
                invalidate();
                break;
            case TARGET_WHEEL:
                wheelPointerId = pointerId;
                wheelOriginX = wheelCurrentX = x;
                wheelOriginY = wheelCurrentY = y;
                wheelMaximumDistance = 0f;
                wheelLongPressTriggered = false;
                handler.postDelayed(wheelLongPressRunnable, WHEEL_LONG_PRESS_MS);
                break;
            case TARGET_CLOSE:
            default:
                break;
        }
        return true;
    }

    private void handlePointerMove(MotionEvent event) {
        for (int i = 0; i < pointerTargets.size(); i++) {
            int pointerId = pointerTargets.keyAt(i);
            int target = pointerTargets.valueAt(i);
            int eventIndex = event.findPointerIndex(pointerId);
            if (eventIndex < 0) {
                continue;
            }
            float x = event.getX(eventIndex);
            float y = event.getY(eventIndex);

            switch (target) {
                case TARGET_COMPACT:
                    float compactDx = x - compactDownX;
                    float compactDy = y - compactDownY;
                    if (!compactDragging && Math.hypot(compactDx, compactDy) > touchSlop) {
                        compactDragging = true;
                    }
                    if (compactDragging) {
                        setCompactCenter(compactStartX + compactDx, compactStartY + compactDy, true);
                        invalidate();
                    }
                    break;
                case TARGET_POINTER_AREA:
                    if (pointerId == pointerAreaPointerId) {
                        float deltaX = x - pointerLastX;
                        float deltaY = y - pointerLastY;
                        pointerLastX = x;
                        pointerLastY = y;
                        getBaseVideoRect();
                        controller.moveCursor(deltaX, deltaY,
                                baseVideoRect.width(), baseVideoRect.height());
                    }
                    break;
                case TARGET_WHEEL:
                    if (pointerId == wheelPointerId) {
                        wheelCurrentX = x;
                        wheelCurrentY = y;
                        wheelMaximumDistance = Math.max(wheelMaximumDistance,
                                (float) Math.hypot(x - wheelOriginX, y - wheelOriginY));
                        if (wheelLongPressTriggered) {
                            updateScrollDirection(x, y);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void handlePointerUp(MotionEvent event, int index, boolean cancelled) {
        int pointerId = event.getPointerId(index);
        int target = pointerTargets.get(pointerId, TARGET_NONE);
        if (target == TARGET_NONE) {
            return;
        }

        float x = event.getX(index);
        float y = event.getY(index);
        pointerTargets.delete(pointerId);

        switch (target) {
            case TARGET_COMPACT:
                if (!cancelled && !compactDragging) {
                    performClick();
                }
                compactDragging = false;
                break;
            case TARGET_LEFT_BUTTON:
                controller.releaseButton(RemoteMouseSink.Button.LEFT);
                break;
            case TARGET_RIGHT_BUTTON:
                controller.releaseButton(RemoteMouseSink.Button.RIGHT);
                break;
            case TARGET_POINTER_AREA:
                if (pointerId == pointerAreaPointerId) {
                    pointerAreaPointerId = MotionEvent.INVALID_POINTER_ID;
                    pointerAreaPressed = false;
                    invalidate();
                }
                break;
            case TARGET_WHEEL:
                if (pointerId == wheelPointerId) {
                    handler.removeCallbacks(wheelLongPressRunnable);
                    wheelPointerId = MotionEvent.INVALID_POINTER_ID;
                    if (wheelLongPressTriggered) {
                        controller.endDirectionalScroll();
                    }
                    else if (!cancelled && wheelMaximumDistance <= touchSlop) {
                        performClick();
                        controller.clickMiddleButton();
                    }
                    wheelLongPressTriggered = false;
                }
                break;
            case TARGET_CLOSE:
                if (!cancelled && closeHitRect.contains(x, y)) {
                    performClick();
                    clearLocalGestureTracking();
                    controller.collapse();
                }
                break;
            default:
                break;
        }
    }

    private int hitTestExpanded(float x, float y) {
        if (closeHitRect.contains(x, y)) {
            return TARGET_CLOSE;
        }
        if (wheelHitRect.contains(x, y)) {
            return TARGET_WHEEL;
        }
        if (leftButtonRect.contains(x, y)) {
            return TARGET_LEFT_BUTTON;
        }
        if (rightButtonRect.contains(x, y)) {
            return TARGET_RIGHT_BUTTON;
        }
        if (pointerAreaRect.contains(x, y)) {
            return TARGET_POINTER_AREA;
        }
        return TARGET_NONE;
    }

    private int findPointerForTarget(int target) {
        for (int i = 0; i < pointerTargets.size(); i++) {
            if (pointerTargets.valueAt(i) == target) {
                return pointerTargets.keyAt(i);
            }
        }
        return MotionEvent.INVALID_POINTER_ID;
    }

    private void clearLocalGestureTracking() {
        handler.removeCallbacks(wheelLongPressRunnable);
        pointerTargets.clear();
        pointerAreaPointerId = MotionEvent.INVALID_POINTER_ID;
        pointerAreaPressed = false;
        wheelPointerId = MotionEvent.INVALID_POINTER_ID;
        wheelLongPressTriggered = false;
        compactDragging = false;
    }

    private void updateScrollDirection(float x, float y) {
        float dx = x - wheelOriginX;
        float dy = y - wheelOriginY;
        VirtualMouseState.ScrollDirection direction;
        if (Math.hypot(dx, dy) < scrollDeadZone) {
            direction = VirtualMouseState.ScrollDirection.NONE;
        }
        else if (Math.abs(dx) > Math.abs(dy)) {
            direction = dx < 0
                    ? VirtualMouseState.ScrollDirection.LEFT
                    : VirtualMouseState.ScrollDirection.RIGHT;
        }
        else {
            direction = dy < 0
                    ? VirtualMouseState.ScrollDirection.UP
                    : VirtualMouseState.ScrollDirection.DOWN;
        }
        controller.setScrollDirection(direction);
    }

    private void updateGeometry() {
        if (controller == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        VirtualMouseState.Mode mode = controller.getMode();
        if (!controller.isEnabled() || controller.isInputSuppressed()
                || mode == VirtualMouseState.Mode.HIDDEN || mode == VirtualMouseState.Mode.COMPACT) {
            applyVideoOffset(0f, 0f);
            restoreCompactCenterFromRatios();
            return;
        }

        if (mode == VirtualMouseState.Mode.DIRECTION_SCROLL) {
            return;
        }

        getBaseVideoRect();
        float baseCursorX = baseVideoRect.left
                + controller.getCursorNormalizedX() * baseVideoRect.width();
        float baseCursorY = baseVideoRect.top
                + controller.getCursorNormalizedY() * baseVideoRect.height();

        float leftInset = pointerEdgeMargin;
        float topInset = pointerEdgeMargin;
        float rightInset = pointerPanelGap + panelWidth + closeGap + closeSize;
        float bottomInset = panelVerticalGap + panelHeight;
        safeRect.set(leftInset, topInset, getWidth() - rightInset, getHeight() - bottomInset);
        if (safeRect.width() < dp(32) || safeRect.height() < dp(32)) {
            float fallbackInset = dp(28);
            safeRect.set(fallbackInset, fallbackInset,
                    getWidth() - fallbackInset, getHeight() - fallbackInset);
        }

        float offsetX = 0f;
        float offsetY = 0f;
        if (baseCursorX < safeRect.left) {
            offsetX = safeRect.left - baseCursorX;
        }
        else if (baseCursorX > safeRect.right) {
            offsetX = safeRect.right - baseCursorX;
        }
        if (baseCursorY < safeRect.top) {
            offsetY = safeRect.top - baseCursorY;
        }
        else if (baseCursorY > safeRect.bottom) {
            offsetY = safeRect.bottom - baseCursorY;
        }

        float maxOffsetX = Math.min(getWidth() - dp(32),
                Math.max(getWidth() * 0.35f, rightInset));
        float maxOffsetY = Math.min(getHeight() - dp(32),
                Math.max(getHeight() * 0.35f, bottomInset));
        offsetX = clamp(offsetX, -Math.max(maxOffsetX, 0f), Math.max(maxOffsetX, 0f));
        offsetY = clamp(offsetY, -Math.max(maxOffsetY, 0f), Math.max(maxOffsetY, 0f));
        applyVideoOffset(offsetX, offsetY);

        cursorX = baseCursorX + offsetX;
        cursorY = baseCursorY + offsetY;
        layoutExpandedControls();
    }

    private void layoutExpandedControls() {
        float panelLeft = cursorX + pointerPanelGap;
        float panelTop = cursorY + panelVerticalGap;
        panelRect.set(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);

        float upperHeight = panelHeight * 0.57f;
        float halfWidth = panelWidth * 0.5f;
        leftButtonRect.set(panelRect.left, panelRect.top,
                panelRect.left + halfWidth, panelRect.top + upperHeight);
        rightButtonRect.set(panelRect.left + halfWidth, panelRect.top,
                panelRect.right, panelRect.top + upperHeight);
        pointerAreaRect.set(panelRect.left, panelRect.top + upperHeight,
                panelRect.right, panelRect.bottom);

        float wheelLeft = panelRect.centerX() - wheelWidth * 0.5f;
        float wheelTop = panelRect.top + (upperHeight - wheelHeight) * 0.5f;
        wheelRect.set(wheelLeft, wheelTop, wheelLeft + wheelWidth, wheelTop + wheelHeight);
        setCenteredMinimumHitRect(wheelHitRect, wheelRect.centerX(), wheelRect.centerY(),
                Math.max(wheelWidth, minimumHitSize), Math.max(wheelHeight, minimumHitSize));

        closeRect.set(panelRect.right + closeGap, panelRect.top,
                panelRect.right + closeGap + closeSize, panelRect.top + closeSize);
        setCenteredMinimumHitRect(closeHitRect, closeRect.centerX(), closeRect.centerY(),
                minimumHitSize, minimumHitSize);
    }

    private void getBaseVideoRect() {
        if (presentationHost != null) {
            presentationHost.getBaseVideoRect(baseVideoRect);
        }
        if (baseVideoRect.width() <= 0f || baseVideoRect.height() <= 0f) {
            baseVideoRect.set(0f, 0f, Math.max(getWidth(), 1), Math.max(getHeight(), 1));
        }
    }

    private void applyVideoOffset(float offsetX, float offsetY) {
        if (Math.abs(appliedVideoOffsetX - offsetX) < 0.5f
                && Math.abs(appliedVideoOffsetY - offsetY) < 0.5f) {
            return;
        }
        appliedVideoOffsetX = offsetX;
        appliedVideoOffsetY = offsetY;
        if (presentationHost != null) {
            presentationHost.setVirtualMouseVideoOffset(offsetX, offsetY);
        }
    }

    private void restoreCompactCenterFromRatios() {
        if (controller == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float radius = compactSize * 0.5f;
        float minX = radius;
        float maxX = Math.max(radius, getWidth() - radius);
        float minY = radius;
        float maxY = Math.max(radius, getHeight() - radius);
        compactCenterX = minX + controller.getCompactCenterRatioX() * Math.max(maxX - minX, 1f);
        compactCenterY = minY + controller.getCompactCenterRatioY() * Math.max(maxY - minY, 1f);
        updateCompactRect();
    }

    private void setCompactCenter(float centerX, float centerY, boolean saveRatios) {
        float radius = compactSize * 0.5f;
        float minX = radius;
        float maxX = Math.max(radius, getWidth() - radius);
        float minY = radius;
        float maxY = Math.max(radius, getHeight() - radius);
        compactCenterX = clamp(centerX, minX, maxX);
        compactCenterY = clamp(centerY, minY, maxY);
        updateCompactRect();
        if (saveRatios && controller != null) {
            controller.setCompactCenterRatios(
                    (compactCenterX - minX) / Math.max(maxX - minX, 1f),
                    (compactCenterY - minY) / Math.max(maxY - minY, 1f));
        }
    }

    private void updateCompactRect() {
        float radius = compactSize * 0.5f;
        compactRect.set(compactCenterX - radius, compactCenterY - radius,
                compactCenterX + radius, compactCenterY + radius);
    }

    private void updateResponsiveMetrics(int width, int height) {
        float shortestDp = Math.min(width, height) / Math.max(density, 0.01f);
        boolean spacious = shortestDp >= 600f;
        compactSize = dp(spacious ? 44 : 38);
        panelWidth = dp(spacious ? 104 : 84);
        panelHeight = dp(spacious ? 120 : 96);
        crossSize = dp(spacious ? 112 : 92);
        pointerPanelGap = dp(spacious ? 26 : 20);
        panelVerticalGap = dp(3);
        closeSize = dp(spacious ? 28 : 26);
        closeGap = dp(4);
        wheelWidth = dp(spacious ? 28 : 22);
        wheelHeight = dp(spacious ? 46 : 36);
        pointerWidth = dp(spacious ? 24 : 20);
        pointerHeight = dp(spacious ? 29 : 25);
        pointerEdgeMargin = dp(4);
        scrollDeadZone = dp(22);
        minimumHitSize = dp(44);
        strokePaint.setStrokeWidth(dp(1));
        iconPaint.setStrokeWidth(dp(1.8f));
    }

    private void drawCompact(Canvas canvas) {
        fillPaint.setColor(Color.argb(0xD1, 0x29, 0x26, 0x33));
        strokePaint.setColor(Color.argb(0x85, 0xD1, 0xC9, 0xF5));
        canvas.drawOval(compactRect, fillPaint);
        canvas.drawOval(compactRect, strokePaint);

        float iconWidth = compactSize * 0.42f;
        float iconHeight = compactSize * 0.56f;
        mouseIconRect.set(compactCenterX - iconWidth * 0.5f,
                compactCenterY - iconHeight * 0.5f,
                compactCenterX + iconWidth * 0.5f,
                compactCenterY + iconHeight * 0.5f);
        canvas.drawRoundRect(mouseIconRect, iconWidth * 0.5f, iconWidth * 0.5f, iconPaint);
        canvas.drawLine(compactCenterX, mouseIconRect.top, compactCenterX,
                mouseIconRect.top + iconHeight * 0.32f, iconPaint);
    }

    private void drawExpanded(Canvas canvas) {
        drawPointer(canvas);

        fillPaint.setColor(Color.argb(0xC2, 0xC7, 0xC2, 0xED));
        strokePaint.setColor(Color.argb(0xAD, 0xEB, 0xE6, 0xFF));
        canvas.drawRoundRect(panelRect, dp(13), dp(13), fillPaint);

        if (controller.isLeftPressed()) {
            fillPaint.setColor(Color.argb(0x4D, 0x80, 0x73, 0xF0));
            canvas.drawRoundRect(leftButtonRect, dp(13), dp(13), fillPaint);
        }
        if (controller.isRightPressed()) {
            fillPaint.setColor(Color.argb(0x4D, 0x80, 0x73, 0xF0));
            canvas.drawRoundRect(rightButtonRect, dp(13), dp(13), fillPaint);
        }
        if (pointerAreaPressed) {
            fillPaint.setColor(Color.argb(0x4D, 0x80, 0x73, 0xF0));
            canvas.drawRoundRect(pointerAreaRect, dp(10), dp(10), fillPaint);
        }

        canvas.drawRoundRect(panelRect, dp(13), dp(13), strokePaint);
        strokePaint.setColor(Color.argb(0x70, 0x50, 0x49, 0x73));
        canvas.drawLine(panelRect.centerX(), panelRect.top,
                panelRect.centerX(), leftButtonRect.bottom, strokePaint);
        canvas.drawLine(panelRect.left, pointerAreaRect.top,
                panelRect.right, pointerAreaRect.top, strokePaint);

        fillPaint.setColor(Color.argb(0xD6, 0x26, 0x24, 0x30));
        canvas.drawRoundRect(wheelRect, wheelRect.width() * 0.5f,
                wheelRect.width() * 0.5f, fillPaint);
        canvas.drawOval(closeRect, fillPaint);

        iconPaint.setColor(Color.argb(0xF2, 0xF4, 0xF1, 0xFF));
        float closeInset = closeSize * 0.32f;
        canvas.drawLine(closeRect.left + closeInset, closeRect.top + closeInset,
                closeRect.right - closeInset, closeRect.bottom - closeInset, iconPaint);
        canvas.drawLine(closeRect.right - closeInset, closeRect.top + closeInset,
                closeRect.left + closeInset, closeRect.bottom - closeInset, iconPaint);

        float wheelMark = wheelRect.height() * 0.18f;
        canvas.drawLine(wheelRect.centerX(), wheelRect.top + wheelMark,
                wheelRect.centerX(), wheelRect.top + wheelMark * 1.8f, iconPaint);
    }

    private void drawPointer(Canvas canvas) {
        pointerPath.reset();
        pointerPath.moveTo(cursorX, cursorY);
        pointerPath.lineTo(cursorX + pointerWidth * 0.25f, cursorY + pointerHeight * 0.86f);
        pointerPath.lineTo(cursorX + pointerWidth * 0.48f, cursorY + pointerHeight * 0.62f);
        pointerPath.lineTo(cursorX + pointerWidth * 0.78f, cursorY + pointerHeight);
        pointerPath.lineTo(cursorX + pointerWidth, cursorY + pointerHeight * 0.82f);
        pointerPath.lineTo(cursorX + pointerWidth * 0.68f, cursorY + pointerHeight * 0.47f);
        pointerPath.lineTo(cursorX + pointerWidth, cursorY + pointerHeight * 0.42f);
        pointerPath.close();
        fillPaint.setColor(Color.argb(0xF2, 0xF4, 0xF1, 0xFF));
        canvas.drawPath(pointerPath, fillPaint);
        strokePaint.setColor(Color.argb(0xB8, 0x42, 0x3B, 0x62));
        canvas.drawPath(pointerPath, strokePaint);
    }

    private void drawDirectionScroll(Canvas canvas) {
        float radius = crossSize * 0.5f;
        crossRect.set(crossCenterX - radius, crossCenterY - radius,
                crossCenterX + radius, crossCenterY + radius);
        fillPaint.setColor(Color.argb(0xCC, 0x26, 0x24, 0x33));
        canvas.drawOval(crossRect, fillPaint);

        VirtualMouseState.ScrollDirection active = controller.getActiveScrollDirection();
        if (active != VirtualMouseState.ScrollDirection.NONE) {
            fillPaint.setColor(Color.argb(0xBD, 0x80, 0x73, 0xF0));
            activeDirectionRect.set(crossCenterX - radius * 0.38f,
                    crossCenterY - radius * 0.38f,
                    crossCenterX + radius * 0.38f,
                    crossCenterY + radius * 0.38f);
            switch (active) {
                case UP:
                    activeDirectionRect.offset(0f, -radius * 0.52f);
                    break;
                case DOWN:
                    activeDirectionRect.offset(0f, radius * 0.52f);
                    break;
                case LEFT:
                    activeDirectionRect.offset(-radius * 0.52f, 0f);
                    break;
                case RIGHT:
                    activeDirectionRect.offset(radius * 0.52f, 0f);
                    break;
                default:
                    break;
            }
            canvas.drawOval(activeDirectionRect, fillPaint);
        }

        iconPaint.setColor(Color.argb(0xF2, 0xF4, 0xF1, 0xFF));
        float arm = radius * 0.62f;
        canvas.drawLine(crossCenterX, crossCenterY - arm,
                crossCenterX, crossCenterY + arm, iconPaint);
        canvas.drawLine(crossCenterX - arm, crossCenterY,
                crossCenterX + arm, crossCenterY, iconPaint);
        float arrow = radius * 0.16f;
        drawArrowHead(canvas, crossCenterX, crossCenterY - arm, 0f, -1f, arrow);
        drawArrowHead(canvas, crossCenterX, crossCenterY + arm, 0f, 1f, arrow);
        drawArrowHead(canvas, crossCenterX - arm, crossCenterY, -1f, 0f, arrow);
        drawArrowHead(canvas, crossCenterX + arm, crossCenterY, 1f, 0f, arrow);
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float directionX,
                               float directionY, float size) {
        iconPath.reset();
        iconPath.moveTo(x, y);
        iconPath.lineTo(x - directionX * size - directionY * size,
                y - directionY * size + directionX * size);
        iconPath.moveTo(x, y);
        iconPath.lineTo(x - directionX * size + directionY * size,
                y - directionY * size - directionX * size);
        canvas.drawPath(iconPath, iconPaint);
    }

    private void initPaints() {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setColor(Color.argb(0xF2, 0xF4, 0xF1, 0xFF));
    }

    private void updateContentDescription() {
        if (controller == null || !controller.isEnabled()) {
            setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_hidden));
            return;
        }
        switch (controller.getMode()) {
            case COMPACT:
                setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_compact));
                break;
            case EXPANDED:
                setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_expanded));
                break;
            case DIRECTION_SCROLL:
                setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_scroll));
                break;
            case HIDDEN:
            default:
                setContentDescription(getResources().getString(R.string.virtual_mouse_accessibility_hidden));
                break;
        }
    }

    private void setCenteredMinimumHitRect(RectF out, float centerX, float centerY,
                                           float width, float height) {
        out.set(centerX - width * 0.5f, centerY - height * 0.5f,
                centerX + width * 0.5f, centerY + height * 0.5f);
    }

    private float dp(float value) {
        return value * density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
