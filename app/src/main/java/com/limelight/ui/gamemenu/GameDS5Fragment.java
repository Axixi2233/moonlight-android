package com.limelight.ui.gamemenu;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDS5Fragment extends BaseGameMenuDialog implements SeekBar.OnSeekBarChangeListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_axi_gamepad_ds5;
    }
    private ImageButton ibtn_back;
    private TextView tx_title;
    private String title;
    private RadioGroup rg_game_control_ds5;
    private SeekBar sb_game_control_ds5_strength;
    private SeekBar sb_game_control_ds5_frequency;
    private SeekBar sb_game_control_ds5_start;
    private SeekBar sb_game_control_ds5_end;


    private TextView tx_game_control_ds5_strength;
    private TextView tx_game_control_ds5_frequency;
    private TextView tx_game_control_ds5_start;
    private TextView tx_game_control_ds5_end;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);
        rg_game_control_ds5=v.findViewById(R.id.rg_game_control_ds5);

        sb_game_control_ds5_strength=v.findViewById(R.id.sb_game_control_ds5_strength);
        sb_game_control_ds5_frequency=v.findViewById(R.id.sb_game_control_ds5_frequency);
        sb_game_control_ds5_start=v.findViewById(R.id.sb_game_control_ds5_start);
        sb_game_control_ds5_end=v.findViewById(R.id.sb_game_control_ds5_end);

        tx_game_control_ds5_strength=v.findViewById(R.id.tx_game_control_ds5_strength);
        tx_game_control_ds5_frequency=v.findViewById(R.id.tx_game_control_ds5_frequency);
        tx_game_control_ds5_start=v.findViewById(R.id.tx_game_control_ds5_start);
        tx_game_control_ds5_end=v.findViewById(R.id.tx_game_control_ds5_end);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.btn_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onClick!=null){
                    onClick.click(1,true);
                    Toast.makeText(getActivity(),"已生效！",Toast.LENGTH_SHORT).show();
                }
            }
        });
        sb_game_control_ds5_strength.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_frequency.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_start.setOnSeekBarChangeListener(this);
        sb_game_control_ds5_end.setOnSeekBarChangeListener(this);

        rg_game_control_ds5.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //关闭
                if(checkedId==R.id.rbt_game_control_ds5_1){
                    setDs5TriggerMode(0);
                    return;
                }
                //阻尼
                if(checkedId==R.id.rbt_game_control_ds5_2){
                    setDs5TriggerMode(1);
                    return;
                }
                //扳机
                if(checkedId==R.id.rbt_game_control_ds5_3){
                    setDs5TriggerMode(2);
                    return;
                }
                //自动步枪扳机
                if(checkedId==R.id.rbt_game_control_ds5_4){
                    setDs5TriggerMode(6);
                    return;
                }
            }
        });
    }


    private void setDs5TriggerMode(int mode){
        prefConfig.ds5TriggerMode=mode;
    }

    private void initDs5View(){
        sb_game_control_ds5_strength.setProgress(prefConfig.ds5TriggerStrength);
        tx_game_control_ds5_strength.setText(""+prefConfig.ds5TriggerStrength);

        sb_game_control_ds5_frequency.setProgress(prefConfig.ds5TriggerFrequency);
        tx_game_control_ds5_frequency.setText(""+prefConfig.ds5TriggerFrequency);

        sb_game_control_ds5_start.setProgress(prefConfig.ds5TriggerStart);
        tx_game_control_ds5_start.setText(""+prefConfig.ds5TriggerStart);

        sb_game_control_ds5_end.setProgress(prefConfig.ds5TriggerEnd);
        tx_game_control_ds5_end.setText(""+prefConfig.ds5TriggerEnd);
    }

    private void initViewData() {
        if(prefConfig==null){
            return;
        }
        initDs5View();
        switch (prefConfig.ds5TriggerMode){
            case 0:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_1);
                break;
            case 1:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_2);
                break;
            case 2:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_3);
                break;
            case 6:
                rg_game_control_ds5.check(R.id.rbt_game_control_ds5_4);
                break;
        }
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private PreferenceConfiguration prefConfig;

    public void setPrefConfig(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    private void saveSetting(String name,int value){
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(name,value)
                .apply();
    }

    private onClick onClick;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(seekBar==sb_game_control_ds5_strength){
            prefConfig.ds5TriggerStrength=progress;
            saveSetting("ds5TriggerStrength",progress);
        }
        if(seekBar==sb_game_control_ds5_frequency){
            prefConfig.ds5TriggerFrequency=progress;
            saveSetting("ds5TriggerFrequency",progress);
        }
        if(seekBar==sb_game_control_ds5_start){
            prefConfig.ds5TriggerStart=progress;
            saveSetting("ds5TriggerStart",progress);
        }
        if(seekBar==sb_game_control_ds5_end){
            prefConfig.ds5TriggerEnd=progress;
            saveSetting("ds5TriggerEnd",progress);
        }
        initDs5View();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public interface onClick{
        void click(int index,boolean flag);
    }

    public void setOnClick(onClick onClick) {
        this.onClick = onClick;
    }
}
