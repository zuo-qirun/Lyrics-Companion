package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/** Shared manual/automatic RGBA color palette used by all lyric presentations. */
final class ColorPaletteControls {
    interface ColorStore { void set(int color); }

    /**
     * Lowest alpha value the palette writes. {@link AppPreferences} treats alpha zero as the old
     * "plain RGB" value and forces it opaque, so alpha zero cannot survive a round trip; one is
     * visually indistinguishable from fully transparent.
     */
    static final int MIN_STORED_ALPHA = 1;

    private ColorPaletteControls() { }

    // ---- 预设配色方案（issue #131） ----

    /** 一套预设配色：一次写入本屏 × 当前样式的歌词、描边、背景与律动颜色。 */
    static final class Scheme {
        final String id;
        final String name;
        final int current;
        final int inactive;
        final int outline;
        final int backgroundLight;
        final int backgroundDark;
        final int spectrum;

        Scheme(String id, String name, int current, int inactive, int outline,
               int backgroundLight, int backgroundDark, int spectrum) {
            this.id = id;
            this.name = name;
            this.current = current;
            this.inactive = inactive;
            this.outline = outline;
            this.backgroundLight = backgroundLight;
            this.backgroundDark = backgroundDark;
            this.spectrum = spectrum;
        }
    }

    private static final Scheme[] SCHEMES = {
            new Scheme("warm", "暖阳", 0xFFFFD27D, 0xFFD9B48F, 0xFF4A2E12,
                    0xFFFFF6E8, 0xFF241A10, 0xFFFFB86B),
            new Scheme("deep", "深海", 0xFF8FD8FF, 0xFF6C93B4, 0xFF061A2C,
                    0xFFEAF5FF, 0xFF071626, 0xFF53C8FF),
            new Scheme("forest", "森林", 0xFFC8F59A, 0xFF8FB98A, 0xFF0E2412,
                    0xFFF2FBEA, 0xFF0B1A10, 0xFF8BE07A),
            new Scheme("neon", "霓虹", 0xFFFF4FD8, 0xFF9B7BFF, 0xFF16002E,
                    0xFFF6ECFF, 0xFF10001F, 0xFF6EE7F2),
            new Scheme("morandi", "莫兰迪", 0xFFEFE6DA, 0xFFB9AFA5, 0xFF3A3631,
                    0xFFF3F0EB, 0xFF2A2723, 0xFFC9BFB2),
    };

    /** 色球可点的预设；每次返回副本，避免调用方改动静态表。 */
    static Scheme[] schemes() {
        return SCHEMES.clone();
    }

    /** 读取存下来的预设 id；容忍大小写与空白，未知值返回 null（按"未套用预设"处理）。 */
    static Scheme schemeFor(String stored) {
        if (stored == null) return null;
        String normalized = stored.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        for (Scheme scheme : SCHEMES) {
            if (scheme.id.equals(normalized)) return scheme;
        }
        return null;
    }

