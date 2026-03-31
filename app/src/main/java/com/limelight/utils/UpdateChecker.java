package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.BuildConfig;
import com.limelight.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateChecker {

    private static final String UPDATE_CONFIG_URL = "https://axixi2233.github.io/res/config/anappversion.json";
    private static final String FALLBACK_UPDATE_URL = "https://pan.quark.cn/s/9a334d831290";
    private static final String PREF_SKIPPED_UPDATE_CODE = "pref_skipped_update_code";

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Gson GSON = new Gson();

    private UpdateChecker() {
    }

    public static void checkForUpdates(Activity activity, boolean interactive) {
        SpinnerDialog spinner = null;
        if (interactive) {
            spinner = SpinnerDialog.displayDialog(activity, "检查更新", "正在获取版本信息...", false);
        }

        SpinnerDialog finalSpinner = spinner;
        Request request = new Request.Builder()
                .url(UPDATE_CONFIG_URL)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThreadSafely(activity, () -> {
                    dismissSpinner(finalSpinner);
                    if (interactive) {
                        showToast(activity, "检查更新失败，已打开默认下载链接");
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response ignored = response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        runOnUiThreadSafely(activity, () -> {
                            dismissSpinner(finalSpinner);
                            if (interactive) {
                                showToast(activity, "检查更新失败，已打开默认下载链接");
                            }
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    UpdateResponse updateResponse = parseUpdateResponse(responseBody);

                    runOnUiThreadSafely(activity, () -> {
                        dismissSpinner(finalSpinner);
                        handleUpdateResponse(activity, updateResponse, interactive);
                    });
                } catch (Exception e) {
                    runOnUiThreadSafely(activity, () -> {
                        dismissSpinner(finalSpinner);
                        if (interactive) {
                            showToast(activity, "更新信息解析失败，已打开默认下载链接");
                        }
                    });
                }
            }
        });
    }

    private static void handleUpdateResponse(Activity activity, UpdateResponse updateResponse, boolean interactive) {
        UpdateRelease latest = null;
        if (updateResponse != null && updateResponse.data != null) {
            latest = updateResponse.data.latest;
        }

        if (latest == null || latest.code <= 0) {
            if (interactive) {
                showToast(activity, "未找到可用更新信息");
            }
            return;
        }

        if (latest.code <= BuildConfig.AXI_CODE) {
            if (interactive) {
                showToast(activity, "当前已是最新版本");
            }
            return;
        }

        if (!interactive && latest.code <= getSkippedUpdateCode(activity)) {
            return;
        }

        showUpdateDialog(activity, latest, normalizeDescription(latest.desc), interactive);
    }

    private static void showUpdateDialog(Activity activity, UpdateRelease latest, String description, boolean interactive) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_prompt, null, false);

        TextView versionsView = dialogView.findViewById(R.id.tv_update_versions);
        TextView descView = dialogView.findViewById(R.id.tv_update_desc);
        View skipRow = dialogView.findViewById(R.id.layout_skip_row);
        View skipButton = dialogView.findViewById(R.id.btn_skip_update);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel_update);
        View downloadButton = dialogView.findViewById(R.id.btn_download_update);

        versionsView.setText("当前版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.AXI_CODE + ")"
                + "\n最新版本：" + safeText(latest.versionName, "未知版本") + " (" + latest.code + ")");
        descView.setText(TextUtils.isEmpty(description) ? "暂无更新说明。" : description);
        skipRow.setVisibility(interactive ? View.GONE : View.VISIBLE);

        AlertDialog dialog = buildDialog(activity, dialogView);
        if (dialog == null) {
            return;
        }

        skipButton.setOnClickListener(v -> {
            saveSkippedUpdateCode(activity, latest.code);
            dialog.dismiss();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        downloadButton.setOnClickListener(v -> {
            dialog.dismiss();
            showDownloadOptions(activity, latest);
        });

        showDialog(dialog);
    }

    private static void showDownloadOptions(Activity activity, UpdateRelease latest) {
        List<String> labels = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        addDownloadOption(labels, urls, "GitHub Releases", latest.github);
        addDownloadOption(labels, urls, "夸克网盘", latest.quark);
        addDownloadOption(labels, urls, "百度网盘", latest.baidu);

        if (urls.isEmpty()) {
            showToast(activity, "当前没有可用下载地址");
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_channels, null, false);
        LinearLayout channelContainer = dialogView.findViewById(R.id.layout_download_channels);
        View cancelButton = dialogView.findViewById(R.id.btn_cancel_channels);

        for (int i = 0; i < labels.size(); i++) {
            View channelView = LayoutInflater.from(activity).inflate(R.layout.item_update_channel, channelContainer, false);
            TextView channelNameView = channelView.findViewById(R.id.tv_channel_name);
            String label = labels.get(i);
            String url = urls.get(i);
            channelNameView.setText(label);
            channelView.setTag(url);
            channelContainer.addView(channelView);
        }

        AlertDialog dialog = buildDialog(activity, dialogView);
        if (dialog == null) {
            return;
        }

        for (int i = 0; i < channelContainer.getChildCount(); i++) {
            View channelItem = channelContainer.getChildAt(i);
            channelItem.setOnClickListener(v -> {
                dialog.dismiss();
                Object taggedUrl = v.getTag();
                if (taggedUrl instanceof String) {
                    openUrl(activity, (String) taggedUrl);
                }
            });
        }
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        showDialog(dialog);
    }

    private static AlertDialog buildDialog(Activity activity, View dialogView) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private static void showDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static void addDownloadOption(List<String> labels, List<String> urls, String label, String url) {
        if (!TextUtils.isEmpty(url)) {
            labels.add(label);
            urls.add(url);
        }
    }

    private static void dismissSpinner(SpinnerDialog spinner) {
        if (spinner != null) {
            spinner.dismiss();
        }
    }

    private static int getSkippedUpdateCode(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        return prefs.getInt(PREF_SKIPPED_UPDATE_CODE, 0);
    }

    private static void saveSkippedUpdateCode(Activity activity, int code) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.edit().putInt(PREF_SKIPPED_UPDATE_CODE, code).apply();
    }

    private static UpdateResponse parseUpdateResponse(String responseBody) {
        try {
            return GSON.fromJson(responseBody, UpdateResponse.class);
        } catch (Exception ignored) {
            UpdateRelease latest = parseLatestReleaseFallback(responseBody);
            if (latest == null) {
                return null;
            }

            UpdateResponse updateResponse = new UpdateResponse();
            updateResponse.data = new UpdateData();
            updateResponse.data.latest = latest;
            return updateResponse;
        }
    }

    private static UpdateRelease parseLatestReleaseFallback(String responseBody) {
        String latestBlock = extractLatestBlock(responseBody);
        if (TextUtils.isEmpty(latestBlock)) {
            return null;
        }

        UpdateRelease latest = new UpdateRelease();
        latest.code = parseIntField(latestBlock, "code", 0);
        latest.versionName = parseStringField(latestBlock, "versionName");
        latest.desc = parseDescField(latestBlock);
        latest.github = parseStringField(latestBlock, "github");
        latest.quark = parseStringField(latestBlock, "quark");
        latest.baidu = parseStringField(latestBlock, "baidu");

        if (latest.code <= 0 && TextUtils.isEmpty(latest.versionName)) {
            return null;
        }
        return latest;
    }

    private static String extractLatestBlock(String responseBody) {
        int latestKeyIndex = responseBody.indexOf("\"latest\"");
        if (latestKeyIndex < 0) {
            return null;
        }

        int objectStart = responseBody.indexOf('{', latestKeyIndex);
        if (objectStart < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = objectStart; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);

            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return responseBody.substring(objectStart, i + 1);
                }
            }
        }

        return null;
    }

    private static int parseIntField(String source, String fieldName, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)").matcher(source);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String parseStringField(String source, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL).matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return cleanupJsonString(matcher.group(1));
    }

    private static String parseDescField(String latestBlock) {
        Matcher matcher = Pattern.compile("\"desc\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"(?:github|quark|baidu)\"", Pattern.DOTALL).matcher(latestBlock);
        if (matcher.find()) {
            return cleanupJsonString(matcher.group(1));
        }
        return parseStringField(latestBlock, "desc");
    }

    private static String cleanupJsonString(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .trim();
    }

    private static String normalizeDescription(String description) {
        if (TextUtils.isEmpty(description)) {
            return "";
        }
        return description
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .trim();
    }

    private static String safeText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    public static void openUrl(Activity activity, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(intent);
        } else {
            showToast(activity, "未找到可用浏览器");
        }
    }

    private static void runOnUiThreadSafely(Activity activity, Runnable runnable) {
        if (activity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(runnable);
    }

    private static void showToast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static final class UpdateResponse {
        private UpdateData data;
    }

    private static final class UpdateData {
        private UpdateRelease latest;
    }

    private static final class UpdateRelease {
        private int code;
        private String versionName;
        private String desc;
        private String github;
        private String quark;
        private String baidu;
    }
}
