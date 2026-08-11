package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CUSTOM_FONT = 2417;
    private static final int REQUEST_RECORD_AUDIO = 2418;
    private static final String UPDATE_MANIFEST_URL =
            "https://lyrics-companion.zuoqirun.top/update.json";
    private static final String UPDATE_HISTORY_URL =
            "https://lyrics-companion.zuoqirun.top/versions";
    private static final String SOURCE_REPOSITORY_URL =
            "https://github.com/zuo-qirun/Lyrics-Companion";
    private static final String REFINED_REPOSITORY_URL =
            "https://github.com/solstice23/refined-now-playing-netease";
    private static final String PIPWINDOW_REPOSITORY_URL =
            "https://github.com/Lukoning/PiPWindow";
    private static final String AMLL_REPOSITORY_URL =
            "https://github.com/amll-dev/applemusic-like-lyrics";
    private static final String THIRD_PARTY_NOTICES_URL =
            SOURCE_REPOSITORY_URL + "/blob/main/THIRD_PARTY_NOTICES.md";
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService APP_ICON_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final long LISTENER_HEALTH_MAX_AGE_MS = 3_000L;
    private static final long LISTENER_INITIAL_RECONNECT_DELAY_MS = 2_500L;
    private static final long LISTENER_RECONNECT_INTERVAL_MS = 1_000L;
    private static final long LISTENER_RECONNECT_WINDOW_MS = 30_000L;
    private static final int PERMISSION_CHECK_NOTIFICATION = 1;
    private static final int PERMISSION_CHECK_OVERLAY = 1 << 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView permissionStatus;
    private TextView musicStatus;
    private TextView displayStatus;
    private TextView updateStatus;
    private TextView onlineStatus;
    private TextView feedbackReplyStatus;
    private MaterialSwitch mainOverlaySwitch;
    private MaterialSwitch secondaryOverlaySwitch;
    private MaterialSwitch launchOverlaySwitch;
    private MaterialSwitch autoStartSwitch;
    private MaterialButton mainRefinedSettingsButton;
    private MaterialButton secondaryRefinedSettingsButton;
    private MaterialButton mainCompactSettingsButton;
    private MaterialButton secondaryCompactSettingsButton;
    private Spinner displaySpinner;
    private LyricsPanelView previewPanel;
    private TextView globalFontSummary;
    private boolean bindingUi;
    private boolean updateBusy;
    private boolean onlineBusy;
    private boolean feedbackBusy;
    private boolean diagnosticBusy;
    private boolean feedbackReplyDialogVisible;
    private boolean activityResumed;
    private boolean stoppingAndExiting;
    private int pendingPermissionFaqCheck;
    private boolean permissionFaqDialogVisible;
    private boolean listenerReconnectScheduled;
    private long listenerReconnectDeadlineElapsedMs;
    private boolean launcherDispatch;

    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 700L);
        }
    };

    private final Runnable communityRefresh = new Runnable() {
        @Override public void run() {
            refreshOnlineStatus();
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable listenerReconnect = new Runnable() {
        @Override public void run() {
            listenerReconnectScheduled = false;
            if (!activityResumed || !hasNotificationAccess()
                    || MusicNotificationListener.isHealthy(LISTENER_HEALTH_MAX_AGE_MS)) {
                return;
            }
            MusicNotificationListener.requestReconnect(MainActivity.this);
            if (SystemClock.elapsedRealtime() < listenerReconnectDeadlineElapsedMs) {
                listenerReconnectScheduled = true;
                handler.postDelayed(this, LISTENER_RECONNECT_INTERVAL_MS);
            } else {
                listenerReconnectScheduled = false;
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        // The companion's dense control surface is intentionally a stable dark workspace.
        // Overlay lyrics can still use the separately selected light/dark environment.
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        boolean launcherIntent = isLauncherIntent();
        if (launcherIntent) {
            AppPreferences.get(this).edit().remove("launch_overlay_target").apply();
            AppPreferences.setServiceStoppedByUser(this, false);
        }
        if (launcherIntent && dispatchLauncherOverlay()) {
            launcherDispatch = true;
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF07111F);
            getWindow().setNavigationBarColor(0xFF07111F);
        }
        setContentView(buildContent());
        CustomFontStore.applyToViewTree(this, getWindow().getDecorView());
        MusicStateStore.initialize(this);
        requestNotificationPermissionIfNeeded();
        handler.postDelayed(this::showCommunityAnnouncementIfNeeded, 300L);
        handler.postDelayed(() -> checkForUpdates(false), 2_000L);
    }

    @Override protected void onResume() {
        super.onResume();
        if (launcherDispatch) return;
        activityResumed = true;
        ensureNotificationListenerConnected();
        bindPreferences();
        refreshDisplayChoices();
        refreshPreview();
        refreshFeedbackReplies();
        LyricsDisplayService.startOrRefresh(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        handler.removeCallbacks(statusRefresh);
        handler.post(statusRefresh);
        handler.removeCallbacks(communityRefresh);
        handler.post(communityRefresh);
        // Some ROMs update the permission state a moment after their Settings page closes.
        // Check after that hand-off so a newly granted switch never produces a false warning.
        handler.postDelayed(this::promptPermissionFaqIfStillMissing, 350L);
    }

    @Override protected void onPause() {
        if (launcherDispatch) {
            super.onPause();
            return;
        }
        activityResumed = false;
        listenerReconnectScheduled = false;
        handler.removeCallbacks(listenerReconnect);
        handler.removeCallbacks(statusRefresh);
        handler.removeCallbacks(communityRefresh);
        if (!stoppingAndExiting) LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private boolean isLauncherIntent() {
        Intent intent = getIntent();
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    private boolean dispatchLauncherOverlay() {
        if (!AppPreferences.launchOverlayOnIcon(this)) return false;
        long now = SystemClock.elapsedRealtime();
        android.content.SharedPreferences preferences = AppPreferences.get(this);
        long last = preferences.getLong(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT, 0L);
        if (last > 0L && now >= last && now - last <= 30_000L) {
            preferences.edit().remove(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT).apply();
            return false;
        }
        if (!LyricsDisplayService.startRememberedFromLauncher(this)) return false;
        preferences.edit().putLong(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT, now).apply();
        return true;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CUSTOM_FONT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String name = CustomFontStore.importFont(this, uri);
            AppPreferences.changed(this);
            Toast.makeText(this, "已全局应用字体：" + name, Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage() == null ? "导入字体失败" : error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showCommunityAnnouncementIfNeeded() {
        if (isFinishing() || isDestroyed()) return;
        if (AppPreferences.get(this).getBoolean(
                AppPreferences.KEY_COMMUNITY_ANNOUNCEMENT_DISMISSED, false)) {
            showSafetyNoticeIfNeeded();
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("公告")
                .setMessage("歌词伴侣反馈交流群\n1049772727")
                .setNegativeButton("关闭", null)
                .setPositiveButton("关闭并不再提示", (ignoredDialog, which) -> AppPreferences.get(this)
                        .edit().putBoolean(AppPreferences.KEY_COMMUNITY_ANNOUNCEMENT_DISMISSED,
                                true).apply())
                .create();
        dialog.setOnShowListener(ignored -> setDialogTitleColor(dialog, Color.BLACK));
        dialog.setOnDismissListener(ignored -> showSafetyNoticeIfNeeded());
        dialog.show();
    }

    private void showSafetyNoticeIfNeeded() {
        if (isFinishing() || isDestroyed() || AppPreferences.get(this).getBoolean(
                AppPreferences.KEY_SAFETY_NOTICE_SEEN, false)) return;
        showSafetyNotice();
    }

    private void showSafetyNotice() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("车机使用须知")
                .setMessage("本应用只读取播放器状态并绘制 Android 悬浮歌词，不控制车辆行驶、转向、制动等安全系统。\n\n"
                        + "请在停车时完成权限和布局设置，驾驶中不要操作屏幕。不同车机的权限、自启动和播放器实现可能导致悬浮窗无法自动恢复；歌词内容和时间请以原播放器为准。")
                .setPositiveButton("知道了", (ignored, which) -> AppPreferences.get(this).edit()
                        .putBoolean(AppPreferences.KEY_SAFETY_NOTICE_SEEN, true).apply())
                .create();
        dialog.show();
    }

    private static void setDialogTitleColor(AlertDialog dialog, int color) {
        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) title.setTextColor(color);
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface,
                0xFF07111F));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("歌词伴侣");
        toolbar.setSubtitle("主屏悬浮窗 · 副屏歌词");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView eyebrow = text("LYRICS · COMPANION", 12, 0xFF6EE7F2, true);
        root.addView(eyebrow);
        TextView title = text("让歌词自然地出现在每块屏幕上", 28, Color.WHITE, true);
        title.setPadding(0, dp(6), 0, dp(5));
        root.addView(title);
        TextView intro = text("读取系统媒体控制中的封面与播放状态，并从网易云、QQ 音乐、酷狗、酷我等来源匹配逐字或逐行歌词。", 14,
                0xFFA9B6C8, false);
        intro.setLineSpacing(0f, 1.18f);
        root.addView(intro);

        LinearLayout previewCard = card();
        TextView previewLabel = sectionLabel("实时预览");
        previewCard.addView(previewLabel);
        previewPanel = new LyricsPanelView(this, false);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1,
                previewHeightPx());
        previewLp.topMargin = dp(10);
        previewCard.addView(previewPanel, previewLp);

        LinearLayout accessCard = card();
        accessCard.addView(sectionLabel("使用权限"));
        permissionStatus = text("", 14, 0xFFD8E1EE, false);
        permissionStatus.setPadding(0, dp(8), 0, dp(12));
        accessCard.addView(permissionStatus);
        LinearLayout permissionButtons = new LinearLayout(this);
        permissionButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton notificationAccess = button("音乐读取权限", true);
        notificationAccess.setOnClickListener(v -> openNotificationAccess());
        permissionButtons.addView(notificationAccess, weightedButton());
        MaterialButton overlayAccess = button("悬浮窗权限", false);
        overlayAccess.setOnClickListener(v -> openOverlayPermission());
        LinearLayout.LayoutParams secondButton = weightedButton();
        secondButton.leftMargin = dp(10);
        permissionButtons.addView(overlayAccess, secondButton);
        accessCard.addView(permissionButtons);

        LinearLayout lyricCard = card();
        lyricCard.addView(sectionLabel("歌词匹配"));
        MaterialSwitch playerCatalogFallback = toggle("回退到播放器同源词库",
                "手动选择的词库无结果时，再尝试从应用名称识别出的播放器词库");
        addLyricCatalogSelector(lyricCard, playerCatalogFallback);
        playerCatalogFallback.setChecked(AppPreferences.playerCatalogFallback(this));
        playerCatalogFallback.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_PLAYER_CATALOG_FALLBACK, checked).apply();
            MusicStateStore.reloadLyrics(this);
        });
        lyricCard.addView(playerCatalogFallback);

        LinearLayout outputCard = card();
        outputCard.addView(sectionLabel("显示与启动"));
        mainOverlaySwitch = toggle("主屏悬浮窗",
                "离开设置页后显示；可拖动，双击强制返回，长按锁定并开启触摸穿透；点击圆形 × 按钮可恢复");
        mainOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_MAIN_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) openOverlayPermission();
            if (checked) requestSpectrumPermissionIfNeeded(false);
            AppPreferences.changed(this);
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(mainOverlaySwitch);
        secondaryOverlaySwitch = toggle("副屏歌词", "直接在选中的非默认 Display 上创建独立悬浮层");
        secondaryOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) openOverlayPermission();
            if (checked) requestSpectrumPermissionIfNeeded(true);
            AppPreferences.changed(this);
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(secondaryOverlaySwitch);
        launchOverlaySwitch = toggle("点击图标启动悬浮窗",
                "开启后首次点击图标按已记忆的主屏、副屏和通知栏歌词恢复显示；30 秒内再次点击进入主界面");
        launchOverlaySwitch.setChecked(AppPreferences.launchOverlayOnIcon(this));
        launchOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_LAUNCH_OVERLAY_ON_ICON, checked)
                    .remove(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT)
                    .apply();
        });
        outputCard.addView(launchOverlaySwitch);
        autoStartSwitch = toggle("开机 / 亮屏自启动悬浮窗",
                "在重启或每次亮屏时恢复已记忆的主屏、副屏和通知栏歌词。关闭服务并退出不会改变此项。");
        autoStartSwitch.setChecked(AppPreferences.autoStartOverlays(this));
        autoStartSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_AUTO_START_OVERLAYS, checked).apply();
            if (checked) {
                boolean addedDefaultTarget = AppPreferences.ensureAutoStartOverlayTarget(this);
                AppPreferences.setServiceStoppedByUser(this, false);
                if (addedDefaultTarget && mainOverlaySwitch != null) {
                    mainOverlaySwitch.setChecked(true);
                }
            }
            LyricsDisplayService.startOrRefresh(this);
        });
        outputCard.addView(autoStartSwitch);
        MaterialSwitch returnToPlayer = toggle("轻触悬浮窗返回播放器",
                "关闭时打开歌词伴侣；无法打开播放器时会自动回到歌词伴侣");
        returnToPlayer.setChecked(AppPreferences.tapOverlayReturnsToPlayer(this));
        returnToPlayer.setOnCheckedChangeListener((button, checked) -> AppPreferences.get(this)
                .edit().putBoolean(AppPreferences.KEY_TAP_OVERLAY_RETURNS_TO_PLAYER, checked)
                .apply());
        outputCard.addView(returnToPlayer);

        MaterialButton visibilityRules = button("悬浮窗隐藏规则", false);
        visibilityRules.setOnClickListener(v -> startActivity(
                new Intent(this, OverlayVisibilitySettingsActivity.class)));
        LinearLayout.LayoutParams visibilityRuleParams = new LinearLayout.LayoutParams(-1, dp(48));
        visibilityRuleParams.topMargin = dp(10);
        outputCard.addView(visibilityRules, visibilityRuleParams);

        MaterialSwitch topLyricStrip = toggle("通知栏显示歌词",
                "在桌面顶部透明显示紧凑双行歌词（本句/下句、居中、逐字高亮）；需要悬浮窗权限，并会被图标启动和自启动记忆");
        topLyricStrip.setChecked(AppPreferences.topLyricStrip(this));
        outputCard.addView(topLyricStrip);
        topLyricStrip.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_TOP_LYRIC_STRIP, checked).apply();
            AppPreferences.changed(this);
        });
        MaterialButton statusLyricSettings = button("通知栏歌词详细设置", false);
        statusLyricSettings.setOnClickListener(v -> startActivity(
                new Intent(this, StatusLyricSettingsActivity.class)));
        LinearLayout.LayoutParams statusLyricSettingsParams = new LinearLayout.LayoutParams(-1, dp(48));
        statusLyricSettingsParams.topMargin = dp(8);
        outputCard.addView(statusLyricSettings, statusLyricSettingsParams);

        MaterialButton stopService = button("关闭服务并退出", false);
        stopService.setOnClickListener(v -> confirmStopServiceAndExit());
        LinearLayout.LayoutParams stopServiceParams = new LinearLayout.LayoutParams(-1, dp(48));
        stopServiceParams.topMargin = dp(12);
        outputCard.addView(stopService, stopServiceParams);

        TextView displayLabel = text("投屏屏幕", 13, 0xFF93A4B9, true);
        displayLabel.setPadding(0, dp(14), 0, dp(5));
        outputCard.addView(displayLabel);
        displaySpinner = new Spinner(this, Spinner.MODE_DIALOG);
        displaySpinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        outputCard.addView(displaySpinner, new LinearLayout.LayoutParams(-1, dp(52)));
        displayStatus = text("", 13, 0xFF8392A8, false);
        displayStatus.setPadding(0, dp(5), 0, 0);
        outputCard.addView(displayStatus);

        TextView joystickLabel = text("副屏位置微调", 13, 0xFF93A4B9, true);
        joystickLabel.setPadding(0, dp(16), 0, 0);
        outputCard.addView(joystickLabel);
        TextView joystickHelp = text("按住摇杆持续移动；松手后自动回中。副屏接入并开启后生效。", 12,
                0xFF74869D, false);
        joystickHelp.setPadding(0, dp(4), 0, dp(4));
        outputCard.addView(joystickHelp);
        SecondaryPositionJoystickView joystick = new SecondaryPositionJoystickView(this);
        joystick.setListener((dx, dy) -> LyricsDisplayService.moveSecondaryBy(this, dx, dy));
        LinearLayout.LayoutParams joystickParams = new LinearLayout.LayoutParams(dp(148), dp(148));
        joystickParams.gravity = Gravity.CENTER_HORIZONTAL;
        outputCard.addView(joystick, joystickParams);

        LinearLayout styleCard = card();
        styleCard.addView(sectionLabel("主屏 / 副屏样式"));
        addStyleSelector(styleCard, "主屏悬浮窗样式", false);
        addStyleSelector(styleCard, "副屏歌词样式", true);
        MaterialButton fullscreenLyrics = button("全屏展示主屏样式", true);
        fullscreenLyrics.setOnClickListener(v -> startActivity(
                new Intent(this, FullscreenLyricsActivity.class)));
        LinearLayout.LayoutParams fullscreenParams = new LinearLayout.LayoutParams(-1, dp(50));
        fullscreenParams.topMargin = dp(10);
        styleCard.addView(fullscreenLyrics, fullscreenParams);
        mainRefinedSettingsButton = button("主屏 Refined Now Playing 详细设置", false);
        mainRefinedSettingsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, RefinedSettingsActivity.class));
        });
        LinearLayout.LayoutParams defaultSettingsParams = new LinearLayout.LayoutParams(-1, dp(50));
        defaultSettingsParams.topMargin = dp(10);
        styleCard.addView(mainRefinedSettingsButton, defaultSettingsParams);
        secondaryRefinedSettingsButton = button("\u526f\u5c4f Refined Now Playing \u8be6\u7ec6\u8bbe\u7f6e", false);
        secondaryRefinedSettingsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, RefinedSettingsActivity.class)
                    .putExtra(RefinedSettingsActivity.EXTRA_SECONDARY, true));
        });
        styleCard.addView(secondaryRefinedSettingsButton, new LinearLayout.LayoutParams(-1, dp(50)));
        mainCompactSettingsButton = button("主屏紧凑歌词详细设置", false);
        mainCompactSettingsButton.setOnClickListener(v -> startActivity(
                new Intent(this, CompactSettingsActivity.class)));
        LinearLayout.LayoutParams compactMainParams = new LinearLayout.LayoutParams(-1, dp(50));
        compactMainParams.topMargin = dp(10);
        styleCard.addView(mainCompactSettingsButton, compactMainParams);
        secondaryCompactSettingsButton = button("副屏紧凑歌词详细设置", false);
        secondaryCompactSettingsButton.setOnClickListener(v -> startActivity(
                new Intent(this, CompactSettingsActivity.class)
                        .putExtra(CompactSettingsActivity.EXTRA_SECONDARY, true)));
        styleCard.addView(secondaryCompactSettingsButton, new LinearLayout.LayoutParams(-1, dp(50)));
        updateRefinedSettingsVisibility();
        addThemeSelector(styleCard);
        addGlobalFontControls(styleCard);
        addDisplaySettingsLaunchers(styleCard);
        addPlaybackControlToggles(styleCard);

        LinearLayout updateCard = card();
        updateCard.addView(sectionLabel("应用更新"));
        updateStatus = text(localVersionText(), 13, 0xFFD8E1EE, false);
        updateStatus.setLineSpacing(0f, 1.18f);
        updateStatus.setPadding(0, dp(9), 0, dp(8));
        updateCard.addView(updateStatus);
        LinearLayout updateButtons = new LinearLayout(this);
        updateButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton checkUpdate = button("检查更新", true);
        checkUpdate.setOnClickListener(v -> checkForUpdates(true));
        updateButtons.addView(checkUpdate, weightedButton());
        MaterialButton versionHistory = button("历史版本", false);
        versionHistory.setOnClickListener(v -> openUrl(UPDATE_HISTORY_URL));
        LinearLayout.LayoutParams historyParams = weightedButton();
        historyParams.leftMargin = dp(10);
        updateButtons.addView(versionHistory, historyParams);
        updateCard.addView(updateButtons);

        LinearLayout openSourceCard = card();
        openSourceCard.addView(sectionLabel("开源与致谢"));
        TextView openSourceSummary = text(
                "歌词伴侣基于 GPL-3.0 开源。Refined Now Playing、PiPWindow 与 Apple Music-like Lyrics 样式参考了对应开源项目，并以原生 Android 方式重新实现。",
                13, 0xFFD8E1EE, false);
        openSourceSummary.setLineSpacing(0f, 1.2f);
        openSourceSummary.setPadding(0, dp(9), 0, dp(10));
        openSourceCard.addView(openSourceSummary);

        MaterialButton sourceRepository = button("歌词伴侣源码 · GPL-3.0", true);
        sourceRepository.setOnClickListener(v -> openUrl(SOURCE_REPOSITORY_URL));
        openSourceCard.addView(sourceRepository, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView referenceLabel = text("样式参考项目", 12, 0xFF93A4B9, true);
        referenceLabel.setPadding(0, dp(13), 0, dp(5));
        openSourceCard.addView(referenceLabel);
        MaterialButton refinedRepository = button("Refined Now Playing · MIT", false);
        refinedRepository.setOnClickListener(v -> openUrl(REFINED_REPOSITORY_URL));
        openSourceCard.addView(refinedRepository, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialButton pipWindowRepository = button("PiPWindow · GPL-3.0", false);
        pipWindowRepository.setOnClickListener(v -> openUrl(PIPWINDOW_REPOSITORY_URL));
        LinearLayout.LayoutParams pipWindowParams = new LinearLayout.LayoutParams(-1, dp(48));
        pipWindowParams.topMargin = dp(6);
        openSourceCard.addView(pipWindowRepository, pipWindowParams);
        MaterialButton amllRepository = button("Apple Music-like Lyrics · AGPL-3.0", false);
        amllRepository.setOnClickListener(v -> openUrl(AMLL_REPOSITORY_URL));
        LinearLayout.LayoutParams amllParams = new LinearLayout.LayoutParams(-1, dp(48));
        amllParams.topMargin = dp(6);
        openSourceCard.addView(amllRepository, amllParams);
        MaterialButton notices = button("第三方开源声明", false);
        notices.setOnClickListener(v -> openUrl(THIRD_PARTY_NOTICES_URL));
        LinearLayout.LayoutParams noticesParams = new LinearLayout.LayoutParams(-1, dp(48));
        noticesParams.topMargin = dp(6);
        openSourceCard.addView(notices, noticesParams);

        LinearLayout communityCard = card();
        communityCard.addView(sectionLabel("社区与反馈"));
        onlineStatus = text("当前在线：正在连接…", 14, 0xFFD8E1EE, true);
        onlineStatus.setPadding(0, dp(9), 0, dp(3));
        communityCard.addView(onlineStatus);
        TextView onlinePrivacy = text("匿名安装 ID 仅用于两分钟内去重，不读取设备硬件标识。", 12,
                0xFF8392A8, false);
        onlinePrivacy.setPadding(0, 0, 0, dp(10));
        communityCard.addView(onlinePrivacy);
        MaterialButton feedback = button("意见反馈", false);
        feedback.setOnClickListener(v -> showFeedbackDialog());
        communityCard.addView(feedback, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialButton safety = button("车机使用须知", false);
        safety.setOnClickListener(v -> showSafetyNotice());
        LinearLayout.LayoutParams safetyParams = new LinearLayout.LayoutParams(-1, dp(48));
        safetyParams.topMargin = dp(10);
        communityCard.addView(safety, safetyParams);
        addSupportControls(communityCard);

        LinearLayout stateCard = card();
        stateCard.addView(sectionLabel("音乐状态与诊断"));
        musicStatus = text("等待播放器…", 14, 0xFFD8E1EE, false);
        musicStatus.setLineSpacing(0f, 1.2f);
        musicStatus.setPadding(0, dp(9), 0, 0);
        stateCard.addView(musicStatus);
        MaterialButton rematchLyrics = button("修正歌曲信息并重新匹配", false);
        rematchLyrics.setOnClickListener(v -> showLyricRematchDialog());
        LinearLayout.LayoutParams rematchParams = new LinearLayout.LayoutParams(-1, dp(48));
        rematchParams.topMargin = dp(12);
        stateCard.addView(rematchLyrics, rematchParams);

        if (useTwoColumnLayout()) {
            LinearLayout columns = new LinearLayout(this);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);
            LinearLayout leftColumn = new LinearLayout(this);
            leftColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout rightColumn = new LinearLayout(this);
            rightColumn.setOrientation(LinearLayout.VERTICAL);
            columns.addView(leftColumn, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1f);
            rightParams.leftMargin = dp(14);
            columns.addView(rightColumn, rightParams);
            leftColumn.addView(previewCard, cardMargins());
            rightColumn.addView(accessCard, cardMargins());
            rightColumn.addView(lyricCard, cardMargins());
            leftColumn.addView(outputCard, cardMargins());
            leftColumn.addView(stateCard, cardMargins());
            leftColumn.addView(updateCard, cardMargins());
            rightColumn.addView(styleCard, cardMargins());
            rightColumn.addView(communityCard, cardMargins());
            rightColumn.addView(openSourceCard, cardMargins());
            root.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        } else {
            root.addView(previewCard, cardMargins());
            root.addView(accessCard, cardMargins());
            root.addView(lyricCard, cardMargins());
            root.addView(communityCard, cardMargins());
            root.addView(updateCard, cardMargins());
            root.addView(openSourceCard, cardMargins());
            root.addView(outputCard, cardMargins());
            root.addView(styleCard, cardMargins());
            root.addView(stateCard, cardMargins());
        }

        TextView footnote = text("提示：支持发布 MediaSession 的在线、本地和 U 盘音乐播放器；文件名会自动清理路径、序号、扩展名和音质标记，仍不准确时可用“修正歌曲信息并重新匹配”。歌词伴侣不会向 iPhone CarPlay 仪表盘注入媒体信息。", 12,
                0xFF66788F, false);
        footnote.setLineSpacing(0f, 1.25f);
        root.addView(footnote);
        return scroll;
    }

    private void confirmStopServiceAndExit() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关闭歌词服务")
                .setMessage("将移除所有悬浮歌词、停止前台服务和音乐监听。主屏、副屏与开机/亮屏自启动开关会保留；重启后会按自启动设置恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("关闭并退出", (dialog, which) -> stopServiceAndExit())
                .show();
    }

    private void stopServiceAndExit() {
        stoppingAndExiting = true;
        LyricsDisplayService.stopAndRememberOverlays(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask();
        else finish();
    }

    private void addStyleSelector(LinearLayout parent, String title, boolean secondary) {
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(6));
        parent.addView(label);
        String[] labels = {"Refined Now Playing", "Apple Music-like Lyrics", "歌词伴侣经典样式", "紧凑歌词", "PiPWindow"};
        String[] values = {"refined", "amll", "default", "compact", "pip"};
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parentView) {
                TextView view = (TextView) super.getView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                                                  ViewGroup parentView) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
        };
        spinner.setAdapter(adapter);
        String saved = AppPreferences.overlayStyle(this, secondary);
        int selection = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selection = i;
        spinner.setSelection(selection, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                 View view, int position, long id) {
                if (values[position].equals(AppPreferences.overlayStyle(MainActivity.this, secondary))) return;
                AppPreferences.setOverlayStyle(MainActivity.this, secondary, values[position]);
                updateRefinedSettingsVisibility();
                if (!secondary) refreshPreview();
                AppPreferences.changed(MainActivity.this);
                if (!secondary) recreate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView help = text(secondary
                        ? "副屏可独立选择样式。"
                        : "Refined、Apple Music-like Lyrics 和 PiPWindow 均为独立样式；经典样式保留原歌词伴侣默认布局。",
                12, 0xFF74869D, false);
        help.setPadding(0, dp(5), 0, 0);
        parent.addView(help);
    }

    private void updateRefinedSettingsVisibility() {
        if (mainRefinedSettingsButton != null) {
            mainRefinedSettingsButton.setVisibility("refined".equals(AppPreferences.overlayStyle(this, false))
                    ? View.VISIBLE : View.GONE);
        }
        if (secondaryRefinedSettingsButton != null) {
            secondaryRefinedSettingsButton.setVisibility("refined".equals(AppPreferences.overlayStyle(this, true))
                    ? View.VISIBLE : View.GONE);
        }
        if (mainCompactSettingsButton != null) {
            mainCompactSettingsButton.setVisibility("compact".equals(AppPreferences.overlayStyle(this, false))
                    ? View.VISIBLE : View.GONE);
        }
        if (secondaryCompactSettingsButton != null) {
            secondaryCompactSettingsButton.setVisibility("compact".equals(AppPreferences.overlayStyle(this, true))
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void addLyricCatalogSelector(LinearLayout parent,
                                         MaterialSwitch playerCatalogFallback) {
        TextView label = text("默认匹配词库", 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(6));
        parent.addView(label);
        String[] labels = {"自动识别播放器", "网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐"};
        String[] values = {"auto", "netease", "qqmusic", "kugou", "kuwo", "soda"};
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parentView) {
                TextView view = (TextView) super.getView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                                                  ViewGroup parentView) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
        };
        spinner.setAdapter(adapter);
        String saved = AppPreferences.lyricCatalog(this);
        int selection = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selection = i;
        spinner.setSelection(selection, false);
        updatePlayerCatalogFallbackEnabled(playerCatalogFallback, selection != 0);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                 View view, int position, long id) {
                updatePlayerCatalogFallbackEnabled(playerCatalogFallback, position != 0);
                if (values[position].equals(AppPreferences.lyricCatalog(MainActivity.this))) return;
                AppPreferences.get(MainActivity.this).edit()
                        .putString(AppPreferences.KEY_LYRIC_CATALOG, values[position]).apply();
                MusicStateStore.reloadLyrics(MainActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView help = text("这是未单独设置播放器时的默认规则。自动模式优先使用识别出的播放器同源词库；手动模式始终先尝试所选词库。当前词库无结果后才依次查询下一词库。",
                12, 0xFF74869D, false);
        help.setPadding(0, dp(5), 0, 0);
        parent.addView(help);
        MaterialButton rules = button("按词库强制匹配应用", false);
        rules.setOnClickListener(v -> showPlayerLyricCatalogRulesDialog());
        LinearLayout.LayoutParams rulesParams = new LinearLayout.LayoutParams(-1, dp(46));
        rulesParams.topMargin = dp(8);
        parent.addView(rules, rulesParams);
    }

    private static void updatePlayerCatalogFallbackEnabled(MaterialSwitch view,
                                                            boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.55f);
    }

    private void showPlayerLyricCatalogRulesDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), 0, dp(4), 0);
        TextView note = text("选择一个词库后，可从所有已安装应用中多选。被选中的应用将只从该词库匹配歌词；同一应用只能归属一个强制词库。未选择的应用继续使用上方默认规则。",
                13, 0xFF74869D, false);
        note.setLineSpacing(0f, 1.2f);
        content.addView(note);
        String[] labels = {"网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐"};
        String[] catalogs = {"netease", "qqmusic", "kugou", "kuwo", "soda"};
        for (int i = 0; i < catalogs.length; i++) {
            final String catalog = catalogs[i];
            final String catalogLabel = labels[i];
            MaterialButton chooseApps = button(catalogLabel + " · 选择应用", false);
            chooseApps.setOnClickListener(v -> showCatalogAppPicker(catalog, catalogLabel));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.topMargin = dp(10);
            content.addView(chooseApps, params);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(this)
                .setTitle("按词库强制匹配应用")
                .setView(scroll)
                .setPositiveButton("完成", null)
                .show();
    }

    private void showCatalogAppPicker(String catalog, String catalogLabel) {
        LinearLayout loading = new LinearLayout(this);
        loading.setPadding(dp(24), dp(16), dp(24), dp(16));
        loading.addView(text("正在读取已安装应用…", 14, 0xFFD8E1EE, false));
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(catalogLabel + "词库")
                .setView(loading)
                .setNegativeButton("取消", null)
                .show();
        UPDATE_EXECUTOR.execute(() -> {
            List<InstalledAppListCache.AppChoice> apps = InstalledAppListCache.load(this,
                    AppPreferences.observedPlayerPackages(this));
            handler.post(() -> {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                showLoadedCatalogAppPicker(catalog, catalogLabel, apps);
            });
        });
    }

    private void showLoadedCatalogAppPicker(String catalog, String catalogLabel,
                                            List<InstalledAppListCache.AppChoice> apps) {
        Set<String> selected = new LinkedHashSet<>();
        for (InstalledAppListCache.AppChoice app : apps) {
            if (catalog.equals(AppPreferences.playerPackageLyricCatalogOverride(this,
                    app.packageName))) selected.add(app.packageName);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4), 0, dp(4), 0);
        for (InstalledAppListCache.AppChoice app : apps) {
            addCatalogAppChoiceRow(list, app, selected);
        }
        scroll.addView(list);
        new MaterialAlertDialogBuilder(this)
                .setTitle(catalogLabel + "词库 · 强制匹配")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    for (InstalledAppListCache.AppChoice app : apps) {
                        String current = AppPreferences.playerPackageLyricCatalogOverride(this,
                                app.packageName);
                        if (selected.contains(app.packageName)) {
                            AppPreferences.putPlayerPackageLyricCatalog(this, app.packageName,
                                    catalog);
                        } else if (catalog.equals(current)) {
                            AppPreferences.putPlayerPackageLyricCatalog(this, app.packageName, "");
                        }
                    }
                    AppPreferences.changed(this);
                    MusicStateStore.reloadLyrics(this);
                    refreshPreview();
                    Toast.makeText(this, "已保存 " + catalogLabel + " 强制匹配应用", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void addCatalogAppChoiceRow(LinearLayout parent, InstalledAppListCache.AppChoice app,
                                        Set<String> selected) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.sym_def_app_icon);
        icon.setTag(app.packageName);
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        ProgressBar iconLoading = new ProgressBar(this);
        iconLoading.setIndeterminate(true);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        spinnerParams.leftMargin = dp(-29);
        spinnerParams.rightMargin = dp(7);
        row.addView(iconLoading, spinnerParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(app.label, 14, 0xFFF3F7FC, true));
        labels.addView(text(app.packageName, 11, 0xFFA9B6C8, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        CheckBox check = new CheckBox(this);
        check.setChecked(selected.contains(app.packageName));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
        });
        row.addView(check, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        APP_ICON_EXECUTOR.execute(() -> {
            Drawable drawable = loadApplicationIcon(app.packageName);
            handler.post(() -> {
                if (!app.packageName.equals(icon.getTag())) return;
                iconLoading.setVisibility(View.GONE);
                if (drawable != null) icon.setImageDrawable(drawable);
            });
        });
    }

    private Drawable loadApplicationIcon(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return info.loadIcon(getPackageManager());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void updateLockscreenLyricsEnabled(MaterialSwitch view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.55f);
    }

    private void bindPreferences() {
        bindingUi = true;
        mainOverlaySwitch.setChecked(AppPreferences.mainEnabled(this));
        secondaryOverlaySwitch.setChecked(AppPreferences.secondaryEnabled(this));
        launchOverlaySwitch.setChecked(AppPreferences.launchOverlayOnIcon(this));
        autoStartSwitch.setChecked(AppPreferences.autoStartOverlays(this));
        bindingUi = false;
    }

    private void requestSpectrumPermissionIfNeeded(boolean secondary) {
        if (!"compact".equals(AppPreferences.overlayStyle(this, secondary))
                || !AppPreferences.compactShowBars(this, secondary)
                || !AppPreferences.compactUseRealSpectrum(this, secondary)
                || Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.startOrRefresh(this);
            refreshPreview();
        }
    }

    private void refreshStatus() {
        boolean notificationAccess = hasNotificationAccess();
        boolean overlay = canDrawOverlays();
        String listenerState = listenerState(notificationAccess);
        permissionStatus.setText("通知读取  " + (notificationAccess ? "已授权" : "未授权")
                + "     监听器  " + listenerState
                + "     悬浮窗  " + (overlay ? "已授权" : "未授权"));
        permissionStatus.setTextColor(notificationAccess && overlay
                ? 0xFF6EE7F2 : 0xFFFFCA66);
        long lastRead = MusicNotificationListener.getLastSuccessfulSessionReadElapsedMs();
        String error = MusicNotificationListener.getLastSessionError();
        if (error == null || error.trim().isEmpty()) error = "无";
        else error = error.replace('\n', ' ').replace('\r', ' ').trim();
        if (error.length() > 160) error = error.substring(0, 160) + "…";
        musicStatus.setText(MusicStateStore.describe(this)
                + "\n通知读取：" + (notificationAccess ? "已授权" : "未授权")
                + "    监听器：" + listenerState
                + "    读取方式：" + backendDescription()
                + "\n最近成功读取会话：" + formatSessionReadAge(lastRead)
                + "    当前会话数量：" + MusicNotificationListener.getLastSessionCount()
                + "\n最近异常信息：" + error);
    }

    private void refreshDisplayChoices() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        List<DisplayChoice> choices = new ArrayList<>();
        choices.add(new DisplayChoice(-1, "自动选择首个副屏"));
        if (manager != null) {
            for (Display display : manager.getDisplays()) {
                if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    choices.add(new DisplayChoice(display.getDisplayId(),
                            display.getName() + "  ·  ID " + display.getDisplayId()));
                }
            }
        }
        ArrayAdapter<DisplayChoice> adapter = new ArrayAdapter<DisplayChoice>(this,
                android.R.layout.simple_spinner_dropdown_item, choices) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(view);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view);
                return view;
            }
        };
        bindingUi = true;
        displaySpinner.setAdapter(adapter);
        int selectedId = AppPreferences.displayId(this);
        int selection = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).id == selectedId) selection = i;
        }
        displaySpinner.setSelection(selection);
        displaySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                if (bindingUi) return;
                DisplayChoice choice = choices.get(position);
                AppPreferences.get(MainActivity.this).edit()
                        .putInt(AppPreferences.KEY_DISPLAY_ID, choice.id).apply();
                AppPreferences.changed(MainActivity.this);
                updateDisplayStatus(choices.size() - 1, choice);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        bindingUi = false;
        updateDisplayStatus(choices.size() - 1, choices.get(selection));
    }

    private void updateDisplayStatus(int count, DisplayChoice selected) {
        if (count == 0) {
            displayStatus.setText("当前未检测到副屏；接入 HDMI、虚拟屏或车机仪表屏后会自动出现。");
        } else {
            displayStatus.setText("检测到 " + count + " 块副屏 · 当前：" + selected.label);
        }
    }

    private void addPlaybackControlToggles(LinearLayout parent) {
        TextView label = sectionLabel("主屏播放控制按键");
        label.setPadding(0, dp(18), 0, dp(3));
        parent.addView(label);
        addPlaybackControlToggle(parent, "显示上一首按键",
                AppPreferences.KEY_SHOW_PREVIOUS_BUTTON,
                AppPreferences.showPreviousButton(this));
        addPlaybackControlToggle(parent, "显示暂停/播放按键",
                AppPreferences.KEY_SHOW_PLAY_PAUSE_BUTTON,
                AppPreferences.showPlayPauseButton(this));
        addPlaybackControlToggle(parent, "显示下一首按键",
                AppPreferences.KEY_SHOW_NEXT_BUTTON,
                AppPreferences.showNextButton(this));
    }

    private void addThemeSelector(LinearLayout parent) {
        TextView label = sectionLabel("悬浮歌词深浅色环境");
        label.setPadding(0, dp(16), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        String[] labels = {"\u8ddf\u968f\u7cfb\u7edf", "\u767d\u5929", "\u591c\u665a"};
        String[] values = {"auto", "light", "dark"};
        spinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        spinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView,
                                          ViewGroup parentView) {
                TextView view = (TextView) super.getView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                                                  ViewGroup parentView) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parentView);
                styleSpinnerText(view);
                return view;
            }
        });
        String current = AppPreferences.themeMode(this);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                spinner.setSelection(i, false);
                break;
            }
        }
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                  View view, int position, long id) {
                if (values[position].equals(AppPreferences.themeMode(MainActivity.this))) return;
                AppPreferences.setThemeMode(MainActivity.this, values[position]);
                LyricsCompanionApp.applyMaterialTheme(values[position]);
                AppPreferences.changed(MainActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView mainThemeNote = text("主界面固定使用黑色主题；此选项只影响悬浮歌词背景与已开启跟随的自动歌词色。", 12,
                0xFF74869D, false);
        mainThemeNote.setPadding(0, dp(3), 0, dp(2));
        parent.addView(mainThemeNote);
        MaterialSwitch followLyrics = toggle("歌词跟随深浅色", "开启后，自动歌词色会随界面主题切换；关闭时主屏和副屏歌词保持原颜色，顶部歌词条始终使用自己的颜色设置。");
        followLyrics.setChecked(AppPreferences.lyricsFollowTheme(this));
        followLyrics.setOnCheckedChangeListener((button, enabled) -> {
            AppPreferences.setLyricsFollowTheme(MainActivity.this, enabled);
            refreshPreview();
            AppPreferences.changed(MainActivity.this);
        });
        parent.addView(followLyrics);
    }

    private void addDisplaySettingsLaunchers(LinearLayout parent) {
        TextView label = sectionLabel("显示参数");
        label.setPadding(0, dp(18), 0, dp(4));
        parent.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton main = button("主屏显示参数", true);
        main.setOnClickListener(v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, false)));
        row.addView(main, weightedButton());
        MaterialButton secondary = button("副屏显示参数", false);
        secondary.setOnClickListener(v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, true)));
        LinearLayout.LayoutParams secondaryParams = weightedButton();
        secondaryParams.leftMargin = dp(10);
        row.addView(secondary, secondaryParams);
        parent.addView(row);
    }

    private void addPlaybackControlToggle(LinearLayout parent, String title, String key,
                                          boolean initial) {
        MaterialSwitch toggle = toggle(title, "关闭后按键和对应触控操作都会隐藏");
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            refreshPreview();
            AppPreferences.changed(this);
        });
        parent.addView(toggle);
    }

    private MaterialSwitch toggle(String title, String subtitle) {
        MaterialSwitch view = new MaterialSwitch(this);
        view.setText(title + "\n" + subtitle);
        view.setTextColor(0xFFF3F7FC);
        view.setTextSize(14f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(12), 0, dp(6));
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private void addSupportControls(LinearLayout parent) {
        MaterialSwitch crashUpload = toggle("自动上传闪退诊断",
                "包含设备型号、系统与权限、活跃播放器包名、曲目元数据、播放/歌词/显示状态及最近事件；不含歌词正文、通知正文或设备唯一标识；默认关闭");
        crashUpload.setChecked(AppPreferences.get(this).getBoolean(
                AppPreferences.KEY_DIAGNOSTIC_UPLOAD_ENABLED, false));
        crashUpload.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(
                    AppPreferences.KEY_DIAGNOSTIC_UPLOAD_ENABLED, checked).apply();
            if (checked) CommunityClient.uploadPendingCrashAsync(this, null);
        });
        parent.addView(crashUpload);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton snapshot = button("上传诊断快照", true);
        snapshot.setOnClickListener(v -> uploadDiagnosticSnapshot());
        actions.addView(snapshot, weightedButton());
        MaterialButton replies = button("查看反馈回复", false);
        replies.setOnClickListener(v -> showFeedbackReplies());
        LinearLayout.LayoutParams repliesParams = weightedButton();
        repliesParams.leftMargin = dp(10);
        actions.addView(replies, repliesParams);
        parent.addView(actions);
        feedbackReplyStatus = text("反馈回复：正在检查…", 12, 0xFF8392A8, false);
        feedbackReplyStatus.setPadding(0, dp(8), 0, 0);
        parent.addView(feedbackReplyStatus);
        MaterialButton faq = button("常见问题 FAQ", false);
        faq.setOnClickListener(v -> showFaqPanel());
        LinearLayout.LayoutParams faqParams = new LinearLayout.LayoutParams(-1, dp(48));
        faqParams.topMargin = dp(10);
        parent.addView(faq, faqParams);
    }

    private void showFaqPanel() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(2), dp(4), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("常见问题")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .create();
        renderFaq(content, FaqClient.cached(this), "正在从服务器同步 FAQ…");
        dialog.show();
        FaqClient.fetchAsync(this, result -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
            renderFaq(content, result.document,
                    result.refreshed ? "已同步最新 FAQ" : result.document == null
                            ? "服务器暂时无法连接，暂无本地缓存" : "当前显示本地缓存，服务器暂时无法连接");
        }));
    }

    private void renderFaq(LinearLayout content, FaqClient.FaqDocument document, String status) {
        content.removeAllViews();
        TextView state = text(status + (document != null && !document.updatedAt.isEmpty()
                        ? "\n更新时间：" + document.updatedAt : ""),
                12, themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0xFF8392A8), false);
        state.setLineSpacing(0f, 1.2f);
        state.setPadding(0, 0, 0, dp(12));
        content.addView(state);
        if (document == null) {
            TextView empty = text("暂时没有可显示的 FAQ，请稍后重试。", 14,
                    themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0xFF8392A8), false);
            content.addView(empty);
            return;
        }
        for (FaqClient.Item item : document.items) {
            TextView question = text(item.question, 16,
                    themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFF2F6FB), true);
            question.setPadding(0, dp(8), 0, dp(6));
            content.addView(question);
            if (!item.answer.isEmpty()) {
                TextView answer = text(item.answer, 14,
                        themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFD8E1EE), false);
                answer.setLineSpacing(0f, 1.2f);
                content.addView(answer);
            }
            for (FaqClient.Instruction instruction : item.instructions) {
                TextView title = text(instruction.title, 13,
                        themeColor(com.google.android.material.R.attr.colorPrimary, 0xFF6EE7F2), true);
                title.setPadding(0, dp(10), 0, dp(4));
                content.addView(title);
                addFaqCommand(content, instruction.command);
            }
            if (!item.note.isEmpty()) {
                TextView note = text(item.note, 12,
                        themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                                0xFF8392A8), false);
                note.setLineSpacing(0f, 1.2f);
                note.setPadding(0, dp(8), 0, dp(4));
                content.addView(note);
            }
        }
    }

    private void addFaqCommand(LinearLayout parent, String command) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = text(command, 12,
                themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFF2F6FB), false);
        value.setTextIsSelectable(true);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        value.setLineSpacing(0f, 1.1f);
        value.setPadding(dp(10), dp(8), dp(10), dp(8));
        value.setBackground(solid(0xFF25364D, 8));
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton copy = button("复制", false);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("FAQ 命令", command));
            Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(64), dp(44));
        copyParams.leftMargin = dp(8);
        row.addView(copy, copyParams);
        parent.addView(row);
    }

    private void uploadDiagnosticSnapshot() {
        String feedbackId = AppPreferences.lastFeedbackId(this);
        if (!feedbackId.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("关联诊断快照")
                    .setMessage("最近提交的反馈编号为 " + shortId(feedbackId)
                            + "。是否把这次诊断快照关联到它？")
                    .setNegativeButton("不关联上传", (dialog, which) -> uploadDiagnosticSnapshotFor(""))
                    .setPositiveButton("关联上传", (dialog, which) -> uploadDiagnosticSnapshotFor(feedbackId))
                    .show();
            return;
        }
        uploadDiagnosticSnapshotFor("");
    }

    private void showLyricRematchDialog() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        if (!snapshot.active || snapshot.title.trim().isEmpty()) {
            Toast.makeText(this, "当前没有可重新匹配的曲目", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = {"自动识别", "网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐"};
        String[] catalogs = {"auto", "netease", "qqmusic", "kugou", "kuwo", "soda"};
        String selected = AppPreferences.lyricCatalog(this, MusicStateStore.activeSourceId());
        int selectedIndex = 0;
        for (int i = 0; i < catalogs.length; i++) {
            if (catalogs[i].equals(selected)) {
                selectedIndex = i;
                break;
            }
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), 0, dp(4), 0);
        TextInputLayout titleLayout = new TextInputLayout(this);
        titleLayout.setHint("用于匹配的歌名");
        TextInputEditText titleInput = new TextInputEditText(titleLayout.getContext());
        titleInput.setSingleLine(true);
        titleInput.setText(snapshot.title);
        titleLayout.addView(titleInput, new LinearLayout.LayoutParams(-1, -2));
        content.addView(titleLayout, new LinearLayout.LayoutParams(-1, -2));
        TextInputLayout artistLayout = new TextInputLayout(this);
        artistLayout.setHint("用于匹配的歌手（可留空）");
        TextInputEditText artistInput = new TextInputEditText(artistLayout.getContext());
        artistInput.setSingleLine(true);
        artistInput.setText(snapshot.artist);
        artistLayout.addView(artistInput, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(-1, -2);
        artistParams.topMargin = dp(8);
        content.addView(artistLayout, artistParams);
        TextView catalogLabel = text("匹配词库", 13, 0xFFA9B6C8, false);
        LinearLayout.LayoutParams catalogLabelParams = new LinearLayout.LayoutParams(-1, -2);
        catalogLabelParams.topMargin = dp(12);
        content.addView(catalogLabel, catalogLabelParams);
        Spinner catalogSpinner = new Spinner(this);
        catalogSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        catalogSpinner.setSelection(selectedIndex);
        LinearLayout.LayoutParams catalogParams = new LinearLayout.LayoutParams(-1, dp(52));
        catalogParams.topMargin = dp(10);
        content.addView(catalogSpinner, catalogParams);
        new MaterialAlertDialogBuilder(this)
                .setTitle("修正歌曲信息并匹配歌词")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("重新匹配", (dialog, which) -> {
                    String requestedTitle = titleInput.getText() == null ? ""
                            : titleInput.getText().toString().trim();
                    String requestedArtist = artistInput.getText() == null ? ""
                            : artistInput.getText().toString().trim();
                    if (requestedTitle.isEmpty()) requestedTitle = snapshot.title;
                    MusicStateStore.reloadLyrics(this, requestedTitle, requestedArtist,
                            catalogs[catalogSpinner.getSelectedItemPosition()]);
                    refreshPreview();
                    Toast.makeText(this, "已按修正后的歌曲信息开始匹配",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void uploadDiagnosticSnapshotFor(String feedbackId) {
        if (diagnosticBusy) return;
        diagnosticBusy = true;
        CommunityClient.uploadSnapshotAsync(this, feedbackId, result -> runOnUiThread(() -> {
            diagnosticBusy = false;
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, result.success ? "诊断快照已上传" : "诊断上传失败：" + result.error,
                    Toast.LENGTH_LONG).show();
        }));
    }

    private static String shortId(String value) {
        if (value == null || value.length() <= 12) return value == null ? "" : value;
        return value.substring(0, 8) + "…" + value.substring(value.length() - 4);
    }

    private void refreshFeedbackReplies() {
        if (feedbackReplyStatus == null) return;
        CommunityClient.fetchFeedbackRepliesAsync(this, result -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || feedbackReplyStatus == null) return;
            if (!result.success) {
                feedbackReplyStatus.setText("反馈回复：暂时无法检查");
            } else if (result.replies.isEmpty()) {
                feedbackReplyStatus.setText("反馈回复：暂无新回复");
            } else {
                List<CommunityClient.FeedbackReply> unread =
                        CommunityClient.unreadFeedbackReplies(this, result.replies);
                if (unread.isEmpty()) {
                    feedbackReplyStatus.setText("反馈回复：已查看 " + result.replies.size() + " 条");
                } else {
                    feedbackReplyStatus.setText("反馈回复：收到 " + unread.size() + " 条新回复");
                    showFeedbackRepliesDialog(unread);
                }
            }
        }));
    }

    private void showFeedbackReplies() {
        CommunityClient.fetchFeedbackRepliesAsync(this, result -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.success) {
                Toast.makeText(this, "无法读取回复：" + result.error, Toast.LENGTH_LONG).show();
                return;
            }
            if (result.replies.isEmpty()) {
                Toast.makeText(this, "暂时没有收到回复", Toast.LENGTH_SHORT).show();
                return;
            }
            showFeedbackRepliesDialog(result.replies);
        }));
    }

    private void showFeedbackRepliesDialog(List<CommunityClient.FeedbackReply> replies) {
        if (feedbackReplyDialogVisible || replies == null || replies.isEmpty()) return;
        feedbackReplyDialogVisible = true;
        StringBuilder content = new StringBuilder();
        for (CommunityClient.FeedbackReply reply : replies) {
            if (content.length() > 0) content.append("\n\n");
            content.append(reply.createdAt).append("\n").append(reply.message);
        }
        CommunityClient.markFeedbackRepliesRead(this, replies);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("反馈回复")
                .setMessage(content).setPositiveButton("知道了", null).create();
        dialog.setOnDismissListener(ignored -> {
            feedbackReplyDialogVisible = false;
            refreshFeedbackReplies();
        });
        dialog.show();
    }

    private void refreshPreview() {
        if (previewPanel == null) return;
        previewPanel.reloadStyle();
        ViewGroup.LayoutParams params = previewPanel.getLayoutParams();
        if (params != null) {
            params.height = previewHeightPx();
            previewPanel.setLayoutParams(params);
        }
    }

    private int previewHeightPx() {
        float density = getResources().getDisplayMetrics().density;
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels / density;
        float availableWidthDp = useTwoColumnLayout()
                ? (screenWidthDp - 40f - 14f) / 2f - 32f
                : screenWidthDp - 72f;
        float aspectHeightDp = availableWidthDp * AppPreferences.panelHeightDp(this)
                / (float) AppPreferences.panelWidthDp(this);
        return dp(Math.max(AppPreferences.minimumPanelHeightDp(this),
                Math.min(420f, aspectHeightDp)));
    }

    private boolean useTwoColumnLayout() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && widthDp >= 600f;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorSurfaceContainer, 0xFF101E31)));
        surface.setCornerSize(dp(20));
        surface.setElevation(dp(1));
        card.setBackground(surface);
        return card;
    }

    private LinearLayout.LayoutParams cardMargins() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        return params;
    }

    private TextView sectionLabel(String value) {
        return text(value, 13, themeColor(com.google.android.material.R.attr.colorPrimary,
                0xFF6EE7F2), true);
    }

    private void addGlobalFontControls(LinearLayout parent) {
        TextView label = sectionLabel("全局字体");
        label.setPadding(0, dp(16), 0, dp(3));
        parent.addView(label);
        globalFontSummary = text("当前：" + CustomFontStore.selectedFontLabel(this)
                + "（替换应用界面与全部歌词，支持 TTF / OTF / TTC）",
                12, 0xFF9EAFBF, false);
        globalFontSummary.setPadding(0, 0, 0, dp(6));
        parent.addView(globalFontSummary);
        LinearLayout row = new LinearLayout(this);
        MaterialButton importButton = button("导入全局字体", false);
        importButton.setOnClickListener(v -> openFontPicker());
        row.addView(importButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        MaterialButton resetButton = button("恢复系统字体", false);
        resetButton.setOnClickListener(v -> {
            CustomFontStore.clear(this);
            AppPreferences.changed(this);
            recreate();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        resetParams.leftMargin = dp(10);
        row.addView(resetButton, resetParams);
        parent.addView(row);
    }

    private void openFontPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (startDocumentPicker(intent)) return;
        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
        fallback.addCategory(Intent.CATEGORY_OPENABLE);
        fallback.setType("*/*");
        if (startDocumentPicker(fallback)) return;
        Toast.makeText(this, "此设备没有可用的文件选择器，请安装或启用系统文件管理器后重试。",
                Toast.LENGTH_LONG).show();
    }

    private boolean startDocumentPicker(Intent intent) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) return false;
            startActivityForResult(intent, REQUEST_CUSTOM_FONT);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(adaptiveTextColor(color));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private MaterialButton button(String value, boolean primary) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextSize(13f);
        button.setTextColor(primary
                ? themeColor(com.google.android.material.R.attr.colorOnPrimary, 0xFF07111F)
                : themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFF1F5FA));
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? themeColor(com.google.android.material.R.attr.colorPrimary, 0xFF6EE7F2)
                        // Keep secondary controls neutral on the deliberately dark home page;
                        // device DynamicColors can otherwise turn them lavender.
                        : 0xFF25364D));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private GradientDrawable solid(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void styleSpinnerText(TextView view) {
        view.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFF2F6FB));
        view.setTextSize(14f);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer,
                0xFF132238));
    }

    private int themeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        return getTheme().resolveAttribute(attribute, value, true) ? value.data : fallback;
    }

    private int adaptiveTextColor(int requested) {
        int night = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) return requested;
        if (requested == 0xFF6EE7F2) {
            return themeColor(com.google.android.material.R.attr.colorPrimary, requested);
        }
        float luminance = (Color.red(requested) * 0.2126f + Color.green(requested) * 0.7152f
                + Color.blue(requested) * 0.0722f) / 255f;
        return luminance > 0.60f
                ? themeColor(com.google.android.material.R.attr.colorOnSurface, requested) : requested;
    }

    private void refreshOnlineStatus() {
        if (onlineBusy || onlineStatus == null) return;
        onlineBusy = true;
        CommunityClient.heartbeatAsync(this, result -> runOnUiThread(() -> {
            onlineBusy = false;
            if (isFinishing() || isDestroyed() || onlineStatus == null) return;
            onlineStatus.setText(result.available()
                    ? "当前在线：" + result.online + " 人"
                    : "当前在线：暂时无法获取");
            onlineStatus.setTextColor(result.available() ? 0xFF6EE7F2 : 0xFF8392A8);
        }));
    }

    private void showFeedbackDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), 0);

        TextInputLayout messageLayout = new TextInputLayout(this);
        messageLayout.setHint("反馈内容");
        TextInputEditText message = new TextInputEditText(messageLayout.getContext());
        styleFeedbackInput(messageLayout, message);
        message.setTextSize(14f);
        message.setGravity(Gravity.TOP | Gravity.START);
        message.setMinLines(4);
        message.setMaxLines(8);
        message.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        messageLayout.addView(message, new LinearLayout.LayoutParams(-1, -2));
        content.addView(messageLayout, new LinearLayout.LayoutParams(-1, -2));

        TextInputLayout contactLayout = new TextInputLayout(this);
        contactLayout.setHint("联系方式（可选）");
        contactLayout.setHelperText("可填写邮箱、QQ 或 GitHub 用户名");
        TextInputEditText contact = new TextInputEditText(contactLayout.getContext());
        styleFeedbackInput(contactLayout, contact);
        contact.setSingleLine(true);
        contact.setInputType(InputType.TYPE_CLASS_TEXT);
        contact.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        contactLayout.addView(contact, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(-1, -2);
        contactParams.topMargin = dp(10);
        content.addView(contactLayout, contactParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("意见反馈")
                .setMessage("反馈会发送到 Lyrics Companion 服务器；联系方式仅用于回复。")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (feedbackBusy) return;
                    String feedbackText = valueOf(message);
                    if (feedbackText.length() < 5) {
                        messageLayout.setError("请至少输入 5 个字符");
                        return;
                    }
                    messageLayout.setError(null);
                    feedbackBusy = true;
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("提交中…");
                    CommunityClient.submitFeedbackAsync(this, feedbackText, valueOf(contact),
                            result -> runOnUiThread(() -> {
                                feedbackBusy = false;
                                if (isFinishing() || isDestroyed()) return;
                                if (result.success) {
                                    if (dialog.isShowing()) dialog.dismiss();
                                    Toast.makeText(this, "反馈已收到，谢谢！", Toast.LENGTH_LONG).show();
                                } else if (dialog.isShowing()) {
                                    messageLayout.setError("提交失败：" + result.error);
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("提交");
                                    dialog.setCanceledOnTouchOutside(true);
                                }
                            }));
                }));
        dialog.show();
    }

    private static String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static void styleFeedbackInput(TextInputLayout layout, TextInputEditText input) {
        ColorStateList secondaryText = ColorStateList.valueOf(0xFF52657D);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(0xFF6EE7F2);
        layout.setHintTextColor(secondaryText);
        layout.setHelperTextColor(secondaryText);
        input.setTextColor(0xFF102033);
        input.setHintTextColor(0xFF52657D);
    }

    private void checkForUpdates(boolean manual) {
        if (updateBusy || updateStatus == null) return;
        updateBusy = true;
        if (manual) updateStatus.setText("正在检查更新…");
        UPDATE_EXECUTOR.execute(() -> {
            try {
                AppUpdater.UpdateInfo info = AppUpdater.check(this, UPDATE_MANIFEST_URL);
                runOnUiThread(() -> {
                    updateBusy = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (info.hasUpdate()) {
                        updateStatus.setText("发现新版本 " + info.remoteVersionName);
                        showUpdateDialog(info);
                    } else if (manual) {
                        updateStatus.setText("已是最新版本\n" + localVersionText());
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    updateBusy = false;
                    if (manual && updateStatus != null) {
                        updateStatus.setText("检查更新失败：" + safeMessage(error)
                                + "\n" + localVersionText());
                    }
                });
            }
        });
    }

    private void showUpdateDialog(AppUpdater.UpdateInfo info) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(4));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.setBackground(solid(themeColor(com.google.android.material.R.attr.colorPrimaryContainer,
                0xFF132B42), 18));
        TextView newest = text("v" + info.remoteVersionName, 20,
                themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFFF2F6FB), true);
        summary.addView(newest);
        TextView versionLine = text("从 v" + info.localVersionName + " 更新", 13,
                themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFFAFC0D6), false);
        versionLine.setPadding(0, dp(3), 0, 0);
        summary.addView(versionLine);
        StringBuilder metadata = new StringBuilder();
        if (info.size > 0L) metadata.append("安装包 ").append(formatApkSize(info.size));
        if (info.force) {
            if (metadata.length() > 0) metadata.append("  ·  ");
            metadata.append("需要更新");
        }
        if (metadata.length() > 0) {
            TextView meta = text(metadata.toString(), 12,
                    themeColor(com.google.android.material.R.attr.colorPrimary, 0xFF6EE7F2), true);
            meta.setPadding(0, dp(9), 0, 0);
            summary.addView(meta);
        }
        content.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        TextView section = text("本次更新", 13, 0xFF6EE7F2, true);
        section.setPadding(dp(4), dp(16), 0, dp(6));
        content.addView(section);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        TextView changelog = text("", 14, 0xFFF2F6FB, false);
        changelog.setLineSpacing(0f, 1.28f);
        changelog.setPadding(dp(8), dp(6), dp(8), dp(10));
        changelog.setMovementMethod(LinkMovementMethod.getInstance());
        changelog.setLinkTextColor(0xFF6EE7F2);
        changelog.setText(MarkdownRenderer.render(updateChangelogBody(info.changelog)));
        changelog.setTextIsSelectable(true);
        scroll.addView(changelog, new ScrollView.LayoutParams(-1, -2));
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.58f);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, maxHeight);
        content.addView(scroll, scrollParams);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("发现新版本")
                .setView(content)
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载并安装", (ignoredDialog, which) -> installUpdate(info))
                .create();
        dialog.setOnShowListener(ignored -> setDialogTitleColor(dialog, 0xFFF2F6FB));
        dialog.show();
    }

    private static String updateChangelogBody(String raw) {
        String body = raw == null ? "" : raw.trim();
        body = body.replaceFirst("(?s)^#\\s*更新日志\\s*\\n+", "");
        return body.isEmpty() ? "本次版本包含体验优化与问题修复。" : body;
    }

    private static String formatApkSize(long bytes) {
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024f / 1024f);
    }

    private void installUpdate(AppUpdater.UpdateInfo info) {
        if (updateBusy) return;
        updateBusy = true;
        UPDATE_EXECUTOR.execute(() -> {
            AppUpdater.downloadAndInstall(this, info,
                    message -> runOnUiThread(() -> {
                        if (updateStatus != null) updateStatus.setText(message);
                    }));
            runOnUiThread(() -> updateBusy = false);
        });
    }

    private String localVersionText() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return "当前版本 " + info.versionName + " (" + code + ")";
        } catch (Throwable ignored) {
            return "当前版本未知";
        }
    }

    private void openUrl(String address) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address))); }
        catch (Throwable error) {
            Toast.makeText(this, "无法打开链接：" + address, Toast.LENGTH_LONG).show();
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName expected = new ComponentName(this, MusicNotificationListener.class);
        String[] entries = enabled.split(":");
        for (String entry : entries) {
            if (expected.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void openNotificationAccess() {
        if (startPermissionSettingsActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                PERMISSION_CHECK_NOTIFICATION)) {
            return;
        }
        // Notification access exists on Android 4.4, but its public settings action was only
        // added in API 22. AOSP KitKat exposes this activity; vendor ROMs may not, so keep
        // every fallback resolve-checked.
        Intent kitKatNotificationAccess = new Intent().setComponent(new ComponentName(
                "com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity"));
        if (startPermissionSettingsActivity(kitKatNotificationAccess, PERMISSION_CHECK_NOTIFICATION)) return;
        if (startPermissionSettingsActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS),
                PERMISSION_CHECK_NOTIFICATION)) return;
        if (startPermissionSettingsActivity(new Intent(Settings.ACTION_SETTINGS),
                PERMISSION_CHECK_NOTIFICATION)) return;
        Toast.makeText(this, "\u65e0\u6cd5\u6253\u5f00\u7cfb\u7edf\u7684\u901a\u77e5\u8bfb\u53d6\u8bbe\u7f6e\uff0c\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u624b\u52a8\u5f00\u542f\u3002",
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

    private boolean startPermissionSettingsActivity(Intent intent, int permissionType) {
        if (!startSettingsActivity(intent)) return false;
        pendingPermissionFaqCheck |= permissionType;
        return true;
    }

    private void promptPermissionFaqIfStillMissing() {
        if (pendingPermissionFaqCheck == 0 || permissionFaqDialogVisible || isFinishing()) return;
        int pending = pendingPermissionFaqCheck;
        pendingPermissionFaqCheck = 0;
        boolean notificationMissing = (pending & PERMISSION_CHECK_NOTIFICATION) != 0
                && !hasNotificationAccess();
        boolean overlayMissing = (pending & PERMISSION_CHECK_OVERLAY) != 0
                && !canDrawOverlays();
        if (!notificationMissing && !overlayMissing) return;

        String missing;
        if (notificationMissing && overlayMissing) {
            missing = "音乐读取权限和悬浮窗权限";
        } else if (notificationMissing) {
            missing = "音乐读取权限";
        } else {
            missing = "悬浮窗权限";
        }
        permissionFaqDialogVisible = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("权限仍未生效")
                .setMessage("检测到“" + missing + "”仍未授权。不同系统可能将开关放在额外的安全、通知或应用管理页面，可在常见问题中查看对应解决方法。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("查看常见问题", (dialog, which) -> showFaqPanel())
                .setOnDismissListener(dialog -> permissionFaqDialogVisible = false)
                .show();
    }

    private void ensureNotificationListenerConnected() {
        handler.removeCallbacks(listenerReconnect);
        listenerReconnectScheduled = false;
        if (!hasNotificationAccess()
                || MusicNotificationListener.isHealthy(LISTENER_HEALTH_MAX_AGE_MS)) return;
        listenerReconnectDeadlineElapsedMs = SystemClock.elapsedRealtime()
                + LISTENER_RECONNECT_WINDOW_MS;
        listenerReconnectScheduled = true;
        // Let NotificationManager restore its listener first. Requesting a rebind immediately
        // after returning from Settings can race the platform's natural bind on Android 7+.
        handler.postDelayed(listenerReconnect, LISTENER_INITIAL_RECONNECT_DELAY_MS);
    }

    private String listenerState(boolean notificationAccess) {
        if (notificationAccess
                && MusicNotificationListener.isHealthy(LISTENER_HEALTH_MAX_AGE_MS)) {
            return "已连接";
        }
        if (notificationAccess && listenerReconnectScheduled) return "重连中";
        return "超时";
    }

    private static String backendDescription() {
        String active = MusicNotificationListener.getBackendName();
        if (active != null && !active.trim().isEmpty()) return active;
        return Build.VERSION.SDK_INT >= 21 ? "MediaSession" : "RemoteController";
    }

    private static String formatSessionReadAge(long lastReadElapsedMs) {
        if (lastReadElapsedMs <= 0L) return "从未";
        long ageMs = Math.max(0L, SystemClock.elapsedRealtime() - lastReadElapsedMs);
        if (ageMs < 1_000L) return "不到 1 秒前";
        return ageMs / 1_000L + " 秒前";
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            if (!startPermissionSettingsActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())), PERMISSION_CHECK_OVERLAY)) {
                Toast.makeText(this, "无法打开系统应用设置，请在系统设置中手动开启悬浮窗权限。",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION",
                Uri.parse("package:" + getPackageName()));
        if (startPermissionSettingsActivity(intent, PERMISSION_CHECK_OVERLAY)) return;
        if (startPermissionSettingsActivity(
                new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION"), PERMISSION_CHECK_OVERLAY)) {
            return;
        }
        if (startPermissionSettingsActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())), PERMISSION_CHECK_OVERLAY)) {
            return;
        }
        Toast.makeText(this, "无法打开系统悬浮窗权限设置，请在系统设置中手动开启。", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class DisplayChoice {
        final int id;
        final String label;
        DisplayChoice(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

}
