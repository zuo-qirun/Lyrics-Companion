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
                && !isConnectionPlaceholder(title)
                && !value.matches("(?:未知歌曲|未知歌名|unknown|unknownsong|unknowntrack|notprovided|unavailable)");
    }

    static boolean isConnectionPlaceholder(String text) {
        return normalize(text).matches("(?:androidauto|carplay|蓝牙音频|bluetoothaudio|"
                + "已连接|未连接|已断开|正在连接|连接中|配对中|无媒体信息|无媒体|"
                + "connected|disconnected|connecting|pairing|nomedia|nomediainformation)");
    }

    static boolean retainMissingTitle(boolean sameSession, boolean hadTitle,
                                      boolean emptyTitle, int state) {
        return sameSession && hadTitle && emptyTitle
                && state != MusicPlaybackData.STATE_STOPPED
                && state != MusicPlaybackData.STATE_ERROR;
    }

    static boolean matchesPublisher(String incomingSource, String incomingPackage,
                                    String currentSource, String currentPackage) {
        if (incomingPackage != null && !incomingPackage.isEmpty()
                && incomingPackage.equals(currentPackage)) return true;
        return incomingSource != null && incomingSource.equals(currentSource)
                && (incomingPackage == null || incomingPackage.isEmpty()
                || currentPackage == null || currentPackage.isEmpty()
                || "bluetooth".equals(incomingSource));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
