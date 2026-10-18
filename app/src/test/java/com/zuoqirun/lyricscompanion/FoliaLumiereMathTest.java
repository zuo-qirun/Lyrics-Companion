package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/** Values exported by tools/folia-original/lumiere-source-values.mjs from upstream 9279251. */
public final class FoliaLumiereMathTest {
    @Test public void deterministicRandomMatchesOriginalUnsignedMulberryStreamAndSkip() {
        double[] expected = {0.4677815765608102, 0.9576418695505708, 0.39120850758627057,
                0.43996696919202805, 0.3027209595311433};
        FoliaLumiereMath.Random random = new FoliaLumiereMath.Random("fixture");
        for (double value : expected) assertEquals(value, random.next(), 0);
        FoliaLumiereMath.Random skipped = new FoliaLumiereMath.Random("fixture");
        skipped.skip(4);
        assertEquals(expected[4], skipped.next(), 0);
    }

    @Test public void cameraMatchesExportedOriginalTransformAtSixPointSevenSeconds() {
        double[] camera = new double[4];
        FoliaLumiereMath.camera(camera, 6.7, 0, 18, 0.035, 0, -0.006, 800, 600);
        assertArrayEquals(new double[]{401.380375621679, 297.1293022396897,
                1.0135451697485733, 0.0006790657186851968}, camera, 1e-10);
    }

    @Test public void cameraHasContinuousPositionAndVelocityAtEndpointsAndPausedTimeIsExact() {
        double[] before = new double[4], at = new double[4], after = new double[4];
        double dt = 0.00001;
        for (double boundary : new double[]{0, 18}) {
            FoliaLumiereMath.camera(before, boundary - dt, 0, 18, 0.035, 0, -0.006, 800, 600);
            FoliaLumiereMath.camera(at, boundary, 0, 18, 0.035, 0, -0.006, 800, 600);
            FoliaLumiereMath.camera(after, boundary + dt, 0, 18, 0.035, 0, -0.006, 800, 600);
            for (int i = 0; i < 4; i++) {
                assertEquals((at[i] - before[i]) / dt, (after[i] - at[i]) / dt, 0.0001);
                assertEquals(at[i], after[i], 0.00001);
            }
        }
        FoliaLumiereMath.camera(before, 6.7, 0, 18, .035, 0, -.006, 800, 600);
        FoliaLumiereMath.camera(after, 6.7, 0, 18, .035, 0, -.006, 800, 600);
        assertArrayEquals(before, after, 0);
    }

    @Test public void noiseAndAllFourWindowMasksMatchSource() {
        assertEquals(0.8962012624353974, FoliaLumiereMath.hash11(-12.3), 1e-12);
        assertEquals(0.6581390138881034, FoliaLumiereMath.hash12(1.25, 7.5), 1e-12);
        assertEquals(0.326231781481563, FoliaLumiereMath.noise1(3.7), 1e-12);
        assertEquals(0.7994873069494908, FoliaLumiereMath.noise2(1.25, 7.5), 1e-12);
        assertEquals(.12, FoliaLumiereMath.gobo(1, 5, .4, .3, .2, .7), 0);
        assertEquals(.12, FoliaLumiereMath.gobo(2, 5, .4, .3, .2, .7), 0);
        assertEquals(.12, FoliaLumiereMath.gobo(3, 5, .4, .3, .2, .7), 0);
        assertEquals(1, FoliaLumiereMath.gobo(4, 5, .4, .3, .2, .7), 0);
    }

    @Test public void allOneHundredOriginalOpticalProfilesRemainDistinctAndParse() {
        assertEquals(100, FoliaLumiereProfiles.size());
        HashSet<String> kinds = new HashSet<>();
        HashSet<String> families = new HashSet<>();
        for (int i = 0; i < FoliaLumiereProfiles.size(); i++) {
            assertTrue(kinds.add(FoliaLumiereProfiles.KINDS[i]));
            families.add(FoliaLumiereProfiles.FAMILIES[i]);
            assertEquals(FoliaLumiereProfiles.KINDS[i], FoliaLumiereProfiles.get(i).optString("kind"));
            assertNotNull(FoliaLumiereProfiles.get(i).optJSONObject("region"));
            assertNotNull(FoliaLumiereProfiles.get(i).optJSONObject("camera"));
        }
        assertEquals(10, families.size());
    }

    @Test public void paragraphRenderTailAndCastChainMatchFullSourceFixture() {
        String[] text = {"在无人的世界里", "听见风穿过树梢", "所有微光 汇成星河",
                "让这首歌陪你远行", "Until the morning comes", "我们仍然向前走"};
        List<LrcTimeline.Line> lines = new ArrayList<>();
        for (int i = 0; i < text.length; i++) lines.add(new LrcTimeline.Line(i * 3000, 3000, text[i]));
        List<FoliaLumiereMath.Paragraph> program = FoliaLumiereMath.compile(lines, 18000, "Fixture\n微光\nFolia");
        assertEquals(2, program.size());
        assertEquals(6000, program.get(0).end);
        assertEquals(18380, program.get(1).end);
        assertEquals("verse", program.get(0).kind);
        assertEquals("outro", program.get(1).kind);
        assertTrue(program.get(0).opening);
        assertFalse(program.get(1).opening);
        assertEquals("lattice-shoji", FoliaLumiereProfiles.KINDS[program.get(0).shots.get(0).profile]);
        assertEquals("prism-droplet", FoliaLumiereProfiles.KINDS[program.get(1).shots.get(0).profile]);
        assertEquals("caustic-pool", FoliaLumiereProfiles.KINDS[program.get(1).shots.get(1).profile]);
    }

    @Test public void briefIntroDoesNotRestartStarfallAndLongBridgesHaveBoundedShots() {
        List<LrcTimeline.Line> lines = new ArrayList<>();
        lines.add(new LrcTimeline.Line(5000, 3000, "Here we go"));
        List<FoliaLumiereMath.Paragraph> program = FoliaLumiereMath.compile(lines, 60000, "fixture");
        assertTrue(program.get(0).opening);
        assertFalse(program.get(1).opening);
        assertTrue(program.get(1).shots.size() > 2);
        for (FoliaLumiereMath.Shot shot : program.get(1).shots) {
            if (shot.bridge) assertTrue(shot.end - shot.start <= 16000);
        }
    }

    @Test public void lineDriftTurnsAwaySmoothlyWithoutStoppingTheHeldLine() {
        assertTrue(FoliaLumiereMath.away(-0.1, 1) > 0);
        assertEquals(0.1, FoliaLumiereMath.away(0.1, 0), 0);
        double step = 0.00001;
        double x = FoliaLumiereMath.held(.01, .02, step, false);
        double y = FoliaLumiereMath.held(.01, .02, step, true);
        assertEquals(.01, x / step, .00001);
        assertEquals(.02, y / step, .00001);
    }
}
