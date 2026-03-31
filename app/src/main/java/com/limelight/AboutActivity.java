package com.limelight;

import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.utils.UpdateChecker;

public class AboutActivity extends BaseActivity implements View.OnClickListener {

    private TextView tvVersion;
    private ImageView ivLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        tvVersion = findViewById(cn.axi.gamepad.an.R.id.tv_version);
        ivLogo = findViewById(cn.axi.gamepad.an.R.id.iv_logo);
        findViewById(cn.axi.gamepad.an.R.id.iv_back).setOnClickListener(v -> finish());
        tvVersion.setText("版本号：" + BuildConfig.VERSION_NAME);

        ivLogo.setClipToOutline(true);
        ivLogo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 30f);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.iv_get) {
            UpdateChecker.checkForUpdates(this, true);
            return;
        }

        if(v.getId() == R.id.iv_res){
            UpdateChecker.openUrl(this,"https://pan.quark.cn/s/9a334d831290");
            return;
        }

        if (v.getId() == R.id.iv_douyin) {
            UpdateChecker.openUrl(this,"https://v.douyin.com/zm9GLKUfBW8/");
            return;
        }

        if (v.getId() == R.id.iv_xhs) {
            UpdateChecker.openUrl(this,"https://www.xiaohongshu.com/user/profile/5d21be61000000001600b878");
            return;
        }

        if (v.getId() == R.id.iv_bili) {
            UpdateChecker.openUrl(this,"https://space.bilibili.com/16893379");
            return;
        }

        if (v.getId() == R.id.iv_github) {
            UpdateChecker.openUrl(this,"https://axixi2233.github.io/");
            return;
        }

        if (v.getId() == R.id.lv_credits) {
            startActivity(new Intent(this, CreditsActivity.class));
        }
    }
}
