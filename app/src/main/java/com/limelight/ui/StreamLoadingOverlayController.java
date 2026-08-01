package com.limelight.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

public final class StreamLoadingOverlayController {
    private static final long SUCCESS_HIDE_DELAY_MS = 180L;

    private enum Phase {
        PREPARING(12, 0, R.string.stream_phase_preparing, R.string.stream_phase_preparing_detail),
        LAUNCHING(24, 0, R.string.stream_phase_launching, R.string.stream_phase_launching_detail),
        CONNECTING(44, 1, R.string.stream_phase_connecting, R.string.stream_phase_connecting_detail),
        INITIALIZING(64, 1, R.string.stream_phase_initializing, R.string.stream_phase_initializing_detail),
        STARTING(82, 2, R.string.stream_phase_starting, R.string.stream_phase_starting_detail),
        WAITING(92, 3, R.string.stream_phase_waiting, R.string.stream_phase_waiting_detail),
        CONNECTED(100, 3, R.string.stream_phase_connected, R.string.stream_phase_connected_detail),
        FAILED(100, 3, R.string.stream_phase_failed, R.string.stream_phase_failed);

        final int progress;
        final int step;
        final int titleResId;
        final int detailResId;

        Phase(int progress, int step, int titleResId, int detailResId) {
            this.progress = progress;
            this.step = step;
            this.titleResId = titleResId;
            this.detailResId = detailResId;
        }
    }

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final View overlay;
    private final View content;
    private final ImageView icon;
    private final ProgressBar progress;
    private final TextView title;
    private final TextView detail;
    private final TextView[] steps;
    private final TextView helpButton;
    private final TextView cancelButton;
    private final Runnable cancelAction;
    private final Runnable helpAction;
    private final Runnable hideRunnable;
    private ObjectAnimator progressAnimator;
    private Phase phase = Phase.PREPARING;

    public StreamLoadingOverlayController(Activity activity, Runnable cancelAction, Runnable helpAction) {
        this.activity = activity;
        this.cancelAction = cancelAction;
        this.helpAction = helpAction;
        overlay = activity.findViewById(R.id.stream_loading_overlay);
        content = activity.findViewById(R.id.stream_loading_content);
        icon = activity.findViewById(R.id.iv_stream_loading_icon);
        progress = activity.findViewById(R.id.pb_stream_loading);
        title = activity.findViewById(R.id.tv_stream_loading_title);
        detail = activity.findViewById(R.id.tv_stream_loading_detail);
        steps = new TextView[] {
                activity.findViewById(R.id.tv_stream_step_prepare),
                activity.findViewById(R.id.tv_stream_step_connect),
                activity.findViewById(R.id.tv_stream_step_start),
                activity.findViewById(R.id.tv_stream_step_picture)
        };
        helpButton = activity.findViewById(R.id.btn_stream_loading_help);
        cancelButton = activity.findViewById(R.id.btn_stream_loading_cancel);
        hideRunnable = () -> {
            if (phase == Phase.CONNECTED && !this.activity.isFinishing()) {
                overlay.setVisibility(View.GONE);
            }
        };

        cancelButton.setOnClickListener(v -> run(cancelAction));
        helpButton.setOnClickListener(v -> run(helpAction));
        overlay.setOnKeyListener((v, keyCode, event) -> false);
        content.post(this::updateContentWidth);
        showPhase(Phase.PREPARING, null);
    }

    public void showPreparing(String customDetail) {
        mainHandler.post(() -> {
            // A retained stream can fail after this overlay has already reached CONNECTED.
            // Reset the terminal phase so the reconnect attempt is visible and subsequent
            // connection stages are allowed to advance normally again.
            if (phase == Phase.CONNECTED || phase == Phase.FAILED) {
                mainHandler.removeCallbacks(hideRunnable);
                cancelProgressAnimator();
                progress.setProgress(0);
            }
            showPhase(Phase.PREPARING, customDetail);
        });
    }

