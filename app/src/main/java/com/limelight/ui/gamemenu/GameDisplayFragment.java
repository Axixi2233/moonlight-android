package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.utils.UiHelper;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDisplayFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_display;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private Button bt_display_screen;

    private Button bt_display_exchange;

    private Button bt_display_direction;

    private Button bt_display_bitrate;

    private Button bt_display_fps;

    private TextView tx_game_display_screen;

    private TextView tx_game_display_bit;

    private TextView tx_game_display_fps;

    private TextView tx_game_display_direction;

    private TextView tx_game_display_ex;

    private RadioGroup rg_game_display_lock;

    private RadioGroup rg_game_display_video_format;

    private RadioGroup rg_game_display_audio;

    private RadioGroup rg_game_display_hdr;

    private RadioGroup rg_game_display_vd;

    private RadioGroup rg_game_display_enforce;

    private RadioGroup rg_game_display_lowlatency;

    private RadioGroup rg_game_display_ignore_hdr;

    private View v_game_display_hdr_high_brightness;

    private RadioGroup rg_game_display_hdr_high_brightness;

    private RadioGroup rg_game_display_render_mode;

    private View v_game_display_stereo_3d_header;

    private RadioGroup rg_game_display_stereo_3d;

    private View v_game_display_stereo_3d_details;

    private RadioGroup rg_game_display_stereo_3d_depth;

    private RadioGroup rg_game_display_stereo_3d_convergence;

    private RadioGroup rg_game_display_stereo_3d_swap_eyes;

    private View v_game_display_fsr_header;

    private RadioGroup rg_game_display_fsr;

    private View v_game_display_fsr_details;

    private RadioGroup rg_game_display_fsr_sharpness;

    private RadioGroup rg_game_display_fsr_hdr_output;

    private int width;

    private int height;

    private int bitrate;

    private int fps;

    private boolean direction;

    private boolean exDiaplay;

    private String renderModePending = PreferenceConfiguration.VIDEO_RENDER_MODE_SYSTEM;

    private String stereo3dModePending = PreferenceConfiguration.STEREO_3D_MODE_OFF;

    private String stereo3dDepthPending = "standard";

    private String stereo3dConvergencePending = "standard";

    private boolean stereo3dSwapEyesPending;

    private String fsrTargetPending = "off";

    private String fsrSharpnessPending = "standard";

    private String fsrHdrOutputPending = "native";

    private boolean showLock=true;
    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        bt_display_screen=v.findViewById(R.id.bt_display_screen);
        bt_display_exchange=v.findViewById(R.id.bt_display_exchange);
        bt_display_direction=v.findViewById(R.id.bt_display_direction);

        bt_display_bitrate=v.findViewById(R.id.bt_display_bitrate);
        bt_display_fps=v.findViewById(R.id.bt_display_fps);
        tx_game_display_screen=v.findViewById(R.id.tx_game_display_screen);
        tx_game_display_bit=v.findViewById(R.id.tx_game_display_bit);
        tx_game_display_fps=v.findViewById(R.id.tx_game_display_fps);
        tx_game_display_direction=v.findViewById(R.id.tx_game_display_direction);
        tx_game_display_ex=v.findViewById(R.id.tx_game_display_ex);

        rg_game_display_lock=v.findViewById(R.id.rg_game_display_lock);
        rg_game_display_video_format=v.findViewById(R.id.rg_game_display_video_format);
        rg_game_display_hdr=v.findViewById(R.id.rg_game_display_hdr);
        rg_game_display_audio=v.findViewById(R.id.rg_game_display_audio);
        rg_game_display_vd=v.findViewById(R.id.rg_game_display_vd);
        rg_game_display_enforce=v.findViewById(R.id.rg_game_display_enforce);
        rg_game_display_lowlatency=v.findViewById(R.id.rg_game_display_lowlatency);

        rg_game_display_ignore_hdr=v.findViewById(R.id.rg_game_display_ignore_hdr);
        v_game_display_hdr_high_brightness=v.findViewById(R.id.v_game_display_hdr_high_brightness);
        rg_game_display_hdr_high_brightness=v.findViewById(R.id.rg_game_display_hdr_high_brightness);
        rg_game_display_render_mode=v.findViewById(R.id.rg_game_display_render_mode);
        v_game_display_stereo_3d_header=v.findViewById(R.id.v_game_display_stereo_3d_header);
        rg_game_display_stereo_3d=v.findViewById(R.id.rg_game_display_stereo_3d);
        v_game_display_stereo_3d_details=v.findViewById(R.id.v_game_display_stereo_3d_details);
        rg_game_display_stereo_3d_depth=v.findViewById(R.id.rg_game_display_stereo_3d_depth);
        rg_game_display_stereo_3d_convergence=v.findViewById(R.id.rg_game_display_stereo_3d_convergence);
        rg_game_display_stereo_3d_swap_eyes=v.findViewById(R.id.rg_game_display_stereo_3d_swap_eyes);
        v_game_display_fsr_header=v.findViewById(R.id.v_game_display_fsr_header);
        rg_game_display_fsr=v.findViewById(R.id.rg_game_display_fsr);
        v_game_display_fsr_details=v.findViewById(R.id.v_game_display_fsr_details);
        rg_game_display_fsr_sharpness=v.findViewById(R.id.rg_game_display_fsr_sharpness);
        rg_game_display_fsr_hdr_output=v.findViewById(R.id.rg_game_display_fsr_hdr_output);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }

        v.findViewById(R.id.lv_display_lock).setVisibility(showLock?View.VISIBLE:View.GONE);

        if(prefConfig!=null){
            width=prefConfig.width;
            height=prefConfig.height;
            bitrate=prefConfig.bitrate;
            fps=prefConfig.fps;
            direction=prefConfig.enablePortrait;
            exDiaplay=prefConfig.enableExDisplay;
            renderModePending = prefConfig.videoRenderMode == PreferenceConfiguration.VideoRenderMode.GLES
                    ? PreferenceConfiguration.VIDEO_RENDER_MODE_GLES
                    : PreferenceConfiguration.VIDEO_RENDER_MODE_SYSTEM;
        }
        fsrTargetPending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString("list_fsr_target", "off");
        stereo3dModePending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(PreferenceConfiguration.STEREO_3D_MODE_PREF_STRING,
                        PreferenceConfiguration.STEREO_3D_MODE_OFF);
        stereo3dDepthPending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(PreferenceConfiguration.STEREO_3D_DEPTH_PREF_STRING, "standard");
        stereo3dConvergencePending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(PreferenceConfiguration.STEREO_3D_CONVERGENCE_PREF_STRING, "standard");
        stereo3dSwapEyesPending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(PreferenceConfiguration.STEREO_3D_SWAP_EYES_PREF_STRING, false);
        fsrSharpnessPending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString("list_fsr_sharpness", "standard");
        fsrHdrOutputPending = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString("list_fsr_hdr_output", "native");
        initViewData();
        initLock();
        initAudio();
        initHDR();
        initIgnoreHDR();
        initHdrHighBrightness();
        initLowLatency();
        initVD();
        initVideoFormat();
        initEnfoce();
        initRenderMode();
        initStereo3d();
        initStereo3dDepth();
        initStereo3dConvergence();
        initStereo3dSwapEyes();
        initFsr();
        initFsrSharpness();
        initFsrHdrOutput();
        ibtn_back.setOnClickListener(this);
        bt_display_screen.setOnClickListener(this);
        bt_display_exchange.setOnClickListener(this);
        bt_display_direction.setOnClickListener(this);
        bt_display_fps.setOnClickListener(this);
        bt_display_bitrate.setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);
        v.findViewById(R.id.bt_display_ex).setOnClickListener(this);

        rg_game_display_lock.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Toast.makeText(getActivity(),"切换成功！",Toast.LENGTH_SHORT).show();
                if(checkedId==R.id.rbt_game_display_lock_1){
                    prefConfig.enableScreenOnAuto=0;
                    saveLock(0);
                    dismiss();
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lock_2){
                    prefConfig.enableScreenOnAuto=1;
                    saveLock(1);
                    dismiss();
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lock_3){
                    prefConfig.enableScreenOnAuto=2;
                    saveLock(2);
                    dismiss();
                    return;
                }
            }
        });

        rg_game_display_video_format.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_video_format_1){
                    saveVideoFormat("auto");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_2){
                    saveVideoFormat("neverh265");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_3){
                    saveVideoFormat("forceh265");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_4){
                    saveVideoFormat("forceav1");
                    return;
                }
            }
        });

        rg_game_display_audio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_audio_1){
                    saveAudio(false);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_audio_2){
                    saveAudio(true);
                    return;
                }
            }
        });

        rg_game_display_hdr.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_hdr_1){
                    saveHDR(true);
                    updateHdrHighBrightnessVisibility(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_hdr_2){
                    saveHDR(false);
                    updateHdrHighBrightnessVisibility(false);
                    return;
                }
            }
        });

        rg_game_display_vd.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_vd_1){
                    saveVD(0);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_vd_2){
                    saveVD(1);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_vd_3){
                    saveVD(2);
                    return;
                }
            }
        });

        rg_game_display_enforce.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_enforce_1){
                    saveEnForce(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_enforce_2){
                    saveEnForce(false);
                    return;
                }
            }
        });

        rg_game_display_lowlatency.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_lowlatency_1){
                    savelowLatency(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lowlatency_2){
                    savelowLatency(false);
                    return;
                }
            }
        });

        rg_game_display_ignore_hdr.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_ignore_hdr_1){
                    saveIgnoreHDR(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_ignore_hdr_2){
                    saveIgnoreHDR(false);
                    return;
                }
            }
        });

        rg_game_display_render_mode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_display_render_mode_gles) {
                renderModePending = PreferenceConfiguration.VIDEO_RENDER_MODE_GLES;
            }
            else {
                renderModePending = PreferenceConfiguration.VIDEO_RENDER_MODE_SYSTEM;
                stereo3dModePending = PreferenceConfiguration.STEREO_3D_MODE_OFF;
                rg_game_display_stereo_3d.check(R.id.rbt_game_display_stereo_3d_off);
                fsrTargetPending = "off";
                rg_game_display_fsr.check(R.id.rbt_game_display_fsr_1);
            }
            updateStereo3dDetailState();
            updateFsrDetailState();
        });

        rg_game_display_stereo_3d.setOnCheckedChangeListener((group, checkedId) -> {
            stereo3dModePending = checkedId == R.id.rbt_game_display_stereo_3d_sbs
                    ? PreferenceConfiguration.STEREO_3D_MODE_SBS
                    : PreferenceConfiguration.STEREO_3D_MODE_OFF;
            updateStereo3dDetailState();
        });

        rg_game_display_stereo_3d_depth.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_display_stereo_3d_depth_soft) {
                stereo3dDepthPending = "soft";
            }
            else if (checkedId == R.id.rbt_game_display_stereo_3d_depth_strong) {
                stereo3dDepthPending = "strong";
            }
            else {
                stereo3dDepthPending = "standard";
            }
        });

        rg_game_display_stereo_3d_convergence.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbt_game_display_stereo_3d_convergence_near) {
                stereo3dConvergencePending = "near";
            }
            else if (checkedId == R.id.rbt_game_display_stereo_3d_convergence_far) {
                stereo3dConvergencePending = "far";
            }
            else {
                stereo3dConvergencePending = "standard";
            }
        });

        rg_game_display_stereo_3d_swap_eyes.setOnCheckedChangeListener((group, checkedId) ->
                stereo3dSwapEyesPending = checkedId == R.id.rbt_game_display_stereo_3d_swap_eyes_on);

        rg_game_display_fsr.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbt_game_display_fsr_1) {
                    fsrTargetPending = "off";
                }
                else if (checkedId == R.id.rbt_game_display_fsr_2) {
                    fsrTargetPending = "2k";
                }
                else if (checkedId == R.id.rbt_game_display_fsr_3) {
                    fsrTargetPending = "4k";
                }
                updateFsrDetailState();
            }
        });

        rg_game_display_fsr_sharpness.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbt_game_display_fsr_sharpness_1) {
                    fsrSharpnessPending = "soft";
                }
                else if (checkedId == R.id.rbt_game_display_fsr_sharpness_2) {
                    fsrSharpnessPending = "standard";
                }
                else if (checkedId == R.id.rbt_game_display_fsr_sharpness_3) {
                    fsrSharpnessPending = "strong";
                }
                else if (checkedId == R.id.rbt_game_display_fsr_sharpness_4) {
                    fsrSharpnessPending = "max";
                }
            }
        });

        rg_game_display_hdr_high_brightness.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbt_game_display_hdr_high_brightness_1) {
                    saveHdrHighBrightness(true);
                    return;
                }
                if (checkedId == R.id.rbt_game_display_hdr_high_brightness_2) {
                    saveHdrHighBrightness(false);
                    return;
                }
            }
        });

        rg_game_display_fsr_hdr_output.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbt_game_display_fsr_hdr_output_2) {
                    fsrHdrOutputPending = "native";
                } else {
                    fsrHdrOutputPending = "sdr";
                }
            }
        });

    }

    private void initEnfoce() {
        boolean foceFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_enforce_display_mode",false);
        rg_game_display_enforce.check(foceFlag?R.id.rbt_game_display_enforce_1:R.id.rbt_game_display_enforce_2);
    }

    private void initViewData() {
        tx_game_display_screen.setText("分辨率："+width+"x"+height);
        tx_game_display_bit.setText("\t码率："+(bitrate/1000)+"mbps");
        tx_game_display_fps.setText("\t帧率："+fps+"fps");
        tx_game_display_direction.setText("\t方向："+(!direction?"横屏":"竖屏(旋转功能失效，自行在PC端显示器改成竖向)"));
        tx_game_display_ex.setText("\t模式："+(exDiaplay?"外接显示器":"正常模式"));
    }

    private void initLock(){
        int lockFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("enable_screen_on_auto",0);
        switch (lockFlag){
            case 0:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_1);
                break;
            case 1:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_2);
                break;
            case 2:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_3);
                break;
        }
    }

    private void initAudio(){
        boolean audioFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_host_audio",false);
        rg_game_display_audio.check(audioFlag?R.id.rbt_game_display_audio_2:R.id.rbt_game_display_audio_1);
    }

    private void initHDR(){
        boolean hdrFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_enable_hdr",false);
        rg_game_display_hdr.check(hdrFlag?R.id.rbt_game_display_hdr_1:R.id.rbt_game_display_hdr_2);
    }
    private void initIgnoreHDR(){
        boolean hdrFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("ignoreCheckHDR",false);
        rg_game_display_ignore_hdr.check(hdrFlag?R.id.rbt_game_display_ignore_hdr_1:R.id.rbt_game_display_ignore_hdr_2);
    }

    private void initHdrHighBrightness() {
        updateHdrHighBrightnessVisibility(PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean("checkbox_enable_hdr", false));
        boolean hdrHighBrightness = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(PreferenceConfiguration.ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING, false);
        rg_game_display_hdr_high_brightness.check(hdrHighBrightness
                ? R.id.rbt_game_display_hdr_high_brightness_1
                : R.id.rbt_game_display_hdr_high_brightness_2);
    }

    private void updateHdrHighBrightnessVisibility(boolean hdrEnabled) {
        if (v_game_display_hdr_high_brightness != null) {
            v_game_display_hdr_high_brightness.setVisibility(hdrEnabled ? View.VISIBLE : View.GONE);
        }
    }


    private void initLowLatency(){
        boolean lowFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("enable_lowLatency_experiment",false);
        rg_game_display_lowlatency.check(lowFlag?R.id.rbt_game_display_lowlatency_1:R.id.rbt_game_display_lowlatency_2);
    }

    private void initVD(){
        int vddValue=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("vdValue",0);
        switch (vddValue){
            case 0://关闭
                rg_game_display_vd.check(R.id.rbt_game_display_vd_1);
                break;
            case 1://扩展虚拟屏
                rg_game_display_vd.check(R.id.rbt_game_display_vd_2);
                break;
            case 2://仅虚拟屏
                rg_game_display_vd.check(R.id.rbt_game_display_vd_3);
                break;
        }
    }


    private void initVideoFormat(){
        String format=PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("video_format","auto");
        switch (format){
            case "auto":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_1);
                break;
            case "neverh265":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_2);
                break;
            case "forceh265":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_3);
                break;
            case "forceav1":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_4);
                break;
        }
    }

    private void initFsr() {
        if ("2k".equalsIgnoreCase(fsrTargetPending)) {
            rg_game_display_fsr.check(R.id.rbt_game_display_fsr_2);
        }
        else if ("4k".equalsIgnoreCase(fsrTargetPending)) {
            rg_game_display_fsr.check(R.id.rbt_game_display_fsr_3);
        }
        else {
            rg_game_display_fsr.check(R.id.rbt_game_display_fsr_1);
        }
        updateFsrDetailState();
    }

    private void initRenderMode() {
        rg_game_display_render_mode.check(
                PreferenceConfiguration.VIDEO_RENDER_MODE_GLES.equalsIgnoreCase(renderModePending)
                        ? R.id.rbt_game_display_render_mode_gles
                        : R.id.rbt_game_display_render_mode_system);
    }

    private void initStereo3d() {
        rg_game_display_stereo_3d.check(
                PreferenceConfiguration.STEREO_3D_MODE_SBS.equalsIgnoreCase(stereo3dModePending)
                        ? R.id.rbt_game_display_stereo_3d_sbs
                        : R.id.rbt_game_display_stereo_3d_off);
        updateStereo3dDetailState();
    }

    private void initStereo3dDepth() {
        if ("soft".equalsIgnoreCase(stereo3dDepthPending)) {
            rg_game_display_stereo_3d_depth.check(R.id.rbt_game_display_stereo_3d_depth_soft);
        }
        else if ("strong".equalsIgnoreCase(stereo3dDepthPending)) {
            rg_game_display_stereo_3d_depth.check(R.id.rbt_game_display_stereo_3d_depth_strong);
        }
        else {
            rg_game_display_stereo_3d_depth.check(R.id.rbt_game_display_stereo_3d_depth_standard);
        }
    }

    private void initStereo3dConvergence() {
        if ("near".equalsIgnoreCase(stereo3dConvergencePending)) {
            rg_game_display_stereo_3d_convergence.check(
                    R.id.rbt_game_display_stereo_3d_convergence_near);
        }
        else if ("far".equalsIgnoreCase(stereo3dConvergencePending)) {
            rg_game_display_stereo_3d_convergence.check(
                    R.id.rbt_game_display_stereo_3d_convergence_far);
        }
        else {
            rg_game_display_stereo_3d_convergence.check(
                    R.id.rbt_game_display_stereo_3d_convergence_standard);
        }
    }

    private void initStereo3dSwapEyes() {
        rg_game_display_stereo_3d_swap_eyes.check(stereo3dSwapEyesPending
                ? R.id.rbt_game_display_stereo_3d_swap_eyes_on
                : R.id.rbt_game_display_stereo_3d_swap_eyes_off);
    }

    private void initFsrSharpness() {
        if ("soft".equalsIgnoreCase(fsrSharpnessPending)) {
            rg_game_display_fsr_sharpness.check(R.id.rbt_game_display_fsr_sharpness_1);
        }
        else if ("strong".equalsIgnoreCase(fsrSharpnessPending)) {
            rg_game_display_fsr_sharpness.check(R.id.rbt_game_display_fsr_sharpness_3);
        }
        else if ("max".equalsIgnoreCase(fsrSharpnessPending)) {
            rg_game_display_fsr_sharpness.check(R.id.rbt_game_display_fsr_sharpness_4);
        }
        else {
            rg_game_display_fsr_sharpness.check(R.id.rbt_game_display_fsr_sharpness_2);
        }
    }

    private void initFsrHdrOutput() {
        rg_game_display_fsr_hdr_output.check("native".equalsIgnoreCase(fsrHdrOutputPending)
                ? R.id.rbt_game_display_fsr_hdr_output_2
                : R.id.rbt_game_display_fsr_hdr_output_1);
    }

    private void updateFsrDetailState() {
        boolean glesRendering = PreferenceConfiguration.VIDEO_RENDER_MODE_GLES
                .equalsIgnoreCase(renderModePending);
        int fsrSectionVisibility = glesRendering ? View.VISIBLE : View.GONE;
        v_game_display_fsr_header.setVisibility(fsrSectionVisibility);
        rg_game_display_fsr.setVisibility(fsrSectionVisibility);
        boolean fsrEnabledPending = glesRendering && !"off".equalsIgnoreCase(fsrTargetPending);
        int visibility = fsrEnabledPending ? View.VISIBLE : View.GONE;
        v_game_display_fsr_details.setVisibility(visibility);
    }

    private void updateStereo3dDetailState() {
        boolean glesRendering = PreferenceConfiguration.VIDEO_RENDER_MODE_GLES
                .equalsIgnoreCase(renderModePending);
        int sectionVisibility = glesRendering ? View.VISIBLE : View.GONE;
        v_game_display_stereo_3d_header.setVisibility(sectionVisibility);
        rg_game_display_stereo_3d.setVisibility(sectionVisibility);
        boolean stereo3dEnabledPending = glesRendering
                && PreferenceConfiguration.STEREO_3D_MODE_SBS.equalsIgnoreCase(stereo3dModePending);
        v_game_display_stereo_3d_details.setVisibility(
                stereo3dEnabledPending ? View.VISIBLE : View.GONE);
    }

    public void setShowLock(boolean showLock) {
        this.showLock = showLock;
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }


    private void saveVideoFormat(String value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString("video_format",value)
                .commit();
    }

    private void saveLock(int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt("enable_screen_on_auto",value)
                .commit();
    }


    private void saveAudio(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_host_audio",value)
                .commit();
    }

    private void saveHDR(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_enable_hdr",value)
                .commit();
    }

    private void saveVD(int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt("vdValue",value)
                .commit();
    }

    private void saveEnForce(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_enforce_display_mode",value)
                .commit();
    }

    private void savelowLatency(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("enable_lowLatency_experiment",value)
                .commit();
    }

    private void saveIgnoreHDR(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("ignoreCheckHDR",value)
                .commit();
    }

    private void saveHdrHighBrightness(boolean value) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(PreferenceConfiguration.ENABLE_HDR_HIGH_BRIGHTNESS_PREF_STRING, value)
                .commit();
    }

    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.ibtn_back){
            dismiss();
            return;
        }

        if(v.getId()==R.id.btn_right){
            if(width==0||height==0||bitrate==0||fps==0){
                Toast.makeText(getActivity(),"请检查配置信息！",Toast.LENGTH_SHORT).show();
                return;
            }
            if(onClick==null){
                return;
            }
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putString(PreferenceConfiguration.RESOLUTION_PREF_STRING,width+"x"+height)
                    .putString(PreferenceConfiguration.FPS_PREF_STRING,String.valueOf(fps))
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,bitrate)
                    .putString("edit_diy_w_h",width+"x"+height)
                    .putBoolean("checkbox_enable_exdisplay",exDiaplay)
                    .putBoolean(PreferenceConfiguration.CHECKBOX_ENABLE_PORTRAIT,direction)
                    .putString(PreferenceConfiguration.VIDEO_RENDER_MODE_PREF_STRING, renderModePending)
                    .putString(PreferenceConfiguration.STEREO_3D_MODE_PREF_STRING, stereo3dModePending)
                    .putString(PreferenceConfiguration.STEREO_3D_DEPTH_PREF_STRING, stereo3dDepthPending)
                    .putString(PreferenceConfiguration.STEREO_3D_CONVERGENCE_PREF_STRING,
                            stereo3dConvergencePending)
                    .putBoolean(PreferenceConfiguration.STEREO_3D_SWAP_EYES_PREF_STRING,
                            stereo3dSwapEyesPending)
                    .putString(PreferenceConfiguration.FSR_TARGET_PREF_STRING, fsrTargetPending)
                    .putString("list_fsr_sharpness", fsrSharpnessPending)
                    .putString("list_fsr_hdr_output", fsrHdrOutputPending)
                    .commit();
            if(prefConfig!=null){
                prefConfig.width=width;
                prefConfig.height=height;
                prefConfig.bitrate=bitrate;
                prefConfig.fps=fps;
                prefConfig.enablePortrait=direction;
                prefConfig.enableExDisplay=exDiaplay;
                prefConfig.videoRenderMode = PreferenceConfiguration.VIDEO_RENDER_MODE_GLES
                        .equalsIgnoreCase(renderModePending)
                        ? PreferenceConfiguration.VideoRenderMode.GLES
                        : PreferenceConfiguration.VideoRenderMode.SYSTEM;
                prefConfig.stereo3dMode = stereo3dModePending;
                prefConfig.stereo3dDepth = stereo3dDepthPending;
                prefConfig.stereo3dConvergence = stereo3dConvergencePending;
                prefConfig.stereo3dSwapEyes = stereo3dSwapEyesPending;
            }
            dismiss();
            onClick.click();
            return;
        }
        if(v.getId()==R.id.bt_display_screen){
            GameDisplayResolutionFragment fragment=new GameDisplayResolutionFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("分辨率");
            fragment.setOnClick(new GameDisplayResolutionFragment.onClick() {
                @Override
                public void click(int w, int h) {
                    width=w;
                    height=h;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display_exchange){
            int h=height;
            int w=width;
            width=h;
            height=w;
            initViewData();
            return;
        }
        if(v.getId()==R.id.bt_display_direction){
            direction=!direction;
            initViewData();
            return;
        }

        if(v.getId()==R.id.bt_display_bitrate){
            GameDisplayBitrateFragment fragment=new GameDisplayBitrateFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("码率");
            fragment.setOnClick(new GameDisplayBitrateFragment.onClick() {
                @Override
                public void click(int num) {
                    bitrate=num*1000;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }
        if(v.getId()==R.id.bt_display_fps){
            GameDisplayFpsFragment fragment=new GameDisplayFpsFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("帧率");
            fragment.setOnClick(new GameDisplayFpsFragment.onClick() {
                @Override
                public void click(int fps2) {
                    fps=fps2;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display_ex){
            exDiaplay=!exDiaplay;
            initViewData();
            return;
        }
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }
    private onClick onClick;

    public interface onClick{
        void click();
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
