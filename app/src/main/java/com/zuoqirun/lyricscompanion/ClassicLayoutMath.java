package com.zuoqirun.lyricscompanion;

/**
 * Pure layout calculations for the classic lyrics card.
 *
 * <p>Since issue #53 the classic style no longer stretches single rows to the panel edges: every
 * row is packed into one block with a shared baseline gap, the block is then placed inside the
 * usable area and the text shrinks only when the block genuinely does not fit. That keeps the
 * 歌名—歌词 gap and the 歌词—歌词 gap in the same rhythm however tall the panel is, and it is what
 * lets the card show more than three lyric rows (issue #64).
 */
final class ClassicLayoutMath {
    /** Below this the card is unreadable anyway, so fitting never goes further. */
    static final float MIN_TEXT_SCALE = 0.45f;
    /** The previous-line size the style used to hard-code: 12dp against the current 22dp. */
    static final float LEGACY_PREVIOUS_SCALE = 12f / 22f;
    /** Approximate Android font metrics: ascent ≈ 82% of the text size, descent ≈ 25%. */
    static final float ASCENT_RATIO = 0.82f;
    static final float DESCENT_RATIO = 0.25f;
    /** 相邻两个歌词行之间至少留这么多基线距离（dp，未乘密度与内容缩放）。 */
    static final float MIN_LYRIC_GAP_DP = 24f;
    /** 歌名到相邻行的历史下限（dp）：歌名字号大时它跟着长，见 {@link #stackedGapDp}。 */
    static final float MIN_TITLE_GAP_DP = 27f;
    /** 本句与翻译之间单独排：翻译跟着本句走，不参与统一行距（否则多行时会离得很远）。 */
    static final float MIN_TRANSLATION_GAP_DP = 22f;
    /** 行间距设置（issue #138）：80–200%，100% = 原有排版。 */
    static final int MIN_LINE_SPACING_PERCENT = 80;
    static final int MAX_LINE_SPACING_PERCENT = 200;

    private ClassicLayoutMath() { }

    static float rowBaseline(float blockTop, float baseline, boolean metadata,
                             float previewShift, float entryShift) {
        return blockTop + baseline + (metadata ? 0f : previewShift + entryShift);
    }

    static float titleSizePx(float density, float densityUnit, float titleScale,
                             boolean keepRequestedSize) {
        return 15f * (keepRequestedSize ? density : densityUnit) * titleScale;
    }

    static float contentScale(float width, float height, float density) {
        float safeDensity = Math.max(0.01f, density);
        float referenceArea = 390f * 226f * safeDensity * safeDensity;
        float areaScale = (float) Math.sqrt(Math.max(0.01f,
                width * height / referenceArea));
        float heightScale = Math.max(0.01f, height / (226f * safeDensity));
        return Math.min(areaScale, heightScale);
    }

    /**
     * The packed geometry of one classic card frame, in pixels.
     *
     * <p>{@code baselinesPx[i]} is row {@code i}'s baseline measured from the top of the block, so
     * the caller only has to decide where the block itself goes. The object is reused between
     * frames: {@link #pack} rewrites it in place.
     */
    static final class Block {
        float[] baselinesPx;
        /** 主行之间实际用到的统一基线行距（px）；测试与调试用。 */
        float uniformGapPx;
        float scale = 1f;
        float heightPx;
        /** 本帧实际生效的行间距百分比（80–200）；100 = 原有排版（issue #138）。 */
        int lineSpacingPercent = 100;
        /** 请求的行间距因面板高度不够被下调过；下调的是行距，字号没有跟着缩。 */
        boolean lineSpacingLimited;

        Block(int capacity) {
            baselinesPx = new float[Math.max(1, capacity)];
        }

        void ensureCapacity(int capacity) {
            if (baselinesPx.length < capacity) baselinesPx = new float[capacity];
        }
    }

    /** 行数设置：经典样式现在最多摆 7 行歌词（上一句 ×3 + 本句 + 下一句 ×3，issue #64）。 */
    static int visibleRowCount(int requested) {
        return Math.max(1, Math.min(7, requested));
    }

