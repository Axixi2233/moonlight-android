package com.limelight.ui.gamemenu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import static com.limelight.ui.gamemenu.GameListKeyBoardFragment.PREF_KEYBOARD_LIST_KEY;
import static com.limelight.ui.gamemenu.GameListKeyBoardFragment.PREF_KEYBOARD_LIST_NAME;
import static com.limelight.ui.gamemenu.GameListQuickFragment.PREF_QUICK_LIST_KEY;
import static com.limelight.ui.gamemenu.GameListQuickFragment.PREF_QUICK_LIST_NAME;

/**
 * Description
 * Date: 2024-12-17
 * Time: 16:07
 */
public class GameKeyboardUpdateFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_keyboard_group_add;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;
    private String title;

    private LinearLayout keyboardView;

    private TextView tx_content;

    private EditText edt_name;

    private StringBuffer contentValues=new StringBuffer();

    private StringBuffer contentNames=new StringBuffer();

    //按键类型0-组合键列表 1-快捷键列表
    private int keyFrom;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        keyboardView=v.findViewById(R.id.lv_keyboard);
        tx_content=v.findViewById(R.id.tx_content);
        edt_name=v.findViewById(R.id.edt_name);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.btn_right).setOnClickListener(this);
        v.findViewById(R.id.btn_reset).setOnClickListener(this);

        v.findViewById(R.id.tx_tips_axi).setVisibility(keyFrom==0?View.VISIBLE:View.GONE);

        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 处理按下事件
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button_confirm);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 处理释放事件
                        v.setBackgroundResource(R.drawable.bg_ax_keyboard_button);
                        if(contentValues.toString().split(",").length>=5){
                            Toast.makeText(getActivity(),"限制只能输入5个按键！",Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        if(!TextUtils.isEmpty(contentValues.toString())){
                            contentValues.append(",");
                        }
                        if(!TextUtils.isEmpty(contentNames.toString())){
                            contentNames.append("+");
                        }
                        TextView view=(TextView) v;
                        String tag=(String) v.getTag();
                        contentValues.append(Integer.parseInt(tag));
                        contentNames.append(view.getText().toString().trim());
                        tx_content.setText(contentNames.toString());
                        return true;
                }
                return false;
            }
        };
        for (int i = 0; i < keyboardView.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) keyboardView.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                View view=keyboardRow.getChildAt(j);
                view.setOnTouchListener(touchListener);
            }
        }
    }

    @Override
    public float getDimAmount() {
        return 0.9f;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setKeyFrom(int keyFrom) {
        this.keyFrom = keyFrom;
    }

    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.btn_right){
            String name=edt_name.getText().toString().trim();
            if(TextUtils.isEmpty(name)){
                Toast.makeText(getActivity(),"请输入名称！",Toast.LENGTH_SHORT).show();
                return;
            }
            if(TextUtils.isEmpty(contentValues.toString())){
                Toast.makeText(getActivity(),"请输入组合键！",Toast.LENGTH_SHORT).show();
                return;
            }
            GameMenuQuickBean bean=new GameMenuQuickBean();
            bean.setName(name);
            if(keyFrom==0){
                bean.setId( PREF_KEYBOARD_LIST_KEY+System.currentTimeMillis());
            }
            if(keyFrom==1){
                bean.setId( PREF_QUICK_LIST_KEY+System.currentTimeMillis());
            }
            bean.setCodes(contentValues.toString());
            bean.setDesc(contentNames.toString());
            saveKeyBoardListData(getActivity(),bean);
            Toast.makeText(getActivity(),"已保存！",Toast.LENGTH_SHORT).show();
            onClick.click(null);
            dismiss();
            return;
        }
        if(v.getId()==R.id.btn_reset){
            contentValues.delete(0,contentValues.length());
            contentNames.delete(0,contentNames.length());
            tx_content.setText("");
            edt_name.setText("");
            return;
        }
    }
    private onClick onClick;

    public interface onClick{
        void click(String data);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }

    public void saveKeyBoardListData(Context context,GameMenuQuickBean bean){
        String key=keyFrom==0?PREF_KEYBOARD_LIST_NAME:PREF_QUICK_LIST_NAME;
        SharedPreferences pref = context.getSharedPreferences(key, Activity.MODE_PRIVATE);
        pref.edit().putString(bean.getId(),new Gson().toJson(bean)).apply();
    }

}
