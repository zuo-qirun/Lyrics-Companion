package com.zuoqirun.lyricscompanion;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TrackColorSelectorTest {
    @Test public void completingArtistAndInactiveGapsKeepTheCurrentSongColor() {
        TrackColorSelector colors = new TrackColorSelector(new Random(2));
        int chosen = colors.select("player", "song", "");
        assertEquals(chosen, colors.select("player", "song", "artist"));
        assertEquals(chosen, colors.select("player", "song", ""));
        assertEquals(chosen, colors.select("player", "", ""));
        assertEquals(chosen, colors.select("player", "song", "artist"));
    }
    @Test public void consecutiveSongsAndDifferentPublishersCannotRepeatThePreviousHue() {
        TrackColorSelector colors = new TrackColorSelector(new Random(1));
        int previous = colors.select("one", "song", "artist");
        int other = colors.select("two", "song", "artist");
        assertNotEquals(previous, other);
        for (int i = 0; i < 200; i++) {
            previous = other;
            other = colors.select("two", "song " + i, "artist");
            assertNotEquals(previous, other);
        }
    }
    @Test public void colorsRemainReadableAgainstBrightDarkAndColoredBases() {
        TrackColorSelector colors = new TrackColorSelector(new Random(9));
        for (int i = 0; i < 100; i++) {
            int hue = colors.select("player", "song " + i, "artist");
            for (int background : new int[]{0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF101A29, 0xFFFFCC22, 0xFF35A7B5}) {
                assertTrue(CoverLyricColorRules.contrast(CoverLyricColorRules.color(hue, background, true), background) >= 4.5);
                assertTrue(CoverLyricColorRules.contrast(CoverLyricColorRules.color(hue, background, false), background) >= 4.5);
            }
        }
    }
    @Test public void separatelyScopedSelectorsDoNotAdvanceEachOther() {
        TrackColorSelector main = new TrackColorSelector(new Random(1)), secondary = new TrackColorSelector(new Random(1));
        int first = secondary.select("player", "song", "artist");
        main.select("player", "song", "artist"); main.select("player", "next", "artist");
        assertEquals(first, secondary.select("player", "song", "artist"));
    }
}
