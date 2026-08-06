package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ForegroundAppDetectorTest {
    @Test public void exactPlayerPackageMatchesForegroundPackage() {
        assertTrue(ForegroundAppDetector.samePackage(
                "com.netease.cloudmusic", "com.netease.cloudmusic"));
    }

    @Test public void differentOrEmptyPackagesDoNotMatch() {
        assertFalse(ForegroundAppDetector.samePackage(
                "com.netease.cloudmusic", "com.android.launcher"));
        assertFalse(ForegroundAppDetector.samePackage("", ""));
    }
}
