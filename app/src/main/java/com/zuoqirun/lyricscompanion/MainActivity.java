package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CUSTOM_FONT = 2417;
    private static final String UPDATE_MANIFEST_URL =
            "https://lyrics-companion.zuoqirun.top/update.json";
    private static final String UPDATE_HISTORY_URL =
            "https://lyrics-companion.zuoqirun.top/versions";
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long LISTENER_HEALTH_MAX_AGE_MS = 3_000L;
    private static final long LISTENER_INITIAL_RECONNECT_DELAY_MS = 2_500L;
    private static final long LISTENER_RECONNECT_INTERVAL_MS = 1_000L;
    private static final long LISTENER_RECONNECT_WINDOW_MS = 30_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView permissionStatus;
    private TextView musicStatus;
    private TextView displayStatus;
    private TextView updateStatus;
    private TextView onlineStatus;
    private TextView feedbackReplyStatus;
    private MaterialSwitch mainOverlaySwitch;
    private MaterialSwitch secondaryOverlaySwitch;
    private MaterialButton mainRefinedSettingsButton;
    private MaterialButton secondaryRefinedSettingsButton;
    private Spinner displaySpinner;
    private LyricsPanelView previewPanel;
    private TextView globalFontSummary;
    private boolean bindingUi;
    private boolean updateBusy;
    private boolean onlineBusy;
    private boolean feedbackBusy;
    private boolean diagnosticBusy;
    private boolean activityResumed;
    private boolean listenerReconnectScheduled;
    private long listenerReconnectDeadlineElapsedMs;

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
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF07111F);
            getWindow().setNavigationBarColor(0xFF07111F);
        }
        setContentView(buildContent());
        CustomFontStore.applyToViewTree(this, getWindow().getDecorView());
        MusicStateStore.initialize(this);
        requestNotificationPermissionIfNeeded();
        handler.postDelayed(() -> checkForUpdates(false), 2_000L);
    }

    @Override protected void onResume() {
        super.onResume();
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
    }

    @Override protected void onPause() {
        activityResumed = false;
        listenerReconnectScheduled = false;
        handler.removeCallbacks(listenerReconnect);
        handler.removeCallbacks(statusRefresh);
        handler.removeCallbacks(communityRefresh);
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
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

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
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
        outputCard.addView(sectionLabel("显示位置"));
        mainOverlaySwitch = toggle("主屏悬浮窗", "离开设置页后显示；可拖动，轻触可返回设置");
        mainOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_MAIN_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) openOverlayPermission();
            AppPreferences.changed(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(mainOverlaySwitch);
        secondaryOverlaySwitch = toggle("副屏歌词", "直接在选中的非默认 Display 上创建独立悬浮层");
        secondaryOverlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) return;
            AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, checked).apply();
            if (checked && !canDrawOverlays()) openOverlayPermission();
            AppPreferences.changed(this);
            LyricsDisplayService.setSettingsVisible(this, true);
        });
        outputCard.addView(secondaryOverlaySwitch);

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
        updateRefinedSettingsVisibility();
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
        addSupportControls(communityCard);

        LinearLayout stateCard = card();
        stateCard.addView(sectionLabel("音乐状态与诊断"));
        musicStatus = text("等待播放器…", 14, 0xFFD8E1EE, false);
        musicStatus.setLineSpacing(0f, 1.2f);
        musicStatus.setPadding(0, dp(9), 0, 0);
        stateCard.addView(musicStatus);

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
            leftColumn.addView(styleCard, cardMargins());
            rightColumn.addView(accessCard, cardMargins());
            rightColumn.addView(lyricCard, cardMargins());
            rightColumn.addView(communityCard, cardMargins());
            rightColumn.addView(updateCard, cardMargins());
            rightColumn.addView(outputCard, cardMargins());
            rightColumn.addView(stateCard, cardMargins());
            root.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        } else {
            root.addView(previewCard, cardMargins());
            root.addView(accessCard, cardMargins());
            root.addView(lyricCard, cardMargins());
            root.addView(communityCard, cardMargins());
            root.addView(updateCard, cardMargins());
            root.addView(outputCard, cardMargins());
            root.addView(styleCard, cardMargins());
            root.addView(stateCard, cardMargins());
        }

        TextView footnote = text("提示：Android 5.0 以上读取 MediaSession；Android 4.4 播放器需发布 RemoteControlClient。副屏重接后会自动恢复。", 12,
                0xFF66788F, false);
        footnote.setLineSpacing(0f, 1.25f);
        root.addView(footnote);
        return scroll;
    }

    private void addStyleSelector(LinearLayout parent, String title, boolean secondary) {
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(14), 0, dp(6));
        parent.addView(label);
        String[] labels = {"Refined Now Playing", "歌词伴侣经典样式", "紧凑单行", "PiPWindow"};
        String[] values = {"refined", "default", "compact", "pip"};
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
                        : "Refined Now Playing 为原 Refined 双栏样式；经典样式保留原歌词伴侣默认布局。",
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
    }

    private void addLyricCatalogSelector(LinearLayout parent,
                                         MaterialSwitch playerCatalogFallback) {
        TextView label = text("优先匹配词库", 14, 0xFFD7E1EE, true);
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
        TextView help = text("自动模式优先使用识别出的播放器同源词库；手动模式始终先尝试所选词库。当前词库无结果后才依次查询下一词库。",
                12, 0xFF74869D, false);
        help.setPadding(0, dp(5), 0, 0);
        parent.addView(help);
    }

    private static void updatePlayerCatalogFallbackEnabled(MaterialSwitch view,
                                                            boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.55f);
    }

    private void bindPreferences() {
        bindingUi = true;
        mainOverlaySwitch.setChecked(AppPreferences.mainEnabled(this));
        secondaryOverlaySwitch.setChecked(AppPreferences.secondaryEnabled(this));
        bindingUi = false;
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
                "仅上传本应用的异常堆栈、系统版本和悬浮窗状态；默认关闭");
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
    }

    private void uploadDiagnosticSnapshot() {
        if (diagnosticBusy) return;
        diagnosticBusy = true;
        CommunityClient.uploadSnapshotAsync(this, result -> runOnUiThread(() -> {
            diagnosticBusy = false;
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, result.success ? "诊断快照已上传" : "诊断上传失败：" + result.error,
                    Toast.LENGTH_LONG).show();
        }));
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
                feedbackReplyStatus.setText("反馈回复：收到 " + result.replies.size() + " 条，请点击查看");
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
            StringBuilder content = new StringBuilder();
            for (CommunityClient.FeedbackReply reply : result.replies) {
                if (content.length() > 0) content.append("\n\n");
                content.append(reply.createdAt).append("\n").append(reply.message);
            }
            new MaterialAlertDialogBuilder(this).setTitle("反馈回复")
                    .setMessage(content).setPositiveButton("知道了", null).show();
            refreshFeedbackReplies();
        }));
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
        surface.setFillColor(android.content.res.ColorStateList.valueOf(0xFF101E31));
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

    private TextView sectionLabel(String value) { return text(value, 13, 0xFF6EE7F2, true); }

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
        startActivityForResult(intent, REQUEST_CUSTOM_FONT);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private MaterialButton button(String value, boolean primary) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setTextSize(13f);
        button.setTextColor(primary ? 0xFF07111F : 0xFFF1F5FA);
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? 0xFF6EE7F2 : 0xFF25364D));
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
        view.setTextColor(0xFFF2F6FB);
        view.setTextSize(14f);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackgroundColor(0xFF132238);
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
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("发现 Lyrics Companion 更新")
                                .setMessage(info.detailText())
                                .setNegativeButton("稍后", null)
                                .setPositiveButton("下载并安装",
                                        (dialog, which) -> installUpdate(info))
                                .show();
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
            if (updateStatus != null) updateStatus.setText("无法打开链接：" + address);
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
        if (startSettingsActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
            return;
        }
        // Notification access exists on Android 4.4, but its public settings action was only
        // added in API 22. AOSP KitKat exposes this activity; vendor ROMs may not, so keep
        // every fallback resolve-checked.
        Intent kitKatNotificationAccess = new Intent().setComponent(new ComponentName(
                "com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity"));
        if (startSettingsActivity(kitKatNotificationAccess)) return;
        if (startSettingsActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS))) return;
        if (startSettingsActivity(new Intent(Settings.ACTION_SETTINGS))) return;
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
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION",
                Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (Throwable ignored) {
            startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION"));
        }
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