    /**
     * 当前句在可见行窗口里的行号（issue #155）：顶部=0、第二行=1、居中=中间行。
     *
     * <p>行数为 1 时三种取值都退化成 0，也就是「当前句行位」对单行没有影响。
     */
    static int anchorRow(int rows, String anchor) {
        int visible = visibleRowCount(rows);
        if ("top".equals(anchor)) return 0;
        if ("second".equals(anchor)) return Math.min(1, visible - 1);
        return (visible - 1) / 2;
    }

    /**
     * 本句之后画几句，与 {@link #precedingRowCount(int, String)} 配对。
     *
     * <p>本句放顶部时后面全排下一句；放第二行时上面留一句上一句；居中时两侧对称，
     * 和旧行为一致（默认空字符串）。总行数不够时由 {@link #anchorRow} 收窄，不会越界。
     */
    static int followingRowCount(int rows, String anchor) {
        return Math.max(0, visibleRowCount(rows) - 1 - anchorRow(rows, anchor));
    }

    /** 本句之前画几句，取决于「当前句行位」（issue #155）。 */
    static int precedingRowCount(int rows, String anchor) {
        return anchorRow(rows, anchor);
    }

    static int followingRowCount(int rows) {
        return followingRowCount(rows, "");
    }

    static int precedingRowCount(int rows) {
        return precedingRowCount(rows, "");
    }

    /** 行间距百分比归一化到 80–200，默认 100（issue #138）。 */
    static int normalizeLineSpacing(int percent) {
        return Math.max(MIN_LINE_SPACING_PERCENT, Math.min(MAX_LINE_SPACING_PERCENT, percent));
    }

    /** 行间距百分比换算成行距倍数，1 = 原有间距。 */
    static float lineSpacingFactor(int percent) {
        return normalizeLineSpacing(percent) / 100f;
    }

    /**
     * 竖排布局里封面的边长上限（issue #134）。
     *
     * <p>封面、歌名与歌词共用一条高度预算：封面最多长到 {@code availablePx} 减去歌名等信息
     * 和歌词保底空间之后剩下的高度。{@code 封面大小} 调得再大也不会把歌词挤没。
     */
    static float stackedCoverSize(float requestedPx, float availablePx, float reservedPx,
                                  float minLyricPx) {
        float room = Math.max(1f, availablePx) - Math.max(0f, reservedPx)
                - Math.max(0f, minLyricPx);
        return Math.min(Math.max(1f, requestedPx), Math.max(1f, room));
    }

    /**
     * Baseline gap that keeps two stacked rows apart.
     *
     * <p>The minimum keeps the historical spacing for the sizes the style was designed around;
     * past that the gap grows with the rows it separates, so raising 歌名与歌手字号 moves the
     * rows below it down instead of squeezing every other line (issue #38).
     */
    static float stackedGapDp(float upperSizeDp, float lowerSizeDp, float minimumDp) {
        return Math.max(minimumDp,
                DESCENT_RATIO * upperSizeDp + ASCENT_RATIO * lowerSizeDp + 2f);
    }

    /** Ascent of a row drawn at {@code sizePx}, as a positive number of pixels. */
    static float ascent(float sizePx) {
        return ASCENT_RATIO * sizePx;
    }

    /** Descent of a row drawn at {@code sizePx}. */
    static float descent(float sizePx) {
        return DESCENT_RATIO * sizePx;
    }

    /**
     * How far a block of rows has to move so it sits at the requested vertical alignment.
     *
     * <p>{@code blockTop} is the top of the block (the first row's ascent above its baseline) and
     * {@code blockHeight} its full extent. {@code ""} means "the style decides"; "top" / "center"
     * / "bottom" place the block inside {@code [areaTop, areaBottom]}, which is how a panel dragged
     * to the screen edge loses the strip of empty space above its text (issue #41).
     */
    static float alignedRowShift(String align, float blockTop, float blockHeight,
                                 float areaTop, float areaBottom) {
        float room = Math.max(0f, areaBottom - areaTop);
        float slack = Math.max(0f, room - Math.max(0f, blockHeight));
        if ("top".equals(align)) return areaTop - blockTop;
        if ("bottom".equals(align)) return areaTop + slack - blockTop;
        if ("center".equals(align)) return areaTop + slack * 0.5f - blockTop;
        return 0f;
    }

