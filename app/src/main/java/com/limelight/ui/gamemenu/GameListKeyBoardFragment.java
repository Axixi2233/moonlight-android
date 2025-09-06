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

import com.google.gson.Gson;
import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.adapter.GameMenuQuickKeyboardAdapter;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description
 * Date: 2024-12-17
 * Time: 16:07
 */
public class GameListKeyBoardFragment extends BaseGameMenuDialog {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_list;
    }

    private ListView lv_menu;

    private ImageButton ibtn_back;

    private TextView tx_title;

    private String title;

    private Button btn_right;

    private List<GameMenuQuickBean> gameMenus=new ArrayList<>();

    private GameMenuQuickKeyboardAdapter adapter;
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
                fragment.setTitle("组合键");
                fragment.setOnClick(new GameKeyboardUpdateFragment.onClick() {
                    @Override
                    public void click(String data) {
                        updateData();
                    }
                });
                fragment.show(getFragmentManager());
            }
        });

        gameMenus=getKeyBoardList(getActivity());
        adapter=new GameMenuQuickKeyboardAdapter(getActivity(),gameMenus);
        lv_menu.setAdapter(adapter);
        adapter.setType(2);
        adapter.notifyDataSetChanged();
        lv_menu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(gameMenus.get(position).getName())
                        .setMessage("是否删除此键值？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeKeyBoardListData(getActivity(),gameMenus.get(position));
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

            }
        });
    }

    public void updateData(){
        gameMenus=getKeyBoardList(getActivity());
        adapter.setDatas(gameMenus);
        adapter.notifyDataSetChanged();
        if(onClick!=null){
            onClick.click();
        }
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
        void click();
    }

    public void setOnClick(GameListKeyBoardFragment.onClick onClick) {
        this.onClick = onClick;
    }

    public List<GameMenuQuickBean> getKeyBoardList(Context context){
        SharedPreferences pref = context.getSharedPreferences(PREF_KEYBOARD_LIST_NAME, Activity.MODE_PRIVATE);
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
        SharedPreferences pref = context.getSharedPreferences(PREF_KEYBOARD_LIST_NAME, Activity.MODE_PRIVATE);
        pref.edit().remove(bean.getId()).apply();
    }

    public static final String PREF_KEYBOARD_LIST_NAME="keyboard_axi_keyAssemble";
    public static final String PREF_KEYBOARD_LIST_KEY="assemble_key_";
}
