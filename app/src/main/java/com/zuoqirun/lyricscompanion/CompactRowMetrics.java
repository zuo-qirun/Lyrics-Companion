package com.zuoqirun.lyricscompanion;

/**
 * 紧凑样式歌词条的纯几何计算（issue #165）。
 *
 * <p>只做「第二行行位 / 当前句字号因子 / 整组高度 / 垂直居中」的计算，不读偏好、不碰
 * Canvas，便于用 JVM 单测锁定一个行为：双行模式打开后，当前句有没有下一句时字号和行位
 * 都一致，最后一句不会突然放大、整块也不会重新居中。
 */
final class CompactRowMetrics {
    /** 双行模式打开后当前句固定使用的字号因子（与改动前有下一句时的观感一致）。 */
    static final float DOUBLE_ROW_SIZE_FACTOR = 0.78f;

    private CompactRowMetrics() { }

    /**
     * 用户是否选中了固定的双行布局（与当前歌词内容无关）。
     *
     * <p>顶部歌词条由自己的「歌词行数」决定：只有「双行」才固定占两行，「单行」「自动」和
     * 状态栏同高保持原来的单行 100% 行为；紧凑样式悬浮窗则看「显示下一句歌词」开关。
     */
    static boolean fixedSecondRow(boolean compactTextOnly, boolean compactShowNextLine,
                                  boolean topStripDoubleRows) {
        return compactTextOnly ? topStripDoubleRows : compactShowNextLine;
    }

    /**
     * 是否给第二行留行位：固定双行时始终为真（没有内容也留一个空白行位），否则翻译或
     * 下一句真的出现时才留。
     */
    static boolean secondRow(boolean fixedSecondRow, boolean showTranslation,
                             boolean showNextLine) {
        return fixedSecondRow || showTranslation || showNextLine;
    }

    /** 当前句字号因子：画第二行时 78%，单行时 100%。 */
    static float lyricSizeFactor(boolean secondRow) {
        return secondRow ? DOUBLE_ROW_SIZE_FACTOR : 1f;
    }

    /** 整组歌词的高度：两行时是两行的 ascent/descent 加上行距，单行时只有当前句。 */
    static float groupHeight(boolean secondRow, float currentAscent, float currentDescent,
                             float lineGap, float secondaryAscent, float secondaryDescent) {
        return secondRow
                ? currentDescent - currentAscent + lineGap + secondaryDescent - secondaryAscent
                : currentDescent - currentAscent;
    }

    /** 整组歌词在歌词区里的顶部：垂直居中，放不下时贴顶。 */
    static float groupTop(float lyricTop, float lyricStageHeight, float groupHeight) {
        return lyricTop + Math.max(0f, (lyricStageHeight - groupHeight) * 0.5f);
    }

    /** 当前句基线：组顶减去当前句 ascent。 */
    static float baseline(float groupTop, float currentAscent) {
        return groupTop - currentAscent;
    }

    /** 第二行基线：当前句基线 + 当前句 descent + 行距 - 第二行 ascent。 */
    static float secondaryBaseline(float baseline, float currentDescent, float lineGap,
                                   float secondaryAscent) {
        return baseline + currentDescent + lineGap - secondaryAscent;
    }
}
