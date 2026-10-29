package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
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

    /**
     * 颜色行：标题 + 「手动调色盘」开关 + 自动说明；打开调色盘后是色值预览、色号输入、不透明度、
     * 纯黑/纯白快捷入口、圆形调色盘、亮度轴与 RGB 通道。控件全部来自 M3 主题与
     * {@code item_setting_*} 模板，颜色不再写死旧色板。
     */
    static void add(Context context, LinearLayout parent, String title, String automaticNote,
                    int savedColor, int automaticPreviewColor, boolean supportsAlpha,
                    ColorStore store, Runnable changed) {
        SettingsUi ui = new SettingsUi(context);
        ui.sectionLabel(parent, title);
        int initial = savedColor == 0 ? automaticPreviewColor : savedColor;
        if (!supportsAlpha) initial |= 0xFF000000;
        int initialAlpha = (initial >>> 24) & 0xFF;
        final int[] alpha = {initialAlpha == 0 ? 0xFF : initialAlpha};
        final int[] rgb = {(initial >> 16) & 0xFF, (initial >> 8) & 0xFF, initial & 0xFF};
        final Runnable[] pickerApply = new Runnable[1];

        final MaterialSwitch manual = ui.addToggle(parent, "手动调色盘", null, savedColor != 0, null);
        final TextView automatic = note(context);
        automatic.setText(automaticNote);
        parent.addView(automatic);
        automatic.setVisibility(savedColor == 0 ? View.VISIBLE : View.GONE);

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setVisibility(savedColor == 0 ? View.GONE : View.VISIBLE);
        final LinearLayout palette = controls;

        LinearLayout stateRow = new LinearLayout(context);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView state = note(context);
        stateRow.addView(state, new LinearLayout.LayoutParams(0, -2, 1f));
        final View swatch = new View(context);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 22));
        swatchParams.leftMargin = dp(context, 10);
        stateRow.addView(swatch, swatchParams);
        state.setText(colorLabel(initial));
        updateSwatch(context, swatch, initial);
        controls.addView(stateRow);

        LinearLayout inputRow = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.item_setting_color_input, null, false);
        final TextInputLayout colorInput = inputRow.findViewById(R.id.settings_color_input);
        final TextInputEditText colorValue = inputRow.findViewById(R.id.settings_color_value);
        MaterialButton applyText = inputRow.findViewById(R.id.settings_color_apply);
        colorInput.setHint(supportsAlpha ? "色号：#RRGGBB、#AARRGGBB 或 R,G,B"
                : "色号：#RRGGBB 或 R,G,B");
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
        controls.addView(inputRow);

        SeekBar alphaBar = null;
        TextView alphaValue = null;
        if (supportsAlpha) {
            // 不透明度滑杆（issue #148）：0 = 完全透明，255 = 完全不透明。
            alphaBar = ui.addSeek(controls, "不透明度", 255, alpha[0], null, null);
            alphaValue = ((View) alphaBar.getParent()).findViewById(R.id.settings_row_value);
            alphaValue.setText(alphaLabel(alpha[0]));
            ui.note(controls, "0 = 完全透明，255 = 完全不透明。");
        }
        final SeekBar alphaSeek = alphaBar;
        final TextView alphaLabelView = alphaValue;

        // 纯黑 / 纯白快捷入口（issue #60）：用户想设纯黑时不用记色号。
        LinearLayout quickRow = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.item_setting_color_quick, null, false);
        MaterialButton pureBlack = quickRow.findViewById(R.id.settings_color_black);
        MaterialButton pureWhite = quickRow.findViewById(R.id.settings_color_white);
        controls.addView(quickRow);

        ui.note(controls, "圆形调色盘：沿外圈选择色相，向中心降低饱和度");
        final ColorCirclePickerView circle = new ColorCirclePickerView(context);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(-1, dp(context, 210));
        circleParams.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(circle, circleParams);
        circle.setColor(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
        // 亮度轴（issue #60）：放在圆盘外面，自己的 SeekBar，避免和圆盘 / ScrollView 抢手势。
        final int brightness = Math.round(circle.value() * 100f);
        SeekBar brightnessSeek = ui.addSeek(controls, "亮度", 100, brightness, "%",
                value -> circle.setValue(ColorWheelMath.normalizeValue(value)));
        SeekBar redSeek = ui.addSeek(controls, "红", 255, rgb[0], null, value -> {
            rgb[0] = value;
            pickerApply[0].run();
        });
        SeekBar greenSeek = ui.addSeek(controls, "绿", 255, rgb[1], null, value -> {
            rgb[1] = value;
            pickerApply[0].run();
        });
        SeekBar blueSeek = ui.addSeek(controls, "蓝", 255, rgb[2], null, value -> {
            rgb[2] = value;
            pickerApply[0].run();
        });
        final SeekBar[] channelSeeks = {redSeek, greenSeek, blueSeek};
        parent.addView(controls);

        Runnable apply = () -> {
            alpha[0] = storedAlpha(alpha[0]);
            int color = storableColor(alpha[0], rgb[0], rgb[1], rgb[2]);
            store.set(color);
            state.setText(colorLabel(color));
            updateSwatch(context, swatch, color);
            circle.setColor(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            if (alphaLabelView != null) alphaLabelView.setText(alphaLabel(alpha[0]));
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
        pickerApply[0] = () -> {
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
        if (alphaSeek != null) {
            alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (alphaLabelView != null) alphaLabelView.setText(alphaLabel(progress));
                    if (!fromUser) return;
                    alpha[0] = storedAlpha(progress);
                    if (alpha[0] != progress) bar.setProgress(alpha[0]);
                    pickerApply[0].run();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
        }
        circle.setListener(color -> {
            rgb[0] = (color >> 16) & 0xFF;
            rgb[1] = (color >> 8) & 0xFF;
            rgb[2] = color & 0xFF;
            for (int index = 0; index < channelSeeks.length; index++) {
                channelSeeks[index].setProgress(rgb[index]);
            }
            pickerApply[0].run();
        });
        pureBlack.setOnClickListener(v -> setRgb(rgb, 0x000000, circle, brightnessSeek, pickerApply[0]));
        pureWhite.setOnClickListener(v -> setRgb(rgb, 0xFFFFFF, circle, brightnessSeek, pickerApply[0]));
        manual.setOnCheckedChangeListener((button, enabled) -> {
            palette.setVisibility(enabled ? View.VISIBLE : View.GONE);
            automatic.setVisibility(enabled ? View.GONE : View.VISIBLE);
            if (enabled) pickerApply[0].run();
            else {
                store.set(0);
                changed.run();
            }
        });
    }

    /** 纯黑 / 纯白快捷入口（issue #60）：写回 rgb[] 后走同一条 apply 路径，亮度轴同步。 */
    private static void setRgb(int[] rgb, int color, ColorCirclePickerView circle, SeekBar brightnessSeek,
                               Runnable apply) {
        rgb[0] = (color >> 16) & 0xFF;
        rgb[1] = (color >> 8) & 0xFF;
        rgb[2] = color & 0xFF;
        circle.setColor(0xFF000000 | color);
        int percent = Math.round(ColorWheelMath.valueFromColorComponents(rgb[0], rgb[1], rgb[2])
                * 100f);
        brightnessSeek.setProgress(percent);
        apply.run();
    }

    private static void updateSwatch(Context context, View view, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, 8));
        shape.setColor(color);
        shape.setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.lc_outline));
        view.setBackground(shape);
    }

    private static TextView note(Context context) {
        return (TextView) LayoutInflater.from(context).inflate(R.layout.item_setting_note, null, false);
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
}
