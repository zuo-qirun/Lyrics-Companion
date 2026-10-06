package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class TopLyricLayoutTest {
    @Test public void ultrawideStripCanBeTenPercentWithoutDpFloor() {
        assertEquals(256, TopLyricLayout.width(2560, 10, 0, 0));
        assertEquals(80, TopLyricLayout.width(800, 10, 0, 0));
        assertEquals(10, TopLyricLayout.regionPercent(-20));
        assertEquals(100, TopLyricLayout.regionPercent(150));
    }

    @Test public void marginsAndOffsetsKeepRegionOnScreen() {
        assertEquals(700, TopLyricLayout.width(1000, 100, 100, 200));
        assertEquals(100, TopLyricLayout.x(1000, 400, 100, 200, "left", -999));
        assertEquals(400, TopLyricLayout.x(1000, 400, 100, 200, "right", 999));
        assertEquals(250, TopLyricLayout.x(1000, 400, 100, 200, "center", 0));
        int width = TopLyricLayout.width(1000, 10, 2000, 2000);
        assertEquals(1, width);
        assertTrue(TopLyricLayout.x(1000, width, 2000, 2000, "center", 0) + width <= 1000);
    }

    @Test public void autoRowsAndStatusAlignmentUseAvailableContent() {
        assertFalse(TopLyricLayout.secondRow("single", false, true));
        assertFalse(TopLyricLayout.secondRow("auto", false, false));
        assertTrue(TopLyricLayout.secondRow("auto", false, true));
        assertTrue(TopLyricLayout.secondRow("double", false, false));
        assertFalse(TopLyricLayout.secondRow("double", true, true));
    }
}
