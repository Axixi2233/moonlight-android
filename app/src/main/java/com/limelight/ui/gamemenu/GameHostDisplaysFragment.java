package com.limelight.ui.gamemenu;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.http.HostDisplayApiUnavailableException;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.utils.UiHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Paired PC displays, using the same rows and independent sheets as the game display menu. */
public class GameHostDisplaysFragment extends BaseGameMenuDialog {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView status;
    private JSONArray devices = new JSONArray();
    private String selectedId = "";
    private String lastInventory = "";
    private boolean busy;
    private final HostDisplayCaptureState captureSwitch = new HostDisplayCaptureState();
    private int generation;
    private final Runnable refresh = () -> load(null, null);

    @Override public int getLayoutRes() {
        return R.layout.dialog_game_menu_host_displays;
    }

    @Override public void bindView(View view) {
        super.bindView(view);
        worker = Executors.newSingleThreadExecutor();
        content = view.findViewById(R.id.host_displays_content);
        scroll = view.findViewById(R.id.host_displays_scroll);
        status = view.findViewById(R.id.host_displays_status);
        view.findViewById(R.id.ibtn_back).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.host_displays_refresh).setOnClickListener(v -> load(null, null));
        load(null, null);
    }

    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("selected_id", selectedId);
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState != null) selectedId = savedState.getString("selected_id", "");
    }

    private boolean choicesOpen() {
        return getFragmentManager() != null && getFragmentManager()
                .findFragmentByTag(GameHostDisplayChoiceFragment.TAG) != null;
    }

    /** Keep HTTP off the UI thread, suspend polling behind a choice sheet and reject stale replies. */
    private void load(JSONObject operation, GameHostDisplayChoiceFragment choice) {
        if (busy || content == null || !(getActivity() instanceof Game)) {
            if (choice != null) choice.complete(false, "正在读取显示器，请稍后重试");
            return;
        }
        if (operation == null && choicesOpen()) return;
        busy = true;
        main.removeCallbacks(refresh);
        int requestGeneration = generation;
        Game game = (Game) getActivity();
        if (operation != null) showStatus(captureSwitch.isPending()
                ? "正在切换串流显示器…" : "正在应用主机显示设置…");
        worker.execute(() -> {
            JSONObject inventory = null;
            String failure = null;
            boolean legacyHost = false;
            try {
                if (operation != null) {
                    JSONObject applied = game.requestHostDisplays(operation);
                    if (!applied.optBoolean("status")) {
                        failure = applied.optString("message", "主机拒绝了这项设置");
                    }
                }
                inventory = game.requestHostDisplays(null);
                if (!inventory.optBoolean("status")) {
                    throw new java.io.IOException(inventory.optString("message", "无法读取显示器"));
                }
            } catch (Exception error) {
                legacyHost = operation == null && error instanceof HostDisplayApiUnavailableException;
                failure = error instanceof java.io.FileNotFoundException
                        ? "请更新 StarCore星核 PC 端以使用显示器管理"
                        : error.getMessage() == null ? "无法连接主机" : error.getMessage();
            }
            JSONObject result = inventory;
            String error = failure;
            boolean useLegacyControls = legacyHost;
            main.post(() -> {
                if (generation != requestGeneration || content == null || !isAdded()) return;
                busy = false;
                if (useLegacyControls && getTargetFragment() instanceof GameMenuFragment
                        && getDialog() != null && getDialog().isShowing()) {
                    game.markHostDisplayApiUnavailable();
                    captureSwitch.failed();
                    main.removeCallbacks(refresh);
                    ((GameMenuFragment) getTargetFragment()).showLegacyDisplayControls();
                    dismiss();
                    return;
                }
                if (result != null && result.optBoolean("status")) {
                    JSONArray list = result.optJSONArray("devices");
                    devices = list == null ? new JSONArray() : list;
                }
                if (error != null) {
                    captureSwitch.failed();
                    showStatus("读取或应用失败：" + error + "，可点击刷新重试");
                    render();
                    if (choice != null) choice.complete(false, error);
                    return;
                }
                String capturedId = "";
                boolean targetActive = false;
                for (int i = 0; i < devices.length(); i++) {
                    JSONObject row = devices.optJSONObject(i);
                    if (row == null) continue;
                    if (row.optBoolean("active") && row.optBoolean("capturing")) capturedId = row.optString("device_id");
                    if (captureSwitch.pendingId().equals(row.optString("device_id"))) targetActive = row.optBoolean("active");
                }
                HostDisplayCaptureState.Result switched = captureSwitch.observe(capturedId, targetActive,
                        SystemClock.elapsedRealtime());
                if (switched == HostDisplayCaptureState.Result.UNAVAILABLE
                        || switched == HostDisplayCaptureState.Result.TIMED_OUT) {
                    showStatus(switched == HostDisplayCaptureState.Result.UNAVAILABLE
                            ? "目标显示器已断开，未完成切换，可点击刷新重试"
                            : "切换尚未完成，请确认当前串流画面后刷新重试");
                    render();
                    return;
                }
                if (captureSwitch.isPending()) {
                    showStatus("正在切换串流显示器…");
                } else {
                    status.setVisibility(devices.length() == 0 ? View.VISIBLE : View.GONE);
                    status.setText("PC 当前没有可用显示器");
                }
                if (!devices.toString().equals(lastInventory) || operation != null || content.getChildCount() == 0
                        || switched == HostDisplayCaptureState.Result.COMPLETE) {
                    lastInventory = devices.toString();
                    render();
                }
                if (choice != null) choice.complete(true, null);
                if (!choicesOpen()) main.postDelayed(refresh, captureSwitch.isPending() ? 500 : 2500);
            });
        });
    }

    private void showStatus(String message) {
        status.setVisibility(View.VISIBLE);
        status.setText(message);
    }

    private JSONObject selected() {
        JSONObject previous = null;
        JSONObject capturing = null;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject row = devices.optJSONObject(i);
            if (row != null && selectedId.equals(row.optString("device_id"))) previous = row;
            if (row != null && row.optBoolean("active") && row.optBoolean("capturing")) capturing = row;
        }
        JSONObject result = capturing != null ? capturing : previous != null ? previous : devices.optJSONObject(0);
        if (result != null) selectedId = result.optString("device_id");
        return result;
    }

    /** Preserve the main sheet's selection, focus and scroll position when inventory changes. */
    private void render() {
        if (content == null) return;
        int scrollY = scroll.getScrollY();
        View focused = content.findFocus();
        Object focusedTag = focused == null ? null : focused.getTag();
        content.removeAllViews();
        JSONObject device = selected();
        if (device == null) return;
        HostDisplayMenuViews.section(content, "显示器");
        for (int i = 0; i < devices.length(); i++) {
            JSONObject row = devices.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("device_id");
            String detail = row.optBoolean("primary") ? "主屏" : "";
            if (row.optBoolean("capturing")) detail += (detail.isEmpty() ? "" : " · ") + "正在串流";
            if (!row.optBoolean("active")) detail += (detail.isEmpty() ? "" : " · ") + "未启用";
            HostDisplayMenuViews.row(content, row.optString("name", "显示器"), detail, "", "device-" + id,
                    row.optBoolean("active") && row.optBoolean("capturing"), false,
                    row.optBoolean("active") && !captureSwitch.isPending(),
                    () -> submitCapture(id, row.optBoolean("active"), row.optBoolean("capturing")));
        }
        HostDisplayMenuViews.section(content, "显示参数");
        boolean active = device.optBoolean("active");
        boolean editable = active && !captureSwitch.isPending();
        JSONArray modes = device.optJSONArray("modes");
        boolean hasModes = editable && modes != null && modes.length() > 0;
        parameter("分辨率", active ? device.optInt("width") + " × " + device.optInt("height") : "未启用",
                "resolution", hasModes);
        parameter("刷新率", active ? hz(device.optDouble("refresh_hz")) + " Hz" : "未启用", "refresh", hasModes);
        JSONArray scales = device.optJSONArray("scale_options");
        parameter("缩放比例", device.isNull("scale_percent") ? "不可用" : device.optInt("scale_percent") + "%",
                "scale", editable && scales != null && scales.length() > 0);
        boolean hdr = device.optBoolean("hdr_supported");
        parameter("HDR", hdr ? device.optBoolean("hdr_enabled") ? "开启" : "关闭" : "不支持", "hdr", editable && hdr);
        HostDisplayMenuViews.note(content, "以上设置作用于 PC 显示器，与串流分辨率和帧率分别设置。");
        if (focusedTag != null) {
            View restored = content.findViewWithTag(focusedTag);
            if (restored != null) restored.requestFocus();
        }
        scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, scrollY); });
    }

    private void parameter(String label, String value, String kind, boolean enabled) {
        HostDisplayMenuViews.row(content, label, "", value, kind, false, true, enabled, () -> choose(kind));
    }

    private void choose(String kind) {
        if (busy || captureSwitch.isPending() || choicesOpen()) return;
        JSONObject device = selected();
        if (device == null) return;
        main.removeCallbacks(refresh);
        GameHostDisplayChoiceFragment choice = new GameHostDisplayChoiceFragment();
        Bundle arguments = new Bundle();
        arguments.putString("device", device.toString());
        arguments.putString("kind", kind);
        choice.setArguments(arguments);
        choice.setTargetFragment(this, 0);
        choice.setWidth(UiHelper.dpToPx(getActivity(), 364));
        choice.show(getFragmentManager(), GameHostDisplayChoiceFragment.TAG);
    }

    /** Apply a choice using the exact device ID and supported mode captured by its own sheet. */
    void submitChoice(JSONObject operation, GameHostDisplayChoiceFragment choice) {
        load(operation, choice);
    }

    void onChoiceDismissed() {
        if (content != null) {
            main.removeCallbacks(refresh);
            main.postDelayed(refresh, 300);
        }
    }

    /** A monitor-row click starts the switch; the green dot follows host capture observations only. */
    private void submitCapture(String id, boolean active, boolean capturing) {
        if (busy) return;
        try {
            JSONObject operation = new JSONObject().put("device_id", id).put("action", "capture");
            if (!captureSwitch.request(id, active, capturing, SystemClock.elapsedRealtime())) return;
            render();
            load(operation, null);
        } catch (JSONException error) {
            captureSwitch.failed();
            showStatus("显示设置参数无效");
        }
    }

    private String hz(double value) {
        return String.format(Locale.US, Math.abs(value - Math.round(value)) < 0.01 ? "%.0f" : "%.2f", value);
    }

    @Override public void onDestroyView() {
        ++generation;
        main.removeCallbacks(refresh);
        if (worker != null) worker.shutdownNow();
        content = null;
        scroll = null;
        busy = false;
        captureSwitch.failed();
        lastInventory = "";
        super.onDestroyView();
    }
}
