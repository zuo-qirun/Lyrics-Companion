package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** A focused parameter page for exactly one overlay display. */
@SuppressLint("SetTextI18n")
public final class DisplaySettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";

    private boolean secondary;
    private LyricsPanelView preview;

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
        toolbar.setTitle(secondary ? "副屏显示参数" : "主屏显示参数");
        toolbar.setSubtitle("仅影响当前屏幕，另一块屏幕不会改变");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(190));
        previewParams.topMargin = dp(8);
        root.addView(preview, previewParams);

        LinearLayout panel = card("悬浮窗尺寸与文字");
        addSeek(panel, "悬浮窗宽度", AppPreferences.minimumPanelWidthDp(this, secondary), 900,
                AppPreferences.panelWidthDp(this, secondary), " dp",
                value -> AppPreferences.setPanelWidthDp(this, secondary, value));
        addSeek(panel, "悬浮窗高度", AppPreferences.minimumPanelHeightDp(this, secondary), 600,
                AppPreferences.panelHeightDp(this, secondary), " dp",
                value -> AppPreferences.setPanelHeightDp(this, secondary, value));
        addSeek(panel, "字号", 75, 150,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_TEXT_SCALE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TEXT_SCALE, value));
        addSeek(panel, "歌词显示行数", 1, 3,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_LYRIC_LINES, 3), " 行",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_LYRIC_LINES, value));
        addSeek(panel, "歌词时间校正", -5000, 5000,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_LYRIC_OFFSET, 0), " ms",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_LYRIC_OFFSET, value));
        addCard(root, panel);

        LinearLayout artwork = card("背景与封面");
        addSeek(artwork, "背景不透明度", 0, 100,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_OPACITY, 88), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_OPACITY, value));
        addSeek(artwork, "封面大小", 60, 150,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_COVER_SIZE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_COVER_SIZE, value));
        addSeek(artwork, "封面背景柔化", 0, 128,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, 128), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, value));
        addSeek(artwork, "封面背景遮罩", 0, 80,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, 38), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, value));
        addCard(root, artwork);

        if (secondary) {
            LinearLayout compact = card("紧凑单行");
            addToggle(compact, "显示封面", AppPreferences.KEY_COMPACT_SHOW_COVER,
                    AppPreferences.compactShowCover(this, true));
            addToggle(compact, "显示底部律动条", AppPreferences.KEY_COMPACT_SHOW_BARS,
                    AppPreferences.compactShowBars(this, true));
            TextView note = text("仅在副屏选择“紧凑单行”样式时生效。", 12, 0xFF8392A8, false);
            note.setPadding(0, dp(8), 0, 0);
            compact.addView(note);
            addCard(root, compact);
        }

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

    private void addSeek(LinearLayout parent, String title, int min, int max,
                         int initial, String suffix, IntConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(text(title, 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(formatValue(initial, suffix), 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(clamp(initial, min, max) - min);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(formatValue(selected, suffix));
                if (!fromUser) return;
                consumer.accept(selected);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, true, key, checked);
            changed();
        });
        parent.addView(toggle);
    }

    private void changed() {
        preview.reloadStyle();
        AppPreferences.changed(this);
        LyricsDisplayService.setSettingsVisible(this, true);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatValue(int value, String suffix) {
        return (value > 0 && " ms".equals(suffix) ? "+" : "") + value + suffix;
    }

    private interface IntConsumer { void accept(int value); }
}