    public void onStageStarting(String stage) {
        Phase next;
        if (TextUtils.isEmpty(stage)) {
            next = Phase.PREPARING;
        } else if (stage.contains("RTSP") || stage.contains("name resolution") || stage.contains("platform initialization")) {
            next = Phase.CONNECTING;
        } else if (stage.contains("stream initialization")) {
            next = Phase.INITIALIZING;
        } else if (stage.contains("stream establishment")) {
            next = Phase.STARTING;
        } else {
            next = Phase.LAUNCHING;
        }
        postPhase(next, null);
    }

    public void onConnectionStarted() {
        postPhase(Phase.WAITING, null);
    }

    public void onFirstFrameRendered() {
        mainHandler.post(() -> {
            if (phase == Phase.FAILED) {
                return;
            }
            showPhase(Phase.CONNECTED, null);
            mainHandler.removeCallbacks(hideRunnable);
            mainHandler.postDelayed(hideRunnable, SUCCESS_HIDE_DELAY_MS);
        });
    }

    public void showFailure(CharSequence message) {
        mainHandler.post(() -> showPhase(Phase.FAILED, message));
    }

    public void setCancelEnabled(boolean enabled) {
        mainHandler.post(() -> {
            cancelButton.setEnabled(enabled);
            cancelButton.setAlpha(enabled ? 1f : 0.45f);
        });
    }

    public boolean isVisible() {
        return overlay.getVisibility() == View.VISIBLE;
    }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        cancelProgressAnimator();
    }

    private void postPhase(Phase next, CharSequence customDetail) {
        mainHandler.post(() -> {
            if (phase == Phase.FAILED || phase == Phase.CONNECTED) {
                return;
            }
            showPhase(next, customDetail);
        });
    }

    private void showPhase(Phase next, CharSequence customDetail) {
        phase = next;
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        title.setText(next.titleResId);
        detail.setText(TextUtils.isEmpty(customDetail) ? activity.getString(next.detailResId) : customDetail);
        animateProgress(next.progress);
        applySteps(next.step, next == Phase.FAILED);

        boolean failed = next == Phase.FAILED;
        boolean connected = next == Phase.CONNECTED;
        int tint = failed ? Color.rgb(255, 104, 124)
                : connected ? Color.rgb(83, 214, 133)
                : Color.rgb(143, 181, 255);
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(tint));
        }
        progress.setProgressTintList(ColorStateList.valueOf(tint));
        detail.setTextColor(failed ? Color.rgb(255, 200, 208) : Color.argb(220, 255, 255, 255));
        helpButton.setVisibility(failed && helpAction != null ? View.VISIBLE : View.GONE);
        cancelButton.setText(failed ? R.string.stream_loading_back : R.string.stream_loading_cancel);
        cancelButton.setBackgroundResource(failed
                ? R.drawable.bg_app_dialog_action_danger : android.R.color.transparent);
        if (failed) {
            cancelButton.requestFocus();
        }
    }

    private void applySteps(int activeStep, boolean failed) {
        for (int i = 0; i < steps.length; i++) {
            TextView step = steps[i];
            boolean reached = i <= activeStep;
            step.setAlpha(reached ? 1f : 0.52f);
            step.setTextColor(failed && i == activeStep
                    ? Color.rgb(255, 190, 200)
                    : reached ? Color.WHITE : Color.argb(180, 255, 255, 255));
        }
    }

    private void animateProgress(int target) {
        cancelProgressAnimator();
        int start = progress.getProgress();
        if (!progress.isLaidOut()) {
            progress.setProgress(target);
            return;
        }
        progressAnimator = ObjectAnimator.ofInt(progress, "progress", start, Math.max(start, target));
        progressAnimator.setDuration(Math.abs(target - start) > 20 ? 420L : 260L);
        progressAnimator.start();
    }

    private void cancelProgressAnimator() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
    }

    private void updateContentWidth() {
        if (!(content.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(420), Math.max(dp(260), Math.round(screenWidth * 0.72f)));
        width = Math.min(width, Math.max(dp(240), screenWidth - dp(32)));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) content.getLayoutParams();
        params.width = width;
        content.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

}
