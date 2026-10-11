package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** One Folia style, with a display-specific choice of its fourteen native effects. */
@SuppressLint("SetTextI18n")
public final class FoliaSettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";
    private boolean secondary;
    private LyricsPanelView preview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        int slot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = slot == DisplaySlotRegistry.SECONDARY_SLOT;
        if (!FoliaModeCatalog.settingsVisible(slot, AppPreferences.overlayStyle(this, secondary))) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle((secondary ? "副屏" : "主屏") + " Folia 详细设置");
        toolbar.setSubtitle("只影响这一屏的 Folia 样式");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));
        preview = new LyricsPanelView(this, secondary);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(230)));

        LinearLayout effects = card("效果与动画");
        LinearLayout ring = card("回环轨道");
        String initialMode = AppPreferences.foliaMode(this, secondary);
        ring.setVisibility("claddagh".equals(initialMode) ? View.VISIBLE : View.GONE);
        choice(effects, "Folia 效果", FoliaModeCatalog.LABELS, FoliaModeCatalog.VALUES,
                initialMode, value -> {
                    AppPreferences.putDisplayString(this, secondary, AppPreferences.KEY_FOLIA_MODE, value);
                    ring.setVisibility("claddagh".equals(value) ? View.VISIBLE : View.GONE);
                    changed();
                });
        seek(effects, "动画强度", AppPreferences.KEY_FOLIA_STRENGTH, 0, 100, 50, "%");
        choice(effects, "动画帧率", new String[]{"关闭动画", "24 fps", "30 fps", "60 fps"},
                new String[]{"0", "24", "30", "60"},
                String.valueOf(AppPreferences.displayInt(this, secondary, AppPreferences.KEY_FOLIA_FPS, 30)),
                value -> {
                    AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_FOLIA_FPS,
                            Integer.parseInt(value));
                    changed();
                });
        toggle(effects, "显示歌词翻译", AppPreferences.KEY_REFINED_SHOW_TRANSLATION,
                AppPreferences.refinedShowTranslation(this, secondary));
        effects.addView(text("原生改编版，布局与光效可能与 Folia 原版不同。暂停冻结运动；无逐字数据时按本句时长估算。",
                12, 0xFF8392A8, false));
        addCard(root, effects);

        seek(ring, "主歌词放大倍率", AppPreferences.KEY_FOLIA_FOCUS, 100, 250, 165, "%");
        seek(ring, "轨道半径", AppPreferences.KEY_FOLIA_RADIUS, 40, 180, 100, "%");
        seek(ring, "轨道倾斜度", AppPreferences.KEY_FOLIA_TILT, 0, 90, 45, "°");
        seek(ring, "额外字间距", AppPreferences.KEY_FOLIA_TRACKING, 0, 30, 0, " dp");
        toggle(ring, "中间轴线", AppPreferences.KEY_FOLIA_AXIS,
                AppPreferences.displayBoolean(this, secondary, AppPreferences.KEY_FOLIA_AXIS, true));
        addCard(root, ring);

        LinearLayout appearance = card("文字与显示");
        seek(appearance, "字号", AppPreferences.KEY_TEXT_SCALE, 75, 220, 100, "%");
        appearance.addView(text("14 种效果共用这一屏的 Folia 参数；主屏和副屏分别保存，其他歌词样式不受影响。建议使用较大的悬浮窗或全屏。",
                12, 0xFF8392A8, false));
        MaterialButton display = new MaterialButton(this);
        display.setText("调整本屏 Folia 的尺寸与背景");
        display.setOnClickListener(v -> startActivity(new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, secondary)));
        appearance.addView(display, new LinearLayout.LayoutParams(-1, dp(50)));
        MaterialButton colors = new MaterialButton(this);
        colors.setText("调整本屏 Folia 的颜色");
        colors.setOnClickListener(v -> startActivity(new Intent(this, ColorSettingsActivity.class)
                .putExtra(ColorSettingsActivity.EXTRA_SCOPE, secondary
                        ? DisplaySlotRegistry.SECONDARY_SLOT : DisplaySlotRegistry.MAIN_SLOT)));
        appearance.addView(colors, new LinearLayout.LayoutParams(-1, dp(50)));
        addCard(root, appearance);

        LinearLayout audio = card("真实律动");
        toggle(audio, "跟随真实频谱", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        toggle(audio, "启用真实音频采集", AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM,
                AppPreferences.compactUseRealSpectrum(this, secondary));
        audio.addView(text("回环与心象使用真实低频驱动。需要在权限设置中授权录音，且 ROM 提供输出频谱；无信号时不伪造节拍。",
                12, 0xFF8392A8, false));
        addCard(root, audio);
        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (preview == null) return;
        if (!FoliaModeCatalog.isStyle(AppPreferences.overlayStyle(this, secondary))) {
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

    private void changed() {
        preview.reloadStyle();
        AppPreferences.changed(this);
        AudioSpectrumSource.sync(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private void seek(LinearLayout parent, String title, String key, int min, int max,
                      int fallback, String suffix) {
        int initial = Math.max(min, Math.min(max, AppPreferences.displayInt(this, secondary, key, fallback)));
        TextView label = text(title + " · " + initial + suffix, 14, 0xFFD7E1EE, false);
        parent.addView(label);
        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        slider.setProgress(initial - min);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                label.setText(title + " · " + (min + progress) + suffix);
                if (!fromUser) return;
                AppPreferences.putDisplayInt(FoliaSettingsActivity.this, secondary, key, min + progress);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(slider, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void toggle(LinearLayout parent, String title, String key, boolean value) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14);
        toggle.setChecked(value);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    }

    private void choice(LinearLayout parent, String title, String[] labels, String[] values,
                        String selected, StringConsumer consumer) {
        parent.addView(text(title, 14, 0xFFD7E1EE, false));
        Spinner picker = new Spinner(this, Spinner.MODE_DIALOG);
        picker.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        int selection = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) selection = i;
        picker.setSelection(selection, false);
        final String[] current = {values[selection]};
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                if (current[0].equals(values[position])) return;
                current[0] = values[position];
                consumer.accept(current[0]);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        parent.addView(picker, new LinearLayout.LayoutParams(-1, dp(52)));
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface StringConsumer { void accept(String value); }
}
