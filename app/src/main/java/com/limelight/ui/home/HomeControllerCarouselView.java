package com.limelight.ui.home;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compact, full-width pager for the controllers shown on the home screen.
 * Each physical controller keeps the existing information-card presentation,
 * while touch, DPAD, shoulder buttons, and a joystick can switch pages.
 */
public final class HomeControllerCarouselView extends RecyclerView {
    private static final String EMPTY_CONTROLLER_KEY = "__moonlight_no_controller__";
    private static final float JOYSTICK_RELEASE_THRESHOLD = 0.35f;
    private static final float JOYSTICK_TRIGGER_THRESHOLD = 0.65f;
    private static final float HAT_TRIGGER_THRESHOLD = 0.5f;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(String controllerKey);
    }

    public interface OnControllerClickListener {
        void onControllerClick();
    }

    public static final class ControllerItem {
        public final String key;
        public final String title;
        public final String subtitle;
        public final boolean vibrationSupported;

        public ControllerItem(String key, String title, String subtitle,
                              boolean vibrationSupported) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.vibrationSupported = vibrationSupported;
        }
    }

    private final LinearLayoutManager layoutManager;
    private final PagerSnapHelper snapHelper = new StartAlignedPagerSnapHelper();
    private final ControllerAdapter controllerAdapter = new ControllerAdapter();
    private final int touchSlop;
    private OnSelectionChangedListener selectionChangedListener;
    private OnControllerClickListener controllerClickListener;
    private int selectedPosition;
    private int pageWidth;
    private boolean joystickArmed = true;
    private boolean restorePageFocus;
    private float touchStartX;
    private float touchStartY;
    private int touchStartPosition = NO_POSITION;
    private int touchPointerId = MotionEvent.INVALID_POINTER_ID;
    private VelocityTracker touchVelocityTracker;
    private boolean horizontalDragLocked;

    public HomeControllerCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setLayoutManager(layoutManager);
        setAdapter(controllerAdapter);
        setItemAnimator(null);
        setHasFixedSize(true);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        setNestedScrollingEnabled(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(true);
        addItemDecoration(new ControllerPageSpacingDecoration(dp(8)));
        snapHelper.attachToRecyclerView(this);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != SCROLL_STATE_IDLE) {
                    return;
                }

                View snapView = snapHelper.findSnapView(layoutManager);
                if (snapView != null) {
                    selectPosition(layoutManager.getPosition(snapView), false, false);
                }
                finishPendingPageFocusTransfer();
            }
        });
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        selectionChangedListener = listener;
    }

    public void setOnControllerClickListener(OnControllerClickListener listener) {
        controllerClickListener = listener;
    }

    public void setControllers(List<ControllerItem> controllers, String preferredKey) {
        String previousKey = preferredKey != null ? preferredKey : getSelectedKey();
        List<ControllerItem> safeControllers = controllers == null
                ? Collections.emptyList() : controllers;
        controllerAdapter.setItems(safeControllers);

        int target = controllerAdapter.findPositionByKey(previousKey);
        if (target < 0) {
            target = Math.min(selectedPosition, controllerAdapter.getItemCount() - 1);
        }
        selectPosition(Math.max(0, target), false, false);
        post(() -> layoutManager.scrollToPositionWithOffset(selectedPosition, 0));
    }

    public String getSelectedKey() {
        return controllerAdapter.getKey(selectedPosition);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && pageWidth != w) {
            pageWidth = w;
            controllerAdapter.notifyDataSetChanged();
            post(() -> layoutManager.scrollToPositionWithOffset(selectedPosition, 0));
        }
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (event.getRepeatCount() == 0 || event.getRepeatCount() % 3 == 0)
                    && moveSelection(-1, true)) {
                return true;
            }
            if (selectedPosition > 0) {
                return true;
            }
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (event.getRepeatCount() == 0 || event.getRepeatCount() % 3 == 0)
                    && moveSelection(1, true)) {
                return true;
            }
            if (selectedPosition < controllerAdapter.getItemCount() - 1) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE
                && (event.getSource() & InputDevice.SOURCE_JOYSTICK)
                == InputDevice.SOURCE_JOYSTICK) {
            float hat = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float direction = Math.abs(hat) >= HAT_TRIGGER_THRESHOLD
                    ? hat : event.getAxisValue(MotionEvent.AXIS_X);
            float magnitude = Math.abs(direction);
            if (magnitude < JOYSTICK_RELEASE_THRESHOLD) {
                joystickArmed = true;
                return super.onGenericMotionEvent(event);
            }
            if (!joystickArmed) {
                return true;
            }
            if (magnitude >= JOYSTICK_TRIGGER_THRESHOLD) {
                joystickArmed = false;
                if (moveSelection(direction > 0 ? 1 : -1, true)) {
                    return true;
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && !restorePageFocus) {
            post(this::requestSelectedPageFocus);
        }
    }

    private boolean moveSelection(int delta, boolean requestFocus) {
        int target = selectedPosition + delta;
        if (target < 0 || target >= controllerAdapter.getItemCount()) {
            return false;
        }
        selectPosition(target, true, requestFocus);
        return true;
    }

    private void beginTouchGesture(MotionEvent event) {
        touchStartX = event.getX();
        touchStartY = event.getY();
        touchStartPosition = selectedPosition;
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
        boolean horizontalGesture = Math.abs(projectedX) > Math.abs(projectedY) * 1.15f;
        boolean crossedDistanceThreshold = Math.abs(distanceX) >= dp(48);
        boolean crossedProjectedThreshold = Math.abs(distanceX) >= dp(12)
                && Math.abs(projectedX) >= dp(58);
        if (horizontalGesture && (crossedDistanceThreshold || crossedProjectedThreshold)) {
            int direction = projectedX < 0f ? 1 : -1;
            int target = Math.max(0, Math.min(
                    touchStartPosition + direction, controllerAdapter.getItemCount() - 1));
            if (target != touchStartPosition) {
                stopScroll();
                selectPosition(target, true, false);
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

    private void selectPosition(int position, boolean smooth, boolean requestFocus) {
        if (controllerAdapter.getItemCount() == 0) {
            return;
        }
        int boundedPosition = Math.max(0, Math.min(position,
                controllerAdapter.getItemCount() - 1));
        boolean keepPageFocus = requestFocus && hasFocus();
        if (keepPageFocus) {
            restorePageFocus = true;
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            requestFocus();
        }

        boolean changed = boundedPosition != selectedPosition;
        selectedPosition = boundedPosition;
        if (smooth) {
            View targetView = layoutManager.findViewByPosition(selectedPosition);
            if (targetView != null) {
                smoothScrollBy(
                        layoutManager.getDecoratedLeft(targetView) - getPaddingLeft(), 0);
            }
            else {
                smoothScrollToPosition(selectedPosition);
            }
        }
        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        notifySelectionChanged();
        if (keepPageFocus) {
            post(() -> {
                if (restorePageFocus && getScrollState() == SCROLL_STATE_IDLE) {
                    finishPendingPageFocusTransfer();
                }
            });
        }
        else if (requestFocus) {
            post(this::requestSelectedPageFocus);
        }
    }

    private void finishPendingPageFocusTransfer() {
        if (!restorePageFocus) {
            return;
        }

        restorePageFocus = false;
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        post(this::requestSelectedPageFocus);
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedKey());
        }
    }

    private void requestSelectedPageFocus() {
        ViewHolder holder = findViewHolderForAdapterPosition(selectedPosition);
        if (holder != null) {
            holder.itemView.requestFocus();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class StartAlignedPagerSnapHelper extends PagerSnapHelper {
        @Override
        public int[] calculateDistanceToFinalSnap(@NonNull LayoutManager layoutManager,
                                                  @NonNull View targetView) {
            if (layoutManager.canScrollHorizontally()) {
                return new int[] {
                        layoutManager.getDecoratedLeft(targetView) - getPaddingLeft(), 0
                };
            }
            return super.calculateDistanceToFinalSnap(layoutManager, targetView);
        }
    }

    private static final class ControllerPageSpacingDecoration extends ItemDecoration {
        private final int pageSpacing;

        ControllerPageSpacingDecoration(int pageSpacing) {
            this.pageSpacing = pageSpacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull State state) {
            outRect.right = pageSpacing;
        }
    }

    private final class ControllerAdapter extends Adapter<ControllerViewHolder> {
        private final List<ControllerItem> items = new ArrayList<>();

        void setItems(List<ControllerItem> controllers) {
            items.clear();
            items.addAll(controllers);
            if (items.isEmpty()) {
                items.add(new ControllerItem(EMPTY_CONTROLLER_KEY, null, null, false));
            }
            notifyDataSetChanged();
        }

        int findPositionByKey(String key) {
            if (key == null) {
                return -1;
            }
            for (int index = 0; index < items.size(); index++) {
                if (key.equals(items.get(index).key)) {
                    return index;
                }
            }
            return -1;
        }

        String getKey(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }
            String key = items.get(position).key;
            return EMPTY_CONTROLLER_KEY.equals(key) ? null : key;
        }

        @NonNull
        @Override
        public ControllerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.view_home_controller_card, parent, false);
            view.setFocusable(true);
            view.setClickable(true);
            view.setNextFocusUpId(R.id.axiButton);
            view.setNextFocusDownId(R.id.hostCarousel);
            return new ControllerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ControllerViewHolder holder, int position) {
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            layoutParams.width = pageWidth > 0
                    ? pageWidth : ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            holder.itemView.setLayoutParams(layoutParams);
            holder.bind(items.get(position), position, items.size());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class ControllerViewHolder extends ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final TextView status;
        private final View dot;

        ControllerViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.homeControllerTitle);
            subtitle = itemView.findViewById(R.id.homeControllerSubtitle);
            status = itemView.findViewById(R.id.homeControllerStatus);
            dot = itemView.findViewById(R.id.homeControllerDot);
            itemView.setOnClickListener(view -> {
                if (controllerClickListener != null) {
                    controllerClickListener.onControllerClick();
                }
            });
        }

        void bind(ControllerItem controller, int position, int count) {
            boolean connected = !EMPTY_CONTROLLER_KEY.equals(controller.key);
            int statusColor = getResources().getColor(connected
                    ? R.color.home_connected : R.color.home_secondary_text);
            if (connected) {
                title.setText(controller.title);
                subtitle.setText(controller.subtitle
                        + (controller.vibrationSupported
                        ? " · " + getResources().getString(
                                R.string.home_controller_vibration_supported)
                        : ""));
                subtitle.setCompoundDrawables(null, null, null, null);
                status.setText(count > 1
                        ? getResources().getString(
                                R.string.home_controller_page_format, position + 1, count)
                        : getResources().getString(R.string.home_controller_connected));
                itemView.setContentDescription(controller.title + ", "
                        + controller.subtitle
                        + (controller.vibrationSupported
                        ? ", " + getResources().getString(
                                R.string.home_controller_vibration_supported)
                        : ""));
            }
            else {
                title.setText(R.string.home_controller_waiting);
                subtitle.setText(R.string.home_controller_waiting_detail);
                subtitle.setCompoundDrawables(null, null, null, null);
                status.setText(R.string.home_controller_disconnected);
                itemView.setContentDescription(getResources().getString(
                        R.string.home_controller_waiting));
            }
            status.setTextColor(statusColor);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(statusColor);
            dot.setBackground(dotDrawable);
        }
    }
}
