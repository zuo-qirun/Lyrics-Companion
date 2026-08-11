package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class OverlayVisibilitySettingsActivity extends AppCompatActivity {
    private static final ExecutorService APP_LIST_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView usageAccessStatus;
    private MaterialButton usageAccessButton;
    private TextView hiddenAppsSummary;

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

        LinearLayout appRules = card("指定应用隐藏");
        hiddenAppsSummary = text("", 13, 0xFFD8E1EE, false);
        hiddenAppsSummary.setPadding(0, dp(10), 0, dp(10));
        appRules.addView(hiddenAppsSummary);
        MaterialButton chooseApps = button("选择应用");
        chooseApps.setOnClickListener(v -> showHiddenAppPicker());
        appRules.addView(chooseApps, new LinearLayout.LayoutParams(-1, dp(48)));
        addCard(root, appRules);

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
        refreshHiddenAppsSummary();
        refreshUsageAccessState();
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
        refreshUsageAccessState();
        if (AppPreferences.hideOverlaysInPlayer(this)
                || !AppPreferences.hiddenOverlayApps(this).isEmpty()) {
            AppPreferences.changed(this);
        }
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
                ? "使用情况访问已授权，可识别当前前台应用"
                : "使用情况访问未授权，按前台应用隐藏的规则暂不生效");
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

    private void showHiddenAppPicker() {
        LinearLayout loading = new LinearLayout(this);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        loading.setPadding(dp(24), dp(12), dp(24), dp(12));
        ProgressBar spinner = new ProgressBar(this);
        loading.addView(spinner, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView loadingText = text("正在读取已安装应用…", 14, 0xFFD8E1EE, false);
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(-2, -2);
        loadingTextParams.leftMargin = dp(14);
        loading.addView(loadingText, loadingTextParams);
        final androidx.appcompat.app.AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("选择应用")
                .setView(loading)
                .setNegativeButton("取消", null)
                .show();
        APP_LIST_EXECUTOR.execute(() -> {
            List<InstalledAppListCache.AppChoice> apps = InstalledAppListCache.load(this,
                    AppPreferences.hiddenOverlayApps(this));
            mainHandler.post(() -> {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                if (apps.isEmpty()) {
                    Toast.makeText(this, "未找到可选择的应用", Toast.LENGTH_SHORT).show();
                    return;
                }
                showLoadedHiddenAppPicker(apps);
            });
        });
    }

    private void showLoadedHiddenAppPicker(List<InstalledAppListCache.AppChoice> apps) {
        Set<String> selected = new HashSet<>(AppPreferences.hiddenOverlayApps(this));
        ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setBackgroundColor(0xFF101E31);
        AppChoiceListAdapter adapter = new AppChoiceListAdapter(this, apps, selected);
        list.setAdapter(adapter);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), 0, dp(4), 0);
        content.addView(appSearchField(adapter));
        content.addView(list, new LinearLayout.LayoutParams(-1, dp(440)));
        new MaterialAlertDialogBuilder(this)
                .setTitle("在哪些应用上隐藏歌词")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> saveHiddenApps(selected))
                .show();
    }

    private TextInputLayout appSearchField(AppChoiceListAdapter adapter) {
        TextInputLayout input = new TextInputLayout(this);
        input.setHint("搜索应用名称或包名");
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        input.setBoxBackgroundColor(0xFF17263A);
        input.setBoxStrokeColor(0xFF6EE7F2);
        input.setHintTextColor(ColorStateList.valueOf(0xFFA9B6C8));
        TextInputEditText editor = new TextInputEditText(this);
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT);
        editor.setTextColor(0xFFF3F7FC);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count,
                                                    int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before,
                                                int count) {
                adapter.setQuery(text == null ? "" : text.toString());
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        input.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(6), dp(4), dp(6), dp(8));
        input.setLayoutParams(params);
        return input;
    }

    private void saveHiddenApps(Set<String> packages) {
        AppPreferences.setHiddenOverlayApps(this, packages);
        AppPreferences.changed(this);
        refreshHiddenAppsSummary();
        if (!packages.isEmpty() && !ForegroundAppDetector.hasUsageAccess(this)) {
            openUsageAccessSettings();
        }
    }

    private void refreshHiddenAppsSummary() {
        if (hiddenAppsSummary == null) return;
        int count = AppPreferences.hiddenOverlayApps(this).size();
        hiddenAppsSummary.setText(count == 0
                ? "未选择应用，歌词不会因打开其它应用而隐藏"
                : "已选择 " + count + " 个应用，进入时自动隐藏，离开后恢复");
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
