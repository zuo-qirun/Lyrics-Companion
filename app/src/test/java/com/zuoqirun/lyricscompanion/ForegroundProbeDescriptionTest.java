package com.zuoqirun.lyricscompanion;

import android.app.usage.UsageEvents;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 前台读取失败时的诊断文案（issue #61）：要说清「没授权 / 没事件 / 调用失败」。 */
public class ForegroundProbeDescriptionTest {
    @Test public void namesTheEmptyQueryReason() {
        String text = ForegroundProbeDescription.describe(
                ForegroundAppDetector.REASON_NO_EVENTS, "", 0, -1L);
        assertTrue(text, text.contains("queryEvents"));
        assertTrue(text, text.contains("空"));
    }

    @Test public void reportsPackageEventAndAge() {
        String text = ForegroundProbeDescription.describe(ForegroundAppDetector.REASON_OK,
                "com.byd.mediacenter", UsageEvents.Event.MOVE_TO_FOREGROUND, 4_200L);
        assertTrue(text, text.contains("com.byd.mediacenter"));
        assertTrue(text, text.contains("FOREGROUND"));
        assertTrue(text, text.contains("4s"));
    }

    @Test public void missingPermissionIsItsOwnSentence() {
        String text = ForegroundProbeDescription.describe(
                ForegroundAppDetector.REASON_USAGE_ACCESS_MISSING,
                "com.old.package", 0, 90_000L);
        assertTrue(text, text.contains("使用情况访问"));
        assertTrue("旧值也要报出来", text.contains("com.old.package"));
    }

    @Test public void ruleImpactTellsWhitelistUsersTheLyricsWillStayHidden() {
        String whitelist = ForegroundProbeDescription.ruleImpact(
                ForegroundAppDetector.REASON_NO_EVENTS, true);
        assertTrue(whitelist, whitelist.contains("白名单"));
        String blacklist = ForegroundProbeDescription.ruleImpact(
                ForegroundAppDetector.REASON_NO_EVENTS, false);
        assertTrue(blacklist, blacklist.contains("黑名单"));
        assertEquals("", ForegroundProbeDescription.ruleImpact(
                ForegroundAppDetector.REASON_OK, true));
    }

    @Test public void eventNamesAreStable() {
        assertEquals("MOVE_TO_FOREGROUND",
                ForegroundProbeDescription.eventName(UsageEvents.Event.MOVE_TO_FOREGROUND));
        assertEquals("MOVE_TO_BACKGROUND",
                ForegroundProbeDescription.eventName(UsageEvents.Event.MOVE_TO_BACKGROUND));
        assertEquals("无", ForegroundProbeDescription.eventName(0));
        assertEquals("无", ForegroundProbeDescription.eventName(-1));
    }
}
