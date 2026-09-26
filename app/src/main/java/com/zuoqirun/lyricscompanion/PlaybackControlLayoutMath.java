package com.zuoqirun.lyricscompanion;

/**
 * 播放控制按钮的横向排布（issue #72）。
 *
 * <p>以前三个按钮写死在 {@code centerX - spacing / centerX / centerX + spacing}，紧凑样式的底板也永远
 * 按三键宽度画：只开一两个按钮时底板留着空格，按钮组还偏在底图左边。这里把「按实际显示的按钮数对称
 * 排布」和「底板包住实际按钮」抽成纯函数——三个按钮时的取值与改动前逐像素一致，所以老安装观感不变。
 */
final class PlaybackControlLayoutMath {
    /** 底板在按钮外缘之外再留出的横向余量（单位 = 按钮半径）；0.65 复现三键时的原观感。 */
    static final float BACKDROP_PADDING_RATIO = 0.65f;

    private PlaybackControlLayoutMath() { }

    /** 这一屏实际画几个按钮。 */
    static int visibleCount(boolean previous, boolean playPause, boolean next) {
        return (previous ? 1 : 0) + (playPause ? 1 : 0) + (next ? 1 : 0);
    }

    /**
     * 第 {@code slot} 个可见按钮相对中心的偏移。n 个按钮按 1 倍间距均匀排开并整体居中：
     * 1 个 = 0、2 个 = ∓0.5、3 个 = -1 / 0 / +1（单位 = {@code spacing}）。
     *
     * <p>两个按钮之间仍是整整一个 {@code spacing}，所以按钮之间不会比三键时更挤。
     */
    static float slotOffset(int slot, int count, float spacing) {
        if (count <= 1) return 0f;
        int clamped = Math.max(0, Math.min(count - 1, slot));
        return (clamped - (count - 1) * 0.5f) * spacing;
    }

    /**
     * 底板的半宽：取最外侧按钮「圆心偏移 + 自身半径」，再各留 {@link #BACKDROP_PADDING_RATIO} 个半径。
     *
     * <p>三键时最外侧按钮的圆心偏移就是 spacing、半径就是 radius，于是结果等于改动前写死的
     * {@code spacing + radius * 1.65}。
     */
    static float backdropHalfWidth(float maxButtonExtent, float radius) {
        return Math.max(0f, maxButtonExtent) + Math.max(0f, radius) * BACKDROP_PADDING_RATIO;
    }

    /** 基准位置 + 偏移百分比 → 实际中心（偏移按面板尺寸的百分比算，issue #73）。 */
    static float resolvedCenter(float baseCenter, float panelExtent, float offsetPercent) {
        return baseCenter + panelExtent * offsetPercent / 100f;
    }

    /**
     * 把按钮组中心夹在面板里：两侧各留 {@code halfExtent}（这一组按钮实际占的半宽/半高）。
     *
     * <p>面板比两组按钮还窄时（小面板 + 大按钮）退化为居中，而不是像以前那样把按钮钉到左缘。
     */
    static float clampCenter(float center, float panelExtent, float halfExtent) {
        float half = Math.max(0f, halfExtent);
        float extent = Math.max(0f, panelExtent);
        if (extent <= half * 2f) return extent * 0.5f;
        return Math.max(half, Math.min(extent - half, center));
    }
}
