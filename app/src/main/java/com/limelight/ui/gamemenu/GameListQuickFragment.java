package com.limelight.ui.gamemenu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.adapter.GameMenuQuickKeyboardAdapter;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.limelight.GameMenu.KEY_NAME;
import static com.limelight.GameMenu.PREF_NAME;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameListQuickFragment extends BaseGameMenuDialog {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_list;
    }

    private ListView lv_menu;
    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;
    private Button btn_right;
    private boolean enableClearDefaultSpecial;

    private List<GameMenuQuickBean> oldGameMenus;

    private GameMenuQuickKeyboardAdapter adapter;

    private List<GameMenuQuickBean> gameMenus;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        lv_menu=v.findViewById(R.id.lv_menu);
        tx_title=v.findViewById(R.id.tx_title);
        btn_right=v.findViewById(R.id.btn_right);
        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }

        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        btn_right.setVisibility(View.VISIBLE);

        btn_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameKeyboardUpdateFragment fragment=new GameKeyboardUpdateFragment();
                fragment.setWidth(getActivity().getResources().getDisplayMetrics().widthPixels);
                fragment.setDimAmount(0.8f);
                fragment.setTitle("设置快捷键");
                fragment.setKeyFrom(1);
                fragment.setOnClick(new GameKeyboardUpdateFragment.onClick() {
                    @Override
                    public void click(String data) {
                        updateData();
                    }
                });
                fragment.show(getFragmentManager());
            }
        });

        oldGameMenus =new ArrayList<>();
        //内置的快捷指令
        if(!enableClearDefaultSpecial){
            oldGameMenus.add(new GameMenuQuickBean("ESC (退出/菜单)",new short[]{KeyboardTranslator.VK_ESCAPE}));
            oldGameMenus.add(new GameMenuQuickBean("F11 (网页全屏)",new short[]{KeyboardTranslator.VK_F11}));
            oldGameMenus.add(new GameMenuQuickBean("Alt + F4 (关闭应用)",new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4}));
            oldGameMenus.add(new GameMenuQuickBean("Alt + Enter (窗口大小)",new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN}));
            oldGameMenus.add(new GameMenuQuickBean("Win (打开Windows开始菜单)",new short[]{KeyboardTranslator.VK_LWIN}));
            oldGameMenus.add(new GameMenuQuickBean("Ctrl+Shift+ESC (任务管理器)",new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE}));
            oldGameMenus.add(new GameMenuQuickBean("Win + D (返回桌面)",new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D}));
            oldGameMenus.add(new GameMenuQuickBean("Win + P (显示器模式)",new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_P}));
            oldGameMenus.add(new GameMenuQuickBean("Win + G (打开Xbox Game Bar)",new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G}));
            oldGameMenus.add(new GameMenuQuickBean("Shift + Tab (打开Steam Overlay)",new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB}));
            oldGameMenus.add(new GameMenuQuickBean("Win + Shift + left (切换桌面)",new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT}));
        }
        //自定义导入的指令
        SharedPreferences preferences=getActivity().getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
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
                        oldGameMenus.add(new GameMenuQuickBean(name,datas));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getActivity(),"自定义导入格式出错了，请检查！",Toast.LENGTH_SHORT).show();
            }
        }

        gameMenus=new ArrayList<>();
        gameMenus.addAll(oldGameMenus);
        gameMenus.addAll(getKeyBoardList(getActivity()));

        adapter=new GameMenuQuickKeyboardAdapter(getActivity(), gameMenus);
        lv_menu.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        lv_menu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(onClick!=null){
                    onClick.click(gameMenus.get(position));
                }
            }
        });
        lv_menu.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if(TextUtils.isEmpty(gameMenus.get(position).getId())){
                    return false;
                }
                new AlertDialog.Builder(getActivity())
                        .setTitle(gameMenus.get(position).getName())
                        .setMessage("是否删除此键值？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeKeyBoardListData(getActivity(), gameMenus.get(position));
                                updateData();
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
                return false;
            }
        });
    }

    public void setEnableClearDefaultSpecial(boolean enableClearDefaultSpecial) {
        this.enableClearDefaultSpecial = enableClearDefaultSpecial;
    }

    public void updateData(){
        gameMenus.clear();
        gameMenus.addAll(oldGameMenus);
        gameMenus.addAll(getKeyBoardList(getActivity()));
        adapter.setDatas(gameMenus);
        adapter.notifyDataSetChanged();
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private onClick onClick;

    public interface onClick{
        void click(GameMenuQuickBean bean);
    }

    public void setOnClick(GameListQuickFragment.onClick onClick) {
        this.onClick = onClick;
    }

    public List<GameMenuQuickBean> getKeyBoardList(Context context){
        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        Map<String,String> map= (Map<String, String>) pref.getAll();
        List<GameMenuQuickBean> quickBeans=new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            quickBeans.add(new Gson().fromJson(value,GameMenuQuickBean.class));
        }
        return quickBeans;
    }

    public void removeKeyBoardListData(Context context,GameMenuQuickBean bean){
        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        pref.edit().remove(bean.getId()).apply();
    }

    public static final String PREF_QUICK_LIST_NAME="quick_axi_keyAssemble";
    public static final String PREF_QUICK_LIST_KEY="quick_assemble_key_";
}
