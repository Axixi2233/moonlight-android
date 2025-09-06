/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.BuildConfig;
import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.FileUriUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import static com.limelight.ui.gamemenu.GameListKeyBoardFragment.PREF_KEYBOARD_LIST_NAME;

public class KeyBoardControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "keyboard_axi_list";
    public static final String OSC_PREFERENCE_VALUE = "OSC_Keyboard";

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static int screenScaleSwicth(int result, int height) {
        return result * 72 / height;
    }

    private static KeyboardDigitalPadButton createDiaitalPadButton(String elementId, int keyCodeLeft, int keyCodeRight, int keyCodeUp, int keyCodeDown, final KeyBoardController controller, final Context context) {
        KeyboardDigitalPadButton button = new KeyboardDigitalPadButton(controller, context, elementId);
        button.addDigitalPadListener(new KeyboardDigitalPadButton.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
            }
        });
        return button;
    }


    private static KeyBoardAnalogStickButton createKeyBoardAnalogStickButton(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButton analogStick = new KeyBoardAnalogStickButton(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButton.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }

    private static KeyBoardAnalogStickButtonFree createKeyBoardAnalogStickButton2(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButtonFree analogStick = new KeyBoardAnalogStickButtonFree(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButtonFree.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }


    private static KeyBoardDigitalButton createDigitalButton(
            final String elementId,
            final Object keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        if(type==1){
            switch ((Integer) keyShort){
                case 1://左
                    button.setIcon(R.drawable.ic_axi_mouse_left);
                    button.setIconPress(R.drawable.ic_axi_mouse_left_s);
                    break;
                case 3://右
                    button.setIcon(R.drawable.ic_axi_mouse_right);
                    if(elementId.startsWith("m_s_")){
                        button.setIcon(R.drawable.ic_axi_mouse_right_2);
                    }
                    button.setIconPress(R.drawable.ic_axi_mouse_right_s);
                    break;
                case 2://中
                    button.setIcon(R.drawable.ic_axi_mouse_middle);
                    button.setIconPress(R.drawable.ic_axi_mouse_middle_s);
                    break;
                case 4:
                case 5://滚轮上下
                    button.setPadding(20,20,20,20);
                    button.setIcon((Integer) keyShort==4?R.drawable.ic_axi_mouse_up:R.drawable.ic_axi_mouse_down);
                    button.setIconPress((Integer) keyShort==4?R.drawable.ic_axi_mouse_up:R.drawable.ic_axi_mouse_down);
                    break;
            }
        }
        if(elementId.startsWith("m_s_")||elementId.startsWith("key_s_")){
            button.setEnableSwitchDown(true);
        }
        Runnable repeater = new Runnable() {
            @Override
            public void run() {
                if ((Integer) keyShort == 4) {
                    Game.instance.mouseHighResScroll(true);
                } else {
                    Game.instance.mouseHighResScroll(false);
                }
                button.postDelayed(this, 100);
            }
        };
        button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                if(type==1){
                    switch ((Integer)keyShort){
                        case 4:
                        case 5:
                            button.post(repeater);
                            return;
                    }
                }
                if(type==4){
                    controller.sendAssembleKey((String) keyShort,KeyEvent.ACTION_DOWN);
                    return;
                }
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, (Integer) keyShort);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }

            @Override
            public void onLongClick() {

            }

            @Override
            public void onRelease() {
                if(type==1){
                    switch ((Integer)keyShort){
                        case 4:
                        case 5:
                            button.removeCallbacks(repeater);
                            return;
                    }
                }
                if(type==4){
                    controller.sendAssembleKey((String) keyShort,KeyEvent.ACTION_UP);
                    return;
                }
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, (Integer) keyShort);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);

            }
        });

        return button;
    }


    private static KeyBoardTouchPadButton createDigitalTouchButton(
            final String elementId,
            final int keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardTouchPadButton button = new KeyBoardTouchPadButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.addDigitalButtonListener(new KeyBoardTouchPadButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                if(keyShort==13){
                    return;
                }
                int code=1;
                switch (keyShort){
                    case 9:
                        code=3;
                        break;
                    case 12:
                        code=2;
                        break;
                }
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onMove(int x, int y) {
                controller.sendMouseMove(x,y);
            }

            @Override
            public void onRelease() {
                if(keyShort==13){
                    return;
                }
                int code=1;
                switch (keyShort){
                    case 9:
                        code=3;
                        break;
                    case 12:
                        code=2;
                        break;
                }
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);

            }
        });

        return button;
    }

    public static void createDefaultLayout(final KeyBoardController controller, final Context context,PreferenceConfiguration config) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int height = screen.heightPixels;

        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int BUTTON_SIZE = 10;

        int w = screenScale(BUTTON_SIZE, height);

        int maxW = screen.widthPixels / 18;

        if (w > maxW) {
            BUTTON_SIZE = screenScaleSwicth(maxW, height);
            w = screenScale(BUTTON_SIZE, height);
        }

        String result = "";

        //启用自定义配置文件
        if(config.enableCustomKeyboardFile){
            String displayName = "axi_keyboard.json";
            File dataBaseFile=new File(context.getFilesDir().getAbsolutePath(), displayName);
            String authority= BuildConfig.APPLICATION_ID+".fileprovider";
            Uri uri= FileProvider.getUriForFile(context,authority,dataBaseFile);
            result=FileUriUtils.openUriForRead(context,uri);
        }
        if(TextUtils.isEmpty(result)){
            try {
                String fileName="config/keyboard.json";
                switch (config.virtualKeyboardFileUsed){
                    case 1:
                        fileName="config/keyboard_1.json";
                        break;
                    case 2:
                        fileName="config/keyboard_2.json";
                        break;
                }
                InputStream is = context.getAssets().open(fileName);
                int lenght = is.available();
                byte[] buffer = new byte[lenght];
                is.read(buffer);
                result = new String(buffer, "utf8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (TextUtils.isEmpty(result)) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject jsonObject1 = jsonObject.getJSONObject("data");

            JSONArray keystrokeList = jsonObject1.optJSONArray("keystroke");
            JSONArray dpadList = jsonObject1.optJSONArray("dpad");
            JSONArray rockerList = jsonObject1.optJSONArray("rocker");
            JSONArray mouseList = jsonObject1.optJSONArray("mouse");

            //十字键
            if(dpadList!=null&&dpadList.length()>0){
                for (int i = 0; i < dpadList.length(); i++) {
                    JSONObject obj = dpadList.getJSONObject(i);
                    String code = obj.optString("elementId");
                    int keyCodeLeft = obj.optInt("leftCode");
                    int keyCodeRight = obj.optInt("rightCode");
                    int keyCodeUp = obj.optInt("upCode");
                    int keyCodeDown = obj.optInt("downCode");
                    controller.addElement(createDiaitalPadButton(code, keyCodeLeft, keyCodeRight, keyCodeUp, keyCodeDown, controller, context),
                            screenScale(92, height) + rightDisplacement,
                            screenScale(41, height),
                            (int) (w * 2.5), (int) (w * 2.5)
                    );
                }
            }

            //摇杆
            if(rockerList!=null&&rockerList.length()>0){
                for (int i = 0; i < rockerList.length(); i++) {
                    JSONObject obj = rockerList.getJSONObject(i);
                    String code = obj.optString("elementId");
                    int keyCodeLeft = obj.optInt("leftCode");
                    int keyCodeRight = obj.optInt("rightCode");
                    int keyCodeUp = obj.optInt("upCode");
                    int keyCodeDown = obj.optInt("downCode");
                    int keyCodeMiddle = obj.optInt("middleCode");
                    int[] keys = new int[]{keyCodeUp, keyCodeDown, keyCodeLeft, keyCodeRight, keyCodeMiddle};

                    if(config.enableNewAnalogStick){
                        controller.addElement(createKeyBoardAnalogStickButton2(controller, code, context, keys),
                                screenScale(4, height),
                                screenScale(41, height),
                                (int) (w * 2.5), (int) (w * 2.5)
                        );
                    }else{
                        controller.addElement(createKeyBoardAnalogStickButton(controller, code, context, keys),
                                screenScale(4, height),
                                screenScale(41, height),
                                (int) (w * 2.5), (int) (w * 2.5)
                        );
                    }
                }
            }

            //鼠标按键
            if(mouseList!=null&&mouseList.length()>0){
                for (int i = 0; i < mouseList.length(); i++) {
                    JSONObject obj = mouseList.getJSONObject(i);
                    obj.put("type", 1);
                    keystrokeList.put(obj);
                }
            }

            //组合按键
//            JSONArray keyAssembleList=jsonObject1.optJSONArray("keyAssemble");
//            if(keyAssembleList!=null&&keyAssembleList.length()>0){
//                for (int i = 0; i < keyAssembleList.length(); i++) {
//                    JSONObject obj = keyAssembleList.getJSONObject(i);
//                    obj.put("type", 4);
//                    keystrokeList.put(obj);
//                }
//            }
            //组合按键
            SharedPreferences pref = context.getSharedPreferences(PREF_KEYBOARD_LIST_NAME, Activity.MODE_PRIVATE);
            Map<String,String> map= (Map<String, String>) pref.getAll();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String value = entry.getValue();
                JSONObject obj = new JSONObject(value);
                obj.put("type", 4);
                keystrokeList.put(obj);
            }

            double buttonSum = 14.0;

            //普通按键
            if(keystrokeList!=null&&keystrokeList.length()>0){
                for (int i = 0; i < keystrokeList.length(); i++) {
                    JSONObject obj = keystrokeList.getJSONObject(i);

                    String name = obj.optString("name");

                    int type = obj.optInt("type");

                    int code = obj.optInt("code");

                    int switchButton=obj.optInt("switchButton");

                    String codes=obj.optString("codes");

                    String elementId;

                    switch (type){
                        case 1:
                            if(switchButton==1){
                                elementId = "m_s_" + code;
                            }else{
                                elementId = "m_" + code;
                            }
                            break;
                        case 4:
                            elementId = obj.optString("id");
                            break;
                        default:
                            if(switchButton==1){
                                elementId = "key_s_" + code;
                            }else{
                                elementId = "key_" + code;
                            }
                            break;
                    }

                    int lastIndex = (int) (i / buttonSum);

                    int x = screenScale(1 + (int) (i % buttonSum) * BUTTON_SIZE, height);

                    int y = screenScale(BUTTON_SIZE + lastIndex * BUTTON_SIZE, height);

                    if(TextUtils.equals("m_9",elementId)||TextUtils.equals("m_10",elementId)
                            ||TextUtils.equals("m_11",elementId)
                            ||TextUtils.equals("m_12",elementId)
                            ||TextUtils.equals("m_13",elementId)){
                        controller.addElement(createDigitalTouchButton(elementId, code, type, 1, name, -1, controller, context),
                                x, y,
                                w, w
                        );
                    }else{
                        controller.addElement(createDigitalButton(elementId, type==4?codes:code, type, 1, name, -1, controller, context),
                                x, y,
                                w, w
                        );
                    }
                    LimeLog.info("x:" + x + ",y:" + y + ",W&H:" + w + "," + screenScale(BUTTON_SIZE, height));
                }
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        controller.setOpacity(config.oscOpacity);
    }

    public static void saveProfile(final KeyBoardController controller,
                                   final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences.Editor prefEditor = context.getSharedPreferences(name, Activity.MODE_PRIVATE).edit();

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefEditor.apply();
    }

    public static void loadFromPreferences(final KeyBoardController controller, final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}
