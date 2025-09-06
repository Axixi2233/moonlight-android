package com.limelight.ui.gamemenu;

import android.text.TextUtils;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDisplayFpsFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_display_fps;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private Button bt_display_fps_90;

    private Button bt_display_fps_120;

    private Button bt_display_fps_144;

    private Button bt_display_fps_max;

    private int maxSupportedFps;
    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        bt_display_fps_90=v.findViewById(R.id.bt_display_fps_90);
        bt_display_fps_120=v.findViewById(R.id.bt_display_fps_120);
        bt_display_fps_144=v.findViewById(R.id.bt_display_fps_144);
        bt_display_fps_max=v.findViewById(R.id.bt_display_fps_max);
        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        ibtn_back.setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_60).setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_30).setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_90).setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_120).setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_144).setOnClickListener(this);
        v.findViewById(R.id.bt_display_fps_max).setOnClickListener(this);

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        maxSupportedFps = (int) display.getRefreshRate();
        boolean unlockFps=PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps;

        if(maxSupportedFps>=90||unlockFps){
            bt_display_fps_90.setVisibility(View.VISIBLE);
        }
        if(maxSupportedFps>=120||unlockFps){
            bt_display_fps_120.setVisibility(View.VISIBLE);
        }
        if(maxSupportedFps>=144){
            bt_display_fps_144.setVisibility(View.VISIBLE);
        }
        if(maxSupportedFps>144){
            bt_display_fps_max.setVisibility(View.VISIBLE);
            bt_display_fps_max.setText(maxSupportedFps+" fps");
        }
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
        dismiss();
        if(onClick==null){
            return;
        }
        if(v.getId()==R.id.bt_display_fps_30){
            onClick.click(30);
            return;
        }
        if(v.getId()==R.id.bt_display_fps_60){
            onClick.click(60);
            return;
        }
        if(v.getId()==R.id.bt_display_fps_90){
            onClick.click(90);
            return;
        }
        if(v.getId()==R.id.bt_display_fps_120){
            onClick.click(120);
            return;
        }
        if(v.getId()==R.id.bt_display_fps_144){
            onClick.click(144);
            return;
        }
        if(v.getId()==R.id.bt_display_fps_max){
            onClick.click(maxSupportedFps);
            return;
        }
    }
    private onClick onClick;

    public interface onClick{
        void click(int fps);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }

}
