package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 歌手字号与歌名字号的关系（issue #63）。 */
public class MetadataTypeScaleMathTest {
    @Test public void unsetArtistPercentKeepsTheLegacyRatio() {
        assertEquals(14.4f, MetadataTypeScaleMath.artistSize(20f, 0.72f,
                MetadataTypeScaleMath.UNSET), 0.001f);
        assertEquals(14.4f, MetadataTypeScaleMath.artistSize(20f, 0.72f, -5), 0.001f);
    }

    @Test public void hundredPercentMatchesTheTitleSize() {
        assertEquals(20f, MetadataTypeScaleMath.artistSize(20f, 0.72f, 100), 0.001f);
        assertEquals(40f, MetadataTypeScaleMath.artistSize(20f, 0.42f, 200), 0.001f);
    }

    @Test public void zeroPercentIsNotUnset() {
        // 0% 与「没调过」是两回事：前者真的把歌手缩到 0（滑杆最左端），后者沿用样式比例。
        assertEquals(0f, MetadataTypeScaleMath.artistSize(20f, 0.72f, 0), 0.001f);
        assertTrue(MetadataTypeScaleMath.artistSize(20f, 0.72f, 0)
                < MetadataTypeScaleMath.artistSize(20f, 0.72f, MetadataTypeScaleMath.UNSET));
    }

    @Test public void legacyRatiosMatchTheShippedStyles() {
        // 紧凑 8.5 / 10.5；AMLL 0.72；Refined 面板 0.42；Refined 全屏没有固定比例
        assertEquals(8.5f / 10.5f, MetadataTypeScaleMath.legacyArtistRatio("compact", false),
                0.0001f);
        assertEquals(0.72f, MetadataTypeScaleMath.legacyArtistRatio("amll", false), 0.0001f);
        assertEquals(0.42f, MetadataTypeScaleMath.legacyArtistRatio("refined", false), 0.0001f);
        assertEquals(MetadataTypeScaleMath.UNSET,
                MetadataTypeScaleMath.legacyArtistRatio("refined", true), 0.0001f);
        assertEquals(MetadataTypeScaleMath.UNSET,
                MetadataTypeScaleMath.legacyArtistRatio("default", false), 0.0001f);
    }

    @Test public void percentIsClampedToSomethingUsable() {
        assertEquals(MetadataTypeScaleMath.UNSET, MetadataTypeScaleMath.normalizePercent(-1));
        assertEquals(0, MetadataTypeScaleMath.normalizePercent(0));
        assertEquals(120, MetadataTypeScaleMath.normalizePercent(120));
        assertEquals(MetadataTypeScaleMath.MAX_PERCENT,
                MetadataTypeScaleMath.normalizePercent(9_999));
        // 上限之外按上限算，不放大到离谱（10dp 歌名 × 200% = 20dp 歌手）
        assertEquals(20f, MetadataTypeScaleMath.artistSize(10f, 0.5f, 9_999), 0.001f);
    }
}
