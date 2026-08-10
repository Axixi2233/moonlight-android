package com.limelight;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.utils.UpdateChecker;

import java.util.List;

public final class VersionHistoryActivity extends BaseActivity {
    private View contentView;
    private View statusPanel;
    private TextView statusView;
    private TextView retryButton;
    private LinearLayout listView;
    private boolean restoreListFocusAfterRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_history);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        contentView = findViewById(R.id.version_history_scroll);
        statusPanel = findViewById(R.id.version_history_status_panel);
        statusView = findViewById(R.id.tv_version_history_status);
        retryButton = findViewById(R.id.btn_version_history_retry);
        listView = findViewById(R.id.version_history_list);
        retryButton.setOnClickListener(v -> {
            restoreListFocusAfterRetry = retryButton.hasFocus();
            loadHistory();
        });

        loadHistory();
    }

    private void loadHistory() {
        showStatus(R.string.version_history_loading, false);
        UpdateChecker.loadVersionHistory(this, new UpdateChecker.VersionHistoryCallback() {
            @Override
            public void onLoaded(List<UpdateChecker.VersionEntry> entries) {
                renderEntries(entries);
            }

            @Override
            public void onError() {
                showStatus(R.string.version_history_load_failed, true);
            }
        });
    }

    private void renderEntries(List<UpdateChecker.VersionEntry> entries) {
        listView.removeAllViews();
        if (entries == null || entries.isEmpty()) {
            showStatus(R.string.version_history_empty, true);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (UpdateChecker.VersionEntry entry : entries) {
            View item = inflater.inflate(R.layout.item_version_history, listView, false);
            TextView versionView = item.findViewById(R.id.tv_version_history_version);
            TextView badgeView = item.findViewById(R.id.tv_version_history_badge);
            TextView descriptionView = item.findViewById(R.id.tv_version_history_description);

            String versionName = TextUtils.isEmpty(entry.getVersionName())
                    ? getString(R.string.version_history_unknown)
                    : entry.getVersionName();
            String description = TextUtils.isEmpty(entry.getDescription())
                    ? getString(R.string.version_history_empty_description)
                    : entry.getDescription();
            versionView.setText(getString(R.string.version_history_version_format, versionName));
            descriptionView.setText(description);

            if (entry.getCode() == BuildConfig.AXI_CODE) {
                badgeView.setText(R.string.version_history_current);
                badgeView.setVisibility(View.VISIBLE);
            }
            else if (entry.isLatest()) {
                badgeView.setText(R.string.version_history_latest);
                badgeView.setVisibility(View.VISIBLE);
            }
            else {
                badgeView.setVisibility(View.GONE);
            }

            item.setContentDescription(versionView.getText() + "。" + description);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(10);
            listView.addView(item, params);
        }

        statusPanel.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        if (restoreListFocusAfterRetry && listView.getChildCount() > 0) {
            listView.getChildAt(0).requestFocus();
        }
        restoreListFocusAfterRetry = false;
    }

    private void showStatus(int messageResId, boolean canRetry) {
        contentView.setVisibility(View.GONE);
        statusPanel.setVisibility(View.VISIBLE);
        statusView.setText(messageResId);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        if (canRetry) {
            retryButton.requestFocus();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
