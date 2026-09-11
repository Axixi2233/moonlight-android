package com.limelight.preferences;

/** Navigation for the persistent settings sheet; independent of the in-stream quick menu. */
final class SettingsCatalog {
    static final class Section {
        final String title;
        final String[] keys;

        Section(String title, String... keys) {
            this.title = title;
            this.keys = keys;
        }
    }

    static final class Group {
        final String title;
        final String description;
        final Section[] sections;

        Group(String title, String description, Section... sections) {
            this.title = title;
            this.description = description;
            this.sections = sections;
        }
    }

    static final Group[] GROUPS = {
        new Group("显示", "分辨率、帧率与画面增强",
            new Section("视频参数", "list_resolution", "list_fps", "seekbar_bitrate_kbps", "video_format", "vdValue"),
            new Section("画面布局", "checkbox_stretch_video", "checkbox_cutout_mode_video",
                "checkbox_auto_screen_orientation", "checkbox_enable_portrait", "screen_gravity_list",
                "checkbox_keep_video_zoom_on_disable"),
            new Section("画面增强", "list_video_render_mode", "list_fsr_target", "list_fsr_sharpness",
                "list_fsr_hdr_output", "list_stereo_3d_mode", "list_stereo_3d_depth",
                "list_stereo_3d_convergence", "checkbox_stereo_3d_swap_eyes", "checkbox_enable_hdr",
                "checkbox_enable_hdr_high_brightness", "checkbox_full_range"),
            new Section("兼容与性能", "frame_pacing", "enable_lowLatency_experiment",
                "checkbox_enable_xiaomi_xring_o1_optimization", "checkbox_unlock_fps",
                "checkbox_reduce_refresh_rate", "checkbox_enforce_display_mode", "checkbox_enable_sops")),
        new Group("手柄", "连接、映射、体感与震动",
            new Section("连接与映射", "list_gamepad_emulation", "checkbox_multi_controller", "seekbar_deadzone",
                "checkbox_disable_trigger_deadzone", "checkbox_flip_face_buttons", "checkbox_usb_driver",
                "checkbox_usb_bind_all", "checkbox_enable_joyconfix", "checkbox_gamepad_enable_battery_report"),
            new Section("鼠标与体感", "checkbox_mouse_emulation", "analog_scrolling",
                "checkbox_gamepad_touchpad_as_mouse", "checkbox_gamepad_motion_sensors",
                "checkbox_gamepad_motion_fallback", "checkbox_enable_virtual_motion"),
            new Section("震动与 DualSense", "checkbox_flip_rumble_ff", "checkbox_enable_device_rumble",
                "checkbox_vibrate_fallback", "seekbar_vibrate_fallback_strength",
                "checkbox_ds5_native_pcm", "checkbox_ds5_controller_speaker")),
        new Group("触摸与鼠标", "触屏、鼠标与实体键盘",
            new Section("鼠标与触屏", "mouse_model_list_axi", "checkbox_touch_stutter_compatibility", "checkbox_mouse_local_cursor",
                "checkbox_mouse_nav_buttons", "checkbox_absolute_mouse_mode"),
            new Section("实体键盘与辅助输入", "checkbox_keyboard_esc_opens_game_menu",
                "checkbox_enable_clear_default_special_button", "import_switch_button_file")),
        new Group("声音", "声道与播放位置",
            new Section("音频输出", "list_audio_config", "checkbox_enable_audiofx", "checkbox_host_audio")),
        new Group("音频震动", "输出目标、强度与语音过滤",
            new Section("音频震动", "checkbox_enable_audio_haptics", "list_audio_haptics_output_target",
                "seekbar_audio_haptics_strength", "list_audio_haptics_voice_filter",
                "checkbox_audio_haptics_keep_controller_rumble")),
        new Group("虚拟按键", "屏幕按键与完整键盘",
            new Section("屏幕按键", "checkbox_enable_keyboard", "keyboard_axi_list", "checkbox_vibrate_keyboard"),
            new Section("完整键盘", "seekbar_keyboard_axi_opacity", "seekbar_keyboard_axi_height",
                "checkbox_enable_keyboard_axi_combination"),
            new Section("按键配置", "import_keyboard_file", "export_keyboard_file")),
        new Group("虚拟手柄", "布局、透明度与触感",
            new Section("屏幕手柄", "checkbox_show_onscreen_controls", "gamepad_axi_list", "seekbar_osc_opacity",
                "checkbox_vibrate_osc", "checkbox_rocker_click_L3R3"),
            new Section("手柄配置", "import_gamepad_file", "export_gamepad_file")),
        new Group("悬浮信息", "性能数据与悬浮球",
            new Section("性能信息", "performance_overlay_mode", "list_perf_overlay_lite_position",
                "checkbox_enable_perf_overlay_lite_dialog",
                "checkbox_enable_perf_overlay_lite_ext", "performance_overlayLite_magin_top",
                "checkbox_enable_post_stream_toast"),
            new Section("悬浮球", "checkbox_enable_ax_floating")),
        new Group("备份与恢复", "导出或导入主机配对数据",
            new Section("主机与配对数据", "export_pairing_backup", "import_pairing_backup")),
        new Group("日志采集", "记录串流并管理日志文件",
            new Section("串流日志", "checkbox_enable_stream_session_logging", "manage_stream_session_logs"),
            new Section("输入调试", "checkbox_enable_accessibility_show_log")),
        new Group("界面与通用", "语言、首页与应用行为",
            new Section("界面", "list_languages", "change_screen_label_key",
                "checkbox_enable_screen_bg", "import_image_file_key", "checkbox_enable_screen_obscure"),
            new Section("应用行为", "checkbox_enable_pip", "checkbox_enable_exdisplay",
                "checkbox_enable_game_manager_quest", "checkbox_disable_warnings"))
    };

    // These legacy rows are represented by one editor, or no longer apply to the new surface.
    static final String[] MERGED_OR_RETIRED = {
        "edit_diy_w_h", "edit_diy_bitrate", "checkbox_enable_perf_overlay",
        "checkbox_enable_perf_overlay_lite", "checkbox_ui_theme_white",
        "checkbox_small_icon_mode", "checkbox_enable_pass_menu", "settings_about", "settings_help"
    };

    static int overlayMode(boolean enabled, boolean lite) {
        return !enabled ? 0 : lite ? 1 : 2;
    }

    private SettingsCatalog() {}
}
