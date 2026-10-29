package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** The single entry point for every user-selectable overlay color. */
@SuppressLint("SetTextI18n")
public final class ColorSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SCOPE = "scope";
    /** LayoutInflater.from 需要已附着的 Activity，只能在 onCreate 里构造。 */
    private SettingsUi ui;
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
        ui = new SettingsUi(this);
        if (state != null) selectedScope = state.getInt(EXTRA_SCOPE, 0);
        else selectedScope = getIntent().getIntExtra(EXTRA_SCOPE, 0);

        displaySlot = displaySlotForScope(selectedScope);
        String style = selectedScope == 2 ? "status" : AppPreferences.overlayStyle(this, displaySlot > 0);
        String scopeLabel = selectedScope == 2 ? "顶部歌词条" : DisplaySlotRegistry.slotLabel(this, displaySlot)
                + " · " + DisplaySettingsActivity.styleName(style);
        navigation = new SettingsNavigator(this, "颜色与描边", scopeLabel, style,
                new SettingsValues(this, displaySlot > 0));

        // 预设配色方案（issue #131）：放在页面最顶部，一键套用本屏 × 当前样式。
        if (selectedScope != 2) addPresetCard();

        // 页内实时预览（issue #24）。
        previewHost = new LinearLayout(this);
        previewHost.setOrientation(LinearLayout.VERTICAL);
        previewHost.setVisibility(View.GONE);
        LinearLayout previewCard = card("效果预览");
        ui.actionButton(previewCard, "查看 / 收起效果预览", v -> previewHost.setVisibility(
                previewHost.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        previewCard.addView(previewHost);
        navigation.addSection(previewCard, SettingsCatalog.Category.COMMON, "效果预览");

        LinearLayout rules = card("颜色规则");
        addToggle(rules, "悬浮歌词跟随深浅环境", "歌词跟随深浅环境",
                AppPreferences.KEY_LYRICS_FOLLOW_THEME, AppPreferences.lyricsFollowTheme(this),
                (button, checked) -> {
                    AppPreferences.setLyricsFollowTheme(this, checked);
                    changed();
                    rebuildColors();
                });
        addToggle(rules, "顶部歌词条跟随深浅环境", "顶部歌词条跟随深浅环境",
                AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, AppPreferences.statusLyricFollowTheme(this),
                (button, checked) -> {
                    AppPreferences.get(this).edit()
                            .putBoolean(AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, checked).apply();
                    changed();
                    rebuildColors();
                });
        navigation.addSection(rules, SettingsCatalog.Category.COMMON, "颜色规则");

        LinearLayout scope = card("编辑区域");
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
        // 卡片标题已经写了「编辑区域」，这一行不再重复一个同名的可见标题。
        LinearLayout choiceRow = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.item_setting_choice, scope, false);
        choiceRow.findViewById(R.id.settings_row_title).setVisibility(View.GONE);
        Spinner spinner = choiceRow.findViewById(R.id.settings_row_spinner);
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
        scope.addView(choiceRow);
        ui.note(scope, "选择“自动”会继续使用当前歌词样式的默认配色。");
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
        ui.note(parent, "跟随封面复用缓存取色；已唱用当前色、未唱用非当前色，并自动加对比描边。无封面沿用固定配色。");
        addColor(parent, "歌词颜色", AppPreferences.KEY_LYRIC_COLOR, "自动时由当前歌词样式决定。",
                AppPreferences.lyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setLyricColor(this, secondary, color));
        addColor(parent, "当前歌词颜色", AppPreferences.KEY_CURRENT_LYRIC_COLOR, "留在自动时沿用“歌词颜色”。",
                AppPreferences.currentLyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCurrentLyricColor(this, secondary, color));
        addColor(parent, "非当前歌词颜色", AppPreferences.KEY_INACTIVE_LYRIC_COLOR, "留在自动时沿用“歌词颜色”。",
                AppPreferences.inactiveLyricColor(this, secondary), 0xFFB1BCCB,
                color -> AppPreferences.setInactiveLyricColor(this, secondary, color));
        // 当前句不透明度（issue #148）：与上方颜色自带的不透明度相乘，100 为完全不透明。
        addPercentSeek(parent, "当前句不透明度", AppPreferences.KEY_CURRENT_LYRIC_OPACITY,
                AppPreferences.currentLyricOpacity(this, secondary),
                value -> AppPreferences.setCurrentLyricOpacity(this, secondary, value));
        ui.note(parent, "与颜色自带的不透明度相乘；上一句 / 下一句的不透明度在“显示设置”里分开调整。");
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
        String key = current ? AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE
                : AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE;
        String title = (current ? "当前" : "非当前") + "歌词配色来源";
        LinearLayout row = row(parent, title, key);
        final String[] modes = {"fixed", "artwork", "random", "artwork_random"};
        String mode = AppPreferences.lyricColorMode(this, secondary, current);
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(mode)) selectedIndex = i;
        final int initial = selectedIndex;
        ui.addChoice(row, title,
                new String[]{"自动 / 自定义（使用下方颜色）", "跟随歌曲封面", "每首歌随机", "封面优先，无封面时随机"},
                initial, new AdapterView.OnItemSelectedListener() {
                    int selected = initial;
                    @Override public void onItemSelected(AdapterView<?> owner, View view,
                                                         int position, long id) {
                        if (selected == position) return;
                        selected = position;
                        AppPreferences.putDisplayString(ColorSettingsActivity.this, secondary, current
                                ? AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE
                                : AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE,
                                modes[position]);
                        changed();
                    }
                    @Override public void onNothingSelected(AdapterView<?> owner) { }
                });
        ui.note(row, "随机颜色按歌曲保持，同一屏的当前 / 非当前句共用色相，按面板基础背景调整明暗。复杂壁纸建议保留描边。");
        navigation.scopeNote(row, key);
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
        String key = current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE
                : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE;
        addToggle(parent, label + "描边", label + "描边", key,
                AppPreferences.lyricOutline(this, secondary, current), (button, checked) -> {
                    AppPreferences.putDisplayBoolean(this, secondary, current
                            ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE
                            : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, checked);
                    changed();
                    rebuildColors();
                });
        addColor(parent, label + "描边颜色",
                current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_COLOR
                        : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_COLOR,
                "自动时会按该歌词颜色生成高反差描边。",
                AppPreferences.lyricOutlineColor(this, secondary, current), 0xFF000000,
                color -> AppPreferences.setLyricOutlineColor(this, secondary, current, color));
        addPercentSeek(parent, label + "描边不透明度",
                current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_ALPHA
                        : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_ALPHA,
                AppPreferences.lyricOutlineAlphaPercent(this, secondary, current),
                value -> AppPreferences.setLyricOutlineAlphaPercent(this, secondary, current, value));
        addPercentSeek(parent, label + "描边宽度（字号百分比）",
                current ? AppPreferences.KEY_CURRENT_LYRIC_OUTLINE_WIDTH
                        : AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE_WIDTH,
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
        LinearLayout row = row(parent, title, key);
        // 顶部歌词条的 setStatusLyric* 仍是旧版不透明写入（issue #148 的 alpha 只覆盖悬浮歌词），
        // 所以在该区域先隐藏透明度输入，避免用户调了 alpha 却被静默丢弃。
        ColorPaletteControls.add(this, row, title, description, initial, fallback,
                selectedScope != 2, color -> consumer.accept(color), this::changed);
        navigation.scopeNote(row, key);
    }

    private void addPercentSeek(LinearLayout parent, String title, String key, int initial,
                                PercentConsumer consumer) {
        LinearLayout row = row(parent, title, key);
        SeekBar seek = ui.addSeek(row, title, 100, Math.max(0, Math.min(100, initial)), "%", value -> {
            consumer.accept(value);
            changed();
        });
        navigation.value(((View) seek.getParent()).findViewById(R.id.settings_row_value));
        navigation.scopeNote(row, key);
    }

    /** 行容器由 SettingsUi 的行模板渲染，外层只为分类 / 搜索登记与作用域提示保留。 */
    private LinearLayout row(LinearLayout parent, String title, String key) {
        LinearLayout row = navigation.row(parent, key, title);
        row.setPadding(0, 0, 0, 0);
        return row;
    }

    private void addToggle(LinearLayout parent, String title, String rowLabel, String key,
                           boolean initial, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = row(parent, rowLabel, key);
        ui.addToggle(row, title, null, initial, listener);
        navigation.scopeNote(row, key);
    }

    /** 与 SettingsUi.card 同一张卡片模板，但先不挂到页面上，方便登记分类后再统一加入。 */
    private LinearLayout card(String title) {
        com.google.android.material.card.MaterialCardView card =
                (com.google.android.material.card.MaterialCardView) getLayoutInflater()
                        .inflate(R.layout.view_settings_card, null, false);
        LinearLayout content = card.findViewById(R.id.settings_card_content);
        ui.sectionLabel(content, title);
        return content;
    }

    private void changed() {
        if (navigation != null) navigation.refresh();
        AppPreferences.changed(this);
        LyricsDisplayService.refreshSecondary(this);
        // 页内实时预览（issue #24）：改颜色、描边、背景都立刻在预览里看到，不必回总览页。
        if (colorPreview != null) colorPreview.reloadStyle();
    }

    /**
     * 预设配色方案（issue #131）：色球一键把本屏 × 当前样式写入一组歌词颜色，并记住方案名。
     */
    private void addPresetCard() {
        boolean secondary = selectedScope != 0;
        String stored = AppPreferences.colorPreset(this, secondary);
        ColorPaletteControls.Scheme applied = ColorPaletteControls.schemeFor(stored);
        LinearLayout section = card("预设配色方案");
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, 0);
        ColorPaletteControls.Scheme[] schemes = ColorPaletteControls.schemes();
        LinearLayout row = null;
        for (int index = 0; index < schemes.length; index++) {
            if (index % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                if (index > 0) row.setPadding(0, dp(10), 0, 0);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            boolean selected = applied != null && applied.id.equals(schemes[index].id);
            row.addView(schemeCell(schemes[index], selected, secondary),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        section.addView(grid);
        String statusText;
        if (applied != null) statusText = "当前：" + applied.name;
        else if (stored.isEmpty()) statusText = "当前：跟随样式默认配色";
        else statusText = "当前：自定义配色（未套用预设）";
        TextView status = (TextView) getLayoutInflater().inflate(R.layout.item_setting_note, section, false);
        status.setText(statusText);
        status.setTextColor(ContextCompat.getColor(this, R.color.lc_primary));
        section.addView(status);
        ui.actionButton(section, "恢复跟随样式", v -> resetPresetColors(secondary));
        ui.note(section, "点按色球会把当前句 / 非当前句 / 描边 / 背景 / 律动颜色一起写成该方案的初值，"
                + "之后仍可逐项微调；只影响本屏 · 当前样式。");
        navigation.addSection(section, SettingsCatalog.Category.COMMON, "预设配色方案");
    }

    /** 一个预设色球：当前句 → 非当前句的渐变，选中时描亮边并高亮名字。 */
    private View schemeCell(ColorPaletteControls.Scheme scheme, boolean selected,
                            boolean secondary) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(4), dp(2), dp(2));
        View ball = new View(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{scheme.current, scheme.inactive});
        gradient.setShape(GradientDrawable.OVAL);
        gradient.setStroke(dp(selected ? 3 : 1), ContextCompat.getColor(this,
                selected ? R.color.lc_primary : R.color.lc_outline_variant));
        ball.setBackground(gradient);
        cell.addView(ball, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView name = (TextView) getLayoutInflater().inflate(R.layout.item_setting_note, cell, false);
        name.setText(scheme.name);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.lc_primary : R.color.lc_on_surface));
        if (selected) name.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        cell.addView(name);
        cell.setContentDescription(scheme.name);
        cell.setOnClickListener(v -> applyPreset(scheme, secondary));
        return cell;
    }

    /** 套用预设：写当前屏 × 当前样式的一组颜色，并记下方案名用于色球选中态。 */
    private void applyPreset(ColorPaletteControls.Scheme scheme, boolean secondary) {
        AppPreferences.putDisplayString(this, secondary,
                AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE, "fixed");
        AppPreferences.putDisplayString(this, secondary,
                AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE, "fixed");
        // 基础歌词颜色也写成当前句颜色：只认“歌词颜色”的样式套用预设才会有变化。
        AppPreferences.setLyricColor(this, secondary, scheme.current);
        AppPreferences.setCurrentLyricColor(this, secondary, scheme.current);
        AppPreferences.setInactiveLyricColor(this, secondary, scheme.inactive);
        // 日夜配色里的成对颜色优先级更高，套用预设前清空，避免旧的自定义颜色把预设盖掉。
        clearThemedLyricColors(secondary);
        AppPreferences.putDisplayBoolean(this, secondary,
                AppPreferences.KEY_CURRENT_LYRIC_OUTLINE, true);
        AppPreferences.putDisplayBoolean(this, secondary,
                AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, true);
        AppPreferences.setLyricOutlineColor(this, secondary, true, scheme.outline);
        AppPreferences.setLyricOutlineColor(this, secondary, false, scheme.outline);
        AppPreferences.setBackgroundColor(this, secondary, true, scheme.backgroundLight);
        AppPreferences.setBackgroundColor(this, secondary, false, scheme.backgroundDark);
        AppPreferences.setCompactSpectrumColor(this, secondary, scheme.spectrum);
        AppPreferences.setColorPreset(this, secondary, scheme.id);
        changed();
        rebuildColors();
    }

    /** 清空预设，恢复"跟随当前样式默认配色"（issue #131）。 */
    private void resetPresetColors(boolean secondary) {
        AppPreferences.setLyricColor(this, secondary, 0);
        AppPreferences.setCurrentLyricColor(this, secondary, 0);
        AppPreferences.setInactiveLyricColor(this, secondary, 0);
        AppPreferences.putDisplayString(this, secondary,
                AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE, "fixed");
        AppPreferences.putDisplayString(this, secondary,
                AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE, "fixed");
        clearThemedLyricColors(secondary);
        AppPreferences.putDisplayBoolean(this, secondary,
                AppPreferences.KEY_CURRENT_LYRIC_OUTLINE, false);
        AppPreferences.putDisplayBoolean(this, secondary,
                AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, false);
        AppPreferences.setLyricOutlineColor(this, secondary, true, 0);
        AppPreferences.setLyricOutlineColor(this, secondary, false, 0);
        AppPreferences.setBackgroundColor(this, secondary, true, 0);
        AppPreferences.setBackgroundColor(this, secondary, false, 0);
        AppPreferences.setCompactSpectrumColor(this, secondary, 0);
        AppPreferences.setColorPreset(this, secondary, "");
        changed();
        rebuildColors();
    }

    /** 清空日夜配色自己的当前 / 非当前句颜色，让套用的基础色重新生效。 */
    private void clearThemedLyricColors(boolean secondary) {
        AppPreferences.setCurrentLyricLightColor(this, secondary, 0);
        AppPreferences.setCurrentLyricDarkColor(this, secondary, 0);
        AppPreferences.setInactiveLyricLightColor(this, secondary, 0);
        AppPreferences.setInactiveLyricDarkColor(this, secondary, 0);
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ColorConsumer { void accept(int color); }

    private interface PercentConsumer { void accept(int percent); }
}
