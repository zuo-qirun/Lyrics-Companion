package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** A focused parameter page for exactly one overlay display. */
@SuppressLint("SetTextI18n")
public final class DisplaySettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";

    private boolean secondary;
    private LyricsPanelView preview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(secondary ? "副屏显示参数" : "主屏显示参数");
        toolbar.setSubtitle("仅影响当前屏幕，另一块屏幕不会改变");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(190));
        previewParams.topMargin = dp(8);
        root.addView(preview, previewParams);

        LinearLayout panel = card("悬浮窗尺寸与文字");
        Point screenDp = targetScreenSizeDp();
        int maximumWidth = Math.max(AppPreferences.minimumPanelWidthDp(this, secondary), screenDp.x);
        int maximumHeight = Math.max(AppPreferences.minimumPanelHeightDp(this, secondary), screenDp.y);
        addSeek(panel, "悬浮窗宽度", AppPreferences.minimumPanelWidthDp(this, secondary),
                maximumWidth,
                AppPreferences.panelWidthDp(this, secondary), " dp",
                value -> AppPreferences.setPanelWidthDp(this, secondary, value));
        addSeek(panel, "悬浮窗高度", AppPreferences.minimumPanelHeightDp(this, secondary),
                maximumHeight,
                AppPreferences.panelHeightDp(this, secondary), " dp",
                value -> AppPreferences.setPanelHeightDp(this, secondary, value));
        addSeek(panel, "字号", 75, 220,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_TEXT_SCALE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TEXT_SCALE, value));
        addSeek(panel, "歌名与歌手字号", 60, 180,
                AppPreferences.titleScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TITLE_SCALE, value));
        addSeek(panel, "下一句字号", 45, 160,
                AppPreferences.nextLyricScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_SCALE, value));
        addSeek(panel, "下一句不透明度", 20, 100,
                AppPreferences.nextLyricOpacity(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_OPACITY, value));
        addLyricColorControls(panel);
        addToggle(panel, "平滑滚动换句", AppPreferences.KEY_SMOOTH_LYRIC_SCROLL,
                AppPreferences.smoothLyricScroll(this, secondary));
        addSeek(panel, "歌词显示行数", 1, 3,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_LYRIC_LINES, 3), " 行",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_LYRIC_LINES, value));
        addSeek(panel, "歌词时间校正", -5000, 5000,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_LYRIC_OFFSET, 0), " ms",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_LYRIC_OFFSET, value));
        addCard(root, panel);

        LinearLayout spectrum = card("律动与频谱");
        addToggle(spectrum, "在当前歌词模式显示律动", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        addToggle(spectrum, "使用真实音频频谱（关闭为虚拟律动）",
                AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM,
                AppPreferences.compactUseRealSpectrum(this, secondary));
        addChoice(spectrum, "频谱样式",
                new String[]{"经典柱状", "中心镜像", "胶囊律动", "点阵跳动", "连续波形"},
                new String[]{"bars", "mirror", "capsule", "dots", "wave"},
                AppPreferences.spectrumStyle(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_SPECTRUM_STYLE, value));
        addChoice(spectrum, "频谱颜色",
                new String[]{"跟随歌词", "自定义单色", "HSL 彩虹", "跟随封面"},
                new String[]{"lyric", "custom", "rainbow", "artwork"},
                AppPreferences.spectrumColorMode(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_SPECTRUM_COLOR_MODE, value));
        ColorPaletteControls.add(this, spectrum, "自定义频谱颜色",
                "关闭手动调色时会跟随歌词颜色。",
                AppPreferences.compactSpectrumColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCompactSpectrumColor(this, secondary, color), this::changed);
        addCard(root, spectrum);

        if (!secondary) {
            LinearLayout controls = card("播放控制与全屏按钮");
            addSeek(controls, "播放按钮大小", 60, 160,
                    AppPreferences.playbackControlScale(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_SCALE, value).apply());
            addSeek(controls, "播放按钮水平位置", -40, 40,
                    AppPreferences.playbackControlX(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_X, value).apply());
            addSeek(controls, "播放按钮垂直位置", -40, 40,
                    AppPreferences.playbackControlY(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_Y, value).apply());
            addChoice(controls, "全屏右上角关闭按钮",
                    new String[]{"自动弱化", "始终显示", "隐藏"},
                    new String[]{"fade", "always", "hidden"},
                    AppPreferences.fullscreenCloseMode(this),
                    value -> AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_FULLSCREEN_CLOSE_MODE, value).apply());
            addChoice(controls, "桌面歌词右上角叉",
                    new String[]{"弱化显示", "始终显示", "隐藏"},
                    new String[]{"fade", "always", "hidden"},
                    AppPreferences.overlayCloseMode(this),
                    value -> AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_OVERLAY_CLOSE_MODE, value).apply());
            addCard(root, controls);
        }

        LinearLayout sourceCorrection = card("按播放器校正");
        addSourceCorrection(sourceCorrection);
        addCard(root, sourceCorrection);

        LinearLayout artwork = card("背景与封面");
        addSeek(artwork, "背景不透明度", 0, 100,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_OPACITY, 88), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_OPACITY, value));
        addSeek(artwork, "封面大小", 60, 150,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_COVER_SIZE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_COVER_SIZE, value));
        addSeek(artwork, "封面背景柔化", 0, 128,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, 128), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, value));
        addSeek(artwork, "封面背景遮罩", 0, 80,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, 38), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, value));
        addCard(root, artwork);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private Point targetScreenSizeDp() {
        Display display = targetDisplay();
        Point pixels = new Point();
        if (display != null) display.getRealSize(pixels);
        android.content.Context displayContext = display == null ? this : createDisplayContext(display);
        float density = Math.max(0.1f,
                displayContext.getResources().getDisplayMetrics().density);
        return new Point(Math.max(1, Math.round(pixels.x / density)),
                Math.max(1, Math.round(pixels.y / density)));
    }

    private Display targetDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (!secondary || manager == null) return getWindowManager().getDefaultDisplay();
        int preferredId = AppPreferences.displayId(this);
        if (preferredId >= 0) {
            Display preferred = manager.getDisplay(preferredId);
            if (preferred != null && preferred.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return preferred;
            }
        }
        for (Display display : manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        for (Display display : manager.getDisplays()) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        return getWindowManager().getDefaultDisplay();
    }

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(0xFF101E31));
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

    private void addSeek(LinearLayout parent, String title, int min, int max,
                         int initial, String suffix, IntConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(text(title, 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(formatValue(initial, suffix), 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(clamp(initial, min, max) - min);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(formatValue(selected, suffix));
                if (!fromUser) return;
                consumer.accept(selected);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            if (checked && (AppPreferences.KEY_SPECTRUM_ENABLED.equals(key)
                    || AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM.equals(key))) {
                requestSpectrumPermissionIfNeeded();
            }
            changed();
        });
        parent.addView(toggle);
    }

    private void requestSpectrumPermissionIfNeeded() {
        if (!AppPreferences.compactUseRealSpectrum(this, secondary)
                || android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2418);
    }

    private void addLyricColorControls(LinearLayout parent) {
        ColorPaletteControls.add(this, parent, "歌词颜色", "自动模式会跟随所选歌词样式的配色。",
                AppPreferences.lyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setLyricColor(this, secondary, color), this::changed);
        if (!AppPreferences.lyricsFollowTheme(this)) return;
        ColorPaletteControls.add(this, parent, "浅色环境歌词颜色",
                "当前为浅色环境时使用。",
                AppPreferences.lyricLightColor(this, secondary), 0xFF17212E,
                color -> AppPreferences.setLyricLightColor(this, secondary, color), this::changed);
        ColorPaletteControls.add(this, parent, "深色环境歌词颜色",
                "当前为深色环境时使用。",
                AppPreferences.lyricDarkColor(this, secondary), 0xFFF5F8FF,
                color -> AppPreferences.setLyricDarkColor(this, secondary, color), this::changed);
    }

    private void addChoice(LinearLayout parent, String title, String[] labels, String[] values,
                           String initial, StringConsumer consumer) {
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(initial)) { spinner.setSelection(i, false); break; }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            String selected = initial;
            @Override public void onItemSelected(AdapterView<?> parentView, View view,
                                                  int position, long id) {
                if (values[position].equals(selected)) return;
                selected = values[position];
                consumer.accept(values[position]);
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addSourceCorrection(LinearLayout parent) {
        TextView description = text("在全局时间校正的基础上，为不同播放器单独微调。", 12,
                0xFF8392A8, false);
        description.setPadding(0, dp(10), 0, dp(4));
        parent.addView(description);
        String[] labels = {"网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "其他播放器"};
        String[] sourceIds = {"netease", "qqmusic", "kugou", "kuwo", "soda", "media"};
        String active = MusicStateStore.activeSourceId();
        int initialIndex = sourceIndex(sourceIds, active);
        final String[] selectedSource = {sourceIds[initialIndex]};

        Spinner picker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        picker.setAdapter(adapter);
        picker.setSelection(initialIndex);
        parent.addView(picker, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(text("所选播放器额外校正", 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text("0 ms", 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(10_000);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int offsetMs = progress - 5_000;
                value.setText(formatValue(offsetMs, " ms"));
                if (!fromUser) return;
                AppPreferences.putLyricSourceOffsetMs(DisplaySettingsActivity.this, secondary,
                        selectedSource[0], offsetMs);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, android.view.View view,
                                                 int position, long id) {
                selectedSource[0] = sourceIds[position];
                int offsetMs = AppPreferences.lyricSourceOffsetMs(DisplaySettingsActivity.this,
                        secondary, selectedSource[0]);
                seek.setProgress(offsetMs + 5_000);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        seek.setProgress(AppPreferences.lyricSourceOffsetMs(this, secondary, selectedSource[0])
                + 5_000);
    }

    private static int sourceIndex(String[] sourceIds, String sourceId) {
        for (int i = 0; i < sourceIds.length; i++) {
            if (sourceIds[i].equals(sourceId)) return i;
        }
        return sourceIds.length - 1;
    }

    private void changed() {
        preview.reloadStyle();
        AppPreferences.changed(this);
        AudioSpectrumSource.sync(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatValue(int value, String suffix) {
        return (value > 0 && " ms".equals(suffix) ? "+" : "") + value + suffix;
    }

    private interface IntConsumer { void accept(int value); }
    private interface StringConsumer { void accept(String value); }
}
