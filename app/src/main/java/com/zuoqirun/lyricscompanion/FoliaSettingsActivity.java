package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/** Per-screen controls for the lightweight and faithful Folia renderers. */
@SuppressLint("SetTextI18n")
public final class FoliaSettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";
    /** LayoutInflater.from 需要已附着的 Activity，只能在 onCreate 里构造。 */
    private SettingsUi ui;
    private boolean secondary;
    private String style;
    private LyricsPanelView preview;
    private LinearLayout ringSettings;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new SettingsUi(this);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        int slot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = slot == DisplaySlotRegistry.SECONDARY_SLOT;
        style = AppPreferences.overlayStyle(this, secondary);
        if (!FoliaModeCatalog.settingsVisible(slot, style)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_folia_settings);
        MaterialToolbar toolbar = findViewById(R.id.folia_toolbar);
        String styleLabel = FoliaModeCatalog.labelForStyle(style);
        boolean original = FoliaModeCatalog.isOriginalStyle(style);
        toolbar.setTitle((secondary ? "副屏 " : "主屏 ") + styleLabel + " 详细设置");
        toolbar.setSubtitle("只影响这一屏的" + styleLabel);
        toolbar.setNavigationOnClickListener(v -> finish());
        LinearLayout root = findViewById(R.id.folia_content);

        preview = new LyricsPanelView(this, secondary);
        LinearLayout previewCard = ui.card(root);
        previewCard.addView(preview, new LinearLayout.LayoutParams(-1, dp(230)));

        LinearLayout effects = ui.card(root, "效果与动画");
        String initialMode = AppPreferences.foliaMode(this, secondary);
        choice(effects, styleLabel + " 效果", FoliaModeCatalog.LABELS, FoliaModeCatalog.VALUES,
                initialMode, value -> {
                    AppPreferences.putDisplayString(this, secondary, AppPreferences.KEY_FOLIA_MODE, value);
                    changed();
                });
        toggle(effects, "每首歌随机使用一个效果", AppPreferences.KEY_FOLIA_RANDOM,
                AppPreferences.displayBoolean(this, secondary, AppPreferences.KEY_FOLIA_RANDOM, false));
        ui.actionButton(effects, "选择参与随机的效果", v -> selectRandomPool());
        ui.note(effects, "切歌时重新抽取，同一首歌内保持不变；多个效果时避免连续重复。"
                + "全部 14 种均可参与，每个屏幕独立抽取。");
        seek(effects, "动画强度", AppPreferences.KEY_FOLIA_STRENGTH, 0, 100, 50, "%");
        choice(effects, "动画帧率", new String[]{"关闭动画", "24 fps", "30 fps", "60 fps"},
                new String[]{"0", "24", "30", "60"},
                String.valueOf(AppPreferences.foliaFps(this, secondary)),
                value -> {
                    AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_FOLIA_FPS,
                            Integer.parseInt(value));
                    changed();
                });
        toggle(effects, "显示歌词翻译", AppPreferences.KEY_REFINED_SHOW_TRANSLATION,
                AppPreferences.refinedShowTranslation(this, secondary));
        ui.note(effects, original
                ? "独立 Canvas 还原版，使用原版镜头和动画。暂停冻结运动；无逐字数据时按本句时长估算。"
                : "原生改编版，布局与光效可能与 Folia 原版不同。暂停冻结运动；无逐字数据时按本句时长估算。");

        LinearLayout ring = ui.card(root, "回环轨道");
        ringSettings = ring;
        updateRingVisibility();
        seek(ring, "主歌词放大倍率", AppPreferences.KEY_FOLIA_FOCUS, 100, 250, 165, "%");
        seek(ring, "轨道半径", AppPreferences.KEY_FOLIA_RADIUS, 40, 180, 100, "%");
        seek(ring, "轨道倾斜度", AppPreferences.KEY_FOLIA_TILT, 0, 90, 45, "°");
        toggle(ring, "中间轴线", AppPreferences.KEY_FOLIA_AXIS,
                AppPreferences.displayBoolean(this, secondary, AppPreferences.KEY_FOLIA_AXIS, true));

        LinearLayout appearance = ui.card(root, "文字与显示");
        seek(appearance, "字号", AppPreferences.KEY_TEXT_SCALE, 75, 220, 100, "%");
        seek(appearance, "额外字间距（全部效果）", AppPreferences.KEY_FOLIA_TRACKING, 0, 30, 0, " dp");
        toggle(appearance, "显示底部下一句", AppPreferences.KEY_FOLIA_SHOW_NEXT,
                AppPreferences.displayBoolean(this, secondary, AppPreferences.KEY_FOLIA_SHOW_NEXT, true));
        seek(appearance, "底部下一句字号", AppPreferences.KEY_FOLIA_NEXT_SCALE, 50, 400, 100, "%");
        ui.note(appearance, "底部优先显示已开启的翻译；没有翻译或关闭翻译时显示下一句。下一句字号独立于主歌词。");
        ui.note(appearance, "14 种效果共用这一屏的" + styleLabel + "参数；主屏、副屏和两个版本分别保存。"
                + "建议使用较大的悬浮窗或全屏。");
        ui.actionButton(appearance, "调整本屏" + styleLabel + "的尺寸与背景",
                v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                        .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, secondary)));
        ui.actionButton(appearance, "调整本屏" + styleLabel + "的颜色",
                v -> startActivity(new Intent(this, ColorSettingsActivity.class)
                        .putExtra(ColorSettingsActivity.EXTRA_SCOPE, secondary
                                ? DisplaySlotRegistry.SECONDARY_SLOT : DisplaySlotRegistry.MAIN_SLOT)));

        if (original) {
            LinearLayout custom = ui.card(root, "还原版自定义");
            seek(custom, "背景元素强度", AppPreferences.KEY_FOLIA_ORIGINAL_BACKGROUND, 0, 150, 100, "%");
            seek(custom, "歌词浮动幅度", AppPreferences.KEY_FOLIA_ORIGINAL_FLOAT, 0, 200, 100, "%");
            seek(custom, "逐字间距", AppPreferences.KEY_FOLIA_ORIGINAL_WORD_SPACING, 80, 160, 100, "%");
            seek(custom, "字幕字号", AppPreferences.KEY_FOLIA_ORIGINAL_SUBTITLE_SCALE, 75, 150, 100, "%");
            toggle(custom, "逐字旋转", AppPreferences.KEY_FOLIA_ORIGINAL_WORD_ROTATION,
                    AppPreferences.displayBoolean(this, secondary,
                            AppPreferences.KEY_FOLIA_ORIGINAL_WORD_ROTATION, true));
            toggle(custom, "显示歌曲信息", AppPreferences.KEY_FOLIA_ORIGINAL_HEADER,
                    AppPreferences.displayBoolean(this, secondary,
                            AppPreferences.KEY_FOLIA_ORIGINAL_HEADER, true));
            ui.note(custom, "背景元素强度作用于莫奈花瓣与浮名纸面装饰；浮动幅度作用于流光/云阶的整行漂浮；"
                    + "逐字旋转只影响流光。");

            LinearLayout background = ui.card(root, "背景模式");
            choice(background, "还原版背景类型",
                    new String[]{"无背景", "通用", "隐现", "莫奈", "漫游", "空", "嵌入"},
                    new String[]{"", "common", "latent", "monet", "nomand", "sora", "url"},
                    AppPreferences.displayString(this, secondary, AppPreferences.KEY_FOLIA_ORIGINAL_BG_MODE, ""),
                    value -> {
                        AppPreferences.putDisplayString(this, secondary,
                                AppPreferences.KEY_FOLIA_ORIGINAL_BG_MODE, value);
                        changed();
                    });
            ui.note(background, "通用为几何图形；隐现按封面色生成流体场；莫奈为柔焦画像；漫游为网点纸面；"
                    + "空为星场；嵌入需要网页地址，留空时保持纯色。"
                    + "“背景元素强度”会整体调节背景浓度；“无背景”保持原来的纯色底。");
        }

        LinearLayout audio = ui.card(root, "真实律动");
        toggle(audio, "跟随真实频谱", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        toggle(audio, "启用真实音频采集", AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM,
                AppPreferences.compactUseRealSpectrum(this, secondary));
        ui.note(audio, original
                ? "把真实音频频谱传入原版场景。需要在权限设置中授权录音，且 ROM 提供输出频谱；无信号时不伪造节拍。"
                : "回环与心象使用真实低频驱动。需要在权限设置中授权录音，且 ROM 提供输出频谱；无信号时不伪造节拍。");

        // 整页套用自定义字体 / UI 缩放（含工具栏），与 SettingsNavigator 页面保持一致。
        CustomFontStore.applyToViewTree(this, findViewById(R.id.folia_root));
    }

    @Override protected void onResume() {
        super.onResume();
        if (preview == null) return;
        if (!style.equals(AppPreferences.overlayStyle(this, secondary))) {
            finish();
            return;
        }
        preview.reloadStyle();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        if (preview != null) LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void selectRandomPool() {
        String[] selected = FoliaEffectSelector.pool(AppPreferences.displayString(this, secondary, AppPreferences.KEY_FOLIA_RANDOM_POOL, ""));
        boolean[] checked = new boolean[FoliaModeCatalog.VALUES.length];
        for (int i = 0; i < checked.length; i++) for (String mode : selected) checked[i] |= mode.equals(FoliaModeCatalog.VALUES[i]);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("参与随机的" + FoliaModeCatalog.labelForStyle(style) + "效果")
                .setMultiChoiceItems(FoliaModeCatalog.LABELS, checked, (view, which, value) -> checked[which] = value)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(view -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < checked.length; i++) if (checked[i]) {
                if (encoded.length() > 0) encoded.append(',');
                encoded.append(FoliaModeCatalog.VALUES[i]);
            }
            if (encoded.length() == 0) {
                SafeToast.show(this, "请至少选择一个效果", android.widget.Toast.LENGTH_SHORT); return;
            }
            AppPreferences.putDisplayString(this, secondary, AppPreferences.KEY_FOLIA_RANDOM_POOL, encoded.toString());
            changed(); dialog.dismiss();
        }));
        dialog.show();
    }

    private void changed() {
        updateRingVisibility();
        preview.reloadStyle();
        AppPreferences.changed(this);
        AudioSpectrumSource.sync(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private void updateRingVisibility() {
        if (ringSettings == null) return;
        SettingsUi.containerOf(ringSettings).setVisibility(
                "claddagh".equals(AppPreferences.foliaMode(this, secondary))
                        || AppPreferences.displayBoolean(this, secondary,
                        AppPreferences.KEY_FOLIA_RANDOM, false) ? View.VISIBLE : View.GONE);
    }

    /**
     * 取值范围不从 0 开始的滑块：SettingsUi 的显示值是相对进度，这里换回绝对值和单位，
     * 与旧版「动画强度 · 165%」的读数保持一致。
     */
    private void seek(LinearLayout parent, String title, String key, int min, int max,
                      int fallback, String suffix) {
        int initial = Math.max(min, Math.min(max, AppPreferences.displayInt(this, secondary, key, fallback)));
        SeekBar seek = ui.addSeek(parent, title, max - min, initial - min, null, null);
        final TextView value = ((View) seek.getParent()).findViewById(R.id.settings_row_value);
        value.setText(initial + suffix);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText((min + progress) + suffix);
                if (!fromUser) return;
                AppPreferences.putDisplayInt(FoliaSettingsActivity.this, secondary, key, min + progress);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void toggle(LinearLayout parent, String title, String key, boolean value) {
        ui.addToggle(parent, title, null, value, (view, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
    }

    private void choice(LinearLayout parent, String title, String[] labels, String[] values,
                        String selected, StringConsumer consumer) {
        int selection = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) selection = i;
        final String[] current = {values[selection]};
        ui.addChoice(parent, title, labels, selection, new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                if (current[0].equals(values[position])) return;
                current[0] = values[position];
                consumer.accept(current[0]);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface StringConsumer { void accept(String value); }
}