    /**
     * Packs {@code count} rows into one block: one uniform baseline gap for the main rows, the
     * rows' own required gap for the ones that hang off another row, and a text scale that shrinks
     * the rows only as far as {@code availableHeightPx} demands.
     *
     * <p>{@code sizesDp[i]} is the row's text size in dp, {@code scales[i]} whether 字号 applies to
     * it (the song title follows 歌名与歌手字号 instead), {@code minimumGapsDp[i]} the historical
     * lower bound for the gap above it, and {@code uniform[i]} whether it shares the uniform gap.
     */
    static void pack(Block out, int count, float[] sizesDp, boolean[] scales,
                     float[] minimumGapsDp, boolean[] uniform, float densityUnit,
                     float requestedScale, float availableHeightPx) {
        pack(out, count, sizesDp, scales, minimumGapsDp, uniform, densityUnit,
                requestedScale, availableHeightPx, 100, null);
    }

    /**
     * 带「行间距」百分比的重载（issue #138）。
     *
     * <p>字号仍然只按面板高度收缩：缩放比例先用 100% 行距算出来，行间距随后只作用在
     * {@code spacingRows[i]} 为 true 的行距上（相邻两行都是歌词时才缩放，歌名、翻译保持
     * 原行距）。调大后装不下时先把行间距收回到能装下的最大值，文字不因此缩小；
     * {@link Block#lineSpacingPercent} / {@link Block#lineSpacingLimited} 记录实际生效值。
     *
     * @param spacingPercent 80–200，100 = 原有排版
     * @param spacingRows    某行行距是否跟随行间距缩放；null 表示都不缩放
     */
    static void pack(Block out, int count, float[] sizesDp, boolean[] scales,
                     float[] minimumGapsDp, boolean[] uniform, float densityUnit,
                     float requestedScale, float availableHeightPx, int spacingPercent,
                     boolean[] spacingRows) {
        float safeUnit = Math.max(0.01f, densityUnit);
        float available = Math.max(1f, availableHeightPx);
        float scale = Math.max(MIN_TEXT_SCALE, Math.min(4f, requestedScale));
        int spacing = normalizeLineSpacing(spacingPercent);
        out.ensureCapacity(count);
        out.lineSpacingPercent = spacing;
        out.lineSpacingLimited = false;
        if (count <= 0) {
            out.scale = scale;
            out.heightPx = 0f;
            out.uniformGapPx = 0f;
            return;
        }
        // 装不下就缩字。高度对字号是单调不减的（行距也只跟着减小到自己的下限），所以直接二分：
        // 比例收缩会被「行距下限不随字号缩小」卡住，二分一次就能收到真正的可行解。
        if (heightAt(scale, count, sizesDp, scales, minimumGapsDp, uniform, safeUnit)
                > available && scale > MIN_TEXT_SCALE) {
            float low = MIN_TEXT_SCALE;
            float high = scale;
            for (int iteration = 0; iteration < 16; iteration++) {
                float middle = (low + high) * 0.5f;
                if (heightAt(middle, count, sizesDp, scales, minimumGapsDp, uniform, safeUnit)
                        <= available) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            scale = low;
        }
        // 字号定下来之后再分配行间距：>100% 装不下就收回到能装下的最大值（字号不动），
        // 已经缩到最小字号还装不下时同样不再额外放大行距。
        float factor = spacing / 100f;
        if (factor > 1f) {
            if (heightAt(scale, count, sizesDp, scales, minimumGapsDp, uniform, safeUnit)
                    > available) {
                factor = 1f;
                out.lineSpacingLimited = true;
            } else if (heightAt(scale, count, sizesDp, scales, minimumGapsDp, uniform, safeUnit,
                    spacingRows, factor) > available) {
                float low = 1f;
                float high = factor;
                for (int iteration = 0; iteration < 8; iteration++) {
                    float middle = (low + high) * 0.5f;
                    if (heightAt(scale, count, sizesDp, scales, minimumGapsDp, uniform, safeUnit,
                            spacingRows, middle) <= available) {
                        low = middle;
                    } else {
                        high = middle;
                    }
                }
                factor = low;
                out.lineSpacingLimited = true;
            }
        }
        float uniformGap = uniformGapPx(count, sizesDp, scales, minimumGapsDp, uniform,
                safeUnit, scale);
        float baseline = 0f;
        float previousSize = 0f;
        for (int index = 0; index < count; index++) {
            float size = rowSizePx(sizesDp[index], scales[index], safeUnit, scale);
            if (index == 0) {
                baseline = ascent(size);
            } else {
                float required = requiredGapPx(index, sizesDp, scales, minimumGapsDp,
                        safeUnit, scale);
                float gap = uniform[index] ? Math.max(required, uniformGap) : required;
                baseline += gap * rowSpacingFactor(index, spacingRows, factor);
            }
            out.baselinesPx[index] = baseline;
            previousSize = size;
        }
        out.scale = scale;
        out.uniformGapPx = uniformGap;
        out.lineSpacingPercent = Math.round(factor * 100f);
        out.heightPx = baseline + descent(previousSize);
    }

    /** 行距是否跟随行间距缩放：只有相邻两行都是歌词的行会被调用方标记。 */
    private static float rowSpacingFactor(int index, boolean[] spacingRows, float factor) {
        if (spacingRows == null || index >= spacingRows.length || !spacingRows[index]) return 1f;
        return factor;
    }

    private static float heightAt(float scale, int count, float[] sizesDp, boolean[] scales,
                                  float[] minimumGapsDp, boolean[] uniform, float densityUnit) {
        return heightAt(scale, count, sizesDp, scales, minimumGapsDp, uniform, densityUnit,
                null, 1f);
    }

    private static float heightAt(float scale, int count, float[] sizesDp, boolean[] scales,
                                  float[] minimumGapsDp, boolean[] uniform, float densityUnit,
                                  boolean[] spacingRows, float spacingFactor) {
        float uniformGap = uniformGapPx(count, sizesDp, scales, minimumGapsDp, uniform,
                densityUnit, scale);
        return blockHeightPx(count, sizesDp, scales, minimumGapsDp, uniform, densityUnit,
                scale, uniformGap, spacingRows, spacingFactor);
    }

    /** 主行共用的那份基线行距：不小于各行自己的下限，也不小于任意一对相邻行的需求。 */
    private static float uniformGapPx(int count, float[] sizesDp, boolean[] scales,
                                      float[] minimumGapsDp, boolean[] uniform,
                                      float densityUnit, float scale) {
        float gap = 0f;
        for (int index = 1; index < count; index++) {
            if (!uniform[index]) continue;
            gap = Math.max(gap, requiredGapPx(index, sizesDp, scales, minimumGapsDp,
                    densityUnit, scale));
        }
        return Math.max(gap, MIN_LYRIC_GAP_DP * densityUnit);
    }

    private static float blockHeightPx(int count, float[] sizesDp, boolean[] scales,
                                       float[] minimumGapsDp, boolean[] uniform,
                                       float densityUnit, float scale, float uniformGap) {
        return blockHeightPx(count, sizesDp, scales, minimumGapsDp, uniform, densityUnit,
                scale, uniformGap, null, 1f);
    }

    private static float blockHeightPx(int count, float[] sizesDp, boolean[] scales,
                                       float[] minimumGapsDp, boolean[] uniform,
                                       float densityUnit, float scale, float uniformGap,
                                       boolean[] spacingRows, float spacingFactor) {
        float baseline = ascent(rowSizePx(sizesDp[0], scales[0], densityUnit, scale));
        float previousSize = rowSizePx(sizesDp[0], scales[0], densityUnit, scale);
        for (int index = 1; index < count; index++) {
            float size = rowSizePx(sizesDp[index], scales[index], densityUnit, scale);
            float required = requiredGapPx(index, sizesDp, scales, minimumGapsDp,
                    densityUnit, scale);
            float gap = uniform[index] ? Math.max(required, uniformGap) : required;
            baseline += gap * rowSpacingFactor(index, spacingRows, spacingFactor);
            previousSize = size;
        }
        return baseline + descent(previousSize);
    }

    private static float rowSizePx(float sizeDp, boolean scales, float densityUnit, float scale) {
        return sizeDp * (scales ? scale : 1f) * densityUnit;
    }

    /** Baseline gap row {@code index} needs above itself so its glyphs never touch the row above. */
    private static float requiredGapPx(int index, float[] sizesDp, boolean[] scales,
                                       float[] minimumGapsDp, float densityUnit, float scale) {
        float upper = sizesDp[index - 1] * (scales[index - 1] ? scale : 1f);
        float lower = sizesDp[index] * (scales[index] ? scale : 1f);
        return stackedGapDp(upper, lower, Math.max(0f, minimumGapsDp[index])) * densityUnit;
    }
}
