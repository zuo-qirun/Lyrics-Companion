package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class AppUpdaterTest {
    @Test public void changelogIncludesOnlyVersionsAfterLocalVersion() throws Exception {
        JSONArray versions = new JSONArray()
                .put(new JSONObject().put("versionCode", 4).put("versionName", "1.3.0")
                        .put("changelog", new JSONArray().put("通知栏歌词")))
                .put(new JSONObject().put("versionCode", 3).put("versionName", "1.2.0")
                        .put("changelog", new JSONArray().put("诊断增强")))
                .put(new JSONObject().put("versionCode", 2).put("versionName", "1.1.0")
                        .put("changelog", new JSONArray().put("旧版本内容")));

        String result = AppUpdater.changelogBetween(2, 4, versions, "fallback");

        assertTrue(result.contains("1.3.0 (4)"));
        assertTrue(result.contains("通知栏歌词"));
        assertTrue(result.contains("1.2.0 (3)"));
        assertTrue(result.contains("诊断增强"));
        assertFalse(result.contains("1.1.0 (2)"));
        assertFalse(result.contains("旧版本内容"));
    }

    @Test public void changelogFallsBackWhenHistoryHasNoApplicableVersion() throws Exception {
        JSONArray versions = new JSONArray().put(new JSONObject()
                .put("versionCode", 2).put("versionName", "1.1.0")
                .put("changelog", new JSONArray().put("旧版本内容")));

        assertTrue(AppUpdater.changelogBetween(2, 2, versions, "latest notes")
                .contains("latest notes"));
    }
}
