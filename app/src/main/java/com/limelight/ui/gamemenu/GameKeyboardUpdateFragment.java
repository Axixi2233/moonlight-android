package com.limelight.ui.gamemenu;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import java.util.ArrayList;
import java.util.List;

import static com.limelight.ui.gamemenu.GameListKeyBoardFragment.PREF_KEYBOARD_LIST_KEY;
import static com.limelight.ui.gamemenu.GameListQuickFragment.PREF_QUICK_LIST_KEY;

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
    private LinearLayout lv_digitpad;
    private LinearLayout lv_digitpad_1;
    private LinearLayout lv_digitpad_2;
    private TextView tx_content;

    private EditText edt_name;

    private StringBuffer contentValues=new StringBuffer();

    private StringBuffer contentNames=new StringBuffer();

    private List<GameMenuQuickBean> beanList=new ArrayList<>();

    private GridView rv_keyboard_mouse;
    //按键类型0-组合键列表 1-快捷键列表
    private int keyFrom;
    private RadioGroup rg_keyboard;

    private GridView rv_keyboard_function;

    private List<GameMenuQuickBean> beanFunctionList =new ArrayList<>();

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        keyboardView=v.findViewById(R.id.lv_keyboard);
        lv_digitpad=v.findViewById(R.id.lv_digitpad);
        lv_digitpad_1=v.findViewById(R.id.lv_digitpad_1);
        lv_digitpad_2=v.findViewById(R.id.lv_digitpad_2);
        tx_content=v.findViewById(R.id.tx_content);
        edt_name=v.findViewById(R.id.edt_name);
        rv_keyboard_mouse=v.findViewById(R.id.rv_keyboard_mouse);
        rv_keyboard_function=v.findViewById(R.id.rv_keyboard_function);
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


        rg_keyboard=v.findViewById(R.id.rg_keyboard);
        rg_keyboard.check(R.id.rbt_keyboard_1);

        rg_keyboard.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(R.id.rbt_keyboard_1==checkedId){
                    v.findViewById(R.id.lv_keyboard).setVisibility(View.VISIBLE);
                    v.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.GONE);
                    rv_keyboard_mouse.setVisibility(View.GONE);
                    rv_keyboard_function.setVisibility(View.GONE);
                    return;
                }
                if(R.id.rbt_keyboard_2==checkedId){
                    v.findViewById(R.id.lv_keyboard).setVisibility(View.GONE);
                    rv_keyboard_mouse.setVisibility(View.GONE);
                    rv_keyboard_function.setVisibility(View.GONE);
                    v.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.VISIBLE);
                    return;
                }
                if(R.id.rbt_keyboard_3==checkedId){
                    v.findViewById(R.id.lv_keyboard).setVisibility(View.GONE);
                    rv_keyboard_mouse.setVisibility(View.VISIBLE);
                    rv_keyboard_function.setVisibility(View.GONE);
                    v.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.GONE);
                    return;
                }
                if(R.id.rbt_keyboard_4==checkedId){
                    v.findViewById(R.id.lv_keyboard).setVisibility(View.GONE);
                    rv_keyboard_mouse.setVisibility(View.GONE);
                    rv_keyboard_function.setVisibility(View.VISIBLE);
                    v.findViewById(R.id.lv_keyboard_digitpad).setVisibility(View.GONE);
                    return;
                }
            }
        });

        v.findViewById(R.id.rbt_keyboard_3).setVisibility(keyFrom==0?View.VISIBLE:View.GONE);
        v.findViewById(R.id.rbt_keyboard_4).setVisibility(keyFrom==0?View.VISIBLE:View.GONE);

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
        for (int i = 0; i < lv_digitpad.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) lv_digitpad.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
            }
        }
        for (int i = 0; i < lv_digitpad_1.getChildCount(); i++){
            lv_digitpad_1.getChildAt(i).setOnTouchListener(touchListener);
        }
        for (int i = 0; i < lv_digitpad_2.getChildCount(); i++){
            lv_digitpad_2.getChildAt(i).setOnTouchListener(touchListener);
        }

        beanList.add(new GameMenuQuickBean("鼠标·左键",1,"常规模式",1,false));
        beanList.add(new GameMenuQuickBean("鼠标·左键",1,"锁定模式",1,true));
        beanList.add(new GameMenuQuickBean("鼠标·右键",3,"常规模式",1,false));
        beanList.add(new GameMenuQuickBean("鼠标·右键",3,"锁定模式",1,true));
        beanList.add(new GameMenuQuickBean("鼠标·中键",2,"常规模式",1,false));
        beanList.add(new GameMenuQuickBean("鼠标·中键",2,"锁定模式",1,true));

        beanList.add(new GameMenuQuickBean("滚轮·上",4,"常规模式",1,false));
        beanList.add(new GameMenuQuickBean("滚轮·上",4,"锁定模式",1,true));
        beanList.add(new GameMenuQuickBean("滚轮·下",5,"常规模式",1,false));
        beanList.add(new GameMenuQuickBean("滚轮·下",5,"锁定模式",1,true));

        beanList.add(new GameMenuQuickBean("触控板",10,"常规模式",2,false).setShapeType(1));
        beanList.add(new GameMenuQuickBean("触控板·左",11,"左键",2,false).setShapeType(1));
        beanList.add(new GameMenuQuickBean("触控板·右",9,"右键",2,false).setShapeType(1));
        beanList.add(new GameMenuQuickBean("触控板·中",12,"中键",2,false).setShapeType(1));
        beanList.add(new GameMenuQuickBean("触控板·无",13,"只转视野",2,false).setShapeType(1));

        beanList.add(new GameMenuQuickBean("摇杆","51,47,29,32","W-A-S-D",3,false));
        beanList.add(new GameMenuQuickBean("摇杆","19,20,21,22","上-左-下-右",3,false));

        beanList.add(new GameMenuQuickBean("自由摇杆","51,47,29,32","W-A-S-D",3,false).setFreeStick(true));
        beanList.add(new GameMenuQuickBean("自由摇杆","19,20,21,22","上-左-下-右",3,false).setFreeStick(true));

        beanList.add(new GameMenuQuickBean("十字键","51,47,29,32","W-A-S-D",5,false));
        beanList.add(new GameMenuQuickBean("十字键","19,20,21,22","↑-←-↓-→",5,false));

        //快捷键：AXIX前缀，例如AXIX0(软键盘)，AXIX1(虚拟按键)，AXIX2(虚拟全键盘)，AXIX3(虚拟手柄)，AXIX4(悬浮球)，AXIX5(性能信息),AXIX6(游戏菜单)
        beanFunctionList.add(new GameMenuQuickBean("软键盘","29,52,37,52,7","AXIX0",4,false));
        beanFunctionList.add(new GameMenuQuickBean("虚拟按键","29,52,37,52,8","AXIX1",4,false));
        beanFunctionList.add(new GameMenuQuickBean("虚拟全键盘","29,52,37,52,9","AXIX2",4,false));
        beanFunctionList.add(new GameMenuQuickBean("虚拟手柄","29,52,37,52,10","AXIX3",4,false));
        beanFunctionList.add(new GameMenuQuickBean("悬浮球","29,52,37,52,11","AXIX4",4,false));
        beanFunctionList.add(new GameMenuQuickBean("性能信息","29,52,37,52,12","AXIX5",4,false));
        beanFunctionList.add(new GameMenuQuickBean("游戏菜单","29,52,37,52,13","AXIX6",4,false));

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rv_keyboard_mouse.setNumColumns(5);
            rv_keyboard_function.setNumColumns(5);
        } else {
            rv_keyboard_mouse.setNumColumns(3);
            rv_keyboard_function.setNumColumns(3);
        }

        rv_keyboard_mouse.setAdapter(new MyGridAdapter(getActivity(),beanList));

        rv_keyboard_mouse.setOnItemClickListener((parent, view, position, id) -> {
            GameMenuQuickBean bean=beanList.get(position);
            bean.setId( PREF_KEYBOARD_LIST_KEY+System.currentTimeMillis());
            LimeLog.info("axi->rv:"+new Gson().toJson(bean));
            onClick.click(bean);
            dismiss();
        });

        rv_keyboard_function.setAdapter(new MyGridAdapter(getActivity(), beanFunctionList));

        rv_keyboard_function.setOnItemClickListener((parent, view, position, id) -> {
            GameMenuQuickBean bean= beanFunctionList.get(position);
            bean.setId( PREF_KEYBOARD_LIST_KEY+System.currentTimeMillis());
            LimeLog.info("axi->rv:"+new Gson().toJson(bean));
            onClick.click(bean);
            dismiss();
        });
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
            String name=edt_name.getText().toString();
            //快捷键列表
            if(keyFrom==1){
                name=name.trim();
            }
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
            bean.setBtnType(4);
            bean.setCodes(contentValues.toString());
            bean.setDesc(contentNames.toString());
//            saveKeyBoardListData(getActivity(),bean);
//            Toast.makeText(getActivity(),"已保存！",Toast.LENGTH_SHORT).show();
            onClick.click(bean);
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
        void click(GameMenuQuickBean bean);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }

    public class MyGridAdapter extends BaseAdapter {
        private Context context;
        private List<GameMenuQuickBean> list;

        public MyGridAdapter(Context context, List<GameMenuQuickBean> list) {
            this.context = context;
            this.list = list;
        }

        @Override
        public int getCount() { return list.size(); }

        @Override
        public Object getItem(int position) { return list.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_layout_axixi_keyboard_mouse, parent, false);
            }
            TextView name = convertView.findViewById(R.id.tv_name);
            TextView desc = convertView.findViewById(R.id.tv_desc);
            GameMenuQuickBean item = list.get(position);
            name.setText(item.getName());
            desc.setText(item.getDesc());
            return convertView;
        }
    }

}
