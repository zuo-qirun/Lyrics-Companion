package com.zuoqirun.lyricscompanion;

/** Shared control capabilities for the independent native Folia renderers. */
final class StyleSettingCapabilities {
    private StyleSettingCapabilities() { }

    static boolean supportsFoliaParameter(String style, String key) {
        return key != null && key.startsWith("folia_") && FoliaModeCatalog.isStyle(style);
    }
}
