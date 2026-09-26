package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Only the adjustments a driver is likely to make repeatedly; expert parameters stay separate. */
@SuppressLint("SetTextI18n")
public final class QuickDisplaySettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";

    private boolean secondary;
    private int displaySlot;
    private LyricsPanelView preview;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        displaySlot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = displaySlot > DisplaySlotRegistry.MAIN_SLOT;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(DisplaySlotRegistry.slotLabel(this, displaySlot) + "日常显示调整");
        toolbar.setSubtitle("只保留最常用的尺寸、字号和透明度");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(170));
        previewParams.topMargin = dp(8);
        root.addView(preview, previewParams);

        LinearLayout panel = card("日常调整");
        int minWidth = AppPreferences.minimumPanelWidthDp(this, secondary);
        int minHeight = AppPreferences.minimumPanelHeightDp(this, secondary);
        int maxWidth = Math.max(minWidth, Math.round(getResources().getDisplayMetrics().widthPixels
                / Math.max(0.1f, getResources().getDisplayMetrics().density)));
        int maxHeight = Math.max(minHeight, Math.round(getResources().getDisplayMetrics().heightPixels
                / Math.max(0.1f, getResources().getDisplayMetrics().density)));
        addSeek(panel, "歌词窗口宽度", minWidth, maxWidth,
                AppPreferences.panelWidthDp(this, secondary), " dp",
                value -> AppPreferences.setPanelWidthDp(this, secondary, value));
        addSeek(panel, "歌词窗口高度", minHeight, maxHeight,
                AppPreferences.panelHeightDp(this, secondary), " dp",
                value -> AppPreferences.setPanelHeightDp(this, secondary, value));
        addSeek(panel, "歌词字号", 75, 220,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_TEXT_SCALE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TEXT_SCALE, value));
        addSeek(panel, "窗口不透明度", 20, 100,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_OPACITY, 88), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_OPACITY, value));
        addCard(root, panel);

        TextView help = text("位置请直接拖动悬浮歌词；更多选项请切到完整模式。",
                12, 0xFF8392A8, false);
        help.setLineSpacing(0f, 1.18f);
        help.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(help);
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

    private void addSeek(LinearLayout parent, String title, int min, int max, int initial,
                         String suffix, IntConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(text(title, 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(initial + suffix, 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(clamp(initial, min, max) - min);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(selected + suffix);
                if (!fromUser) return;
                consumer.accept(selected);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void changed() {
        preview.reloadStyle();
        AppPreferences.changed(this);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(ColorStateList.valueOf(0xFF101E31));
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
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private interface IntConsumer { void accept(int value); }
}
