package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public final class FoliaEffectSelectorTest {
    @Test public void poolFiltersUnknownDuplicatesAndDefaultsToAllFourteen() {
        assertArrayEquals(new String[]{"claddagh", "tilt"}, FoliaEffectSelector.pool("claddagh,unknown, tilt ,claddagh"));
        assertArrayEquals(FoliaModeCatalog.VALUES, FoliaEffectSelector.pool("invalid"));
        assertEquals(14, FoliaEffectSelector.pool(null).length);
    }

    @Test public void firstDrawCanChooseAnyPoolMemberIncludingDefault() {
        FoliaEffectSelector selector = new FoliaEffectSelector(new Random() {
            @Override public int nextInt(int bound) { return 0; }
        });
        assertEquals("claddagh", selector.select("first", true, "tilt", "claddagh,tilt"));
        assertEquals("tilt", selector.select("second", true, "tilt", "claddagh,tilt"));
    }

    @Test public void repeatedTrackKeepsSelectionAndDifferentTracksDoNotRepeat() {
        FoliaEffectSelector selector = new FoliaEffectSelector(new Random(7));
        String selected = selector.select("song", true, "still", "");
        for (int i = 0; i < 100; i++) assertEquals(selected, selector.select("song", true, "tilt", ""));
        for (int i = 0; i < 100; i++) {
            String next = selector.select("song" + i, true, "still", "");
            assertNotEquals(selected, next);
            selected = next;
        }
    }

    @Test public void fillingOrTemporarilyLosingArtistDoesNotCountAsChangingTracks() {
        FoliaEffectSelector selector = new FoliaEffectSelector(new Random(4));
        String selected = selector.selectTrack("player", "song", "", true, "still", "claddagh,tilt");
        assertEquals(selected, selector.selectTrack("player", "song", "artist", true, "still", "claddagh,tilt"));
        assertEquals(selected, selector.selectTrack("player", "song", "", true, "still", "claddagh,tilt"));
        assertEquals(selected, selector.selectTrack("player", "", "", true, "still", "claddagh,tilt"));
        assertNotEquals(selected, selector.selectTrack("player", "song", "different", true, "still", "claddagh,tilt"));
    }

    @Test public void singleChoiceAndPoolChangesAreRespectedWithoutChangingSong() {
        FoliaEffectSelector selector = new FoliaEffectSelector(new Random(0));
        assertEquals("tilt", selector.select("song", true, "still", "tilt"));
        assertEquals("tilt", selector.select("next", true, "still", "tilt"));
        assertEquals("still", selector.select("next", true, "tilt", "still"));
        assertEquals("claddagh", selector.select("next", false, "claddagh", "still"));
    }

    @Test public void screenSelectorsHaveIndependentTrackAndConfigurationState() {
        FoliaEffectSelector main = new FoliaEffectSelector(new Random(1));
        FoliaEffectSelector secondary = new FoliaEffectSelector(new Random(2));
        assertEquals("tilt", main.select("song", true, "still", "tilt"));
        assertEquals("still", secondary.select("song", true, "tilt", "still"));
        main.select("other", true, "still", "claddagh");
        assertEquals("still", secondary.select("song", true, "tilt", "still"));
    }
}
