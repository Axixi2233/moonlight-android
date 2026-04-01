package com.limelight.ui.gamemenu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

import org.apmem.tools.layouts.FlowLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private FlowLayout flow_custom;
    private TextView tx_custom_title;

    private static final String PREFS_NAME = "CustomResolutions";
    private static final String KEY_RESOLUTIONS = "resolutions";

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        edt_width=v.findViewById(R.id.edt_width);
        edt_height=v.findViewById(R.id.edt_height);
        flow_custom = v.findViewById(R.id.flow_custom);
        tx_custom_title = v.findViewById(R.id.tx_custom_title);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);
        TextView tx=v.findViewWithTag("5");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = getActivity().getWindowManager().getCurrentWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            tx.setText(bounds.width()+"x"+bounds.height());
        }else{
            tx.setText(getResources().getDisplayMetrics().widthPixels+"x"+getResources().getDisplayMetrics().heightPixels);
        }
        for (int i = 0; i < 6; i++) {
            TextView textView=v.findViewWithTag(""+i);
            textView.setOnClickListener(v1 -> {
                String txt=textView.getText().toString().trim();
                String[] strings=txt.split("x");
                if(onClick==null){
                    return;
                }
                onClick.click(Integer.parseInt(strings[0]),Integer.parseInt(strings[1]));
                dismiss();
            });
        }
    }

    private void initViewData() {
        loadCustomResolutions();
    }

    private void loadCustomResolutions() {
        flow_custom.removeAllViews();
        SharedPreferences prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> resolutions = prefs.getStringSet(KEY_RESOLUTIONS, new HashSet<>());

        if (resolutions.isEmpty()) {
            tx_custom_title.setVisibility(View.GONE);
        } else {
            tx_custom_title.setVisibility(View.VISIBLE);
            List<String> list = new ArrayList<>(resolutions);
            // Sort them if needed, or keep order
            for (String res : list) {
                addResolutionToFlow(res);
            }
        }
    }

    private void addResolutionToFlow(String res) {
        TextView tv = (TextView) LayoutInflater.from(getActivity()).inflate(R.layout.layout_resolution_item, flow_custom, false);
        if (tv == null) {
            // Fallback if layout not found, though we should create it
            tv = new TextView(getActivity());
            FlowLayout.LayoutParams lp = new FlowLayout.LayoutParams(60, 28); // This is risky, better use dp
            lp.rightMargin = 4;
            lp.topMargin = 5;
            tv.setLayoutParams(lp);
        }
        tv.setText(res);
        tv.setOnClickListener(v -> {
            String[] strings = res.split("x");
            if (onClick != null) {
                onClick.click(Integer.parseInt(strings[0]), Integer.parseInt(strings[1]));
                dismiss();
            }
        });
        tv.setOnLongClickListener(v -> {
            showDeleteConfirmDialog(res);
            return true;
        });
        flow_custom.addView(tv);
    }

    private void showDeleteConfirmDialog(String res) {
        new AlertDialog.Builder(getActivity())
                .setTitle("确认删除")
                .setMessage("是否删除分辨率 " + res + "？")
                .setPositiveButton("确定", (dialog, which) -> {
                    deleteResolution(res);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteResolution(String res) {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> resolutions = new HashSet<>(prefs.getStringSet(KEY_RESOLUTIONS, new HashSet<>()));
        if (resolutions.remove(res)) {
            prefs.edit().putStringSet(KEY_RESOLUTIONS, resolutions).apply();
            loadCustomResolutions();
        }
    }

    private void saveResolution(String w, String h) {
        String res = w + "x" + h;
        SharedPreferences prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> resolutions = new HashSet<>(prefs.getStringSet(KEY_RESOLUTIONS, new HashSet<>()));
        if (resolutions.add(res)) {
            prefs.edit().putStringSet(KEY_RESOLUTIONS, resolutions).apply();
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
        if(v.getId()==R.id.ibtn_back){
            dismiss();
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
            
            saveResolution(width, height);
            
            if(onClick==null){
                dismiss();
                return;
            }
            onClick.click(Integer.parseInt(width),Integer.parseInt(height));
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

}
