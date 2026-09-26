package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OverlayPlaybackVisibilityTest {
    @Test public void disabledOptionKeepsOverlayVisibleWhenNotPlaying() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, false, false, false));
    }

    @Test public void enabledOptionKeepsOverlayVisibleDuringPlayback() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(true, true, false, false));
    }

    @Test public void enabledOptionHidesOverlayWhenPausedOrStopped() {
        assertTrue(OverlayPlaybackVisibility.shouldHide(true, false, false, false));
    }

    @Test public void enabledPlayerRuleHidesOverlayInsidePlayer() {
        assertTrue(OverlayPlaybackVisibility.shouldHide(false, true, true, true));
    }

    @Test public void enabledPlayerRuleKeepsOverlayVisibleOutsidePlayer() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, true, true, false));
    }

    @Test public void selectedForegroundAppHidesOverlay() {
        assertTrue(OverlayPlaybackVisibility.shouldHide(false, true, false, false, true));
    }

    /** issue #67：无歌词 / 纯音乐自动隐藏（开关默认关，且要过了宽限期才算）。 */
    @Test public void noLyricRuleIsOffByDefaultAndWaitsForTheGracePeriod() {
        // 开关关着：其它条件都不成立时不隐藏
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, true, false, false, false,
                false, true));
        // 开着 + 宽限期已过 → 隐藏
        assertTrue(OverlayPlaybackVisibility.shouldHide(false, true, false, false, false,
                true, true));
        // 开着但宽限期没过 → 不隐藏
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, true, false, false, false,
                true, false));
        // 兼容旧调用点：5 参 / 4 参重载行为不变
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, true, false, false));
        assertTrue(OverlayPlaybackVisibility.shouldHide(true, false, false, false));
    }
}
