package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Native controls mirroring the visual settings exposed by Refined Now Playing. */
@SuppressLint("SetTextI18n")
public final class RefinedSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";
    private LyricsPanelView preview;
    private SettingsNavigator navigation;
    private boolean secondary;
    private int displaySlot;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        displaySlot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = displaySlot > DisplaySlotRegistry.MAIN_SLOT;
        String style = AppPreferences.overlayStyle(this, secondary);
        navigation = new SettingsNavigator(this, "Refined 专属设置",
                DisplaySlotRegistry.slotLabel(this, displaySlot) + " · " + DisplaySettingsActivity.styleName(style),
                style, new SettingsValues(this, secondary));
        LinearLayout root = navigation.content;
        preview = new LyricsPanelView(this, secondary);
        LinearLayout previewCard = card("效果预览");
        com.google.android.material.button.MaterialButton toggle = new com.google.android.material.button.MaterialButton(this);
        toggle.setText("查看 / 收起效果预览");
        toggle.setOnClickListener(v -> preview.setVisibility(preview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        preview.setVisibility(View.GONE);
        previewCard.addView(toggle);
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

        TextView note = text("逐字动画按完整 Unicode 字符跳变；无逐字时间轴的 LRC 仍整句切换。",
                12, 0xFF74869D, false);
        note.setLineSpacing(0f, 1.2f);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        navigation.addSection(appearance, SettingsCatalog.Category.COMMON, "外观");
        navigation.addSection(cover, SettingsCatalog.Category.BACKGROUND, "封面");
        navigation.addSection(background, SettingsCatalog.Category.BACKGROUND, "背景");
        lyric.addView(note);
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

    private void addChoice(LinearLayout parent, String title, String key,
                           String[] labels, String[] values, String current) {
        parent = navigation.row(parent, key, title);
        TextView label = text(title, 13, 0xFFD7E1EE, true);
        label.setPadding(0, 0, 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        spinner.setSelection(selected, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView, View view,
                                                 int position, long id) {
                String existing = AppPreferences.displayString(RefinedSettingsActivity.this,
                        secondary, key, current);
                if (values[position].equals(existing)) return;
                AppPreferences.putDisplayString(RefinedSettingsActivity.this, secondary,
                        key, values[position]);
                changed();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        navigation.scopeNote(parent, key);
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        parent = navigation.row(parent, key, title);
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(8), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        toggle.setMinHeight(dp(48));
        parent.addView(toggle);
        navigation.scopeNote(parent, key);
    }

    private void addSeek(LinearLayout parent, String title, String key,
                         int min, int max, int initial, String suffix) {
        parent = navigation.row(parent, key, title);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        row.addView(text(title, 13, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(initial + suffix, 12, 0xFF6EE7F2, true);
        row.addView(value);
        navigation.value(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(min, Math.min(max, initial)) - min);
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
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        navigation.scopeNote(parent, key);
    }

    private void changed() {
        if (preview != null) preview.reloadStyle();
        if (navigation != null) navigation.refresh();
        AppPreferences.changed(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
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
