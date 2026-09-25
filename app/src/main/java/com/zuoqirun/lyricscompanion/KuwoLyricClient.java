package com.zuoqirun.lyricscompanion;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

final class KuwoLyricClient {
    private static final String REFERER = "https://www.kuwo.cn/";
    private final Context context;
    private final LyricCache cache;

    KuwoLyricClient(Context context) {
        this.context = context.getApplicationContext();
        cache = new LyricCache(context, "kuwo");
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
        try {
            byte[] response = LyricHttp.getBytes("http://mlyric.kuwo.cn/mobi.s?f=web"
                    + "&type=lyric&lrcx=1&rid=" + LyricHttp.encode(id)
                    + "&encode=utf8", REFERER);
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
            log(route + " rid=" + id + " word channel="
                    + error.getClass().getSimpleName());
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
