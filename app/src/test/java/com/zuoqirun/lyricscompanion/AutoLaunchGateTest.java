package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * issue #152：打开指定播放器时自动拉起歌词的前台去重规则。纯逻辑测试，不需要 Android 运行时。
 */
public final class AutoLaunchGateTest {
    private static final String TARGET = "com.netease.cloudmusic";
    private static final String OTHER = "com.android.launcher";

    @Test public void triggersOnceAfterForegroundIsStable() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        // 刚切到目标播放器的第一轮只记录，避免一闪而过的前台误触发。
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 1_000L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 1_500L));
        // 同一段前台只拉起一次。
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 2_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 60_000L));
    }

    @Test public void disabledSwitchOrEmptyTargetNeverTriggers() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(false, TARGET, TARGET, false, false, 1_000L));
        assertFalse(gate.shouldTrigger(false, TARGET, TARGET, false, false, 2_000L));
        ForegroundAppDetector.AutoLaunchGate empty = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(empty.shouldTrigger(true, "", TARGET, false, false, 1_000L));
        assertFalse(empty.shouldTrigger(true, "", TARGET, false, false, 2_000L));
    }

    @Test public void settingsPageBlocksTriggerButKeepsSession() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 1_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, true, false, 1_500L));
        // 关掉设置页、仍在同一段前台时，应该补上这次自动拉起。
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 2_000L));
    }

    @Test public void visibleLyricLayerConsumesTheForegroundSession() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 1_000L));
        // 歌词层已经显示（例如上一次自动拉起留下的、或用户自己开的）：本段前台算作已拉起。
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, true, 1_500L));
        // 用户随后手动关掉歌词：同一段前台不再自动弹出。
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 2_000L));
        // 离开播放器再回来（新的前台会话）才会再次自动拉起。
        assertFalse(gate.shouldTrigger(true, TARGET, OTHER, false, false, 3_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 3_500L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 4_000L));
    }

    @Test public void newForegroundSessionRespectsMinimumInterval() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 0L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 100L));
        // 30 秒内切走再切回来，不重复拉起。
        assertFalse(gate.shouldTrigger(true, TARGET, OTHER, false, false, 1_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 2_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 10_000L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 30_099L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 30_100L));
    }

    @Test public void changingTargetPlayerResetsTheSession() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 0L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 100L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 200L));
        String otherPlayer = "com.tencent.qqmusic";
        // 换目标播放器：旧会话失效。
        assertFalse(gate.shouldTrigger(true, otherPlayer, TARGET, false, false, 1_000L));
        // 新播放器进入前台的第一轮只记录，下一轮就允许拉起（换目标不受旧的最小间隔影响）。
        assertFalse(gate.shouldTrigger(true, otherPlayer, otherPlayer, false, false, 1_500L));
        assertTrue(gate.shouldTrigger(true, otherPlayer, otherPlayer, false, false, 2_000L));
    }

    @Test public void turningTheSwitchOffAndOnAgainResetsTheSession() {
        ForegroundAppDetector.AutoLaunchGate gate = new ForegroundAppDetector.AutoLaunchGate();
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 0L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 100L));
        assertFalse(gate.shouldTrigger(false, TARGET, TARGET, false, false, 200L));
        // 关掉再打开会重新允许触发，但最小间隔仍然生效，避免来回切换开关刷屏。
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 300L));
        assertFalse(gate.shouldTrigger(true, TARGET, TARGET, false, false, 30_000L));
        assertTrue(gate.shouldTrigger(true, TARGET, TARGET, false, false, 30_100L));
    }
}
