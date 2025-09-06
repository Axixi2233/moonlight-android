package com.limelight.ui.gamemenu;

import android.hardware.SensorManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamepad.GyroView;

public class GameGyroFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_game_gyro;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;
    private String title;

    private GyroView gv_gyro_view;
    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);
        gv_gyro_view=v.findViewById(R.id.gv_gyro_view);
//        tx_info=v.findViewById(R.id.tx_info);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(this);
        gv_gyro_view.setSensorManager(sensorManager);
        gv_gyro_view.start();
    }

    private void initViewData() {

    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }


    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.ibtn_back){
            dismiss();
            return;
        }
    }
    private onClick onClick;

    public interface onClick{
        void click(int w,int h);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gv_gyro_view.release();
    }

    private SensorManager sensorManager;
    public void setSensorManager(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }
}
