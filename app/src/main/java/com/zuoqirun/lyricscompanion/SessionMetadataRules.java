package com.zuoqirun.lyricscompanion;

import java.util.Locale;

/** Shared conservative rules for service-only titles and incomplete car sessions. */
final class SessionMetadataRules {
    private SessionMetadataRules() { }

    static boolean isServiceTitle(String title, String label) {
        String value = normalize(title);
        String app = normalize(label);
        if (value.isEmpty() || app.isEmpty()) return false;
        if (value.equals(app)) return true;
        if (!value.startsWith(app)) return false;
        String suffix = value.substring(app.length());
        return suffix.matches("(?:服务|音乐服务|运行中|已启动|正在运行|后台|后台运行|service|musicservice|running)");
    }

    static boolean usableTitle(String title, String label) {
        String value = normalize(title);
        return !value.isEmpty() && !isServiceTitle(title, label)
                && !value.matches("(?:未知歌曲|未知歌名|unknown|unknownsong|unknowntrack|notprovided|unavailable)");
    }

    static boolean retainMissingTitle(boolean sameSession, boolean hadTitle,
                                      boolean emptyTitle, int state) {
        return sameSession && hadTitle && emptyTitle
                && state != MusicPlaybackData.STATE_STOPPED
                && state != MusicPlaybackData.STATE_ERROR;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
