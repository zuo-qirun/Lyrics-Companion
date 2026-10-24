package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassicLayoutMathTest {
    @Test public void enlargedTitleIsIndependentOfSmallPanelContentScale() {
        assertEquals(27f, ClassicLayoutMath.titleSizePx(1f, 0.4f, 1.8f, true), 0.001f);
        assertEquals(10.8f, ClassicLayoutMath.titleSizePx(1f, 0.4f, 1.8f, false), 0.001f);
        assertEquals(54f, ClassicLayoutMath.titleSizePx(2f, 0.8f, 1.8f, true), 0.001f);
    }

    /** 状态行 + 歌名 + n 行歌词的常见组合，字号按经典样式的默认值。 */
    private static void fillDefaultRows(float[] sizes, boolean[] scales, float[] mins,
                                        boolean[] uniform, int lyricRows) {
        int index = 0;
        sizes[index] = 11f;
        scales[index] = true;
        mins[index] = 0f;
        uniform[index] = true;
        index++;
        sizes[index] = 15f;
        scales[index] = false;
        mins[index] = ClassicLayoutMath.MIN_LYRIC_GAP_DP;
        uniform[index] = true;
        index++;
        for (int row = 0; row < lyricRows; row++) {
            sizes[index] = row == lyricRows / 2 ? 22f : 22f * 0.7f;
            scales[index] = true;
            // 歌名下面那一行是上一句时沿用 27dp 的历史下限，是本句时用 24dp。
            mins[index] = row == 0 && lyricRows > 1
                    ? ClassicLayoutMath.MIN_TITLE_GAP_DP : ClassicLayoutMath.MIN_LYRIC_GAP_DP;
            uniform[index] = true;
            index++;
        }
    }

    @Test public void wideningFixedHeightDoesNotIncreaseClassicScalePastHeight() {
        float normal = ClassicLayoutMath.contentScale(390f, 226f, 1f);
        float wide = ClassicLayoutMath.contentScale(780f, 226f, 1f);
        assertEquals(1f, normal, 0.001f);
        assertEquals(normal, wide, 0.001f);
    }

    @Test public void classicRowsFollowTheLineCountSetting() {
        // 经典样式现在最多摆七行歌词（上一句 ×3 / 本句 / 下一句 ×3，issue #64）。
        assertEquals(7, ClassicLayoutMath.visibleRowCount(9));
        assertEquals(5, ClassicLayoutMath.visibleRowCount(5));
        assertEquals(3, ClassicLayoutMath.visibleRowCount(3));
        assertEquals(1, ClassicLayoutMath.visibleRowCount(0));
        // 1 行只有本句；2 行＝本句+下一句；3 行＝上一句+本句+下一句；之后两侧交替补行。
        assertEquals(0, ClassicLayoutMath.followingRowCount(1));
        assertEquals(0, ClassicLayoutMath.precedingRowCount(1));
        assertEquals(1, ClassicLayoutMath.followingRowCount(2));
        assertEquals(0, ClassicLayoutMath.precedingRowCount(2));
        assertEquals(1, ClassicLayoutMath.followingRowCount(3));
        assertEquals(1, ClassicLayoutMath.precedingRowCount(3));
        assertEquals(2, ClassicLayoutMath.followingRowCount(4));
        assertEquals(1, ClassicLayoutMath.precedingRowCount(4));
        assertEquals(2, ClassicLayoutMath.followingRowCount(5));
        assertEquals(2, ClassicLayoutMath.precedingRowCount(5));
        assertEquals(3, ClassicLayoutMath.followingRowCount(7));
        assertEquals(3, ClassicLayoutMath.precedingRowCount(7));
    }

    @Test public void tallerPanelAddsMarginInsteadOfWideningTheRowGap() {
        // issue #53 的原话：调高悬浮窗，歌名与相邻句的行距不该跟着变大。
        float[] sizes = new float[5];
        boolean[] scales = new boolean[5];
        float[] mins = new float[5];
        boolean[] uniform = new boolean[5];
        fillDefaultRows(sizes, scales, mins, uniform, 3);
        ClassicLayoutMath.Block tight = new ClassicLayoutMath.Block(5);
        ClassicLayoutMath.Block roomy = new ClassicLayoutMath.Block(5);
        ClassicLayoutMath.pack(tight, 5, sizes, scales, mins, uniform, 1f, 1f, 140f);
        ClassicLayoutMath.pack(roomy, 5, sizes, scales, mins, uniform, 1f, 1f, 300f);
        assertEquals(tight.heightPx, roomy.heightPx, 0.001f);
        assertEquals(tight.uniformGapPx, roomy.uniformGapPx, 0.001f);
        for (int index = 0; index < 5; index++) {
            assertEquals(tight.baselinesPx[index], roomy.baselinesPx[index], 0.001f);
        }
    }

    @Test public void mainRowsShareOneUniformBaselineGap() {
        // 歌名与歌词行、歌词行与歌词行用同一个基线行距：整块看起来才均匀（issue #53）。
        float[] sizes = new float[5];
        boolean[] scales = new boolean[5];
        float[] mins = new float[5];
        boolean[] uniform = new boolean[5];
        fillDefaultRows(sizes, scales, mins, uniform, 3);
        ClassicLayoutMath.Block block = new ClassicLayoutMath.Block(5);
        ClassicLayoutMath.pack(block, 5, sizes, scales, mins, uniform, 1f, 1f, 400f);
        assertEquals(1f, block.scale, 0.0001f);
        for (int index = 1; index < 5; index++) {
            assertEquals("第 " + index + " 行的行距应与统一行距一致",
                    block.uniformGapPx, block.baselinesPx[index] - block.baselinesPx[index - 1],
                    0.001f);
        }
    }

    @Test public void translationHangsOffTheCurrentLineWithItsOwnGap() {
        // 翻译行不参与统一行距，贴着本句（多行时尤其明显，issue #64）。
        float[] sizes = {11f, 15f, 22f * 0.7f, 22f, 12f, 22f * 0.7f};
        boolean[] scales = {true, false, true, true, true, true};
        float[] mins = {0f, ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_TITLE_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_TRANSLATION_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP};
        boolean[] uniform = {true, true, true, true, false, true};
        ClassicLayoutMath.Block block = new ClassicLayoutMath.Block(sizes.length);
        ClassicLayoutMath.pack(block, sizes.length, sizes, scales, mins, uniform, 1f, 1f, 400f);
        float translationGap = block.baselinesPx[4] - block.baselinesPx[3];
        float nextGap = block.baselinesPx[5] - block.baselinesPx[4];
        assertTrue("翻译到本句的间距应小于统一行距", translationGap < block.uniformGapPx);
        assertEquals("翻译后面的行回到统一行距", block.uniformGapPx, nextGap, 0.001f);
    }

    @Test public void fiveLyricRowsShrinkToFitInsteadOfOverflowing() {
        // 状态 + 歌名 + 五行歌词：226dp 面板能排下，但再矮一点就得缩字（issue #64）。
        float[] sizes = {11f, 15f, 22f, 22f, 22f, 22f, 22f};
        boolean[] scales = {true, false, true, true, true, true, true};
        float[] mins = {0f, ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_TITLE_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_LYRIC_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_LYRIC_GAP_DP};
        boolean[] uniform = {true, true, true, true, true, true, true};
        ClassicLayoutMath.Block roomy = new ClassicLayoutMath.Block(sizes.length);
        ClassicLayoutMath.pack(roomy, sizes.length, sizes, scales, mins, uniform, 1f, 1f, 187f);
        assertEquals("226dp 的面板要能原样排下五行歌词", 1f, roomy.scale, 0.0001f);
        assertTrue(roomy.heightPx <= 187f);
        ClassicLayoutMath.Block tight = new ClassicLayoutMath.Block(sizes.length);
        ClassicLayoutMath.pack(tight, sizes.length, sizes, scales, mins, uniform, 1f, 1f, 170f);
        assertTrue("矮面板必须缩字才排得下，实际 scale=" + tight.scale, tight.scale < 1f);
        assertTrue(tight.scale >= ClassicLayoutMath.MIN_TEXT_SCALE);
        assertTrue("排好的块不能超过可用高度，实际 " + tight.heightPx, tight.heightPx <= 170.5f);
        for (int index = 1; index < sizes.length; index++) {
            assertTrue(tight.baselinesPx[index] > tight.baselinesPx[index - 1]);
        }
    }

    @Test public void raisingTheTitleSizeNoLongerShrinksTheOtherRows() {
        // 歌名 180%：其它行仍按用户设的字号显示，只有整块真的装不下时才缩（issue #38）。
        float[] sizes = {11f, 15f * 1.8f, 22f * 0.7f, 22f, 12f, 22f * 0.7f};
        boolean[] scales = {true, false, true, true, true, true};
        float[] mins = {0f, ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_TITLE_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_TRANSLATION_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP};
        boolean[] uniform = {true, true, true, true, false, true};
        ClassicLayoutMath.Block block = new ClassicLayoutMath.Block(sizes.length);
        ClassicLayoutMath.pack(block, sizes.length, sizes, scales, mins, uniform, 1f, 1f, 260f);
        assertEquals(1f, block.scale, 0.0001f);
    }

    @Test public void rowGapGrowsWithTheTitleSizeAndKeepsItsMinimum() {
        // 默认字号下间距保持改动前的 24dp / 27dp，观感不变。
        assertEquals(24f, ClassicLayoutMath.stackedGapDp(11f, 15f, 24f), 0.01f);
        assertEquals(27f, ClassicLayoutMath.stackedGapDp(15f, 12.1f, 27f), 0.01f);
        // 歌名调到 180%：间距跟着长，两行不再互相挤（issue #38）。
        assertTrue(ClassicLayoutMath.stackedGapDp(11f, 27f, 24f) > 24f);
    }

    @Test public void contentAlignmentMovesTheWholeBlock() {
        // 留空＝沿用样式自己的摆法，位移为 0。
        assertEquals(0f, ClassicLayoutMath.alignedRowShift("", 90f, 60f, 10f, 200f), 0.0001f);
        // 顶部：块顶贴到区域上沿。
        assertEquals(-80f, ClassicLayoutMath.alignedRowShift("top", 90f, 60f, 10f, 200f), 0.0001f);
        // 居中：剩余空间上下各一半。
        assertEquals(-15f, ClassicLayoutMath.alignedRowShift("center", 90f, 60f, 10f, 200f), 0.0001f);
        // 底部：块底贴到区域下沿。
        assertEquals(50f, ClassicLayoutMath.alignedRowShift("bottom", 90f, 60f, 10f, 200f), 0.0001f);
        // 内容比区域还高时不再往外推，仍然从上沿开始。
        assertEquals(-80f, ClassicLayoutMath.alignedRowShift("center", 90f, 260f, 10f, 200f),
                0.0001f);
    }

    @Test public void currentLineAnchorRebalancesPrecedingAndFollowingRows() {
        // issue #155：顶部时当前句上面不再排上一句，7 行设置下后面排 6 行。
        assertEquals(0, ClassicLayoutMath.anchorRow(7, "top"));
        assertEquals(0, ClassicLayoutMath.precedingRowCount(7, "top"));
        assertEquals(6, ClassicLayoutMath.followingRowCount(7, "top"));
        // 第二行：上面留一句上一句、当前句落第 2 行、后面还有 5 行（7 行设置的验收场景）。
        assertEquals(1, ClassicLayoutMath.anchorRow(7, "second"));
        assertEquals(1, ClassicLayoutMath.precedingRowCount(7, "second"));
        assertEquals(5, ClassicLayoutMath.followingRowCount(7, "second"));
        // 居中（空字符串）与旧的两参数行为一致：3 前 / 3 后。
        assertEquals(ClassicLayoutMath.precedingRowCount(7), ClassicLayoutMath.anchorRow(7, ""));
        assertEquals(3, ClassicLayoutMath.precedingRowCount(7, ""));
        assertEquals(3, ClassicLayoutMath.followingRowCount(7, ""));
        // 行数不足时自动退化，不会出现负行位。
        assertEquals(0, ClassicLayoutMath.anchorRow(1, "second"));
        assertEquals(0, ClassicLayoutMath.followingRowCount(1, "top"));
        assertEquals(1, ClassicLayoutMath.anchorRow(2, "second"));
    }

    /** 按 drawDefault 的行表造 7 行歌词（歌名 + 若干上一句/当前句/下一句），返回当前句的行号。 */
    private static int packAnchoredRows(ClassicLayoutMath.Block block, int lyricRows,
                                        String anchor) {
        int preceding = ClassicLayoutMath.precedingRowCount(lyricRows, anchor);
        int following = ClassicLayoutMath.followingRowCount(lyricRows, anchor);
        // 歌名行 + 上一句 preceding 行 + 当前句 + 下一句 following 行 = 2 + preceding + following。
        int count = 2 + preceding + following;
        float[] sizes = new float[count];
        boolean[] scales = new boolean[count];
        float[] mins = new float[count];
        boolean[] uniform = new boolean[count];
        boolean[] spacing = new boolean[count];
        sizes[0] = 15f;
        scales[0] = false;
        mins[0] = ClassicLayoutMath.MIN_LYRIC_GAP_DP;
        uniform[0] = true;
        int currentRow = -1;
        int index = 1;
        for (int offset = -preceding; offset <= following; offset++) {
            sizes[index] = offset == 0 ? 22f : 22f * 0.7f;
            scales[index] = true;
            mins[index] = index == 1 ? ClassicLayoutMath.MIN_TITLE_GAP_DP
                    : ClassicLayoutMath.MIN_LYRIC_GAP_DP;
            uniform[index] = true;
            spacing[index] = true;
            if (offset == 0) currentRow = index;
            index++;
        }
        ClassicLayoutMath.pack(block, count, sizes, scales, mins, uniform, 1f, 1f, 400f, 100,
                spacing);
        return currentRow;
    }

    @Test public void currentLineAnchorPlacesTheCurrentLineOnTheRequestedRow() {
        ClassicLayoutMath.Block top = new ClassicLayoutMath.Block(8);
        assertEquals("顶部时当前句紧贴歌名下面一行", 1,
                packAnchoredRows(top, 7, "top"));
        ClassicLayoutMath.Block second = new ClassicLayoutMath.Block(8);
        assertEquals("第二行时歌名与当前句中间隔一行上一句", 2,
                packAnchoredRows(second, 7, "second"));
        ClassicLayoutMath.Block center = new ClassicLayoutMath.Block(8);
        assertEquals("默认居中仍是原有行位", 4,
                packAnchoredRows(center, 7, ""));
    }

    @Test public void lineSpacingPercentIsClampedToTheSupportedRange() {
        // issue #138：滑杆范围 80–200，默认 100 = 原有排版。
        assertEquals(80, ClassicLayoutMath.MIN_LINE_SPACING_PERCENT);
        assertEquals(200, ClassicLayoutMath.MAX_LINE_SPACING_PERCENT);
        assertEquals(80, ClassicLayoutMath.normalizeLineSpacing(0));
        assertEquals(80, ClassicLayoutMath.normalizeLineSpacing(79));
        assertEquals(100, ClassicLayoutMath.normalizeLineSpacing(100));
        assertEquals(200, ClassicLayoutMath.normalizeLineSpacing(201));
        assertEquals(200, ClassicLayoutMath.normalizeLineSpacing(999));
        assertEquals(1f, ClassicLayoutMath.lineSpacingFactor(100), 0.0001f);
        assertEquals(1.5f, ClassicLayoutMath.lineSpacingFactor(150), 0.0001f);
        assertEquals(2f, ClassicLayoutMath.lineSpacingFactor(200), 0.0001f);
        assertEquals(0.8f, ClassicLayoutMath.lineSpacingFactor(20), 0.0001f);
    }

    private static void packSpacingRows(ClassicLayoutMath.Block block, int percent) {
        float[] sizes = {22f, 22f, 22f};
        boolean[] scales = {true, true, true};
        float[] mins = {0f, ClassicLayoutMath.MIN_LYRIC_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP};
        boolean[] uniform = {true, true, true};
        boolean[] spacingRows = {false, true, true};
        ClassicLayoutMath.pack(block, 3, sizes, scales, mins, uniform, 1f, 1f, 400f, percent,
                spacingRows);
    }

    @Test public void lineSpacingScalesOnlyAdjacentLyricRowGaps() {
        ClassicLayoutMath.Block base = new ClassicLayoutMath.Block(3);
        ClassicLayoutMath.Block wide = new ClassicLayoutMath.Block(3);
        ClassicLayoutMath.Block tight = new ClassicLayoutMath.Block(3);
        packSpacingRows(base, 100);
        packSpacingRows(wide, 200);
        packSpacingRows(tight, 80);
        float baseGap = base.baselinesPx[1] - base.baselinesPx[0];
        float wideGap = wide.baselinesPx[1] - wide.baselinesPx[0];
        float tightGap = tight.baselinesPx[1] - tight.baselinesPx[0];
        assertEquals("200% 只放大歌词行之间的行距", 2f * baseGap, wideGap, 0.01f);
        assertEquals("80% 收窄到原来的八成", 0.8f * baseGap, tightGap, 0.01f);
        assertEquals(100, base.lineSpacingPercent);
        assertEquals(200, wide.lineSpacingPercent);
        assertEquals(80, tight.lineSpacingPercent);
        assertTrue(!wide.lineSpacingLimited);
        // 字号没有被行间距带着变，块高只由行距增长。
        assertEquals(base.scale, wide.scale, 0.0001f);
        assertTrue(wide.heightPx > base.heightPx);
    }

    @Test public void lineSpacingShrinksBackWhenThePanelCannotFitIt() {
        // 字号边界：默认字号在 180dp 高度下正好排得下 7 行歌词；200% 行距装不进去。
        float[] sizes = {22f, 22f, 22f, 22f, 22f, 22f, 22f};
        boolean[] scales = {true, true, true, true, true, true, true};
        float[] mins = {0f, ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_LYRIC_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_LYRIC_GAP_DP,
                ClassicLayoutMath.MIN_LYRIC_GAP_DP, ClassicLayoutMath.MIN_LYRIC_GAP_DP};
        boolean[] uniform = {true, true, true, true, true, true, true};
        boolean[] spacingRows = {false, true, true, true, true, true, true};
        ClassicLayoutMath.Block base = new ClassicLayoutMath.Block(7);
        ClassicLayoutMath.pack(base, 7, sizes, scales, mins, uniform, 1f, 1f, 180f, 100,
                spacingRows);
        ClassicLayoutMath.Block crowded = new ClassicLayoutMath.Block(7);
        ClassicLayoutMath.pack(crowded, 7, sizes, scales, mins, uniform, 1f, 1f, 180f, 200,
                spacingRows);
        assertEquals("字号不因行间距缩小", base.scale, crowded.scale, 0.0001f);
        assertTrue("200% 放不下时应记录被收住", crowded.lineSpacingLimited);
        assertTrue("实际生效行距应低于 200%", crowded.lineSpacingPercent < 200);
        assertTrue("实际生效行距不应低于 100%", crowded.lineSpacingPercent >= 100);
        assertTrue("收住行距后必须装得下，实际 " + crowded.heightPx, crowded.heightPx <= 180.5f);
    }

    @Test public void stackedCoverSizeLeavesRoomForMetadataAndLyrics() {
        // issue #134：竖排封面大小给上限，封面 + 歌名 + 歌词共用高度预算。
        assertEquals(120f,
                ClassicLayoutMath.stackedCoverSize(120f, 300f, 40f, 60f), 0.001f);
        assertEquals(80f,
                ClassicLayoutMath.stackedCoverSize(200f, 180f, 40f, 60f), 0.001f);
        assertEquals(1f,
                ClassicLayoutMath.stackedCoverSize(200f, 50f, 40f, 60f), 0.001f);
        assertTrue(ClassicLayoutMath.stackedCoverSize(200f, 300f, 40f, 60f)
                <= 300f - 40f - 60f);
    }
}
