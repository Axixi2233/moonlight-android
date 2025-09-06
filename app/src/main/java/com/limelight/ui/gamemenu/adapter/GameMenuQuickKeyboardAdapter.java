package com.limelight.ui.gamemenu.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import java.util.List;

/**
 * Description
 * Date: 2024-10-20
 * Time: 20:50
 */
public class GameMenuQuickKeyboardAdapter extends BaseAdapter {

    private List<GameMenuQuickBean> datas;

    private Context context;

    //0-快捷键 1-鼠标模式切换 2--组合键
    private int type;

    public GameMenuQuickKeyboardAdapter(Context context,List<GameMenuQuickBean> datas) {
        this.datas = datas;
        this.context=context;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setDatas(List<GameMenuQuickBean> datas) {
        this.datas = datas;
    }

    @Override
    public int getCount() {
        return datas==null?0:datas.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if (convertView == null) {
            convertView=LayoutInflater.from(context).inflate(R.layout.item_game_menu_list, null, false);
            holder = new ViewHolder();
            holder.tx_title = convertView.findViewById(R.id.tx_title);
            //将holder放入当前视图中
            convertView.setTag(holder);
        }else {
            //复用holder
            holder = (ViewHolder) convertView.getTag();
        }
        if(type==2){
            holder.tx_title.setText(datas.get(position).getName()+"\t【"+datas.get(position).getDesc()+"】");
        }else{
            if(type==0&& !TextUtils.isEmpty(datas.get(position).getDesc())){
                holder.tx_title.setText(datas.get(position).getName()+"\t【"+datas.get(position).getDesc()+"】");
                holder.tx_title.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
            }else{
                holder.tx_title.setText(datas.get(position).getName());
                holder.tx_title.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
            }
        }
        int resId=-1;
        if(type==1){
            resId=R.drawable.ic_axi_touch;
        }
        if(type==2){
            resId=R.drawable.ic_axi_keyboard_list;
        }
        if(resId!=-1){
            Drawable drawable = convertView.getResources().getDrawable(resId);
            drawable.setBounds(0, 0, drawable.getMinimumWidth(),
                    drawable.getMinimumHeight());
            holder.tx_title.setCompoundDrawables(drawable, null,null, null);
        }
        return convertView;
    }

    class ViewHolder {
        TextView tx_title;
    }
}
