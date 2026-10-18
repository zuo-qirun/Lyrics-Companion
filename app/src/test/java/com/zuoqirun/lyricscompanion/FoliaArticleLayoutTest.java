package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class FoliaArticleLayoutTest {
    private static final FoliaArticleLayout.Measure MEASURE = new FoliaArticleLayout.Measure() {
        @Override
        public int lineCount(String value, float fontPx, float innerWidth) {
            if (value == null || value.trim().isEmpty()) return 1;
            float width = 0f;
            for (int i = 0; i < value.length(); i++) {
                char code = value.charAt(i);
                boolean wide = code >= 0x4e00 && code <= 0x9fa5 || code >= 0x3040 && code <= 0x30ff;
                width += wide ? fontPx : fontPx * 0.5f;
            }
            return Math.max(1, (int) Math.ceil(width / Math.max(1f, innerWidth)));
        }
    };

    @Test public void articleKeepsTimelineIdentityAfterSeededPlacementShuffle() {
        List<LrcTimeline.Line> lines = Arrays.asList(new LrcTimeline.Line(0, 3000, "在无人的世界里"),
                new LrcTimeline.Line(3000, 3000, "听见风穿过树梢"), new LrcTimeline.Line(6000, 3000, "所有微光 汇成星河"),
                new LrcTimeline.Line(9000, 3000, "让这首歌陪你远行"));
        FoliaArticleLayout.Article a = FoliaArticleLayout.build(lines, 800, 600, 1, "Folia", MEASURE);
        FoliaArticleLayout.Article b = FoliaArticleLayout.build(lines, 800, 600, 1, "Folia", MEASURE);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(lines.size(), a.blocks.length);
        int heroes = 0;
        for (int i = 0; i < a.blocks.length; i++) {
            FoliaArticleLayout.Block block = a.blocks[i];
            assertNotNull(block);
            assertEquals(i, block.line);
            assertTrue(block.font > 0 && block.height > 0 && block.width > 0);
            assertEquals(a.blocks[i].x, b.blocks[i].x, 0);
            assertEquals(a.blocks[i].y, b.blocks[i].y, 0);
            if (block.hero) heroes++;
        }
        assertTrue(heroes >= 1);
    }

    @Test public void blankArticleDoesNotCreateDisplayBlocks() {
        FoliaArticleLayout.Article article = FoliaArticleLayout.build(
                Collections.singletonList(new LrcTimeline.Line(0, 3000, " ")), 320, 200, 1, "Folia", MEASURE);
        assertNull(article);
    }
}
