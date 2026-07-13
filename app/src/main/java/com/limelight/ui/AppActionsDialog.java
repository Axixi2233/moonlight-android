package com.limelight.ui;

import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.NvApp;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

public final class AppActionsDialog extends BaseGameMenuDialog {
    public interface Listener {
        void onStart();
        void onResume();
        void onQuit();
        void onQuitAndStart();
        void onToggleVisibility();
        void onCreateShortcut();
        void onDismiss();
    }

    private NvApp app;
    private int runningAppId;
    private boolean hidden;
    private boolean shortcutAvailable;
    private Listener listener;

    public void setApp(NvApp app, int runningAppId, boolean hidden, boolean shortcutAvailable) {
        this.app = app;
        this.runningAppId = runningAppId;
        this.hidden = hidden;
        this.shortcutAvailable = shortcutAvailable;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.dialog_app_actions;
    }

    @Override
    public void bindView(View view) {
        if (app == null) {
            dismiss();
            return;
        }

        ImageButton backButton = view.findViewById(R.id.ibtn_app_actions_back);
        TextView titleView = view.findViewById(R.id.tv_app_actions_title);
        TextView detailsView = view.findViewById(R.id.tv_app_actions_details);
        Button startButton = view.findViewById(R.id.btn_app_actions_start);
        Button resumeButton = view.findViewById(R.id.btn_app_actions_resume);
        Button quitButton = view.findViewById(R.id.btn_app_actions_quit);
        Button quitAndStartButton = view.findViewById(R.id.btn_app_actions_quit_and_start);
        Button showButton = view.findViewById(R.id.btn_app_actions_show);
        Button hideButton = view.findViewById(R.id.btn_app_actions_hide);
        Button shortcutButton = view.findViewById(R.id.btn_app_actions_shortcut);

        boolean isRunning = app.getAppId() == runningAppId;
        boolean hasOtherRunningApp = runningAppId != 0 && !isRunning;
        boolean canToggleVisibility = !isRunning || hidden;

        titleView.setText(app.getAppName());
        detailsView.setText(getString(
                R.string.app_actions_details_format,
                app.getAppName(),
                getHdrSupportText(app.isHdrSupported()),
                app.getAppId()));

        startButton.setVisibility(runningAppId == 0 ? View.VISIBLE : View.GONE);
        resumeButton.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        quitButton.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        quitAndStartButton.setVisibility(hasOtherRunningApp ? View.VISIBLE : View.GONE);
        showButton.setVisibility(canToggleVisibility && hidden ? View.VISIBLE : View.GONE);
        hideButton.setVisibility(canToggleVisibility && !hidden ? View.VISIBLE : View.GONE);
        shortcutButton.setVisibility(shortcutAvailable ? View.VISIBLE : View.GONE);

        backButton.setOnClickListener(v -> dismiss());
        startButton.setOnClickListener(v -> runAction(() -> listener.onStart()));
        resumeButton.setOnClickListener(v -> runAction(() -> listener.onResume()));
        quitButton.setOnClickListener(v -> runAction(() -> listener.onQuit()));
        quitAndStartButton.setOnClickListener(v -> runAction(() -> listener.onQuitAndStart()));
        showButton.setOnClickListener(v -> runAction(() -> listener.onToggleVisibility()));
        hideButton.setOnClickListener(v -> runAction(() -> listener.onToggleVisibility()));
        shortcutButton.setOnClickListener(v -> runAction(() -> listener.onCreateShortcut()));

        View firstAction = findFirstVisibleAction(startButton, resumeButton, quitAndStartButton,
                quitButton, showButton, hideButton, shortcutButton);
        (firstAction == null ? backButton : firstAction).requestFocus();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (listener != null) {
            listener.onDismiss();
        }
    }

    private String getHdrSupportText(boolean hdrSupported) {
        return getString(hdrSupported ? R.string.app_actions_hdr_supported : R.string.app_actions_hdr_unknown);
    }

    private View findFirstVisibleAction(View... actions) {
        for (View action : actions) {
            if (action.getVisibility() == View.VISIBLE) {
                return action;
            }
        }
        return null;
    }

    private void runAction(Runnable action) {
        dismiss();
        if (listener != null) {
            action.run();
        }
    }
}
