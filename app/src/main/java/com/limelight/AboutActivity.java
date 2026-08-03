package com.limelight;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import com.limelight.ui.AppDialog;
import com.limelight.utils.UpdateChecker;

import static com.limelight.utils.DeviceUtils.isTablet;

public class AboutActivity extends BaseActivity implements View.OnClickListener {

    private TextView tvVersion;
    private ImageView ivLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        tvVersion = findViewById(R.id.tv_version);
        ivLogo = findViewById(R.id.iv_logo);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.iv_coffee).setOnClickListener(v -> showSponsoredQrDialog(this));
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

        if (v.getId() == R.id.iv_help){
            UpdateChecker.openUrl(this,"https://www.axixi.top/help.html");
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
            UpdateChecker.openUrl(this,"https://www.axixi.top/");
            return;
        }

        if (v.getId() == R.id.lv_other_app) {
            UpdateChecker.openUrl(this,"https://www.axixi.top/");
            return;
        }

        if (v.getId() == R.id.lv_credits) {
            startActivity(new Intent(this, CreditsActivity.class));
        }
    }

    public static void showSponsoredQrDialog(Activity activity) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_sponsored_qr, null, false);
        ImageView qrView = dialogView.findViewById(R.id.iv_sponsored_qr);
        AlertDialog dialog = AppDialog.createCustomDialog(activity, dialogView, true);
        if (dialog == null) {
            return;
        }
        View closeButton = dialogView.findViewById(R.id.bt_sponsored_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        boolean isLandscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int qrMaxHeight = isLandscape
                ? Math.min(dp(activity,240), Math.round(screenHeight * 0.42f))
                : Math.min(dp(activity,420), Math.round(screenHeight * 0.52f));
        qrView.setMaxHeight(qrMaxHeight);

        float widthRatio = isTablet() ? 0.5f : 0.66f;
        int maxDialogWidth = isTablet() ? dp(activity,460) : dp(activity,380);
        AppDialog.showCustomDialog(activity, dialog, widthRatio, isTablet() ? 460 : 380,
                closeButton, closeButton, closeButton);
        if (dialog.getWindow() != null) {
            int dialogWidth = Math.min(
                    Math.round(activity.getResources().getDisplayMetrics().widthPixels * widthRatio),
                    maxDialogWidth);
            int dialogHeight = isLandscape
                    ? Math.round(screenHeight * 0.88f)
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setGravity(Gravity.CENTER);
            dialog.getWindow().setLayout(dialogWidth, dialogHeight);
        }
    }

    public static int dp(Context context,int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
