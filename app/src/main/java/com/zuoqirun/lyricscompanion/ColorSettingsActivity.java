package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.util.ArrayList;
import java.util.List;

/** The single entry point for every user-selectable overlay color. */
@SuppressLint("SetTextI18n")
public final class ColorSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SCOPE = "scope";
    private LinearLayout root;
    private LinearLayout colorHost;
    private int selectedScope;
    /** Which screen the colors currently on show belong to; 0 for the top strip's own palette. */
    private int displaySlot;
    /** 页内实时预览与它的容器（issue #24）。 */
    private LinearLayout previewHost;
    private LyricsPanelView colorPreview;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selectedScope = state.getInt(EXTRA_SCOPE, 0);
        else selectedScope = getIntent().getIntExtra(EXTRA_SCOPE, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("自定义颜色");
        toolbar.setSubtitle("主屏、副屏与顶部歌词条的颜色统一在这里设置");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        // 页内实时预览（issue #24）。
        previewHost = new LinearLayout(this);
        previewHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = dp(8);
        root.addView(previewHost, previewParams);

        LinearLayout rules = card("颜色规则");
        MaterialSwitch followLyrics = toggle("主屏与副屏歌词跟随深浅环境",
                AppPreferences.lyricsFollowTheme(this));
        followLyrics.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setLyricsFollowTheme(this, checked);
            changed();
            rebuildColors();
        });
        rules.addView(followLyrics);
        MaterialSwitch followStatus = toggle("顶部歌词条跟随深浅环境",
                AppPreferences.statusLyricFollowTheme(this));
        followStatus.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, checked).apply();
            changed();
            rebuildColors();
        });
        rules.addView(followStatus);
        addCard(rules);

        LinearLayout scope = card("编辑区域");
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        // Scopes 0/1/2 keep their historical meaning; every further entry is one extra screen,
        // whose own settings live in its own store (slot = scope - 1).
        List<String> labelList = new ArrayList<>();
        labelList.add("主屏悬浮歌词");
        labelList.add("副屏歌词");
        labelList.add("顶部歌词条");
        List<DisplaySlotRegistry.Entry> extraEntries = DisplaySlotRegistry.entries(this);
        for (int index = 0; index < extraEntries.size(); index++) {
            labelList.add(DisplaySlotRegistry.slotLabel(this,
                    DisplaySlotRegistry.slotFor(index)) + "颜色");
        }
        String[] labels = labelList.toArray(new String[0]);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        spinner.setSelection(Math.max(0, Math.min(labels.length - 1, selectedScope)), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (selectedScope == position && colorHost != null) return;
                selectedScope = position;
                rebuildColors();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        scope.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView note = text("选择“自动”会继续使用当前歌词样式的默认配色。", 12,
                0xFF8392A8, false);
        note.setPadding(0, dp(6), 0, 0);
        scope.addView(note);
        addCard(scope);

        rebuildColors();
        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(EXTRA_SCOPE, selectedScope);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void rebuildColors() {
        if (root == null) return;
        if (colorHost != null) root.removeView(colorHost);
        displaySlot = displaySlotForScope(selectedScope);
        rebuildPreview();
        colorHost = card(selectedScope == 0 ? "主屏颜色"
                : selectedScope == 1 ? "副屏颜色"
                : selectedScope == 2 ? "顶部歌词条颜色"
                : DisplaySlotRegistry.slotLabel(this, displaySlot) + "颜色");
        if (selectedScope == 2) addStatusColors(colorHost);
        else addDisplayColors(colorHost, selectedScope != 0);
        addCard(colorHost);
        CustomFontStore.applyToViewTree(this, colorHost);
    }

    /** The top strip has a palette of its own, so only real screens have a slot. */
    private static int displaySlotForScope(int scope) {
        if (scope <= DisplaySlotRegistry.SECONDARY_SLOT) return scope;
        return scope == 2 ? DisplaySlotRegistry.MAIN_SLOT : scope - 1;
    }

    private void addDisplayColors(LinearLayout parent, boolean secondary) {
        addLyricColorMode(parent, secondary, true);
        addLyricColorMode(parent, secondary, false);
        parent.addView(text("跟随封面复用缓存取色；已唱用当前色、未唱用非当前色，并自动加对比描边。无封面沿用固定配色。",
                12, 0xFF8392A8, false));
        addColor(parent, "歌词颜色", "自动时由当前歌词样式决定。",
                AppPreferences.lyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setLyricColor(this, secondary, color));
        addColor(parent, "当前歌词颜色", "留在自动时沿用“歌词颜色”。",
                AppPreferences.currentLyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCurrentLyricColor(this, secondary, color));
        addColor(parent, "非当前歌词颜色", "留在自动时沿用“歌词颜色”。",
                AppPreferences.inactiveLyricColor(this, secondary), 0xFFB1BCCB,
                color -> AppPreferences.setInactiveLyricColor(this, secondary, color));
        addOutlineControls(parent, secondary, true, "当前歌词");
        addOutlineControls(parent, secondary, false, "非当前歌词");
        if (AppPreferences.lyricsFollowTheme(this)) {
            addColor(parent, "浅色环境歌词颜色", "用于白天或浅色环境。",
                    AppPreferences.lyricLightColor(this, secondary), 0xFF17212E,
                    color -> AppPreferences.setLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境歌词颜色", "用于夜晚或深色环境。",
                    AppPreferences.lyricDarkColor(this, secondary), 0xFFF5F8FF,
                    color -> AppPreferences.setLyricDarkColor(this, secondary, color));
            addColor(parent, "浅色环境当前歌词颜色", "留空时沿用上方浅色环境歌词颜色。",
                    AppPreferences.currentLyricLightColor(this, secondary), 0xFFFFCA66,
                    color -> AppPreferences.setCurrentLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境当前歌词颜色", "留空时沿用上方深色环境歌词颜色。",
                    AppPreferences.currentLyricDarkColor(this, secondary), 0xFFFFCA66,
                    color -> AppPreferences.setCurrentLyricDarkColor(this, secondary, color));
            addColor(parent, "浅色环境非当前歌词颜色", "留空时沿用上方浅色环境歌词颜色。",
                    AppPreferences.inactiveLyricLightColor(this, secondary), 0xFFB1BCCB,
                    color -> AppPreferences.setInactiveLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境非当前歌词颜色", "留空时沿用上方深色环境歌词颜色。",
                    AppPreferences.inactiveLyricDarkColor(this, secondary), 0xFFB1BCCB,
                    color -> AppPreferences.setInactiveLyricDarkColor(this, secondary, color));
        }
        addMetadataColor(parent, secondary, "歌名颜色", AppPreferences.KEY_TITLE_COLOR,
                AppPreferences.titleColor(this, secondary), 0xFFFFFFFF);
        addMetadataColor(parent, secondary, "歌手颜色", AppPreferences.KEY_ARTIST_COLOR,
                AppPreferences.artistColor(this, secondary), 0xFFB8C5D8);
        addMetadataColor(parent, secondary, "播放器名称颜色", AppPreferences.KEY_PLAYER_COLOR,
                AppPreferences.playerColor(this, secondary), 0xFF6EE7F2);
        addMetadataColor(parent, secondary, "歌词来源颜色", AppPreferences.KEY_LYRIC_SOURCE_COLOR,
                AppPreferences.lyricSourceColor(this, secondary), 0xFFFFCA66);
        addColor(parent, "白天歌词背景颜色", "自动时使用当前样式背景。",
                AppPreferences.backgroundLightColor(this, secondary), 0xFFF3F7FC,
                color -> AppPreferences.setBackgroundColor(this, secondary, true, color));
        addColor(parent, "黑夜歌词背景颜色", "自动时使用当前样式背景。",
                AppPreferences.backgroundDarkColor(this, secondary), 0xFF101A29,
                color -> AppPreferences.setBackgroundColor(this, secondary, false, color));
        addColor(parent, "频谱与律动颜色", "自动时跟随歌词颜色。",
                AppPreferences.compactSpectrumColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCompactSpectrumColor(this, secondary, color));
    }

    private void addLyricColorMode(LinearLayout parent, boolean secondary, boolean current) {
        TextView label = text((current ? "当前" : "非当前") + "歌词配色来源", 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this,
                new String[]{"自动 / 自定义（使用下方颜色）", "跟随歌曲封面"}));
        int initial = AppPreferences.lyricFollowsArtwork(this, secondary, current) ? 1 : 0;
        spinner.setSelection(initial, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int selected = initial;
            @Override public void onItemSelected(AdapterView<?> owner, View view, int position, long id) {
                if (selected == position) return;
                selected = position;
                AppPreferences.putDisplayString(ColorSettingsActivity.this, secondary, current
                        ? AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE : AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE,
                        position == 1 ? "artwork" : "fixed");
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> owner) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void addStatusColors(LinearLayout parent) {
        if (!AppPreferences.statusLyricFollowTheme(this)) {
            addColor(parent, "歌词颜色", "自动时使用高对比白色。",
                    AppPreferences.statusLyricColor(this), 0xFFF5F8FF,
                    color -> AppPreferences.setStatusLyricColor(this, color));
            return;
        }
        addColor(parent, "浅色环境歌词颜色", "自动时使用深色歌词。",
                AppPreferences.statusLyricLightColor(this), 0xFF17212E,
                color -> AppPreferences.setStatusLyricLightColor(this, color));
        addColor(parent, "深色环境歌词颜色", "自动时使用浅色歌词。",
                AppPreferences.statusLyricDarkColor(this), 0xFFF5F8FF,
                color -> AppPreferences.setStatusLyricDarkColor(this, color));
    }

    private void addOutlineControls(LinearLayout parent, boolean secondary, boolean current,
                                    String label) {
        MaterialSwitch outline = toggle(label + "描边",
                AppPreferences.lyricOutline(this, secondary, current));
        outline.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, current
                    ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE
                    : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, checked);
            changed();
            rebuildColors();
        });
        parent.addView(outline);
        if (!AppPreferences.lyricOutline(this, secondary, current)) return;
        addColor(parent, label + "描边颜色", "自动时会按该歌词颜色生成高反差描边。",
                AppPreferences.lyricOutlineColor(this, secondary, current), 0xFF000000,
                color -> AppPreferences.setLyricOutlineColor(this, secondary, current, color));
        addPercentSeek(parent, label + "描边不透明度",
                AppPreferences.lyricOutlineAlphaPercent(this, secondary, current),
                value -> AppPreferences.setLyricOutlineAlphaPercent(this, secondary, current, value));
        addPercentSeek(parent, label + "描边宽度（字号百分比）",
                AppPreferences.lyricOutlineWidthPercent(this, secondary, current),
                value -> AppPreferences.setLyricOutlineWidthPercent(this, secondary, current, value));
    }

    private void addMetadataColor(LinearLayout parent, boolean secondary, String title,
                                  String key, int initial, int fallback) {
        addColor(parent, title, "自动时沿用当前样式配色。", initial, fallback,
                color -> AppPreferences.setMetadataColor(this, secondary, key, color));
    }

    private void addColor(LinearLayout parent, String title, String description, int initial,
                          int fallback, ColorConsumer consumer) {
        ColorPaletteControls.add(this, parent, title, description, initial, fallback,
                color -> consumer.accept(color), this::changed);
    }

    private void addPercentSeek(LinearLayout parent, String title, int initial,
                                PercentConsumer consumer) {
        TextView label = text(title + "：" + initial + "%", 13, 0xFFF1F5FA, false);
        label.setPadding(0, dp(12), 0, 0);
        parent.addView(label);
        android.widget.SeekBar seek = new android.widget.SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Math.max(0, Math.min(100, initial)));
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int value,
                                                    boolean fromUser) {
                if (!fromUser) return;
                label.setText(title + "：" + value + "%");
                consumer.accept(value);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(30)));
    }

    private void changed() {
        AppPreferences.changed(this);
        LyricsDisplayService.refreshSecondary(this);
        // 页内实时预览（issue #24）：改颜色、描边、背景都立刻在预览里看到，不必回总览页。
        if (colorPreview != null) colorPreview.reloadStyle();
    }

    /**
     * 页内实时预览（issue #24）：按当前编辑的区域（主屏 / 副屏 / 顶部歌词条 / 某块附加屏）建一个
     * 小尺寸 LyricsPanelView，改颜色时 {@link #changed()} 会让它重新读一遍样式。
     */
    private void rebuildPreview() {
        if (previewHost == null) return;
        previewHost.removeAllViews();
        colorPreview = selectedScope == 2
                ? new LyricsPanelView(this, false, false, true)
                : new LyricsPanelView(this, displaySlot > DisplaySlotRegistry.MAIN_SLOT);
        previewHost.addView(colorPreview, new LinearLayout.LayoutParams(-1, dp(140)));
    }

    private MaterialSwitch toggle(String title, boolean checked) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setChecked(checked);
        return toggle;
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

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(card, params);
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

    private interface ColorConsumer { void accept(int color); }

    private interface PercentConsumer { void accept(int percent); }
}
