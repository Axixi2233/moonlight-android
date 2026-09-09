package com.limelight.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.AppDialog;

/** Small restorable selector above the settings home, with exactly five named slots. */
public class SettingsProfilePicker extends DialogFragment {
    static final String TAG = "settings_profile_picker";
    private View[] choices;
    private View cancel;
    private int active;

    @Override public Dialog onCreateDialog(Bundle state) {
        active = SettingsProfileStore.active(getActivity());
        LinearLayout body = new LinearLayout(getActivity());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.setBackgroundResource(R.drawable.settings_input_background);
        TextView title = label(getString(R.string.settings_profile_switch), 16);
        body.addView(title);
        TextView hint = label(getString(R.string.settings_profile_choose_hint), 12);
        hint.setTextColor(0xFFB4B4B4);
        hint.setPadding(0, dp(8), 0, dp(8));
        body.addView(hint);
        choices = new View[SettingsProfileState.COUNT];
        for (int i = 0; i < SettingsProfileState.COUNT; i++) {
            final int target = i;
            LinearLayout item = new LinearLayout(getActivity());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(8), dp(12), dp(8));
            item.setMinimumHeight(dp(44));
            String profileName = getString(R.string.settings_profile_name, name(i));
            TextView nameView = label(profileName, 14);
            nameView.setTextColor(i == active ? getResources().getColor(R.color.home_accent_bright) : 0xFFEEEEEE);
            item.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));
            TextView indicator = label(i == active ? "●" : "○", 20);
            indicator.setGravity(Gravity.CENTER);
            indicator.setTextColor(i == active ? getResources().getColor(R.color.home_accent_bright) : 0xFFAAAAAA);
            indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(indicator, new LinearLayout.LayoutParams(dp(26), -2));
            item.setContentDescription(profileName + (i == active ? "，已选中" : "，未选中"));
            item.setBackgroundResource(R.drawable.settings_control_row_background);
            item.setFocusable(true);
            item.setSelected(i == active);
            item.setOnClickListener(v -> {
                dismiss();
                ((StreamSettings) getActivity()).changeSettingsProfile(target, false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(5);
            body.addView(item, lp);
            choices[i] = item;
        }
        TextView close = label(getString(R.string.dialog_action_cancel), 13);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(12), dp(10), dp(12), dp(10));
        close.setMinHeight(dp(44));
        close.setBackgroundResource(R.drawable.ic_game_menu_btn_selector);
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        body.addView(close, lp);
        cancel = close;
        ScrollView scroller = new ScrollView(getActivity());
        scroller.addView(body);
        return AppDialog.createCustomDialog(getActivity(), scroller, true);
    }

    @Override public void onStart() {
        super.onStart();
        AppDialog.showCustomDialog(getActivity(), (AlertDialog) getDialog(), 0.72f, 340,
                choices[active], cancel, choices);
        getDialog().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onDestroyView() {
        choices = null;
        cancel = null;
        super.onDestroyView();
    }

    static String name(int index) { return String.valueOf((char) ('A' + index)); }

    private TextView label(String text, int size) {
        TextView label = new TextView(getActivity());
        label.setText(text);
        label.setTextColor(0xFFEEEEEE);
        label.setTextSize(size);
        return label;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
