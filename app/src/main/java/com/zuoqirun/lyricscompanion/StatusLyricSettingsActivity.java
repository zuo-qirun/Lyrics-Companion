package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Settings for the transparent lyric strip embedded around the system status area. */
@SuppressLint("SetTextI18n")
public final class StatusLyricSettingsActivity extends AppCompatActivity {
    private LyricsPanelView previewPanel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("顶部歌词条详细设置");
        toolbar.setSubtitle("透明叠加在状态栏区域，系统图标仍由系统显示");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        LinearLayout preview = card("预览");
        previewPanel = new LyricsPanelView(this, false, false, true);
        preview.addView(previewPanel, new LinearLayout.LayoutParams(-1, dp(86)));
        addCard(root, preview);

        LinearLayout layout = card("布局与字号");
        addSeek(layout, "歌词字号", 60, 200, AppPreferences.topLyricFontScale(this), "%",
                AppPreferences.KEY_TOP_LYRIC_FONT_SCALE);
        addSeek(layout, "显示区域宽度", 45, 100, AppPreferences.topLyricRegionPercent(this), "%",
                AppPreferences.KEY_TOP_LYRIC_REGION_PERCENT);
        addSeek(layout, "水平偏移", -240, 240, AppPreferences.topLyricOffsetXDp(this), " dp",
                AppPreferences.KEY_TOP_LYRIC_OFFSET_X_DP);
        addSeek(layout, "垂直偏移", -240, 240, AppPreferences.topLyricOffsetYDp(this), " dp",
                AppPreferences.KEY_TOP_LYRIC_OFFSET_Y_DP);
        addToggle(layout, "显示歌词翻译（替代下一句）",
                AppPreferences.KEY_TOP_LYRIC_SHOW_TRANSLATION,
                AppPreferences.topLyricShowTranslation(this));
        addToggle(layout, "显示律动条", AppPreferences.KEY_TOP_LYRIC_SPECTRUM,
                AppPreferences.topLyricSpectrum(this));
        TextView layoutNote = text("显示区域默认占满屏幕宽度且居中；偏移会在此基础上移动。顶部条强制使用紧凑歌词的双行、逐字高亮和跟随滚动。", 12,
                0xFF8392A8, false);
        layoutNote.setLineSpacing(0f, 1.2f);
        layoutNote.setPadding(0, dp(8), 0, 0);
        layout.addView(layoutNote);
        addCard(root, layout);

        LinearLayout background = card("背景样式");
        addBackgroundChoice(background);
        TextView backgroundNote = text("毛玻璃使用系统窗口背景模糊（Android 12 及以上），旧系统自动降级为半透明材质；紧凑单行背景保留原来的封面柔化卡片。", 12,
                0xFF8392A8, false);
        backgroundNote.setPadding(0, dp(8), 0, 0);
        background.addView(backgroundNote);
        addCard(root, background);

        LinearLayout color = card("歌词颜色");
        TextView description = text("顶部歌词条可独立决定是否跟随深浅环境；开启后可分别设置浅色与深色环境的歌词颜色。", 12,
                0xFF8392A8, false);
        description.setLineSpacing(0f, 1.2f);
        description.setPadding(0, dp(9), 0, dp(3));
        color.addView(description);
        addColorControls(color);
        addCard(root, color);
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

    private void addColorControls(LinearLayout parent) {
        com.google.android.material.materialswitch.MaterialSwitch followTheme =
                new com.google.android.material.materialswitch.MaterialSwitch(this);
        followTheme.setText("歌词跟随深浅色");
        followTheme.setTextColor(0xFFF1F5FA);
        followTheme.setPadding(0, dp(10), 0, 0);
        followTheme.setChecked(AppPreferences.statusLyricFollowTheme(this));
        parent.addView(followTheme);

        LinearLayout fixedColors = new LinearLayout(this);
        fixedColors.setOrientation(LinearLayout.VERTICAL);
        ColorPaletteControls.add(this, fixedColors, "歌词颜色",
                "自动模式使用高对比白色，适合大多数桌面。",
                AppPreferences.statusLyricColor(this), 0xFFF5F8FF,
                color -> AppPreferences.setStatusLyricColor(this, color), () -> {
                    updatePreview();
                    AppPreferences.changed(this);
                });
        parent.addView(fixedColors);

        LinearLayout themedColors = new LinearLayout(this);
        themedColors.setOrientation(LinearLayout.VERTICAL);
        ColorPaletteControls.add(this, themedColors, "浅色环境歌词颜色",
                "自动使用深色歌词。", AppPreferences.statusLyricLightColor(this),
                0xFF17212E, color -> AppPreferences.setStatusLyricLightColor(this, color),
                () -> { updatePreview(); AppPreferences.changed(this); });
        ColorPaletteControls.add(this, themedColors, "深色环境歌词颜色",
                "自动使用浅色歌词。", AppPreferences.statusLyricDarkColor(this),
                0xFFF5F8FF, color -> AppPreferences.setStatusLyricDarkColor(this, color),
                () -> { updatePreview(); AppPreferences.changed(this); });
        parent.addView(themedColors);
        updateColorControlVisibility(fixedColors, themedColors, followTheme.isChecked());
        followTheme.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_STATUS_LYRIC_FOLLOW_THEME, checked).apply();
            updateColorControlVisibility(fixedColors, themedColors, checked);
            updatePreview();
            AppPreferences.changed(this);
        });
    }

    private static void updateColorControlVisibility(View fixed, View themed, boolean follows) {
        fixed.setVisibility(follows ? View.GONE : View.VISIBLE);
        themed.setVisibility(follows ? View.VISIBLE : View.GONE);
    }

    private void addSeek(LinearLayout parent, String title, int min, int max, int initial,
                         String suffix, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(text(title, 14, 0xFFD7E1EE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(format(initial, suffix), 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(min, Math.min(max, initial)) - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int selected = min + progress;
                value.setText(format(selected, suffix));
                if (!user) return;
                AppPreferences.setTopLyricInt(StatusLyricSettingsActivity.this, key, selected);
                updatePreview();
                AppPreferences.changed(StatusLyricSettingsActivity.this);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        com.google.android.material.materialswitch.MaterialSwitch toggle =
                new com.google.android.material.materialswitch.MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            updatePreview();
            AudioSpectrumSource.sync(this);
            AppPreferences.changed(this);
        });
        parent.addView(toggle);
    }

    private void addBackgroundChoice(LinearLayout parent) {
        String[] labels = {"完全透明", "毛玻璃", "紧凑单行背景"};
        String[] values = {"transparent", "blur", "compact"};
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        String initial = AppPreferences.topLyricBackground(this);
        for (int i = 0; i < values.length; i++) if (values[i].equals(initial)) {
            spinner.setSelection(i, false); break;
        }
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            String selected = initial;
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView,
                                                  android.view.View view, int position, long id) {
                if (values[position].equals(selected)) return;
                selected = values[position];
                AppPreferences.get(StatusLyricSettingsActivity.this).edit()
                        .putString(AppPreferences.KEY_TOP_LYRIC_BACKGROUND, values[position]).apply();
                updatePreview();
                AppPreferences.changed(StatusLyricSettingsActivity.this);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void updatePreview() {
        if (previewPanel != null) previewPanel.reloadStyle();
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

    private static String format(int value, String suffix) {
        return (value > 0 && " dp".equals(suffix) ? "+" : "") + value + suffix;
    }

}
