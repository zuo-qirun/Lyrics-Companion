package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Display-scoped controls for the compact lyric presentation. */
@SuppressLint("SetTextI18n")
public final class CompactSettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";
    private boolean secondary;
    private LyricsPanelView preview;
    private TextView spectrumStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        // The launcher is intentionally conditional. Finishing here also prevents stale
        // shortcuts from editing a style that is no longer selected.
        if (!"compact".equals(AppPreferences.overlayStyle(this, secondary))) {
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
        toolbar.setTitle((secondary ? "副屏" : "主屏") + "紧凑歌词详细设置");
        toolbar.setSubtitle("本页设置只影响当前屏幕的紧凑歌词样式");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(138)));

        LinearLayout content = card("歌词内容");
        addToggle(content, "显示下一句歌词（双行）", AppPreferences.KEY_COMPACT_SHOW_NEXT_LINE,
                AppPreferences.compactShowNextLine(this, secondary));
        addToggle(content, "显示歌词翻译（替代下一句）",
                AppPreferences.KEY_REFINED_SHOW_TRANSLATION,
                AppPreferences.refinedShowTranslation(this, secondary));
        addToggle(content, "显示封面、歌名和歌手", AppPreferences.KEY_COMPACT_SHOW_COVER,
                AppPreferences.compactShowCover(this, secondary));
        addCard(root, content);

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
                Toast.makeText(this, "请在首页“使用权限”中授予录音频谱权限",
                        Toast.LENGTH_LONG).show();
            }
            changed();
            updateSpectrumStatus();
        });
        spectrum.addView(realSpectrum);
        spectrumStatus = text(spectrumStatusText(), 12, 0xFF8392A8, false);
        spectrumStatus.setPadding(0, dp(7), 0, 0);
        spectrum.addView(spectrumStatus);
        addCard(root, spectrum);

        TextView note = text("紧凑歌词会按窗口宽高自动缩放。双行模式会优先显示本句和下一句；逐字时间轴会保留逐字点亮和跟随滚动。", 12,
                0xFF8392A8, false);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(note);
        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
        updateSpectrumStatus();
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        styleToggle(toggle);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        parent.addView(toggle);
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

    private void addCard(LinearLayout root, LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(card, params);
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
