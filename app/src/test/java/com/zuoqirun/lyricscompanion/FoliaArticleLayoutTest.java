package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class FoliaArticleLayoutTest {
    @Test public void articleKeepsTimelineIdentityAfterSeededPlacementShuffle() {
        List<LrcTimeline.Line> lines = Arrays.asList(new LrcTimeline.Line(0, 3000, "在无人的世界里"),
                new LrcTimeline.Line(3000, 3000, "听见风穿过树梢"), new LrcTimeline.Line(6000, 3000, "所有微光 汇成星河"),
                new LrcTimeline.Line(9000, 3000, "让这首歌陪你远行"));
        float[] widths = {7, 7, 8.3f, 8};
        FoliaArticleLayout.Block[] a = FoliaArticleLayout.build(lines, 800, 600, 1, "Folia", widths);
        FoliaArticleLayout.Block[] b = FoliaArticleLayout.build(lines, 800, 600, 1, "Folia", widths);
        assertEquals(lines.size(), a.length);
        int heroes = 0;
        for (int i = 0; i < a.length; i++) {
            assertEquals(i, a[i].line);
            assertTrue(a[i].font > 0 && a[i].height > 0 && a[i].width > 0);
            assertEquals(a[i].x, b[i].x, 0);
            assertEquals(a[i].y, b[i].y, 0);
            if (a[i].hero) heroes++;
        }
        assertTrue(heroes >= 1);
    }

    @Test public void blankArticleDoesNotCreateDisplayBlocks() {
        FoliaArticleLayout.Block[] blocks = FoliaArticleLayout.build(
                Collections.singletonList(new LrcTimeline.Line(0, 3000, " ")), 320, 200, 1, "Folia", new float[]{0.3f});
        assertEquals(1, blocks.length);
        assertNull(blocks[0]);
    }
}
