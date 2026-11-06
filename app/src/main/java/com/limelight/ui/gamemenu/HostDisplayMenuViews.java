package com.limelight.ui.gamemenu;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/** Shared typography, spacing and focusable rows matching the existing game display menu. */
final class HostDisplayMenuViews {
    static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static TextView label(LinearLayout parent, String value, int size, int color) {
        TextView label = new TextView(parent.getContext());
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        return label;
    }

    static void section(LinearLayout parent, String value) {
        TextView label = label(parent, value, 12, 0xffffffff);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(parent, 10), 0, dp(parent, 5));
        parent.addView(label, params);
    }

    static TextView note(LinearLayout parent, String value) {
        TextView label = label(parent, value, 8, 0xffbbbbbb);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(parent, 8), 0, dp(parent, 5));
        parent.addView(label, params);
        return label;
    }

    /** Keep values right-aligned and use the same arrow asset and 48dp height as other menus. */
    static View row(LinearLayout parent, String title, String subtitle, String value,
                    String tag, boolean selected, boolean arrow, boolean enabled, Runnable click) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(parent, 10), 0, dp(parent, 10), 0);
        row.setBackgroundResource(R.drawable.ic_game_menu_btn_selector);
        row.setTag(tag);
        row.setFocusable(enabled);
        row.setClickable(enabled);
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        row.setContentDescription(title + (subtitle.isEmpty() ? "" : "，" + subtitle)
                + (value.isEmpty() ? "" : "，" + value) + (selected ? "，已选中" : ""));
        row.setOnClickListener(view -> { if (enabled) click.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(parent, 48));
        params.topMargin = dp(parent, 8);
        parent.addView(row, params);

        LinearLayout labels = new LinearLayout(parent.getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = label(parent, title, 11, 0xffffffff);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        if (!subtitle.isEmpty()) {
            TextView detail = label(parent, subtitle, 8, 0xffbbbbbb);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(detail);
        }
        if (!value.isEmpty()) {
            TextView current = label(parent, value, 11, 0xffdddddd);
            current.setSingleLine(true);
            current.setGravity(Gravity.END);
            LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(-2, -2);
            currentParams.leftMargin = dp(parent, 8);
            row.addView(current, currentParams);
        }
        if (selected) {
            View dot = new View(parent.getContext());
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xff76c900);
            dot.setBackground(background);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(parent, 6), dp(parent, 6));
            dotParams.leftMargin = dp(parent, 10);
            row.addView(dot, dotParams);
        }
        if (arrow) {
            ImageView chevron = new ImageView(parent.getContext());
            chevron.setImageResource(R.drawable.ic_axi_arrow);
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(parent, 24), dp(parent, 24));
            arrowParams.leftMargin = dp(parent, 4);
            row.addView(chevron, arrowParams);
        }
        return row;
    }
}
