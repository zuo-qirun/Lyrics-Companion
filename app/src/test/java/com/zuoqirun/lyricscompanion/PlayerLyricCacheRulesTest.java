package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure-JVM tests for the opt-in player lyric cache rules (issue #154). */
public final class PlayerLyricCacheRulesTest {
    private static final String TITLE = "晴天";
    private static final String ARTIST = "周杰伦";

    private static int score(String fileName) {
        return PlayerLyricCacheRules.scoreFileName(fileName, TITLE, ARTIST, "");
    }

    @Test
    public void matchesTitleAndArtistInEitherOrder() {
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("晴天 - 周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("周杰伦 - 晴天.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("晴天-周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("晴天_周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("周杰伦－晴天.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED, score("晴天 – 周杰伦.lrc"));
    }

    @Test
    public void matchesTitleOnlyAndIgnoresDecorations() {
        assertEquals(PlayerLyricCacheRules.SCORE_TITLE, score("晴天.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_TITLE, score("《晴天》.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_TITLE, score("晴天.KRC"));
        // (Live) 之类的括号后缀去掉后仍能匹配，但分数降一档。
        assertEquals(80, score("晴天 (Live) - 周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_CLEANED_TITLE, score("晴天 (Live).lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_CLEANED_TITLE, score("03 晴天.lrc"));
    }

    @Test
    public void matchesAcrossFullWidthAndSpecialCharacters() {
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED,
                PlayerLyricCacheRules.scoreFileName("ABC - DEF.lrc", "ＡＢＣ", "ＤＥＦ", ""));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED,
                PlayerLyricCacheRules.scoreFileName("A+B - C&D.lrc", "A+B", "C&D", ""));
        assertEquals(PlayerLyricCacheRules.SCORE_COMBINED,
                PlayerLyricCacheRules.scoreFileName("晴天《》 - 周杰伦.lrc", "晴天", "周杰伦", ""));
    }

    @Test
    public void doesNotMatchDifferentSongArtistOrFormat() {
        assertEquals(PlayerLyricCacheRules.SCORE_NONE, score("夜曲 - 周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE, score("晴天 - 陈奕迅.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE, score("周杰伦.lrc"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE, score("晴天.mp3"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE, score(null));
    }

    @Test
    public void matchesHalfNameWhenArtistMetadataIsMissing() {
        assertEquals(PlayerLyricCacheRules.SCORE_TITLE_PARTIAL,
                PlayerLyricCacheRules.scoreFileName("晴天 - 周杰伦.lrc", TITLE, "", ""));
        assertEquals(PlayerLyricCacheRules.SCORE_TITLE,
                PlayerLyricCacheRules.scoreFileName("晴天.lrc", TITLE, "", ""));
    }

    @Test
    public void matchesPlayerTrackIdOnlyForUsableIds() {
        assertEquals(PlayerLyricCacheRules.SCORE_TRACK_ID,
                PlayerLyricCacheRules.scoreFileName("186016.lrc", TITLE, ARTIST, "186016"));
        assertEquals(PlayerLyricCacheRules.SCORE_TRACK_ID,
                PlayerLyricCacheRules.scoreFileName("003OUlho2HcRHC.krc", TITLE, ARTIST,
                        "003OUlho2HcRHC"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE,
                PlayerLyricCacheRules.scoreFileName("186016.lrc", TITLE, ARTIST, "123456"));
        // 纯字母 ID 不可信（可能就是歌名），不参与匹配。
        assertEquals(PlayerLyricCacheRules.SCORE_NONE,
                PlayerLyricCacheRules.scoreFileName("abc.lrc", TITLE, ARTIST, "abc"));
        assertEquals(PlayerLyricCacheRules.SCORE_NONE,
                PlayerLyricCacheRules.scoreFileName("晴天.lrc", "", "", "186016"));
        assertTrue(PlayerLyricCacheRules.isUsableTrackId("186016"));
        assertFalse(PlayerLyricCacheRules.isUsableTrackId("abc"));
        assertFalse(PlayerLyricCacheRules.isUsableTrackId(""));
        assertFalse(PlayerLyricCacheRules.isUsableTrackId(null));
    }

    @Test
    public void doesNotGuessWhenTitleIsMissing() {
        assertEquals(PlayerLyricCacheRules.SCORE_NONE,
                PlayerLyricCacheRules.scoreFileName("晴天 - 周杰伦.lrc", "", ARTIST, ""));
    }

    @Test
    public void recognisesLyricFilesOnly() {
        assertTrue(PlayerLyricCacheRules.isLyricFile("晴天.lrc"));
        assertTrue(PlayerLyricCacheRules.isLyricFile("晴天.LRC"));
        assertTrue(PlayerLyricCacheRules.isLyricFile("晴天.krc"));
        assertTrue(PlayerLyricCacheRules.isLyricFile("晴天.KRC"));
        assertFalse(PlayerLyricCacheRules.isLyricFile("晴天.mp3"));
        assertFalse(PlayerLyricCacheRules.isLyricFile("lrc"));
        assertFalse(PlayerLyricCacheRules.isLyricFile(null));
    }

    @Test
    public void candidateDirectoriesCoverTheFivePlayersAndPublicFolders() {
        List<String> directories = PlayerLyricCacheRules.candidateDirectories(
                Collections.singletonList("/storage/emulated/0"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/data/com.netease.cloudmusic/files"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/data/com.tencent.qqmusic/files"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/data/com.kugou.android/files"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/data/cn.kuwo.player/files"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/data/com.luna.music/files"));
        assertTrue(directories.contains(
                "/storage/emulated/0/Android/media/com.netease.cloudmusic"));
        assertTrue(directories.contains("/storage/emulated/0/网易云音乐"));
        assertTrue(directories.contains("/storage/emulated/0/QQMusic/Lyric"));
        assertTrue(directories.contains("/storage/emulated/0/kugou/lyric"));
        assertTrue(directories.contains("/storage/emulated/0/kuwo/lyric"));
        assertTrue(directories.contains("/storage/emulated/0/汽水音乐"));
        assertTrue(directories.contains("/storage/emulated/0/Download/Lyrics"));
        assertTrue(directories.contains("/storage/emulated/0/Music/Lyrics"));
        assertEquals("候选目录里不应有重复项",
                directories.size(), new LinkedHashSet<>(directories).size());
        assertTrue(directories.size() <= PlayerLyricCacheRules.MAX_CANDIDATE_DIRECTORIES);
        assertTrue(directories.size() >= 40);
    }

    @Test
    public void candidateDirectoriesDeduplicateRootsAndSkipBlankOnes() {
        List<String> single = PlayerLyricCacheRules.candidateDirectories(
                Collections.singletonList("/storage/emulated/0"));
        List<String> withVariants = PlayerLyricCacheRules.candidateDirectories(Arrays.asList(
                "/storage/emulated/0", "/storage/emulated/0/", "\\storage\\emulated\\0", "", null));
        assertEquals(single, withVariants);
        assertTrue(PlayerLyricCacheRules.candidateDirectories(null).isEmpty());
        assertTrue(PlayerLyricCacheRules.candidateDirectories(
                Collections.<String>emptyList()).isEmpty());
    }

    @Test
    public void candidateDirectoriesRespectTheCapAcrossStorageVolumes() {
        List<String> directories = PlayerLyricCacheRules.candidateDirectories(Arrays.asList(
                "/storage/emulated/0", "/storage/1234-5678"));
        assertTrue(directories.size() <= PlayerLyricCacheRules.MAX_CANDIDATE_DIRECTORIES);
        assertNotNull(directories);
    }

    @Test
    public void budgetStopsAtConfiguredLimits() {
        PlayerLyricCacheRules.Budget budget = new PlayerLyricCacheRules.Budget(2, 3);
        assertTrue(budget.takeDirectory());
        assertTrue(budget.takeDirectory());
        assertFalse(budget.takeDirectory());
        assertTrue(budget.directoriesExhausted());
        assertEquals(2, budget.directories());
        assertTrue(budget.takeFile());
        assertTrue(budget.takeFile());
        assertTrue(budget.takeFile());
        assertFalse(budget.takeFile());
        assertTrue(budget.filesExhausted());
        assertEquals(3, budget.files());
    }

    @Test
    public void emptyAndNegativeBudgetsAllowNoWork() {
        PlayerLyricCacheRules.Budget empty = new PlayerLyricCacheRules.Budget();
        assertTrue(empty.takeDirectory());
        assertTrue(empty.takeFile());

        PlayerLyricCacheRules.Budget zero = new PlayerLyricCacheRules.Budget(0, 0);
        assertFalse(zero.takeDirectory());
        assertFalse(zero.takeFile());
        assertTrue(zero.directoriesExhausted());
        assertTrue(zero.filesExhausted());

        PlayerLyricCacheRules.Budget negative = new PlayerLyricCacheRules.Budget(-5, -5);
        assertFalse(negative.takeDirectory());
        assertFalse(negative.takeFile());
        assertEquals(0, negative.maxDirectories());
        assertEquals(0, negative.maxFiles());
    }

    @Test
    public void defaultBudgetsStaySmallEnoughForOneProbe() {
        PlayerLyricCacheRules.Budget budgets = new PlayerLyricCacheRules.Budget();
        assertEquals(PlayerLyricCacheRules.MAX_DIRECTORIES, budgets.maxDirectories());
        assertEquals(PlayerLyricCacheRules.MAX_FILES, budgets.maxFiles());
        assertTrue(PlayerLyricCacheRules.MAX_DIRECTORIES > 0);
        assertTrue(PlayerLyricCacheRules.MAX_FILES >= PlayerLyricCacheRules.MAX_FILES_PER_DIRECTORY);
        assertTrue(PlayerLyricCacheRules.MAX_CANDIDATES > 0
                && PlayerLyricCacheRules.MAX_CANDIDATES <= 10);
        assertTrue(PlayerLyricCacheRules.MAX_DEPTH >= 0
                && PlayerLyricCacheRules.MAX_DEPTH <= 2);
    }
}
