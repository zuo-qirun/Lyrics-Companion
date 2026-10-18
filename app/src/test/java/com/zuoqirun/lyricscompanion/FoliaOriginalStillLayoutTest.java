package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class FoliaOriginalStillLayoutTest {
    private static List<LrcTimeline.Line> lines() {
        return Arrays.asList(new LrcTimeline.Line(0, 3000, "听见风穿过树梢", "The light stays with you", Collections.emptyList()),
                new LrcTimeline.Line(3000, 3000, "所有微光 汇成星河", "The light stays with you", Collections.emptyList()),
                new LrcTimeline.Line(6000, 3000, "让这首歌陪你远行", "The light stays with you", Collections.emptyList()));
    }

    @Test public void translatedRowsMatchMeasuredUpstreamCssBoxes() {
        // Chromium upstream fixture at 800x600: boxes y/h = 117/36, 221/50, 347/36.
        assertArrayEquals(new float[]{135, 246, 365},
                FoliaOriginalStillLayout.lyricCenters(lines(), 1, 600, true), 0);
    }

    @Test public void translationToggleRemovesOnlyItsOwnFlexHeight() {
        // Chromium upstream with hidden translation: boxes y/h = 175/36, 243/50, 325/36.
        assertArrayEquals(new float[]{193, 268, 343},
                FoliaOriginalStillLayout.lyricCenters(lines(), 1, 600, false), 0);
    }
}
