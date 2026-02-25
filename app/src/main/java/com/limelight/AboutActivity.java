package com.limelight;

import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends BaseActivity implements View.OnClickListener {

    private TextView tv_version;

    private ImageView iv_logo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        tv_version=findViewById(cn.axi.gamepad.an.R.id.tv_version);
        iv_logo=findViewById(cn.axi.gamepad.an.R.id.iv_logo);
        findViewById(cn.axi.gamepad.an.R.id.iv_back).setOnClickListener(v -> finish());
        tv_version.setText("版本号："+ BuildConfig.VERSION_NAME);
        // 开启裁剪
        iv_logo.setClipToOutline(true);
        iv_logo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // 设置圆角矩形轮廓：(左, 上, 右, 下, 半径)
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 30f);
            }
        });
    }


    @Override
    public void onClick(View v) {
        if(v.getId()== cn.axi.gamepad.an.R.id.iv_get){
            getToUrl("https://pan.quark.cn/s/9a334d831290");
            return;
        }

        if(v.getId()== cn.axi.gamepad.an.R.id.iv_douyin){
            getToUrl("https://v.douyin.com/zm9GLKUfBW8/");
            return;
        }

        if(v.getId()== cn.axi.gamepad.an.R.id.iv_xhs){
            getToUrl("https://www.xiaohongshu.com/user/profile/5d21be61000000001600b878");
            return;
        }
        if(v.getId()== cn.axi.gamepad.an.R.id.iv_bili){
            getToUrl("https://space.bilibili.com/16893379");
            return;
        }
    }

    private void getToUrl(String url){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        // 检查是否有应用能处理这个 Intent
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // 提示用户未找到浏览器，或使用 WebView 加载
            Toast.makeText(this, "未找到可用浏览器", Toast.LENGTH_SHORT).show();
        }
    }

}