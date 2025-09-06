/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.gamemenu.TouchPadView;

import java.util.ArrayList;
import java.util.List;

public class KeyBoardLayoutController {

    private final ControllerHandler controllerHandler;
    private final Context context;
    private FrameLayout frame_layout = null;
    private Vibrator vibrator;
    private LinearLayout keyboardView;
    private RadioGroup rg_keyboard;
    private PreferenceConfiguration prefConfig;

    public KeyBoardLayoutController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context,PreferenceConfiguration prefConfig) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.prefConfig=prefConfig;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.keyboardView= (LinearLayout) LayoutInflater.from(context).inflate(R.layout.layout_axixi_keyboard,null);
        initKeyboard();
    }

    private List<String> keyList=new ArrayList<>();

    //组合键模式
    private boolean isCombination=false;

    private void initKeyboard(){
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 处理按下事件
                        String tag=(String) v.getTag();
                        if(TextUtils.equals("hide",tag)){
                            return true;
                        }
                        if(!TextUtils.isEmpty(tag)&&tag.startsWith("mouse_")){
                            int code=1;
                            switch (tag){
                                case "mouse_1":
                                    code=1;
                                    break;
                                case "mouse_2":
                                    code=2;
                                    break;
                                case "mouse_3":
                                    code=3;
                                    break;
                            }
                            KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, code);
                            keyEvent.setSource(1);
                            sendKeyEvent(keyEvent);
                            v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
                            return true;
                        }

                        if(isCombination){
                            switch (tag){
                                case "113"://ctrl
                                case "117"://win
                                case "57"://alt
                                case "59"://shift
                                case "60"://shift right
                                    if(!keyList.contains(tag)){
                                        keyList.add(tag);
                                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_press);
                                    }else{
                                        keyList.remove(tag);
                                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                                    }
                                    return true;
                            }
                            if(!keyList.isEmpty()){
                                for (String t:keyList) {
                                    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, Integer.parseInt(t));
                                    keyEvent.setSource(0);
                                    sendKeyEvent(keyEvent);
                                }
                            }
                        }
                        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, Integer.parseInt(tag));
                        keyEvent.setSource(0);
                        sendKeyEvent(keyEvent);
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 处理释放事件
                        String tag2=(String) v.getTag();
                        if(TextUtils.equals("hide",tag2)){
//                            hide();
                            return true;
                        }
                        if(!TextUtils.isEmpty(tag2)&&tag2.startsWith("mouse_")){
                            int code=1;
                            switch (tag2){
                                case "mouse_1":
                                    code=1;
                                    break;
                                case "mouse_2":
                                    code=2;
                                    break;
                                case "mouse_3":
                                    code=3;
                                    break;
                            }
                            KeyEvent keyUP = new KeyEvent(KeyEvent.ACTION_UP, code);
                            keyUP.setSource(1);
                            sendKeyEvent(keyUP);
                            v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                            return true;
                        }
                        if(isCombination){
                            switch (tag2) {
                                case "113"://ctrl
                                case "117"://win
                                case "57"://alt
                                case "59"://shift left
                                case "60"://shift right
                                    return true;
                            }
                            if(!keyList.isEmpty()){
                                for (String t:keyList) {
                                    KeyEvent keyEvent2 = new KeyEvent(KeyEvent.ACTION_UP, Integer.parseInt(t));
                                    keyEvent2.setSource(0);
                                    sendKeyEvent(keyEvent2);
                                    keyboardView.findViewById(R.id.lv_keyboard).findViewWithTag(t).setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                                    keyboardView.findViewById(R.id.lv_keyboard_digitpad).findViewWithTag(t).setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                                }
                                keyList.clear();
                            }
                        }
                        KeyEvent keyUP = new KeyEvent(KeyEvent.ACTION_UP, Integer.parseInt(tag2));
                        keyUP.setSource(0);
                        sendKeyEvent(keyUP);
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                        return true;
                }
                return false;
            }
        };
        LinearLayout layout=keyboardView.findViewById(R.id.lv_keyboard);
        for (int i = 0; i < layout.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) layout.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
            }
        }

        LinearLayout lv_digitpad=keyboardView.findViewById(R.id.lv_digitpad);
        for (int i = 0; i < lv_digitpad.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) lv_digitpad.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
            }
        }
        LinearLayout lv_digitpad_mouse=keyboardView.findViewById(R.id.lv_digitpad_mouse);
        for (int i = 0; i < lv_digitpad_mouse.getChildCount(); i++) {
            lv_digitpad_mouse.getChildAt(i).setOnTouchListener(touchListener);
        }
        LinearLayout lv_digitpad_mouse_2=keyboardView.findViewById(R.id.lv_digitpad_mouse_2);
        for (int i = 0; i < lv_digitpad_mouse_2.getChildCount(); i++) {
            lv_digitpad_mouse_2.getChildAt(i).setOnTouchListener(touchListener);
        }

        LinearLayout lv_digitpad_function=keyboardView.findViewById(R.id.lv_digitpad_function);
        for (int i = 0; i < lv_digitpad_function.getChildCount(); i++) {
            lv_digitpad_function.getChildAt(i).setOnTouchListener(touchListener);
        }

        CheckBox btn_combination=keyboardView.findViewById(R.id.btn_combination);
        btn_combination.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isCombination=isChecked;
                LimeLog.info("axi-2-"+isCombination);
                resetView();
            }
        });

        keyboardView.findViewById(R.id.iv_down).setOnClickListener(v -> hide());

        keyboardView.findViewById(R.id.iv_game_menu).setOnClickListener(v -> Game.instance.showGameMenu(null));

        rg_keyboard=keyboardView.findViewById(R.id.rg_keyboard);
        rg_keyboard.check(R.id.rbt_keyboard_1);

        rg_keyboard.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(R.id.rbt_keyboard_1==checkedId){
                    resetView();
                    keyboardView.findViewById(R.id.lv_keyboard).setVisibility(View.VISIBLE);
                    keyboardView.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.GONE);
                    return;
                }
                if(R.id.rbt_keyboard_2==checkedId){
                    resetView();
                    keyboardView.findViewById(R.id.lv_keyboard).setVisibility(View.GONE);
                    keyboardView.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.VISIBLE);
                    return;
                }
            }
        });

        TouchPadView touchPadView=keyboardView.findViewById(R.id.fv_keyboard_touch);

        touchPadView.setViewLister(new TouchPadView.TouchPadViewLister() {
            @Override
            public void sendMouseMove(float dx, float dy) {
                Game.instance.mouseMove((int) dx, (int) dy);
            }

            @Override
            public void sendMouseLeft(boolean down) {
//                Game.instance.mouseButtonEvent(1,down);
            }

            @Override
            public void sendMouseRight(boolean down) {
//                Game.instance.mouseButtonEvent(3,down);
            }

            @Override
            public void sendMouseScroll(float distance) {
//                Game.instance.mouseHScroll((byte) distance);
            }
        });

        keyboardView.findViewById(R.id.btn_soft_phone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Game.instance.hasWindowFocus()) {
                    new Handler().postDelayed(() -> Game.instance.toggleKeyboard(),10);
                    return;
                }
                Game.instance.toggleKeyboard();
            }
        });

        keyboardView.findViewById(R.id.mouse_up).setOnTouchListener(new View.OnTouchListener() {
            private Runnable repeater = new Runnable() {
                @Override
                public void run() {
                    Game.instance.mouseHighResScroll(true);
                    keyboardView.findViewById(R.id.mouse_up).postDelayed(this, 100);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        keyboardView.findViewById(R.id.mouse_up).post(repeater);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        keyboardView.findViewById(R.id.mouse_up).removeCallbacks(repeater);
                        return true;
                }
                return false;
            }
        });

        keyboardView.findViewById(R.id.mouse_down).setOnTouchListener(new View.OnTouchListener() {
            private Runnable repeater = new Runnable() {
                @Override
                public void run() {
                    Game.instance.mouseHighResScroll(false);
                    keyboardView.findViewById(R.id.mouse_down).postDelayed(this, 100);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        keyboardView.findViewById(R.id.mouse_down).post(repeater);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        keyboardView.findViewById(R.id.mouse_down).removeCallbacks(repeater);
                        return true;
                }
                return false;
            }
        });

    }

    private void resetView(){
        keyList.clear();
//        isCombination=false;
        String[] keys={"113","117","57","59","60"};
        for (String key:keys) {
            if(keyboardView.findViewById(R.id.lv_keyboard).findViewWithTag(key)!=null){
                keyboardView.findViewById(R.id.lv_keyboard).findViewWithTag(key).setBackgroundResource(R.drawable.bg_ax_keyboard_button);
            }
            if(keyboardView.findViewById(R.id.lv_keyboard_digitpad).findViewWithTag(key)!=null){
                keyboardView.findViewById(R.id.lv_keyboard_digitpad).findViewWithTag(key).setBackgroundResource(R.drawable.bg_ax_keyboard_button);
            }
        }
    }

    public void hide() {
        keyboardView.setVisibility(View.GONE);
    }

    public void show() {
        keyboardView.setVisibility(View.VISIBLE);
    }

    public void switchShowHide() {
        if (keyboardView.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
        resetView();
    }

    public void refreshLayout() {
        frame_layout.removeView(keyboardView);
//        DisplayMetrics screen = context.getResources().getDisplayMetrics();
//        (int)(screen.heightPixels/0.4)
        int height=prefConfig.oscKeyboardHeight;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity= Gravity.BOTTOM;
//        params.leftMargin = 20 + buttonSize;
//        params.topMargin = 15;

        LinearLayout.LayoutParams params1=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dip2px(context,height));
        keyboardView.findViewById(R.id.lv_keyboard).setLayoutParams(params1);
        keyboardView.findViewById(R.id.lv_keyboard_digitpad).setLayoutParams(params1);


        keyboardView.setAlpha(prefConfig.oscKeyboardOpacity/100f);
        frame_layout.addView(keyboardView,params);

    }

    public int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
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
//        if (prefConfig.enableKeyboardVibrate && vibrator.hasVibrator()) {
//            vibrator.vibrate(10);
//        }
    }
}
