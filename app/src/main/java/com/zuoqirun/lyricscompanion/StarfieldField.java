package com.zuoqirun.lyricscompanion;

/**
 * Deterministic star field for the 「星空」 background (issue #59).
 *
 * <p>The layout is a pure function of the star index and one seed, so the same field comes back
 * identically on every frame without keeping any state: no allocation per frame, nothing to keep in
 * sync with the renderer, and a settings preview and the real overlay show the same sky. Only the
 * twinkle phase and the slow drift follow the clock, and both are pure functions of elapsed time.
 */
final class StarfieldField {
    /** 星点上限：一屏最多画这么多颗，密度 100% 时大约是这个数的六成，够满又不会糊成一片。 */
    static final int MAX_STARS = 180;
    /** 密度百分比换算成星点数。 */
    static final float BASE_DENSITY_RATIO = 0.6f;
    /** 星空帧率（fps）：5 最省电，60 最流畅（默认，与屏幕刷新对齐）。 */
    static final int MIN_FPS = 5;
    static final int MAX_FPS = 60;
    static final int DEFAULT_FPS = 60;

    private StarfieldField() { }

    /**
     * 用户设定的星空帧率对应的一帧毫秒数。
     *
     * <p>60 fps 得到 17 ms（1000/60 向上取整到毫秒），也就是一个刷新周期：面板把「≤17 ms」当作
     * 「跟屏幕刷新对齐」，改走 {@code postInvalidateOnAnimation} 而不是固定延时。把帧率调低就是省电档
     * 之外的另一种省电方式——星点一跳一跳地走，但不再每帧重绘整片星空。
     */
    static long frameDelayMs(int fps) {
        int safe = Math.max(MIN_FPS, Math.min(MAX_FPS, fps));
        return Math.max(16L, Math.round(1_000f / safe));
    }

    /** 该密度下要画多少颗星（issue #59 的「密度」参数）。 */
    static int starCount(int densityPercent) {
        int count = Math.round(MAX_STARS * BASE_DENSITY_RATIO * Math.max(0, densityPercent) / 100f);
        return Math.max(4, Math.min(MAX_STARS, count));
    }

    /** 第 {@code index} 颗星的位置，单位是面板宽 / 高的比例，取值 [0, 1)。 */
    static float starX(int index, int seed) {
        return unitHash(index, seed, 1);
    }

    static float starY(int index, int seed) {
        return unitHash(index, seed, 2);
    }

    /** 星点半径系数（0.35 ~ 1.5），再乘「星点大小」与密度单位。 */
    static float starRadius(int index, int seed) {
        return 0.35f + unitHash(index, seed, 3) * 1.15f;
    }

    /** 每颗星自己的闪烁周期（毫秒），1800 ~ 5200，避免整片一起亮灭。 */
    static float twinklePeriodMs(int index, int seed) {
        return 1_800f + unitHash(index, seed, 4) * 3_400f;
    }

    /** 闪烁相位偏移（0 ~ 1），让星星错开。 */
    static float twinklePhase(int index, int seed) {
        return unitHash(index, seed, 5);
    }

    /**
     * 闪烁亮度系数：0.30 ~ 1。「省电档」（{@code still}）返回固定值，星点静止不闪。
     */
    static float twinkle(int index, int seed, long elapsedMs, boolean still) {
        if (still) return 0.72f;
        float period = twinklePeriodMs(index, seed);
        float phase = (elapsedMs % (long) period) / period + twinklePhase(index, seed);
        double wave = 0.5d + 0.5d * Math.sin(phase * Math.PI * 2d);
        return (float) (0.30d + 0.70d * wave);
    }

    /**
     * 整片星空的漂移，单位同样是面板宽 / 高的比例，取值 [0, 1)（超出即对 1 取模，星星从另一侧回来）。
     *
     * <p>100% 速度时横向走完一屏约 2 分钟、纵向约 5 分钟；「省电档」不漂移。
     */
    static float driftX(long elapsedMs, int speedPercent, boolean still) {
        return drift(elapsedMs, speedPercent, still, 120_000L);
    }

    static float driftY(long elapsedMs, int speedPercent, boolean still) {
        return drift(elapsedMs, speedPercent, still, 300_000L);
    }

    /**
     * 漂移比例，取值 [0, 1)。
     *
     * <p>钟用 {@code elapsedRealtime}（开机以来的毫秒数）：它单调、不随用户改时间或 NTP 对时而跳，
     * 而且包含休眠时间——悬浮窗、设置页预览、副屏三处看到的是同一片星空，视图重建也不会让星星瞬移。
     * 但它是十亿量级的大数，**不能先转 float 再除**：那样只剩约 2ms 精度，取小数后一次跳屏宽的 0.2%
     * （1080p 上 2~3 像素），表现就是「停十几帧才跳一下」的位移，开机越久越明显、与帧率无关。
     * 所以先用 long 取余拿到「这一轮走到哪了」（精确），再换算成比例——float 只碰 0..1 的小数，精度
     * 约 1e-7 屏宽，足够连续。流体 / 动态渐变底色一直用的也是这个写法（issue #59）。
     */
    private static float drift(long elapsedMs, int speedPercent, boolean still, long periodMs) {
        if (still || speedPercent <= 0) return 0f;
        float phase = (elapsedMs % periodMs) / (float) periodMs;
        return wrap(phase * (speedPercent / 100f));
    }

    /** 位置加上漂移后的实际比例坐标，落在 [0, 1)。 */
    static float positioned(float unitValue, float drift) {
        return wrap(unitValue + drift);
    }

    private static float wrap(double value) {
        double wrapped = value - Math.floor(value);
        return (float) (wrapped < 0d ? wrapped + 1d : wrapped);
    }

    /** 32 位整数哈希：同一 (index, seed, salt) 永远得到同一个数。 */
    private static float unitHash(int index, int seed, int salt) {
        int value = index * 0x9E3779B9 ^ seed * 0x85EBCA6B ^ salt * 0xC2B2AE35;
        value ^= value >>> 15;
        value *= 0x2545F491;
        value ^= value >>> 13;
        value *= 0x9E3779B1;
        value ^= value >>> 16;
        return (value >>> 8) / (float) (1 << 24);
    }
}
