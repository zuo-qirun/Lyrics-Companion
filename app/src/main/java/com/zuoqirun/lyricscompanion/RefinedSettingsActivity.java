package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Native controls mirroring the visual settings exposed by Refined Now Playing. */
@SuppressLint("SetTextI18n")
public final class RefinedSettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";
    private LyricsPanelView preview;
    private boolean secondary;

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
        toolbar.setTitle("Refined 风格详细设置");
        toolbar.setSubtitle("按 BetterNCM 插件源码映射到原生 Canvas");
        toolbar.setTitle((secondary ? "\u526f\u5c4f" : "\u4e3b\u5c4f")
                + " Refined \u98ce\u683c\u8be6\u7ec6\u8bbe\u7f6e");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);

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
                new String[]{"流体", "模糊", "渐变", "纯色", "无"},
                new String[]{"fluid", "blur", "gradient", "solid", "none"},
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
                16, 64, AppPreferences.refinedLyricFontSize(this, secondary), " sp");
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

        TextView note = text("逐字动画保持完整 Unicode 字符跳变；没有逐字时间轴的普通 LRC 仍按整句切换。BetterNCM 的鼠标悬停、评论区和播放器底栏选项不属于悬浮窗渲染范围。",
                12, 0xFF74869D, false);
        note.setLineSpacing(0f, 1.2f);
        note.setPadding(dp(4), dp(14), dp(4), 0);
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
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(190));
            previewParams.topMargin = dp(14);
            leftColumn.addView(preview, previewParams);
            addCard(leftColumn, appearance);
            addCard(leftColumn, cover);
            addCard(rightColumn, background);
            addCard(rightColumn, lyric);
            root.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        } else {
            root.addView(preview, new LinearLayout.LayoutParams(-1, dp(190)));
            addCard(root, appearance);
            addCard(root, cover);
            addCard(root, background);
            addCard(root, lyric);
        }
        root.addView(note);
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

    private boolean useTwoColumnLayout() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && widthDp >= 600f;
    }

    private void addChoice(LinearLayout parent, String title, String key,
                           String[] labels, String[] values, String current) {
        TextView label = text(title, 13, 0xFFD7E1EE, true);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parentView) {
                return styleSpinner((TextView) super.getView(position, convertView, parentView));
            }
            @Override public View getDropDownView(int position, View convertView,
                                                  ViewGroup parentView) {
                return styleSpinner((TextView) super.getDropDownView(position, convertView, parentView));
            }
        };
        spinner.setAdapter(adapter);
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
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
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
        parent.addView(toggle);
    }

    private void addSeek(LinearLayout parent, String title, String key,
                         int min, int max, int initial, String suffix) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        row.addView(text(title, 13, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(initial + suffix, 12, 0xFF6EE7F2, true);
        row.addView(value);
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
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private void changed() {
        if (preview != null) preview.reloadStyle();
        AppPreferences.changed(this);
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    private TextView styleSpinner(TextView view) {
        view.setTextColor(0xFFF2F6FB);
        view.setTextSize(14f);
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        view.setBackgroundColor(0xFF132238);
        return view;
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
