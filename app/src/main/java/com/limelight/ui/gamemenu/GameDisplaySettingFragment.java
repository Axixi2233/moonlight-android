package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * 游戏菜单-杂项
 */
public class GameDisplaySettingFragment extends BaseGameMenuDialog {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_setting;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private CheckBox btn_game_float_ball;
    private CheckBox btn_game_audio_mute;
    private CheckBox btn_game_lite_ext;
    private CheckBox btn_game_lite_click;
    private CheckBox btn_game_rumble_force;
    private CheckBox btn_game_rumble_force_stop;

    //悬浮球记住位置
    private CheckBox btn_game_float_ball_postion;

    private CheckBox btn_game_rumble_hud;

    private RadioGroup rg_game_setting_control;

    private RadioGroup rg_game_setting_touch;

    private RadioGroup rg_game_setting_float_ball;


    private CheckBox btn_game_force_gyro;
    private CheckBox btn_game_force_gyro_left_trgger;
    private CheckBox btn_game_force_gyro_switch;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        btn_game_float_ball=v.findViewById(R.id.btn_game_float_ball);
        btn_game_audio_mute=v.findViewById(R.id.btn_game_audio_mute);
        btn_game_lite_ext=v.findViewById(R.id.btn_game_lite_ext);
        btn_game_lite_click=v.findViewById(R.id.btn_game_lite_click);
        btn_game_float_ball_postion=v.findViewById(R.id.btn_game_float_ball_postion);
        rg_game_setting_control=v.findViewById(R.id.rg_game_setting_control);
        rg_game_setting_touch =v.findViewById(R.id.rg_game_setting_touch);
        btn_game_rumble_force=v.findViewById(R.id.btn_game_rumble_force);
        btn_game_rumble_force_stop=v.findViewById(R.id.btn_game_rumble_force_stop);
        btn_game_rumble_hud=v.findViewById(R.id.btn_game_rumble_hud);
        rg_game_setting_float_ball=v.findViewById(R.id.rg_game_setting_float_ball);
        btn_game_force_gyro=v.findViewById(R.id.btn_game_force_gyro);
        btn_game_force_gyro_left_trgger=v.findViewById(R.id.btn_game_force_gyro_left_trgger);
        btn_game_force_gyro_switch=v.findViewById(R.id.btn_game_force_gyro_switch);
        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        initControl();
        initTouchNumber();
        initFloatBall();
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        btn_game_float_ball.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableAXFloating=isChecked;
            setSetting("checkbox_enable_ax_floating",isChecked);
            if(onClick!=null){
                onClick.click(0,isChecked);
            }
        });

        btn_game_float_ball_postion.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.axFloatingPostionAuto =isChecked;
                setSetting("ax_floating_postion_auto",isChecked);
                if(!isChecked){
                    saveSettingFloat("ax_floating_postion_x",-1);
                    saveSettingFloat("ax_floating_postion_y",-1);
                }
            }
        });

        btn_game_audio_mute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.audioMute=isChecked;
            setSetting("ax_audio_mute",isChecked);
        });
        btn_game_lite_ext.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enablePerfOverlayLiteExt=isChecked;
            setSetting("checkbox_enable_perf_overlay_lite_ext",isChecked);
        });
        btn_game_lite_click.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enablePerfOverlayLiteDialog=isChecked;
            setSetting("checkbox_enable_perf_overlay_lite_dialog",isChecked);
            if(onClick!=null){
                onClick.click(2,isChecked);
            }
        });

        btn_game_rumble_force.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.enableForceStrongVibrations=isChecked;
                setSetting("enable_force_strong_vibrations",isChecked);
            }
        });
        btn_game_rumble_force_stop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.enableForceStrongVibrationsStop=isChecked;
                setSetting("enable_force_strong_vibrations_stop",isChecked);
            }
        });

        btn_game_rumble_hud.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.showRumbleHUD=isChecked;
                setSetting("rumble_HUD_show",isChecked);
                if(onClick!=null){
                    onClick.click(1,isChecked);
                }
            }
        });

        rg_game_setting_control.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //关闭
                if(checkedId==R.id.rbt_game_setting_control_1){
                    prefConfig.mouseEmulation=false;
                    saveSetting("checkbox_mouse_emulation",false);
                    prefConfig.mouseEmulationGameMenu=0;
                    saveSetting("ax_quick_game_menu_key",0);
                    return;
                }
                //开始键长按
                if(checkedId==R.id.rbt_game_setting_control_2){
                    prefConfig.mouseEmulation=true;
                    saveSetting("checkbox_mouse_emulation",true);
                    prefConfig.mouseEmulationGameMenu=0;
                    saveSetting("ax_quick_game_menu_key",0);
                    return;
                }
                //xbox键单机
                if(checkedId==R.id.rbt_game_setting_control_3){
                    prefConfig.mouseEmulation=true;
                    saveSetting("checkbox_mouse_emulation",true);
                    prefConfig.mouseEmulationGameMenu=1;
                    saveSetting("ax_quick_game_menu_key",1);
                    return;
                }
                //菜单键 长按
                if(checkedId==R.id.rbt_game_setting_control_4){
                    prefConfig.mouseEmulation=true;
                    saveSetting("checkbox_mouse_emulation",true);
                    prefConfig.mouseEmulationGameMenu=2;
                    saveSetting("ax_quick_game_menu_key",2);
                    return;
                }
            }
        });

        rg_game_setting_touch.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_setting_touch_1){
                    prefConfig.quickSoftKeyboardFingers=0;
                    saveSetting("touch_number_quick_soft_keyboard",0);
                    return;
                }
                if(checkedId==R.id.rbt_game_setting_touch_2){
                    prefConfig.quickSoftKeyboardFingers=3;
                    saveSetting("touch_number_quick_soft_keyboard",3);
                    return;
                }
                if(checkedId==R.id.rbt_game_setting_touch_3){
                    prefConfig.quickSoftKeyboardFingers=4;
                    saveSetting("touch_number_quick_soft_keyboard",4);
                    return;
                }
                if(checkedId==R.id.rbt_game_setting_touch_4){
                    prefConfig.quickSoftKeyboardFingers=5;
                    saveSetting("touch_number_quick_soft_keyboard",5);
                    return;
                }
            }
        });

        rg_game_setting_float_ball.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_setting_float_ball_1){
                    prefConfig.axFloatingOperate=0;
                    saveSetting("ax_floating_operate",0);
                    return;
                }
                if(checkedId==R.id.rbt_game_setting_float_ball_2){
                    prefConfig.axFloatingOperate=1;
                    saveSetting("ax_floating_operate",1);
                    return;
                }
                if(checkedId==R.id.rbt_game_setting_float_ball_3){
                    prefConfig.axFloatingOperate=2;
                    saveSetting("ax_floating_operate",2);
                    return;
                }

            }
        });

        btn_game_force_gyro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.gameForceGyro=isChecked;
                setSetting("gameForceGyro",isChecked);
            }
        });

        btn_game_force_gyro_left_trgger.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.gameForceGyroLeftTrigger=isChecked;
                setSetting("gameForceGyroLeftTrigger",isChecked);
            }
        });
        btn_game_force_gyro_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefConfig.gameForceGyroXYSwitch=isChecked;
                setSetting("gameForceGyroXYSwitch",isChecked);
            }
        });
    }


    private void initControl(){
        boolean enableKey=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_mouse_emulation",false);
        if(!enableKey){
            rg_game_setting_control.check(R.id.rbt_game_setting_control_1);
            return;
        }
        int keyFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("ax_quick_game_menu_key",0);
        switch (keyFlag){
            case 0:
                rg_game_setting_control.check(R.id.rbt_game_setting_control_2);
                break;
            case 1:
                rg_game_setting_control.check(R.id.rbt_game_setting_control_3);
                break;
            case 2:
                rg_game_setting_control.check(R.id.rbt_game_setting_control_4);
                break;
        }
    }

    private void initTouchNumber(){
        int keyFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("touch_number_quick_soft_keyboard",0);
        switch (keyFlag){
            case 0:
                rg_game_setting_touch.check(R.id.rbt_game_setting_touch_1);
                break;
            case 3:
                rg_game_setting_touch.check(R.id.rbt_game_setting_touch_2);
                break;
            case 4:
                rg_game_setting_touch.check(R.id.rbt_game_setting_touch_3);
                break;
            case 5:
                rg_game_setting_touch.check(R.id.rbt_game_setting_touch_4);
                break;
        }
    }

    private void initFloatBall(){
        int keyFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("ax_floating_operate",0);
        switch (keyFlag){
            case 0:
                rg_game_setting_float_ball.check(R.id.rbt_game_setting_float_ball_1);
                break;
            case 1:
                rg_game_setting_float_ball.check(R.id.rbt_game_setting_float_ball_2);
                break;
            case 2:
                rg_game_setting_float_ball.check(R.id.rbt_game_setting_float_ball_3);
                break;
        }
    }

    private void saveSetting(String name,int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(name,value)
                .apply();
    }

    private void saveSettingFloat(String name,float value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putFloat(name,value)
                .apply();
    }

    private void saveSetting(String name,boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(name,value)
                .apply();
    }

    private void setSetting(String name,boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(name,value)
                .apply();
        initViewData();
    }

    private void initViewData() {
        if(prefConfig==null){
            return;
        }
        btn_game_float_ball.setChecked(prefConfig.enableAXFloating);
        btn_game_float_ball_postion.setChecked(prefConfig.axFloatingPostionAuto);
        btn_game_audio_mute.setChecked(prefConfig.audioMute);
        btn_game_lite_ext.setChecked(prefConfig.enablePerfOverlayLiteExt);
        btn_game_lite_click.setChecked(prefConfig.enablePerfOverlayLiteDialog);
        btn_game_rumble_force.setChecked(prefConfig.enableForceStrongVibrations);
        btn_game_rumble_force_stop.setChecked(prefConfig.enableForceStrongVibrationsStop);
        btn_game_rumble_hud.setChecked(prefConfig.showRumbleHUD);

        btn_game_force_gyro.setChecked(prefConfig.gameForceGyro);
        btn_game_force_gyro_left_trgger.setChecked(prefConfig.gameForceGyroLeftTrigger);
        btn_game_force_gyro_switch.setChecked(prefConfig.gameForceGyroXYSwitch);
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    private onClick onClick;

    public interface onClick{
        void click(int index,boolean flag);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }

}
