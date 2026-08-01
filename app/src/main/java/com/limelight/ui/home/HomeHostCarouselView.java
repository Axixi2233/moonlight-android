package com.limelight.ui.home;

import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;

import com.limelight.PcView;

import java.util.Collections;
import java.util.List;

public final class HomeHostCarouselView extends RecyclerView
        implements HomeHostAdapter.InteractionListener {

    public interface Listener {
        void onSelectionChanged(String selectionKey, int position, int hostCount,
                                PcView.ComputerObject computer, boolean addCard,
                                boolean userInitiated);
        void onHostActivated(PcView.ComputerObject computer, View sourceView);
        void onHostActions(PcView.ComputerObject computer, View sourceView, int position);
        void onAddHost();
    }

    private final LinearLayoutManager layoutManager;
    private final LinearSnapHelper snapHelper = new LinearSnapHelper();
    private final HomeHostAdapter hostAdapter;
    private final int touchSlop;
    private Listener listener;
    private int cardWidth;
    private boolean joystickArmed = true;
    private boolean confirmLongPressHandled;
    private long lastJoystickMoveMs;
    private float touchStartX;
    private float touchStartY;
    private int touchStartPosition = NO_POSITION;
    private int touchPointerId = MotionEvent.INVALID_POINTER_ID;
    private VelocityTracker touchVelocityTracker;
    private boolean horizontalDragLocked;
    private boolean userScrollPending;

    public HomeHostCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        setLayoutManager(layoutManager);
        hostAdapter = new HomeHostAdapter(this);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setAdapter(hostAdapter);
        setItemAnimator(null);
        setHasFixedSize(true);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setItemViewCacheSize(5);
        addItemDecoration(new SpacingDecoration(dp(7)));
        snapHelper.attachToRecyclerView(this);

        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                applyCardTransforms();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_DRAGGING) {
                    userScrollPending = true;
                }
                if (newState == SCROLL_STATE_IDLE) {
                    View snapView = snapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        setSelectionInternal(position, false, false, userScrollPending);
                    }
                    userScrollPending = false;
                }
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setComputers(List<PcView.ComputerObject> computers, String preferredSelectionKey) {
        List<PcView.ComputerObject> safeComputers = computers == null
                ? Collections.emptyList()
                : computers;
        hostAdapter.setComputers(safeComputers);
        int position = hostAdapter.findPositionForKey(preferredSelectionKey);
        setSelectionInternal(position, false, false, false);
        post(() -> centerPosition(position, false));
    }

    public String getSelectedKey() {
        return hostAdapter.getSelectionKey(hostAdapter.getSelectedPosition());
    }

    public void requestFocusOnSelectedCard() {
        requestSelectedCardFocus();
    }

    public void activateSelectedCard() {
        int position = hostAdapter.getSelectedPosition();
        if (hostAdapter.isAddPosition(position)) {
            if (listener != null) {
                listener.onAddHost();
            }
            return;
        }

        PcView.ComputerObject computer = hostAdapter.getComputerAt(position);
        View sourceView = findCardView(position);
        if (listener != null && computer != null) {
            listener.onHostActivated(computer, sourceView == null ? this : sourceView);
        }
    }

    public void showSelectedHostActions() {
        int position = hostAdapter.getSelectedPosition();
        PcView.ComputerObject computer = hostAdapter.getComputerAt(position);
        if (listener != null && computer != null) {
            View sourceView = findCardView(position);
            listener.onHostActions(computer, sourceView == null ? this : sourceView, position);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) {
            return;
        }

        boolean compactPhoneLandscape = isCompactPhoneLandscape();
        int minWidth = dp(compactPhoneLandscape ? 240 : 270);
        int maxWidth = dp(compactPhoneLandscape ? 310
                : getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 360 : 430);
        int proposedWidth = Math.round(w * (compactPhoneLandscape ? 0.72f : 0.78f));
        cardWidth = Math.min(Math.max(proposedWidth, minWidth), maxWidth);
        cardWidth = Math.min(cardWidth, Math.max(dp(220), w - dp(16)));
        int horizontalPadding = Math.max(0, (w - cardWidth) / 2 - dp(7));
        setPadding(horizontalPadding, getPaddingTop(), horizontalPadding, getPaddingBottom());
        hostAdapter.setCardWidth(cardWidth);
        post(() -> centerPosition(hostAdapter.getSelectedPosition(), false));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            beginTouchGesture(event);
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            updateTouchInterception(event);
        }
        if (touchVelocityTracker != null) {
            touchVelocityTracker.addMovement(event);
        }

        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            finishTouchGesture(event);
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            resetTouchGesture();
        }
        return handled;
    }

    @Override
    public void onCardClicked(int position, View sourceView) {
        if (position != hostAdapter.getSelectedPosition()) {
            setSelectionInternal(position, true, false, true);
            return;
        }
        activateSelectedCard();
    }

    @Override
    public void onCardFocused(int position, View sourceView) {
        setSelectionInternal(position, true, false, false);
    }

    @Override
    public void onCardFocusLost(int position, View sourceView) {
        post(this::applyCardTransforms);
    }

    @Override
    public void onCardLongPressed(int position, View sourceView) {
        setSelectionInternal(position, true, false, true);
        showSelectedHostActions();
    }

    @Override
    public void onMoreActions(int position, View sourceView) {
        setSelectionInternal(position, true, false, true);
        showSelectedHostActions();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean keyDown = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean firstDown = keyDown && event.getRepeatCount() == 0;

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            if (keyDown && (event.getRepeatCount() == 0 || event.getRepeatCount() % 3 == 0)) {
                moveSelection(-1, true);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (keyDown && (event.getRepeatCount() == 0 || event.getRepeatCount() % 3 == 0)) {
                moveSelection(1, true);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (firstDown) {
                confirmLongPressHandled = false;
            }
            else if (keyDown && !confirmLongPressHandled
                    && (event.isLongPress() || event.getRepeatCount() == 1)) {
                showSelectedHostActions();
                confirmLongPressHandled = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            else if (event.getAction() == KeyEvent.ACTION_UP && !confirmLongPressHandled) {
                playSoundEffect(SoundEffectConstants.CLICK);
                activateSelectedCard();
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                confirmLongPressHandled = false;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            if (firstDown) {
                showSelectedHostActions();
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE
                && (event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK)
                == android.view.InputDevice.SOURCE_JOYSTICK) {
            float horizontal = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            if (Math.abs(horizontal) < 0.01f) {
                horizontal = event.getAxisValue(MotionEvent.AXIS_X);
            }

            if (Math.abs(horizontal) < 0.35f) {
                joystickArmed = true;
            }
            else if (Math.abs(horizontal) >= 0.65f && joystickArmed
                    && event.getEventTime() - lastJoystickMoveMs >= 180) {
                joystickArmed = false;
                lastJoystickMoveMs = event.getEventTime();
                moveSelection(horizontal > 0 ? 1 : -1, true);
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            post(this::requestSelectedCardFocus);
        }
    }

    private void moveSelection(int delta, boolean requestFocus) {
        int target = Math.max(0, Math.min(
                hostAdapter.getSelectedPosition() + delta,
                hostAdapter.getItemCount() - 1));
        if (target == hostAdapter.getSelectedPosition()) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            return;
        }
        setSelectionInternal(target, true, requestFocus, true);
    }

    private void beginTouchGesture(MotionEvent event) {
        touchStartX = event.getX();
        touchStartY = event.getY();
        touchStartPosition = hostAdapter.getSelectedPosition();
        touchPointerId = event.getPointerId(0);
        horizontalDragLocked = false;
        if (touchVelocityTracker == null) {
            touchVelocityTracker = VelocityTracker.obtain();
        }
        else {
            touchVelocityTracker.clear();
        }
    }

    private void updateTouchInterception(MotionEvent event) {
        if (horizontalDragLocked || touchPointerId == MotionEvent.INVALID_POINTER_ID) {
            return;
        }
        int pointerIndex = event.findPointerIndex(touchPointerId);
        if (pointerIndex < 0) {
            return;
        }
        float distanceX = event.getX(pointerIndex) - touchStartX;
        float distanceY = event.getY(pointerIndex) - touchStartY;
        if (Math.abs(distanceX) >= touchSlop
                && Math.abs(distanceX) > Math.abs(distanceY) * 1.08f) {
            horizontalDragLocked = true;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
    }

    private void finishTouchGesture(MotionEvent event) {
        if (touchStartPosition == NO_POSITION || touchVelocityTracker == null) {
            resetTouchGesture();
            return;
        }

        touchVelocityTracker.computeCurrentVelocity(1000);
        float velocityX = touchVelocityTracker.getXVelocity(touchPointerId);
        float velocityY = touchVelocityTracker.getYVelocity(touchPointerId);
        float distanceX = event.getX() - touchStartX;
        float distanceY = event.getY() - touchStartY;
        float projectedX = distanceX + velocityX * 0.06f;
        float projectedY = distanceY + velocityY * 0.06f;

        // LinearSnapHelper normally needs the next card to cross the viewport center.
        // Treat a shorter intentional swipe as a one-card step, while projected velocity
        // lets a quick flick qualify without stealing predominantly vertical gestures.
        boolean horizontalGesture = Math.abs(projectedX) > Math.abs(projectedY) * 1.15f;
        boolean crossedDistanceThreshold = Math.abs(distanceX) >= dp(48);
        boolean crossedProjectedThreshold = Math.abs(distanceX) >= dp(12)
                && Math.abs(projectedX) >= dp(58);
        if (horizontalGesture && (crossedDistanceThreshold || crossedProjectedThreshold)) {
            int direction = projectedX < 0f ? 1 : -1;
            int target = Math.max(0, Math.min(
                    touchStartPosition + direction,
                    hostAdapter.getItemCount() - 1));
            if (target != touchStartPosition) {
                stopScroll();
                setSelectionInternal(target, true, false, true);
            }
        }
        resetTouchGesture();
    }

    private void resetTouchGesture() {
        if (horizontalDragLocked && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        horizontalDragLocked = false;
        touchStartPosition = NO_POSITION;
        touchPointerId = MotionEvent.INVALID_POINTER_ID;
        if (touchVelocityTracker != null) {
            touchVelocityTracker.recycle();
            touchVelocityTracker = null;
        }
    }

    private void setSelectionInternal(int position, boolean smooth, boolean requestFocus,
                                      boolean userInitiated) {
        int boundedPosition = Math.max(0, Math.min(position, hostAdapter.getItemCount() - 1));
        boolean changed = boundedPosition != hostAdapter.getSelectedPosition();
        hostAdapter.setSelectedPosition(boundedPosition);
        centerPosition(boundedPosition, smooth && animationsEnabled());
        applyCardTransforms();

        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        notifySelectionChanged(userInitiated);

        if (requestFocus) {
            post(this::requestSelectedCardFocus);
        }
    }

    private void centerPosition(int position, boolean smooth) {
        if (position < 0 || position >= hostAdapter.getItemCount()) {
            return;
        }
        if (smooth) {
            smoothScrollToPosition(position);
        }
        else {
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private void notifySelectionChanged(boolean userInitiated) {
        if (listener == null) {
            return;
        }
        int position = hostAdapter.getSelectedPosition();
        boolean addCard = hostAdapter.isAddPosition(position);
        listener.onSelectionChanged(
                hostAdapter.getSelectionKey(position),
                position,
                hostAdapter.getHostCount(),
                hostAdapter.getComputerAt(position),
                addCard,
                userInitiated);
    }

    private void requestSelectedCardFocus() {
        int position = hostAdapter.getSelectedPosition();
        View card = findCardView(position);
        if (card != null) {
            card.requestFocus();
        }
        else {
            centerPosition(position, false);
            post(() -> {
                View attachedCard = findCardView(position);
                if (attachedCard != null) {
                    attachedCard.requestFocus();
                }
            });
        }
    }

    private View findCardView(int position) {
        ViewHolder holder = findViewHolderForAdapterPosition(position);
        return holder == null ? null : holder.itemView;
    }

    private void applyCardTransforms() {
        if (getWidth() == 0) {
            return;
        }
        float center = getWidth() / 2f;
        float stride = Math.max(1f, cardWidth + dp(14));
        boolean animateScale = animationsEnabled();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            float distance = Math.min(Math.abs(childCenter - center) / stride, 1.25f);
            float scale = animateScale ? Math.max(0.88f, 1f - distance * 0.10f) : 1f;
            if (child.hasFocus()) {
                // Keep the focused card inside the RecyclerView's vertical bounds.
                // The white focus stroke and raised Z already provide a strong focus
                // cue, while scaling beyond 1.0 clips the top and bottom of the stroke.
                scale = Math.min(1f, scale * 1.03f);
            }
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(Math.max(0.56f, 1f - distance * 0.30f));
            child.setTranslationZ(child.hasFocus() ? dp(8) : dp(2));
        }
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(
                    getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f) != 0f;
        }
        catch (Exception ignored) {
            return true;
        }
    }

    private boolean isCompactPhoneLandscape() {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        return configuration.orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && configuration.smallestScreenWidthDp < 600;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SpacingDecoration extends ItemDecoration {
        private final int halfSpacing;

        SpacingDecoration(int halfSpacing) {
            this.halfSpacing = halfSpacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull State state) {
            outRect.left = halfSpacing;
            outRect.right = halfSpacing;
        }
    }
}
