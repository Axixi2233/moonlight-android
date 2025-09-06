package com.limelight.ui.gamemenu;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameFunctionFragment extends BaseGameMenuDialog implements View.OnClickListener {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_function;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
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
        v.findViewById(R.id.btn_task_manager).setOnClickListener(this);
        v.findViewById(R.id.btn_sleep).setOnClickListener(this);
        v.findViewById(R.id.btn_shutdown).setOnClickListener(this);
        v.findViewById(R.id.btn_clipboard_send).setOnClickListener(this);
        v.findViewById(R.id.btn_reboot).setOnClickListener(this);
        v.findViewById(R.id.btn_open_setting).setOnClickListener(this);
        v.findViewById(R.id.btn_logout).setOnClickListener(this);
        v.findViewById(R.id.btn_clipboard_open).setOnClickListener(this);
        v.findViewById(R.id.btn_computer).setOnClickListener(this);
        v.findViewById(R.id.btn_win_center).setOnClickListener(this);
        v.findViewById(R.id.btn_win_p).setOnClickListener(this);

    }

    public void setTitle(String title) {
        this.title = title;
    }

    private onClick click;

    @Override
    public void onClick(View v) {
        if(click==null){
            return;
        }
        if(v.getId()==R.id.btn_logout){
            click.click("注销",0);
            return;
        }
        if(v.getId()==R.id.btn_shutdown){
            click.click("关机",1);
            return;
        }
        if(v.getId()==R.id.btn_sleep){
            click.click("睡眠",2);
            return;
        }
        if(v.getId()==R.id.btn_reboot){
            click.click("重启",3);
            return;
        }
        if(v.getId()==R.id.btn_task_manager){
            click.click("任务管理器",4);
            return;
        }
        if(v.getId()==R.id.btn_clipboard_send){
            click.click("发送剪切板",5);
            return;
        }
        if(v.getId()==R.id.btn_clipboard_open){
            click.click("打开剪切板",6);
            return;
        }
        if(v.getId()==R.id.btn_open_setting){
            click.click("打开设置",7);
            return;
        }
        if(v.getId()==R.id.btn_computer){
            click.click("我的电脑",8);
            return;
        }
        if(v.getId()==R.id.btn_win_center){
            click.click("移动中心",9);
            return;
        }
        if(v.getId()==R.id.btn_win_p){
            click.click("win+p",10);
            return;
        }


    }

    public interface onClick{
        void click(String title,int index);
    }

    public void setOnClick(onClick onClick) {
        this.click = onClick;
    }
}
