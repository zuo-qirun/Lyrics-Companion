package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
    private SettingsUi ui;
    private TextView hiddenAppsSummary;
    private TextView ignoredAppsSummary;
    /** 常驻的前台读取状态提示（issue #61）。 */
    private TextView foregroundStatus;
    /** 本页卡片内容的宿主：重建时只清空它，保留 toolbar、ScrollView 与滚动位置。 */
    private LinearLayout pageRoot;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuildPage();
    }

    /** 就地重建本页，替代 recreate()：规则开关状态都写在偏好里，重新读一遍即可。 */
    private void rebuildPage() {
        if (pageRoot == null) {
            setContentView(R.layout.activity_overlay_visibility);
            if (Build.VERSION.SDK_INT >= 21) {
                int background = ContextCompat.getColor(this, R.color.lc_background);
                getWindow().setStatusBarColor(background);
                getWindow().setNavigationBarColor(background);
            }

            MaterialToolbar toolbar = findViewById(R.id.overlay_toolbar);
            toolbar.setTitle("悬浮窗隐藏规则");
            toolbar.setSubtitle("规则命中时隐藏，条件解除后自动恢复");
            toolbar.setNavigationOnClickListener(v -> finish());
            pageRoot = findViewById(R.id.overlay_content);
        } else {
            // 以前这里是整页 setContentView：重建后 ScrollView 回到顶部，用户点了页面下方的
            // 「只在桌面显示」会突然被扔回页首。原地换掉卡片内容就不动滚动位置。
            pageRoot.removeAllViews();
        }

        LinearLayout root = pageRoot;
        ui = new SettingsUi(this);

        LinearLayout rules = ui.card(root, "自动隐藏");
        addRuleToggle(rules, "暂停或停止播放后隐藏",
                "恢复播放时自动显示已启用的主屏悬浮窗和副屏歌词",
                AppPreferences.KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING,
                AppPreferences.hideOverlaysWhenNotPlaying(this), false);
        addPauseGraceRow(rules);
        addRuleToggle(rules, "进入当前播放器后隐藏",
                "离开当前媒体会话所属的播放器后自动恢复",
                AppPreferences.KEY_HIDE_OVERLAYS_IN_PLAYER,
                AppPreferences.hideOverlaysInPlayer(this), true);
        // 无歌词 / 纯音乐自动隐藏（issue #67）：按屏各自开关。
        addTargetToggle(rules, "主屏：无歌词 / 纯音乐时隐藏",
                AppPreferences.KEY_HIDE_WHEN_NO_LYRICS, false,
                AppPreferences.hideWhenNoLyrics(this, false));
        addTargetToggle(rules, "副屏：无歌词 / 纯音乐时隐藏",
                AppPreferences.KEY_HIDE_WHEN_NO_LYRICS, true,
                AppPreferences.hideWhenNoLyrics(this, true));
        addTargetToggle(rules, "主屏：不显示「暂无匹配歌词」占位",
                AppPreferences.KEY_HIDE_NO_LYRIC_PLACEHOLDER, false,
                AppPreferences.hideNoLyricPlaceholder(this, false));
        addTargetToggle(rules, "副屏：不显示「暂无匹配歌词」占位",
                AppPreferences.KEY_HIDE_NO_LYRIC_PLACEHOLDER, true,
                AppPreferences.hideNoLyricPlaceholder(this, true));
        addTargetToggle(rules, "主屏：匹配期间歌词区留白", AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER,
                false, AppPreferences.hideMatchingPlaceholder(this, false));
        addTargetToggle(rules, "副屏：匹配期间歌词区留白", AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER,
                true, AppPreferences.hideMatchingPlaceholder(this, true));
        addGraceRow(rules);

        LinearLayout appRules = ui.card(root, "指定应用（黑名单 / 白名单）");
        addTargetToggle(appRules, "主屏启用这条规则", AppPreferences.KEY_HIDE_SELECTED_APPS_ON_MAIN,
                AppPreferences.hideSelectedAppsOnMain(this));
        addModeToggle(appRules, "主屏改为白名单：只在名单内的应用里显示", false);
        addTargetToggle(appRules, "副屏启用这条规则", AppPreferences.KEY_HIDE_SELECTED_APPS_ON_SECONDARY,
                AppPreferences.hideSelectedAppsOnSecondary(this));
        addModeToggle(appRules, "副屏改为白名单：只在名单内的应用里显示", true);
        hiddenAppsSummary = addSummaryRow(appRules);
        ui.actionButton(appRules, "选择应用", v -> showAppPicker(PICK_HIDDEN_APPS));
        ui.actionButton(appRules, "只在桌面显示（白名单里只放桌面）",
                v -> applyDesktopOnlyRule());
        ui.note(appRules, "黑名单：名单内隐藏。白名单：只在名单内显示，识别不到前台应用时也隐藏。"
                + "两者都依赖「使用情况访问」权限，未授权时白名单不生效。");
        // 常驻提示（issue #61）：读不到前台应用时必须说清原因，否则用户只会以为规则坏了。
        foregroundStatus = addSummaryRow(appRules);
        foregroundStatus.setTextSize(12f);
        foregroundStatus.setTextColor(ContextCompat.getColor(this, R.color.lc_tertiary));

        LinearLayout ignored = ui.card(root, "忽略这些应用的媒体元数据");
        ignoredAppsSummary = addSummaryRow(ignored);
        ui.actionButton(ignored, "选择要忽略的应用", v -> showAppPicker(PICK_IGNORED_PLAYERS));
        ui.actionButton(ignored, "加入蓝牙音频（com.android.bluetooth）",
                v -> addIgnoredBluetooth());
        ui.note(ignored, "被忽略的应用不再更新歌词状态（车机媒体中心、导航或蓝牙通道抢歌词时用）。"
                + "只影响它们发布给本应用的元数据。");
        // 东风车机的常驻会话会把活跃位按死，酷我之类的播放器出不来的话用这个开关（issue #75）。
        ui.addToggle(ignored, "东风播放器会话始终优先",
                "关闭时：它长时间没有换歌 / 状态变化就让位给正在播放的其它播放器",
                AppPreferences.dftcAlwaysPreferred(this), (button, checked) -> {
                    AppPreferences.get(this).edit()
                            .putBoolean(AppPreferences.KEY_DFTC_ALWAYS_PREFERRED, checked).apply();
                    AppPreferences.changed(this);
                });

        CustomFontStore.applyToViewTree(this, findViewById(R.id.overlay_root));
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
        ui.addToggle(parent, title, subtitle, initial, (button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            AppPreferences.changed(this);
            if (checked && needsUsageAccess && !ForegroundAppDetector.hasUsageAccess(this)) {
                SafeToast.show(this, "请在首页“使用权限”中授权使用情况访问",
                        Toast.LENGTH_LONG);
            }
        });
    }

    private void addTargetToggle(LinearLayout parent, String title, String key, boolean initial) {
        addTargetToggle(parent, title, key, false, initial);
    }

    /** 按屏开关（issue #67 的无歌词隐藏也用它）：写这一屏的 slot 文件，主 / 副屏各一份。 */
    private void addTargetToggle(LinearLayout parent, String title, String key, boolean secondary,
                                 boolean initial) {
        ui.addToggle(parent, title, null, initial, (button, checked) -> {
            if (secondary || isPerScreenKey(key)) {
                AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            } else {
                AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            }
            AppPreferences.changed(this);
            refreshHiddenAppsSummary();
        });
    }

    /** 这些键是按屏存的（主 / 副屏各一份），其余是本页原有的全局开关。 */
    private static boolean isPerScreenKey(String key) {
        return AppPreferences.KEY_HIDE_WHEN_NO_LYRICS.equals(key)
                || AppPreferences.KEY_HIDE_NO_LYRIC_PLACEHOLDER.equals(key);
    }

    /** 「无歌词多久后隐藏」滑杆（issue #67）：主 / 副屏写同一个值，省得两地各有各的宽限期。 */
    private void addGraceRow(LinearLayout parent) {
        int seconds = AppPreferences.noLyricGraceMs(this, false) / 1_000;
        ui.addSeek(parent, "无歌词多久后隐藏", NoLyricVisibilityRules.MAX_GRACE_MS / 1_000,
                seconds, " 秒", progress -> {
                    AppPreferences.putDisplayInt(
                            OverlayVisibilitySettingsActivity.this, false,
                            AppPreferences.KEY_NO_LYRIC_GRACE_MS, progress * 1_000);
                    AppPreferences.putDisplayInt(
                            OverlayVisibilitySettingsActivity.this, true,
                            AppPreferences.KEY_NO_LYRIC_GRACE_MS, progress * 1_000);
                    AppPreferences.changed(OverlayVisibilitySettingsActivity.this);
                });
        ui.note(parent, "切歌匹配期间不会闪");
    }

    private void addPauseGraceRow(LinearLayout parent) {
        ui.addSeek(parent, "暂停多久后隐藏", 60,
                (int) (AppPreferences.pauseHideGraceMs(this) / 1000L), " 秒", progress -> {
                    AppPreferences.get(OverlayVisibilitySettingsActivity.this).edit()
                            .putInt(AppPreferences.KEY_PAUSE_HIDE_GRACE_SECONDS, progress)
                            .apply();
                    AppPreferences.changed(OverlayVisibilitySettingsActivity.this);
                });
    }

    /** 某一屏的规则方向：黑名单（名单内隐藏）或白名单（只在名单内显示，issue #43）。 */
    private void addModeToggle(LinearLayout parent, String title, boolean secondary) {
        ui.addToggle(parent, title, null, AppPreferences.appRuleWhitelist(this, secondary),
                (button, checked) -> {
                    if (checked == AppPreferences.appRuleWhitelist(this, secondary)) return;
                    Runnable apply = () -> {
                        AppPreferences.setAppRuleWhitelist(this, secondary, checked);
                        AppPreferences.changed(this);
                        refreshHiddenAppsSummary();
                        if (checked && !ForegroundAppDetector.hasUsageAccess(this)) {
                            SafeToast.show(this, "白名单需要「使用情况访问」权限：未授权时它不会生效，"
                                    + "请到首页“使用权限”里授予", Toast.LENGTH_LONG);
                        }
                    };
                    if (AppPreferences.hiddenOverlayApps(this).isEmpty()) {
                        apply.run();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(checked ? "切换为白名单" : "切换为黑名单")
                            .setMessage(checked
                                    ? "名单将变为「只在这些应用显示」。名单由主副屏共用，清空也会影响另一屏。"
                                    : "名单将变为「在这些应用隐藏」；如果包含桌面，桌面歌词会隐藏。"
                                    + "名单由主副屏共用，清空也会影响另一屏。")
                            .setPositiveButton("保留名单并切换", (dialog, which) -> apply.run())
                            .setNeutralButton("清空名单并切换", (dialog, which) -> {
                                AppPreferences.setHiddenOverlayApps(this, Collections.emptySet());
                                apply.run();
                            })
                            .setNegativeButton("取消", (dialog, which) -> button.setChecked(!checked))
                            .setOnCancelListener(dialog -> button.setChecked(!checked))
                            .show();
                });
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
                rebuildPage();
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
        loading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        loading.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12));
        ProgressBar spinner = new ProgressBar(this);
        loading.addView(spinner, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        TextView loadingText = new TextView(this);
        loadingText.setText("正在读取已安装应用…");
        loadingText.setTextSize(14f);
        loadingText.setTextColor(ContextCompat.getColor(this, R.color.lc_on_surface_variant));
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(-2, -2);
        loadingTextParams.leftMargin = ui.dp(14);
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
        list.setBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface_container_low));
        AppChoiceListAdapter adapter = new AppChoiceListAdapter(this, apps, selected);
        list.setAdapter(adapter);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        content.addView(appSearchField(adapter));
        ui.actionButton(content, "手动添加包名（含无桌面入口应用）",
                v -> addInstalledPackage(adapter));
        content.addView(list, new LinearLayout.LayoutParams(-1, ui.dp(440)));
        new MaterialAlertDialogBuilder(this)
                .setTitle(kind == PICK_IGNORED_PLAYERS ? "忽略哪些应用的媒体元数据"
                        : "在哪些应用上隐藏歌词")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> savePickedApps(kind, selected))
                .show();
    }

    private void addInstalledPackage(AppChoiceListAdapter adapter) {
        TextInputEditText editor = new TextInputEditText(this);
        editor.setSingleLine(true); editor.setHint("例如 com.example.voice");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(8));
        noteView(content, "输入已安装应用的准确包名；系统应用和没有桌面入口的应用也可添加。");
        content.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("添加已安装应用").setView(content).setNegativeButton("取消", null)
                .setPositiveButton("添加", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
            InstalledAppListCache.AppChoice app = InstalledAppListCache.resolveInstalledPackage(this,
                    editor.getText() == null ? "" : editor.getText().toString());
            if (app == null) { editor.setError("未找到此包名，或不能选择歌词伴侣自身"); return; }
            adapter.addSelected(app); dialog.dismiss();
        }));
        dialog.show();
    }

    private TextInputLayout appSearchField(AppChoiceListAdapter adapter) {
        TextInputLayout input = new TextInputLayout(this);
        input.setHint("搜索应用名称或包名");
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        input.setBoxBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface_container_lowest));
        input.setBoxStrokeColor(ContextCompat.getColor(this, R.color.lc_primary));
        input.setHintTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.lc_on_surface_variant)));
        TextInputEditText editor = new TextInputEditText(this);
        editor.setSingleLine(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT);
        editor.setTextColor(ContextCompat.getColor(this, R.color.lc_on_surface));
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
        params.setMargins(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(8));
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
                + (whitelistInUse ? "白名单下离开名单就隐藏歌词"
                : "进入名单里的应用时隐藏歌词，离开后恢复");
        if (!rule.isEmpty() && !ForegroundAppDetector.hasUsageAccess(this)) {
            summary += "\n⚠ 未授权使用情况访问，指定应用规则不会生效。";
        }
        hiddenAppsSummary.setText(summary);
        refreshForegroundStatus();
    }

    /**
     * 常驻的前台读取状态（issue #61）：读不到前台应用时黑名单/白名单都会「看起来没生效」，
     * 这里把具体原因写清楚，并提示白名单未授权时会一直不显示。
     */
    private void refreshForegroundStatus() {
        if (foregroundStatus == null) return;
        ForegroundAppDetector.Probe probe = ForegroundAppDetector.probe(this);
        long ageMs = probe.lastEventWallTimeMs <= 0L ? -1L
                : System.currentTimeMillis() - probe.lastEventWallTimeMs;
        String line = ForegroundProbeDescription.describe(probe.reason, probe.packageName,
                probe.lastEventType, ageMs);
        String impact = ForegroundProbeDescription.ruleImpact(probe.reason,
                AppPreferences.appRuleWhitelist(this, false)
                        || AppPreferences.appRuleWhitelist(this, true));
        boolean whitelist = AppPreferences.appRuleWhitelist(this, false)
                || AppPreferences.appRuleWhitelist(this, true);
        if (whitelist && !ForegroundAppDetector.hasUsageAccess(this)) {
            impact = "⚠ 未授权「使用情况访问」：白名单不生效，歌词会一直不显示。";
        }
        foregroundStatus.setText(impact.isEmpty() ? line : line + "\n" + impact);
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

    /** 名单/状态这类段落文字：比普通 note 亮一点、大一点，仍走主题排版。 */
    private TextView addSummaryRow(LinearLayout parent) {
        TextView view = noteView(parent, "");
        view.setTextSize(13f);
        view.setTextColor(ContextCompat.getColor(this, R.color.lc_on_surface));
        return view;
    }

    private TextView noteView(LinearLayout parent, CharSequence text) {
        TextView view = (TextView) getLayoutInflater()
                .inflate(R.layout.item_setting_note, parent, false);
        view.setText(text);
        parent.addView(view);
        return view;
    }

}
