package com.zuoqirun.lyricscompanion;

/**
 * 「为什么没显示歌词」的可诊断判定（issue #62）。
 *
 * <p>U 盘 / 视频场景下最常见的三种情况——播放器压根没发布媒体会话、发布了歌名但没有进度、
 * 歌名有了但词库里没有这首歌——以前在「音乐状态与诊断」里分不出来，用户只能看到「暂无匹配歌词」。
 * 这里把判定抽成纯函数：输入都是 MusicStateStore 已有的布尔量，输出一句人话，日志与界面共用。
 */
final class MediaDiagnosisRules {
    /** 诊断结论。 */
    enum Verdict {
        /** 没有任何可用的媒体会话：播放器没把歌名发布给系统。 */
        NO_SESSION,
        /** 有歌名但没有播放进度：只能显示、不能滚动。 */
        TITLE_NO_PROGRESS,
        /** 歌词已就绪。 */
        MATCHED,
        /** 歌曲识别到了，但所有词库与本地都没命中。 */
        NOT_FOUND,
        /** 还在匹配。 */
        LOADING
    }

    private MediaDiagnosisRules() { }

    static Verdict classify(boolean active, boolean titlePresent, boolean progressKnown,
                            boolean lyricLoaded, boolean lyricAvailable,
                            String lyricSourceName) {
        if (!active || !titlePresent) return Verdict.NO_SESSION;
        if (lyricAvailable) return Verdict.MATCHED;
        if (!lyricLoaded) return Verdict.LOADING;
        if (!progressKnown) return Verdict.TITLE_NO_PROGRESS;
        return Verdict.NOT_FOUND;
    }

    /** 结论文案；已命中时带上来源名，没来源名就绝不宣称「已匹配词库」。 */
    static String verdictText(Verdict verdict, String lyricSourceName) {
        if (verdict == null) return "";
        String sourceName = lyricSourceName == null ? "" : lyricSourceName.trim();
        switch (verdict) {
            case MATCHED:
                return sourceName.isEmpty() ? "歌词已就绪"
                        : "已匹配：" + sourceName;
            case NO_SESSION:
                return "未收到媒体会话：播放器没把歌名发布给系统（U 盘 / 部分车机自带播放器常见）";
            case TITLE_NO_PROGRESS:
                return "有歌名但没有播放进度：歌词只能整句显示，无法跟随滚动";
            case LOADING:
                return "正在匹配歌词";
            default:
                return "已识别歌曲，但词库与本地都没命中："
                        + "可把同名 .lrc 放到歌曲同目录，或在「本地歌词」里授权该目录";
        }
    }

    /**
     * 「播放器只提供标题」这类来源能否走同一条按歌名匹配的链路：真正的判定在
     * {@link LocalTrackQueryRules#canQueryByTitle}，这里只做委派，避免两处规则漂移。
     */
    static boolean acceptsTitleOnlyMatching(String source, String title, String artist) {
        return LocalTrackQueryRules.canQueryByTitle(source, title, artist);
    }
}
