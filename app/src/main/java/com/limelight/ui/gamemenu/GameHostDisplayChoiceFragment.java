package com.limelight.ui.gamemenu;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Independent game-menu selector; tapping an option applies it immediately to the PC. */
public class GameHostDisplayChoiceFragment extends BaseGameMenuDialog {
    static final String TAG = "host-display-choice";

    private static final class Choice {
        final String key;
        final String title;
        final JSONObject operation;

        Choice(String key, String title, JSONObject operation) {
            this.key = key;
            this.title = title;
            this.operation = operation;
        }
    }

    private final List<Choice> choices = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView status;
    private JSONObject device;
    private String kind;
    private String selectedKey;
    private boolean busy;

    @Override public int getLayoutRes() {
        return R.layout.dialog_game_menu_host_display_choice;
    }

    @Override public String getFragmentTag() {
        return TAG;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selectedKey = state.getString("selected_key");
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("selected_key", selectedKey);
    }

    @Override public void bindView(View view) {
        super.bindView(view);
        content = view.findViewById(R.id.host_display_choice_content);
        scroll = view.findViewById(R.id.host_display_choice_scroll);
        status = view.findViewById(R.id.host_display_choice_status);
        view.findViewById(R.id.ibtn_back).setOnClickListener(v -> dismiss());
        try {
            device = new JSONObject(getArguments().getString("device", "{}"));
            kind = getArguments().getString("kind", "");
            ((TextView) view.findViewById(R.id.tx_title)).setText(title());
            buildChoices();
            render();
        } catch (JSONException error) {
            status.setText("无法读取显示参数，请返回刷新后重试");
        }
    }

    private String title() {
        switch (kind) {
            case "resolution": return "分辨率";
            case "refresh": return "刷新率";
            case "scale": return "缩放比例";
            default: return "HDR";
        }
    }

    private JSONObject operation(String action) throws JSONException {
        return new JSONObject().put("device_id", device.getString("device_id")).put("action", action);
    }

    /** Match refresh internally while keeping the resolution list limited to width and height. */
    private void buildChoices() throws JSONException {
        choices.clear();
        int width = device.optInt("width");
        int height = device.optInt("height");
        int currentHz = device.optInt("mode_refresh_hz", (int) Math.round(device.optDouble("refresh_hz")));
        if ("resolution".equals(kind) || "refresh".equals(kind)) {
            List<HostDisplayModes.Mode> modes = new ArrayList<>();
            JSONArray values = device.optJSONArray("modes");
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    JSONObject mode = values.optJSONObject(i);
                    if (mode != null) modes.add(new HostDisplayModes.Mode(mode.getInt("width"),
                            mode.getInt("height"), mode.getInt("refresh_hz")));
                }
            }
            boolean resolution = "resolution".equals(kind);
            List<HostDisplayModes.Mode> options = resolution ? HostDisplayModes.resolutions(modes, currentHz)
                    : HostDisplayModes.refreshRates(modes, width, height);
            for (HostDisplayModes.Mode mode : options) {
                String key = resolution ? mode.width + "x" + mode.height : String.valueOf(mode.hz);
                JSONObject request = operation("mode").put("width", mode.width).put("height", mode.height)
                        .put("refresh_hz", mode.hz);
                choices.add(new Choice(key, resolution ? mode.width + " × " + mode.height : mode.hz + " Hz", request));
                if (selectedKey == null && (resolution ? mode.width == width && mode.height == height : mode.hz == currentHz)) {
                    selectedKey = key;
                }
            }
        } else if ("scale".equals(kind)) {
            JSONArray values = device.optJSONArray("scale_options");
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    int percent = values.getInt(i);
                    String key = String.valueOf(percent);
                    choices.add(new Choice(key, percent + "%", operation("scale").put("percent", percent)));
                    if (selectedKey == null && percent == device.optInt("scale_percent")) selectedKey = key;
                }
            }
        } else if ("hdr".equals(kind)) {
            choices.add(new Choice("on", "开启", operation("hdr").put("enabled", true)));
            choices.add(new Choice("off", "关闭", operation("hdr").put("enabled", false)));
            if (selectedKey == null) selectedKey = device.optBoolean("hdr_enabled") ? "on" : "off";
        }
    }

    private Choice selected() {
        for (Choice choice : choices) if (choice.key.equals(selectedKey)) return choice;
        return null;
    }

    private void render() {
        int scrollY = scroll.getScrollY();
        View focus = content.findFocus();
        Object focusTag = focus == null ? null : focus.getTag();
        content.removeAllViews();
        HostDisplayMenuViews.section(content, "可用" + title());
        for (Choice choice : choices) {
            HostDisplayMenuViews.row(content, choice.title, "", "", "choice-" + choice.key,
                    choice.key.equals(selectedKey), false, !busy, () -> {
                        selectedKey = choice.key;
                        submit();
                    });
        }
        if (busy) {
            status.setText("正在应用主机显示设置…");
        } else if (choices.isEmpty()) {
            status.setText("此显示器暂无可用选项，请返回刷新后重试。");
        } else if ("refresh".equals(kind)) {
            status.setText(device.optInt("width") + " × " + device.optInt("height") + " · 点击刷新率即可应用。");
        } else {
            status.setText("点击选项即可应用到 PC 显示器。");
        }
        if (focusTag != null) {
            View restored = content.findViewWithTag(focusTag);
            if (restored != null) restored.requestFocus();
        }
        scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, scrollY); });
    }

    private void submit() {
        Choice picked = selected();
        if (busy || picked == null) return;
        if (!(getTargetFragment() instanceof GameHostDisplaysFragment)) {
            status.setText("显示器页面已关闭，请返回重试");
            return;
        }
        busy = true;
        render();
        ((GameHostDisplaysFragment) getTargetFragment()).submitChoice(picked.operation, this);
    }

    /** Close only after a successful host reply; retain a failed selection for an explicit retry. */
    void complete(boolean success, String error) {
        if (content == null || !isAdded()) return;
        busy = false;
        if (success) {
            dismiss();
        } else {
            render();
            status.setText("应用失败：" + error);
        }
    }

    @Override public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getTargetFragment() instanceof GameHostDisplaysFragment) {
            ((GameHostDisplaysFragment) getTargetFragment()).onChoiceDismissed();
        }
    }

    @Override public void onDestroyView() {
        content = null;
        scroll = null;
        busy = false;
        super.onDestroyView();
    }
}
