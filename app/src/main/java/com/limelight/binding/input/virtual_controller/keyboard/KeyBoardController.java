/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyBoardController {

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons,
        NONE
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();

    private View buttonConfigure = null;

    private Vibrator vibrator;
    private List<keyBoardVirtualControllerElement> elements = new ArrayList<>();

    private PreferenceConfiguration prefConfig;
    private boolean isShow=true;
    private ImageView iv_game_virtual_pad;
    private RadioGroup rg_game_virtual_pad;

    public KeyBoardController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context,PreferenceConfiguration prefConfig) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.prefConfig=prefConfig;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

//        buttonConfigure = new Button(context);
//        buttonConfigure.setAlpha(0.25f);
//        buttonConfigure.setFocusable(false);
//        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
//        buttonConfigure.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (currentMode == KeyBoardController.ControllerMode.Active) {
//                    switchMode(KeyBoardController.ControllerMode.DisableEnableButtons);
//                } else if (currentMode == KeyBoardController.ControllerMode.DisableEnableButtons){
//                    switchMode(KeyBoardController.ControllerMode.MoveButtons);
//                } else if (currentMode == KeyBoardController.ControllerMode.MoveButtons) {
//                    switchMode(KeyBoardController.ControllerMode.ResizeButtons);
//                } else {
//                    switchMode(KeyBoardController.ControllerMode.Active);
//                }
//            }
//        });
        buttonConfigure=View.inflate(context,R.layout.ax_gamepad_top_view,null);
        initTopView();
    }


    private void initTopView(){
        iv_game_virtual_pad= buttonConfigure.findViewById(R.id.iv_game_virtual_pad);
        rg_game_virtual_pad= buttonConfigure.findViewById(R.id.rg_game_virtual_pad);
        rg_game_virtual_pad.setOnCheckedChangeListener((group1, checkedId) -> {
            if(checkedId==R.id.btn_game_virtual_move){
                switchMode(KeyBoardController.ControllerMode.MoveButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_zoom){
                switchMode(KeyBoardController.ControllerMode.ResizeButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_disable){
                switchMode(KeyBoardController.ControllerMode.DisableEnableButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_nomall){
                switchMode(KeyBoardController.ControllerMode.Active);
                return;
            }
        });
        iv_game_virtual_pad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(rg_game_virtual_pad.getVisibility()==View.GONE){
                    iv_game_virtual_pad.setImageResource(R.drawable.ic_axi_game_pad_top_left);
                    rg_game_virtual_pad.setVisibility(View.VISIBLE);
                }else{
                    iv_game_virtual_pad.setImageResource(R.drawable.ic_axi_game_pad_top_right);
                    rg_game_virtual_pad.setVisibility(View.GONE);
                }

            }
        });
    }


    public void switchMode(ControllerMode currentMode){
        this.currentMode=currentMode;
        String message="";
        switch (currentMode){
            case Active:
                message="正常模式~";
                buttonConfigure.setVisibility(View.GONE);
                KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                break;
            case MoveButtons:
                message="位移模式~";
                buttonConfigure.setVisibility(View.VISIBLE);
                rg_game_virtual_pad.check(R.id.btn_game_virtual_move);
                showEnabledElements();
                break;
            case ResizeButtons:
                buttonConfigure.setVisibility(View.VISIBLE);
                rg_game_virtual_pad.check(R.id.btn_game_virtual_zoom);
                message="缩放模式~";
                break;
            case DisableEnableButtons:
                buttonConfigure.setVisibility(View.VISIBLE);
                rg_game_virtual_pad.check(R.id.btn_game_virtual_disable);
                message="禁用模式~";
                showElements();
                break;
        }
        if(TextUtils.isEmpty(message)){
            return;
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        buttonConfigure.invalidate();
        for (keyBoardVirtualControllerElement element : elements) {
            element.invalidate();
        }

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }
        isShow=false;

        buttonConfigure.setVisibility(View.GONE);
    }

    public void show() {
        showEnabledElements();
        isShow=true;
        this.currentMode = ControllerMode.Active;
//        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public void showElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(element.enabled ? View.VISIBLE : View.GONE);
        }
    }

    public int switchShowHide() {
        if (isShow) {
            hide();
            return 0;
        } else {
            show();
            return 1;
        }
    }

    public void removeElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(keyBoardVirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frame_layout.addView(buttonConfigure, params);
        buttonConfigure.setVisibility(View.GONE);

        // Start with the default layout
        KeyBoardControllerConfigurationLoader.createDefaultLayout(this, context,prefConfig);

        // Apply user preferences onto the default layout
        KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        //1-鼠标 0-按键 2-摇杆 3-十字键
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(keyEvent.getKeyCode(), KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }
        if (prefConfig.enableKeyboardVibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(10);
        }
    }

    public void sendMouseMove(int x,int y){
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseMove(x,y);
    }

    public void sendAssembleKey(String codes,int action){
        if (prefConfig.enableKeyboardVibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(10);
        }
        String[] keys=codes.split(",");
        //阿西西快捷键
        if(codes.startsWith("29,52,37,52")&&keys.length==5){
            if(action==KeyEvent.ACTION_DOWN){
                return;
            }
            int value= Integer.parseInt(keys[4]);
            switch (value){
                case 7://0 软键盘
                    if (!Game.instance.hasWindowFocus()) {
                        new Handler().postDelayed(() -> Game.instance.toggleKeyboard(),10);
                        return;
                    }
                    Game.instance.toggleKeyboard();
                    break;
                case 8://1 虚拟按键
                    Game.instance.showHideKeyboardController();
                    break;
                case 9://2 全键盘
                    Game.instance.showHidekeyBoardLayoutController();
                    break;
                case 10://3 虚拟手柄
                    Game.instance.showHideVirtualController();
                    break;
                case 11://4 悬浮球
                    Game.instance.switchFloatView();
                    break;
                case 12://5 性能信息
                    Game.instance.showHUD();
                    break;
                case 13://6 快捷菜单
                    Game.instance.showGameMenu(null);
                    break;
            }
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            KeyEvent keyEvent = new KeyEvent(action,Integer.parseInt(keys[i]));
            keyEvent.setSource(0);
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }
    }

}
