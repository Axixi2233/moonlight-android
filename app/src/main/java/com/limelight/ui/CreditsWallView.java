package com.limelight.ui;

import android.content.Context;
import android.graphics.Outline;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreditsWallView extends FrameLayout {

    private static final int ITEM_BLOCK_HEIGHT_DP = 140;
    private static final int MIN_COLUMN_WIDTH_DP = 150;
    private static final int COLUMN_GAP_DP = 8;
    private static final float BASE_SCROLL_SPEED_DP_PER_SECOND = 22f;
    private static final float COLUMN_SCROLL_SPEED_STEP_DP_PER_SECOND = 2f;

    private final LinearLayout columnsContainer;
    private final LayoutInflater layoutInflater;
    private final List<ColumnState> columnStates = new ArrayList<>();
    private final Runnable rebuildRunnable = this::rebuildColumns;
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            advanceAutoScroll();
            if (autoScrollRunning) {
                postOnAnimation(this);
            }
        }
    };

    private List<CreditEntry> entries = Collections.emptyList();
    private boolean autoScrollRunning;
    private long lastFrameTimeMs;

    public CreditsWallView(Context context) {
        this(context, null);
    }

    public CreditsWallView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CreditsWallView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        layoutInflater = LayoutInflater.from(context);
        setClipChildren(true);
        setClipToPadding(true);

        columnsContainer = new LinearLayout(context);
        columnsContainer.setOrientation(LinearLayout.HORIZONTAL);
        columnsContainer.setClipChildren(true);
        columnsContainer.setClipToPadding(true);
        addView(columnsContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    public void setEntries(List<CreditEntry> entries) {
        this.entries = new ArrayList<>(entries);
        scheduleRebuild();
    }

    public void startAutoScroll() {
        if (columnStates.isEmpty() || autoScrollRunning || !isAttachedToWindow()) {
            return;
        }

        autoScrollRunning = true;
        lastFrameTimeMs = 0L;
        postOnAnimation(tickRunnable);
    }

    public void stopAutoScroll() {
        autoScrollRunning = false;
        lastFrameTimeMs = 0L;
        removeCallbacks(tickRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!entries.isEmpty()) {
            scheduleRebuild();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAutoScroll();
        removeCallbacks(rebuildRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            scheduleRebuild();
        }
    }

    private void scheduleRebuild() {
        removeCallbacks(rebuildRunnable);
        post(rebuildRunnable);
    }

    private void rebuildColumns() {
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (availableWidth <= 0 || availableHeight <= 0 || entries.isEmpty()) {
            return;
        }

        stopAutoScroll();
        columnsContainer.removeAllViews();
        columnStates.clear();

        int columnCount = getColumnCount(availableWidth);
        List<List<CreditEntry>> entriesByColumn = splitEntries(columnCount);
        for (int i = 0; i < columnCount; i++) {
            columnsContainer.addView(createColumn(entriesByColumn.get(i), availableHeight, i));
        }

        post(this::resetScrollAndStart);
    }

    private View createColumn(List<CreditEntry> columnEntries, int viewportHeightPx, int columnIndex) {
        FrameLayout viewport = new FrameLayout(getContext());
        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f);
        int sideMargin = dp(COLUMN_GAP_DP / 2f);
        viewportParams.leftMargin = sideMargin;
        viewportParams.rightMargin = sideMargin;
        viewport.setLayoutParams(viewportParams);
        viewport.setClipChildren(true);
        viewport.setClipToPadding(true);

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setFillViewport(true);
        scrollView.setOnTouchListener((v, event) -> true);

        LinearLayout track = new LinearLayout(getContext());
        track.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(track, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        List<CreditEntry> loopEntries = buildLoopEntries(columnEntries, viewportHeightPx);
        populateTrack(track, loopEntries);
        populateTrack(track, loopEntries);

        viewport.addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        int loopHeightPx = loopEntries.size() * dp(ITEM_BLOCK_HEIGHT_DP);
        float speedPxPerMs = dp(BASE_SCROLL_SPEED_DP_PER_SECOND + (columnIndex * COLUMN_SCROLL_SPEED_STEP_DP_PER_SECOND)) / 1000f;
        columnStates.add(new ColumnState(scrollView, loopHeightPx, speedPxPerMs));
        return viewport;
    }

    private void populateTrack(LinearLayout track, List<CreditEntry> loopEntries) {
        for (CreditEntry entry : loopEntries) {
            View itemView = layoutInflater.inflate(R.layout.item_credit_entry, track, false);
            ImageView avatarView = itemView.findViewById(R.id.iv_credit_avatar);
            TextView nameView = itemView.findViewById(R.id.tv_credit_name);

            nameView.setText(entry.name);
            applyCircularOutline(avatarView);
            avatarView.setImageDrawable(null);

            String avatarUrl = entry.getNormalizedAvatarUrl();
            if (!TextUtils.isEmpty(avatarUrl)) {
                Glide.with(getContext())
                        .load(avatarUrl)
                        .override(dp(54), dp(54))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(avatarView);
            }

            track.addView(itemView);
        }
    }

    private List<List<CreditEntry>> splitEntries(int columnCount) {
        List<List<CreditEntry>> entriesByColumn = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            entriesByColumn.add(new ArrayList<>());
        }

        for (int i = 0; i < entries.size(); i++) {
            entriesByColumn.get(i % columnCount).add(entries.get(i));
        }

        return entriesByColumn;
    }

    private List<CreditEntry> buildLoopEntries(List<CreditEntry> baseEntries, int viewportHeightPx) {
        if (baseEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<CreditEntry> loopEntries = new ArrayList<>(baseEntries);
        int itemBlockHeightPx = dp(ITEM_BLOCK_HEIGHT_DP);
        int minLoopHeightPx = viewportHeightPx + (itemBlockHeightPx * 2);

        while ((loopEntries.size() * itemBlockHeightPx) < minLoopHeightPx) {
            loopEntries.addAll(baseEntries);
        }

        return loopEntries;
    }

    private void resetScrollAndStart() {
        for (ColumnState state : columnStates) {
            state.offsetPx = 0f;
            state.scrollView.scrollTo(0, 0);
        }
        startAutoScroll();
    }

    private void advanceAutoScroll() {
        if (columnStates.isEmpty()) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (lastFrameTimeMs == 0L) {
            lastFrameTimeMs = now;
            return;
        }

        float deltaMs = Math.min(48f, now - lastFrameTimeMs);
        lastFrameTimeMs = now;

        for (ColumnState state : columnStates) {
            if (state.loopHeightPx <= 0) {
                continue;
            }

            state.offsetPx += state.speedPxPerMs * deltaMs;
            while (state.offsetPx >= state.loopHeightPx) {
                state.offsetPx -= state.loopHeightPx;
            }
            state.scrollView.scrollTo(0, Math.round(state.offsetPx));
        }
    }

    private void applyCircularOutline(ImageView avatarView) {
        avatarView.setClipToOutline(true);
        avatarView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
    }

    private int getColumnCount(int availableWidthPx) {
        int minColumnWidthPx = dp(MIN_COLUMN_WIDTH_DP);
        int columnGapPx = dp(COLUMN_GAP_DP);
        int columnCount = (availableWidthPx + columnGapPx) / (minColumnWidthPx + columnGapPx);
        return Math.max(1, Math.min(4, columnCount));
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    public static final class CreditEntry {
        public final String name;
        public final String avatarUrl;

        public CreditEntry(String name, String avatarUrl) {
            this.name = name;
            this.avatarUrl = avatarUrl;
        }

        public String getNormalizedAvatarUrl() {
            int suffixIndex = avatarUrl.indexOf('@');
            if (suffixIndex >= 0) {
                return avatarUrl.substring(0, suffixIndex);
            }
            return avatarUrl;
        }
    }

    private static final class ColumnState {
        private final ScrollView scrollView;
        private final int loopHeightPx;
        private final float speedPxPerMs;
        private float offsetPx;

        private ColumnState(ScrollView scrollView, int loopHeightPx, float speedPxPerMs) {
            this.scrollView = scrollView;
            this.loopHeightPx = loopHeightPx;
            this.speedPxPerMs = speedPxPerMs;
        }
    }
}
