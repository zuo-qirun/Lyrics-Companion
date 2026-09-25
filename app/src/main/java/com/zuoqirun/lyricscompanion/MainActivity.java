package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
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
    private static final int REQUEST_LOCAL_LYRIC_DIRECTORY = 2419;
    private static final int REQUEST_BLUETOOTH_CONNECT = 2420;
    private static final int REQUEST_LOCAL_LYRIC_STORAGE = 2421;
    private static final String STATE_SELECTED_SECTION = "selected_section";
    private static final String[] SECTION_LABELS = {"总览", "显示", "歌词", "高级"};
    private static final String[] SECTION_TITLES = {"设置总览", "显示与外观", "歌词来源与校准", "高级与维护"};
    private static final String[] SECTION_DESCRIPTIONS = {
            "先完成必要权限，打开需要的歌词出口，再通过实时预览确认效果",
            "按主屏、副屏和顶部歌词条查找尺寸、样式、颜色与位置设置",
            "管理词库优先级、本地歌词、蓝牙识别和匹配修正",
            "管理启动与交互、诊断、更新、数据、反馈和开源信息"
    };
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
    /** Rows for the additional screens that can show lyrics at the same time as the main/secondary. */
    private LinearLayout extraDisplayHost;
    private LinearLayout extraDisplayDirectoryHost;
    /** One style selector per extra screen, plus the buttons of its style-specific page. */
    private LinearLayout extraStyleHost;
    private final List<ExtraStyleRow> extraStyleRows = new ArrayList<>();
    /** Which screen the position joystick moves; 副屏 until an extra screen is picked. */
    private int joystickSlot = DisplaySlotRegistry.SECONDARY_SLOT;
    private LinearLayout joystickTargetHost;
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
    private TextView sectionHeading;
    private TextView sectionDescription;
    private ScrollView mainScroll;
    private final List<View> sectionPages = new ArrayList<>();
    private final List<MaterialButton> sectionButtons = new ArrayList<>();
    private int selectedSection;
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
    private boolean conciseSettingsMode;

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
        if (savedInstanceState != null) {
            selectedSection = savedInstanceState.getInt(STATE_SELECTED_SECTION, 0);
        }
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
        handler.postDelayed(this::showCommunityAnnouncementIfNeeded, 300L);
        handler.postDelayed(() -> checkForUpdates(false), 2_000L);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_SECTION, selectedSection);
        super.onSaveInstanceState(outState);
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
        if (requestCode == REQUEST_LOCAL_LYRIC_DIRECTORY) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) { }
            AppPreferences.get(this).edit().putString(
                    AppPreferences.KEY_LOCAL_LYRIC_DIRECTORY_URI, uri.toString()).apply();
            MusicStateStore.reloadLyrics(this);
            SafeToast.show(this, "已授权本地歌词目录", Toast.LENGTH_SHORT);
            return;
        }
        if (requestCode != REQUEST_CUSTOM_FONT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String name = CustomFontStore.importFont(this, uri);
            AppPreferences.changed(this);
            SafeToast.show(this, "已全局应用字体：" + name, Toast.LENGTH_SHORT);
            recreate();
        } catch (Exception error) {
            SafeToast.show(this, error.getMessage() == null ? "导入字体失败" : error.getMessage(),
                    Toast.LENGTH_LONG);
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
        sectionPages.clear();
        sectionButtons.clear();
        conciseSettingsMode = AppPreferences.conciseSettingsMode(this);
        View shell = getLayoutInflater().inflate(R.layout.activity_main, null, false);
        LinearLayout root = shell.findViewById(R.id.main_content);
        LinearLayout pageHost = shell.findViewById(R.id.main_page_host);
        mainScroll = shell.findViewById(R.id.main_scroll);
        MaterialToolbar toolbar = shell.findViewById(R.id.main_toolbar);
        toolbar.setTitle("歌词伴侣");
        toolbar.setSubtitle("主屏悬浮窗 · 副屏歌词");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        sectionHeading = shell.findViewById(R.id.main_section_heading);
        sectionDescription = shell.findViewById(R.id.main_section_description);
        sectionDescription.setLineSpacing(0f, 1.18f);

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
        addPermissionRow(accessCard, "音乐读取权限", true, v -> openNotificationAccess(),
                "悬浮窗权限", false, v -> openOverlayPermission());
        addPermissionRow(accessCard, "录音频谱权限", false, v -> requestRecordAudioPermission(),
                "蓝牙读取权限", false, v -> requestBluetoothPermission());
        addPermissionRow(accessCard, "使用情况访问", false, v -> openUsageAccessSettings(),
                "通知显示权限", false, v -> requestPostNotificationPermission());
        addPermissionRow(accessCard, "安装更新权限", false, v -> openUnknownAppSources(),
                "默认应用设置", false, v -> openDefaultAppsSettings());
        MaterialButton appPermissionSettings = button("应用权限 / 自启动设置", false);
        appPermissionSettings.setOnClickListener(v -> openApplicationDetails());
        LinearLayout.LayoutParams appPermissionParams = new LinearLayout.LayoutParams(-1, dp(48));
        appPermissionParams.topMargin = dp(8);
        accessCard.addView(appPermissionSettings, appPermissionParams);
        addRealSpectrumRateSelector(accessCard);

        LinearLayout lyricCard = card();
        lyricCard.addView(sectionLabel("歌词匹配"));
        MaterialSwitch playerCatalogFallback = toggle("回退到播放器同源词库");
        addLyricCatalogSelector(lyricCard, playerCatalogFallback);
        playerCatalogFallback.setChecked(AppPreferences.playerCatalogFallback(this));
        playerCatalogFallback.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_PLAYER_CATALOG_FALLBACK, checked).apply();
            MusicStateStore.reloadLyrics(this);
        });
        lyricCard.addView(playerCatalogFallback);
        MaterialSwitch localLyrics = toggle("优先匹配本地歌词（.lrc / 内嵌标签）");
        localLyrics.setChecked(AppPreferences.localLyricEnabled(this));
        localLyrics.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_LOCAL_LYRIC_ENABLED, checked).apply();
            MusicStateStore.reloadLyrics(this);
        });
        lyricCard.addView(localLyrics);
        MaterialButton localLyricDirectory = button("选择本地音乐目录", false);
        localLyricDirectory.setOnClickListener(v -> openLocalLyricDirectoryPicker());
        LinearLayout.LayoutParams localDirectoryParams = new LinearLayout.LayoutParams(-1, dp(48));
        localDirectoryParams.topMargin = dp(8);
        lyricCard.addView(localLyricDirectory, localDirectoryParams);
        MaterialButton localLyricPath = button("手动填写歌词目录路径", false);
        localLyricPath.setOnClickListener(v -> editLocalLyricDirectoryPath());
        lyricCard.addView(localLyricPath, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialSwitch avrcp = toggle("蓝牙 AVRCP 歌曲识别");
        avrcp.setChecked(AppPreferences.avrcpEnabled(this));
        avrcp.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_AVRCP_ENABLED, checked).apply();
            if (checked && Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                SafeToast.show(this, "请在首页“使用权限”中授予蓝牙读取权限",
                        Toast.LENGTH_LONG);
            } else if (!checked && BluetoothAvrcpReceiver.ownsCurrentState()) {
                MusicStateStore.clear();
            }
        });
        lyricCard.addView(avrcp);
        // issue #44：匹配到的歌词长时间不滚动时，改用播放器实时歌词。
        MaterialSwitch stuckFallback = toggle("匹配歌词长时间不滚动时改用播放器实时歌词");
        stuckFallback.setChecked(AppPreferences.stuckLyricFallback(this));
        stuckFallback.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setStuckLyricFallback(this, checked);
            MusicStateStore.reloadLyrics(this);
        });
        lyricCard.addView(stuckFallback);
        MaterialSwitch compositeIdentity = toggle("从歌手栏综合识别歌名（蓝牙/未知通道）");
        compositeIdentity.setChecked(AppPreferences.compositeIdentityFromArtist(this));
        compositeIdentity.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_COMPOSITE_IDENTITY_FROM_ARTIST, checked).apply();
        });
        lyricCard.addView(compositeIdentity);

        LinearLayout outputCard = card();
        outputCard.addView(sectionLabel("歌词显示开关"));
        mainOverlaySwitch = toggle("主屏悬浮窗",
                "可拖动，双击强制返回，长按锁定并开启触摸穿透，点圆形 × 恢复");
        mainOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_MAIN_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) showPermissionHomeHint("悬浮窗");
            AppPreferences.changed(this);
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(mainOverlaySwitch);
        secondaryOverlaySwitch = toggle("副屏歌词", "直接在选中的非默认 Display 上创建独立悬浮层");
        secondaryOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) showPermissionHomeHint("悬浮窗");
            AppPreferences.changed(this);
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(secondaryOverlaySwitch);

        LinearLayout startupCard = card();
        startupCard.addView(sectionLabel("启动与交互"));
        launchOverlaySwitch = toggle("点击图标启动悬浮窗",
                "首次点击图标恢复已记忆的歌词显示，30 秒内再次点击进入主界面");
        launchOverlaySwitch.setChecked(AppPreferences.launchOverlayOnIcon(this));
        launchOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_LAUNCH_OVERLAY_ON_ICON, checked)
                    .remove(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT)
                    .apply();
        });
        startupCard.addView(launchOverlaySwitch);
        autoStartSwitch = toggle("开机 / 亮屏自启动悬浮窗",
                "重启或每次亮屏时恢复已记忆的歌词显示；关闭服务并退出不影响此项");
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
        startupCard.addView(autoStartSwitch);
        MaterialSwitch returnToPlayer = toggle("轻触悬浮窗返回播放器",
                "关闭时打开歌词伴侣；无法打开播放器时会自动回到歌词伴侣");
        returnToPlayer.setChecked(AppPreferences.tapOverlayReturnsToPlayer(this));
        returnToPlayer.setOnCheckedChangeListener((button, checked) -> AppPreferences.get(this)
                .edit().putBoolean(AppPreferences.KEY_TAP_OVERLAY_RETURNS_TO_PLAYER, checked)
                .apply());
        startupCard.addView(returnToPlayer);
        addPlaybackControlToggles(startupCard);

        LinearLayout visibilityCard = card();
        visibilityCard.addView(sectionLabel("可见性与特殊输出"));
        MaterialButton visibilityRules = button("悬浮窗隐藏与自动隐藏规则", true);
        visibilityRules.setOnClickListener(v -> startActivity(
                new Intent(this, OverlayVisibilitySettingsActivity.class)));
        LinearLayout.LayoutParams visibilityRuleParams = new LinearLayout.LayoutParams(-1, dp(48));
        visibilityRuleParams.topMargin = dp(10);
        visibilityCard.addView(visibilityRules, visibilityRuleParams);

        MaterialSwitch topLyricStrip = toggle("通知栏显示歌词",
                "在桌面顶部透明显示双行歌词（本句/下句、居中、逐字高亮）；需悬浮窗权限，"
                        + "并会被图标启动和自启动记忆");
        topLyricStrip.setChecked(AppPreferences.topLyricStrip(this));
        outputCard.addView(topLyricStrip);
        topLyricStrip.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_TOP_LYRIC_STRIP, checked).apply();
            if (checked && !canDrawOverlays()) showPermissionHomeHint("悬浮窗");
            AppPreferences.changed(this);
        });
        MaterialSwitch bottomSpectrum = toggle("独立底部频谱条",
                "固定在屏幕底边，不随歌词悬浮窗移动；样式与主屏显示参数一致");
        bottomSpectrum.setChecked(AppPreferences.bottomSpectrum(this));
        bottomSpectrum.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_BOTTOM_SPECTRUM, checked).apply();
            if (checked && !canDrawOverlays()) showPermissionHomeHint("悬浮窗");
            AppPreferences.changed(this);
            LyricsDisplayService.startOrRefresh(this);
        });
        outputCard.addView(bottomSpectrum);
        MaterialButton statusLyricSettings = button("通知栏歌词详细设置", false);
        statusLyricSettings.setOnClickListener(v -> startActivity(
                new Intent(this, StatusLyricSettingsActivity.class)));
        LinearLayout.LayoutParams statusLyricSettingsParams = new LinearLayout.LayoutParams(-1, dp(48));
        statusLyricSettingsParams.topMargin = dp(8);
        visibilityCard.addView(statusLyricSettings, statusLyricSettingsParams);

        MaterialButton stopService = button("关闭服务并退出", false);
        stopService.setOnClickListener(v -> confirmStopServiceAndExit());
        LinearLayout.LayoutParams stopServiceParams = new LinearLayout.LayoutParams(-1, dp(48));
        stopServiceParams.topMargin = dp(12);
        startupCard.addView(stopService, stopServiceParams);

        LinearLayout screenCard = card();
        screenCard.addView(sectionLabel("副屏与位置"));
        TextView displayLabel = text("投屏屏幕", 13, 0xFF93A4B9, true);
        displayLabel.setPadding(0, dp(14), 0, dp(5));
        screenCard.addView(displayLabel);
        displaySpinner = new Spinner(this, Spinner.MODE_DIALOG);
        displaySpinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        screenCard.addView(displaySpinner, new LinearLayout.LayoutParams(-1, dp(52)));
        displayStatus = text("", 13, 0xFF8392A8, false);
        displayStatus.setPadding(0, dp(5), 0, 0);
        screenCard.addView(displayStatus);

        // Beyond the main overlay and the one secondary there was no way to drive a second extra
        // screen — a HUD next to the driving display, for instance. Any number of further
        // displays can now be switched on here, each with its own parameters.
        TextView extraLabel = text("其它屏幕同时显示", 13, 0xFF93A4B9, true);
        extraLabel.setPadding(0, dp(18), 0, 0);
        screenCard.addView(extraLabel);
        TextView extraHelp = text("除「投屏屏幕」外，还能让更多显示器各自显示歌词，各有一套样式与位置。"
                + "副屏选“自动选择”时建议先指定具体屏幕，避免同一块屏重复显示。",
                12, 0xFF74869D, false);
        extraHelp.setPadding(0, dp(4), 0, dp(6));
        screenCard.addView(extraHelp);
        extraDisplayHost = new LinearLayout(this);
        extraDisplayHost.setOrientation(LinearLayout.VERTICAL);
        screenCard.addView(extraDisplayHost);

        TextView joystickLabel = text("副屏位置微调", 13, 0xFF93A4B9, true);
        joystickLabel.setPadding(0, dp(16), 0, 0);
        screenCard.addView(joystickLabel);
        TextView joystickHelp = text("按住摇杆持续移动；松手后自动回中。副屏接入并开启后生效。", 12,
                0xFF74869D, false);
        joystickHelp.setPadding(0, dp(4), 0, dp(4));
        screenCard.addView(joystickHelp);
        // With several extra screens the joystick needs to know which one it is moving; with only
        // the secondary it stays the plain joystick it always was.
        joystickTargetHost = new LinearLayout(this);
        joystickTargetHost.setOrientation(LinearLayout.VERTICAL);
        screenCard.addView(joystickTargetHost);
        SecondaryPositionJoystickView joystick = new SecondaryPositionJoystickView(this);
        joystick.setListener((dx, dy) -> LyricsDisplayService.moveSecondaryBy(this, joystickSlot, dx, dy));
        LinearLayout.LayoutParams joystickParams = new LinearLayout.LayoutParams(dp(148), dp(148));
        joystickParams.gravity = Gravity.CENTER_HORIZONTAL;
        screenCard.addView(joystick, joystickParams);

        LinearLayout styleCard = card();
        styleCard.addView(sectionLabel("主屏 / 副屏 / 其它屏幕样式"));
        addStyleSelector(styleCard, "主屏悬浮窗样式", DisplaySlotRegistry.MAIN_SLOT);
        addStyleSelector(styleCard, "副屏歌词样式", DisplaySlotRegistry.SECONDARY_SLOT);
        extraStyleHost = new LinearLayout(this);
        extraStyleHost.setOrientation(LinearLayout.VERTICAL);
        styleCard.addView(extraStyleHost);
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
        addDisplaySettingsLaunchers(styleCard);

        LinearLayout appearanceCard = card();
        appearanceCard.addView(sectionLabel("颜色、主题与字体"));
        addThemeSelector(appearanceCard);
        MaterialButton colors = button("歌词颜色、描边与频谱颜色", true);
        colors.setOnClickListener(v -> startActivity(new Intent(this, ColorSettingsActivity.class)));
        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(-1, dp(48));
        colorParams.topMargin = dp(10);
        appearanceCard.addView(colors, colorParams);
        addGlobalFontControls(appearanceCard);

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
                "歌词伴侣基于 GPL-3.0 开源；Refined Now Playing、PiPWindow 与 Apple Music-like Lyrics 样式参考对应开源项目，并以原生 Android 重写。",
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

        LinearLayout resetCard = card();
        resetCard.addView(sectionLabel("数据与重置"));
        TextView resetSummary = text(
                "恢复显示、歌词、启动、频谱、蓝牙、目录和字体等默认设置；反馈记录与官方回复会保留。",
                12, 0xFF8392A8, false);
        resetSummary.setPadding(0, dp(9), 0, dp(10));
        resetCard.addView(resetSummary);
        MaterialButton resetSettings = button("恢复默认设置", false);
        resetSettings.setOnClickListener(v -> confirmResetSettings());
        resetCard.addView(resetSettings, new LinearLayout.LayoutParams(-1, dp(48)));

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
        MaterialButton configurationShare = button("配置分享码", false);
        configurationShare.setOnClickListener(v -> showConfigurationShareDialog());
        LinearLayout.LayoutParams configurationShareParams = new LinearLayout.LayoutParams(-1, dp(48));
        configurationShareParams.topMargin = dp(10);
        communityCard.addView(configurationShare, configurationShareParams);
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

        LinearLayout settingsModeCard = buildSettingsModeCard();
        LinearLayout quickControlCard = buildQuickControlCard();
        LinearLayout displayDirectoryCard = buildDisplayDirectoryCard();

        LinearLayout homePage = sectionPage();
        homePage.addView(settingsModeCard, cardMargins());
        if (conciseSettingsMode) {
            homePage.addView(previewCard, cardMargins());
            homePage.addView(quickControlCard, cardMargins());
        } else {
            homePage.addView(buildGettingStartedCard(), cardMargins());
            homePage.addView(accessCard, cardMargins());
            homePage.addView(outputCard, cardMargins());
            homePage.addView(previewCard, cardMargins());
        }

        LinearLayout displayPage = sectionPage();
        if (conciseSettingsMode) {
            TextView hint = text("只保留常用的尺寸、字号和透明度；颜色、描边、样式、位置、频谱等请切换到完整模式。", 13,
                    0xFF8392A8, false);
            hint.setPadding(0, dp(10), 0, dp(4));
            displayPage.addView(hint);
            displayPage.addView(buildQuickDisplayCard(), cardMargins());
        } else if (useSideNavigation()) {
            LinearLayout displayColumns = new LinearLayout(this);
            displayColumns.setOrientation(LinearLayout.HORIZONTAL);
            displayColumns.setBaselineAligned(false);
            LinearLayout leftColumn = sectionPage();
            LinearLayout rightColumn = sectionPage();
            leftColumn.addView(displayDirectoryCard, cardMargins());
            leftColumn.addView(screenCard, cardMargins());
            leftColumn.addView(visibilityCard, cardMargins());
            rightColumn.addView(styleCard, cardMargins());
            rightColumn.addView(appearanceCard, cardMargins());
            displayColumns.addView(leftColumn, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rightColumnParams =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            rightColumnParams.leftMargin = dp(14);
            displayColumns.addView(rightColumn, rightColumnParams);
            displayPage.addView(displayColumns, new LinearLayout.LayoutParams(-1, -2));
        } else {
            displayPage.addView(displayDirectoryCard, cardMargins());
            displayPage.addView(screenCard, cardMargins());
            displayPage.addView(visibilityCard, cardMargins());
            displayPage.addView(styleCard, cardMargins());
            displayPage.addView(appearanceCard, cardMargins());
        }

        LinearLayout lyricsPage = sectionPage();
        lyricsPage.addView(lyricCard, cardMargins());
        lyricsPage.addView(stateCard, cardMargins());

        LinearLayout systemPage = sectionPage();
        if (conciseSettingsMode) {
            systemPage.addView(accessCard, cardMargins());
            systemPage.addView(updateCard, cardMargins());
        } else {
            systemPage.addView(startupCard, cardMargins());
            systemPage.addView(communityCard, cardMargins());
            systemPage.addView(updateCard, cardMargins());
            systemPage.addView(resetCard, cardMargins());
            systemPage.addView(openSourceCard, cardMargins());
        }

        TextView footnote = text("提示：歌词优先读取系统媒体信息与音乐通知，通知不含进度时无法精准滚动。匹配优先复用本地缓存，仍不准确时可用“修正歌曲信息并重新匹配”。本应用不会向 iPhone CarPlay 仪表盘注入媒体信息。", 12,
                0xFF66788F, false);
        footnote.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams footnoteParams = new LinearLayout.LayoutParams(-1, -2);
        footnoteParams.topMargin = dp(16);
        systemPage.addView(footnote, footnoteParams);

        sectionPages.add(homePage);
        sectionPages.add(displayPage);
        sectionPages.add(lyricsPage);
        sectionPages.add(systemPage);
        for (View page : sectionPages) {
            pageHost.addView(page, new LinearLayout.LayoutParams(-1, -2));
        }

        bindSectionNavigation(shell);
        selectSection(Math.max(0, Math.min(selectedSection, sectionPages.size() - 1)));
        return shell;
    }

    private LinearLayout buildSettingsModeCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("设置模式"));
        TextView summary = text(conciseSettingsMode
                        ? "精简模式：仅显示日常开关和常用入口，减少车机上的滚动与干扰。"
                        : "完整模式：显示所有显示、样式、位置、颜色、歌词、诊断和系统选项。",
                13, 0xFFD8E1EE, false);
        summary.setPadding(0, dp(8), 0, dp(10));
        summary.setLineSpacing(0f, 1.18f);
        card.addView(summary);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton concise = button("精简模式", conciseSettingsMode);
        concise.setOnClickListener(v -> switchSettingsMode(true));
        row.addView(concise, weightedButton());
        MaterialButton complete = button("完整模式", !conciseSettingsMode);
        complete.setOnClickListener(v -> switchSettingsMode(false));
        LinearLayout.LayoutParams completeParams = weightedButton();
        completeParams.leftMargin = dp(10);
        row.addView(complete, completeParams);
        card.addView(row);
        addSettingsUiScaleSelector(card);
        return card;
    }

    private LinearLayout buildGettingStartedCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("第一次使用，按这 3 步"));
        TextView steps = text(
                "1  授予“音乐读取权限”和“悬浮窗权限”\n"
                        + "2  打开主屏、副屏或顶部歌词条\n"
                        + "3  在下方预览确认歌词，再到“显示”微调样式",
                13, 0xFFD8E1EE, false);
        steps.setLineSpacing(dp(4), 1.16f);
        steps.setPadding(0, dp(9), 0, dp(4));
        card.addView(steps);
        TextView expert = text("熟悉应用后，可直接使用顶部分类；每个显示对象都有独立参数入口。",
                12, 0xFF8392A8, false);
        expert.setPadding(0, dp(5), 0, 0);
        card.addView(expert);
        return card;
    }

    private LinearLayout buildDisplayDirectoryCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("按显示对象直达"));
        TextView summary = text("先选你想改变的对象；主屏和副屏的尺寸、字号等参数互不影响。",
                13, 0xFFD8E1EE, false);
        summary.setPadding(0, dp(8), 0, dp(10));
        card.addView(summary);

        LinearLayout screenRow = new LinearLayout(this);
        screenRow.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton main = button("主屏参数", true);
        main.setOnClickListener(v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, false)));
        screenRow.addView(main, weightedButton());
        MaterialButton secondary = button("副屏参数", false);
        secondary.setOnClickListener(v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, true)));
        LinearLayout.LayoutParams secondaryParams = weightedButton();
        secondaryParams.leftMargin = dp(10);
        screenRow.addView(secondary, secondaryParams);
        card.addView(screenRow);

        extraDisplayDirectoryHost = new LinearLayout(this);
        extraDisplayDirectoryHost.setOrientation(LinearLayout.VERTICAL);
        card.addView(extraDisplayDirectoryHost);

        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton topLyric = button("顶部歌词条", false);
        topLyric.setOnClickListener(v -> startActivity(
                new Intent(this, StatusLyricSettingsActivity.class)));
        detailRow.addView(topLyric, weightedButton());
        MaterialButton colors = button("颜色与描边", false);
        colors.setOnClickListener(v -> startActivity(new Intent(this, ColorSettingsActivity.class)));
        LinearLayout.LayoutParams colorsParams = weightedButton();
        colorsParams.leftMargin = dp(10);
        detailRow.addView(colors, colorsParams);
        LinearLayout.LayoutParams detailRowParams = new LinearLayout.LayoutParams(-1, dp(48));
        detailRowParams.topMargin = dp(8);
        card.addView(detailRow, detailRowParams);

        TextView map = text("继续向下：副屏与位置 · 样式参数 · 颜色与字体 · 隐藏规则",
                12, 0xFF8392A8, false);
        map.setPadding(0, dp(10), 0, 0);
        card.addView(map);
        return card;
    }

    private void switchSettingsMode(boolean concise) {
        if (concise == conciseSettingsMode) return;
        AppPreferences.setConciseSettingsMode(this, concise);
        selectedSection = 0;
        recreate();
    }

    /** A deliberately small surface for users who only need to turn lyrics on and adjust them. */
    private LinearLayout buildQuickControlCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("每天会用的控制"));
        card.addView(quickOverlayToggle("主屏悬浮歌词", AppPreferences.KEY_MAIN_OVERLAY,
                AppPreferences.mainEnabled(this), "在主屏显示歌词悬浮窗"));
        card.addView(quickOverlayToggle("副屏 / 仪表盘歌词", AppPreferences.KEY_SECONDARY_OVERLAY,
                AppPreferences.secondaryEnabled(this), "在选中的副屏独立显示歌词"));
        card.addView(quickOverlayToggle("顶部歌词条", AppPreferences.KEY_TOP_LYRIC_STRIP,
                AppPreferences.topLyricStrip(this), "在状态栏附近显示当前歌词"));
        MaterialButton displaySettings = button("调整主屏大小、字号和透明度", true);
        displaySettings.setOnClickListener(v -> startActivity(
                new Intent(this, QuickDisplaySettingsActivity.class)));
        LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-1, dp(48));
        displayParams.topMargin = dp(10);
        card.addView(displaySettings, displayParams);
        MaterialButton allSettings = button("查看全部设置（颜色、样式、频谱等）", false);
        allSettings.setOnClickListener(v -> switchToCompleteDisplaySettings());
        LinearLayout.LayoutParams allSettingsParams = new LinearLayout.LayoutParams(-1, dp(48));
        allSettingsParams.topMargin = dp(8);
        card.addView(allSettings, allSettingsParams);
        return card;
    }

    private LinearLayout buildQuickDisplayCard() {
        LinearLayout card = card();
        card.addView(sectionLabel("日常显示调整"));
        TextView summary = text("尺寸、字号和透明度是最常需要改动的参数；主屏与副屏互不影响。", 13,
                0xFFD8E1EE, false);
        summary.setPadding(0, dp(8), 0, dp(10));
        card.addView(summary);
        MaterialButton main = button("调整主屏显示", true);
        main.setOnClickListener(v -> startActivity(new Intent(this, QuickDisplaySettingsActivity.class)));
        card.addView(main, new LinearLayout.LayoutParams(-1, dp(48)));
        MaterialButton secondary = button("调整副屏 / 仪表盘显示", false);
        secondary.setOnClickListener(v -> startActivity(new Intent(this, QuickDisplaySettingsActivity.class)
                .putExtra(QuickDisplaySettingsActivity.EXTRA_SECONDARY, true)));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(-1, dp(48));
        secondaryParams.topMargin = dp(8);
        card.addView(secondary, secondaryParams);
        MaterialButton complete = button("需要更多显示设置", false);
        complete.setOnClickListener(v -> switchToCompleteDisplaySettings());
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(-1, dp(48));
        completeParams.topMargin = dp(8);
        card.addView(complete, completeParams);
        // A HUD next to the driving display is a display-object question, so it lives with the
        // per-display parameters rather than in the everyday card.
        MaterialButton moreScreens = button("在更多屏幕上同时显示（HUD、后座屏等）", false);
        moreScreens.setOnClickListener(v -> switchToCompleteDisplaySettings());
        LinearLayout.LayoutParams moreScreensParams = new LinearLayout.LayoutParams(-1, dp(48));
        moreScreensParams.topMargin = dp(8);
        card.addView(moreScreens, moreScreensParams);
        return card;
    }

    private void switchToCompleteDisplaySettings() {
        AppPreferences.setConciseSettingsMode(this, false);
        selectedSection = 1;
        recreate();
    }

    private MaterialSwitch quickOverlayToggle(String title, String key, boolean enabled,
                                               String description) {
        MaterialSwitch toggle = toggle(title, description);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            if (checked && !canDrawOverlays()) showPermissionHomeHint("悬浮窗");
            AppPreferences.setServiceStoppedByUser(this, false);
            AppPreferences.changed(this);
            AudioSpectrumSource.sync(this);
        });
        return toggle;
    }

    private LinearLayout sectionPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        return page;
    }

    private void bindSectionNavigation(View shell) {
        int[] ids = {R.id.nav_home, R.id.nav_display, R.id.nav_lyrics, R.id.nav_system};
        for (int index = 0; index < ids.length; index++) {
            final int section = index;
            MaterialButton item = shell.findViewById(ids[index]);
            item.setText(navigationLabel(index));
            item.setTextSize(12f);
            item.setMinWidth(0);
            item.setMinimumWidth(0);
            item.setMinHeight(0);
            item.setMinimumHeight(0);
            item.setCornerRadius(dp(16));
            item.setContentDescription("打开" + navigationLabel(index) + "分类");
            item.setOnClickListener(v -> selectSection(section));
            sectionButtons.add(item);
        }
    }

    private void selectSection(int section) {
        if (section < 0 || section >= sectionPages.size()) return;
        boolean sectionChanged = selectedSection != section;
        selectedSection = section;
        for (int index = 0; index < sectionPages.size(); index++) {
            sectionPages.get(index).setVisibility(index == section ? View.VISIBLE : View.GONE);
        }
        for (int index = 0; index < sectionButtons.size(); index++) {
            boolean selected = index == section;
            MaterialButton item = sectionButtons.get(index);
            item.setSelected(selected);
            item.setTextColor(selected ? 0xFF07111F : 0xFFA9B6C8);
            item.setBackgroundTintList(ColorStateList.valueOf(
                    selected ? 0xFF6EE7F2 : Color.TRANSPARENT));
        }
        if (sectionHeading != null) sectionHeading.setText(sectionTitle(section));
        if (sectionDescription != null) {
            sectionDescription.setText(sectionDescription(section));
        }
        if (sectionChanged && mainScroll != null) {
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        }
    }

    private String sectionTitle(int section) {
        if (!conciseSettingsMode) return SECTION_TITLES[section];
        String[] titles = {"每天会用", "日常显示调整", "歌词与匹配", "权限与更新"};
        return titles[section];
    }

    private String navigationLabel(int section) {
        if (!conciseSettingsMode) return SECTION_LABELS[section];
        String[] labels = {"常用", "显示", "歌词", "维护"};
        return labels[section];
    }

    private String sectionDescription(int section) {
        if (!conciseSettingsMode) return SECTION_DESCRIPTIONS[section];
        String[] descriptions = {
                "开关主屏、副屏或顶部歌词，并快速进入日常调整",
                "只调整尺寸、字号和透明度，不显示颜色等专家参数",
                "选择歌词来源并查看当前播放状态",
                "完成权限、查看更新；诊断与更多选项在完整模式中提供"
        };
        return descriptions[section];
    }

    private void confirmStopServiceAndExit() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关闭歌词服务")
                .setMessage("将移除所有悬浮歌词并停止音乐监听。若保留开机/亮屏自启动，会留下最小启动待命服务以接收亮屏事件；关闭该选项才会完全停止所有服务。")
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

    /**
     * One screen's style picker. Slot 0 is the main overlay, slot 1 the 副屏, and every further
     * slot an extra screen, which reads and writes its own store through {@link DisplaySlotContext}.
     */
    private void addStyleSelector(LinearLayout parent, String title, int slot) {
        final boolean secondary = slot > DisplaySlotRegistry.MAIN_SLOT;
        final Context styleContext = slot >= DisplaySlotRegistry.FIRST_EXTRA_SLOT
                ? new DisplaySlotContext(this, slot) : this;
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(6));
        parent.addView(label);
        String[] labels = {"Refined Now Playing", "Apple Music-like Lyrics", "歌词伴侣经典样式", "紧凑歌词", "PiPWindow", "纯净歌词"};
        String[] values = {"refined", "amll", "default", "compact", "pip", "pure"};
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        String saved = AppPreferences.overlayStyle(styleContext, secondary);
        int selection = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selection = i;
        spinner.setSelection(selection, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView,
                                                 View view, int position, long id) {
                if (values[position].equals(
                        AppPreferences.overlayStyle(styleContext, secondary))) return;
                AppPreferences.setOverlayStyle(styleContext, secondary, values[position]);
                updateRefinedSettingsVisibility();
                if (!secondary) refreshPreview();
                AppPreferences.changed(MainActivity.this);
                if (!secondary) recreate();
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView help = text(secondary
                        ? (slot >= DisplaySlotRegistry.FIRST_EXTRA_SLOT
                        ? "本屏样式只影响这块屏幕，与主屏、副屏互不影响。"
                        : "副屏可独立选择样式。")
                        : "Refined、Apple Music-like Lyrics 和 PiPWindow 为独立样式；经典样式保留默认布局。",
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
        for (ExtraStyleRow row : extraStyleRows) {
            String style = AppPreferences.overlayStyle(new DisplaySlotContext(this, row.slot), true);
            row.refined.setVisibility("refined".equals(style) ? View.VISIBLE : View.GONE);
            row.compact.setVisibility("compact".equals(style) ? View.VISIBLE : View.GONE);
        }
    }

    /** One extra screen's style picker and the shortcuts that belong to the style it is on. */
    private static final class ExtraStyleRow {
        final int slot;
        final MaterialButton refined;
        final MaterialButton compact;

        ExtraStyleRow(int slot, MaterialButton refined, MaterialButton compact) {
            this.slot = slot;
            this.refined = refined;
            this.compact = compact;
        }
    }

    private void addLyricCatalogSelector(LinearLayout parent,
                                         MaterialSwitch playerCatalogFallback) {
        TextView label = text("默认匹配词库", 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(6));
        parent.addView(label);
        String[] labels = {"自动识别播放器", "网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "咪咕音乐"};
        String[] values = {"auto", "netease", "qqmusic", "kugou", "kuwo", "soda", "migu"};
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
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
        TextView help = text("这是未单独设置播放器时的默认规则：自动模式优先使用识别出的播放器同源词库，手动模式先试所选词库；无结果后才依次查询下一词库。",
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
        TextView note = text("选择一个词库后可从所有已安装应用中多选；被选中的应用只从该词库匹配，未选择的应用沿用上方默认规则。",
                13, 0xFF74869D, false);
        note.setLineSpacing(0f, 1.2f);
        content.addView(note);
        String[] labels = {"网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "咪咕音乐"};
        String[] catalogs = {"netease", "qqmusic", "kugou", "kuwo", "soda", "migu"};
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
                .setTitle(catalogLabel + "词库 · 强制匹配")
                .setView(content)
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
                    SafeToast.show(this, "已保存 " + catalogLabel + " 强制匹配应用", Toast.LENGTH_SHORT);
                })
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO || requestCode == REQUEST_BLUETOOTH_CONNECT
                || requestCode == 101) {
            AudioSpectrumSource.sync(this);
            LyricsDisplayService.startOrRefresh(this);
            refreshPreview();
            refreshStatus();
        } else if (requestCode == REQUEST_LOCAL_LYRIC_STORAGE) {
            SafeToast.show(this, LocalLyricClient.canReadManualDirectory(this)
                    ? "已授予存储读取权限，正在重新读取本地歌词"
                    : LocalLyricClient.manualDirectorySaveMessage(this), Toast.LENGTH_LONG);
            MusicStateStore.reloadLyrics(this);
        }
    }

    private void refreshStatus() {
        boolean notificationAccess = hasNotificationAccess();
        boolean overlay = canDrawOverlays();
        boolean recordAudio = Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean bluetooth = Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        boolean postNotifications = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean usageAccess = ForegroundAppDetector.hasUsageAccess(this);
        boolean installUpdates = Build.VERSION.SDK_INT < 26
                || getPackageManager().canRequestPackageInstalls();
        String listenerState = listenerState(notificationAccess);
        permissionStatus.setText("通知读取  " + (notificationAccess ? "已授权" : "未授权")
                + "     监听器  " + listenerState
                + "     悬浮窗  " + permissionState(overlay)
                + "\n录音频谱  " + permissionState(recordAudio)
                + "     蓝牙读取  " + permissionState(bluetooth)
                + "     通知显示  " + permissionState(postNotifications)
                + "\n使用情况  " + permissionState(usageAccess)
                + "     安装更新  " + permissionState(installUpdates));
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
        ThemedSpinnerAdapter<DisplayChoice> adapter = new ThemedSpinnerAdapter<>(this, choices);
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
        refreshExtraDisplayChoices();
    }

    private void updateDisplayStatus(int count, DisplayChoice selected) {
        if (count == 0) {
            displayStatus.setText("当前未检测到副屏；接入 HDMI、虚拟屏或车机仪表屏后会自动出现。");
        } else {
            displayStatus.setText("检测到 " + count + " 块副屏 · 当前：" + selected.label);
        }
    }

    /**
     * Rows for every connected non-default display plus any saved extra screen that is currently
     * unplugged — the latter stay listed so they can be switched off or adjusted without having to
     * reconnect them. A screen that is switched off keeps its place in the list, so the slots of
     * the screens after it never move.
     */
    private void refreshExtraDisplayChoices() {
        if (extraDisplayHost == null && extraDisplayDirectoryHost == null) return;
        List<DisplaySlotRegistry.Entry> entries =
                new ArrayList<>(DisplaySlotRegistry.entries(this));
        List<Display> connected = new ArrayList<>();
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager != null) {
            for (Display display : manager.getDisplays()) {
                if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY) continue;
                connected.add(display);
            }
        }

        if (extraDisplayHost != null) {
            extraDisplayHost.removeAllViews();
            // 每块屏解析一次；「已启用且已连接」的屏另外记一份快照，用来看两行是不是同一块
            // 物理屏（issue #23）。未连接的屏没有名字与分辨率，既不参与判断也不出提示。
            ExtraScreenOverlap overlap = new ExtraScreenOverlap();
            Display[] resolved = new Display[entries.size()];
            int[] panelIndex = new int[entries.size()];
            for (int index = 0; index < entries.size(); index++) {
                resolved[index] = findConnectedDisplay(entries.get(index));
                panelIndex[index] = -1;
                if (resolved[index] != null && entries.get(index).enabled) {
                    panelIndex[index] = overlap.remember(resolved[index]);
                }
            }
            for (int index = 0; index < entries.size(); index++) {
                addExtraDisplayRow(extraDisplayHost, entries.get(index), index, resolved[index],
                        overlap.hint(panelIndex[index], resolved[index]));
            }
            for (Display display : connected) {
                if (indexOfDisplay(entries, display) >= 0) continue;
                addNewDisplayRow(extraDisplayHost, display, overlap.hint(-1, display));
            }
            if (extraDisplayHost.getChildCount() == 0) {
                TextView empty = text("当前没有检测到其它屏幕；接入后会自动出现在这里。",
                        12, 0xFF8392A8, false);
                empty.setPadding(0, dp(6), 0, 0);
                extraDisplayHost.addView(empty);
            }
            String overlapNote = overlap.summary();
            if (!overlapNote.isEmpty()) {
                TextView note = text(overlapNote, 12, 0xFF74869D, false);
                note.setPadding(0, dp(8), 0, 0);
                extraDisplayHost.addView(note);
            }
        }

        if (extraDisplayDirectoryHost != null) {
            extraDisplayDirectoryHost.removeAllViews();
            for (int index = 0; index < entries.size(); index++) {
                DisplaySlotRegistry.Entry entry = entries.get(index);
                if (!entry.enabled) continue;
                int slot = DisplaySlotRegistry.slotFor(index);
                MaterialButton button = button(DisplaySlotRegistry.slotLabel(this, slot) + "参数",
                        false);
                button.setOnClickListener(v -> openSlotSettings(slot));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
                params.topMargin = dp(8);
                extraDisplayDirectoryHost.addView(button, params);
            }
        }

        refreshExtraStyleSelectors(entries);
        refreshJoystickTarget(entries);
    }

    /**
     * One style picker per extra screen, each writing into that screen's own store, plus the
     * shortcut to its style-specific page while that style is selected.
     */
    private void refreshExtraStyleSelectors(List<DisplaySlotRegistry.Entry> entries) {
        if (extraStyleHost == null) return;
        extraStyleHost.removeAllViews();
        extraStyleRows.clear();
        for (int index = 0; index < entries.size(); index++) {
            DisplaySlotRegistry.Entry entry = entries.get(index);
            if (!entry.enabled) continue;
            final int slot = DisplaySlotRegistry.slotFor(index);
            String label = DisplaySlotRegistry.slotLabel(this, slot);
            addStyleSelector(extraStyleHost, label + "样式", slot);

            final Context styleContext = new DisplaySlotContext(this, slot);
            MaterialButton refined = button(label + " Refined Now Playing 详细设置", false);
            refined.setOnClickListener(v -> startActivity(
                    new Intent(this, RefinedSettingsActivity.class)
                            .putExtra(RefinedSettingsActivity.EXTRA_SECONDARY, true)
                            .putExtra(DisplaySlotContext.EXTRA_SLOT, slot)));
            LinearLayout.LayoutParams refinedParams = new LinearLayout.LayoutParams(-1, dp(50));
            refinedParams.topMargin = dp(10);
            extraStyleHost.addView(refined, refinedParams);

            MaterialButton compact = button(label + "紧凑歌词详细设置", false);
            compact.setOnClickListener(v -> startActivity(
                    new Intent(this, CompactSettingsActivity.class)
                            .putExtra(CompactSettingsActivity.EXTRA_SECONDARY, true)
                            .putExtra(DisplaySlotContext.EXTRA_SLOT, slot)));
            extraStyleHost.addView(compact, new LinearLayout.LayoutParams(-1, dp(50)));

            MaterialButton display = button(label + "显示参数", false);
            display.setOnClickListener(v -> openSlotSettings(slot));
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-1, dp(50));
            displayParams.topMargin = dp(10);
            extraStyleHost.addView(display, displayParams);

            ExtraStyleRow row = new ExtraStyleRow(slot, refined, compact);
            extraStyleRows.add(row);
            String style = AppPreferences.overlayStyle(styleContext, true);
            refined.setVisibility("refined".equals(style) ? View.VISIBLE : View.GONE);
            compact.setVisibility("compact".equals(style) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The joystick moves one screen. With extra screens on, a picker says which one; the choice is
     * written into the extras list so it survives a restart, and the label follows it.
     */
    private void refreshJoystickTarget(List<DisplaySlotRegistry.Entry> entries) {
        if (joystickTargetHost == null) return;
        joystickTargetHost.removeAllViews();
        List<Integer> slots = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        slots.add(DisplaySlotRegistry.SECONDARY_SLOT);
        labels.add("副屏 / 仪表盘");
        for (int index = 0; index < entries.size(); index++) {
            if (!entries.get(index).enabled) continue;
            int slot = DisplaySlotRegistry.slotFor(index);
            slots.add(slot);
            labels.add(DisplaySlotRegistry.slotLabel(this, slot));
        }
        if (slots.size() == 1) {
            joystickSlot = DisplaySlotRegistry.SECONDARY_SLOT;
            return;
        }
        int saved = AppPreferences.joystickSlot(this);
        int selection = 0;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) == saved) selection = i;
        joystickSlot = slots.get(selection);
        TextView label = text("摇杆调整的屏幕", 13, 0xFF93A4B9, true);
        label.setPadding(0, dp(10), 0, dp(4));
        joystickTargetHost.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        spinner.setSelection(selection, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View view,
                                                 int position, long id) {
                joystickSlot = slots.get(position);
                AppPreferences.setJoystickSlot(MainActivity.this, joystickSlot);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        joystickTargetHost.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addExtraDisplayRow(LinearLayout host, DisplaySlotRegistry.Entry entry, int index,
                                    Display display, String overlapHint) {
        String label = display != null ? displayLabel(display) + overlapHint
                : entry.describe() + "（未连接）";
        LinearLayout row = extraDisplayRow(label, entry.enabled,
                checked -> setExtraDisplayEnabled(index, null, checked));
        host.addView(row);
        if (entry.enabled) {
            int slot = DisplaySlotRegistry.slotFor(index);
            MaterialButton params = button("调整“" + DisplaySlotRegistry.slotLabel(this, slot)
                    + "”的样式与位置", false);
            params.setOnClickListener(v -> openSlotSettings(slot));
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(42));
            buttonParams.topMargin = dp(2);
            host.addView(params, buttonParams);
        }
    }

    private void addNewDisplayRow(LinearLayout host, Display display, String overlapHint) {
        String label = displayLabel(display) + "（未显示歌词）" + overlapHint;
        host.addView(extraDisplayRow(label, false,
                checked -> setExtraDisplayEnabled(-1, display, checked)));
    }

    /** 一行屏幕的标签：名字加显示 ID，和「投屏屏幕」下拉里的写法一致。 */
    private static String displayLabel(Display display) {
        return display.getName() + "  ·  ID " + display.getDisplayId();
    }

    /**
     * 一块屏的名字与分辨率快照；判断本身由不依赖 Android 类型的 DisplayIdentity 完成。
     *
     * <p>{@code getRealMetrics} 在新 SDK 上标了过时，但它是 minSdk 19 上不必为投屏通道新建 Context
     * 的取法，也不会因为某块屏不让建 Context 而失败；与仓库里其它量屏幕尺寸的地方一致。
     */
    private static DisplayIdentity.Screen panelOf(Display display) {
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        return new DisplayIdentity.Screen(display.getName(), metrics.widthPixels,
                metrics.heightPixels, metrics.densityDpi);
    }

    /**
     * 「已启用且已连接」的屏幕清单，用来在两行之间认出同一块物理屏（issue #23）。
     *
     * <p>有的车机把同一块屏暴露成多个 Display（投屏通道，如 {@code ..._0} 与 {@code ..._1}）。
     * 两块都开启只会在同一块屏上叠两层歌词；这里只提示，不合并、不隐藏，也不写任何设置，用户想
     * 两块一起开仍然可以。每次刷新列表都重建，全是纯计算，不弹 Toast。
     */
    private static final class ExtraScreenOverlap {
        private final List<DisplayIdentity.Screen> panels = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();

        /** 记下一块已启用的屏，返回它在清单里的下标（判断时用来跳过它自己）。 */
        int remember(Display display) {
            panels.add(panelOf(display));
            labels.add(displayLabel(display));
            return panels.size() - 1;
        }

        /**
         * 这一行的重叠提示。
         *
         * @param self   该行在清单里的下标，-1 表示它本身还没启用
         * @param display 该行连接的屏幕，未连接时为 null（不猜）
         */
        String hint(int self, Display display) {
            if (display == null) return "";
            DisplayIdentity.Screen panel = panelOf(display);
            for (int index = 0; index < panels.size(); index++) {
                if (index == self) continue;
                String reason = DisplayIdentity.duplicateReason(panel, panels.get(index));
                if (reason.isEmpty()) continue;
                return " · 可能是同一块屏（与「" + labels.get(index) + "」" + reason + "）";
            }
            return "";
        }

        /** 已启用的屏幕里互相疑似同一块时的列表说明；没有重叠、或只有一块屏时返回空串。 */
        String summary() {
            boolean[] involved = new boolean[panels.size()];
            String reason = "";
            int count = 0;
            for (int left = 0; left < panels.size(); left++) {
                for (int right = left + 1; right < panels.size(); right++) {
                    String found = DisplayIdentity.duplicateReason(panels.get(left),
                            panels.get(right));
                    if (found.isEmpty()) continue;
                    if (reason.isEmpty()) reason = found;
                    involved[left] = true;
                    involved[right] = true;
                }
            }
            for (boolean hit : involved) {
                if (hit) count++;
            }
            if (count == 0) return "";
            return "检测到 " + count + " 块屏幕疑似同一块（" + reason
                    + "）：同一块屏叠两层歌词只会更粗更亮，还会重复渲染，建议只留一块。";
        }
    }

    /** One screen row: its name plus the switch that turns its own overlay on or off. */
    private LinearLayout extraDisplayRow(String label, boolean checked,
                                         BooleanConsumer onChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, 0);
        TextView name = text(label, 13, 0xFFD8E1EE, false);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(label);
        toggle.setOnCheckedChangeListener((button, value) -> {
            if (bindingUi) return;
            onChanged.accept(value);
        });
        row.addView(toggle);
        return row;
    }

    /**
     * @param index   position of an existing entry, or -1 when {@code display} is being added
     * @param display the connected screen being added, or null when switching an entry off
     */
    private void setExtraDisplayEnabled(int index, Display display, boolean enabled) {
        List<DisplaySlotRegistry.Entry> entries =
                new ArrayList<>(DisplaySlotRegistry.entries(this));
        if (index >= 0 && index < entries.size()) {
            entries.set(index, entries.get(index).withEnabled(enabled));
        } else if (enabled && display != null) {
            int existing = indexOfDisplay(entries, display);
            if (existing >= 0) {
                entries.set(existing, entries.get(existing).withEnabled(true));
            } else {
                entries.add(DisplaySlotRegistry.of(display));
            }
        }
        DisplaySlotRegistry.putEntries(this, entries);
        AppPreferences.changed(this);
        refreshExtraDisplayChoices();
        SafeToast.show(this, enabled ? "已在该屏幕显示歌词" : "已在该屏幕关闭歌词",
                Toast.LENGTH_SHORT);
    }

    private Display findConnectedDisplay(DisplaySlotRegistry.Entry entry) {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        return DisplaySlotRegistry.resolve(entry, manager);
    }

    private static int indexOfDisplay(List<DisplaySlotRegistry.Entry> entries, Display display) {
        for (int index = 0; index < entries.size(); index++) {
            DisplaySlotRegistry.Entry entry = entries.get(index);
            if (display.getDisplayId() == entry.displayId
                    || display.getName().equals(entry.name)) {
                return index;
            }
        }
        return -1;
    }

    /** Opens the parameter page of one screen: 0 = 主屏, 1 = 副屏, 2+ = an extra screen. */
    private void openSlotSettings(int slot) {
        startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY,
                        slot > DisplaySlotRegistry.MAIN_SLOT)
                .putExtra(DisplaySlotContext.EXTRA_SLOT, slot));
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
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
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
                // 时间段开关打开时按当前时间换算成白天/夜晚，再交给 Material 主题（issue #34）。
                LyricsCompanionApp.applyMaterialTheme(
                        AppPreferences.resolvedThemeMode(MainActivity.this));
                AppPreferences.changed(MainActivity.this);
                refreshStatus();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView mainThemeNote = text("主界面固定使用黑色主题；此选项只影响悬浮歌词背景与已开启跟随的自动歌词色。", 12,
                0xFF74869D, false);
        mainThemeNote.setPadding(0, dp(3), 0, dp(2));
        parent.addView(mainThemeNote);

        // 可选的时间段（issue #34）：打开后按下面的深色时段自动切换；关掉就按上面的模式判断，
        // 「跟随系统」即由系统的深浅色决定。
        MaterialSwitch schedule = toggle("按时间段自动切换深浅色",
                "开启后按下面设定的时段使用夜晚配色，其余时间用白天配色；关闭则按上面的模式判断"
                        + "（起止时间相同表示不启用）");
        View startGroup = addThemeScheduleHour(parent, "深色开始（整点）", true);
        View endGroup = addThemeScheduleHour(parent, "深色结束（整点）", false);
        View[] scheduleRows = { startGroup, endGroup };
        schedule.setChecked(AppPreferences.themeScheduleEnabled(this));
        schedule.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setThemeScheduleEnabled(MainActivity.this, checked);
            for (View row : scheduleRows) {
                row.setVisibility(checked ? View.VISIBLE : View.GONE);
            }
            LyricsCompanionApp.applyMaterialTheme(
                    AppPreferences.resolvedThemeMode(MainActivity.this));
            AppPreferences.changed(MainActivity.this);
        });
        parent.addView(schedule);
        for (View row : scheduleRows) {
            row.setVisibility(schedule.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 时间段配色的起止整点下拉（issue #34）；跨零点（如 19 → 7）按深色时段处理。
     *
     * @return the group holding the label and the spinner, so the switch above can hide it
     */
    private View addThemeScheduleHour(LinearLayout parent, String title, boolean start) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(title, 13, 0xFF93A4B9, true);
        label.setPadding(0, dp(8), 0, 0);
        group.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        String[] hours = new String[24];
        for (int hour = 0; hour < 24; hour++) {
            hours[hour] = String.format(java.util.Locale.ROOT, "%02d:00", hour);
        }
        spinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, hours));
        int selected = (start ? AppPreferences.themeScheduleStartMinute(this)
                : AppPreferences.themeScheduleEndMinute(this)) / 60;
        spinner.setSelection(Math.max(0, Math.min(23, selected)), false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                  View view, int position, long id) {
                int minute = position * 60;
                int stored = start ? AppPreferences.themeScheduleStartMinute(MainActivity.this)
                        : AppPreferences.themeScheduleEndMinute(MainActivity.this);
                if (stored == minute) return;
                if (start) AppPreferences.setThemeScheduleStartMinute(MainActivity.this, minute);
                else AppPreferences.setThemeScheduleEndMinute(MainActivity.this, minute);
                LyricsCompanionApp.applyMaterialTheme(
                        AppPreferences.resolvedThemeMode(MainActivity.this));
                AppPreferences.changed(MainActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        group.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        if (!start) {
            TextView note = text("深色开始与结束相同表示不启用该时段（始终白天）。", 12,
                    0xFF74869D, false);
            note.setPadding(0, dp(3), 0, 0);
            group.addView(note);
        }
        parent.addView(group);
        return group;
    }

    /** Enlarges controls on low-density automotive screens without affecting lyric typography. */
    private void addSettingsUiScaleSelector(LinearLayout parent) {
        TextView label = sectionLabel("设置界面缩放");
        label.setPadding(0, dp(16), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        String[] labels = {"标准（100%）", "大号（150%）", "特大（200%）"};
        int[] values = {100, 150, 200};
        spinner.setPopupBackgroundDrawable(solid(0xFF132238, 14));
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        int current = AppPreferences.settingsUiScale(this);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                spinner.setSelection(i, false);
                break;
            }
        }
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                  View view, int position, long id) {
                if (values[position] == AppPreferences.settingsUiScale(MainActivity.this)) return;
                AppPreferences.setSettingsUiScale(MainActivity.this, values[position]);
                recreate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView note = text("仅放大设置页面的文字与控件，不改变主屏、副屏或通知栏歌词字号。", 12,
                0xFF74869D, false);
        note.setPadding(0, dp(3), 0, dp(2));
        parent.addView(note);
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

    /** A switch that shows only its title, without a description under it. */
    private MaterialSwitch toggle(String title) {
        return toggle(title, "");
    }

    private MaterialSwitch toggle(String title, String subtitle) {
        MaterialSwitch view = new MaterialSwitch(this);
        view.setText(subtitle == null || subtitle.isEmpty() ? title : title + "\n" + subtitle);
        view.setTextColor(0xFFF3F7FC);
        view.setTextSize(14f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(12), 0, dp(6));
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private void addSupportControls(LinearLayout parent) {
        MaterialSwitch crashUpload = toggle("自动上传闪退诊断",
                "含设备型号、系统与权限、播放器包名、曲目元数据及播放/歌词状态；不含歌词正文、通知正文或设备标识；默认关闭");
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
            SafeToast.show(this, "命令已复制", Toast.LENGTH_SHORT);
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
            SafeToast.show(this, "当前没有可重新匹配的曲目", Toast.LENGTH_SHORT);
            return;
        }
        String[] labels = {"自动识别", "网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "咪咕音乐"};
        String[] catalogs = {"auto", "netease", "qqmusic", "kugou", "kuwo", "soda", "migu"};
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
        catalogSpinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
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
                    SafeToast.show(this, "已按修正后的歌曲信息开始匹配",
                            Toast.LENGTH_SHORT);
                })
                .show();
    }

    private void uploadDiagnosticSnapshotFor(String feedbackId) {
        if (diagnosticBusy) return;
        diagnosticBusy = true;
        CommunityClient.uploadSnapshotAsync(this, feedbackId, result -> runOnUiThread(() -> {
            diagnosticBusy = false;
            if (isFinishing() || isDestroyed()) return;
            SafeToast.show(this, result.success ? "诊断快照已上传" : "诊断上传失败：" + result.error,
                    Toast.LENGTH_LONG);
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
                SafeToast.show(this, "无法读取回复：" + result.error, Toast.LENGTH_LONG);
                return;
            }
            if (result.replies.isEmpty()) {
                SafeToast.show(this, "暂时没有收到回复", Toast.LENGTH_SHORT);
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
        float availableWidthDp = screenWidthDp - (useSideNavigation() ? 104f : 0f) - 72f;
        float aspectHeightDp = availableWidthDp * AppPreferences.panelHeightDp(this)
                / (float) AppPreferences.panelWidthDp(this);
        return dp(Math.max(AppPreferences.minimumPanelHeightDp(this),
                Math.min(420f, aspectHeightDp)));
    }

    private boolean useSideNavigation() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
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
        SafeToast.show(this, "此设备没有可用的文件选择器，请安装或启用系统文件管理器后重试。",
                Toast.LENGTH_LONG);
    }

    private void openLocalLyricDirectoryPicker() {
        if (Build.VERSION.SDK_INT < 21) {
            SafeToast.show(this, "Android 4.4 会直接尝试歌曲同目录；无需选择目录。",
                    Toast.LENGTH_LONG);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                SafeToast.show(this, "此设备没有目录选择器。将 .lrc 与歌曲放在同一目录即可直接匹配，无需授权。",
                        Toast.LENGTH_LONG);
                return;
            }
            startActivityForResult(intent, REQUEST_LOCAL_LYRIC_DIRECTORY);
        } catch (Throwable error) {
            SafeToast.show(this, "无法打开目录选择器。将 .lrc 与歌曲放在同一目录即可直接匹配，无需授权。",
                    Toast.LENGTH_LONG);
        }
    }

    private void editLocalLyricDirectoryPath() {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("例如 /storage/XXXX-XXXX/Music");
        layout.setPadding(dp(20), 0, dp(20), 0);
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setText(AppPreferences.localLyricDirectoryPath(this));
        layout.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("手动填写本地歌词目录")
                .setMessage("用于没有系统目录选择器的车机：只在该目录及其子目录查找 .lrc，路径不会写入配置分享码。"
                        + LocalLyricClient.manualDirectoryRequirementNote(this))
                .setView(layout)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", (dialog, which) -> {
                    AppPreferences.get(this).edit()
                            .remove(AppPreferences.KEY_LOCAL_LYRIC_DIRECTORY_PATH).apply();
                    MusicStateStore.reloadLyrics(this);
                })
                .setPositiveButton("保存", (dialog, which) -> {
                    String path = input.getText() == null ? "" : input.getText().toString().trim();
                    AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_LOCAL_LYRIC_DIRECTORY_PATH, path).apply();
                    MusicStateStore.reloadLyrics(this);
                    if (path.isEmpty()) {
                        SafeToast.show(this, "已清除手动歌词目录", Toast.LENGTH_SHORT);
                    } else if (!LocalLyricClient.requestManualDirectoryAccess(this,
                            REQUEST_LOCAL_LYRIC_STORAGE)) {
                        // 无需申请或系统不再支持该权限时立刻给出结论；申请时由
                        // onRequestPermissionsResult 收尾，权限被拒绝也不会看起来还能用。
                        SafeToast.show(this, LocalLyricClient.manualDirectorySaveMessage(this),
                                Toast.LENGTH_LONG);
                    }
                }).show();
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

    private void addPermissionRow(LinearLayout parent, String firstLabel, boolean firstPrimary,
                                  View.OnClickListener firstAction, String secondLabel,
                                  boolean secondPrimary, View.OnClickListener secondAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton first = button(firstLabel, firstPrimary);
        first.setOnClickListener(firstAction);
        row.addView(first, weightedButton());
        MaterialButton second = button(secondLabel, secondPrimary);
        second.setOnClickListener(secondAction);
        LinearLayout.LayoutParams secondParams = weightedButton();
        secondParams.leftMargin = dp(10);
        row.addView(second, secondParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
        if (parent.getChildCount() > 2) rowParams.topMargin = dp(8);
        parent.addView(row, rowParams);
    }

    private void addRealSpectrumRateSelector(LinearLayout parent) {
        TextView label = text("真实频谱采集频率", 13, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        String[] labels = {"低频率（10 Hz / 128 点，推荐老车机）",
                "高频率（30 Hz / 512 点，更流畅）"};
        String[] values = {"low", "high"};
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        spinner.setSelection("high".equals(AppPreferences.realSpectrumCaptureRate(this)) ? 1 : 0,
                false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            String selected = AppPreferences.realSpectrumCaptureRate(MainActivity.this);
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                  View view, int position, long id) {
                if (values[position].equals(selected)) return;
                selected = values[position];
                AppPreferences.get(MainActivity.this).edit()
                        .putString(AppPreferences.KEY_REAL_SPECTRUM_CAPTURE_RATE,
                                values[position]).apply();
                AudioSpectrumSource.release();
                AudioSpectrumSource.sync(MainActivity.this);
                AppPreferences.changed(MainActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
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

    private void showConfigurationShareDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("配置分享")
                .setItems(new String[]{"生成分享码", "输入分享码导入"}, (dialog, which) -> {
                    if (which == 0) promptShareConfiguration();
                    else promptImportConfiguration();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmResetSettings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("恢复默认设置？")
                .setMessage("将清除当前显示布局、颜色、歌词匹配规则、启动方式、频谱、蓝牙识别、本地歌词目录和自定义字体。\n\n意见反馈、官方回复和匿名社区身份不会删除。")
                .setPositiveButton("恢复默认", (dialog, which) -> resetSettingsToDefaults())
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetSettingsToDefaults() {
        String treeUri = AppPreferences.localLyricDirectoryUri(this);
        if (!treeUri.isEmpty()) {
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(treeUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) { }
        }
        CustomFontStore.clear(this);
        int removed = AppPreferences.resetUserSettings(this);
        AudioSpectrumSource.release();
        MusicStateStore.reloadLyrics(this);
        AppPreferences.changed(this);
        SafeToast.show(this, "已恢复默认设置（重置 " + removed + " 项）",
                Toast.LENGTH_SHORT);
        recreate();
    }

    private void promptShareConfiguration() {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("配置简介（可选，最多 200 字）");
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        new MaterialAlertDialogBuilder(this)
                .setTitle("生成配置分享码")
                .setMessage("只上传可分享的设置，不含歌曲、歌词、本地目录、字体、设备标识与诊断数据。")
                .setView(input)
                .setPositiveButton("上传", (dialog, which) -> {
                    String description = input.getText() == null ? "" : input.getText().toString();
                    ConfigurationShareClient.share(this, description, result -> runOnUiThread(() -> {
                        if (!result.success) {
                            SafeToast.show(this, "生成失败：" + result.error, Toast.LENGTH_LONG);
                            return;
                        }
                        String code = result.value.optString("code");
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("分享码已生成")
                                .setMessage(code + "\n\n有效期至：" + result.value.optString("expiresAt"))
                                .setPositiveButton("复制", (ignored, copyWhich) -> copyText("配置分享码", code))
                                .setNegativeButton("完成", null)
                                .show();
                    }));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptImportConfiguration() {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("8 位分享码");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        new MaterialAlertDialogBuilder(this)
                .setTitle("导入配置")
                .setView(input)
                .setPositiveButton("查看", (dialog, which) -> {
                    String code = input.getText() == null ? "" : input.getText().toString();
                    ConfigurationShareClient.fetch(code, result -> runOnUiThread(() -> {
                        if (!result.success) {
                            SafeToast.show(this, "读取失败：" + result.error, Toast.LENGTH_LONG);
                            return;
                        }
                        String description = result.value.optString("description", "未填写简介");
                        if (description.trim().isEmpty()) description = "未填写简介";
                        String finalDescription = description;
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("确认导入 " + result.value.optString("code"))
                                .setMessage("配置简介：\n" + finalDescription
                                        + "\n\n导入会覆盖分享码中包含的设置，本机私密数据不受影响。")
                                .setPositiveButton("导入", (ignored, importWhich) -> {
                                    try {
                                        int count = ConfigurationCodec.importConfiguration(this,
                                                result.value.getJSONObject("config"));
                                        AppPreferences.changed(this);
                                        AudioSpectrumSource.sync(this);
                                        LyricsDisplayService.startOrRefresh(this);
                                        SafeToast.show(this, "已导入 " + count + " 项设置",
                                                Toast.LENGTH_SHORT);
                                        recreate();
                                    } catch (Throwable error) {
                                        SafeToast.show(this, "导入失败：" + error.getMessage(),
                                                Toast.LENGTH_LONG);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        SafeToast.show(this, "已复制", Toast.LENGTH_SHORT);
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
                                    SafeToast.show(this, "反馈已收到，谢谢！", Toast.LENGTH_LONG);
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
        body = body.replaceFirst("(?s)^#\\s*[^\\n]*更新日志[^\\n]*\\n+", "");
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
            SafeToast.show(this, "无法打开链接：" + address, Toast.LENGTH_LONG);
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
        SafeToast.show(this, "\u65e0\u6cd5\u6253\u5f00\u7cfb\u7edf\u7684\u901a\u77e5\u8bfb\u53d6\u8bbe\u7f6e\uff0c\u8bf7\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u624b\u52a8\u5f00\u542f\u3002",
                Toast.LENGTH_LONG);
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
                SafeToast.show(this, "无法打开系统应用设置，请在系统设置中手动开启悬浮窗权限。",
                        Toast.LENGTH_LONG);
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
        SafeToast.show(this, "无法打开系统悬浮窗权限设置，请在系统设置中手动开启。", Toast.LENGTH_LONG);
    }

    private static String permissionState(boolean granted) {
        return granted ? "已授权" : "未授权";
    }

    private void showPermissionHomeHint(String permission) {
        SafeToast.show(this, "请在首页“使用权限”中授予" + permission + "权限",
                Toast.LENGTH_LONG);
    }

    private void requestRecordAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            SafeToast.show(this, "当前系统无需单独授予录音权限", Toast.LENGTH_SHORT);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            SafeToast.show(this, "录音频谱权限已授权", Toast.LENGTH_SHORT);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT < 31) {
            SafeToast.show(this, "当前系统无需单独授予蓝牙读取权限", Toast.LENGTH_SHORT);
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            SafeToast.show(this, "蓝牙读取权限已授权", Toast.LENGTH_SHORT);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_BLUETOOTH_CONNECT);
    }

    private void requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            SafeToast.show(this, "当前系统无需单独授予通知显示权限", Toast.LENGTH_SHORT);
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            SafeToast.show(this, "通知显示权限已授权", Toast.LENGTH_SHORT);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
    }

    private void openUsageAccessSettings() {
        if (Build.VERSION.SDK_INT < 21) {
            SafeToast.show(this, "当前系统无需使用情况访问权限", Toast.LENGTH_SHORT);
            return;
        }
        Intent direct = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (startSettingsActivity(direct)) return;
        if (startSettingsActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) return;
        openApplicationDetails();
    }

    private void openUnknownAppSources() {
        if (Build.VERSION.SDK_INT < 26) {
            openApplicationDetails();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        if (!startSettingsActivity(intent)) openApplicationDetails();
    }

    private void openDefaultAppsSettings() {
        if (startSettingsActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))) {
            return;
        }
        if (!startSettingsActivity(new Intent(Settings.ACTION_SETTINGS))) {
            SafeToast.show(this, "无法打开默认应用设置，请在系统设置中手动进入。",
                    Toast.LENGTH_LONG);
        }
    }

    private void openApplicationDetails() {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (!startSettingsActivity(details)) {
            startSettingsActivity(new Intent(Settings.ACTION_SETTINGS));
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

    /** API 19 has no {@code java.util.function}, and this project does not desugar it. */
    private interface BooleanConsumer { void accept(boolean value); }

}
