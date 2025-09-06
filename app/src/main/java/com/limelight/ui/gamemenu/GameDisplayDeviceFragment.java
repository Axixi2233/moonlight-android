package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDisplayDeviceFragment extends BaseGameMenuDialog implements SeekBar.OnSeekBarChangeListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_control;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private CheckBox btn_game_usb;
    private CheckBox btn_game_grip;
    private CheckBox btn_game_trigger;
    private CheckBox btn_game_shake;
    private CheckBox btn_game_gyroscope;
    private CheckBox btn_game_joycon;

    private CheckBox btn_game_battery;

    private CheckBox btn_game_usb_gyroscope;

    private RadioGroup rg_game_control_ds5;

    private SeekBar sb_game_control_ds5_strength;
    private SeekBar sb_game_control_ds5_frequency;
    private SeekBar sb_game_control_ds5_start;
    private SeekBar sb_game_control_ds5_end;


    private TextView tx_game_control_ds5_strength;
    private TextView tx_game_control_ds5_frequency;
    private TextView tx_game_control_ds5_start;
    private TextView tx_game_control_ds5_end;

    private CheckBox btn_game_trigger_rumble;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        btn_game_usb=v.findViewById(R.id.btn_game_usb);
        btn_game_grip=v.findViewById(R.id.btn_game_grip);
        btn_game_trigger=v.findViewById(R.id.btn_game_trigger);
        btn_game_shake=v.findViewById(R.id.btn_game_shake);
        btn_game_gyroscope=v.findViewById(R.id.btn_game_gyroscope);
        btn_game_joycon=v.findViewById(R.id.btn_game_joycon);
        rg_game_control_ds5=v.findViewById(R.id.rg_game_control_ds5);
        btn_game_battery=v.findViewById(R.id.btn_game_battery);
        btn_game_usb_gyroscope=v.findViewById(R.id.btn_game_usb_gyroscope);

        sb_game_control_ds5_strength=v.findViewById(R.id.sb_game_control_ds5_strength);
        sb_game_control_ds5_frequency=v.findViewById(R.id.sb_game_control_ds5_frequency);
        sb_game_control_ds5_start=v.findViewById(R.id.sb_game_control_ds5_start);
        sb_game_control_ds5_end=v.findViewById(R.id.sb_game_control_ds5_end);

        tx_game_control_ds5_strength=v.findViewById(R.id.tx_game_control_ds5_strength);
        tx_game_control_ds5_frequency=v.findViewById(R.id.tx_game_control_ds5_frequency);
        tx_game_control_ds5_start=v.findViewById(R.id.tx_game_control_ds5_start);
        tx_game_control_ds5_end=v.findViewById(R.id.tx_game_control_ds5_end);

        btn_game_trigger_rumble=v.findViewById(R.id.btn_game_trigger_rumble);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.btn_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onClick!=null){
                    onClick.click(1,true);
                    Toast.makeText(getActivity(),"更新成功！",Toast.LENGTH_SHORT).show();
                }
            }
        });

        btn_game_usb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked){
                prefConfig.usbDriver=true;
                setSetting("checkbox_usb_driver", true);
            }
            prefConfig.bindAllUsb=isChecked;
            setSetting("checkbox_usb_bind_all",isChecked);
        });

        btn_game_grip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableFlipRumbleFF=isChecked;
            setSetting("checkbox_flip_rumble_ff",isChecked);
        });
        btn_game_trigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.disableTriggerDeadzone=isChecked;
            setSetting("checkbox_disable_trigger_deadzone",isChecked);
        });
        btn_game_shake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableDeviceRumble=isChecked;
            setSetting("checkbox_enable_device_rumble",isChecked);
        });
        btn_game_gyroscope.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableVirtualControllerMotion=isChecked;
            setSetting("checkbox_enable_virtual_motion",isChecked);
        });
        btn_game_joycon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableJoyConFix=isChecked;
            setSetting("checkbox_enable_joyconfix",isChecked);
        });

        btn_game_battery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.enableBatteryReport=isChecked;
            setSetting("checkbox_gamepad_enable_battery_report",isChecked);
        });

        btn_game_usb_gyroscope.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.usbGyroscopeReport=isChecked;
            setSetting("usbGyroscopeReport",isChecked);
        });

        btn_game_trigger_rumble.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefConfig.gameTriggerRumbleLink=isChecked;
            setSetting("gameTriggerRumbleLink",isChecked);
        });

        sb_game_control_ds5_strength.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_frequency.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_start.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_end.setOnSeekBarChangeListener(this);

        rg_game_control_ds5.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //关闭
                if(checkedId==R.id.rbt_game_control_ds5_1){
                    setDs5TriggerMode(0);
                    return;
                }
                //阻尼
                if(checkedId==R.id.rbt_game_control_ds5_2){
                    setDs5TriggerMode(1);
                    return;
                }
                //扳机
                if(checkedId==R.id.rbt_game_control_ds5_3){
                    setDs5TriggerMode(2);
                    return;
                }
                //自动步枪扳机
                if(checkedId==R.id.rbt_game_control_ds5_4){
                    setDs5TriggerMode(6);
                    return;
                }
            }
        });
    }

    private void setSetting(String name,boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(name,value)
                .commit();
        initViewData();
    }

    private void setDs5TriggerMode(int mode){
        prefConfig.ds5TriggerMode=mode;
//        saveSetting("ds5TriggerMode",mode);
    }

    private void initDs5View(){
        sb_game_control_ds5_strength.setProgress(prefConfig.ds5TriggerStrength);
        tx_game_control_ds5_strength.setText(""+prefConfig.ds5TriggerStrength);

        sb_game_control_ds5_frequency.setProgress(prefConfig.ds5TriggerFrequency);
        tx_game_control_ds5_frequency.setText(""+prefConfig.ds5TriggerFrequency);

        sb_game_control_ds5_start.setProgress(prefConfig.ds5TriggerStart);
        tx_game_control_ds5_start.setText(""+prefConfig.ds5TriggerStart);

        sb_game_control_ds5_end.setProgress(prefConfig.ds5TriggerEnd);
        tx_game_control_ds5_end.setText(""+prefConfig.ds5TriggerEnd);
    }

    private void initViewData() {
        if(prefConfig==null){
            return;
        }
        btn_game_usb.setChecked(prefConfig.bindAllUsb);
        btn_game_grip.setChecked(prefConfig.enableFlipRumbleFF);
        btn_game_trigger.setChecked(prefConfig.disableTriggerDeadzone);
        btn_game_shake.setChecked(prefConfig.enableDeviceRumble);
        btn_game_gyroscope.setChecked(prefConfig.enableVirtualControllerMotion);
        btn_game_joycon.setChecked(prefConfig.enableJoyConFix);
        btn_game_battery.setChecked(prefConfig.enableBatteryReport);
        btn_game_usb_gyroscope.setChecked(prefConfig.usbGyroscopeReport);
        btn_game_trigger_rumble.setChecked(prefConfig.gameTriggerRumbleLink);
        initDs5View();

        switch (prefConfig.ds5TriggerMode){
            case 0:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_1);
                break;
            case 1:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_2);
                break;
            case 2:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_3);
                break;
            case 6:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_4);
                break;
        }
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

    private void saveSetting(String name,int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(name,value)
                .apply();
    }

    private onClick onClick;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(seekBar==sb_game_control_ds5_strength){
            prefConfig.ds5TriggerStrength=progress;
            saveSetting("ds5TriggerStrength",progress);
        }
        if(seekBar==sb_game_control_ds5_frequency){
            prefConfig.ds5TriggerFrequency=progress;
            saveSetting("ds5TriggerFrequency",progress);
        }
        if(seekBar==sb_game_control_ds5_start){
            prefConfig.ds5TriggerStart=progress;
            saveSetting("ds5TriggerStart",progress);
        }
        if(seekBar==sb_game_control_ds5_end){
            prefConfig.ds5TriggerEnd=progress;
            saveSetting("ds5TriggerEnd",progress);
        }
        initDs5View();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public interface onClick{
        void click(int index,boolean flag);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
