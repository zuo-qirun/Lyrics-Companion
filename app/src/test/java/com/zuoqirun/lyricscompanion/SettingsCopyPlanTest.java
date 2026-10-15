package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SettingsCopyPlanTest {
    @Test public void categorySelectionCopiesOnlyExplicitSourceStyleValues() {
        Map<String, Object> source = new HashMap<>();
        source.put("text_scale_main_default", 130);
        source.put("opacity_main_default", 80);
        source.put("lyric_color_main_default", 0xFF123456);
        source.put("artist_scale_main_compact", 90);
        source.put("title_scale_main", 160); // inherited screen default must remain inherited
        source.put("playback_auto_fade", true);
        Map<String, Object> before = new HashMap<>(source);
        Map<String, Object> plan = SettingsCopyPlan.create(source, false, "default", true, "compact",
                EnumSet.of(SettingsCatalog.Category.TEXT));
        assertEquals(1, plan.size());
        assertEquals(130, plan.get("text_scale_secondary_compact"));
        assertEquals(before, source);
    }

    @Test public void unsupportedTargetRowsAreNotWritten() {
        Map<String, Object> source = new HashMap<>();
        source.put("style_lyric_lines_main_default", 7);
        source.put("text_scale_main_default", 150);
        Map<String, Object> compact = SettingsCopyPlan.create(source, false, "default", true, "compact",
                EnumSet.allOf(SettingsCatalog.Category.class));
        assertEquals(1, compact.size());
        Map<String, Object> pure = SettingsCopyPlan.create(source, false, "default", true, "pure",
                EnumSet.allOf(SettingsCatalog.Category.class));
        assertEquals(2, pure.size());
        assertEquals(7, pure.get("style_lyric_lines_secondary_pure"));
    }

    @Test public void foliaDimensionsAndGlobalBehavioursNeverEnterAppearanceCopy() {
        Map<String, Object> source = new HashMap<>();
        source.put("folia_panel_width_dp_secondary_folia", 650);
        source.put("folia_panel_height_dp_secondary_folia", 250);
        source.put("folia_strength_secondary_folia", 120);
        source.put("lyric_offset_secondary", 2000);
        source.put("overlay_position_locked_secondary", true);
        Map<String, Object> plan = SettingsCopyPlan.create(source, true, "folia", false, "folia",
                EnumSet.allOf(SettingsCatalog.Category.class));
        assertEquals(1, plan.size());
        assertEquals(120, plan.get("folia_strength_main_folia"));
    }

    @Test public void additionalScreenSnapshotsCannotReadOtherScreensOrStyles() {
        Map<String, Object> extraScreen = new HashMap<>();
        extraScreen.put("text_scale_secondary_compact", 145);
        extraScreen.put("text_scale_main_compact", 190);
        extraScreen.put("text_scale_secondary_refined", 200);
        Map<String, Object> plan = SettingsCopyPlan.create(extraScreen, true, "compact", true, "pure",
                EnumSet.of(SettingsCatalog.Category.TEXT));
        assertEquals(1, plan.size());
        assertEquals(145, plan.get("text_scale_secondary_pure"));
    }

    @Test public void noSelectionProducesNoWrites() {
        Map<String, Object> source = new HashMap<>();
        source.put("opacity_main_default", 80);
        assertTrue(SettingsCopyPlan.create(source, false, "default", true, "compact",
                EnumSet.noneOf(SettingsCatalog.Category.class)).isEmpty());
    }
}
