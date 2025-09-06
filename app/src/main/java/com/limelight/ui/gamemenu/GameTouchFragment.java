package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

import static com.limelight.preferences.PreferenceConfiguration.TOUCH_SENSITIVITY;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameTouchFragment extends BaseGameMenuDialog implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_touch;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private Button btn_touch_switch;

    private Button btn_touch_center;

    private Button btn_touch_all;

    private SeekBar sb_touch_x;

    private SeekBar sb_touch_y;

    private SeekBar sb_touchpad_x;

    private SeekBar sb_touchpad_y;

    private SeekBar sb_touchpad_view_x;

    private SeekBar sb_touchpad_view_y;

    private TextView tx_touch_x;

    private TextView tx_touch_y;

    private TextView tx_touchpad_x;

    private TextView tx_touchpad_y;

    private TextView tx_touchpad_view_x;

    private TextView tx_touchpad_view_y;
    private SeekBar sb_mouse_gamepad_sensitity;
    private TextView tx_mouse_gamepad_sensitity;

    private SeekBar sb_mouse_sc_amount;
    private TextView tx_mouse_sc_amount;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        btn_touch_switch=v.findViewById(R.id.btn_touch_switch);
        btn_touch_center=v.findViewById(R.id.btn_touch_center);
        btn_touch_all=v.findViewById(R.id.btn_touch_all);

        sb_touch_x=v.findViewById(R.id.sb_touch_x);
        sb_touch_y=v.findViewById(R.id.sb_touch_y);
        sb_touchpad_x=v.findViewById(R.id.sb_touchpad_x);
        sb_touchpad_y=v.findViewById(R.id.sb_touchpad_y);
        sb_touchpad_view_x=v.findViewById(R.id.sb_touchpad_view_x);
        sb_touchpad_view_y=v.findViewById(R.id.sb_touchpad_view_y);

        tx_touch_x=v.findViewById(R.id.tx_touch_x);
        tx_touch_y=v.findViewById(R.id.tx_touch_y);
        tx_touchpad_x=v.findViewById(R.id.tx_touchpad_x);
        tx_touchpad_y=v.findViewById(R.id.tx_touchpad_y);
        tx_touchpad_view_x=v.findViewById(R.id.tx_touchpad_view_x);
        tx_touchpad_view_y=v.findViewById(R.id.tx_touchpad_view_y);

        sb_mouse_gamepad_sensitity=v.findViewById(R.id.sb_mouse_gamepad_sensitity);
        tx_mouse_gamepad_sensitity=v.findViewById(R.id.tx_mouse_gamepad_sensitity);

        sb_mouse_sc_amount=v.findViewById(R.id.sb_mouse_sc_amount);
        tx_mouse_sc_amount=v.findViewById(R.id.tx_mouse_sc_amount);

        tx_title.setText(title);
        initViewData();
        initViewTouch();
        initViewTouchPad();
        initViewTouchPadView();
        initViewMouseGamePadView();

        initViewMouseSCView();

        ibtn_back.setOnClickListener(this);
        btn_touch_switch.setOnClickListener(this);
        btn_touch_center.setOnClickListener(this);
        btn_touch_all.setOnClickListener(this);

        v.findViewById(R.id.btn_right).setOnClickListener(this);

        sb_touch_x.setOnSeekBarChangeListener(this);
        sb_touch_y.setOnSeekBarChangeListener(this);
        sb_touchpad_x.setOnSeekBarChangeListener(this);
        sb_touchpad_y.setOnSeekBarChangeListener(this);
        sb_touchpad_view_x.setOnSeekBarChangeListener(this);
        sb_touchpad_view_y.setOnSeekBarChangeListener(this);
        sb_mouse_gamepad_sensitity.setOnSeekBarChangeListener(this);
        sb_mouse_sc_amount.setOnSeekBarChangeListener(this);
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }


    private void initViewData(){
        btn_touch_switch.setBackgroundResource(prefConfig.enableTouchSensitivity?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
        btn_touch_center.setBackgroundResource(prefConfig.touchSensitivityRotationAuto?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
        btn_touch_all.setBackgroundResource(prefConfig.touchSensitivityGlobal?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
    }

    private void initViewTouch(){
        sb_touch_x.setProgress(prefConfig.touchSensitivityX);
        sb_touch_y.setProgress(prefConfig.touchSensitivityY);
        tx_touch_x.setText("X轴："+prefConfig.touchSensitivityX+"%");
        tx_touch_y.setText("Y轴："+prefConfig.touchSensitivityY+"%");
    }

    private void initViewTouchPad(){
        sb_touchpad_x.setProgress(prefConfig.mouseTouchPadSensitityX);
        sb_touchpad_y.setProgress(prefConfig.mouseTouchPadSensitityY);
        tx_touchpad_x.setText("X轴："+prefConfig.mouseTouchPadSensitityX+"%");
        tx_touchpad_y.setText("Y轴："+prefConfig.mouseTouchPadSensitityY+"%");
    }

    private void initViewTouchPadView(){
        sb_touchpad_view_x.setProgress(prefConfig.touchPadSensitivity);
        sb_touchpad_view_y.setProgress(prefConfig.touchPadYSensitity);
        tx_touchpad_view_x.setText("X轴："+prefConfig.touchPadSensitivity+"%");
        tx_touchpad_view_y.setText("Y轴："+prefConfig.touchPadYSensitity+"%");
    }

    private void initViewMouseGamePadView(){
        sb_mouse_gamepad_sensitity.setProgress(prefConfig.mouseGamePadSensitity);
        tx_mouse_gamepad_sensitity.setText("灵敏度："+prefConfig.mouseGamePadSensitity+"%");
    }

    private void initViewMouseSCView(){
        sb_mouse_sc_amount.setProgress(prefConfig.mouseSCAmount);
        tx_mouse_sc_amount.setText("距离："+prefConfig.mouseSCAmount);
    }

    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.ibtn_back){
            dismiss();
            return;
        }

        if(v.getId()==R.id.btn_right){
            //鼠标触控板模式
            prefConfig.mouseTouchPadSensitityX=100;
            prefConfig.mouseTouchPadSensitityY=100;
            //多点触控屏幕灵敏度
            prefConfig.enableTouchSensitivity=false;
            prefConfig.touchSensitivityX=100;
            prefConfig.touchSensitivityY=100;
            prefConfig.touchSensitivityGlobal=false;
            prefConfig.touchSensitivityRotationAuto=true;

            //触控板模式灵敏度
            prefConfig.touchPadSensitivity=100;
            prefConfig.touchPadYSensitity=100;

            prefConfig.mouseSCAmount=5;

            prefConfig.mouseGamePadSensitity=100;

            saveSetting(TOUCH_SENSITIVITY,100);
            saveSetting("seekbar_touch_sensitivity_opacity_y",100);
            saveSetting("seekbar_mouse_touchpad_sensitivity_x_opacity",100);
            saveSetting("seekbar_mouse_touchpad_sensitivity_y_opacity",100);
            saveSetting("seekbar_touchpad_sensitivity_opacity",100);
            saveSetting("seekbar_touchpad_sensitivity_y_opacity",100);
            saveSetting("mouse_gamepad_sensitity",100);
            saveSetting("mouse_sc_amount",5);

            saveSetting("checkbox_enable_touch_sensitivity",prefConfig.enableTouchSensitivity);
            saveSetting("checkbox_enable_touch_sensitivity_rotation_auto",prefConfig.touchSensitivityRotationAuto);
            saveSetting("checkbox_enable_global_touch_sensitivity",prefConfig.touchSensitivityGlobal);

            initViewData();
            initViewTouch();
            initViewTouchPad();
            initViewTouchPadView();

            initViewMouseGamePadView();
            initViewMouseSCView();

            return;
        }
        if(v.getId()==R.id.btn_touch_switch){
            prefConfig.enableTouchSensitivity=!prefConfig.enableTouchSensitivity;
            saveSetting("checkbox_enable_touch_sensitivity",prefConfig.enableTouchSensitivity);
            initViewData();
            return;
        }

        if(v.getId()==R.id.btn_touch_center){
            prefConfig.touchSensitivityRotationAuto=!prefConfig.touchSensitivityRotationAuto;
            saveSetting("checkbox_enable_touch_sensitivity_rotation_auto",prefConfig.touchSensitivityRotationAuto);
            initViewData();
            return;
        }

        if(v.getId()==R.id.btn_touch_all){
            prefConfig.touchSensitivityGlobal=!prefConfig.touchSensitivityGlobal;
            saveSetting("checkbox_enable_global_touch_sensitivity",prefConfig.touchSensitivityGlobal);
            initViewData();
            return;
        }
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(seekBar==sb_touch_x){
            prefConfig.touchSensitivityX=progress;
            saveSetting(TOUCH_SENSITIVITY,progress);
            initViewTouch();
        }
        if(seekBar==sb_touch_y){
            prefConfig.touchSensitivityY=progress;
            saveSetting("seekbar_touch_sensitivity_opacity_y",progress);
            initViewTouch();
        }
        if(seekBar==sb_touchpad_x){
            prefConfig.mouseTouchPadSensitityX=progress;
            saveSetting("seekbar_mouse_touchpad_sensitivity_x_opacity",progress);
            initViewTouchPad();
        }
        if(seekBar==sb_touchpad_y){
            prefConfig.mouseTouchPadSensitityY=progress;
            saveSetting("seekbar_mouse_touchpad_sensitivity_y_opacity",progress);
            initViewTouchPad();
        }
        if(seekBar==sb_touchpad_view_x){
            prefConfig.touchPadSensitivity=progress;
            saveSetting("seekbar_touchpad_sensitivity_opacity",progress);
            initViewTouchPadView();
        }
        if(seekBar==sb_touchpad_view_y){
            prefConfig.touchPadYSensitity=progress;
            saveSetting("seekbar_touchpad_sensitivity_y_opacity",progress);
            initViewTouchPadView();
        }

        if(seekBar==sb_mouse_gamepad_sensitity){
            prefConfig.mouseGamePadSensitity=progress;
            saveSetting("mouse_gamepad_sensitity",progress);
            initViewMouseGamePadView();
        }

        if(seekBar==sb_mouse_sc_amount){
            prefConfig.mouseSCAmount=progress;
            saveSetting("mouse_sc_amount",progress);
            initViewMouseSCView();
        }

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void saveSetting(String name,int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(name,value)
                .apply();
    }


    private void saveSetting(String name,boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(name,value)
                .apply();
    }
}
