package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the transient-hold rules that keep the Dongfeng vendor session from flapping. */
public class DftcMediaSessionReaderTest {
    @Test public void usableReadRequiresNonEmptyTitleAndKnownStatus() {
        assertTrue(DftcMediaSessionReader.isUsableRead("夜曲", 1));
        assertTrue(DftcMediaSessionReader.isUsableRead(" 夜曲 ", 0));
        assertFalse(DftcMediaSessionReader.isUsableRead("", 1));
        assertFalse(DftcMediaSessionReader.isUsableRead("   ", 0));
        assertFalse(DftcMediaSessionReader.isUsableRead("夜曲", -1));
        assertFalse(DftcMediaSessionReader.isUsableRead(null, 1));
    }

    @Test public void retainsSnapshotOnlyWithinHoldWindowWhileBinderAlive() {
        assertTrue(DftcMediaSessionReader.shouldReuseRetainedSnapshot("夜曲", true, 0L));
        assertTrue(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                " 夜曲 ", true, 14_999L));
        // The window is bounded so a player that truly exits is not shown forever.
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", true, 15_000L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", true, -1L));
    }

    @Test public void neverRetainsAcrossDeadBinderOrWithoutTitle() {
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", false, 0L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot("", true, 0L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(null, true, 0L));
    }
}
