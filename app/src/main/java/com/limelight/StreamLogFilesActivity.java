package com.limelight;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.log.StreamLogStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StreamLogFilesActivity extends BaseActivity {
    private static final int REQUEST_EXPORT_LOG = 7001;

    private LinearLayout logList;
    private TextView emptyView;
    private File pendingExportFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream_logs);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        logList = findViewById(R.id.stream_log_list);
        emptyView = findViewById(R.id.stream_log_empty);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        logList.removeAllViews();
        List<File> files = StreamLogStore.list(this);
        emptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        for (File file : files) {
            logList.addView(createLogRow(file));
        }
    }

    private View createLogRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackgroundResource(R.drawable.bg_update_dialog_card_selector);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        row.setLayoutParams(params);
        row.setFocusable(true);
        row.setClickable(true);

        TextView title = new TextView(this);
        title.setText(file.getName());
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setSingleLine(true);
        row.addView(title);

        TextView detail = new TextView(this);
        detail.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(file.lastModified())) + "  ·  " + Formatter.formatShortFileSize(this, file.length()));
        detail.setTextColor(0xB3FFFFFF);
        detail.setTextSize(11);
        detail.setPadding(0, dp(5), 0, 0);
        row.addView(detail);

        row.setOnClickListener(v -> showActions(file));
        return row;
    }

    private void showActions(File file) {
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(new String[]{"查看", "导出", "删除"}, (dialog, which) -> {
                    if (which == 0) {
                        showPreview(file);
                    }
                    else if (which == 1) {
                        exportFile(file);
                    }
                    else {
                        confirmDelete(file);
                    }
                })
                .show();
    }

    private void showPreview(File file) {
        String content = StreamLogStore.readPreview(this, file, 24000);
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(content.isEmpty() ? "日志内容为空" : content)
                .setPositiveButton("关闭", null)
                .setNeutralButton("导出", (dialog, which) -> exportFile(file))
                .show();
    }

    private void exportFile(File file) {
        pendingExportFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        startActivityForResult(intent, REQUEST_EXPORT_LOG);
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle("删除日志")
                .setMessage("确定删除这份串流日志吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (StreamLogStore.delete(this, file)) {
                        refreshList();
                    }
                    else {
                        Toast.makeText(this, "日志删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_LOG) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || pendingExportFile == null) {
            pendingExportFile = null;
            return;
        }
        Uri destination = data.getData();
        boolean exported = destination != null && StreamLogStore.export(this, pendingExportFile, destination);
        pendingExportFile = null;
        Toast.makeText(this, exported ? "日志已导出" : "日志导出失败", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
