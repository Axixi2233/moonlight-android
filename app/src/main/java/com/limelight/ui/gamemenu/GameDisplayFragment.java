package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.utils.UiHelper;


/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDisplayFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_display;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private Button bt_display_screen;

    private Button bt_display_exchange;

    private Button bt_display_direction;

    private Button bt_display_bitrate;

    private Button bt_display_fps;

    private TextView tx_game_display_screen;

    private TextView tx_game_display_bit;

    private TextView tx_game_display_fps;

    private TextView tx_game_display_direction;

    private TextView tx_game_display_ex;

    private RadioGroup rg_game_display_lock;

    private RadioGroup rg_game_display_video_format;

    private RadioGroup rg_game_display_audio;

    private RadioGroup rg_game_display_hdr;

    private RadioGroup rg_game_display_vd;

    private RadioGroup rg_game_display_enforce;

    private RadioGroup rg_game_display_lowlatency;

    private int width;

    private int height;

    private int bitrate;

    private int fps;

    private boolean direction;

    private boolean exDiaplay;

    private boolean showLock=true;
    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        bt_display_screen=v.findViewById(R.id.bt_display_screen);
        bt_display_exchange=v.findViewById(R.id.bt_display_exchange);
        bt_display_direction=v.findViewById(R.id.bt_display_direction);

        bt_display_bitrate=v.findViewById(R.id.bt_display_bitrate);
        bt_display_fps=v.findViewById(R.id.bt_display_fps);
        tx_game_display_screen=v.findViewById(R.id.tx_game_display_screen);
        tx_game_display_bit=v.findViewById(R.id.tx_game_display_bit);
        tx_game_display_fps=v.findViewById(R.id.tx_game_display_fps);
        tx_game_display_direction=v.findViewById(R.id.tx_game_display_direction);
        tx_game_display_ex=v.findViewById(R.id.tx_game_display_ex);

        rg_game_display_lock=v.findViewById(R.id.rg_game_display_lock);
        rg_game_display_video_format=v.findViewById(R.id.rg_game_display_video_format);
        rg_game_display_hdr=v.findViewById(R.id.rg_game_display_hdr);
        rg_game_display_audio=v.findViewById(R.id.rg_game_display_audio);
        rg_game_display_vd=v.findViewById(R.id.rg_game_display_vd);
        rg_game_display_enforce=v.findViewById(R.id.rg_game_display_enforce);
        rg_game_display_lowlatency=v.findViewById(R.id.rg_game_display_lowlatency);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }

        v.findViewById(R.id.lv_display_lock).setVisibility(showLock?View.VISIBLE:View.GONE);

        if(prefConfig!=null){
            width=prefConfig.width;
            height=prefConfig.height;
            bitrate=prefConfig.bitrate;
            fps=prefConfig.fps;
            direction=prefConfig.enablePortrait;
            exDiaplay=prefConfig.enableExDisplay;
        }
        initViewData();
        initLock();
        initAudio();
        initHDR();
        initLowLatency();
        initVD();
        initVideoFormat();
        initEnfoce();
        ibtn_back.setOnClickListener(this);
        bt_display_screen.setOnClickListener(this);
        bt_display_exchange.setOnClickListener(this);
        bt_display_direction.setOnClickListener(this);
        bt_display_fps.setOnClickListener(this);
        bt_display_bitrate.setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);
        v.findViewById(R.id.bt_display_ex).setOnClickListener(this);

        rg_game_display_lock.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Toast.makeText(getActivity(),"切换成功！",Toast.LENGTH_SHORT).show();
                if(checkedId==R.id.rbt_game_display_lock_1){
                    prefConfig.enableScreenOnAuto=0;
                    saveLock(0);
                    dismiss();
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lock_2){
                    prefConfig.enableScreenOnAuto=1;
                    saveLock(1);
                    dismiss();
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lock_3){
                    prefConfig.enableScreenOnAuto=2;
                    saveLock(2);
                    dismiss();
                    return;
                }
            }
        });

        rg_game_display_video_format.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_video_format_1){
                    saveVideoFormat("auto");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_2){
                    saveVideoFormat("neverh265");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_3){
                    saveVideoFormat("forceh265");
                    return;
                }
                if(checkedId==R.id.rbt_game_display_video_format_4){
                    saveVideoFormat("forceav1");
                    return;
                }
            }
        });

        rg_game_display_audio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_audio_1){
                    saveAudio(false);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_audio_2){
                    saveAudio(true);
                    return;
                }
            }
        });

        rg_game_display_hdr.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_hdr_1){
                    saveHDR(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_hdr_2){
                    saveHDR(false);
                    return;
                }
            }
        });

        rg_game_display_vd.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_vd_1){
                    saveVD(0);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_vd_2){
                    saveVD(1);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_vd_3){
                    saveVD(2);
                    return;
                }
            }
        });

        rg_game_display_enforce.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_enforce_1){
                    saveEnForce(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_enforce_2){
                    saveEnForce(false);
                    return;
                }
            }
        });

        rg_game_display_lowlatency.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId==R.id.rbt_game_display_lowlatency_1){
                    savelowLatency(true);
                    return;
                }
                if(checkedId==R.id.rbt_game_display_lowlatency_2){
                    savelowLatency(false);
                    return;
                }
            }
        });
    }

    private void initEnfoce() {
        boolean foceFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_enforce_display_mode",false);
        rg_game_display_enforce.check(foceFlag?R.id.rbt_game_display_enforce_1:R.id.rbt_game_display_enforce_2);
    }

    private void initViewData() {
        tx_game_display_screen.setText("分辨率："+width+"x"+height);
        tx_game_display_bit.setText("\t码率："+(bitrate/1000)+"mbps");
        tx_game_display_fps.setText("\t帧率："+fps+"fps");
        tx_game_display_direction.setText("\t方向："+(!direction?"横屏":"竖屏(旋转功能失效，自行在PC端显示器改成竖向)"));
        tx_game_display_ex.setText("\t模式："+(exDiaplay?"外接显示器":"正常模式"));
    }

    private void initLock(){
        int lockFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("enable_screen_on_auto",0);
        switch (lockFlag){
            case 0:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_1);
                break;
            case 1:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_2);
                break;
            case 2:
                rg_game_display_lock.check(R.id.rbt_game_display_lock_3);
                break;
        }
    }

    private void initAudio(){
        boolean audioFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_host_audio",false);
        rg_game_display_audio.check(audioFlag?R.id.rbt_game_display_audio_2:R.id.rbt_game_display_audio_1);
    }

    private void initHDR(){
        boolean hdrFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("checkbox_enable_hdr",false);
        rg_game_display_hdr.check(hdrFlag?R.id.rbt_game_display_hdr_1:R.id.rbt_game_display_hdr_2);
    }
    private void initLowLatency(){
        boolean lowFlag=PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("enable_lowLatency_experiment",false);
        rg_game_display_lowlatency.check(lowFlag?R.id.rbt_game_display_lowlatency_1:R.id.rbt_game_display_lowlatency_2);
    }

    private void initVD(){
        int vddValue=PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("vdValue",0);
        switch (vddValue){
            case 0://关闭
                rg_game_display_vd.check(R.id.rbt_game_display_vd_1);
                break;
            case 1://扩展虚拟屏
                rg_game_display_vd.check(R.id.rbt_game_display_vd_2);
                break;
            case 2://仅虚拟屏
                rg_game_display_vd.check(R.id.rbt_game_display_vd_3);
                break;
        }
    }


    private void initVideoFormat(){
        String format=PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("video_format","auto");
        switch (format){
            case "auto":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_1);
                break;
            case "neverh265":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_2);
                break;
            case "forceh265":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_3);
                break;
            case "forceav1":
                rg_game_display_video_format.check(R.id.rbt_game_display_video_format_4);
                break;
        }
    }

    public void setShowLock(boolean showLock) {
        this.showLock = showLock;
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }


    private void saveVideoFormat(String value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString("video_format",value)
                .commit();
    }

    private void saveLock(int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt("enable_screen_on_auto",value)
                .commit();
    }


    private void saveAudio(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_host_audio",value)
                .commit();
    }

    private void saveHDR(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_enable_hdr",value)
                .commit();
    }

    private void saveVD(int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt("vdValue",value)
                .commit();
    }

    private void saveEnForce(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("checkbox_enforce_display_mode",value)
                .commit();
    }

    private void savelowLatency(boolean value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean("enable_lowLatency_experiment",value)
                .commit();
    }

    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.ibtn_back){
            dismiss();
            return;
        }

        if(v.getId()==R.id.btn_right){
            if(width==0||height==0||bitrate==0||fps==0){
                Toast.makeText(getActivity(),"请检查配置信息！",Toast.LENGTH_SHORT).show();
                return;
            }
            if(onClick==null){
                return;
            }
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .edit()
                    .putString(PreferenceConfiguration.RESOLUTION_PREF_STRING,width+"x"+height)
                    .putString(PreferenceConfiguration.FPS_PREF_STRING,String.valueOf(fps))
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,bitrate)
                    .putString("edit_diy_w_h",width+"x"+height)
                    .putBoolean("checkbox_enable_exdisplay",exDiaplay)
                    .putBoolean(PreferenceConfiguration.CHECKBOX_ENABLE_PORTRAIT,direction)
                    .commit();
            dismiss();
            onClick.click();
            return;
        }
        if(v.getId()==R.id.bt_display_screen){
            GameDisplayResolutionFragment fragment=new GameDisplayResolutionFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("分辨率");
            fragment.setOnClick(new GameDisplayResolutionFragment.onClick() {
                @Override
                public void click(int w, int h) {
                    width=w;
                    height=h;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display_exchange){
            int h=height;
            int w=width;
            width=h;
            height=w;
            initViewData();
            return;
        }
        if(v.getId()==R.id.bt_display_direction){
            direction=!direction;
            initViewData();
            return;
        }

        if(v.getId()==R.id.bt_display_bitrate){
            GameDisplayBitrateFragment fragment=new GameDisplayBitrateFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("码率");
            fragment.setOnClick(new GameDisplayBitrateFragment.onClick() {
                @Override
                public void click(int num) {
                    bitrate=num*1000;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }
        if(v.getId()==R.id.bt_display_fps){
            GameDisplayFpsFragment fragment=new GameDisplayFpsFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("帧率");
            fragment.setOnClick(new GameDisplayFpsFragment.onClick() {
                @Override
                public void click(int fps2) {
                    fps=fps2;
                    initViewData();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display_ex){
            exDiaplay=!exDiaplay;
            initViewData();
            return;
        }
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }
    private onClick onClick;

    public interface onClick{
        void click();
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
