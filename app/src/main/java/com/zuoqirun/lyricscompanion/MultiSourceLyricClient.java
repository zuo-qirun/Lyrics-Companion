package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Uses the preferred catalog first, then checks each remaining catalog in order. */
final class MultiSourceLyricClient {
    private static final String TAG = "LyricsCatalog";
    private final NetEaseLyricClient netease;
    private final QQMusicLyricClient qq;
    private final KugouLyricClient kugou;
    private final KuwoLyricClient kuwo;
    private final SodaLyricClient soda;

    MultiSourceLyricClient(Context context) {
        netease = new NetEaseLyricClient(context);
        qq = new QQMusicLyricClient(context);
        kugou = new KugouLyricClient(context);
        kuwo = new KuwoLyricClient(context);
        soda = new SodaLyricClient(context);
    }

    Result load(String currentSource, String selectedCatalog, boolean playerCatalogFallback,
                String mediaId, String title, String artist, long durationMs) throws Exception {
        CatalogPlan plan = catalogPlan(currentSource, selectedCatalog, playerCatalogFallback);
        for (String provider : plan.providers) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Log.i(TAG, "Trying catalog " + provider + ": " + title + " / " + artist);
            Result result = tryProvider(provider,
                    directMediaId(currentSource, provider, mediaId),
                    title, artist, durationMs);
            if (!result.timeline.isEmpty()) return result;
            Log.i(TAG, "No lyric in catalog " + provider + ": " + title);
        }
        return Result.EMPTY;
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
                case "soda":
                    timeline = soda.load(mediaId, title, artist, durationMs);
                    label = "汽水音乐";
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

    static CatalogPlan catalogPlan(String currentSource, String selectedCatalog,
                                   boolean playerCatalogFallback) {
        List<String> providers = new ArrayList<>(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo", "soda"));
        List<String> ordered = new ArrayList<>();
        List<String> priority = new ArrayList<>();
        String selected = MusicAppRegistry.lyricCatalogForSource(selectedCatalog);
        String player = MusicAppRegistry.lyricCatalogForSource(currentSource);
        if (selected.isEmpty()) {
            if (!player.isEmpty()) {
                priority.add(player);
                ordered.add(player);
            }
        } else {
            priority.add(selected);
            ordered.add(selected);
            if (!player.isEmpty() && !player.equals(selected)) {
                if (playerCatalogFallback) {
                    priority.add(player);
                    ordered.add(player);
                }
                else providers.remove(player);
            }
        }
        for (String provider : providers) {
            if (!ordered.contains(provider)) ordered.add(provider);
        }
        return new CatalogPlan(ordered, priority, !selected.isEmpty());
    }

    static String directMediaId(String currentSource, String provider, String mediaId) {
        return currentSource != null && currentSource.equals(provider)
                && ("netease".equals(provider) || "soda".equals(provider)) ? mediaId : "";
    }

    static Result chooseResult(List<String> priority, List<Result> successful) {
        if (successful == null || successful.isEmpty()) return Result.EMPTY;
        if (priority != null) {
            for (String provider : priority) {
                for (Result result : successful) {
                    if (provider.equals(result.providerId)) return result;
                }
            }
        }
        return successful.get(0);
    }

    static final class CatalogPlan {
        final List<String> providers;
        final List<String> priority;
        final boolean manualSelection;

        CatalogPlan(List<String> providers, List<String> priority, boolean manualSelection) {
            this.providers = providers;
            this.priority = priority;
            this.manualSelection = manualSelection;
        }
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
