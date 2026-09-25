package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class OverlayVisibilitySettingsActivity extends AppCompatActivity {
    private static final ExecutorService APP_LIST_EXECUTOR = Executors.newSingleThreadExecutor();
    /** 应用选择器的两种用途：隐藏规则名单，与忽略元数据的名单（issue #35）。 */
    private static final int PICK_HIDDEN_APPS = 0;
    private static final int PICK_IGNORED_PLAYERS = 1;
    /** 蓝牙 AVRCP 的"来源包名"，它是通道而不是普通播放器。 */
    private static final String BLUETOOTH_PACKAGE = "com.android.bluetooth";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView hiddenAppsSummary;
    private TextView ignoredAppsSummary;

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

        LinearLayout appRules = card("指定应用（黑名单 / 白名单）");
        addTargetToggle(appRules, "主屏启用这条规则", AppPreferences.KEY_HIDE_SELECTED_APPS_ON_MAIN,
                AppPreferences.hideSelectedAppsOnMain(this));
        addModeToggle(appRules, "主屏改为白名单：只在名单内的应用里显示", false);
        addTargetToggle(appRules, "副屏启用这条规则", AppPreferences.KEY_HIDE_SELECTED_APPS_ON_SECONDARY,
                AppPreferences.hideSelectedAppsOnSecondary(this));
        addModeToggle(appRules, "副屏改为白名单：只在名单内的应用里显示", true);
        hiddenAppsSummary = text("", 13, 0xFFD8E1EE, false);
        hiddenAppsSummary.setPadding(0, dp(10), 0, dp(10));
        hiddenAppsSummary.setLineSpacing(0f, 1.2f);
        appRules.addView(hiddenAppsSummary);
        MaterialButton chooseApps = button("选择应用");
        chooseApps.setOnClickListener(v -> showAppPicker(PICK_HIDDEN_APPS));
        appRules.addView(chooseApps, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialButton desktopOnly = button("只在桌面显示（白名单里只放桌面）");
        desktopOnly.setOnClickListener(v -> applyDesktopOnlyRule());
        LinearLayout.LayoutParams desktopParams = new LinearLayout.LayoutParams(-1, dp(48));
        desktopParams.topMargin = dp(8);
        appRules.addView(desktopOnly, desktopParams);
        TextView appRuleNote = text("黑名单：进入名单内的应用时隐藏。白名单：只在名单内的应用里显示，"
                + "离开这些应用、或系统识别不到前台应用时都隐藏——车机桌面常常不发前台事件，白名单"
                + "因此不需要识别桌面就能做到「只在桌面显示」。两种模式都依赖「使用情况访问」权限："
                + "完全没有授权时白名单不会生效（否则会一直隐藏歌词），设置页与诊断日志都会提示。", 12,
                0xFF8392A8, false);
        appRuleNote.setPadding(0, dp(8), 0, 0);
        appRuleNote.setLineSpacing(0f, 1.2f);
        appRules.addView(appRuleNote);
        addCard(root, appRules);

        LinearLayout ignored = card("忽略这些应用的媒体元数据");
        ignoredAppsSummary = text("", 13, 0xFFD8E1EE, false);
        ignoredAppsSummary.setPadding(0, dp(10), 0, dp(10));
        ignoredAppsSummary.setLineSpacing(0f, 1.2f);
        ignored.addView(ignoredAppsSummary);
        MaterialButton chooseIgnored = button("选择要忽略的应用");
        chooseIgnored.setOnClickListener(v -> showAppPicker(PICK_IGNORED_PLAYERS));
        ignored.addView(chooseIgnored, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialButton ignoreBluetooth = button("加入蓝牙音频（com.android.bluetooth）");
        ignoreBluetooth.setOnClickListener(v -> addIgnoredBluetooth());
        LinearLayout.LayoutParams bluetoothParams = new LinearLayout.LayoutParams(-1, dp(48));
        bluetoothParams.topMargin = dp(8);
        ignored.addView(ignoreBluetooth, bluetoothParams);
        TextView ignoredNote = text("被忽略的应用不再更新歌词状态：车机自带媒体中心、导航或蓝牙通道"
                + "抢走歌词时用得上。只影响它们发布给歌词伴侣的元数据，不影响这些应用自己的播放。"
                + "蓝牙 AVRCP 与 CarPlay/媒体会话互相抢占时，也可以把蓝牙这条通道加进来。", 12,
                0xFF8392A8, false);
        ignoredNote.setPadding(0, dp(8), 0, 0);
        ignoredNote.setLineSpacing(0f, 1.2f);
        ignored.addView(ignoredNote);
        addCard(root, ignored);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
        refreshHiddenAppsSummary();
        refreshIgnoredAppsSummary();
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
        refreshHiddenAppsSummary();
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
                SafeToast.show(this, "请在首页“使用权限”中授权使用情况访问",
                        Toast.LENGTH_LONG);
            }
        });
        parent.addView(toggle);
    }

    private void addTargetToggle(LinearLayout parent, String title, String key, boolean initial) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF3F7FC);
        toggle.setTextSize(14f);
        toggle.setPadding(0, dp(8), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            AppPreferences.changed(this);
            refreshHiddenAppsSummary();
        });
        parent.addView(toggle);
    }

    /** 某一屏的规则方向：黑名单（名单内隐藏）或白名单（只在名单内显示，issue #43）。 */
    private void addModeToggle(LinearLayout parent, String title, boolean secondary) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF3F7FC);
        toggle.setTextSize(14f);
        toggle.setPadding(0, dp(6), 0, 0);
        toggle.setChecked(AppPreferences.appRuleWhitelist(this, secondary));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setAppRuleWhitelist(this, secondary, checked);
            AppPreferences.changed(this);
            refreshHiddenAppsSummary();
            if (checked && !ForegroundAppDetector.hasUsageAccess(this)) {
                SafeToast.show(this, "白名单需要「使用情况访问」权限：未授权时它不会生效，"
                        + "请到首页“使用权限”里授予", Toast.LENGTH_LONG);
            }
        });
        parent.addView(toggle);
    }

    /**
     * issue #28：白名单里只放系统桌面，就是「只在桌面显示歌词」。桌面包名靠 {@code CATEGORY_HOME}
     * 查出来（普通启动器查询看不到它），与「选择应用」里的「（桌面）」标记同源。
     */
    private void applyDesktopOnlyRule() {
        APP_LIST_EXECUTOR.execute(() -> {
            List<InstalledAppListCache.AppChoice> apps = InstalledAppListCache.load(this,
                    Collections.emptySet(), true);
            Set<String> desktops = new LinkedHashSet<>();
            for (InstalledAppListCache.AppChoice app : apps) {
                if (app.desktop) desktops.add(app.packageName);
            }
            mainHandler.post(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                if (desktops.isEmpty()) {
                    SafeToast.show(this, "没识别到系统桌面，请改用「选择应用」手动添加",
                            Toast.LENGTH_LONG);
                    return;
                }
                AppPreferences.setHiddenOverlayApps(this, desktops);
                AppPreferences.get(this).edit()
                        .putBoolean(AppPreferences.KEY_HIDE_SELECTED_APPS_ON_MAIN, true)
                        .putBoolean(AppPreferences.KEY_HIDE_SELECTED_APPS_ON_SECONDARY, true)
                        .apply();
                AppPreferences.setAppRuleWhitelist(this, false, true);
                AppPreferences.setAppRuleWhitelist(this, true, true);
                AppPreferences.changed(this);
                SafeToast.show(this, "已设为「只在桌面显示」：白名单 = 系统桌面（" + desktops.size()
                        + " 个）", Toast.LENGTH_LONG);
                recreate();
            });
        });
    }

    /** 蓝牙 AVRCP 是一个"通道"而不是普通播放器，用包名加入忽略名单（issue #35）。 */
    private void addIgnoredBluetooth() {
        Set<String> ignored = AppPreferences.ignoredPlayerPackages(this);
        if (!ignored.add(BLUETOOTH_PACKAGE)) {
            SafeToast.show(this, "蓝牙音频已经在忽略名单里", Toast.LENGTH_SHORT);
            return;
        }
        AppPreferences.setIgnoredPlayerPackages(this, ignored);
        AppPreferences.changed(this);
        refreshIgnoredAppsSummary();
        SafeToast.show(this, "已忽略蓝牙音频的媒体元数据", Toast.LENGTH_LONG);
    }

    private void showAppPicker(int kind) {
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
                .setTitle(kind == PICK_IGNORED_PLAYERS ? "选择要忽略的应用" : "选择应用")
                .setView(loading)
                .setNegativeButton("取消", null)
                .show();
        APP_LIST_EXECUTOR.execute(() -> {
            // 忽略名单只关心「谁在发布媒体元数据」，桌面不会发布，所以不必列出来。
            List<InstalledAppListCache.AppChoice> apps = InstalledAppListCache.load(this,
                    pickedPackages(kind), kind != PICK_IGNORED_PLAYERS);
            mainHandler.post(() -> {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                if (apps.isEmpty()) {
                    SafeToast.show(this, "未找到可选择的应用", Toast.LENGTH_SHORT);
                    return;
                }
                showLoadedAppPicker(kind, apps);
            });
        });
    }

    private Set<String> pickedPackages(int kind) {
        return kind == PICK_IGNORED_PLAYERS ? AppPreferences.ignoredPlayerPackages(this)
                : AppPreferences.hiddenOverlayApps(this);
    }

    private void showLoadedAppPicker(int kind, List<InstalledAppListCache.AppChoice> apps) {
        Set<String> selected = new HashSet<>(pickedPackages(kind));
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
                .setTitle(kind == PICK_IGNORED_PLAYERS ? "忽略哪些应用的媒体元数据"
                        : "在哪些应用上隐藏歌词")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> savePickedApps(kind, selected))
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

    private void savePickedApps(int kind, Set<String> packages) {
        if (kind == PICK_IGNORED_PLAYERS) {
            AppPreferences.setIgnoredPlayerPackages(this, packages);
            AppPreferences.changed(this);
            refreshIgnoredAppsSummary();
            return;
        }
        AppPreferences.setHiddenOverlayApps(this, packages);
        AppPreferences.changed(this);
        refreshHiddenAppsSummary();
        if (!packages.isEmpty() && !ForegroundAppDetector.hasUsageAccess(this)) {
            SafeToast.show(this, "请在首页“使用权限”中授权使用情况访问",
                    Toast.LENGTH_LONG);
        }
    }

    private void refreshHiddenAppsSummary() {
        if (hiddenAppsSummary == null) return;
        int count = AppPreferences.hiddenOverlayApps(this).size();
        String main = describeRule(false);
        String secondary = describeRule(true);
        String rule = main.isEmpty() ? secondary
                : secondary.isEmpty() ? main : main + "、" + secondary;
        boolean whitelistInUse = AppPreferences.appRuleWhitelist(this, false)
                || AppPreferences.appRuleWhitelist(this, true);
        String summary = count == 0
                ? "未选择应用，歌词不会因打开其它应用而隐藏"
                : rule.isEmpty() ? "已选择 " + count + " 个应用，但主屏与副屏都还没启用这条规则"
                : "已选择 " + count + " 个应用（" + rule + "）。"
                + (whitelistInUse ? "白名单下离开名单里的应用就隐藏歌词，识别不到前台应用时也隐藏"
                : "进入名单里的应用时隐藏歌词，离开后恢复");
        if (!rule.isEmpty() && !ForegroundAppDetector.hasUsageAccess(this)) {
            summary += "\n⚠ 未授权使用情况访问，指定应用规则不会生效；请到首页「使用权限」授权。";
        }
        hiddenAppsSummary.setText(summary);
    }

    /** 「主屏白名单」/「副屏黑名单」；该屏没启用这条规则时是空串。 */
    private String describeRule(boolean secondary) {
        boolean enabled = secondary ? AppPreferences.hideSelectedAppsOnSecondary(this)
                : AppPreferences.hideSelectedAppsOnMain(this);
        if (!enabled) return "";
        return (secondary ? "副屏" : "主屏")
                + (AppPreferences.appRuleWhitelist(this, secondary) ? "白名单" : "黑名单");
    }

    private void refreshIgnoredAppsSummary() {
        if (ignoredAppsSummary == null) return;
        Set<String> ignored = AppPreferences.ignoredPlayerPackages(this);
        ignoredAppsSummary.setText(ignored.isEmpty()
                ? "未忽略任何应用，所有播放器与通道的元数据都会更新歌词"
                : "已忽略 " + ignored.size() + " 个应用/通道：" + joinPackages(ignored)
                + "。它们不再更新歌词状态（蓝牙通道用包名 com.android.bluetooth）");
    }

    private static String joinPackages(Set<String> packages) {
        StringBuilder joined = new StringBuilder();
        for (String packageName : new java.util.TreeSet<>(packages)) {
            if (joined.length() > 0) joined.append("、");
            joined.append(packageName);
        }
        return joined.toString();
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
