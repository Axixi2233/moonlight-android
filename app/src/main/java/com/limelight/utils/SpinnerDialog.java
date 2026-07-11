package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.AppDialog;

public class SpinnerDialog implements Runnable,OnCancelListener {
    private final String title;
    private final String message;
    private final Activity activity;
    private AlertDialog progress;
    private TextView messageView;
    private final boolean finish;
    private boolean finishOnCancelEnabled;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.progress = null;
        this.finish = finish;
        this.finishOnCancelEnabled = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
    {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog dialog = i.next();
                if (dialog.activity == activity) {
                    i.remove();
                    if (dialog.progress.isShowing()) {
                        dialog.progress.dismiss();
                    }
                }
            }
        }
    }

    public void dismiss()
    {
        // Running again with progress != null will destroy it
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message)
    {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageView != null) {
                    messageView.setText(message);
                }
            }
        });
    }

    public void setFinishOnCancelEnabled(final boolean enabled) {
        finishOnCancelEnabled = enabled;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progress == null) {
                    return;
                }

                if (finish) {
                    progress.setCancelable(enabled);
                    progress.setCanceledOnTouchOutside(false);
                }
            }
        });
    }

    @Override
    public void run() {

        // If we're dying, don't bother doing anything
        if (activity.isFinishing()) {
            return;
        }

        if (progress == null)
        {
            View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_spinner_prompt, null, false);
            TextView titleView = dialogView.findViewById(R.id.tv_spinner_title);
            messageView = dialogView.findViewById(R.id.tv_spinner_message);

            titleView.setText(title);
            messageView.setText(message);

            progress = AppDialog.createCustomDialog(activity, dialogView, finish);
            if (progress == null) {
                return;
            }
            progress.setOnCancelListener(this);

            // If we want to finish the activity when this is killed, make it cancellable
            if (finish)
            {
                progress.setCancelable(true);
                progress.setCanceledOnTouchOutside(false);
            }
            else
            {
                progress.setCancelable(false);
            }

            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                if (finish) {
                    AppDialog.showCustomDialog(activity, progress, 0.68f, 420,
                            null, null);
                } else {
                    AppDialog.showPassiveCustomDialog(activity, progress, 0.68f, 420);
                }
            }
        }
        else
        {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this) && progress.isShowing()) {
                    progress.dismiss();
                }
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }

        if (finish && finishOnCancelEnabled) {
            activity.finish();
        }
    }
}
