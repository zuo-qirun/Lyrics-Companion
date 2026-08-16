package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
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

/** The single entry point for every user-selectable overlay color. */
@SuppressLint("SetTextI18n")
public final class ColorSettingsActivity extends AppCompatActivity {
    static final String EXTRA_SCOPE = "scope";
    private LinearLayout root;
    private LinearLayout colorHost;
    private int selectedScope;

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
        String[] labels = {"主屏悬浮歌词", "副屏歌词", "顶部歌词条"};
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
        colorHost = card(selectedScope == 0 ? "主屏颜色"
                : selectedScope == 1 ? "副屏颜色" : "顶部歌词条颜色");
        if (selectedScope == 2) addStatusColors(colorHost);
        else addDisplayColors(colorHost, selectedScope == 1);
        addCard(colorHost);
        CustomFontStore.applyToViewTree(this, colorHost);
    }

    private void addDisplayColors(LinearLayout parent, boolean secondary) {
        addColor(parent, "歌词颜色", "自动时由当前歌词样式决定。",
                AppPreferences.lyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setLyricColor(this, secondary, color));
        addColor(parent, "当前歌词颜色", "留在自动时沿用“歌词颜色”。",
                AppPreferences.currentLyricColor(this, secondary), 0xFFFFCA66,
                color -> AppPreferences.setCurrentLyricColor(this, secondary, color));
        addColor(parent, "非当前歌词颜色", "留在自动时沿用“歌词颜色”。",
                AppPreferences.inactiveLyricColor(this, secondary), 0xFFB1BCCB,
                color -> AppPreferences.setInactiveLyricColor(this, secondary, color));
        MaterialSwitch outline = toggle("歌词文字描边", AppPreferences.lyricOutline(this, secondary));
        outline.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, AppPreferences.KEY_LYRIC_OUTLINE, checked);
            changed();
        });
        parent.addView(outline);
        if (AppPreferences.lyricsFollowTheme(this)) {
            addColor(parent, "浅色环境歌词颜色", "用于白天或浅色环境。",
                    AppPreferences.lyricLightColor(this, secondary), 0xFF17212E,
                    color -> AppPreferences.setLyricLightColor(this, secondary, color));
            addColor(parent, "深色环境歌词颜色", "用于夜晚或深色环境。",
                    AppPreferences.lyricDarkColor(this, secondary), 0xFFF5F8FF,
                    color -> AppPreferences.setLyricDarkColor(this, secondary, color));
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

    private void changed() {
        AppPreferences.changed(this);
        LyricsDisplayService.refreshSecondary(this);
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
}
