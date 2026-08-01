package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
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
        addSeek(panel, "下一句字号", 45, 120,
                AppPreferences.nextLyricScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_SCALE, value));
        addSeek(panel, "下一句不透明度", 20, 100,
                AppPreferences.nextLyricOpacity(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_OPACITY, value));
        addToggle(panel, "平滑滚动换句", AppPreferences.KEY_SMOOTH_LYRIC_SCROLL,
                AppPreferences.smoothLyricScroll(this, secondary));
        addSeek(panel, "歌词显示行数", 1, 3,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_LYRIC_LINES, 3), " 行",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_LYRIC_LINES, value));
        addSeek(panel, "歌词时间校正", -5000, 5000,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_LYRIC_OFFSET, 0), " ms",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_LYRIC_OFFSET, value));
        addCard(root, panel);

        LinearLayout sourceCorrection = card("按播放器校正");
        addSourceCorrection(sourceCorrection);
        addCard(root, sourceCorrection);

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
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            changed();
        });
        parent.addView(toggle);
    }

    private void addSourceCorrection(LinearLayout parent) {
        TextView description = text("在全局时间校正的基础上，为不同播放器单独微调。", 12,
                0xFF8392A8, false);
        description.setPadding(0, dp(10), 0, dp(4));
        parent.addView(description);
        String[] labels = {"网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "其他播放器"};
        String[] sourceIds = {"netease", "qqmusic", "kugou", "kuwo", "soda", "media"};
        String active = MusicStateStore.activeSourceId();
        int initialIndex = sourceIndex(sourceIds, active);
        final String[] selectedSource = {sourceIds[initialIndex]};

        Spinner picker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        picker.setAdapter(adapter);
        picker.setSelection(initialIndex);
        parent.addView(picker, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(text("所选播放器额外校正", 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text("0 ms", 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(10_000);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int offsetMs = progress - 5_000;
                value.setText(formatValue(offsetMs, " ms"));
                if (!fromUser) return;
                AppPreferences.putLyricSourceOffsetMs(DisplaySettingsActivity.this, secondary,
                        selectedSource[0], offsetMs);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, android.view.View view,
                                                 int position, long id) {
                selectedSource[0] = sourceIds[position];
                int offsetMs = AppPreferences.lyricSourceOffsetMs(DisplaySettingsActivity.this,
                        secondary, selectedSource[0]);
                seek.setProgress(offsetMs + 5_000);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        seek.setProgress(AppPreferences.lyricSourceOffsetMs(this, secondary, selectedSource[0])
                + 5_000);
    }

    private static int sourceIndex(String[] sourceIds, String sourceId) {
        for (int i = 0; i < sourceIds.length; i++) {
            if (sourceIds[i].equals(sourceId)) return i;
        }
        return sourceIds.length - 1;
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
