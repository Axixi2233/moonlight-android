package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu {

    private static final long TEST_GAME_FOCUS_DELAY = 10;
    private static final long KEY_UP_DELAY = 25;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        public final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final NvConnection conn;
    private final GameInputDevice device;

    public GameMenu(Game game, NvConnection conn, GameInputDevice device) {
        this.game = game;
        this.conn = conn;
        this.device = device;

        showMenu();
    }

    private String getString(int id) {
        return game.getResources().getString(id);
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

    private void sendKeys(short[] keys) {
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

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        builder.setTitle(title);
        builder.setIcon(R.drawable.app_icon);

        final ArrayAdapter<String> actions =
                new ArrayAdapter<String>(game, android.R.layout.simple_list_item_1);

        for (MenuOption option : options) {
            actions.add(option.label);
        }

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (!label.equals(option.label)) {
                    continue;
                }

                run(option);
                break;
            }
        });

        builder.show();
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).enableClearDefaultSpecial){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_p),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_P})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_q),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_Q})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_s), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_S})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_u), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_U})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_r), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_R})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_i), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_I})), 200);
            }));

        }

        //自定义导入的指令
        SharedPreferences preferences=game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value=preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                JSONObject object=new JSONObject(value);
                JSONArray array=object.optJSONArray("data");
                if(array!=null&&array.length()>0){
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject object1=array.getJSONObject(i);
                        String name=object1.optString("name");
                        JSONArray array1=object1.getJSONArray("data");
                        short[] datas=new short[array1.length()];
                        for (int j = 0; j < array1.length(); j++) {
                            String code=array1.getString(j);
                            datas[j]= (short) Integer.parseInt(code.substring(2), 16);
                        }
                        MenuOption option=new MenuOption(name, () -> sendKeys(datas));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,"自定义导入格式出错了，请检查！",Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[options.size()]));
    }

    private void showMenu() {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), () -> game.disconnect()));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
                () -> game.toggleKeyboard()));

        options.add(new MenuOption(getString(R.string.game_menu_switch_mouse_model), true,
                () -> game.switchMouseModel()));

        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> showSpecialKeysMenu()));

        options.add(new MenuOption(getString(R.string.game_menu_hud), true,
                () -> game.showHUD()));

        options.add(new MenuOption(getString(R.string.game_menu_switch_keyboard_model), true,
                () -> game.showHideKeyboardController()));

        options.add(new MenuOption(getString(R.string.game_menu_switch_virtual_model), true,
                () -> game.showHideVirtualController()));
        options.add(new MenuOption(getString(R.string.game_menu_switch_virtual_keyboard_model), true,
                () -> game.showHidekeyBoardLayoutController()));
        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true,
                () -> game.switchTouchSensitivity()));
        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_update_x), true,
                () -> showSetSensitivityDialog("touchX")));
        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_update_y), true,
                () -> showSetSensitivityDialog("touchY")));
        options.add(new MenuOption("横竖屏切换", true,
                () -> game.switchLandscapePortraitScreen()));
        options.add(new MenuOption("(开启/关闭)画面移动&缩放", true,
                () -> game.screenMoveZoom()));
        options.add(new MenuOption("切换性能信息样式", true,
                () -> game.switchHUD()));
        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }

        options.add(new MenuOption(getString(R.string.game_menu_quit_steaming), () -> {
            game.isQuitSteamingFlag=true;
            game.disconnect();
        }));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog("游戏快捷菜单", options.toArray(new MenuOption[options.size()]));
    }


    public void showSetSensitivityDialog(String key) {
        Context context=game;
        int step = 10;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("调整触摸灵敏度");
        // 动态创建布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);

        // 创建TextView用于显示SeekBar的值
        final TextView valueText = new TextView(context);
        valueText.setGravity(Gravity.CENTER_HORIZONTAL);
        valueText.setTextSize(32);
        layout.addView(valueText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 创建SeekBar
        final SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(800 / step);
        // seekBar.setMax(getResources().getInteger(R.integer.seekbar_touch_sensitivity));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            seekBar.setMin(10 / step);
        }
        layout.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 从配置中获取初始值
        int initialValue;
        switch (key) {
            case "touchX":
                initialValue = game.prefConfig.touchSensitivityX;
                builder.setTitle("调整触摸灵敏度X轴(当前界面生效)");
                break;
            case "touchY":
                initialValue = game.prefConfig.touchSensitivityY;
                builder.setTitle("调整触摸灵敏度Y轴(当前界面生效)");
                break;
            default:
                initialValue = 100;
                break;
        }
        seekBar.setProgress(initialValue / step);
        valueText.setText(initialValue+"%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(progress * step +"%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        builder.setView(layout);

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存修改后的值
                int newValue = seekBar.getProgress() * step;
                switch (key) {
                    case "touchX":
                        game.prefConfig.touchSensitivityX = newValue;
                        break;
                    case "touchY":
                        game.prefConfig.touchSensitivityY = newValue;
                        break;
                }
            }
        });

        builder.setNegativeButton("取消", null);

        builder.create().show();
    }
}