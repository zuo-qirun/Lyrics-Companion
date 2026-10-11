package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.*;
import org.junit.Test;

public class SessionMetadataRulesTest {
    @Test public void serviceLabelsAreNotSongsButRealTitlesRemainUsable() {
        assertFalse(SessionMetadataRules.usableTitle("网易云音乐服务", "网易云音乐"));
        assertFalse(SessionMetadataRules.usableTitle("网易云音乐正在运行", "网易云音乐"));
        assertFalse(SessionMetadataRules.usableTitle("Music Service", "Music"));
        assertFalse(SessionMetadataRules.usableTitle("Not Provided", "音乐"));
        assertTrue(SessionMetadataRules.usableTitle("服务", "网易云音乐"));
        assertTrue(SessionMetadataRules.usableTitle("网易云音乐的夏天", "网易云音乐"));
        assertTrue(SessionMetadataRules.usableTitle("Why Would I Ever", "酷狗音乐"));
        assertTrue(SessionMetadataRules.usableTitle("月亮照山川", ""));
    }

    @Test public void retainOnlyEstablishedSameSessionWithoutStopOrError() {
        assertTrue(SessionMetadataRules.retainMissingTitle(true, true, true, 0));
        assertFalse(SessionMetadataRules.retainMissingTitle(false, true, true, 0));
        assertFalse(SessionMetadataRules.retainMissingTitle(true, false, true, 0));
        assertFalse(SessionMetadataRules.retainMissingTitle(true, true, false, 0));
        assertFalse(SessionMetadataRules.retainMissingTitle(true, true, true, 1));
        assertFalse(SessionMetadataRules.retainMissingTitle(true, true, true, 7));
    }

    @Test public void persistentNotificationCanFallbackToRealSongText() {
        assertNull(NotificationTrackParser.parse("网易云音乐服务", "正在运行", "", "网易云音乐"));
        assertEquals("地下铁", NotificationTrackParser.parse(
                "网易云音乐服务", "地下铁", "萧亚轩", "网易云音乐").title);
    }

    @Test public void serviceOnlyDiagnosisDoesNotClaimMissingLyricMatch() {
        assertEquals(MediaDiagnosisRules.Verdict.SERVICE_ONLY, MediaDiagnosisRules.classify(
                false, false, false, false, false, "", 1, true));
        assertEquals(MediaDiagnosisRules.Verdict.MATCHED, MediaDiagnosisRules.classify(
                true, true, true, true, true, "QQ", 2, true));
    }
}
