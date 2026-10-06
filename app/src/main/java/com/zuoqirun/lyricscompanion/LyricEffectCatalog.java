package com.zuoqirun.lyricscompanion;

/** One catalogue supplies stored values, settings labels, validation and scheduling capabilities. */
final class LyricEffectCatalog {
    enum Effect {
        NONE("none", "无特效（默认）", false),
        KARAOKE("karaoke", "卡拉 OK 逐字点亮", true),
        NEON("neon", "霓虹辉光呼吸", true),
        MIRROR("mirror", "镜像倒影", false),
        BOUNCE("bounce", "弹性弹跳", true),
        FLOAT("float", "仙气漂浮", true),
        SWEEP("sweep", "流光扫线", true),
        RIPPLE("ripple", "律动文字波纹", true),
        FROST("frost", "磨砂悬浮底衬", false),
        RAINBOW("rainbow", "彩虹流光", true),
        GLITCH("glitch", "故障抖动", true),
        SPARKLE("sparkle", "星光闪烁", true),
        FLAME("flame", "火焰燃烧", true),
        ORBIT("orbit", "星辰悬空（模拟 3D）", true),
        GRADIENT("gradient", "流光渐变歌词", true),
        CINEMA("cinema", "柔滚上移", true);

        final String value;
        final String label;
        final boolean animated;

        Effect(String value, String label, boolean animated) {
            this.value = value;
            this.label = label;
            this.animated = animated;
        }
    }

    private static final Effect[] ALL = Effect.values();

    private LyricEffectCatalog() { }

    static Effect find(String value) {
        for (Effect effect : ALL) {
            if (effect.value.equals(value)) return effect;
        }
        return Effect.NONE;
    }

    static String[] labels() { return choices(true); }
    static String[] values() { return choices(false); }

    private static String[] choices(boolean labels) {
        String[] choices = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            choices[i] = labels ? ALL[i].label : ALL[i].value;
        }
        return choices;
    }
}
