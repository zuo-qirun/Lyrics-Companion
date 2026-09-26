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
        float unit = Math.max(0.01f, density);
        float minimum = MIN_HEIGHT_DP * unit;
        float maximum = MAX_HEIGHT_DP * unit;
        if (panelHeightPx <= minimum) return Math.max(1f, panelHeightPx);
        return Math.min(maximum, panelHeightPx);
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

    /** 文字区可用宽度：胶囊宽度减去两侧内边距、封面与间距。 */
    static float textWidthPx(float capsuleWidthPx, float capsuleHeightPx) {
        float available = capsuleWidthPx - sidePaddingPx(capsuleHeightPx) * 2f
                - coverSizePx(capsuleHeightPx) - coverGapPx(capsuleHeightPx);
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
