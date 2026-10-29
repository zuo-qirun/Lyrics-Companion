package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Display-scoped controls for the compact lyric presentation. */
@SuppressLint("SetTextI18n")
public final class CompactSettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";
    /** LayoutInflater.from 需要已附着的 Activity，只能在 onCreate 里构造。 */
    private SettingsUi ui;
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
        ui = new SettingsUi(this);
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
        ui.actionButton(previewCard, "查看 / 收起效果预览", v -> preview.setVisibility(
                preview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        preview.setVisibility(View.GONE);
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
        ui.note(content, "紧凑歌词按窗口宽高自动缩放；双行模式优先显示本句与下一句。");
        navigation.addSection(content, SettingsCatalog.Category.COMMON, "歌词内容");

        LinearLayout spectrum = card("底部律动");
        addToggle(spectrum, "显示底部律动条", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        LinearLayout realRow = row(spectrum, "真实音频律动", AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM);
        ui.addToggle(realRow, "真实音频律动（关闭为虚拟律动）", null,
                AppPreferences.compactUseRealSpectrum(this, secondary), (button, checked) -> {
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
        navigation.scopeNote(realRow, AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM);
        spectrumStatus = noteText(spectrum, spectrumStatusText());
        navigation.addSection(spectrum, SettingsCatalog.Category.EFFECTS, "底部律动");

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

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        LinearLayout row = row(parent, title, key);
        ui.addToggle(row, title, null, initial, (button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        navigation.scopeNote(row, key);
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

    private TextView noteText(LinearLayout parent, String value) {
        TextView note = (TextView) getLayoutInflater().inflate(R.layout.item_setting_note, parent, false);
        note.setText(value);
        parent.addView(note);
        return note;
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
