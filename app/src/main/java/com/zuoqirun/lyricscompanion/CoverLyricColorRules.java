package com.zuoqirun.lyricscompanion;

/** Cover hue with bounded saturation and contrast against the panel's base color. */
final class CoverLyricColorRules {
    private CoverLyricColorRules() { }

    static int color(int cover, int background, boolean current) {
        // Preserve hue; neutral covers remain neutral. Inactive text has a softer tint.
        int neutral = luminance(cover) > 0.45 ? 0xFFFFFFFF : 0xFF808080;
        int candidate = blend(cover, neutral, current ? 0.16 : 0.42);
        if (contrast(candidate, background) >= 4.5) return candidate;
        int target = contrast(0xFFFFFFFF, background) >= contrast(0xFF000000, background)
                ? 0xFFFFFFFF : 0xFF000000;
        for (int step = 1; step <= 20; step++) {
            int adjusted = blend(candidate, target, step / 20.0);
            if (contrast(adjusted, background) >= 4.5) return adjusted;
        }
        return target;
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 255) + 0.7152 * linear((color >> 8) & 255)
                + 0.0722 * linear(color & 255);
    }

    private static double linear(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int blend(int a, int b, double amount) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int blue = (int) Math.round((a & 255) * (1 - amount) + (b & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }
}
