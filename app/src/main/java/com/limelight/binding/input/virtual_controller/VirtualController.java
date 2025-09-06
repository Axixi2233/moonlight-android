/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
//        public short inputMap = 0x0000;
        public int inputMap = 0;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

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

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private View buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    private Vibrator vibrator;

    private PreferenceConfiguration prefConfig;

    private boolean isShow=true;

    private ImageView iv_game_virtual_pad;
    private RadioGroup rg_game_virtual_pad;

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context,PreferenceConfiguration prefConfig) {
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
//                if (currentMode == ControllerMode.Active) {
//                    switchMode(ControllerMode.DisableEnableButtons);
//                } else if (currentMode == ControllerMode.DisableEnableButtons){
//                    switchMode(ControllerMode.MoveButtons);
//                } else if (currentMode == ControllerMode.MoveButtons) {
//                    switchMode(ControllerMode.ResizeButtons);
//                } else {
//                    switchMode(ControllerMode.Active);
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
                switchMode(ControllerMode.MoveButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_zoom){
                switchMode(ControllerMode.ResizeButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_disable){
                switchMode(ControllerMode.DisableEnableButtons);
                return;
            }
            if(checkedId==R.id.btn_game_virtual_nomall){
                switchMode(ControllerMode.Active);
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
                VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
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
        for (VirtualControllerElement element : elements) {
            element.invalidate();
        }

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (VirtualControllerElement element : elements) {
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

    public int switchShowHide() {
        if (isShow) {
            hide();
            return 0;
        } else {
            show();
            return 1;
        }
    }

    public void showElements(){
        for(VirtualControllerElement element : elements){
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements(){
        for(VirtualControllerElement element: elements){
            element.setVisibility( element.enabled ? View.VISIBLE : View.GONE );
        }
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

//        DisplayMetrics screen = context.getResources().getDisplayMetrics();
//        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        params.leftMargin = 15;
//        params.topMargin = 15;
//        params.gravity= Gravity.CENTER_HORIZONTAL;
        frame_layout.addView(buttonConfigure, params);
        buttonConfigure.setVisibility(View.GONE);
        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context,prefConfig);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    public void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();
        if (prefConfig.enableKeyboardVibrate && vibrator.hasVibrator()) {
            //摇杆不震动
            if(inputContext.inputMap!=0||inputContext.leftTrigger!=0x00||inputContext.rightTrigger!=0x00) {
                vibrator.vibrate(10);
            }
        }
        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
