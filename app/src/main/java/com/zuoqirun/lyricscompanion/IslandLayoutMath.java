package com.zuoqirun.lyricscompanion;

/**
 * 「灵动岛」样式的几何（issue #54）。
 *
 * <p>一颗胶囊：左边圆形封面、右边歌名（可选）与单行当前歌词，整体在胶囊里居中。胶囊高度夹在
 * 最小 / 最大之间，角半径取高度一半（真的像胶囊），宽度用面板宽度但内容按可用宽度收。
 */
final class IslandLayoutMath {
    /** 胶囊最小高度（dp）：再矮封面就放不下了。 */
    static final float MIN_HEIGHT_DP = 40f;
    /** 胶囊最大高度（dp）：再高就不像「岛」了。 */
    static final float MAX_HEIGHT_DP = 72f;
    /** 封面边长占胶囊高度的比例（略小于高度，留出内边距）。 */
    static final float COVER_RATIO_OF_HEIGHT = 0.72f;
    /** 封面与文字之间的间距（占胶囊高度的比例）。 */
    static final float COVER_GAP_RATIO = 0.18f;
    /** 胶囊内左右内边距（占胶囊高度的比例）。 */
    static final float SIDE_PADDING_RATIO = 0.14f;
    /** 歌名与歌词都放不下时，至少给歌词留这么多宽度（占可用宽度的比例）。 */
    static final float MIN_LYRIC_WIDTH_RATIO = 0.45f;

    private IslandLayoutMath() { }

    /** 胶囊高度（像素）：面板高度夹在 40–72dp 之间；面板比下限还矮就用面板高度。 */
    static float capsuleHeightPx(float panelHeightPx, float density) {
        return capsuleHeightPx(panelHeightPx, density, MAX_HEIGHT_DP);
    }

    static float capsuleHeightPx(float panelHeightPx, float density, float maximumDp) {
        float unit = Math.max(0.01f, density);
        float minimum = MIN_HEIGHT_DP * unit;
        float maximum = Math.max(MAX_HEIGHT_DP, Math.min(240f, maximumDp)) * unit;
        if (panelHeightPx <= minimum) return Math.max(1f, panelHeightPx);
        return Math.min(maximum, panelHeightPx);
    }

    /** Mutable per-view result; baseline and clips use the actual typeface metrics. */
    static final class Rows {
        float mainSize, secondSize, mainBaseline, secondBaseline;
        float mainTop, mainBottom, secondTop, secondBottom;
    }

    static void packRows(Rows rows, float height, float textScale, float secondScale,
                         int mainPercent, float ascent, float descent) {
        float inset = height * 0.08f;
        float gap = height * 0.04f;
        float available = Math.max(1f, height - 2f * inset - gap);
        float ratio = mainPercent <= 0 ? 1f / (1f + Math.max(0.1f, secondScale))
                : Math.max(35, Math.min(85, mainPercent)) / 100f;
        float metrics = Math.max(0.1f, descent - ascent);
        rows.mainTop = inset;
        rows.mainBottom = inset + available * ratio;
        rows.secondTop = rows.mainBottom + gap;
        rows.secondBottom = height - inset;
        float requested = height * 0.60f * ratio * Math.max(0.1f, textScale);
        rows.mainSize = Math.min(requested, (rows.mainBottom - rows.mainTop) / metrics);
        rows.secondSize = Math.min(requested * Math.max(0.1f, secondScale),
                (rows.secondBottom - rows.secondTop) / metrics);
        rows.mainBaseline = (rows.mainTop + rows.mainBottom - (ascent + descent) * rows.mainSize) / 2f;
        rows.secondBaseline = (rows.secondTop + rows.secondBottom - (ascent + descent) * rows.secondSize) / 2f;
    }

    /** 胶囊角半径：取高度一半，两端就是半圆。 */
    static float capsuleRadiusPx(float capsuleHeightPx) {
        return Math.max(0f, capsuleHeightPx) * 0.5f;
    }

    static float coverSizePx(float capsuleHeightPx) {
        return Math.max(1f, capsuleHeightPx * COVER_RATIO_OF_HEIGHT);
    }

    static float sidePaddingPx(float capsuleHeightPx) {
        return Math.max(0f, capsuleHeightPx * SIDE_PADDING_RATIO);
    }

    static float coverGapPx(float capsuleHeightPx) {
        return Math.max(0f, capsuleHeightPx * COVER_GAP_RATIO);
    }

    static boolean showsCover(boolean enabled, boolean artMissing, boolean hideWithoutArt) {
        return enabled && !(artMissing && hideWithoutArt);
    }

    static float textLeftPx(float capsuleHeightPx, boolean showCover) {
        return sidePaddingPx(capsuleHeightPx) + (showCover
                ? coverSizePx(capsuleHeightPx) + coverGapPx(capsuleHeightPx) : 0f);
    }

    /** Reserve cover and gap only when drawn; both modes retain the same right padding. */
    static float textWidthPx(float capsuleWidthPx, float capsuleHeightPx, boolean showCover) {
        float available = capsuleWidthPx - textLeftPx(capsuleHeightPx, showCover)
                - sidePaddingPx(capsuleHeightPx);
        return Math.max(1f, available);
    }

    /**
     * 是否显示歌名行：胶囊够高（≥ 56dp）且文字区不至于太窄时才分两行，否则只显示歌词
     * （矮胶囊里两行会挤成一团）。
     */
    static boolean showsTitleRow(float capsuleHeightPx, float textWidthPx, float density) {
        float unit = Math.max(0.01f, density);
        if (capsuleHeightPx < 56f * unit) return false;
        return textWidthPx >= capsuleHeightPx * 2.2f;
    }

    /** 歌名字号：胶囊高度的 26%，但不超过歌词字号（歌词是主角）。 */
    static float titleSizePx(float capsuleHeightPx, float lyricSizePx) {
        return Math.min(Math.max(1f, capsuleHeightPx * 0.26f), Math.max(1f, lyricSizePx));
    }

    /** 当前歌词字号：胶囊高度的 34%。 */
    static float lyricSizePx(float capsuleHeightPx) {
        return Math.max(1f, capsuleHeightPx * 0.34f);
    }
}
