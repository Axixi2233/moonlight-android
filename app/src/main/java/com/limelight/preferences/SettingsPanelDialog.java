package com.limelight.preferences;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuFragmentDialog;
import org.apmem.tools.layouts.FlowLayout;

import java.math.BigDecimal;

/** Persistent settings UI. The legacy fragment remains the capability and action model only. */
public class SettingsPanelDialog extends BaseGameMenuFragmentDialog {
    public static final String TAG = "settings_panel";
    private static final String HUD = "performance_overlay_mode";
    private static final String CUSTOM_RESOLUTION = "edit_diy_w_h";
    private int group = -1;
    private String editor;
    private String draft;
    private int groupScroll;
    private int hubScroll;
    private int restoredScroll = -1;
    private String returnFocus;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView title;
    private EditText input;
    private EditText heightInput;
    private TextView inputError;
    private android.window.OnBackInvokedCallback backCallback;
    private android.window.OnBackInvokedDispatcher backDispatcher;

    @Override public int getLayoutRes() { return R.layout.dialog_settings_panel; }
    @Override public String getFragmentTag() { return TAG; }
    @Override public int getViewSize() {
        return Math.min(dp(460), getResources().getDisplayMetrics().widthPixels);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            group = state.getInt("group", -1);
            editor = state.getString("editor");
            draft = state.getString("draft");
            groupScroll = state.getInt("groupScroll");
            hubScroll = state.getInt("hubScroll");
            returnFocus = state.getString("returnFocus");
            restoredScroll = state.getInt("scroll");
            if (group >= SettingsCatalog.GROUPS.length) {
                group = -1;
                editor = null;
                draft = null;
            }
        }
    }

    @Override public void onSaveInstanceState(Bundle state) {
        state.putInt("group", group);
        state.putString("editor", editor);
        state.putString("draft", currentDraft());
        state.putInt("groupScroll", groupScroll);
        state.putInt("hubScroll", hubScroll);
        state.putString("returnFocus", returnFocus);
        state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        return new Dialog(getActivity(), getTheme()) {
            @Override public void cancel() {
                // Outside taps must navigate before Dialog.cancel() can dismiss the window
                // and enqueue removal of this fragment. onCancel() would be too late.
                goBack();
            }
        };
    }

    @Override public void bindView(View view) {
        content = view.findViewById(R.id.settings_content);
        scroll = view.findViewById(R.id.settings_scroll);
        title = view.findViewById(R.id.settings_title);
        view.findViewById(R.id.settings_back).setOnClickListener(v -> goBack());
        view.findViewById(R.id.settings_close).setOnClickListener(v -> closeSettings());
        view.setOnApplyWindowInsetsListener((root, insets) -> {
            android.view.DisplayCutout cutout = Build.VERSION.SDK_INT >= 28 ? insets.getDisplayCutout() : null;
            root.setPadding(dp(12) + (cutout == null ? 0 : cutout.getSafeInsetLeft()),
                    dp(12) + (cutout == null ? 0 : cutout.getSafeInsetTop()),
                    dp(12) + (cutout == null ? 0 : cutout.getSafeInsetRight()),
                    dp(12) + (cutout == null ? 0 : cutout.getSafeInsetBottom()));
            return insets;
        });
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B
                    || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) goBack();
                return true;
            }
            return false;
        });
        render();
    }

    @Override public void onStart() {
        super.onStart();
        resizeWindow();
        if (Build.VERSION.SDK_INT >= 33) {
            unregisterBackCallback();
            backCallback = this::goBack;
            backDispatcher = getDialog().getOnBackInvokedDispatcher();
            backDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
        }
    }

    @Override public void onStop() {
        unregisterBackCallback();
        super.onStop();
    }

    private void unregisterBackCallback() {
        // DialogFragment may already have cleared its Dialog during dismissal. Release
        // the registration from its original owner, independently of the current view.
        if (Build.VERSION.SDK_INT >= 33 && backDispatcher != null && backCallback != null) {
            backDispatcher.unregisterOnBackInvokedCallback(backCallback);
        }
        backDispatcher = null;
        backCallback = null;
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        resizeWindow();
    }

    private void resizeWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = landscape ? getViewSize() : WindowManager.LayoutParams.MATCH_PARENT;
        params.height = landscape ? WindowManager.LayoutParams.MATCH_PARENT
                : getResources().getDisplayMetrics().heightPixels * 2 / 3;
        params.gravity = landscape ? Gravity.END : Gravity.BOTTOM;
        // Pan the focused input above the IME without leaving immersive fullscreen.
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        getDialog().getWindow().setAttributes(params);
    }

    @Override public void onDestroyView() {
        unregisterBackCallback();
        content = null;
        scroll = null;
        title = null;
        input = null;
        heightInput = null;
        inputError = null;
        super.onDestroyView();
    }

    private StreamSettings.SettingsFragment model() {
        Activity activity = getActivity();
        return activity instanceof StreamSettings ? ((StreamSettings) activity).getSettingsModel() : null;
    }

    private Preference preference(String key) {
        StreamSettings.SettingsFragment model = model();
        return model == null ? null : model.findPreference(key);
    }

    public void refresh() {
        if (content == null) return;
        int position = restoredScroll >= 0 ? restoredScroll : scroll.getScrollY();
        restoredScroll = -1;
        View focus = content.findFocus();
        boolean editingHeight = focus != null && focus == heightInput;
        boolean editing = focus != null && (focus == input || editingHeight);
        while (focus != null && focus != content && !(focus.getTag() instanceof String)) {
            focus = focus.getParent() instanceof View ? (View) focus.getParent() : null;
        }
        String focusedKey = focus != null && focus.getTag() instanceof String ? (String) focus.getTag() : null;
        draft = currentDraft();
        render();
        scroll.post(() -> {
            if (scroll == null) return;
            if (editing && input != null) {
                EditText target = editingHeight && heightInput != null ? heightInput : input;
                target.requestFocus();
                target.setSelection(target.length());
            } else if (focusedKey != null && !scroll.isInTouchMode()) {
                View target = content.findViewWithTag(focusedKey);
                if (target != null) target.requestFocus();
            }
            scroll.scrollTo(0, position);
        });
    }

    private void closeSettings() {
        hideKeyboard();
        if (getActivity() != null) getActivity().onBackPressed();
    }

    private String currentDraft() {
        if (input == null) return draft;
        return input.getText().toString() + (heightInput == null ? "" : "x" + heightInput.getText());
    }

    private void goBack() {
        if (content == null || getActivity() == null || getActivity().isFinishing()) return;
        hideKeyboard();
        if (editor != null) {
            editor = null;
            draft = null;
            input = null;
            render();
            restorePosition(groupScroll);
        } else if (group >= 0) {
            returnFocus = "group_" + group;
            group = -1;
            render();
            restorePosition(hubScroll);
        } else {
            closeSettings();
        }
    }

    private void restorePosition(int position) {
        scroll.post(() -> {
            if (scroll == null) return;
            View focus = returnFocus == null ? null : content.findViewWithTag(returnFocus);
            if (focus != null && !scroll.isInTouchMode()) focus.requestFocus();
            scroll.scrollTo(0, position);
        });
    }

    private void hideKeyboard() {
        if (input != null) {
            ((InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void render() {
        if (content == null || model() == null) return;
        content.removeAllViews();
        input = null;
        heightInput = null;
        inputError = null;
        if (editor != null) {
            renderEditor();
        } else if (group < 0) {
            title.setText(R.string.settings_panel_title);
            paragraph(getString(R.string.settings_panel_hint));
            for (int i = 0; i < SettingsCatalog.GROUPS.length; i += 2) {
                LinearLayout line = new LinearLayout(getActivity());
                for (int j = i; j < Math.min(i + 2, SettingsCatalog.GROUPS.length); j++) {
                    final int index = j;
                    SettingsCatalog.Group item = SettingsCatalog.GROUPS[j];
                    LinearLayout tile = vertical();
                    tile.setPadding(dp(12), dp(10), dp(10), dp(10));
                    tile.setMinimumHeight(dp(68));
                    tile.addView(text(item.title, 15, Color.WHITE));
                    TextView hint = text(item.description, 11, 0xFFB4B4B4);
                    hint.setPadding(0, dp(4), 0, 0);
                    tile.addView(hint);
                    tile.setTag("group_" + j);
                    clickable(tile, () -> {
                        hubScroll = scroll.getScrollY();
                        group = index;
                        groupScroll = 0;
                        render();
                        scroll.scrollTo(0, 0);
                    });
                    // Measure the tile's own height. MATCH_PARENT lets the empty cell
                    // determine the height of an odd final row and clips its only tile.
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    lp.setMargins(dp(j == i ? 0 : 6), dp(6), 0, 0);
                    line.addView(tile, lp);
                }
                if (i + 1 == SettingsCatalog.GROUPS.length) {
                    View spacer = new View(getActivity());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
                    lp.leftMargin = dp(6);
                    line.addView(spacer, lp);
                }
                content.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        } else {
            SettingsCatalog.Group page = SettingsCatalog.GROUPS[group];
            title.setText(page.title);
            int rowCount = 0;
            for (SettingsCatalog.Section section : page.sections) {
                boolean headingAdded = false;
                for (String key : section.keys) {
                    Preference pref = preference(key);
                    if (!HUD.equals(key) && (pref == null || !visible(pref))) continue;
                    if (!headingAdded) {
                        heading(section.title);
                        headingAdded = true;
                    }
                    if (HUD.equals(key)) {
                        row("性能信息", hudLabel(), null, key, () -> openEditor(key));
                    } else {
                        addPreference(pref);
                    }
                    rowCount++;
                }
            }
            if (rowCount == 0) paragraph(getString(R.string.settings_panel_empty));
        }
        // Editors use the same immersive presentation as navigation pages. Keyboard
        // avoidance is handled by adjustPan, rather than exposing the status bar.
        getDialog().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private boolean checked(String key) {
        Preference pref = preference(key);
        return pref instanceof CheckBoxPreference && ((CheckBoxPreference) pref).isChecked();
    }

    private boolean visible(Preference pref) {
        String key = pref.getKey();
        if ("checkbox_enable_hdr_high_brightness".equals(key)) return checked("checkbox_enable_hdr");
        if (key.startsWith("checkbox_enable_perf_overlay_lite_") || "performance_overlayLite_magin_top".equals(key)) {
            return checked("checkbox_enable_perf_overlay_lite") && checked("checkbox_enable_perf_overlay");
        }
        if ("checkbox_enable_screen_obscure".equals(key)) return checked("checkbox_enable_screen_bg") && Build.VERSION.SDK_INT >= 31;
        if ("checkbox_enable_screen_bg".equals(key) || "import_image_file_key".equals(key)) return Build.VERSION.SDK_INT >= 29;
        return true;
    }

    private void addPreference(Preference pref) {
        Boolean state = pref instanceof CheckBoxPreference ? ((CheckBoxPreference) pref).isChecked() : null;
        LinearLayout view = row(pref.getTitle(), summary(pref), state, pref.getKey(), () -> {
            if (!pref.isEnabled()) return;
            if (pref instanceof CheckBoxPreference) {
                CheckBoxPreference toggle = (CheckBoxPreference) pref;
                boolean value = !toggle.isChecked();
                if (acceptChange(toggle, value)) toggle.setChecked(value);
                refresh();
            } else if (pref instanceof LanguagePreference && Build.VERSION.SDK_INT >= 33) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.parse("package:" + getActivity().getPackageName())));
                } catch (ActivityNotFoundException e) {
                    openEditor(pref.getKey());
                }
            } else if (pref instanceof ListPreference || pref instanceof EditTextPreference || pref instanceof SeekBarPreference) {
                openEditor(pref.getKey());
            } else if (pref.getOnPreferenceClickListener() != null) {
                pref.getOnPreferenceClickListener().onPreferenceClick(pref);
            } else if (pref instanceof WebLauncherPreference) {
                ((WebLauncherPreference) pref).onClick();
            }
        });
        view.setEnabled(pref.isEnabled());
        view.setAlpha(pref.isEnabled() ? 1f : 0.4f);
    }

    private CharSequence summary(Preference pref) {
        if (pref instanceof ListPreference) {
            ListPreference list = (ListPreference) pref;
            return list.getEntry() == null ? list.getValue() : list.getEntry();
        }
        if (pref instanceof SeekBarPreference) {
            SeekBarPreference seek = (SeekBarPreference) pref;
            return number(seek.getSavedValue(), seek.getDivisor()) + " " + (seek.getSuffix() == null ? "" : seek.getSuffix());
        }
        if (pref instanceof EditTextPreference) return ((EditTextPreference) pref).getText();
        return pref.getSummary();
    }

    private void openEditor(String key) {
        if (editor == null) groupScroll = scroll.getScrollY();
        returnFocus = "edit_diy_w_h".equals(key) ? "list_resolution" : key;
        editor = key;
        draft = null;
        render();
        scroll.scrollTo(0, 0);
    }

    private void renderEditor() {
        if (HUD.equals(editor)) {
            title.setText("性能信息");
            String[] labels = {"关闭", "精简", "完整"};
            for (int i = 0; i < labels.length; i++) {
                final int mode = i;
                choice(labels[i], labels[i].equals(hudLabel()), () -> {
                    CheckBoxPreference full = (CheckBoxPreference) preference("checkbox_enable_perf_overlay");
                    CheckBoxPreference lite = (CheckBoxPreference) preference("checkbox_enable_perf_overlay_lite");
                    full.setChecked(mode != 0);
                    lite.setChecked(mode == 1);
                    goBack();
                });
            }
            return;
        }
        Preference pref = preference(editor);
        if (pref == null) {
            editor = null;
            render();
            return;
        }
        title.setText(CUSTOM_RESOLUTION.equals(editor) ? getString(R.string.settings_panel_custom) : pref.getTitle());
        if (pref instanceof ListPreference) {
            ListPreference list = (ListPreference) pref;
            paragraph(pref.getSummary());
            CharSequence[] entries = list.getEntries();
            CharSequence[] values = list.getEntryValues();
            if (entries != null && values != null) {
                for (int i = 0; i < Math.min(entries.length, values.length); i++) {
                    String value = values[i].toString();
                    choice(entries[i], value.equals(list.getValue()), () -> {
                        if (acceptChange(list, value)) {
                            list.setValue(value);
                            goBack();
                        }
                    });
                }
            }
            if ("list_resolution".equals(editor)) {
                row(getString(R.string.settings_panel_custom), getString(R.string.settings_panel_resolution_hint), null,
                        CUSTOM_RESOLUTION, () -> openEditor(CUSTOM_RESOLUTION));
            }
        } else if (pref instanceof SeekBarPreference) {
            if (PreferenceConfiguration.BITRATE_PREF_STRING.equals(pref.getKey())) {
                bitrateEditor((SeekBarPreference) pref);
            } else {
                numericEditor((SeekBarPreference) pref);
            }
        } else if (pref instanceof EditTextPreference) {
            textEditor((EditTextPreference) pref);
        }
    }

    private String hudLabel() {
        int mode = SettingsCatalog.overlayMode(checked("checkbox_enable_perf_overlay"),
                checked("checkbox_enable_perf_overlay_lite"));
        return new String[] {"关闭", "精简", "完整"}[mode];
    }

    private void numericEditor(SeekBarPreference pref) {
        paragraph(pref.getSummary());
        String unit = pref.getSuffix() == null ? "" : pref.getSuffix();
        paragraph(number(pref.getMinValue(), pref.getDivisor()) + " – "
                + number(pref.getMaxValue(), pref.getDivisor()) + " " + unit);
        input = edit(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                draft == null ? number(pref.getSavedValue(), pref.getDivisor()) : draft);
        SeekBar slider = new SeekBar(getActivity());
        int step = Math.max(1, pref.getStepSize());
        slider.setMax((pref.getMaxValue() - pref.getMinValue()) / step);
        slider.setProgress((pref.getSavedValue() - pref.getMinValue()) / step);
        // Match the in-stream audio/display sliders, including their gradient and no thumb.
        slider.setProgressTintList(null);
        slider.setProgressBackgroundTintList(null);
        slider.setProgressDrawable(getActivity().getDrawable(R.drawable.ic_game_menu_seek_bg));
        slider.setThumbTintList(null);
        slider.setThumb(new ColorDrawable(Color.TRANSPARENT));
        slider.setThumbOffset(0);
        slider.setSplitTrack(false);
        slider.setBackgroundResource(R.drawable.settings_slider_background);
        slider.setPadding(dp(4), dp(12), dp(4), dp(12));
        slider.setContentDescription(pref.getTitle());
        slider.setTag("settings_slider");
        try {
            int value = SettingsValueParser.parseNumber(input.getText().toString(), pref.getDivisor(),
                    pref.getMinValue(), pref.getMaxValue());
            slider.setProgress((value - pref.getMinValue()) / step);
        } catch (IllegalArgumentException ignored) {
            // A partially typed draft can be restored after a configuration change.
        }
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int value = SettingsValueParser.parseNumber(s.toString(), pref.getDivisor(),
                            pref.getMinValue(), pref.getMaxValue());
                    slider.setProgress((value - pref.getMinValue()) / step);
                } catch (IllegalArgumentException ignored) {
                    // Allow incomplete input; validate only when saving.
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) input.setText(number(pref.getMinValue() + progress * step, pref.getDivisor()));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { hideKeyboard(); }
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
        action(getString(R.string.settings_panel_apply), () -> {
            try {
                int value = SettingsValueParser.parseNumber(input.getText().toString(), pref.getDivisor(),
                        pref.getMinValue(), pref.getMaxValue());
                if (pref.saveValue(value)) goBack();
            } catch (IllegalArgumentException e) {
                showInputError(getString(R.string.settings_panel_range_error,
                        number(pref.getMinValue(), pref.getDivisor()), number(pref.getMaxValue(), pref.getDivisor())));
            }
        });
        action(getString(R.string.settings_panel_reset), () -> {
            int value = "seekbar_bitrate_kbps".equals(pref.getKey())
                    ? PreferenceConfiguration.getDefaultBitrate(getActivity()) : pref.getDefaultValue();
            if (pref.saveValue(value)) goBack();
        });
    }

    private void textEditor(EditTextPreference pref) {
        boolean resolution = CUSTOM_RESOLUTION.equals(editor);
        paragraph(resolution ? getString(R.string.settings_panel_resolution_hint) : pref.getDialogMessage());
        if (resolution) {
            resolutionInputs(draft == null ? pref.getText() : draft);
        } else {
            input = edit(pref.getEditText().getInputType(), draft == null ? pref.getText() : draft);
        }
        action(getString(R.string.settings_panel_apply), () -> {
            String value = input.getText().toString().trim();
            if (resolution) {
                try {
                    value = SettingsValueParser.parseResolution(value + "x" + heightInput.getText().toString().trim());
                } catch (IllegalArgumentException e) {
                    showInputError(getString(R.string.settings_panel_resolution_error));
                    return;
                }
                if (!acceptChange(pref, value)) return;
                pref.setText(value);
                // Custom dimensions are selected immediately, using the same bitrate policy.
                ListPreference res = (ListPreference) preference("list_resolution");
                res.setValue(value);
                PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                        .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                                PreferenceConfiguration.getDefaultBitrate(value,
                                        ((ListPreference) preference("list_fps")).getValue())).apply();
                goBack();
                ((StreamSettings) getActivity()).reloadSettings();
            } else if (acceptChange(pref, value)) {
                pref.setText(value);
                goBack();
            }
        });
    }

    private EditText edit(int inputType, String value) {
        EditText field = createInput(inputType, value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(8), dp(4), dp(8));
        content.addView(field, lp);
        addInputError();
        return field;
    }

    private EditText createInput(int inputType, String value) {
        EditText field = new EditText(getActivity());
        field.setTextColor(Color.WHITE);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setText(value);
        field.setSelectAllOnFocus(true);
        field.setContentDescription(title.getText());
        field.setBackgroundResource(R.drawable.settings_input_background);
        field.setBackgroundTintList(null);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setMinHeight(dp(44));
        field.setHintTextColor(0xFF989AA8);
        field.setHighlightColor(0x668C99E6);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        if (Build.VERSION.SDK_INT >= 29) field.setTextCursorDrawable(R.drawable.settings_input_cursor);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (inputError != null) inputError.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        return field;
    }

    private void addInputError() {
        inputError = text("", 12, 0xFFFFA09A);
        inputError.setPadding(dp(4), 0, dp(4), dp(8));
        inputError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        inputError.setVisibility(View.GONE);
        content.addView(inputError);
    }

    private void showInputError(String message) {
        inputError.setText(message);
        inputError.setVisibility(View.VISIBLE);
    }

    private void resolutionInputs(String value) {
        String[] dimensions = value == null ? new String[0] : value.split("x", -1);
        input = createInput(InputType.TYPE_CLASS_NUMBER, dimensions.length == 2 ? dimensions[0] : "");
        heightInput = createInput(InputType.TYPE_CLASS_NUMBER, dimensions.length == 2 ? dimensions[1] : "");
        input.setContentDescription(getString(R.string.settings_panel_width));
        heightInput.setContentDescription(getString(R.string.settings_panel_height));
        input.setHint(getString(R.string.settings_panel_pixels));
        heightInput.setHint(getString(R.string.settings_panel_pixels));
        input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(5)});
        heightInput.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(5)});
        LinearLayout fields = new LinearLayout(getActivity());
        fields.setGravity(Gravity.BOTTOM);
        LinearLayout widthColumn = vertical();
        TextView widthLabel = text(getString(R.string.settings_panel_width), 12, 0xFFB4B4B4);
        widthLabel.setPadding(0, 0, 0, dp(6));
        widthColumn.addView(widthLabel);
        widthColumn.addView(input, new LinearLayout.LayoutParams(-1, -2));
        fields.addView(widthColumn, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton swap = new ImageButton(getActivity());
        swap.setImageResource(R.drawable.ic_settings_swap_dimensions);
        swap.setBackgroundResource(R.drawable.ic_game_menu_btn_side_selector);
        swap.setContentDescription(getString(R.string.settings_panel_swap));
        swap.setPadding(dp(10), dp(10), dp(10), dp(10));
        swap.setOnClickListener(v -> {
            String width = input.getText().toString();
            input.setText(heightInput.getText().toString());
            heightInput.setText(width);
        });
        LinearLayout.LayoutParams swapParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        swapParams.setMargins(dp(6), 0, dp(6), 0);
        fields.addView(swap, swapParams);
        LinearLayout heightColumn = vertical();
        TextView heightLabel = text(getString(R.string.settings_panel_height), 12, 0xFFB4B4B4);
        heightLabel.setPadding(0, 0, 0, dp(6));
        heightColumn.addView(heightLabel);
        heightColumn.addView(heightInput, new LinearLayout.LayoutParams(-1, -2));
        fields.addView(heightColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(8), dp(4), dp(12));
        content.addView(fields, lp);
        addInputError();
    }

    private void bitrateEditor(SeekBarPreference pref) {
        paragraph(getString(R.string.settings_panel_bitrate_hint));
        input = edit(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                draft == null ? number(pref.getSavedValue(), pref.getDivisor()) : draft);
        input.setHint("Mbps");
        input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(9)});
        heading(getString(R.string.settings_panel_bitrate_presets));
        FlowLayout presets = new FlowLayout(getActivity());
        for (int value : new int[] {20, 50, 60, 100, 150, 200, 300}) {
            TextView button = text(value + " Mbps", 12, Color.WHITE);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(40));
            button.setPadding(dp(12), dp(8), dp(12), dp(8));
            clickable(button, () -> {
                if (pref.saveValue(value * 1000)) goBack();
            });
            FlowLayout.LayoutParams lp = new FlowLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            presets.addView(button, lp);
        }
        content.addView(presets, new LinearLayout.LayoutParams(-1, -2));
        action(getString(R.string.settings_panel_apply), () -> {
            try {
                int value = SettingsValueParser.parseBitrateMbps(input.getText().toString());
                if (pref.saveValue(value)) goBack();
            } catch (IllegalArgumentException e) {
                showInputError(getString(R.string.settings_panel_range_error, "0.5", "99999"));
            }
        });
        action(getString(R.string.settings_panel_reset), () -> {
            if (pref.saveValue(PreferenceConfiguration.getDefaultBitrate(getActivity()))) goBack();
        });
    }

    private static boolean acceptChange(Preference preference, Object value) {
        Preference.OnPreferenceChangeListener listener = preference.getOnPreferenceChangeListener();
        return listener == null || listener.onPreferenceChange(preference, value);
    }

    private void choice(CharSequence label, boolean selected, Runnable action) {
        LinearLayout item = row(label, null, null, null, action);
        TextView mark = (TextView) item.getChildAt(1);
        mark.setText(selected ? "●" : "○");
        mark.setTextColor(selected ? getResources().getColor(R.color.home_accent_bright) : 0xFFAAAAAA);
        item.setBackgroundResource(R.drawable.settings_control_row_background);
        item.setSelected(selected);
    }

    private void action(String label, Runnable action) {
        TextView button = text(label, 13, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(42));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        clickable(button, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(8), dp(4), 0);
        content.addView(button, lp);
    }

    private LinearLayout row(CharSequence label, CharSequence detail, Boolean checked, String key, Runnable action) {
        LinearLayout row = new LinearLayout(getActivity());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(46));
        row.setTag(key);
        LinearLayout labels = vertical();
        labels.addView(text(label, 14, Color.WHITE));
        if (!TextUtils.isEmpty(detail)) {
            TextView subtitle = text(detail, 11, 0xFFB4B4B4);
            subtitle.setPadding(0, dp(3), dp(8), 0);
            labels.addView(subtitle);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (checked != null) {
            Switch toggle = new Switch(getActivity());
            toggle.setChecked(checked);
            toggle.setClickable(false);
            toggle.setFocusable(false);
            toggle.setBackground(null);
            toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            toggle.setThumbTintList(getResources().getColorStateList(R.color.settings_toggle_thumb));
            toggle.setTrackTintList(getResources().getColorStateList(R.color.settings_toggle_track));
            row.addView(toggle);
            row.setContentDescription(label + (checked ? "，已开启" : "，已关闭")
                    + (TextUtils.isEmpty(detail) ? "" : "，" + detail));
        } else {
            TextView arrow = text("›", 22, 0xFFAAAAAA);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(26), -2));
        }
        clickable(row, action);
        if (checked != null) row.setBackgroundResource(R.drawable.settings_control_row_background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        content.addView(row, lp);
        return row;
    }

    private void clickable(View view, Runnable action) {
        view.setBackgroundResource(R.drawable.ic_game_menu_btn_selector);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
    }

    private void heading(String value) {
        TextView heading = text(value, 12, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(4), dp(12), 0, dp(4));
        content.addView(heading);
    }

    private void paragraph(CharSequence value) {
        if (TextUtils.isEmpty(value)) return;
        TextView view = text(value, 12, 0xFFB4B4B4);
        view.setPadding(dp(4), dp(10), dp(4), dp(8));
        content.addView(view);
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(CharSequence value, int size, int color) {
        TextView view = new TextView(getActivity());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String number(int value, int divisor) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor)).stripTrailingZeros().toPlainString();
    }
}
