package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * 游戏菜单-音频震动
 */
public class GameAudioHapticsFragment extends BaseGameMenuDialog {
    private ImageButton ibtnBack;
    private TextView txTitle;
    private RadioGroup rgEnable;
    private LinearLayout layoutDetails;
    private RadioGroup rgOutputTarget;
    private TextView txKeepControllerRumble;
    private RadioGroup rgKeepControllerRumble;
    private TextView txStrength;
    private SeekBar sbStrength;
    private RadioGroup rgVoiceFilter;

    private String title;
    private PreferenceConfiguration prefConfig;
    private OnSettingsChangedListener onSettingsChangedListener;

    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_audio_haptics;
    }

    @Override
    public void bindView(View v) {
        super.bindView(v);

        ibtnBack = v.findViewById(R.id.ibtn_back);
        txTitle = v.findViewById(R.id.tx_title);
        rgEnable = v.findViewById(R.id.rg_game_audio_haptics_enable);
        layoutDetails = v.findViewById(R.id.layout_game_audio_haptics_details);
        rgOutputTarget = v.findViewById(R.id.rg_game_audio_haptics_output_target);
        txKeepControllerRumble = v.findViewById(R.id.tx_game_audio_haptics_keep_controller_rumble);
        rgKeepControllerRumble = v.findViewById(R.id.rg_game_audio_haptics_keep_controller_rumble);
        txStrength = v.findViewById(R.id.tx_game_audio_haptics_strength);
        sbStrength = v.findViewById(R.id.sb_game_audio_haptics_strength);
        rgVoiceFilter = v.findViewById(R.id.rg_game_audio_haptics_voice_filter);

        if (!TextUtils.isEmpty(title)) {
            txTitle.setText(title);
        }

        if (prefConfig == null) {
            prefConfig = new PreferenceConfiguration();
        }

        initViewData();
        bindControls();
        ibtnBack.setOnClickListener(view -> dismiss());
    }

    private void initViewData() {
        rgEnable.check(prefConfig.enableAudioHaptics
                ? R.id.rbt_game_audio_haptics_enable_on
                : R.id.rbt_game_audio_haptics_enable_off);

        rgOutputTarget.check("controller".equals(prefConfig.audioHapticsOutputTarget)
                ? R.id.rbt_game_audio_haptics_output_target_controller
                : R.id.rbt_game_audio_haptics_output_target_phone);

        rgKeepControllerRumble.check(prefConfig.audioHapticsKeepControllerRumble
                ? R.id.rbt_game_audio_haptics_keep_controller_rumble_on
                : R.id.rbt_game_audio_haptics_keep_controller_rumble_off);

        if ("low".equals(prefConfig.audioHapticsVoiceFilter)) {
            rgVoiceFilter.check(R.id.rbt_game_audio_haptics_voice_filter_2);
        }
        else if ("medium".equals(prefConfig.audioHapticsVoiceFilter)) {
            rgVoiceFilter.check(R.id.rbt_game_audio_haptics_voice_filter_3);
        }
        else if ("high".equals(prefConfig.audioHapticsVoiceFilter)) {
            rgVoiceFilter.check(R.id.rbt_game_audio_haptics_voice_filter_4);
        }
        else {
            rgVoiceFilter.check(R.id.rbt_game_audio_haptics_voice_filter_1);
        }

        sbStrength.setProgress(prefConfig.audioHapticsStrength);
        updateStrengthLabel();
        updateDetailsVisibility();
    }

    private void bindControls() {
        rgEnable.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_audio_haptics_enable_on) {
                prefConfig.enableAudioHaptics = true;
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_enable_off) {
                prefConfig.enableAudioHaptics = false;
            }
            else {
                return;
            }

            saveBoolean("checkbox_enable_audio_haptics", prefConfig.enableAudioHaptics);
            updateDetailsVisibility();
            notifySettingsChanged();
        });

        rgOutputTarget.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_audio_haptics_output_target_phone) {
                prefConfig.audioHapticsOutputTarget = "phone";
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_output_target_controller) {
                prefConfig.audioHapticsOutputTarget = "controller";
            }
            else {
                return;
            }

            saveString("list_audio_haptics_output_target", prefConfig.audioHapticsOutputTarget);
            updateDetailsVisibility();
            notifySettingsChanged();
        });

        rgKeepControllerRumble.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_audio_haptics_keep_controller_rumble_on) {
                prefConfig.audioHapticsKeepControllerRumble = true;
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_keep_controller_rumble_off) {
                prefConfig.audioHapticsKeepControllerRumble = false;
            }
            else {
                return;
            }

            saveBoolean("checkbox_audio_haptics_keep_controller_rumble",
                    prefConfig.audioHapticsKeepControllerRumble);
            notifySettingsChanged();
        });

        rgVoiceFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_audio_haptics_voice_filter_1) {
                prefConfig.audioHapticsVoiceFilter = "off";
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_voice_filter_2) {
                prefConfig.audioHapticsVoiceFilter = "low";
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_voice_filter_3) {
                prefConfig.audioHapticsVoiceFilter = "medium";
            }
            else if (checkedId == R.id.rbt_game_audio_haptics_voice_filter_4) {
                prefConfig.audioHapticsVoiceFilter = "high";
            }
            else {
                return;
            }

            saveString("list_audio_haptics_voice_filter", prefConfig.audioHapticsVoiceFilter);
            notifySettingsChanged();
        });

        sbStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefConfig.audioHapticsStrength = progress;
                saveInt("seekbar_audio_haptics_strength", progress);
                updateStrengthLabel();
                notifySettingsChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updateStrengthLabel() {
        txStrength.setText("音频震动强度：" + prefConfig.audioHapticsStrength + "%");
    }

    private void updateDetailsVisibility() {
        layoutDetails.setVisibility(prefConfig.enableAudioHaptics ? View.VISIBLE : View.GONE);
        boolean showKeepControllerRumble = prefConfig.enableAudioHaptics &&
                "controller".equals(prefConfig.audioHapticsOutputTarget);
        txKeepControllerRumble.setVisibility(showKeepControllerRumble ? View.VISIBLE : View.GONE);
        rgKeepControllerRumble.setVisibility(showKeepControllerRumble ? View.VISIBLE : View.GONE);
    }

    private void saveBoolean(String name, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(name, value)
                .apply();
    }

    private void saveInt(String name, int value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(name, value)
                .apply();
    }

    private void saveString(String name, String value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(name, value)
                .apply();
    }

    private void notifySettingsChanged() {
        if (onSettingsChangedListener != null) {
            onSettingsChangedListener.onSettingsChanged();
        }
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    public void setOnSettingsChangedListener(OnSettingsChangedListener listener) {
        this.onSettingsChangedListener = listener;
    }

    public interface OnSettingsChangedListener {
        void onSettingsChanged();
    }
}
