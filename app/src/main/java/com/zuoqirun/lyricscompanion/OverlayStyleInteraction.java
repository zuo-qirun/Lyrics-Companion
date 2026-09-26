package com.zuoqirun.lyricscompanion;

/** Touch-routing rules that do not depend on Android view state. */
final class OverlayStyleInteraction {
    private OverlayStyleInteraction() { }

    /**
     * 这一样式是否把整个面板留给「拖动窗口」用（而不是当成歌词浏览手势区）：
     * 紧凑、纯净、以及灵动岛（一只小胶囊，整块都该能拖，issue #54）。
     */
    static boolean reservesSurfaceForWindowDrag(String style) {
        return "compact".equals(style) || "pure".equals(style) || "island".equals(style);
    }
}
