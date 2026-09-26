package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Issue #17: several screens can show lyrics at once, and each one owns the slot its parameters
 * are stored under. These tests pin the two properties the rest of the app relies on — a slot
 * keeps its number while other entries come and go, and the stored form survives a round trip.
 */
public class DisplaySlotRegistryTest {
    /** issue #71：两块物理屏同名时，名字没有区分能力，按 id 认。 */
    @Test public void ambiguousNameFallsBackToDisplayId() {
        String[] names = {"HDMI 屏幕", "HDMI 屏幕"};
        int[] ids = {1, 2};
        assertEquals(1, DisplaySlotRegistry.chooseIndex("HDMI 屏幕", 2, names, ids));
        assertEquals(0, DisplaySlotRegistry.chooseIndex("HDMI 屏幕", 1, names, ids));
        // id 也不在时退回第一块同名的，行为与改动前一致
        assertEquals(0, DisplaySlotRegistry.chooseIndex("HDMI 屏幕", 9, names, ids));
    }

    @Test public void uniqueNameStillWinsOverAStaleId() {
        // 车机 HUD 的 id 重启后会变，所以名字唯一时仍然按名字（老行为不变）
        assertEquals(0, DisplaySlotRegistry.chooseIndex("HUD", 7, new String[]{"HUD"}, new int[]{3}));
    }

    @Test public void unknownNameFallsBackToId() {
        assertEquals(0, DisplaySlotRegistry.chooseIndex("HUD", 2,
                new String[]{"HDMI 屏幕"}, new int[]{2}));
        assertEquals(-1, DisplaySlotRegistry.chooseIndex("HUD", 5,
                new String[]{"HDMI 屏幕"}, new int[]{2}));
        assertEquals(-1, DisplaySlotRegistry.chooseIndex("HUD", 5, new String[0], new int[0]));
    }

    @Test public void firstExtraScreenIsSlotTwo() {
        assertEquals(DisplaySlotRegistry.MAIN_SLOT, 0);
        assertEquals(DisplaySlotRegistry.SECONDARY_SLOT, 1);
        assertEquals(2, DisplaySlotRegistry.FIRST_EXTRA_SLOT);
        assertEquals(2, DisplaySlotRegistry.slotFor(0));
        assertEquals(3, DisplaySlotRegistry.slotFor(1));
        assertEquals(5, DisplaySlotRegistry.slotFor(3));
    }

    @Test public void entriesSurviveARoundTrip() {
        List<DisplaySlotRegistry.Entry> entries = Arrays.asList(
                new DisplaySlotRegistry.Entry("HDMI Screen", 12),
                new DisplaySlotRegistry.Entry("HUD", 7, false));
        List<DisplaySlotRegistry.Entry> decoded =
                DisplaySlotRegistry.decode(DisplaySlotRegistry.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals("HDMI Screen", decoded.get(0).name);
        assertEquals(12, decoded.get(0).displayId);
        assertTrue(decoded.get(0).enabled);
        assertEquals("HUD", decoded.get(1).name);
        assertEquals(7, decoded.get(1).displayId);
        assertFalse("a switched off screen stays switched off", decoded.get(1).enabled);
    }

    @Test public void switchingAScreenOffKeepsTheSlotOfTheNextOne() {
        List<DisplaySlotRegistry.Entry> entries = new ArrayList<>(Arrays.asList(
                new DisplaySlotRegistry.Entry("Driving", 3),
                new DisplaySlotRegistry.Entry("HUD", 7)));
        // The driver turns the first screen off; the second must keep slot 3, not inherit slot 2.
        entries.set(0, entries.get(0).withEnabled(false));
        List<DisplaySlotRegistry.Entry> decoded =
                DisplaySlotRegistry.decode(DisplaySlotRegistry.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals(DisplaySlotRegistry.slotFor(0), 2);
        assertEquals(DisplaySlotRegistry.slotFor(1), 3);
        assertEquals("HUD", decoded.get(1).name);
    }

    @Test public void entriesWrittenBeforeSwitchingExistedStayEnabled() {
        List<DisplaySlotRegistry.Entry> decoded = DisplaySlotRegistry.decode("HDMI Screen\u000112");
        assertEquals(1, decoded.size());
        assertEquals("HDMI Screen", decoded.get(0).name);
        assertEquals(12, decoded.get(0).displayId);
        assertTrue(decoded.get(0).enabled);
    }

    @Test public void emptyOrUnreadableListsDecodeToNothing() {
        assertTrue(DisplaySlotRegistry.decode("").isEmpty());
        assertTrue(DisplaySlotRegistry.decode(null).isEmpty());
        assertTrue(DisplaySlotRegistry.decode("   ").isEmpty());
        assertNull(DisplaySlotRegistry.of(null));
    }

    @Test public void eachSlotGetsItsOwnPreferenceFile() {
        assertEquals("lyrics_companion_display2", DisplaySlotContext.fileName(2));
        assertEquals("lyrics_companion_display3", DisplaySlotContext.fileName(3));
        // A slot number below the first extra one can never name a file of its own.
        assertEquals(2, DisplaySlotContext.normalizeSlot(2));
        assertEquals(2, DisplaySlotContext.normalizeSlot(0));
    }

    @Test public void aSettingsPageWithoutASlotExtraFallsBackToMainOrSecondary() {
        assertEquals(DisplaySlotRegistry.MAIN_SLOT, DisplaySlotContext.slotFrom(null, false));
        assertEquals(DisplaySlotRegistry.SECONDARY_SLOT, DisplaySlotContext.slotFrom(null, true));
    }
}
