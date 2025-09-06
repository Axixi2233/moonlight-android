package com.limelight.ui.gamemenu;

import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.adapter.GameMenuQuickKeyboardAdapter;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameListMouseFragment extends BaseGameMenuDialog {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_list;
    }

    private ListView lv_menu;
    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        lv_menu=v.findViewById(R.id.lv_menu);
        tx_title=v.findViewById(R.id.tx_title);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }

        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        String[] strings=getResources().getStringArray(R.array.mouse_model_names_axi);
        List<GameMenuQuickBean> gameMenus=new ArrayList<>();
        for (int i = 0; i < strings.length; i++) {
            gameMenus.add(new GameMenuQuickBean(strings[i],null));
        }
        gameMenus.add(new GameMenuQuickBean("切换本地鼠标(需外接物理鼠标)",null));
        gameMenus.add(new GameMenuQuickBean("适合远程桌面的鼠标模式(需切换到普通鼠标模式)",null));
        gameMenus.add(new GameMenuQuickBean("隐藏/显示电脑端鼠标光标",null));
        GameMenuQuickKeyboardAdapter adapter=new GameMenuQuickKeyboardAdapter(getActivity(),gameMenus);
        adapter.setType(1);
        lv_menu.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        lv_menu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(onClick!=null){
                    onClick.click(gameMenus.get(position).getName(),position);
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

    private onClick onClick;

    public interface onClick{
        void click(String title,int index);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
