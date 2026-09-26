package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassicLayoutMathTest {
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
}
