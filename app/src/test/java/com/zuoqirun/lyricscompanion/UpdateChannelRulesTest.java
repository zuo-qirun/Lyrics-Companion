package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the update-channel decisions behind the beta channel: which manifest a channel reads,
 * when a skipped version stays quiet, and what happens when the installed build is newer than the
 * channel it is looking at (a beta user switching back to stable).
 */
public class UpdateChannelRulesTest {

    @Test public void unknownChannelFallsBackToStable() {
        assertEquals(UpdateChannelRules.CHANNEL_STABLE, UpdateChannelRules.normalize(null));
        assertEquals(UpdateChannelRules.CHANNEL_STABLE, UpdateChannelRules.normalize(""));
        assertEquals(UpdateChannelRules.CHANNEL_STABLE, UpdateChannelRules.normalize("nightly"));
        assertEquals(UpdateChannelRules.CHANNEL_BETA, UpdateChannelRules.normalize("beta"));
        assertFalse(UpdateChannelRules.isBeta(null));
        assertTrue(UpdateChannelRules.isBeta(UpdateChannelRules.CHANNEL_BETA));
        assertEquals("测试版", UpdateChannelRules.displayName("beta"));
        assertEquals("正式版", UpdateChannelRules.displayName("stable"));
    }

    @Test public void eachChannelReadsItsOwnManifest() {
        assertEquals("https://example.test/update.json",
                UpdateChannelRules.manifestUrl("stable",
                        "https://example.test/update.json", "https://example.test/update-beta.json"));
        assertEquals("https://example.test/update-beta.json",
                UpdateChannelRules.manifestUrl("beta",
                        "https://example.test/update.json", "https://example.test/update-beta.json"));
    }

    @Test public void aSkippedVersionStaysQuietOnAutomaticChecks() {
        // 用户对 200 选了"不再提醒"：同一个 200 的自动检查不再弹窗。
        assertTrue(UpdateChannelRules.skipPrompt(false, false, 200, 200));
        // 比跳过记录更旧的版本同样不再弹。
        assertTrue(UpdateChannelRules.skipPrompt(false, false, 199, 200));
        // 更高的版本照常提示。
        assertFalse(UpdateChannelRules.skipPrompt(false, false, 201, 200));
    }

    @Test public void manualChecksAndForcedUpdatesAlwaysPrompt() {
        assertFalse(UpdateChannelRules.skipPrompt(true, false, 200, 200));
        assertFalse(UpdateChannelRules.skipPrompt(false, true, 200, 200));
        assertFalse(UpdateChannelRules.skipPrompt(false, false, 200, 0));
        assertFalse(UpdateChannelRules.skipPrompt(false, false, 0, 200));
    }

    @Test public void anInstalledBetaNewerThanStableIsReported() {
        // 测试版 200 装在车上，正式版最新只有 150 —— 不能说"已是最新"。
        assertTrue(UpdateChannelRules.localIsAhead(200, 150));
        assertFalse(UpdateChannelRules.localIsAhead(150, 200));
        assertFalse(UpdateChannelRules.localIsAhead(200, 200));
        // 通道还没有可用版本（beta 占位清单 versionCode = 0）时不算"本地超前"。
        assertFalse(UpdateChannelRules.localIsAhead(200, 0));
    }
}
