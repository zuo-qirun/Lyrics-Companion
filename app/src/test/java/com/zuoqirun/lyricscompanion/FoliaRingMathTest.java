package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class FoliaRingMathTest {
    @Test public void originalForegroundProjectionAndFocusArePreserved() {
        FoliaRingMath.Pose pose = new FoliaRingMath.Pose();
        FoliaRingMath.project(pose, 0, 352, 45, 0, 0.65, 1, false, false, 0, 1, 1);
        assertEquals(352 * 0.09 / Math.sqrt(2), pose.x, 1e-9);
        assertEquals(pose.x, pose.y, 1e-9);
        assertEquals(1.98, pose.scale, 1e-9);
        assertEquals(1, pose.opacity, 1e-9);
        assertEquals(-38, pose.rotation, 1e-9);
        assertEquals(0, pose.blur, 1e-9);
        assertEquals(1, pose.focus, 1e-9);
    }

    @Test public void rearGlyphUsesOriginalDepthAndLeadFade() {
        FoliaRingMath.Pose pose = new FoliaRingMath.Pose();
        FoliaRingMath.project(pose, Math.PI, 352, 45, 1, 0.65, 1, false, true, 0, 1, 1);
        assertEquals(0.22, pose.scale, 1e-9);
        assertEquals(0.35 * 0.68 * 0.18, pose.opacity, 1e-9);
        assertEquals(8, pose.blur, 1e-9);
        assertEquals(0, pose.focus, 1e-9);
        FoliaRingMath.project(pose, Math.PI, 352, 45, 1, 0.65, 1, true, false, 1, 1, 1);
        assertEquals(0, pose.opacity, 1e-9);
    }

    @Test public void radiusHasOriginalResponsiveCapAndTiltCanBeHorizontal() {
        assertEquals(352, FoliaRingMath.radius(800, 1), 1e-9);
        assertEquals(560, FoliaRingMath.radius(1920, 1), 1e-9);
        assertEquals(672, FoliaRingMath.radius(1920, 1.2), 1e-9);
        FoliaRingMath.Pose pose = new FoliaRingMath.Pose();
        FoliaRingMath.project(pose, 0, 352, 0, 0, 0.65, 1, false, false, 0, 1, 1);
        assertEquals(0, pose.x, 1e-9);
        assertEquals(31.68, pose.y, 1e-9);
        assertEquals(0, pose.rotation, 1e-9);
        assertEquals(-10, FoliaRingMath.readableAngle(170), 1e-9);
        assertEquals(10, FoliaRingMath.readableAngle(-170), 1e-9);
    }

    @Test public void wordClockInterpolatesAndKeepsDriftingDuringLongNotes() {
        long[] starts = {1000, 2000, 3000};
        long[] ends = {2000, 3000, 4000};
        double[] angles = {-0.3, 0, 0.3};
        assertEquals(-0.15, FoliaRingMath.wordOffset(angles, starts, ends, 1500, 5000), 1e-9);
        assertEquals(0.6, FoliaRingMath.wordOffset(angles, starts, ends, 4000, 5000), 1e-9);
        assertEquals(0.9, FoliaRingMath.wordOffset(angles, starts, ends, 5000, 5000), 1e-9);
        assertEquals(0, FoliaRingMath.wordOffset(new double[0], new long[0], new long[0], 1500, 5000), 1e-9);
    }

    @Test public void pauseAndAnimationOffDoNotKeepRequestingFastFrames() {
        assertEquals(400, FoliaRingMath.frameDelay("claddagh", 60, false, true));
        assertEquals(400, FoliaRingMath.frameDelay("claddagh", 0, true, true));
        assertEquals(400, FoliaRingMath.frameDelay("still", 60, true, true));
        assertEquals(17, FoliaRingMath.frameDelay("claddagh", 60, true, false));
        assertEquals(33, FoliaRingMath.frameDelay("claddagh", 30, true, false));
        assertEquals(42, FoliaRingMath.frameDelay("claddagh", 24, true, false));
        assertEquals(12000, FoliaRingMath.sceneTime("claddagh", 30, 12000));
        assertEquals(0, FoliaRingMath.sceneTime("claddagh", 0, 12000));
        assertEquals(0, FoliaRingMath.sceneTime("still", 60, 12000));
        assertEquals(0, FoliaRingMath.sceneTime("claddagh", 30, -500));
        assertEquals(0, FoliaRingMath.spring(0), 1e-9);
        assertEquals(1, FoliaRingMath.spring(3), 1e-8);
    }

    @Test public void realWordTimestampsAndLongGapAreRetained() {
        LrcTimeline line = LrcTimeline.parse("", "", "[1000,4000](1000,1000,0)你(4000,1000,0)好");
        FoliaGlyphTimeline glyphs = new FoliaGlyphTimeline(line.renderLines().get(0), 9000);
        assertArrayEquals(new String[]{"你", "好"}, glyphs.glyphs);
        assertArrayEquals(new long[]{1000, 4000}, glyphs.starts);
        assertArrayEquals(new long[]{2000, 5000}, glyphs.ends);
        assertEquals(8660, glyphs.renderEnd);
        assertEquals(0.5, glyphs.progress(0, 1500), 1e-9);
        assertEquals(0, glyphs.progress(1, 3000), 1e-9);
    }

    @Test public void plainLineAndUnicodeSequencesRemainWhole() {
        assertArrayEquals(new String[]{"A", "e\u0301", "👩‍🚀", "🇨🇳", "👍🏽"},
                FoliaGlyphTimeline.split("Ae\u0301👩‍🚀🇨🇳👍🏽"));
        FoliaGlyphTimeline glyphs = new FoliaGlyphTimeline(new LrcTimeline.Line(1000, 4000, "歌词"), 7000);
        assertArrayEquals(new long[]{1000, 3000}, glyphs.starts);
        assertArrayEquals(new long[]{3000, 5000}, glyphs.ends);
        assertEquals(0.5, glyphs.progress(0, 2000), 1e-9);
    }

    @Test public void allModesAreSelectableInsideOneStyleAndLegacyIdsRemainReadable() {
        assertEquals(14, FoliaModeCatalog.VALUES.length);
        assertEquals(14, new java.util.HashSet<>(Arrays.asList(FoliaModeCatalog.VALUES)).size());
        for (String mode : FoliaModeCatalog.VALUES) {
            assertTrue(FoliaModeCatalog.isStyle("folia_" + mode));
            assertEquals(mode, FoliaModeCatalog.modeForStyle("folia_" + mode));
            assertNotEquals(AppPreferences.styleScopedKey(AppPreferences.KEY_FOLIA_FOCUS, false, "folia_" + mode),
                    AppPreferences.styleScopedKey(AppPreferences.KEY_FOLIA_FOCUS, true, "folia_" + mode));
        }
        assertFalse(FoliaModeCatalog.isStyle("folia_unknown"));
        assertTrue(FoliaModeCatalog.isStyle("folia"));
        assertFalse(FoliaModeCatalog.isLegacyStyle("folia"));
        assertArrayEquals(new String[]{"default", "folia"},
                FoliaModeCatalog.append(new String[]{"default"}, false));
        assertFalse(FoliaModeCatalog.isStyle("refined"));
        assertEquals("claddagh", FoliaModeCatalog.normalize("unknown"));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_FOLIA_PANEL_WIDTH_DP));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_FOLIA_FPS));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_FOLIA_RADIUS));
    }

    @Test public void settingsShortcutOnlyAppearsForSelectedMainOrSecondaryFolia() {
        assertTrue(FoliaModeCatalog.settingsVisible(0, "folia"));
        assertTrue(FoliaModeCatalog.settingsVisible(1, "folia"));
        assertFalse(FoliaModeCatalog.settingsVisible(0, "compact"));
        assertFalse(FoliaModeCatalog.settingsVisible(1, "refined"));
        assertFalse(FoliaModeCatalog.settingsVisible(2, "folia"));
        assertFalse(FoliaModeCatalog.settingsVisible(-1, "folia"));
    }
}
