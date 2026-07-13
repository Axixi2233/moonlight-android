package com.limelight.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

public final class AppDialog {
    private AppDialog() {
    }

    public static AlertDialog showMessage(Activity activity,
                                          CharSequence title,
                                          CharSequence message,
                                          CharSequence primaryText,
                                          Runnable primaryAction) {
        return showMessage(activity, title, message, primaryText, primaryAction, null, null, false);
    }

    public static AlertDialog showMessage(Activity activity,
                                          CharSequence title,
                                          CharSequence message,
                                          CharSequence primaryText,
                                          Runnable primaryAction,
                                          CharSequence secondaryText,
                                          Runnable secondaryAction,
                                          boolean primaryDanger) {
        if (!canShow(activity)) {
            return null;
        }

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_app_message, null, false);
        TextView titleView = content.findViewById(R.id.tv_app_dialog_title);
        TextView messageView = content.findViewById(R.id.tv_app_dialog_message);
        TextView primaryView = content.findViewById(R.id.btn_app_dialog_primary);
        TextView secondaryView = content.findViewById(R.id.btn_app_dialog_secondary);

        titleView.setText(title);
        messageView.setText(message);
        primaryView.setText(TextUtils.isEmpty(primaryText)
                ? activity.getString(R.string.dialog_action_close) : primaryText);
        if (primaryDanger) {
            primaryView.setBackgroundResource(R.drawable.bg_app_dialog_action_danger);
        } else {
            primaryView.setBackgroundResource(R.drawable.bg_app_dialog_primary);
        }

        AlertDialog dialog = createDialog(activity, content, true);
        primaryView.setOnClickListener(v -> {
            dialog.dismiss();
            run(primaryAction);
        });

        if (!TextUtils.isEmpty(secondaryText)) {
            secondaryView.setVisibility(View.VISIBLE);
            secondaryView.setText(secondaryText);
            secondaryView.setOnClickListener(v -> {
                dialog.dismiss();
                run(secondaryAction);
            });
        } else {
            secondaryView.setVisibility(View.GONE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) primaryView.getLayoutParams();
            params.setMarginStart(0);
            primaryView.setLayoutParams(params);
        }

        installInputNavigation(dialog, primaryView, secondaryView, false);
        showAndSize(activity, dialog, 0.68f, 440);
        View initialFocus = secondaryView.getVisibility() == View.VISIBLE ? secondaryView : primaryView;
        initialFocus.post(initialFocus::requestFocus);
        return dialog;
    }

    public static AlertDialog showConfirm(Activity activity,
                                          CharSequence title,
                                          CharSequence message,
                                          CharSequence confirmText,
                                          boolean danger,
                                          Runnable onConfirm,
                                          Runnable onCancel) {
        AlertDialog dialog = showMessage(
                activity,
                title,
                message,
                confirmText,
                onConfirm,
                activity.getString(R.string.dialog_action_cancel),
                onCancel,
                danger);
        if (dialog != null) {
            View primaryView = dialog.findViewById(R.id.btn_app_dialog_primary);
            View secondaryView = dialog.findViewById(R.id.btn_app_dialog_secondary);
            installInputNavigation(dialog, primaryView, secondaryView, true);
        }
        return dialog;
    }

    public static AlertDialog showProgress(Activity activity,
                                           CharSequence title,
                                           CharSequence message,
                                           Runnable onCancel) {
        if (!canShow(activity)) {
            return null;
        }

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_app_progress, null, false);
        TextView titleView = content.findViewById(R.id.tv_app_progress_title);
        TextView messageView = content.findViewById(R.id.tv_app_progress_message);
        titleView.setText(title);
        messageView.setText(message);

        AlertDialog dialog = createDialog(activity, content, onCancel != null);
        if (onCancel != null) {
            dialog.setOnCancelListener(ignored -> onCancel.run());
        }
        showAndSize(activity, dialog, 0.68f, 420);
        return dialog;
    }

    public static AlertDialog createCustomDialog(Activity activity, View content, boolean cancelable) {
        if (!canShow(activity)) {
            return null;
        }
        return createDialog(activity, content, cancelable);
    }

    public static void showCustomDialog(Activity activity,
                                        AlertDialog dialog,
                                        float widthFraction,
                                        int maxWidthDp,
                                        View initialFocus,
                                        View dismissAction,
                                        View... actionViews) {
        if (!canShow(activity) || dialog == null) {
            return;
        }
        installCustomInput(dialog, dismissAction, actionViews);
        showAndSize(activity, dialog, widthFraction, maxWidthDp);
        if (initialFocus != null) {
            initialFocus.post(initialFocus::requestFocus);
        }
    }

    public static void showPassiveCustomDialog(Activity activity,
                                               AlertDialog dialog,
                                               float widthFraction,
                                               int maxWidthDp) {
        if (!canShow(activity) || dialog == null) {
            return;
        }
        showAndSize(activity, dialog, widthFraction, maxWidthDp);
    }

    private static AlertDialog createDialog(Activity activity, View content, boolean cancelable) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(content).create();
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        return dialog;
    }

    private static void showAndSize(Activity activity, AlertDialog dialog, float widthFraction, int maxWidthDp) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.46f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(dp(activity, 18));
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            params.setBlurBehindRadius(dp(activity, 12));
        }
        window.setAttributes(params);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int desiredWidth = Math.min(Math.round(metrics.widthPixels * widthFraction), dp(activity, maxWidthDp));
        int availableWidth = Math.max(1, metrics.widthPixels - dp(activity, 32));
        int width = Math.min(Math.max(dp(activity, 280), desiredWidth), availableWidth);
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void installInputNavigation(AlertDialog dialog,
                                               View primaryView,
                                               View secondaryView,
                                               boolean dismissThroughSecondaryAction) {
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (isDismissKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (dismissThroughSecondaryAction && secondaryView.getVisibility() == View.VISIBLE) {
                        secondaryView.performClick();
                    } else {
                        dialog.dismiss();
                    }
                }
                return true;
            }

            if (isActivateKey(keyCode)) {
                View focusedView = dialog.getCurrentFocus();
                if (focusedView == primaryView || focusedView == secondaryView) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        focusedView.performClick();
                    }
                    return true;
                }
            }
            return false;
        });
    }

    private static void installCustomInput(AlertDialog dialog,
                                           View dismissAction,
                                           View... actionViews) {
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (isDismissKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (dismissAction != null) {
                        dismissAction.performClick();
                    } else {
                        dialog.cancel();
                    }
                }
                return true;
            }

            if (isActivateKey(keyCode)) {
                View focusedView = dialog.getCurrentFocus();
                for (View actionView : actionViews) {
                    if (focusedView == actionView) {
                        if (event.getAction() == KeyEvent.ACTION_UP) {
                            actionView.performClick();
                        }
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static boolean isDismissKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_BUTTON_B;
    }

    private static boolean isActivateKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_SPACE;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static boolean canShow(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
