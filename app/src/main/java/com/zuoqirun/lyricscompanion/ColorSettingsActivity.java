package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.util.ArrayList;
import java.util.List;

/** The single entry point for every user-selectable overlay color. */
@SuppressLint("SetTextI18n")
public final class ColorSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SCOPE = "scope";
    private LinearLayout root;
    private SettingsNavigator navigation;
    private boolean ready;
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

        displaySlot = displaySlotForScope(selectedScope);
        String style = selectedScope == 2 ? "status" : AppPreferences.overlayStyle(this, displaySlot > 0);
        String scopeLabel = selectedScope == 2 ? "顶部歌词条" : DisplaySlotRegistry.slotLabel(this, displaySlot)
                + " · " + DisplaySettingsActivity.styleName(style);
        navigation = new SettingsNavigator(this, "颜色与描边", scopeLabel, style,
                new SettingsValues(this, displaySlot > 0));
        root = navigation.content;

        // 页内实时预览（issue #24）。
        previewHost = new LinearLayout(this);
        previewHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = dp(8);
        LinearLayout previewCard = card("效果预览");
        com.google.android.material.button.MaterialButton previewToggle = new com.google.android.material.button.MaterialButton(this);
        previewToggle.setText("查看 / 收起效果预览");
        previewToggle.setOnClickListener(v -> previewHost.setVisibility(
                previewHost.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        previewHost.setVisibility(View.GONE);
        previewCard.addView(previewToggle);
        previewCard.addView(previewHost, previewParams);
        navigation.addSection(previewCard, SettingsCatalog.Category.COMMON, "效果预览");

        LinearLayout rules = card("颜色规则");
        MaterialSwitch followLyrics = toggle("悬浮歌词跟随深浅环境",
                AppPreferences.lyricsFollowTheme(this));
        followLyrics.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.setLyricsFollowTheme(this, checked);
            changed();
            rebuildColors();
        });
        LinearLayout followLyricsRow = navigation.row(rules, AppPreferences.KEY_LYRICS_FOLLOW_THEME, "歌词跟随深浅环境");
        followLyricsRow.addView(followLyrics);
        navigation.scopeNote(followLyricsRow, AppPreferences.KEY_LYRICS_FOLLOW_THEME);
        MaterialSwitch followStatus = toggle("顶部歌词条跟随深浅环境",
                AppPreferences.statusLyricFollowTheme(this));
        followStatus.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, checked).apply();
            changed();
            rebuildColors();
        });
        LinearLayout followStatusRow = navigation.row(rules, AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, "顶部歌词条跟随深浅环境");
        followStatusRow.addView(followStatus);
        navigation.scopeNote(followStatusRow, AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME);
        navigation.addSection(rules, SettingsCatalog.Category.COMMON, "颜色规则");

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
                if (selectedScope == position) return;
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
        navigation.addSection(scope, SettingsCatalog.Category.COMMON, "编辑区域");

        buildColorSections();
        navigation.finish(state);
        ready = true;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(EXTRA_SCOPE, selectedScope);
        navigation.save(state);
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

    @Override public void onBackPressed() {
        if (!navigation.back()) super.onBackPressed();
    }

    private void rebuildColors() {
        if (ready) recreate();
    }

    private void buildColorSections() {
        rebuildPreview();
        colorHost = colorSection("歌词配色", SettingsCatalog.Category.COLOR_LYRICS);
        if (selectedScope == 2) addStatusColors(colorHost);
        else addDisplayColors(colorHost, selectedScope != 0);
    }

    private LinearLayout colorSection(String title, SettingsCatalog.Category category) {
        LinearLayout section = card(title);
        navigation.addSection(section, category, title);
        return section;
    }

    private static int displaySlotForScope(int scope) {
        if (scope <= DisplaySlotRegistry.SECONDARY_SLOT) return scope;
        return scope == 2 ? DisplaySlotRegistry.MAIN_SLOT : scope - 1;
    }

    private void addDisplayColors(LinearLayout parent, boolean secondary) {
        addLyricColorMode(parent, secondary, true);
        addLyricColorMode(parent, secondary, false);
        parent.addView(text("跟随封面复用缓存取色；已唱用当前色、未唱用非当前色，并自动加对比描边。无封面沿用固定配色。",
                12, 0xFF8392A8, false));
        addColor(parent, "歌词颜色", AppPreferences.KEY_LYRIC_COLOR, "自动时由当前歌词样式决定。",
                AppPreferences.lyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setLyricColor(this, secondary, color));
        addColor(parent, "当前歌词颜色", AppPreferences.KEY_CURRENT_LYRIC_COLOR, "留在自动时沿用“歌词颜色”。",
                AppPreferences.currentLyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCurrentLyricColor(this, secondary, color));
        addColor(parent, "非当前歌词颜色", AppPreferences.KEY_INACTIVE_LYRIC_COLOR, "留在自动时沿用“歌词颜色”。",
                AppPreferences.inactiveLyricColor(this, secondary), 0xFFB1BCCB,
                color -> AppPreferences.setInactiveLyricColor(this, secondary, color));
        LinearLayout outlines = colorSection("文字描边", SettingsCatalog.Category.COLOR_OUTLINE);
        addOutlineControls(outlines, secondary, true, "当前歌词");
        addOutlineControls(outlines, secondary, false, "非当前歌词");
        if (AppPreferences.lyricsFollowTheme(this)) {
            parent = colorSection("日夜配色", SettingsCatalog.Category.COLOR_THEME);
            addColor(parent, "浅色环境歌词颜色", AppPreferences.KEY_LYRIC_LIGHT_COLOR, "用于白天或浅色环境。",
                    AppPreferences.lyricLightColor(this, secondary), 0xFF17212E,
                    color -> AppPreferences.setLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境歌词颜色", AppPreferences.KEY_LYRIC_DARK_COLOR, "用于夜晚或深色环境。",
                    AppPreferences.lyricDarkColor(this, secondary), 0xFFF5F8FF,
                    color -> AppPreferences.setLyricDarkColor(this, secondary, color));
            addColor(parent, "浅色环境当前歌词颜色", AppPreferences.KEY_CURRENT_LYRIC_LIGHT_COLOR, "留空时沿用上方浅色环境歌词颜色。",
                    AppPreferences.currentLyricLightColor(this, secondary), 0xFFFFCA66,
                    color -> AppPreferences.setCurrentLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境当前歌词颜色", AppPreferences.KEY_CURRENT_LYRIC_DARK_COLOR, "留空时沿用上方深色环境歌词颜色。",
                    AppPreferences.currentLyricDarkColor(this, secondary), 0xFFFFCA66,
                    color -> AppPreferences.setCurrentLyricDarkColor(this, secondary, color));
            addColor(parent, "浅色环境非当前歌词颜色", AppPreferences.KEY_INACTIVE_LYRIC_LIGHT_COLOR, "留空时沿用上方浅色环境歌词颜色。",
                    AppPreferences.inactiveLyricLightColor(this, secondary), 0xFFB1BCCB,
                    color -> AppPreferences.setInactiveLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境非当前歌词颜色", AppPreferences.KEY_INACTIVE_LYRIC_DARK_COLOR, "留空时沿用上方深色环境歌词颜色。",
                    AppPreferences.inactiveLyricDarkColor(this, secondary), 0xFFB1BCCB,
                    color -> AppPreferences.setInactiveLyricDarkColor(this, secondary, color));
        }
        parent = colorSection("歌名与状态配色", SettingsCatalog.Category.COLOR_METADATA);
        addMetadataColor(parent, secondary, "歌名颜色", AppPreferences.KEY_TITLE_COLOR,
                AppPreferences.titleColor(this, secondary), 0xFFFFFFFF);
        addMetadataColor(parent, secondary, "歌手颜色", AppPreferences.KEY_ARTIST_COLOR,
                AppPreferences.artistColor(this, secondary), 0xFFB8C5D8);
        addMetadataColor(parent, secondary, "播放器名称颜色", AppPreferences.KEY_PLAYER_COLOR,
                AppPreferences.playerColor(this, secondary), 0xFF6EE7F2);
        addMetadataColor(parent, secondary, "歌词来源颜色", AppPreferences.KEY_LYRIC_SOURCE_COLOR,
                AppPreferences.lyricSourceColor(this, secondary), 0xFFFFCA66);
        parent = colorSection("背景与频谱配色", SettingsCatalog.Category.COLOR_BACKGROUND);
        addColor(parent, "白天歌词背景颜色", AppPreferences.KEY_BACKGROUND_LIGHT_COLOR, "自动时使用当前样式背景。",
                AppPreferences.backgroundLightColor(this, secondary), 0xFFF3F7FC,
                color -> AppPreferences.setBackgroundColor(this, secondary, true, color));
        addColor(parent, "黑夜歌词背景颜色", AppPreferences.KEY_BACKGROUND_DARK_COLOR, "自动时使用当前样式背景。",
                AppPreferences.backgroundDarkColor(this, secondary), 0xFF101A29,
                color -> AppPreferences.setBackgroundColor(this, secondary, false, color));
        addColor(parent, "频谱与律动颜色", AppPreferences.KEY_COMPACT_SPECTRUM_COLOR, "自动时跟随歌词颜色。",
                AppPreferences.compactSpectrumColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCompactSpectrumColor(this, secondary, color));
    }

    private void addLyricColorMode(LinearLayout parent, boolean secondary, boolean current) {
        String key = current ? AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE : AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE;
        parent = navigation.row(parent, key, (current ? "当前" : "非当前") + "歌词配色来源");
        navigation.scopeNote(parent, key);
        TextView label = text((current ? "当前" : "非当前") + "歌词配色来源", 14, 0xFFD7E1EE, true);
        label.setPadding(0, 0, 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this,
                new String[]{"自动 / 自定义（使用下方颜色）", "跟随歌曲封面", "每首歌随机", "封面优先，无封面时随机"}));
        String[] modes = {"fixed", "artwork", "random", "artwork_random"};
        String mode = AppPreferences.lyricColorMode(this, secondary, current);
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(mode)) selectedIndex = i;
        final int initial = selectedIndex;
        spinner.setSelection(initial, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int selected = initial;
            @Override public void onItemSelected(AdapterView<?> owner, View view, int position, long id) {
                if (selected == position) return;
                selected = position;
                AppPreferences.putDisplayString(ColorSettingsActivity.this, secondary, current
                        ? AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE : AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE,
                        modes[position]);
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> owner) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        parent.addView(text("随机颜色按歌曲保持，同一屏的当前 / 非当前句共用色相，按面板基础背景调整明暗。复杂壁纸建议保留描边。",
                12, 0xFF8392A8, false));
    }

    private void addStatusColors(LinearLayout parent) {
        if (!AppPreferences.statusLyricFollowTheme(this)) {
            addColor(parent, "歌词颜色", AppPreferences.KEY_STATUS_LYRIC_COLOR, "自动时使用高对比白色。",
                    AppPreferences.statusLyricColor(this), 0xFFF5F8FF,
                    color -> AppPreferences.setStatusLyricColor(this, color));
            return;
        }
        addColor(parent, "浅色环境歌词颜色", AppPreferences.KEY_STATUS_LYRIC_LIGHT_COLOR, "自动时使用深色歌词。",
                AppPreferences.statusLyricLightColor(this), 0xFF17212E,
                color -> AppPreferences.setStatusLyricLightColor(this, color));
        addColor(parent, "深色环境歌词颜色", AppPreferences.KEY_STATUS_LYRIC_DARK_COLOR, "自动时使用浅色歌词。",
                AppPreferences.statusLyricDarkColor(this), 0xFFF5F8FF,
                color -> AppPreferences.setStatusLyricDarkColor(this, color));
    }

    private void addOutlineControls(LinearLayout parent, boolean secondary, boolean current,
                                    String label) {
        String key = current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE;
        LinearLayout outlineRow = navigation.row(parent, key, label + "描边");
        MaterialSwitch outline = toggle(label + "描边",
                AppPreferences.lyricOutline(this, secondary, current));
        outline.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, current
                    ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE
                    : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, checked);
            changed();
            rebuildColors();
        });
        outlineRow.addView(outline);
        navigation.scopeNote(outlineRow, key);
        addColor(parent, label + "描边颜色", current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_COLOR : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_COLOR, "自动时会按该歌词颜色生成高反差描边。",
                AppPreferences.lyricOutlineColor(this, secondary, current), 0xFF000000,
                color -> AppPreferences.setLyricOutlineColor(this, secondary, current, color));
        addPercentSeek(parent, label + "描边不透明度", current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_ALPHA : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_ALPHA,
                AppPreferences.lyricOutlineAlphaPercent(this, secondary, current),
                value -> AppPreferences.setLyricOutlineAlphaPercent(this, secondary, current, value));
        addPercentSeek(parent, label + "描边宽度（字号百分比）", current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_WIDTH : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_WIDTH,
                AppPreferences.lyricOutlineWidthPercent(this, secondary, current),
                value -> AppPreferences.setLyricOutlineWidthPercent(this, secondary, current, value));
    }

    private void addMetadataColor(LinearLayout parent, boolean secondary, String title,
                                  String key, int initial, int fallback) {
        addColor(parent, title, key, "自动时沿用当前样式配色。", initial, fallback,
                color -> AppPreferences.setMetadataColor(this, secondary, key, color));
    }

    private void addColor(LinearLayout parent, String title, String key, String description, int initial,
                          int fallback, ColorConsumer consumer) {
        parent = navigation.row(parent, key, title);
        navigation.scopeNote(parent, key);
        ColorPaletteControls.add(this, parent, title, description, initial, fallback,
                color -> consumer.accept(color), this::changed);
    }

    private void addPercentSeek(LinearLayout parent, String title, String key, int initial,
                                PercentConsumer consumer) {
        parent = navigation.row(parent, key, title);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        TextView amount = text(initial + "%", 13, 0xFF6EE7F2, true);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        heading.addView(amount);
        navigation.value(amount);
        label.setPadding(0, dp(12), 0, 0);
        parent.addView(heading);
        android.widget.SeekBar seek = new android.widget.SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Math.max(0, Math.min(100, initial)));
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int value,
                                                    boolean fromUser) {
                if (!fromUser) return;
                amount.setText(value + "%");
                consumer.accept(value);
                changed();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        navigation.scopeNote(parent, key);
    }

    private void changed() {
        if (navigation != null) navigation.refresh();
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
