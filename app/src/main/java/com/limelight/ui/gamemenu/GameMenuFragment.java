package com.limelight.ui.gamemenu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.GameMenu;
import com.limelight.R;
import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;
import com.limelight.utils.UiHelper;

import java.security.Key;
import java.util.List;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameMenuFragment extends BaseGameMenuDialog implements View.OnClickListener{

    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu;
    }

    private Button btn_performance;

    private Button btn_game_pad;

    private Button btn_v_keyboard;

    private Button btn_gamepad_mouse;

    private Button btn_screen_move;

    private TextView tx_title_battery;

    private Game game;
    private NvConnection conn;
    private GameInputDevice device;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        btn_performance=v.findViewById(R.id.btn_performance);
        btn_game_pad=v.findViewById(R.id.btn_game_pad);
        btn_v_keyboard=v.findViewById(R.id.btn_v_keyboard);
        btn_gamepad_mouse=v.findViewById(R.id.btn_gamepad_mouse);
        btn_screen_move=v.findViewById(R.id.btn_screen_move);
        if(game!=null){
            btn_performance.setBackgroundResource(game.prefConfig.enablePerfOverlay?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_game_pad.setBackgroundResource(game.prefConfig.onscreenController?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_v_keyboard.setBackgroundResource(game.prefConfig.enableKeyboard?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_screen_move.setBackgroundResource(game.getScreenMoveZoom()?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);

        }
        btn_gamepad_mouse.setVisibility(device!=null?View.VISIBLE:View.INVISIBLE);

        v.findViewById(R.id.btn_unlink).setOnClickListener(this);
        v.findViewById(R.id.btn_exit).setOnClickListener(this);
        v.findViewById(R.id.btn_swicth_screen).setOnClickListener(this);
        v.findViewById(R.id.btn_performance).setOnClickListener(this);
        v.findViewById(R.id.btn_game_pad).setOnClickListener(this);
        v.findViewById(R.id.btn_v_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_screen_move).setOnClickListener(this);
        v.findViewById(R.id.btn_gamepad_mouse).setOnClickListener(this);
        v.findViewById(R.id.bt_touch_sensitivity).setOnClickListener(this);
        v.findViewById(R.id.bt_quick_list).setOnClickListener(this);
        v.findViewById(R.id.bt_touch_list).setOnClickListener(this);
        v.findViewById(R.id.btn_soft_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_desktop).setOnClickListener(this);
        v.findViewById(R.id.btn_window).setOnClickListener(this);
        v.findViewById(R.id.bt_touch_sensitivity).setOnClickListener(this);
        v.findViewById(R.id.btn_hdr).setOnClickListener(this);
        v.findViewById(R.id.btn_display_2).setOnClickListener(this);
        v.findViewById(R.id.btn_display_1).setOnClickListener(this);
        v.findViewById(R.id.bt_virtual_view).setOnClickListener(this);
        v.findViewById(R.id.bt_display).setOnClickListener(this);
        v.findViewById(R.id.bt_device).setOnClickListener(this);
        v.findViewById(R.id.bt_other_setting).setOnClickListener(this);
        v.findViewById(R.id.btn_soft_function).setOnClickListener(this);

        v.findViewById(R.id.btn_performance).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(game!=null){
                    game.switchHUD();
                }
                return false;
            }
        });

