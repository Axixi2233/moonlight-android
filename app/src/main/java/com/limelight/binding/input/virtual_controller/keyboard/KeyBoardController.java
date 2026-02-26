/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.gamemenu.GameKeyboardUpdateFragment;
import com.limelight.ui.gamemenu.GamePadAddFragment;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE;

public class KeyBoardController {

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

    ControllerInputContext inputContext = new ControllerInputContext();

    private final Activity context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private View buttonConfigure = null;

    private Vibrator vibrator;
    private List<keyBoardVirtualControllerElement> elements = new ArrayList<>();

    private PreferenceConfiguration prefConfig;
    private boolean isShow=true;
    private ImageView iv_game_virtual_pad;
    private LinearLayout lv_right_view;
    private View lv_left_view = null;

    private TextView txName;
    private TextView txDesc;
    private TextView txZoom;
    private CheckBox cb_round;
    private SeekBar sb_zoom_x;

    private TextView tx_zoom_w;
    private TextView tx_zoom_h;
    private SeekBar sb_zoom_w;
    private SeekBar sb_zoom_h;
    private TextView tx_margin;

    private int currentIndex=-1;

    private int buttonWidth;
    private int buttonHeight;

    private String fileName="vk_1.txt";

    private boolean isGamePadMode;

    public KeyBoardController(final ControllerHandler controllerHandler, FrameLayout layout, final Activity context,PreferenceConfiguration prefConfig,boolean isGamePadMode) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.isGamePadMode=isGamePadMode;
        this.handler = new Handler(Looper.getMainLooper());
        this.prefConfig=prefConfig;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        buttonConfigure=View.inflate(context,R.layout.axi_keyboard_top_right_view,null);
        lv_left_view=View.inflate(context,R.layout.axi_keyboard_top_left_view,null);
        buttonWidth=UiHelper.dpToPx(context,50);
        buttonHeight=UiHelper.dpToPx(context,50);
        initTopView();
    }

    private void initTopView(){
        iv_game_virtual_pad= buttonConfigure.findViewById(R.id.iv_game_virtual_pad);
        lv_right_view=buttonConfigure.findViewById(R.id.lv_right_view);

        txName=lv_left_view.findViewById(R.id.tx_name);
        txDesc=lv_left_view.findViewById(R.id.tx_desc);
        txZoom=lv_left_view.findViewById(R.id.tx_zoom);
        cb_round=lv_left_view.findViewById(R.id.cb_round);
        sb_zoom_x=lv_left_view.findViewById(R.id.sb_zoom_x);
        sb_zoom_w=lv_left_view.findViewById(R.id.sb_zoom_w);
        sb_zoom_h=lv_left_view.findViewById(R.id.sb_zoom_h);
        tx_zoom_w=lv_left_view.findViewById(R.id.tx_zoom_w);
        tx_zoom_h=lv_left_view.findViewById(R.id.tx_zoom_h);
        tx_margin=lv_left_view.findViewById(R.id.tx_margin);

        iv_game_virtual_pad.setOnClickListener(v -> {
            if(lv_right_view.getVisibility()==View.GONE){
                iv_game_virtual_pad.setImageResource(R.drawable.ic_axi_game_pad_top_right);
                lv_right_view.setVisibility(View.VISIBLE);
            }else{
                iv_game_virtual_pad.setImageResource(R.drawable.ic_axi_game_pad_top_left);
                lv_right_view.setVisibility(View.GONE);
            }
        });
        buttonConfigure.findViewById(R.id.btn_game_virtual_add).setOnClickListener(v -> {
            if(isGamePadMode){
                GamePadAddFragment fragment=new GamePadAddFragment();
                if(isLandscape(context)){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowMetrics windowMetrics = context.getWindowManager().getCurrentWindowMetrics();
                        Rect bounds = windowMetrics.getBounds();
                        fragment.setWidth(bounds.width());
                    }else{
                        fragment.setWidth(context.getResources().getDisplayMetrics().widthPixels);
                    }
                }else{
                    fragment.setWidth((context.getResources().getDisplayMetrics().heightPixels*2)/3);
                }
                fragment.setTitle("手柄按键");
                fragment.setOnClick(bean -> {
                    LimeLog.info("axi->组合键:"+new Gson().toJson(bean));
                    addItem(bean);
                });
                fragment.show(context.getFragmentManager());
                return;
            }
            GameKeyboardUpdateFragment fragment=new GameKeyboardUpdateFragment();
            if(isLandscape(context)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowMetrics windowMetrics = context.getWindowManager().getCurrentWindowMetrics();
                    Rect bounds = windowMetrics.getBounds();
                    fragment.setWidth(bounds.width());
                }else{
                    fragment.setWidth(context.getResources().getDisplayMetrics().widthPixels);
                }
            }else{
                fragment.setWidth((context.getResources().getDisplayMetrics().heightPixels*2)/3);
            }
            fragment.setTitle("组合键");
            fragment.setOnClick(bean -> {
                LimeLog.info("axi->组合键:"+new Gson().toJson(bean));
                addItem(bean);
            });
            fragment.show(context.getFragmentManager());
        });

        buttonConfigure.findViewById(R.id.btn_game_virtual_save).setOnClickListener(v -> {
            save();
        });

        buttonConfigure.findViewById(R.id.btn_game_virtual_reset).setOnClickListener(v -> {
            refreshLayout();
            buttonConfigure.setVisibility(View.VISIBLE);
        });

        lv_left_view.findViewById(R.id.tx_cancel).setOnClickListener(v -> {
            View view=frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode));
            currentIndex=-1;
            if(view!=null){
                view.invalidate();
            }
            lv_left_view.setVisibility(View.GONE);
        });
        lv_left_view.findViewById(R.id.tx_del).setOnClickListener(v -> {
            beanList.remove(currentIndex);
            lv_left_view.setVisibility(View.GONE);
            updateItem();
        });
        cb_round.setOnCheckedChangeListener((buttonView, isChecked) -> {
            //方形按钮
            lv_left_view.findViewById(R.id.lv_zoom_wh).setVisibility(isChecked?View.VISIBLE:View.GONE);
            txZoom.setVisibility(isChecked?View.GONE:View.VISIBLE);
            sb_zoom_x.setVisibility(isChecked?View.GONE:View.VISIBLE);

            cb_round.setChecked(isChecked);
            beanList.get(currentIndex).setShapeType(isChecked?1:0);
            keyBoardVirtualControllerElement element=frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode));
            element.setShapeType(beanList.get(currentIndex).getShapeType());
            element.invalidate();
        });
        sb_zoom_x.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txZoom.setText("缩放比例："+progress+"%");
                beanList.get(currentIndex).setZoom(progress);
                switch (beanList.get(currentIndex).getBtnType()){
                    case 1://1鼠标 2触控板 3摇杆 4普通按钮 5十字键
                    case 4:
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*progress*0.01));
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*progress*0.01));
                        break;
                    case 2:
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*4*progress*0.01));
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*2*progress*0.01));
                        break;
                    case 3:
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*2*progress*0.01));
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*2*progress*0.01));
                        break;
                    case 5://十字键
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*2*progress*0.01));
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*2*progress*0.01));
                        break;
                }
                frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode)).getLayoutParams().width=beanList.get(currentIndex).getWidth();
                frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode)).getLayoutParams().height=beanList.get(currentIndex).getHeight();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        sb_zoom_w.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tx_zoom_w.setText("缩放宽度："+progress+"%");
                beanList.get(currentIndex).setZoomW(progress);
                switch (beanList.get(currentIndex).getBtnType()){
                    case 2:
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*4*progress*0.01));
                        break;
                    case 4:
                        beanList.get(currentIndex).setWidth((int) (buttonWidth*progress*0.01));
                        break;
                }
                frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode)).getLayoutParams().width=beanList.get(currentIndex).getWidth();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        sb_zoom_h.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tx_zoom_h.setText("缩放高度："+progress+"%");
                beanList.get(currentIndex).setZoomH(progress);
                switch (beanList.get(currentIndex).getBtnType()){
                    case 2:
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*2*progress*0.01));
                        break;
                    case 4:
                        beanList.get(currentIndex).setHeight((int) (buttonHeight*progress*0.01));
                        break;
                }
                frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode)).getLayoutParams().height=beanList.get(currentIndex).getHeight();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private List<GameMenuQuickBean> beanList=new ArrayList<>();


    private void addItem(GameMenuQuickBean bean){
        int w=context.getResources().getDisplayMetrics().widthPixels;
        int h=context.getResources().getDisplayMetrics().heightPixels;
        LimeLog.info("axi->宽度:"+w);
        LimeLog.info("axi->高度:"+h);
        bean.setmLeft(w/2);
        bean.setmTop(h/2);
        switch(bean.getBtnType()){
            case 1:
            case 4://鼠标&普通按钮
                bean.setWidth(buttonWidth);
                bean.setHeight(buttonHeight);
                break;
            case 2://触控板
                bean.setWidth(buttonWidth*4);
                bean.setHeight(buttonHeight*2);
                break;
            case 3://摇杆
                bean.setWidth(buttonWidth*2);
                bean.setHeight(buttonHeight*2);
                break;
            case 5://十字键
                bean.setWidth(buttonWidth*2);
                bean.setHeight(buttonHeight*2);
                break;
        }
        beanList.add(bean);
        updateItem();
    }


    private void updateItem(){
        removeElements();
        initView();
        buttonConfigure.setVisibility(View.VISIBLE);
        currentIndex=-1;
        for (int i = 0; i < beanList.size(); i++) {
            addView(beanList.get(i),i);
        }
    }

    private String tips;

    private void initData(){
        String res=FileUriUtils.getKeyBoardJson(context,fileName);
        if(!TextUtils.isEmpty(res)){
            LimeLog.info("axi->"+res);
            GameMenuQuickBean[] beans=new Gson().fromJson(res,GameMenuQuickBean[].class);
            Collections.addAll(beanList, beans);
        }
        LimeLog.info("axi->"+getControllerMode());
        if(getControllerMode()==ControllerMode.Active&& beanList.isEmpty()){
            if(TextUtils.isEmpty(tips)){
                tips="无按键可用，打开编辑模式新增按钮后使用！(菜单-虚拟手柄与按键-编辑模式)";
                Toast.makeText(context,tips,Toast.LENGTH_LONG).show();
            }
//            switchMode(ControllerMode.MoveButtons);
            return;
        }
        for (int i = 0; i < beanList.size(); i++) {
            GameMenuQuickBean bean=beanList.get(i);
            addView(bean,i);
        }
    }

    private void addView(GameMenuQuickBean bean,int i){
        LimeLog.info("axi->addView:"+i);
        keyBoardVirtualControllerElement element = null;
        //普通按钮
        if(bean.getBtnType()==4){
            //游戏按钮
            if(bean.isGamePad()){
                //扳机按钮
                if(bean.getCode()==ControllerPacket.PADDLE3_FLAG||bean.getCode()==ControllerPacket.PADDLE4_FLAG){
                    element=new TriggerGamePad(this,bean.getId(),bean.getName(),bean.getCode()==ControllerPacket.PADDLE3_FLAG,bean.isSwitchMode(),1,context);
                }else{
                    element=KeyBoardControllerConfigurationLoader.createDigitalButtonGamePad(bean.getId(),bean.getCode(),0,1,bean.getName(),-1,bean.isSwitchMode(),this,context);
                }
            }else{
                element=KeyBoardControllerConfigurationLoader.createDigitalButton(bean.getId(),bean.getCodes(),bean.getBtnType(),1,bean.getName(),-1,bean.isSwitchMode(),this,context);
            }
        }
        //鼠标
        if(bean.getBtnType()==1){
            element=KeyBoardControllerConfigurationLoader.createDigitalButton(bean.getId(),bean.getCode(),bean.getBtnType(),1,bean.getName(),-1,bean.isSwitchMode(),this,context);
        }
        //触控板
        if(bean.getBtnType()==2){
            element=KeyBoardControllerConfigurationLoader.createDigitalTouchButton(bean.getId(),bean.getCode(),1,1,bean.getName(),-1,this,context);
        }
        //摇杆
        if(bean.getBtnType()==3){
            if(bean.isGamePad()){
                if(bean.isFreeStick()){
                    element=new AnalogStickFreeGamePad(this,bean.getId(),context,bean.getCode()==ControllerPacket.PADDLE5_FLAG,bean.isFixedStrokeFreeStick(),bean.isFreeeStickDrawNormal());
                }else{
                    element=new AnalogStickGamePad(this,bean.getId(),context,bean.getCode()==ControllerPacket.PADDLE5_FLAG,bean.isFixedStrokeFreeStick());
                }
            }else{
                String[] tips=bean.getDesc().split("-");
                String[] keys=bean.getCodes().split(",");
                int[] intArray = new int[keys.length];
                for (int j = 0; j < keys.length; j++) {
                    intArray[j] = Integer.parseInt(keys[j]); // 自动拓宽转换 (Widening Primitive Conversion)
                }
                if(bean.isFreeStick()){
                    element=KeyBoardControllerConfigurationLoader.createKeyBoardAnalogStickButton2(this,bean.getId(),context,intArray,tips);
                }else{
                    element=KeyBoardControllerConfigurationLoader.createKeyBoardAnalogStickButton(this,bean.getId(),context,intArray,tips);
                }
            }
        }
        //十字键
        if(bean.getBtnType()==5){
            if(bean.isGamePad()){
                String[] tips=bean.getDesc().split("-");
                element=KeyBoardControllerConfigurationLoader.createDiaitalPadButtonGamePad(bean.getId(),bean.getCode()==ControllerPacket.PADDLE2_FLAG , tips,this, context);
            }else{
                String[] keys=bean.getCodes().split(",");
                String[] tips=bean.getDesc().split("-");
                element=KeyBoardControllerConfigurationLoader.createDiaitalPadButton(bean.getId(),
                        Integer.parseInt(keys[2]), Integer.parseInt(keys[3]), Integer.parseInt(keys[0]), Integer.parseInt(keys[1]),
                        tips,this, context);
            }
        }

        if(element!=null){
            element.setShapeType(bean.getShapeType());
            element.setTag(new TagInfo(i,isGamePadMode));
            element.setOnClick(tag -> {
//                LimeLog.info("axi->当前："+new Gson().toJson(beanList.get(tag)));
                updateItem(tag.index);
            });
            element.setOpacity(PreferenceConfiguration.readPreferences(context).oscOpacity);
            addElement(element,bean.getmLeft(),bean.getmTop(),bean.getWidth(),bean.getHeight());
        }
    }


    private void updateItem(int index){
        if(beanList.size()<index){
            return;
        }
        lv_left_view.setVisibility(View.VISIBLE);
        View lastView=null;
        if(currentIndex!=-1){
            lastView=frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode));
        }
        currentIndex=index;
        if(lastView!=null){
            lastView.invalidate();
        }
        txName.setText("当前按钮："+beanList.get(index).getName());
        txDesc.setText("键值："+beanList.get(index).getDesc());
        tx_margin.setText("坐标："+beanList.get(index).getmLeft()+"，"+beanList.get(index).getmTop());

        if(beanList.get(index).getBtnType()==4||beanList.get(index).getBtnType()==2){
            cb_round.setChecked(beanList.get(index).getShapeType()==1);
            cb_round.setVisibility(beanList.get(index).getBtnType()==4?View.VISIBLE:View.GONE);

            lv_left_view.findViewById(R.id.lv_zoom_wh).setVisibility(beanList.get(index).getShapeType()==1?View.VISIBLE:View.GONE);
            txZoom.setVisibility(beanList.get(index).getShapeType()==1?View.GONE:View.VISIBLE);
            sb_zoom_x.setVisibility(beanList.get(index).getShapeType()==1?View.GONE:View.VISIBLE);

            tx_zoom_w.setText("缩放宽度："+beanList.get(index).getZoomW()+"%");
            tx_zoom_h.setText("缩放高度："+beanList.get(index).getZoomH()+"%");
            sb_zoom_w.setProgress(beanList.get(index).getZoomW());
            sb_zoom_h.setProgress(beanList.get(index).getZoomH());
        }else{
            cb_round.setVisibility(View.GONE);
            lv_left_view.findViewById(R.id.lv_zoom_wh).setVisibility(View.GONE);
            txZoom.setVisibility(View.VISIBLE);
            sb_zoom_x.setVisibility(View.VISIBLE);
            txZoom.setText("缩放比例："+beanList.get(index).getZoom()+"%");
            sb_zoom_x.setProgress(beanList.get(index).getZoom());
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frame_layout.findViewWithTag(new TagInfo(currentIndex,isGamePadMode)).getLayoutParams();
        beanList.get(currentIndex).setmLeft(layoutParams.leftMargin);
        beanList.get(currentIndex).setmTop(layoutParams.topMargin);
    }

    private void save(){
        LimeLog.info("axi->保存："+fileName);
        FileUriUtils.saveKeyBoardJson(context,fileName,new Gson().toJson(beanList));
        this.currentMode=ControllerMode.Active;
        buttonConfigure.setVisibility(View.GONE);
        lv_left_view.setVisibility(View.GONE);
        currentIndex=-1;
        for (keyBoardVirtualControllerElement element : elements) {
            element.invalidate();
        }
        Toast.makeText(context,"已保存！",Toast.LENGTH_SHORT).show();
    }


    public void switchMode(ControllerMode currentMode){
        this.currentMode=currentMode;
        String message="";
        switch (currentMode){
            case Active:
                message="正常模式~";
                buttonConfigure.setVisibility(View.GONE);
                lv_left_view.setVisibility(View.GONE);
                break;
            case MoveButtons:
                message="位移模式~";
                buttonConfigure.setVisibility(View.VISIBLE);
                break;
        }
        if(TextUtils.isEmpty(message)){
            return;
        }
        for (keyBoardVirtualControllerElement element : elements) {
            element.invalidate();
        }

    }

    Handler getHandler() {
        return handler;
    }
    
    public TagInfo getCurrentIndex() {
        return new TagInfo(currentIndex,isGamePadMode);
    }

    public void hide() {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }
        isShow=false;
        lv_left_view.setVisibility(View.GONE);
        buttonConfigure.setVisibility(View.GONE);
        this.currentMode = ControllerMode.NONE;
    }

    public void show() {
//        showEnabledElements();
        isShow=true;
        this.currentMode = ControllerMode.Active;
        refreshLayout();
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
        frame_layout.removeView(lv_left_view);
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
        LimeLog.info("axi->addElement:"+width+","+height+",x:"+x+",y:"+y);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void initView(){
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity= Gravity.RIGHT;
        frame_layout.addView(buttonConfigure, params);
        FrameLayout.LayoutParams params1 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params1.gravity=Gravity.LEFT;
        frame_layout.addView(lv_left_view, params1);
        buttonConfigure.setVisibility(View.GONE);
        lv_left_view.setVisibility(View.GONE);
    }


    public void refreshLayout() {
        if(this.currentMode==ControllerMode.NONE){
            return;
        }
        removeElements();
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);
        if(isGamePadMode){
            name="gamePad";
        }
        if(!isLandscape(context)){
            name+="_1";
        }
        fileName="axi_"+name+".txt";
        LimeLog.info("axi->refreshLayout："+fileName);
        initView();
        currentIndex=-1;
        beanList.clear();
        initData();
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
        if (prefConfig.enableKeyboardVibrate && vibrator.hasVibrator()&&keyEvent.getSource()!=2) {
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

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        LimeLog.info("axi->gamepad:"+inputContext.inputMap);
        if (controllerHandler != null) {
            LimeLog.info("axi->gamepad:end");

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


    public boolean isLandscape(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels>context.getResources().getDisplayMetrics().heightPixels;
    }

}
