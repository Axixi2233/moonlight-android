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

public class GamePadAddFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_game_pad_add;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;
    private String title;

    private GridView rv_keyboard_gamepad;

    private List<GameMenuQuickBean> beanGamePadList=new ArrayList<>();

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);
        rv_keyboard_gamepad=v.findViewById(R.id.rv_keyboard_gamepad);
        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        beanGamePadList.add(new GameMenuQuickBean("ABXY",ControllerPacket.PADDLE2_FLAG,"Y-X-A-B",5,false).setGamePad(true));

        //"▲", "◀", "▼", "▶"
        beanGamePadList.add(new GameMenuQuickBean("十字键",ControllerPacket.PADDLE1_FLAG,"▲-◀-▼-▶",5,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("L3", ControllerPacket.LS_CLK_FLAG,"左摇杆按下",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("R3", ControllerPacket.RS_CLK_FLAG,"右摇杆按下",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("L1", ControllerPacket.LB_FLAG,"左肩键",4,false).setGamePad(true));
        beanGamePadList.add(new GameMenuQuickBean("L2", ControllerPacket.PADDLE3_FLAG,"左扳机",4,false).setGamePad(true).setShapeType(1));

        beanGamePadList.add(new GameMenuQuickBean("R1", ControllerPacket.RB_FLAG,"右肩键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("R2", ControllerPacket.PADDLE4_FLAG,"右扳机",4,false).setGamePad(true).setShapeType(1));

        beanGamePadList.add(new GameMenuQuickBean("MODE", ControllerPacket.SPECIAL_BUTTON_FLAG,"XBOX键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("SELECT", ControllerPacket.BACK_FLAG,"视图键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("START", ControllerPacket.PLAY_FLAG,"菜单键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("触控板",ControllerPacket.TOUCHPAD_FLAG,"DS4触控板按键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("左摇杆",ControllerPacket.PADDLE5_FLAG,"常规模式",3,false).setFreeStick(false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("右摇杆",ControllerPacket.PADDLE6_FLAG,"常规模式",3,false).setFreeStick(false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("左摇杆",ControllerPacket.PADDLE5_FLAG,"自由摇杆",3,false).setFreeStick(false).setGamePad(true).setFreeStick(true));

        beanGamePadList.add(new GameMenuQuickBean("右摇杆",ControllerPacket.PADDLE6_FLAG,"自由摇杆",3,false).setFreeStick(false).setGamePad(true).setFreeStick(true));

        beanGamePadList.add(new GameMenuQuickBean("A", ControllerPacket.A_FLAG,"A键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("B", ControllerPacket.B_FLAG,"B键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("X", ControllerPacket.X_FLAG,"X键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("Y", ControllerPacket.Y_FLAG,"Y键",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("▲", ControllerPacket.UP_FLAG,"十字键·上",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("▼", ControllerPacket.DOWN_FLAG,"十字键·下",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("◀", ControllerPacket.LEFT_FLAG,"十字键·左",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("▶", ControllerPacket.RIGHT_FLAG,"十字键·右",4,false).setGamePad(true));

        beanGamePadList.add(new GameMenuQuickBean("Share", ControllerPacket.MISC_FLAG,"手柄分享键",4,false).setGamePad(true));

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rv_keyboard_gamepad.setNumColumns(5);
        } else {
            rv_keyboard_gamepad.setNumColumns(3);
        }
        rv_keyboard_gamepad.setAdapter(new MyGridAdapter(getActivity(),beanGamePadList));

        rv_keyboard_gamepad.setOnItemClickListener((parent, view, position, id) -> {
            GameMenuQuickBean bean=beanGamePadList.get(position);
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

    @Override
    public void onClick(View v) {

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
