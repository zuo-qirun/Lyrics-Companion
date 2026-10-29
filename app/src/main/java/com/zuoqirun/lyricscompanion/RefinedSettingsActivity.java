package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Native controls mirroring the visual settings exposed by Refined Now Playing. */
@SuppressLint("SetTextI18n")
public final class RefinedSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";
    /** LayoutInflater.from 需要已附着的 Activity，只能在 onCreate 里构造。 */
    private SettingsUi ui;
    private LyricsPanelView preview;
    private SettingsNavigator navigation;
    private boolean secondary;
    private int displaySlot;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new SettingsUi(this);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        displaySlot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = displaySlot > DisplaySlotRegistry.MAIN_SLOT;
        String style = AppPreferences.overlayStyle(this, secondary);
        navigation = new SettingsNavigator(this, "Refined 专属设置",
                DisplaySlotRegistry.slotLabel(this, displaySlot) + " · " + DisplaySettingsActivity.styleName(style),
                style, new SettingsValues(this, secondary));
        preview = new LyricsPanelView(this, secondary);
        LinearLayout previewCard = card("效果预览");
        ui.actionButton(previewCard, "查看 / 收起效果预览", v -> preview.setVisibility(
                preview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        preview.setVisibility(View.GONE);
        previewCard.addView(preview, new LinearLayout.LayoutParams(-1, dp(150)));
        navigation.addSection(previewCard, SettingsCatalog.Category.COMMON, "效果预览");

        LinearLayout appearance = card("外观");
        addChoice(appearance, "显示内容", AppPreferences.KEY_REFINED_DISPLAY_MODE,
                new String[]{"全部", "仅歌词", "仅封面"},
                new String[]{"all", "lyrics", "cover"}, AppPreferences.refinedDisplayMode(this, secondary));
        addChoice(appearance, "颜色模式", AppPreferences.KEY_REFINED_COLOR_SCHEME,
                new String[]{"跟随系统", "暗色", "亮色"},
                new String[]{"auto", "dark", "light"}, AppPreferences.refinedColorScheme(this, secondary));
        addChoice(appearance, "沉浸主题色", AppPreferences.KEY_REFINED_ACCENT_VARIANT,
                new String[]{"鲜艳", "柔和", "偏色", "关闭"},
                new String[]{"primary", "secondary", "tertiary", "off"},
                AppPreferences.refinedAccentVariant(this, secondary));
        addChoice(appearance, "文字效果", AppPreferences.KEY_REFINED_TEXT_EFFECT,
                new String[]{"无", "文字阴影", "文字辉光"},
                new String[]{"none", "shadow", "glow"}, AppPreferences.refinedTextEffect(this, secondary));
        addToggle(appearance, "进度条贴底", AppPreferences.KEY_REFINED_PROGRESS_BOTTOM,
                AppPreferences.refinedProgressBottom(this, secondary));

        LinearLayout cover = card("封面");
        addChoice(cover, "水平对齐", AppPreferences.KEY_REFINED_COVER_HORIZONTAL,
                new String[]{"居左", "居中"}, new String[]{"left", "center"},
                AppPreferences.refinedCoverHorizontal(this, secondary));
        addChoice(cover, "垂直对齐", AppPreferences.KEY_REFINED_COVER_VERTICAL,
                new String[]{"居下", "居中"}, new String[]{"bottom", "middle"},
                AppPreferences.refinedCoverVertical(this, secondary));
        addToggle(cover, "方形专辑封面", AppPreferences.KEY_REFINED_RECTANGLE_COVER,
                AppPreferences.refinedRectangleCover(this, secondary));
        addToggle(cover, "封面弥散阴影", AppPreferences.KEY_REFINED_COVER_SHADOW,
                AppPreferences.refinedCoverShadow(this, secondary));
        addSeek(cover, "封面大小", AppPreferences.KEY_STYLE_COVER_SIZE,
                60, 150, Math.round(AppPreferences.styleCoverScale(this, secondary) * 100f), "%");

        LinearLayout background = card("背景");
        addChoice(background, "类型", AppPreferences.KEY_REFINED_BACKGROUND_TYPE,
                new String[]{"流体", "模糊", "渐变", "纯色", "无", "星空 / 星尘", "封面渐隐"},
                new String[]{"fluid", "blur", "gradient", "solid", "none", "starfield", "cover_fade"},
                AppPreferences.refinedBackgroundType(this, secondary));
        addToggle(background, "静态流体", AppPreferences.KEY_REFINED_STATIC_FLUID,
                AppPreferences.refinedStaticFluid(this, secondary));
        addToggle(background, "动态渐变", AppPreferences.KEY_REFINED_DYNAMIC_GRADIENT,
                AppPreferences.refinedDynamicGradient(this, secondary));
        addSeek(background, "背景模糊", AppPreferences.KEY_STYLE_BLUR,
                0, 128, AppPreferences.styleBlur(this, secondary), "");
        addSeek(background, "背景暗化", AppPreferences.KEY_STYLE_DIM,
                0, 90, AppPreferences.styleDim(this, secondary), "%");
        addSeek(background, "背景不透明度", AppPreferences.KEY_OPACITY,
                0, 100, AppPreferences.opacity(this, secondary), "%");

        LinearLayout lyric = card("歌词");
        addSeek(lyric, "字体大小", AppPreferences.KEY_REFINED_LYRIC_FONT_SIZE,
                16, 96, AppPreferences.refinedLyricFontSize(this, secondary), " sp");
        addToggle(lyric, "加粗首行", AppPreferences.KEY_REFINED_ORIGINAL_BOLD,
                AppPreferences.refinedOriginalBold(this, secondary));
        addToggle(lyric, "显示翻译", AppPreferences.KEY_REFINED_SHOW_TRANSLATION,
                AppPreferences.refinedShowTranslation(this, secondary));
        addToggle(lyric, "歌词渐隐", AppPreferences.KEY_REFINED_LYRIC_FADE,
                AppPreferences.refinedLyricFade(this, secondary));
        addToggle(lyric, "歌词缩放", AppPreferences.KEY_REFINED_LYRIC_ZOOM,
                AppPreferences.refinedLyricZoom(this, secondary));
        addToggle(lyric, "歌词模糊", AppPreferences.KEY_REFINED_LYRIC_BLUR,
                AppPreferences.refinedLyricBlur(this, secondary));
        addToggle(lyric, "歌词旋转", AppPreferences.KEY_REFINED_LYRIC_ROTATE,
                AppPreferences.refinedLyricRotate(this, secondary));
        addSeek(lyric, "旋转曲率（弧形换句）", AppPreferences.KEY_REFINED_ROTATE_CURVATURE,
                10, 80, AppPreferences.refinedRotateCurvature(this, secondary), "°");
        addChoice(lyric, "逐字动画", AppPreferences.KEY_REFINED_KARAOKE_ANIMATION,
                new String[]{"上浮（整字符）", "阶梯点亮"},
                new String[]{"float", "step"}, AppPreferences.refinedKaraokeAnimation(this, secondary));
        addChoice(lyric, "当前歌词位置", AppPreferences.KEY_REFINED_CURRENT_ALIGN,
                new String[]{"居中", "居上"}, new String[]{"50", "30"},
                Integer.toString(AppPreferences.refinedCurrentAlign(this, secondary)));
        addToggle(lyric, "当前歌词辉光", AppPreferences.KEY_REFINED_LYRIC_GLOW,
                AppPreferences.refinedLyricGlow(this, secondary));
        ui.note(lyric, "逐字动画按完整 Unicode 字符跳变；无逐字时间线的 LRC 仍整句切换。");

        navigation.addSection(appearance, SettingsCatalog.Category.COMMON, "外观");
        navigation.addSection(cover, SettingsCatalog.Category.BACKGROUND, "封面");
        navigation.addSection(background, SettingsCatalog.Category.BACKGROUND, "背景");
        navigation.addSection(lyric, SettingsCatalog.Category.TEXT, "歌词");
        navigation.finish(savedInstanceState);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
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

    /** 行容器直接由 SettingsUi 的行模板渲染，外层只为分类 / 搜索登记与作用域提示保留。 */
    private LinearLayout row(LinearLayout parent, String title, String key) {
        LinearLayout row = navigation.row(parent, key, title);
        row.setPadding(0, 0, 0, 0);
        return row;
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

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        LinearLayout row = row(parent, title, key);
        ui.addToggle(row, title, null, initial, (button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        navigation.scopeNote(row, key);
    }

    private void addChoice(LinearLayout parent, String title, String key,
                           String[] labels, String[] values, String current) {
        LinearLayout row = row(parent, title, key);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        final String fallback = current;
        ui.addChoice(row, title, labels, selected, new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View view,
                                                 int position, long id) {
                String existing = AppPreferences.displayString(RefinedSettingsActivity.this,
                        secondary, key, fallback);
                if (values[position].equals(existing)) return;
                AppPreferences.putDisplayString(RefinedSettingsActivity.this, secondary,
                        key, values[position]);
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        navigation.scopeNote(row, key);
    }

    /**
     * 取值范围不从 0 开始的滑块：SettingsUi 的显示值是相对进度，这里换回绝对值和单位，
     * 与旧版「字体大小 16 sp」的读数保持一致。
     */
    private void addSeek(LinearLayout parent, String title, String key,
                         int min, int max, int initial, String suffix) {
        LinearLayout row = row(parent, title, key);
        int clamped = Math.max(min, Math.min(max, initial));
        SeekBar seek = ui.addSeek(row, title, max - min, clamped - min, null, null);
        final TextView value = ((View) seek.getParent()).findViewById(R.id.settings_row_value);
        value.setText(clamped + suffix);
        navigation.value(value);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(selected + suffix);
                if (!fromUser) return;
                AppPreferences.putDisplayInt(RefinedSettingsActivity.this, secondary,
                        key, selected);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        navigation.scopeNote(row, key);
    }

    private void changed() {
        if (preview != null) preview.reloadStyle();
        if (navigation != null) navigation.refresh();
        AppPreferences.changed(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
