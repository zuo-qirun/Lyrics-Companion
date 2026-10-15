package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Display-scoped controls for the compact lyric presentation. */
@SuppressLint("SetTextI18n")
public final class CompactSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";
    private boolean secondary;
    private int displaySlot;
    private LyricsPanelView preview;
    private SettingsNavigator navigation;
    private TextView spectrumStatus;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        displaySlot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = displaySlot > DisplaySlotRegistry.MAIN_SLOT;
        // The launcher is intentionally conditional. Finishing here also prevents stale
        // shortcuts from editing a style that is no longer selected.
        if (!"compact".equals(AppPreferences.overlayStyle(this, secondary))) {
            finish();
            return;
        }
        navigation = new SettingsNavigator(this, "紧凑专属设置",
                DisplaySlotRegistry.slotLabel(this, displaySlot) + " · 紧凑", "compact", new SettingsValues(this, secondary));
        preview = new LyricsPanelView(this, secondary);
        LinearLayout previewCard = card("效果预览");
        com.google.android.material.button.MaterialButton previewToggle = new com.google.android.material.button.MaterialButton(this);
        previewToggle.setText("查看 / 收起效果预览");
        previewToggle.setOnClickListener(v -> preview.setVisibility(
                preview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        preview.setVisibility(View.GONE);
        previewCard.addView(previewToggle);
        previewCard.addView(preview, new LinearLayout.LayoutParams(-1, dp(138)));
        navigation.addSection(previewCard, SettingsCatalog.Category.COMMON, "效果预览");

        LinearLayout content = card("歌词内容");
        addToggle(content, "显示下一句歌词（双行）", AppPreferences.KEY_COMPACT_SHOW_NEXT_LINE,
                AppPreferences.compactShowNextLine(this, secondary));
        addToggle(content, "显示歌词翻译（替代下一句）",
                AppPreferences.KEY_REFINED_SHOW_TRANSLATION,
                AppPreferences.refinedShowTranslation(this, secondary));
        addToggle(content, "显示封面、歌名和歌手", AppPreferences.KEY_COMPACT_SHOW_COVER,
                AppPreferences.compactShowCover(this, secondary));
        navigation.addSection(content, SettingsCatalog.Category.COMMON, "歌词内容");

        LinearLayout spectrum = card("底部律动");
        addToggle(spectrum, "显示底部律动条", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        MaterialSwitch realSpectrum = new MaterialSwitch(this);
        realSpectrum.setText("真实音频律动（关闭为虚拟律动）");
        styleToggle(realSpectrum);
        realSpectrum.setChecked(AppPreferences.compactUseRealSpectrum(this, secondary));
        realSpectrum.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary,
                    AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM, checked);
            if (checked && android.os.Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                SafeToast.show(this, "请在首页“使用权限”中授予录音频谱权限",
                        Toast.LENGTH_LONG);
            }
            changed();
            updateSpectrumStatus();
        });
        LinearLayout realRow = navigation.row(spectrum, AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM, "真实音频律动");
        realRow.addView(realSpectrum);
        navigation.scopeNote(realRow, AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM);
        spectrumStatus = text(spectrumStatusText(), 12, 0xFF8392A8, false);
        spectrumStatus.setPadding(0, dp(7), 0, 0);
        spectrum.addView(spectrumStatus);
        navigation.addSection(spectrum, SettingsCatalog.Category.EFFECTS, "底部律动");

        TextView note = text("紧凑歌词按窗口宽高自动缩放；双行模式优先显示本句与下一句。", 12,
                0xFF8392A8, false);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        content.addView(note);
        navigation.finish(state);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
        updateSpectrumStatus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (navigation != null) navigation.save(state);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (navigation == null || !navigation.back()) super.onBackPressed();
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        parent = navigation.row(parent, key, title);
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        styleToggle(toggle);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        parent.addView(toggle);
        navigation.scopeNote(parent, key);
    }

    private String spectrumStatusText() {
        return AudioSpectrumSource.status(this,
                AppPreferences.compactUseRealSpectrum(this, secondary));
    }

    private void updateSpectrumStatus() {
        if (spectrumStatus != null) spectrumStatus.setText(spectrumStatusText());
    }

    private void changed() {
        if (preview != null) preview.reloadStyle();
        if (navigation != null) navigation.refresh();
        AppPreferences.changed(this);
        AudioSpectrumSource.sync(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
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

    private void styleToggle(MaterialSwitch toggle) {
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, 0);
    }

    private TextView text(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
