package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class TopLyricColorEnvironmentTest {
    @Test public void externalDarkWallpaperCanSelectLightTextWithoutChangingTheAppTheme() {
        assertEquals(0xFFF3F7FC, LyricsPanelView.topLyricOverrideColor("dark"));
        assertEquals(0xFF101A29, LyricsPanelView.topLyricOverrideColor("light"));
        assertEquals(0, LyricsPanelView.topLyricOverrideColor("auto"));
        assertEquals(0, LyricsPanelView.topLyricOverrideColor("unknown"));
    }
}
