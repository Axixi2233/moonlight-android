package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;

import com.limelight.R;
import com.limelight.ui.AppDialog;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private AlertDialog alert;
    private boolean suppressDismissCallback;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        ArrayList<Dialog> dialogsToDismiss;
        synchronized (rundownDialogs) {
            dialogsToDismiss = new ArrayList<>(rundownDialogs);
            rundownDialogs.clear();
        }

        for (Dialog d : dialogsToDismiss) {
            d.dismissSilently();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = AppDialog.showMessage(
                activity,
                title,
                message,
                activity.getResources().getText(android.R.string.ok),
                null,
                activity.getResources().getText(R.string.help),
                () -> HelpLauncher.launchTroubleshooting(activity),
                false);
        if (alert == null) {
            return;
        }
        alert.setOnDismissListener(ignored -> {
            synchronized (rundownDialogs) {
                rundownDialogs.remove(Dialog.this);
            }
            if (!suppressDismissCallback && runOnDismiss != null) {
                runOnDismiss.run();
            }
        });

        View primaryView = alert.findViewById(R.id.btn_app_dialog_primary);
        if (primaryView != null) {
            primaryView.post(primaryView::requestFocus);
        }

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
        }
    }

    private void dismissSilently() {
        suppressDismissCallback = true;
        if (alert != null && alert.isShowing()) {
            alert.dismiss();
        }
    }

}