    static void add(Context context, LinearLayout parent, String title, String automaticNote,
                    int savedColor, int automaticPreviewColor, boolean supportsAlpha,
                    ColorStore store, Runnable changed) {
        int pad = dp(context, 16);
        TextView heading = text(context, title, 14, 0xFFD7E1EE, true);
        heading.setPadding(0, pad, 0, 0);
        parent.addView(heading);
        int initial = savedColor == 0 ? automaticPreviewColor : savedColor;
        if (!supportsAlpha) initial |= 0xFF000000;
        int initialAlpha = (initial >>> 24) & 0xFF;
        int[] alpha = {initialAlpha == 0 ? 0xFF : initialAlpha};
        int[] rgb = {(initial >> 16) & 0xFF, (initial >> 8) & 0xFF, initial & 0xFF};
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
        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(context, 6), 0, 0);
        TextInputLayout colorInput = new TextInputLayout(context);
        colorInput.setHint(supportsAlpha ? "色号：#RRGGBB、#AARRGGBB 或 R,G,B"
                : "色号：#RRGGBB 或 R,G,B");
        colorInput.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText colorValue = new TextInputEditText(context);
        colorValue.setSingleLine(true);
        colorValue.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        // 这个输入框以前没设文字颜色，在深色底上几乎是黑的（issue #79），显式给个亮色。
        colorValue.setTextColor(0xFFF1F5FA);
        colorValue.setText(formatColor(initial));
        // 「应用」按输入框内容取色，而输入框只在创建时写过一次；调色盘选完色再点「应用」就会被这个
        // 旧值覆盖回原色（issue #79）。用一个"用户正在编辑"的标记来决定谁说了算。
        final boolean[] textEdited = {false};
        final boolean[] programmaticText = {false};
        colorValue.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!programmaticText[0]) textEdited[0] = true;
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        colorInput.addView(colorValue);
        inputRow.addView(colorInput, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton applyText = new MaterialButton(context);
        applyText.setText("应用");
        applyText.setTextSize(12f);
        applyText.setAllCaps(false);
        inputRow.addView(applyText, new LinearLayout.LayoutParams(-2, dp(context, 48)));
        controls.addView(inputRow);
        // 不透明度滑杆（issue #148）：0 = 完全透明，255 = 完全不透明；只在支持 alpha 的区域显示。
        LinearLayout alphaRow = new LinearLayout(context);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(0, dp(context, 8), 0, 0);
        alphaRow.addView(text(context, "不透明度", 13, 0xFFD7E1EE, false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView alphaValue = text(context, alphaLabel(alpha[0]), 13, 0xFF6EE7F2, true);
        alphaRow.addView(alphaValue);
        alphaRow.setVisibility(supportsAlpha ? View.VISIBLE : View.GONE);
        controls.addView(alphaRow);
        SeekBar alphaSeek = new SeekBar(context);
        alphaSeek.setMax(255);
        alphaSeek.setProgress(alpha[0]);
        if (Build.VERSION.SDK_INT >= 21) {
            alphaSeek.setProgressTintList(ColorStateList.valueOf(0xFF6EE7F2));
            alphaSeek.setThumbTintList(ColorStateList.valueOf(0xFFFFCA66));
        }
        alphaSeek.setVisibility(supportsAlpha ? View.VISIBLE : View.GONE);
        controls.addView(alphaSeek, new LinearLayout.LayoutParams(-1, dp(context, 38)));
        if (supportsAlpha) {
            TextView alphaHint = text(context,
                    "0 = 完全透明，255 = 完全不透明。", 12, 0xFF8392A8, false);
            alphaHint.setPadding(0, 0, 0, dp(context, 2));
            controls.addView(alphaHint);
        }
        // 纯黑 / 纯白快捷入口（issue #60）：用户想设纯黑时不用记色号。
        LinearLayout quickRow = new LinearLayout(context);
        quickRow.setPadding(0, dp(context, 6), 0, 0);
        MaterialButton pureBlack = new MaterialButton(context);
        pureBlack.setText("纯黑");
        pureBlack.setTextSize(12f);
        pureBlack.setAllCaps(false);
        MaterialButton pureWhite = new MaterialButton(context);
        pureWhite.setText("纯白");
        pureWhite.setTextSize(12f);
        pureWhite.setAllCaps(false);
        quickRow.addView(pureBlack, new LinearLayout.LayoutParams(0, dp(context, 44), 1f));
        LinearLayout.LayoutParams whiteParams = new LinearLayout.LayoutParams(0, dp(context, 44), 1f);
        whiteParams.leftMargin = dp(context, 8);
        quickRow.addView(pureWhite, whiteParams);
        controls.addView(quickRow);
        TextView hint = text(context, "圆形调色盘：沿外圈选择色相，向中心降低饱和度", 12,
                0xFF8392A8, false);
        hint.setPadding(0, dp(context, 8), 0, dp(context, 3));
        controls.addView(hint);
        ColorCirclePickerView circle = new ColorCirclePickerView(context);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(-1, dp(context, 210));
        circleParams.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(circle, circleParams);
        circle.setColor(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
        // 亮度轴（issue #60）：放在圆盘外面，自己的 SeekBar，避免和圆盘 / ScrollView 抢手势。
        LinearLayout brightnessRow = new LinearLayout(context);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        brightnessRow.setPadding(0, dp(context, 8), 0, 0);
        brightnessRow.addView(text(context, "亮度", 13, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView brightnessValue = text(context, "100%", 13, 0xFF6EE7F2, true);
        brightnessRow.addView(brightnessValue);
        controls.addView(brightnessRow);
        android.widget.SeekBar brightnessSeek = new android.widget.SeekBar(context);
        brightnessSeek.setMax(100);
        brightnessSeek.setProgress(Math.round(circle.value() * 100f));
        brightnessValue.setText(Math.round(circle.value() * 100f) + "%");
        if (Build.VERSION.SDK_INT >= 21) {
            brightnessSeek.setProgressTintList(ColorStateList.valueOf(0xFF6EE7F2));
            brightnessSeek.setThumbTintList(ColorStateList.valueOf(0xFFFFCA66));
        }
        controls.addView(brightnessSeek, new LinearLayout.LayoutParams(-1, dp(context, 38)));
        Runnable apply = () -> {
            alpha[0] = storedAlpha(alpha[0]);
            int color = storableColor(alpha[0], rgb[0], rgb[1], rgb[2]);
            store.set(color);
            state.setText(colorLabel(color));
            updateSwatch(context, swatch, color);
            circle.setColor(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            alphaValue.setText(alphaLabel(alpha[0]));
            if (!textEdited[0]) {
                // 输入框跟着当前颜色走：这样"选色后随手点一下应用"不会把颜色覆盖回旧值（issue #79）。
                programmaticText[0] = true;
                colorValue.setText(formatColor(color));
                programmaticText[0] = false;
                colorInput.setError(null);
            }
            changed.run();
        };
        // 用调色盘 / 通道滑杆 / 亮度轴 / 纯黑纯白选色时，输入框以控件为准（清掉"用户正在编辑"标记）。
        final Runnable pickerApply = () -> {
            textEdited[0] = false;
            apply.run();
        };
        applyText.setOnClickListener(v -> {
            String raw = colorValue.getText() == null ? "" : colorValue.getText().toString();
            int parsed = parseColor(raw);
            // #00000000 的解析结果也是 0，和旧版"非法输入"的返回值相同，靠 8 位格式把它区分开。
            boolean transparentBlack = parsed == 0 && supportsAlpha && isValidAlphaColorText(raw);
            if (parsed == 0 && !transparentBlack) {
                colorInput.setError(supportsAlpha
                        ? "请输入 #RRGGBB、#AARRGGBB 或 R,G,B（每项 0-255）"
                        : "请输入 #RRGGBB 或 R,G,B（每项 0-255）");
                return;
            }
            colorInput.setError(null);
            if (transparentBlack) {
                rgb[0] = 0;
                rgb[1] = 0;
                rgb[2] = 0;
                alpha[0] = 0;
            } else {
                rgb[0] = (parsed >> 16) & 0xFF;
                rgb[1] = (parsed >> 8) & 0xFF;
                rgb[2] = parsed & 0xFF;
                alpha[0] = (parsed >>> 24) & 0xFF;
            }
            textEdited[0] = false;
            apply.run();
        });
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                alpha[0] = storedAlpha(progress);
                if (alpha[0] != progress) bar.setProgress(alpha[0]);
                pickerApply.run();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        Channel[] channels = new Channel[]{
                addChannel(context, controls, "红", rgb, 0, pickerApply),
                addChannel(context, controls, "绿", rgb, 1, pickerApply),
                addChannel(context, controls, "蓝", rgb, 2, pickerApply)};
        circle.setListener(color -> {
            rgb[0] = (color >> 16) & 0xFF;
            rgb[1] = (color >> 8) & 0xFF;
            rgb[2] = color & 0xFF;
            for (int index = 0; index < channels.length; index++) {
                channels[index].value.setText(Integer.toString(rgb[index]));
                channels[index].seek.setProgress(rgb[index]);
            }
            pickerApply.run();
        });
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightnessValue.setText(progress + "%");
                if (!fromUser) return;
                circle.setValue(ColorWheelMath.normalizeValue(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        pureBlack.setOnClickListener(v -> setRgb(rgb, 0x000000, circle, pickerApply, brightnessSeek,
                brightnessValue));
        pureWhite.setOnClickListener(v -> setRgb(rgb, 0xFFFFFF, circle, pickerApply, brightnessSeek,
                brightnessValue));
        parent.addView(controls);
        manual.setOnCheckedChangeListener((button, enabled) -> {
            controls.setVisibility(enabled ? View.VISIBLE : View.GONE);
            automatic.setVisibility(enabled ? View.GONE : View.VISIBLE);
            if (enabled) pickerApply.run();
            else {
                store.set(0);
                changed.run();
            }
        });
    }

    /** 纯黑 / 纯白快捷入口（issue #60）：写回 rgb[] 后走同一条 apply 路径，亮度轴同步。 */
    private static void setRgb(int[] rgb, int color, ColorCirclePickerView circle, Runnable apply,
                               SeekBar brightnessSeek, TextView brightnessValue) {
        rgb[0] = (color >> 16) & 0xFF;
        rgb[1] = (color >> 8) & 0xFF;
        rgb[2] = color & 0xFF;
        circle.setColor(0xFF000000 | color);
        int percent = Math.round(ColorWheelMath.valueFromColorComponents(rgb[0], rgb[1], rgb[2])
                * 100f);
        brightnessSeek.setProgress(percent);
        brightnessValue.setText(percent + "%");
        apply.run();
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

    /** 面板状态文字；带透明度时补充百分比，便于用户确认 alpha 生效。 */
    static String colorLabel(int color) {
        String label = "当前：" + formatColor(color);
        int alpha = (color >>> 24) & 0xFF;
        return alpha == 0xFF ? label : label + "（不透明度 " + alphaPercent(alpha) + "%）";
    }

    /** 不透明度标签，例如 {@code 128（50%）}。 */
    static String alphaLabel(int alpha) {
        return alpha + "（" + alphaPercent(alpha) + "%）";
    }

    /**
     * Parses {@code #RRGGBB}, {@code #AARRGGBB} or {@code R,G,B} (each 0-255).
     * Returns zero for malformed input, matching the old behaviour; the only legitimate value
     * that also returns zero is fully transparent black ({@code #00000000}), which callers can
     * tell apart with {@link #isValidAlphaColorText(String)}.
     */
    static int parseColor(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            if (normalized.matches("#?[0-9a-fA-F]{8}")) {
                return (int) Long.parseLong(normalized.startsWith("#")
                        ? normalized.substring(1) : normalized, 16);
            }
            if (normalized.matches("#?[0-9a-fA-F]{6}")) {
                int rgb = (int) Long.parseLong(normalized.startsWith("#")
                        ? normalized.substring(1) : normalized, 16);
                return 0xFF000000 | rgb;
            }
            String[] values = normalized.split("\\s*,\\s*");
            if (values.length != 3) return 0;
            int red = Integer.parseInt(values[0]);
            int green = Integer.parseInt(values[1]);
            int blue = Integer.parseInt(values[2]);
            if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) return 0;
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** Whether the text is shaped like {@code #AARRGGBB}; the alpha digits may be zero. */
    static boolean isValidAlphaColorText(String value) {
        return value != null && value.trim().matches("#?[0-9a-fA-F]{8}");
    }

    /** 不透明时输出 {@code #RRGGBB}，带透明度时输出 {@code #AARRGGBB}。 */
    static String formatColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        if (alpha == 0xFF) {
            return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
        }
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", alpha, red, green, blue);
    }

    /** alpha（0-255）转显示百分比，四舍五入；输入先夹到 0-255。 */
    static int alphaPercent(int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return Math.round(clamped * 100f / 255f);
    }

    /**
     * 滑杆 / 输入框里的 alpha 转实际存储值：{@link AppPreferences} 把 alpha 0 当成旧版无 alpha
     * 色值补成不透明，所以最低存 {@link #MIN_STORED_ALPHA}。
     */
    static int storedAlpha(int alpha) {
        return Math.max(MIN_STORED_ALPHA, Math.min(255, alpha));
    }

    /** 按 ARGB 组装最终色值；alpha 下限见 {@link #MIN_STORED_ALPHA}，RGB 各通道夹到 0-255。 */
    static int storableColor(int alpha, int red, int green, int blue) {
        return (storedAlpha(alpha) << 24)
                | (Math.max(0, Math.min(255, red)) << 16)
                | (Math.max(0, Math.min(255, green)) << 8)
                | Math.max(0, Math.min(255, blue));
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
