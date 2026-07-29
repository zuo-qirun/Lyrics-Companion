package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Uses the current player's catalog first, then races the remaining lyric catalogs. */
final class MultiSourceLyricClient {
    private static final String TAG = "LyricsCatalog";
    private static final long FALLBACK_TIMEOUT_MS = 13_000L;
    private final NetEaseLyricClient netease;
    private final QQMusicLyricClient qq;
    private final KugouLyricClient kugou;
    private final KuwoLyricClient kuwo;
    private final ExecutorService fallbackPool = Executors.newFixedThreadPool(4);

    MultiSourceLyricClient(Context context) {
        netease = new NetEaseLyricClient(context);
        qq = new QQMusicLyricClient(context);
        kugou = new KugouLyricClient(context);
        kuwo = new KuwoLyricClient(context);
    }

    Result load(String currentSource, String mediaId, String title, String artist,
                long durationMs) throws Exception {
        String preferred = providerForSource(currentSource);
        List<String> providers = new ArrayList<>(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo"));
        CompletionService<Result> completion = new ExecutorCompletionService<>(fallbackPool);
        List<Future<Result>> futures = new ArrayList<>();
        for (String provider : providers) {
            futures.add(completion.submit(() -> tryProvider(
                    provider, directMediaId(currentSource, provider, mediaId),
                    title, artist, durationMs)));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FALLBACK_TIMEOUT_MS);
        List<Result> successful = new ArrayList<>();
        try {
            for (int remaining = futures.size(); remaining > 0; remaining--) {
                long waitNanos = deadline - System.nanoTime();
                if (waitNanos <= 0L) break;
                Future<Result> completed = completion.poll(waitNanos, TimeUnit.NANOSECONDS);
                if (completed == null) break;
                try {
                    Result result = completed.get();
                    if (!result.timeline.isEmpty()) {
                        successful.add(result);
                        if (result.providerId.equals(preferred)) return result;
                        long graceDeadline = System.nanoTime()
                                + TimeUnit.MILLISECONDS.toNanos(1_500L);
                        deadline = Math.min(deadline, graceDeadline);
                    }
                } catch (Exception error) {
                    Log.d(TAG, "Fallback catalog failed", error);
                }
            }
        } finally {
            for (Future<Result> future : futures) future.cancel(true);
        }
        return chooseResult(preferred, successful);
    }

    private Result tryProvider(String provider, String mediaId, String title, String artist,
                               long durationMs) {
        try {
            LrcTimeline timeline;
            String label;
            switch (provider) {
                case "netease":
                    timeline = netease.load(mediaId, title, artist, durationMs).timeline;
                    label = "网易云音乐";
                    break;
                case "qqmusic":
                    timeline = qq.load(title, artist, durationMs);
                    label = "QQ 音乐";
                    break;
                case "kugou":
                    timeline = kugou.load(title, artist, durationMs);
                    label = "酷狗音乐";
                    break;
                case "kuwo":
                    timeline = kuwo.load(title, artist, durationMs);
                    label = "酷我音乐";
                    break;
                default:
                    return Result.EMPTY;
            }
            if (!timeline.isEmpty()) {
                Log.i(TAG, "Lyric matched from " + label + ": " + title + " / " + artist);
                return new Result(timeline, label, provider);
            }
        } catch (Throwable error) {
            Log.d(TAG, provider + " lyric lookup failed for " + title, error);
        }
        return Result.EMPTY;
    }

    private static String providerForSource(String source) {
        if ("netease".equals(source) || "qqmusic".equals(source)
                || "kugou".equals(source) || "kuwo".equals(source)) return source;
        return "";
    }

    static String directMediaId(String currentSource, String provider, String mediaId) {
        return "netease".equals(currentSource) && "netease".equals(provider)
                ? mediaId : "";
    }

    static Result chooseResult(String preferred, List<Result> successful) {
        if (successful == null || successful.isEmpty()) return Result.EMPTY;
        if (preferred != null && !preferred.isEmpty()) {
            for (Result result : successful) {
                if (preferred.equals(result.providerId)) return result;
            }
        }
        return successful.get(0);
    }

    static final class Result {
        static final Result EMPTY = new Result(LrcTimeline.EMPTY, "", "");
        final LrcTimeline timeline;
        final String sourceName;
        final String providerId;

        Result(LrcTimeline timeline, String sourceName, String providerId) {
            this.timeline = timeline == null ? LrcTimeline.EMPTY : timeline;
            this.sourceName = sourceName == null ? "" : sourceName;
            this.providerId = providerId == null ? "" : providerId;
        }
    }
}
