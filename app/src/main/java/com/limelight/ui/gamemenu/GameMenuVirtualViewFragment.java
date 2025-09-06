package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.utils.UiHelper;

import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameMenuVirtualViewFragment extends BaseGameMenuDialog implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_virtual_gamepad;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private Button btn_vibration;

    private Button btn_free_rocker;

    private SeekBar sb_adjust_free_rocker;

    private SeekBar sb_adjust_keyboard_all;

    private SeekBar sb_height_keyboard_all;

    private SeekBar sb_adjust_virtual_gamepad;

    private SeekBar sb_gamepad_scale_factor;

    private TextView tx_gamepad_scale_factor;

    private TextView tx_adjust_free_rocker;

    private TextView tx_adjust_keyboard_all;

    private TextView tx_height_keyboard_all;

    private TextView tx_adjust_virtual_gamepad;

    private RadioGroup rg_game_virtual_pad;

    private RadioGroup rg_game_virtual_pad_key;

    private RadioGroup rg_game_virtual_key_scheme;

    private RadioGroup rg_game_virtual_key_color;

    private RadioGroup rg_game_virtual_key_file;

    private RadioGroup rg_game_pad_skin;

    private VirtualController.ControllerMode gamePadMode;
    private KeyBoardController.ControllerMode gameKeyMode;

    private Button btn_button_square;

    private String[] keyValues;
    private String keyName;
    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        btn_vibration=v.findViewById(R.id.btn_vibration);
        btn_free_rocker=v.findViewById(R.id.btn_free_rocker);
        btn_button_square=v.findViewById(R.id.btn_button_square);

        sb_adjust_free_rocker=v.findViewById(R.id.sb_adjust_free_rocker);
        sb_adjust_keyboard_all=v.findViewById(R.id.sb_adjust_keyboard_all);
        sb_height_keyboard_all=v.findViewById(R.id.sb_height_keyboard_all);
        sb_adjust_virtual_gamepad=v.findViewById(R.id.sb_adjust_virtual_gamepad);

        tx_adjust_free_rocker=v.findViewById(R.id.tx_adjust_free_rocker);
        tx_adjust_keyboard_all=v.findViewById(R.id.tx_adjust_keyboard_all);
        tx_height_keyboard_all=v.findViewById(R.id.tx_height_keyboard_all);
        tx_adjust_virtual_gamepad=v.findViewById(R.id.tx_adjust_virtual_gamepad);

        rg_game_virtual_key_scheme=v.findViewById(R.id.rg_game_virtual_key_scheme);
        rg_game_virtual_pad=v.findViewById(R.id.rg_game_virtual_pad);
        rg_game_virtual_pad_key=v.findViewById(R.id.rg_game_virtual_key);

        rg_game_virtual_key_color=v.findViewById(R.id.rg_game_virtual_key_color);
        rg_game_virtual_key_file=v.findViewById(R.id.rg_game_virtual_key_file);

        rg_game_pad_skin=v.findViewById(R.id.rg_game_pad_skin);

        sb_gamepad_scale_factor=v.findViewById(R.id.sb_gamepad_scale_factor);
        tx_gamepad_scale_factor=v.findViewById(R.id.tx_gamepad_scale_factor);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        initViewHeight();
        initViewAdjust();

        ibtn_back.setOnClickListener(this);
        btn_vibration.setOnClickListener(this);
        btn_free_rocker.setOnClickListener(this);
        v.findViewById(R.id.btn_button_square).setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);

        v.findViewById(R.id.bt_keyboard_list).setOnClickListener(this);


        sb_adjust_free_rocker.setOnSeekBarChangeListener(this);
        sb_adjust_keyboard_all.setOnSeekBarChangeListener(this);
        sb_height_keyboard_all.setOnSeekBarChangeListener(this);
        sb_adjust_virtual_gamepad.setOnSeekBarChangeListener(this);
        sb_gamepad_scale_factor.setOnSeekBarChangeListener(this);

        switch (gamePadMode){
            case Active:
                rg_game_virtual_pad.check(R.id.btn_game_virtual_nomall);
                break;
            case MoveButtons:
                rg_game_virtual_pad.check(R.id.btn_game_virtual_move);
                break;
            case ResizeButtons:
                rg_game_virtual_pad.check(R.id.btn_game_virtual_zoom);
                break;
            case DisableEnableButtons:
                rg_game_virtual_pad.check(R.id.btn_game_virtual_disable);
                break;
        }

        switch (gameKeyMode){
            case Active:
                rg_game_virtual_pad_key.check(R.id.btn_game_virtual_key_nomall);
                break;
            case MoveButtons:
                rg_game_virtual_pad_key.check(R.id.btn_game_virtual_key_move);
                break;
            case ResizeButtons:
                rg_game_virtual_pad_key.check(R.id.btn_game_virtual_key_zoom);
                break;
            case DisableEnableButtons:
                rg_game_virtual_pad_key.check(R.id.btn_game_virtual_key_disable);
                break;
        }

        rg_game_virtual_pad.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(onClick==null){
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_nomall){
                    onClick.switchModeGamePad("正常", VirtualController.ControllerMode.Active);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_move){
                    onClick.switchModeGamePad("位移", VirtualController.ControllerMode.MoveButtons);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_zoom){
                    onClick.switchModeGamePad("缩放", VirtualController.ControllerMode.ResizeButtons);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_disable){
                    onClick.switchModeGamePad("禁用", VirtualController.ControllerMode.DisableEnableButtons);
                    return;
                }
            }
        });

        rg_game_virtual_pad_key.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(onClick==null){
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_key_nomall){
                    onClick.switchModeGameKey("正常", KeyBoardController.ControllerMode.Active);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_key_move){
                    onClick.switchModeGameKey("位移", KeyBoardController.ControllerMode.MoveButtons);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_key_zoom){
                    onClick.switchModeGameKey("缩放", KeyBoardController.ControllerMode.ResizeButtons);
                    return;
                }
                if(checkedId==R.id.btn_game_virtual_key_disable){
                    onClick.switchModeGameKey("禁用", KeyBoardController.ControllerMode.DisableEnableButtons);
                    return;
                }
            }
        });

        keyValues=getResources().getStringArray(R.array.keyboard_axi_values);
        keyName = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);
        for (int i = 0; i < keyValues.length; i++) {
            if(TextUtils.equals(keyName,keyValues[i])){
                switch (i){
                    case 0:
                        rg_game_virtual_key_scheme.check(R.id.btn_game_virtual_key_scheme_1);
                        break;
                    case 1:
                        rg_game_virtual_key_scheme.check(R.id.btn_game_virtual_key_scheme_2);
                        break;
                    case 2:
                        rg_game_virtual_key_scheme.check(R.id.btn_game_virtual_key_scheme_3);
                        break;
                    case 3:
                        rg_game_virtual_key_scheme.check(R.id.btn_game_virtual_key_scheme_4);
                        break;
                    case 4:
                        rg_game_virtual_key_scheme.check(R.id.btn_game_virtual_key_scheme_5);
                        break;
                }
                break;
            }
        }
        rg_game_virtual_key_scheme.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.btn_game_virtual_key_scheme_1){
                    keyName=keyValues[0];
                }
                if(checkedId==R.id.btn_game_virtual_key_scheme_2){
                    keyName=keyValues[1];
                }
                if(checkedId==R.id.btn_game_virtual_key_scheme_3){
                    keyName=keyValues[2];
                }
                if(checkedId==R.id.btn_game_virtual_key_scheme_4){
                    keyName=keyValues[3];
                }
                if(checkedId==R.id.btn_game_virtual_key_scheme_5){
                    keyName=keyValues[4];
                }
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit()
                        .putString(OSC_PREFERENCE,keyName)
                        .commit();
            }
        });

        switch (PreferenceConfiguration.readPreferences(getActivity()).virtualkeyViewNormalColor){
            case 0xF0000000:
                rg_game_virtual_key_color.check(R.id.btn_game_virtual_key_color_1);
                break;
            case 0xF0FFFFFF:
                rg_game_virtual_key_color.check(R.id.btn_game_virtual_key_color_2);
                break;
            case 0xF0888888:
                rg_game_virtual_key_color.check(R.id.btn_game_virtual_key_color_3);
                break;
        }

        rg_game_virtual_key_color.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int value=0xF0888888;
                if(checkedId==R.id.btn_game_virtual_key_color_1){
                    value=0xF0000000;
                }
                if(checkedId==R.id.btn_game_virtual_key_color_2){
                    value=0xF0FFFFFF;
                }
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit()
                        .putInt("virtual_key_view_normal_color",value)
                        .commit();
            }
        });

        switch (PreferenceConfiguration.readPreferences(getActivity()).virtualKeyboardFileUsed){
            case 0:
                rg_game_virtual_key_file.check(R.id.btn_game_virtual_key_file_1);
                break;
            case 1:
                rg_game_virtual_key_file.check(R.id.btn_game_virtual_key_file_2);
                break;
            case 2:
                rg_game_virtual_key_file.check(R.id.btn_game_virtual_key_file_3);
                break;
        }

        switch (PreferenceConfiguration.readPreferences(getActivity()).gamepad_skin){
            case 0:
                rg_game_pad_skin.check(R.id.btn_game_pad_skin_1);
                break;
            case 1:
                rg_game_pad_skin.check(R.id.btn_game_pad_skin_2);
                break;
            case 2:
                rg_game_pad_skin.check(R.id.btn_game_pad_skin_3);
                break;
        }

        rg_game_virtual_key_file.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int value=0;
                if(checkedId==R.id.btn_game_virtual_key_file_2){
                    value=1;
                }
                if(checkedId==R.id.btn_game_virtual_key_file_3){
                    value=2;
                }
                if(prefConfig!=null){
                    prefConfig.virtualKeyboardFileUsed=value;
                }
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit()
                        .putInt("virtual_Key_board_file_used",value)
                        .commit();
                if(onClick!=null){
                    onClick.click("刷新",0);
                }
            }
        });

        rg_game_pad_skin.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int value=0;
                if(checkedId==R.id.btn_game_pad_skin_2){
                    value=1;
                }
                if(checkedId==R.id.btn_game_pad_skin_3){
                    value=2;
                }
                if(prefConfig!=null){
                    prefConfig.gamepad_skin=value;
                }
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit()
                        .putInt("onscreen_game_pad_skin",value)
                        .commit();
                if(onClick!=null){
                    onClick.click("刷新",0);
                }
            }
        });
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setGameKeyMode(KeyBoardController.ControllerMode gameKeyMode) {
        this.gameKeyMode = gameKeyMode;
    }

    public void setGamePadMode(VirtualController.ControllerMode gamePadMode) {
        this.gamePadMode = gamePadMode;
    }

    private void initViewData(){
        btn_vibration.setBackgroundResource(prefConfig.enableKeyboardVibrate?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
        btn_free_rocker.setBackgroundResource(prefConfig.enableNewAnalogStick?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
        btn_button_square.setBackgroundResource(prefConfig.enableKeyboardSquare?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);

    }

    private void initViewHeight(){
        sb_height_keyboard_all.setProgress(prefConfig.oscKeyboardHeight);
        tx_height_keyboard_all.setText("高度："+prefConfig.oscKeyboardHeight);

    }

    private void initViewAdjust(){
        sb_adjust_virtual_gamepad.setProgress(prefConfig.oscOpacity);
        tx_adjust_virtual_gamepad.setText("透明度："+prefConfig.oscOpacity+"%");

        sb_adjust_free_rocker.setProgress(prefConfig.senableNewAnalogStickOpacity);
        sb_adjust_keyboard_all.setProgress(prefConfig.oscKeyboardOpacity);

        tx_adjust_free_rocker.setText("透明度："+prefConfig.senableNewAnalogStickOpacity+"%");
        tx_adjust_keyboard_all.setText("透明度："+prefConfig.oscKeyboardOpacity+"%");

        sb_gamepad_scale_factor.setProgress(prefConfig.virtualGamePadScaleFactor);
        tx_gamepad_scale_factor.setText("缩放："+prefConfig.virtualGamePadScaleFactor+"%");
    }


    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.ibtn_back){
            dismiss();
            return;
        }

        if(v.getId()==R.id.btn_right){
            if(onClick!=null){
                onClick.click("刷新",0);
            }
            return;
        }
        if(v.getId()==R.id.bt_keyboard_list){
            GameListKeyBoardFragment fragment=new GameListKeyBoardFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("虚拟按键列表");
            fragment.setOnClick(new GameListKeyBoardFragment.onClick() {
                @Override
                public void click() {
                    if(onClick!=null){
                        onClick.click("刷新",0);
                    }
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.btn_vibration){
            prefConfig.enableKeyboardVibrate=!prefConfig.enableKeyboardVibrate;
            initViewData();
            return;
        }

        if(v.getId()==R.id.btn_free_rocker){
            prefConfig.enableNewAnalogStick=!prefConfig.enableNewAnalogStick;
            initViewData();
            return;
        }

        if(v.getId()==R.id.btn_button_square){
            prefConfig.enableKeyboardSquare=!prefConfig.enableKeyboardSquare;
            initViewData();
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putBoolean("checkbox_enable_keyboard_square",prefConfig.enableKeyboardSquare)
                    .commit();
            return;
        }
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(seekBar==sb_height_keyboard_all){
            prefConfig.oscKeyboardHeight=progress;
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putInt("seekbar_keyboard_axi_height",progress)
                    .commit();
            initViewHeight();
        }
        if(seekBar==sb_adjust_keyboard_all){
            prefConfig.oscKeyboardOpacity=progress;
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putInt("seekbar_keyboard_axi_opacity",progress)
                    .commit();
            initViewAdjust();
        }
        if(seekBar==sb_adjust_free_rocker){
            prefConfig.senableNewAnalogStickOpacity=progress;
            initViewAdjust();
        }
        if(seekBar==sb_adjust_virtual_gamepad){
            prefConfig.oscOpacity=progress;
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putInt(PreferenceConfiguration.OSC_OPACITY_PREF_STRING,progress)
                    .commit();
            initViewAdjust();
        }

        if(seekBar==sb_gamepad_scale_factor){
            prefConfig.virtualGamePadScaleFactor=progress;
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putInt("virtualGamePadScaleFactor",progress)
                    .commit();
            initViewAdjust();
        }

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private onClick onClick;

    public interface onClick{
        void click(String name,int index);
        void switchModeGamePad(String name, VirtualController.ControllerMode mode);
        void switchModeGameKey(String name, KeyBoardController.ControllerMode mode);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
