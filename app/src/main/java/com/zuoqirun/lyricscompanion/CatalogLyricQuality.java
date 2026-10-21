package com.zuoqirun.lyricscompanion;

/** Network catalogs must provide a usable timeline before stopping cross-catalog fallback. */
final class CatalogLyricQuality {
    private CatalogLyricQuality() { }

    static boolean usable(LrcTimeline timeline) {
        // A single provider notice/credit at a remote timestamp used to poison the matched cache.
        // This rule applies to catalog results only; local LRC and live session text stay intact.
        if (timeline == null || timeline.lineCount() <= 1) return false;
        for (LrcTimeline.Line line : timeline.renderLines()) {
            String text = line.text.trim();
            if (!text.isEmpty() && !placeholder(text)) return true;
        }
        return false;
    }

    private static boolean placeholder(String text) {
        String value = text.replaceAll("[\\s\\p{Punct}，。！：、]+", "");
        return value.contains("纯音乐请欣赏") || value.equals("纯音乐")
                || value.contains("暂无歌词") || value.contains("暂时没有歌词")
                || value.equalsIgnoreCase("instrumental");
    }
}
