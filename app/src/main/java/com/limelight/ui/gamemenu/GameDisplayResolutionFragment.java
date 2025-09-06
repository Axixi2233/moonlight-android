package com.limelight.ui.gamemenu;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

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

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        edt_width=v.findViewById(R.id.edt_width);
        edt_height=v.findViewById(R.id.edt_height);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        initViewData();
        ibtn_back.setOnClickListener(this);
        v.findViewById(R.id.btn_right).setOnClickListener(this);
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
