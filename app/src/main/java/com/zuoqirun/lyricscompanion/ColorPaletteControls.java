package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Shared manual/automatic RGB color palette used by all lyric presentations. */
final class ColorPaletteControls {
    interface ColorStore { void set(int color); }

    private ColorPaletteControls() { }

    static void add(Context context, LinearLayout parent, String title, String automaticNote,
                    int savedColor, int automaticPreviewColor, ColorStore store, Runnable changed) {
        int pad = dp(context, 16);
        TextView heading = text(context, title, 14, 0xFFD7E1EE, true);
        heading.setPadding(0, pad, 0, 0);
        parent.addView(heading);
        int initial = savedColor == 0 ? automaticPreviewColor : savedColor;
        int[] rgb = {Color.red(initial), Color.green(initial), Color.blue(initial)};
        MaterialSwitch manual = new MaterialSwitch(context);
        manual.setText("手动调色盘");
        manual.setTextColor(0xFFF1F5FA);
        manual.setTextSize(14f);
        manual.setGravity(Gravity.CENTER_VERTICAL);
        manual.setPadding(0, dp(context, 6), 0, 0);
        manual.setChecked(savedColor != 0);
        parent.addView(manual);

        TextView automatic = text(context, automaticNote, 12, 0xFF8392A8, false);
        automatic.setPadding(0, 0, 0, dp(context, 4));
        automatic.setVisibility(savedColor == 0 ? View.VISIBLE : View.GONE);
        parent.addView(automatic);
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setVisibility(savedColor == 0 ? View.GONE : View.VISIBLE);
        TextView state = text(context, colorLabel(initial), 12, 0xFF8392A8, false);
        LinearLayout stateRow = new LinearLayout(context);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.addView(state, new LinearLayout.LayoutParams(0, -2, 1f));
        View swatch = new View(context);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 22));
        swatchParams.leftMargin = dp(context, 10);
        stateRow.addView(swatch, swatchParams);
        updateSwatch(context, swatch, initial);
        controls.addView(stateRow);
        TextView hint = text(context, "圆形调色盘：沿外圈选择色相，向中心降低饱和度", 12,
                0xFF8392A8, false);
        hint.setPadding(0, dp(context, 8), 0, dp(context, 3));
        controls.addView(hint);
        ColorCirclePickerView circle = new ColorCirclePickerView(context);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(-1, dp(context, 210));
        circleParams.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(circle, circleParams);
        circle.setColor(initial);
        Runnable apply = () -> {
            int color = Color.rgb(rgb[0], rgb[1], rgb[2]);
            store.set(color);
            state.setText(colorLabel(color));
            updateSwatch(context, swatch, color);
            circle.setColor(color);
            changed.run();
        };
        Channel[] channels = new Channel[]{
                addChannel(context, controls, "红", rgb, 0, apply),
                addChannel(context, controls, "绿", rgb, 1, apply),
                addChannel(context, controls, "蓝", rgb, 2, apply)};
        circle.setListener(color -> {
            rgb[0] = Color.red(color);
            rgb[1] = Color.green(color);
            rgb[2] = Color.blue(color);
            for (int index = 0; index < channels.length; index++) {
                channels[index].value.setText(Integer.toString(rgb[index]));
                channels[index].seek.setProgress(rgb[index]);
            }
            apply.run();
        });
        parent.addView(controls);
        manual.setOnCheckedChangeListener((button, enabled) -> {
            controls.setVisibility(enabled ? View.VISIBLE : View.GONE);
            automatic.setVisibility(enabled ? View.GONE : View.VISIBLE);
            if (enabled) apply.run();
            else {
                store.set(0);
                changed.run();
            }
        });
    }

    private static Channel addChannel(Context context, LinearLayout parent, String title, int[] rgb,
                                      int channel, Runnable apply) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 6), 0, 0);
        row.addView(text(context, title, 13, 0xFFD7E1EE, false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(context, Integer.toString(rgb[channel]), 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(context);
        seek.setMax(255);
        seek.setProgress(rgb[channel]);
        if (Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                value.setText(Integer.toString(progress));
                if (user) { rgb[channel] = progress; apply.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(context, 34)));
        return new Channel(value, seek);
    }

    private static void updateSwatch(Context context, View view, int color) {
        MaterialShapeDrawable shape = new MaterialShapeDrawable();
        shape.setFillColor(ColorStateList.valueOf(color));
        shape.setStroke(dp(context, 1), ColorStateList.valueOf(0xFF6B7C94));
        shape.setCornerSize(dp(context, 8));
        view.setBackground(shape);
    }

    private static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private static String colorLabel(int color) {
        return String.format(java.util.Locale.ROOT, "当前：#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Channel {
        final TextView value;
        final SeekBar seek;
        Channel(TextView value, SeekBar seek) { this.value = value; this.seek = seek; }
    }
}
