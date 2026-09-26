package com.zuoqirun.lyricscompanion;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

final class KuwoLyricClient {
    private static final String REFERER = "https://www.kuwo.cn/";
    /**
     * 逐字通道的负缓存（issue #74）：这条通道连不上时，每首歌都要先等一次连接超时才轮到老接口。
     * 连续失败 {@link WordChannelGate#FAILURES_BEFORE_BACKOFF} 次就退避 15 分钟，成功一次立刻清零。
     * 状态是静态的：每个匹配任务都会新建一个 client，实例字段留不住。
     */
    private static volatile int wordChannelFailures;
    private static volatile long wordChannelBlockedUntilMs;
    private final Context context;
    private final LyricCache cache;

    KuwoLyricClient(Context context) {
        this.context = context.getApplicationContext();
        cache = new LyricCache(context, "kuwo");
    }

    /** 诊断用：逐字通道当前是否被退避、还剩多久（毫秒）。 */
    static long wordChannelBlockedForMs() {
        return WordChannelGate.remainingMs(android.os.SystemClock.elapsedRealtime(),
                wordChannelBlockedUntilMs);
    }

    LrcTimeline load(String mediaId, String title, String artist, long durationMs)
            throws Exception {
        Set<String> attempted = new HashSet<>();
        String rid = KuwoLyricParser.trackId(mediaId);
        if (!rid.isEmpty()) {
            attempted.add(rid);
            LrcTimeline direct = tryLyrics(rid, "direct");
            if (!direct.isEmpty()) return direct;
        }
        String combined = title + (artist == null || artist.trim().isEmpty()
                ? "" : " " + artist.trim());
        String[] queries = combined.equals(title)
                ? new String[]{title} : new String[]{combined, title};
        int candidateRequests = 0;
        for (String query : queries) {
            checkInterrupted();
            try {
                String response = LyricHttp.get("https://search.kuwo.cn/r.s?all="
                        + LyricHttp.encode(query)
                        + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=30"
                        + "&rformat=json&encoding=utf8", REFERER);
                java.util.List<KuwoLyricParser.Candidate> candidates =
                        KuwoLyricParser.candidates(response, title, artist, durationMs);
                log("search candidates=" + candidates.size());
                for (KuwoLyricParser.Candidate candidate : candidates) {
                    if (!attempted.add(candidate.id)) continue;
                    if (candidateRequests >= 3) return LrcTimeline.EMPTY;
                    candidateRequests++;
                    LrcTimeline result = tryLyrics(candidate.id, "search score=" + candidate.score);
                    if (!result.isEmpty()) return result;
                }
            } catch (InterruptedException error) {
                throw error;
            } catch (Exception error) {
                checkInterrupted();
                log("search error=" + error.getClass().getSimpleName());
            }
        }
        return LrcTimeline.EMPTY;
    }

    private LrcTimeline tryLyrics(String id, String route) throws Exception {
        checkInterrupted();
        String enhanced = cache.read(id + "_enhanced_v2");
        if (enhanced != null) {
            LrcTimeline cachedEnhanced = LrcTimeline.parse("", "", enhanced);
            if (!cachedEnhanced.isEmpty()) return cachedEnhanced;
        }
        // 逐字通道最近连不上就跳过它（缓存与老接口照常走），不再让每首歌先等一次超时（issue #74）。
        long now = android.os.SystemClock.elapsedRealtime();
        if (WordChannelGate.isOpen(now, wordChannelBlockedUntilMs)) {
            try {
                byte[] response = LyricHttp.getBytes("http://mlyric.kuwo.cn/mobi.s?f=web"
                        + "&type=lyric&lrcx=1&rid=" + LyricHttp.encode(id)
                        + "&encode=utf8", REFERER, LyricHttp.Timeouts.WORD_CHANNEL);
                // 拿到响应就算通道可用，连续失败清零。
                wordChannelFailures = 0;
                wordChannelBlockedUntilMs = 0L;
                enhanced = KuwoWordLyricCodec.toEnhancedTimeline(
                        LyricSourceRules.plainKuwoWordResponse()
                                ? new String(response, java.nio.charset.StandardCharsets.UTF_8)
                                : KuwoWordLyricCodec.decode(response));
                LrcTimeline wordTimed = LrcTimeline.parse("", "", enhanced);
                if (!wordTimed.isEmpty()) {
                    cache.write(id + "_enhanced_v2", enhanced);
                    log(route + " rid=" + id + " wordTimedLines=" + wordTimed.lineCount());
                    return wordTimed;
                }
            } catch (InterruptedException error) {
                throw error;
            } catch (Exception error) {
                checkInterrupted();
                // 传输层失败才退避；「连上了但没有逐字歌词」不算失败。
                int failures = WordChannelGate.failuresAfterFailure(wordChannelFailures);
                wordChannelFailures = failures;
                wordChannelBlockedUntilMs = WordChannelGate.blockedUntil(
                        android.os.SystemClock.elapsedRealtime(), failures);
                log(route + " rid=" + id + " word channel="
                        + error.getClass().getSimpleName() + " failures=" + failures
                        + (wordChannelBlockedUntilMs > 0L
                        ? " backoffMs=" + WordChannelGate.BACKOFF_MS : ""));
            }
        } else {
            log(route + " rid=" + id + " word channel skipped backoffMs="
                    + WordChannelGate.remainingMs(now, wordChannelBlockedUntilMs));
        }
        String cached = cache.read(id);
        if (cached != null) {
            LrcTimeline result = LrcTimeline.parse(cached, "");
            if (!result.isEmpty()) return result;
        }
        try {
            String result = KuwoLyricParser.webLyrics(LyricHttp.get(
                    "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=" + id, REFERER));
            LrcTimeline timeline = LrcTimeline.parse(result, "");
            if (!timeline.isEmpty()) cache.write(id, result);
            log(route + " rid=" + id + " lines=" + timeline.lineCount());
            return timeline;
        } catch (InterruptedException error) {
            throw error;
        } catch (Exception error) {
            checkInterrupted();
            log(route + " rid=" + id + " error=" + error.getClass().getSimpleName()
                    + (error instanceof KuwoLyricParser.ApiException
                    ? " status=" + ((KuwoLyricParser.ApiException) error).status : ""));
            return LrcTimeline.EMPTY;
        }
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private void log(String message) { DiagnosticLog.record(context, "Kuwo", message); }
}
