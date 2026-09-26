package com.zuoqirun.lyricscompanion;

/**
 * 长句显示方式（issue #47）。
 *
 * <p>超过可用宽度的歌词原来只有一种处理：横向跑马灯（紧凑 / 纯净共用）。这里增加两个可选档——
 * 「缩小字号」与「换行」，默认仍是跑马灯。反馈者原话是「跑马灯别取消啊」，所以默认值保持不动。
 *
 * <p>换行的代价要知道：它会把当前句排成 2–3 行，本来只有 1 行行位的地方会被挤到（尤其紧凑 / 纯净），
 * 所以这里按可用高度算最多几行，并且只把块居中在原行位置上，不改动调用方的版面预算。
 */
final class LongLineLayout {
    static final String MODE_MARQUEE = "marquee";
    static final String MODE_SHRINK = "shrink";
    static final String MODE_WRAP = "wrap";
    /** 缩小字号的下限：与 {@code fitSize} 一致，再小就不如换行了。 */
    static final float SHRINK_FLOOR_RATIO = 0.62f;
    /** 换行最多几行。 */
    static final int MAX_WRAP_LINES = 3;

    private LongLineLayout() { }

    static String normalizeMode(String mode) {
        if (MODE_SHRINK.equals(mode)) return MODE_SHRINK;
        if (MODE_WRAP.equals(mode)) return MODE_WRAP;
        return MODE_MARQUEE;
    }

    /**
     * 实际生效的档位：顶部歌词条是固定高度的双行条，换行 / 缩小都会把它撑坏，所以永远跑马灯
     * （issue #47 的建议，也让这一条的行为与改动前一致）。
     */
    static String resolveMode(String stored, boolean topStrip) {
        return topStrip ? MODE_MARQUEE : normalizeMode(stored);
    }

    /**
     * 「缩小字号」档的目标字号：宽度超出就按比例缩小，最低 {link #SHRINK_FLOOR_RATIO}。
     * 宽度足够时不放大。
     */
    static float shrinkSize(float requestedSize, float textWidth, float maxWidth) {
        if (requestedSize <= 0f || maxWidth <= 0f || textWidth <= maxWidth) return requestedSize;
        float ratio = Math.max(SHRINK_FLOOR_RATIO, maxWidth / Math.max(1f, textWidth));
        return requestedSize * Math.min(1f, ratio);
    }

    /** 「换行」档最多排几行：由可用高度与行高决定，至少 1 行、至多 {@link #MAX_WRAP_LINES}。 */
    static int wrapMaxLines(float availableHeight, float lineHeight, int maxLines) {
        int cap = Math.max(1, Math.min(MAX_WRAP_LINES, maxLines));
        if (lineHeight <= 0f || availableHeight <= 0f) return 1;
        int byHeight = (int) Math.floor(availableHeight / lineHeight);
        return Math.max(1, Math.min(cap, byHeight));
    }
}