//        v.findViewById(R.id.btn_soft_keyboard).setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                if(game!=null){
//                    game.sendClipboardText();
//                }
//                return false;
//            }
//        });

        tx_title_battery=v.findViewById(R.id.tx_title_battery);
        tx_title_battery.setText(getPhoneBattery(getActivity())+"%");
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    @Override
    public void onClick(View v) {
        //操作
        if(v.getId()==R.id.btn_soft_function){
            GameFunctionFragment fragment=new GameFunctionFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("操作");
            fragment.setOnClick(new GameFunctionFragment.onClick() {
                @Override
                public void click(String title, int index) {
                    if(conn==null){
                        return;
                    }
                    switch (index){
                        case 0://注销
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_I})), 200);
                            break;
                        case 1://关机
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_U})), 200);
                            break;
                        case 2://睡眠
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_S})), 200);
                            break;
                        case 3://重启
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_R})), 200);
                            break;
                        case 4://任务管理器
                            sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE});
                            break;
                        case 5://发送剪切板
                            if(game!=null){
                                game.sendClipboardText();
                            }
                            break;
                        case 6://打开剪切板
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_V});
                            break;
                        case 7://系统设置
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_I});
                            break;
                        case 8://我的电脑
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_E});
                            break;
                        case 9://移动中心
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            break;
                        case 10://Win+P
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_P});
                            break;
                    }
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        //断开链接
        if(v.getId()==R.id.btn_unlink){
            dismiss();
            if(game!=null){
                game.finish();
            }
            return;
        }
        if(v.getId()==R.id.btn_exit){
            dismiss();
            if(game!=null){
                game.isQuitSteamingFlag=true;
                game.disconnect();
            }
            return;
        }

        if(v.getId()==R.id.btn_swicth_screen){
            dismiss();
            if(game!=null){
                game.switchLandscapePortraitScreen();
            }
            return;
        }
        if(v.getId()==R.id.btn_game_pad){
            if(game!=null){
                game.showHideVirtualController();
                btn_game_pad.setBackgroundResource(game.prefConfig.onscreenController?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            }
            return;
        }

        if(v.getId()==R.id.btn_performance){
            if(game!=null){
                game.showHUD();
                btn_performance.setBackgroundResource(game.prefConfig.enablePerfOverlay?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            }
            return;
        }

        if(v.getId()==R.id.btn_v_keyboard){
            if(game!=null){
                game.showHideKeyboardController();
                btn_v_keyboard.setBackgroundResource(game.prefConfig.enableKeyboard?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            }
            return;
        }

        if(v.getId()==R.id.btn_keyboard){
            if(game!=null){
                game.showHidekeyBoardLayoutController();
            }
            return;
        }

        if(v.getId()==R.id.btn_soft_keyboard){
            dismiss();
            if(game!=null){
                toggleKeyboard();
            }
            return;
        }

        if(v.getId()==R.id.btn_screen_move){
            dismiss();
            if(game!=null){
                game.screenMoveZoom();
            }
            return;
        }

        if(v.getId()==R.id.btn_desktop){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D});
            }
            return;
        }

        if(v.getId()==R.id.btn_window){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_TAB});
            }
            return;
        }

        if(v.getId()==R.id.btn_hdr){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B});
            }
            return;
        }

        if(v.getId()==R.id.btn_display_1){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1});
            }
            return;
        }

        if(v.getId()==R.id.btn_display_2){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12});
            }
            return;
        }

        if(v.getId()==R.id.btn_gamepad_mouse){
            if (device != null) {
                List<GameMenu.MenuOption> menuOptions=device.getGameMenuOptions();
                if(menuOptions!=null&& !menuOptions.isEmpty()){
                    menuOptions.get(0).runnable.run();
                }
            }
            return;
        }


        if(v.getId()==R.id.bt_quick_list){
            GameListQuickFragment fragment=new GameListQuickFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("快捷键(字体加粗项可长按删除)");
            if(game!=null){
                fragment.setEnableClearDefaultSpecial(game.prefConfig.enableClearDefaultSpecial);
            }
            fragment.setOnClick(new GameListQuickFragment.onClick() {
                @Override
                public void click(GameMenuQuickBean bean) {
                    if(bean==null){
                        return;
                    }
                    if(!TextUtils.isEmpty(bean.getName())){
                        Toast.makeText(getActivity(),bean.getName(),Toast.LENGTH_SHORT).show();
                    }
                    //原来的快捷键列表逻辑
                    if(TextUtils.isEmpty(bean.getId())){
                        if(conn!=null){
                            sendKeys(bean.getDatas());
                        }
                        return;
                    }
                    sendQuickKeylist(bean.getCodes());

                }
            });
            fragment.show(getFragmentManager());
            return;
        }
        if(v.getId()==R.id.bt_touch_list){
            GameListMouseFragment fragment=new GameListMouseFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("鼠标与触控");
            fragment.setOnClick(new GameListMouseFragment.onClick() {
                @Override
                public void click(String title, int index) {
                    if(!TextUtils.isEmpty(title)&&index!=6){
                        Toast.makeText(getActivity(),title,Toast.LENGTH_SHORT).show();
                    }
                    if(game==null||index<0){
                        return;
                    }
                    if(index==5){
                        game.switchMouseLocalCursor();
                        return;
                    }
                    if(index==6){
                        game.prefConfig.absoluteMouseMode=!game.prefConfig.absoluteMouseMode;
                        Toast.makeText(getActivity(),"远程桌面鼠标模式"+(game.prefConfig.absoluteMouseMode?"已启用！":"已禁用！"),Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(index==7){
                        if(conn!=null){
                            sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_N});
                        }
                        return;
                    }
                    game.switchMouseModel(index);
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_touch_sensitivity){
            GameTouchFragment fragment=new GameTouchFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("触控灵敏度");
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display){
            GameDisplayFragment fragment=new GameDisplayFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("显示");
            fragment.setOnClick(new GameDisplayFragment.onClick() {
                @Override
                public void click() {
                    dismiss();
                    if(game!=null){
                        game.finish();
                    }
                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_device){
            GameDisplayDeviceFragment fragment=new GameDisplayDeviceFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("外设");
            fragment.setOnClick((index, flag) -> {
                if(index==1){
                    game.setDualSenseTrigger();
                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId() == R.id.bt_other_setting){
            GameDisplaySettingFragment fragment=new GameDisplaySettingFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("杂项");
            fragment.setOnClick(new GameDisplaySettingFragment.onClick() {
                @Override
                public void click(int index,boolean flag) {
                    if(game==null){
                        return;
                    }
                    //悬浮球
                    if(index==0){
                        if(flag){
                            game.showFloatView();
                            return;
                        }
                        game.hideFloatView();
                        return;
                    }
                    //显示震动信息
                    if(index==1){
                        game.switchPerformanceRumbleHUD();
                        return;
                    }
                    //性能信息点击
                    if(index==2){
                        game.switchPerformanceLiteHudclick();
                        return;
                    }
                    //性能信息缩放
                    if(index==3){
                        game.setPerformanceOverlayZoom();
                        return;
                    }


                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_virtual_view){
            GameMenuVirtualViewFragment fragment=new GameMenuVirtualViewFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("虚拟手柄与虚拟按键");
            fragment.setGamePadMode(game==null? VirtualController.ControllerMode.NONE:game.getVirtualControllerMode());
            fragment.setGameKeyMode(game==null? KeyBoardController.ControllerMode.NONE:game.getVirtualKeyControllerMode());
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.setOnClick(new GameMenuVirtualViewFragment.onClick() {
                @Override
                public void click(String name, int index) {
                    if(game==null){
                        return;
                    }
                    game.updateVirtualView();
                    Toast.makeText(getActivity(),"更新成功！",Toast.LENGTH_SHORT).show();
                }

                @Override
                public void switchModeGamePad(String name, VirtualController.ControllerMode mode) {
                    if(game==null){
                        return;
                    }
                    game.switchVirtualController(mode);
                }

                @Override
                public void switchModeGameKey(String name, KeyBoardController.ControllerMode mode) {
                    if(game==null){
                        return;
                    }
                    game.switchVirtualKeyController(mode);
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

    }

    private void toggleKeyboard(){
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> toggleKeyboard(),10);
            return;
        }
        game.toggleKeyboard();
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public void setConn(NvConnection conn) {
        this.conn = conn;
    }

    public void setDevice(GameInputDevice device) {
        this.device = device;
    }


    private static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN:
                return KeyboardPacket.MODIFIER_META;
            case KeyboardTranslator.VK_LMENU:
                return KeyboardPacket.MODIFIER_ALT;
            default:
                return 0;
        }
    }

    public void sendKeys(short[] keys) {
        sendKeys(conn,keys);
    }

    public static void sendKeys(NvConnection conn, short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private static final long KEY_UP_DELAY = 25;


    private int getPhoneBattery(Context context) {
        try{
            int level = 0;
            Intent batteryInfoIntent = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            level = batteryInfoIntent.getIntExtra("level", 0);
            int batterySum = batteryInfoIntent.getIntExtra("scale", 100);
            return 100 * level / batterySum;
        }catch (Exception e){
            e.printStackTrace();
        }
        return 100;
    }

    private void sendQuickKeylist(String codes){
        if(TextUtils.isEmpty(codes)){
            return;
        }
        String[] keys=codes.split(",");
        for (int i = 0; i < keys.length; i++) {
            KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN,Integer.parseInt(keys[i]));
            keyEvent.setSource(0);
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }
        new Handler().postDelayed((() -> {
            for (int i = keys.length - 1; i >= 0; i--) {
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP,Integer.parseInt(keys[i]));
                keyEvent.setSource(0);
                Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
            }
        }), KEY_UP_DELAY);
    }
}
