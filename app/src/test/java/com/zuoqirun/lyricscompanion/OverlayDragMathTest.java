package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 无界拖动 / 贴边的夹取与内容留白（issue #49）。 */
public class OverlayDragMathTest {
    @Test public void offscreenDisabledClampsToScreenEdges() {
        assertEquals(0, OverlayDragMath.clampAxis(-120, 1000, 400, 0));
        assertEquals(600, OverlayDragMath.clampAxis(9_999, 1000, 400, 0));
        assertEquals(300, OverlayDragMath.clampAxis(300, 1000, 400, 0));
    }

    @Test public void offscreenPercentAllowsBothDirections() {
        // 面板 400px、允许 30% → 可探出 120px
        int limit = OverlayDragMath.offsetLimitPx(true, 30, 400, 1f);
        assertEquals(120, limit);
        assertEquals(-120, OverlayDragMath.clampAxis(-500, 1000, 400, limit));
        assertEquals(720, OverlayDragMath.clampAxis(9_999, 1000, 400, limit));
        // 完全在屏幕内时不受影响
        assertEquals(200, OverlayDragMath.clampAxis(200, 1000, 400, limit));
    }

    @Test public void hardLimitKeepsPartOfThePanelReachable() {
        // 大面板：百分比先到顶（50% × 400 = 200，硬上限 400 - 24 = 376）
        assertEquals(200, OverlayDragMath.offsetLimitPx(true, OverlayDragMath.PERCENT_LIMIT,
                400, 1f));
        // 小面板：硬上限赢——50% × 40 = 20，但必须留 24px 在屏幕内，所以只能探出 16px
        assertEquals(16, OverlayDragMath.offsetLimitPx(true, OverlayDragMath.PERCENT_LIMIT,
                40, 1f));
        // 关闭时永远是 0；比例 0 也是 0
        assertEquals(0, OverlayDragMath.offsetLimitPx(false, 50, 400, 1f));
        assertEquals(0, OverlayDragMath.offsetLimitPx(true, 0, 400, 1f));
        assertEquals(0, OverlayDragMath.offsetLimitPx(true, 30, 0, 1f));
    }

    @Test public void joystickAndDragShareTheSameClamp() {
        int limit = OverlayDragMath.offsetLimitPx(true, 30, 400, 1f);
        assertEquals(OverlayDragMath.clampAxis(-500, 1000, 400, limit),
                OverlayDragMath.clampAxis(-500, 1000, 400, limit));
    }

    @Test public void percentIsClampedToTheSafeBand() {
        assertEquals(0, OverlayDragMath.normalizePercent(-10));
        assertEquals(30, OverlayDragMath.normalizePercent(30));
        assertEquals(OverlayDragMath.PERCENT_LIMIT,
                OverlayDragMath.normalizePercent(9_999));
        assertEquals(OverlayDragMath.DEFAULT_PERCENT, 30);
    }

    @Test public void contentPaddingScalesTheStyleValue() {
        // 未设置 → 逐像素沿用样式原本的内边距
        assertEquals(18f, ContentPaddingMath.padPx(18f, ContentPaddingMath.UNSET), 0.0001f);
        assertEquals(0f, ContentPaddingMath.padPx(18f, 0), 0.0001f);
        assertEquals(9f, ContentPaddingMath.padPx(18f, 50), 0.0001f);
        assertEquals(18f, ContentPaddingMath.padPx(18f, 100), 0.0001f);
        assertTrue(ContentPaddingMath.padPx(18f, 150) > 18f);
        assertEquals(ContentPaddingMath.MAX_PERCENT,
                ContentPaddingMath.normalizePercent(9_999));
        assertEquals(ContentPaddingMath.UNSET, ContentPaddingMath.normalizePercent(-1));
    }
}
