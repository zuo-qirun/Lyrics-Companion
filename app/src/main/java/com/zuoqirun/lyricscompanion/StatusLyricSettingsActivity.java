package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Settings for the transparent lyric strip embedded around the system status area. */
@SuppressLint("SetTextI18n")
public final class StatusLyricSettingsActivity extends AppCompatActivity {
    private LyricsPanelView previewPanel;
    private SettingsUi ui;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_status_lyric);

        MaterialToolbar toolbar = findViewById(R.id.status_lyric_toolbar);
        toolbar.setTitle("顶部歌词条详细设置");
        toolbar.setSubtitle("透明叠加在状态栏区域，系统图标仍由系统显示");
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout content = findViewById(R.id.status_lyric_content);
        ui = new SettingsUi(this);

        LinearLayout preview = ui.card(content, "预览");
        previewPanel = new LyricsPanelView(this, false, false, true);
        preview.addView(previewPanel, new LinearLayout.LayoutParams(-1, ui.dp(86)));

        LinearLayout layout = ui.card(content, "布局与字号");
        addSeek(layout, "歌词字号", 60, 200, AppPreferences.topLyricFontScale(this), "%",
                AppPreferences.KEY_TOP_LYRIC_FONT_SCALE);
        addSeek(layout, "显示区域宽度", TopLyricLayout.MIN_REGION_PERCENT, 100,
                AppPreferences.topLyricRegionPercent(this), "%",
                AppPreferences.KEY_TOP_LYRIC_REGION_PERCENT);
        addChoice(layout, "垂直位置", new String[]{"沿用原位置", "屏幕贴顶", "系统状态栏同高（单行）"},
                new String[]{"legacy", "top", "status"}, AppPreferences.KEY_TOP_LYRIC_PLACEMENT, "legacy");
        addChoice(layout, "歌词行数", new String[]{"双行", "单行", "自动（有翻译或下一句时双行）"},
                new String[]{"double", "single", "auto"}, AppPreferences.KEY_TOP_LYRIC_ROWS, "double");
        addChoice(layout, "区域水平对齐", new String[]{"居中", "靠左", "靠右"},
                new String[]{"center", "left", "right"}, AppPreferences.KEY_TOP_LYRIC_ALIGN, "center");
        int horizontalRange = AppPreferences.topLyricMaxOffsetDp(this);
        addSeek(layout, "左侧留白", 0, horizontalRange,
                AppPreferences.get(this).getInt(AppPreferences.KEY_TOP_LYRIC_LEFT_MARGIN, 0), " dp",
                AppPreferences.KEY_TOP_LYRIC_LEFT_MARGIN);
        addSeek(layout, "右侧留白", 0, horizontalRange,
                AppPreferences.get(this).getInt(AppPreferences.KEY_TOP_LYRIC_RIGHT_MARGIN, 0), " dp",
                AppPreferences.KEY_TOP_LYRIC_RIGHT_MARGIN);
        addSeek(layout, "水平偏移", -horizontalRange, horizontalRange,
                AppPreferences.topLyricOffsetXDp(this), " dp",
                AppPreferences.KEY_TOP_LYRIC_OFFSET_X_DP);
        addSeek(layout, "垂直偏移", -240, 240, AppPreferences.topLyricOffsetYDp(this), " dp",
                AppPreferences.KEY_TOP_LYRIC_OFFSET_Y_DP);
        addSeek(layout, "下一句字号", 45, 160, AppPreferences.topLyricNextFontScale(this), "%",
                AppPreferences.KEY_TOP_LYRIC_NEXT_FONT_SCALE);
        addTopLyricToggle(layout, "显示歌词翻译（替代下一句）",
                AppPreferences.KEY_TOP_LYRIC_SHOW_TRANSLATION,
                AppPreferences.topLyricShowTranslation(this), null);
        addTopLyricToggle(layout, "显示律动条", AppPreferences.KEY_TOP_LYRIC_SPECTRUM,
                AppPreferences.topLyricSpectrum(this), null);
        ui.note(layout, "宽度可调到 10%，不再限制为 160dp；留白可避开车况与时钟。"
                + "状态栏对齐使用系统报告的高度并显示单行；ROM 未报告高度时使用 20dp。"
                + "顶部条为外挂悬浮窗，无法嵌入原车桌面或自动识别其图标。");

        LinearLayout textEffects = ui.card(content, "文字特效");
        LinearLayout effectParameters = column();
        LinearLayout effectMotion = column();
        LinearLayout effectSpeed = column();
        String initialEffect = LyricEffectRules.normalize(AppPreferences.get(this)
                .getString(AppPreferences.KEY_TOP_LYRIC_EFFECT, "none"));
        addChoiceValue(textEffects, "顶部条文字特效", LyricEffectCatalog.labels(),
                LyricEffectCatalog.values(), initialEffect, value -> {
                    AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_TOP_LYRIC_EFFECT, value).apply();
                    updateEffectParameters(value, effectParameters, effectMotion, effectSpeed);
                });
        addSeek(effectParameters, "特效强度", 0, 100,
                AppPreferences.get(this).getInt(AppPreferences.KEY_TOP_LYRIC_EFFECT_STRENGTH, 50), "%",
                AppPreferences.KEY_TOP_LYRIC_EFFECT_STRENGTH);
        addSeek(effectSpeed, "特效速度", 25, 300,
                AppPreferences.get(this).getInt(AppPreferences.KEY_TOP_LYRIC_EFFECT_SPEED, 100), "%",
                AppPreferences.KEY_TOP_LYRIC_EFFECT_SPEED);
        effectMotion.addView(effectSpeed);
        addChoiceValue(effectMotion, "特效帧率", new String[]{"关闭动画", "24 fps", "30 fps", "60 fps"},
                new String[]{"0", "24", "30", "60"},
                String.valueOf(AppPreferences.get(this).getInt(AppPreferences.KEY_TOP_LYRIC_EFFECT_FPS, 30)),
                value -> AppPreferences.setTopLyricInt(this, AppPreferences.KEY_TOP_LYRIC_EFFECT_FPS,
                        Integer.parseInt(value)));
        effectParameters.addView(effectMotion);
        textEffects.addView(effectParameters);
        updateEffectParameters(initialEffect, effectParameters, effectMotion, effectSpeed);
        ui.note(textEffects, "15 项特效及参数只作用于顶部歌词条。卡拉 OK 无逐字数据时按句长估算，"
                + "速度跟随播放；关闭动画停用动态项，保留镜像与磨砂。"
                + "低帧率也会限制本条的逐字、粒子与律动重绘。");

        LinearLayout effects = ui.card(content, "歌词粒子与消散");
        // The strip is its own display object: the dissolve family below is stored separately
        // from the main screen, so tuning the main overlay no longer changes the strip.
        final View[][] dependents = new View[1][];
        MaterialSwitch particles = addTopLyricToggle(effects, "本句结束粒子消散",
                AppPreferences.KEY_TOP_LYRIC_PARTICLES, AppPreferences.topLyricParticles(this),
                () -> setDependentVisibility(AppPreferences.topLyricParticles(this), dependents[0]));
        View[] amountRow = addSeekRow(effects, "粒子量", 20, 300,
                AppPreferences.topLyricParticleAmount(this), "%",
                AppPreferences.KEY_TOP_LYRIC_PARTICLE_AMOUNT);
        MaterialSwitch wordErase = addTopLyricToggle(effects, "逐字歌词及时擦除",
                AppPreferences.KEY_TOP_LYRIC_WORD_DISSOLVE,
                AppPreferences.topLyricWordDissolve(this), null);
        dependents[0] = new View[] { amountRow[0], amountRow[1], (View) wordErase.getParent() };
        setDependentVisibility(particles.isChecked(), dependents[0]);
        addSeek(effects, "上一句不透明度", 0, 100,
                AppPreferences.topLyricPreviousOpacity(this), "%",
                AppPreferences.KEY_TOP_LYRIC_PREVIOUS_OPACITY);
        ui.note(effects, "这些选项只作用于顶部歌词条，不影响主屏；首次调整前沿用主屏设置。");

        LinearLayout background = ui.card(content, "背景样式");
        addChoice(background, "顶部条配色环境", new String[]{"跟随应用 / 系统昼夜", "浅色背景（固定深色歌词）",
                        "深色背景（固定浅色歌词）"},
                new String[]{"auto", "light", "dark"}, AppPreferences.KEY_TOP_LYRIC_COLOR_ENVIRONMENT, "auto");
        ui.note(background, "系统昼夜模式与桌面状态栏图标颜色可能不同。"
                + "动态壁纸或地图下看不清时，可在这里单独指定顶部条配色环境；"
                + "应用无法读取其他窗口的实际图标颜色。");
        addBackgroundChoice(background);
        ui.note(background, "毛玻璃需要系统支持跨窗口模糊，不支持时降级为浅色半透明。");

        CustomFontStore.applyToViewTree(this, findViewById(R.id.status_lyric_root));
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void addSeek(LinearLayout parent, String title, int min, int max, int initial,
                         String suffix, String key) {
        addSeekRow(parent, title, min, max, initial, suffix, key);
    }

    /** Same row, but the caller gets the views it needs to show or hide a dependent setting. */
    private View[] addSeekRow(LinearLayout parent, String title, int min, int max, int initial,
                              String suffix, String key) {
        LinearLayout row = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.item_setting_seek, parent, false);
        ((TextView) row.findViewById(R.id.settings_row_title)).setText(title);
        TextView value = row.findViewById(R.id.settings_row_value);
        value.setText(format(initial, suffix));
        SeekBar seek = row.findViewById(R.id.settings_row_seek);
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
        parent.addView(row);
        return new View[] { row, seek };
    }

    /** Rows that only matter while the feature they belong to is on. */
    private static void setDependentVisibility(boolean visible, View[] views) {
        if (views == null) return;
        for (View view : views) {
            if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private MaterialSwitch addTopLyricToggle(LinearLayout parent, String title, String key,
                                             boolean initial, Runnable afterChange) {
        return ui.addToggle(parent, title, null, initial, (button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(key, checked).apply();
            updatePreview();
            AudioSpectrumSource.sync(this);
            AppPreferences.changed(this);
            if (afterChange != null) afterChange.run();
        });
    }

    private void addBackgroundChoice(LinearLayout parent) {
        addChoice(parent, "背景样式", new String[]{"完全透明", "毛玻璃", "紧凑单行背景"},
                new String[]{"transparent", "blur", "compact"}, AppPreferences.KEY_TOP_LYRIC_BACKGROUND,
                AppPreferences.topLyricBackground(this));
    }

    private void addChoice(LinearLayout parent, String title, String[] labels, String[] values,
                           String key, String fallback) {
        addChoiceValue(parent, title, labels, values, AppPreferences.get(this).getString(key, fallback),
                value -> AppPreferences.get(this).edit().putString(key, value).apply());
    }

    private interface ChoiceChange { void accept(String value); }

    /** Shared spinner handling keeps integer choices stored as integers, including FPS. */
    private void addChoiceValue(LinearLayout parent, String title, String[] labels, String[] values,
                                String initial, ChoiceChange onChange) {
        ui.addChoice(parent, title, labels, indexOf(values, initial),
                new AdapterView.OnItemSelectedListener() {
                    String selected = initial;

                    @Override public void onItemSelected(AdapterView<?> parentView,
                                                         View view, int position, long id) {
                        if (values[position].equals(selected)) return;
                        selected = values[position];
                        onChange.accept(selected);
                        updatePreview();
                        AppPreferences.changed(StatusLyricSettingsActivity.this);
                    }

                    @Override public void onNothingSelected(AdapterView<?> parentView) { }
                });
    }

    private static int indexOf(String[] values, String initial) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(initial)) return i;
        }
        return 0;
    }

    private static void updateEffectParameters(String value, View parameters, View motion, View speed) {
        parameters.setVisibility("none".equals(value) ? View.GONE : View.VISIBLE);
        motion.setVisibility(LyricEffectCatalog.find(value).animated ? View.VISIBLE : View.GONE);
        speed.setVisibility("karaoke".equals(value) ? View.GONE : View.VISIBLE);
    }

    private void updatePreview() {
        if (previewPanel != null) previewPanel.reloadStyle();
    }

    private static String format(int value, String suffix) {
        return (value > 0 && " dp".equals(suffix) ? "+" : "") + value + suffix;
    }

}
