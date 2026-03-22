package com.limelight.ui.gamemenu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameDisplayResolutionFragment extends BaseGameMenuDialog implements View.OnClickListener{
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_display_resolution;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    private EditText edt_width;
    private EditText edt_height;
    private LinearLayout layout_preset;
    private LinearLayout layout_custom;
    private View ll_custom_title;
    private Button btn_save;

    private static final String CUSTOM_RESOLUTIONS_KEY = "custom_resolutions_list";

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        edt_width=v.findViewById(R.id.edt_width);
        edt_height=v.findViewById(R.id.edt_height);
        layout_preset = v.findViewById(R.id.layout_preset);
        layout_custom = v.findViewById(R.id.layout_custom);
        ll_custom_title = v.findViewById(R.id.ll_custom_title);
        btn_save = v.findViewById(R.id.btn_save);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);
        btn_save.setOnClickListener(this);
    }

    private void initViewData() {
        refreshResolutions();
    }

    private void refreshResolutions() {
        layout_preset.removeAllViews();
        layout_custom.removeAllViews();

        // Preset Resolutions
        String[] presets = {
                PreferenceConfiguration.RES_360P,
                PreferenceConfiguration.RES_480P,
                PreferenceConfiguration.RES_720P,
                PreferenceConfiguration.RES_1080P,
                PreferenceConfiguration.RES_1440P,
                PreferenceConfiguration.RES_4K
        };

        for (String res : presets) {
            addResolutionView(layout_preset, res, false);
        }

        // Custom Resolutions
        String customResStr = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(CUSTOM_RESOLUTIONS_KEY, "");
        if (!TextUtils.isEmpty(customResStr)) {
            ll_custom_title.setVisibility(View.VISIBLE);
            String[] customResArray = customResStr.split(",");
            for (String res : customResArray) {
                if (!TextUtils.isEmpty(res)) {
                    addResolutionView(layout_custom, res, true);
                }
            }
        } else {
            ll_custom_title.setVisibility(View.GONE);
        }
    }

    private void addResolutionView(LinearLayout parent, final String res, boolean canDelete) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.item_game_menu_list, parent, false);
        TextView textView = view.findViewById(R.id.tx_title);
        textView.setText(res);
        textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_axi_screen, 0, 0, 0);
        
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] parts = res.split("x");
                if (parts.length == 2) {
                    edt_width.setText(parts[0]);
                    edt_height.setText(parts[1]);
                }
            }
        });

        if (canDelete) {
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showDeleteConfirmDialog(res);
                    return true;
                }
            });
        }

        parent.addView(view);
        
        // Add a small margin between items
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.bottomMargin = 4;
        view.setLayoutParams(params);
    }

    private void showDeleteConfirmDialog(final String res) {
        new AlertDialog.Builder(getActivity())
                .setTitle("确认删除")
                .setMessage("是否删除分辨率: " + res + "?")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteResolution(res);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteResolution(String res) {
        String customResStr = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(CUSTOM_RESOLUTIONS_KEY, "");
        List<String> list = new ArrayList<>(Arrays.asList(customResStr.split(",")));
        list.remove(res);
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (!TextUtils.isEmpty(list.get(i))) {
                sb.append(list.get(i));
                if (i < list.size() - 1) {
                    sb.append(",");
                }
            }
        }
        
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(CUSTOM_RESOLUTIONS_KEY, sb.toString())
                .apply();
        
        Toast.makeText(getActivity(), "已删除: " + res, Toast.LENGTH_SHORT).show();
        refreshResolutions();
    }

    private void saveResolution(String res) {
        if (!res.contains("x")) return;
        
        String customResStr = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(CUSTOM_RESOLUTIONS_KEY, "");
        if (customResStr.contains(res)) {
            Toast.makeText(getActivity(), "该分辨率已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!TextUtils.isEmpty(customResStr)) {
            customResStr += ",";
        }
        customResStr += res;
        
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(CUSTOM_RESOLUTIONS_KEY, customResStr)
                .apply();
        
        Toast.makeText(getActivity(), "已保存: " + res, Toast.LENGTH_SHORT).show();
        refreshResolutions();
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

        if(v.getId()==R.id.btn_save){
            String width=edt_width.getText().toString().trim();
            String height=edt_height.getText().toString().trim();
            if(TextUtils.isEmpty(width) || TextUtils.isEmpty(height)){
                Toast.makeText(getActivity(),"宽高不能为空！",Toast.LENGTH_SHORT).show();
                return;
            }
            saveResolution(width + "x" + height);
            return;
        }

        if(v.getId()==R.id.btn_right){
            String width=edt_width.getText().toString().trim();
            String height=edt_height.getText().toString().trim();
            if(TextUtils.isEmpty(width)){
                Toast.makeText(getActivity(),"宽度不能为空！",Toast.LENGTH_SHORT).show();
                return;
            }
            if(TextUtils.isEmpty(height)){
                Toast.makeText(getActivity(),"高度不能为空！",Toast.LENGTH_SHORT).show();
                return;
            }
            dismiss();
            if(onClick==null){
                return;
            }
            onClick.click(Integer.parseInt(width),Integer.parseInt(height));
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

}
