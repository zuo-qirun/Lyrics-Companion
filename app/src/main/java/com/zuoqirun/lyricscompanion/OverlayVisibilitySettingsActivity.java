package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

@SuppressLint("SetTextI18n")
public final class OverlayVisibilitySettingsActivity extends AppCompatActivity {
    private TextView usageAccessStatus;
    private MaterialButton usageAccessButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF07111F);
            getWindow().setNavigationBarColor(0xFF07111F);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("悬浮窗隐藏规则");
        toolbar.setSubtitle("规则命中时隐藏，条件解除后自动恢复");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(76)));

        LinearLayout rules = card("自动隐藏");
        addRuleToggle(rules, "暂停或停止播放后隐藏",
                "恢复播放时自动显示已启用的主屏悬浮窗和副屏歌词",
                AppPreferences.KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING,
                AppPreferences.hideOverlaysWhenNotPlaying(this), false);
        addRuleToggle(rules, "进入当前播放器后隐藏",
                "离开当前媒体会话所属的播放器后自动恢复",
                AppPreferences.KEY_HIDE_OVERLAYS_IN_PLAYER,
                AppPreferences.hideOverlaysInPlayer(this), true);
        addCard(root, rules);

        LinearLayout access = card("播放器前台识别");
        usageAccessStatus = text("", 14, 0xFFD8E1EE, false);
        usageAccessStatus.setPadding(0, dp(10), 0, dp(10));
        access.addView(usageAccessStatus);
        usageAccessButton = button("授权使用情况访问");
        usageAccessButton.setOnClickListener(v -> openUsageAccessSettings());
        access.addView(usageAccessButton, new LinearLayout.LayoutParams(-1, dp(48)));
        addCard(root, access);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
        refreshUsageAccessState();
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
        refreshUsageAccessState();
        if (AppPreferences.hideOverlaysInPlayer(this)) AppPreferences.changed(this);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void addRuleToggle(LinearLayout parent, String title, String subtitle, String key,
                               boolean initial, boolean needsUsageAccess) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title + "\n" + subtitle);
        toggle.setTextColor(0xFFF3F7FC);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(12), 0, dp(6));
        toggle.setLineSpacing(0f, 1.15f);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            AppPreferences.changed(this);
            if (checked && needsUsageAccess && !ForegroundAppDetector.hasUsageAccess(this)) {
                openUsageAccessSettings();
            }
        });
        parent.addView(toggle);
    }

    private void refreshUsageAccessState() {
        if (usageAccessStatus == null || usageAccessButton == null) return;
        boolean granted = ForegroundAppDetector.hasUsageAccess(this);
        if (Build.VERSION.SDK_INT < 21) {
            usageAccessStatus.setText("当前 Android 版本可直接识别前台播放器");
            usageAccessStatus.setTextColor(0xFF6EE7F2);
            usageAccessButton.setText("无需额外授权");
            usageAccessButton.setEnabled(false);
            return;
        }
        usageAccessStatus.setText(granted
                ? "使用情况访问已授权，可识别当前播放器是否在前台"
                : "使用情况访问未授权，进入播放器后隐藏暂不生效");
        usageAccessStatus.setTextColor(granted ? 0xFF6EE7F2 : 0xFFFFCA66);
        usageAccessButton.setText(granted ? "已授权" : "授权使用情况访问");
        usageAccessButton.setEnabled(!granted);
        usageAccessButton.setAlpha(granted ? 0.55f : 1f);
    }

    private void openUsageAccessSettings() {
        if (Build.VERSION.SDK_INT < 21) return;
        Intent direct = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (startSettingsActivity(direct)) return;
        if (startSettingsActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) return;
        if (startSettingsActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())))) return;
        Toast.makeText(this, "无法打开使用情况访问设置，请在系统设置中手动授权。",
                Toast.LENGTH_LONG).show();
    }

    private boolean startSettingsActivity(Intent intent) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) return false;
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(ColorStateList.valueOf(0xFF101E31));
        surface.setCornerSize(dp(20));
        card.setBackground(surface);
        card.addView(text(title, 13, 0xFF6EE7F2, true));
        return card;
    }

    private void addCard(LinearLayout root, LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(card, params);
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextSize(13f);
        button.setTextColor(0xFFF1F5FA);
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setBackgroundTintList(ColorStateList.valueOf(0xFF25364D));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
