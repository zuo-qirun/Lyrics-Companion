package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuiltinMatchRulesTest {
    @Test public void exposesSingleBuiltinCleaningPack() {
        List<MatchRulePack> packs = BuiltinMatchRules.packs();
        assertEquals(1, packs.size());
        MatchRulePack pack = packs.get(0);
        assertEquals(BuiltinMatchRules.PACK_ID, pack.id);
        assertEquals(BuiltinMatchRules.PACK_VERSION, pack.version);
        assertEquals(1, pack.schemaVersion);
        assertEquals("内置清洗规则", pack.name);
        assertFalse(pack.rules.isEmpty());
    }

    @Test public void splitsCarTitleAndCarArtistForEchoPlayer() {
        MatchOutput output = apply("张学友 - 吻别", "车载音乐");
        assertTrue(output.changed);
        assertEquals("吻别", output.primaryTitle);
        assertEquals("张学友", output.primaryArtist);
        assertFalse(output.candidates.isEmpty());
    }

    @Test public void splitsTitleWhenArtistIsUnknownForEchoPlayer() {
        MatchOutput output = apply("张学友 - 吻别", "未知歌手");
        assertEquals("吻别", output.primaryTitle);
        assertEquals("张学友", output.primaryArtist);
    }

    @Test public void stripsTrailingNumberForBydPlayer() {
        MatchOutput output = apply("单身情歌（01）", "");
        assertEquals("单身情歌", output.primaryTitle);
        assertTrue(output.changed);
        assertFalse(output.candidates.isEmpty());
    }

    @Test public void splitsWhenArtistIsEmptyForDftcPlayer() {
        MatchOutput output = apply("刘德华 - 忘情水", "");
        assertEquals("忘情水", output.primaryTitle);
        assertEquals("刘德华", output.primaryArtist);
    }

    @Test public void normalizesCommonArtistAliasesForLxPlayer() {
        MatchOutput output = apply("千年之恋", "A-Lin/黄霄雲");
        assertEquals("千年之恋", output.primaryTitle);
        assertEquals("黄丽玲/黄霄云", output.primaryArtist);
        assertTrue(output.changed);
    }

    @Test public void normalizesGemAlias() {
        MatchOutput output = apply("泡沫", "G.E.M.");
        assertEquals("邓紫棋", output.primaryArtist);
    }

    @Test public void handlesFullWidthAndEmDashSeparators() {
        MatchOutput output = apply("王菲 — 红豆", "车载音乐");
        assertEquals("红豆", output.primaryTitle);
        assertEquals("王菲", output.primaryArtist);
    }

    @Test public void leavesOrdinaryTrackMetadataUnchanged() {
        MatchOutput output = apply("夜曲", "周杰伦");
        assertFalse(output.changed);
        assertTrue(output.candidates.isEmpty());
        assertEquals("夜曲", output.primaryTitle);
        assertEquals("周杰伦", output.primaryArtist);
    }

    private static MatchOutput apply(String title, String artist) {
        MatchInput input = new MatchInput(title, artist, "", "com.test.player", "media");
        return MatchRuleEngine.apply(BuiltinMatchRules.packs(), input);
    }
}
