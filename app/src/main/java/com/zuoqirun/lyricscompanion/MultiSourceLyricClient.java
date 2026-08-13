package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.os.SystemClock;
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
    private final LocalLyricClient local;
    private final Context appContext;

    MultiSourceLyricClient(Context context) {
        appContext = context.getApplicationContext();
        netease = new NetEaseLyricClient(context);
        qq = new QQMusicLyricClient(context);
        kugou = new KugouLyricClient(context);
        kuwo = new KuwoLyricClient(context);
        soda = new SodaLyricClient(context);
        local = new LocalLyricClient(context);
    }

    Result load(String currentSource, String selectedCatalog, boolean playerCatalogFallback,
                boolean forceSelectedCatalog,
                String sourcePackage, String mediaId, String mediaUri, String title, String artist,
                long durationMs) throws Exception {
        if (AppPreferences.localLyricEnabled(appContext)) {
            LrcTimeline localTimeline = local.load(mediaUri, title, artist);
            if (!localTimeline.isEmpty()) {
                DiagnosticLog.record(appContext, "Lyrics", "provider=local result=matched lines="
                        + localTimeline.lineCount());
                return new Result(localTimeline, "本地 LRC", "local");
            }
        }
        CatalogPlan plan = catalogPlan(currentSource, selectedCatalog, playerCatalogFallback,
                forceSelectedCatalog);
        DiagnosticLog.record(appContext, "Lyrics", "lookup start source=" + currentSource
                + " selected=" + selectedCatalog + " playerFallback=" + playerCatalogFallback
                + " forced=" + forceSelectedCatalog
                + " package=" + sourcePackage
                + " providers=" + plan.providers + " title=" + title + " artist=" + artist
                + " durationMs=" + durationMs + " directMediaId="
                + (!directMediaId(currentSource, currentSource, mediaId).isEmpty()));
        List<LocalTrackQueryRules.Query> queries = new ArrayList<>();
        queries.add(new LocalTrackQueryRules.Query(title, artist));
        queries.addAll(LocalTrackQueryRules.fallbackQueries(currentSource, title, artist));
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            LocalTrackQueryRules.Query query = queries.get(queryIndex);
            if (queryIndex > 0) {
                DiagnosticLog.record(appContext, "Lyrics", "local filename fallback index="
                        + queryIndex + " title=" + query.title + " artist=" + query.artist);
            }
            for (String provider : plan.providers) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Log.i(TAG, "Trying catalog " + provider + ": "
                        + query.title + " / " + query.artist);
                String providerMediaId = queryIndex == 0
                        ? directMediaId(currentSource, provider, mediaId) : "";
                Result result = tryProvider(provider, sourcePackage, providerMediaId,
                        query.title, query.artist, durationMs);
                if (!result.timeline.isEmpty()) return result;
                Log.i(TAG, "No lyric in catalog " + provider + ": " + query.title);
            }
        }
        return Result.EMPTY;
    }

    private Result tryProvider(String provider, String sourcePackage, String mediaId,
                               String title, String artist, long durationMs) {
        long startedAt = SystemClock.elapsedRealtime();
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
                    timeline = soda.load(sourcePackage, mediaId, title, artist, durationMs);
                    label = "汽水音乐";
                    break;
                default:
                    return Result.EMPTY;
            }
            if (!timeline.isEmpty()) {
                Log.i(TAG, "Lyric matched from " + label + ": " + title + " / " + artist);
                DiagnosticLog.record(appContext, "Lyrics", "provider=" + provider
                        + " result=matched lines=" + timeline.lineCount()
                        + " elapsedMs=" + (SystemClock.elapsedRealtime() - startedAt)
                        + " directMediaId=" + !mediaId.isEmpty());
                return new Result(timeline, label, provider);
            }
            DiagnosticLog.record(appContext, "Lyrics", "provider=" + provider
                    + " result=empty elapsedMs="
                    + (SystemClock.elapsedRealtime() - startedAt)
                    + " directMediaId=" + !mediaId.isEmpty());
        } catch (Throwable error) {
            Log.d(TAG, provider + " lyric lookup failed for " + title, error);
            DiagnosticLog.record(appContext, "Lyrics", "provider=" + provider
                    + " result=error elapsedMs="
                    + (SystemClock.elapsedRealtime() - startedAt)
                    + " error=" + error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "" : error.getMessage()));
        }
        return Result.EMPTY;
    }

    static CatalogPlan catalogPlan(String currentSource, String selectedCatalog,
                                   boolean playerCatalogFallback) {
        return catalogPlan(currentSource, selectedCatalog, playerCatalogFallback, false);
    }

    static CatalogPlan catalogPlan(String currentSource, String selectedCatalog,
                                   boolean playerCatalogFallback, boolean forceSelectedCatalog) {
        List<String> providers = new ArrayList<>(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo", "soda"));
        List<String> ordered = new ArrayList<>();
        List<String> priority = new ArrayList<>();
        String selected = MusicAppRegistry.lyricCatalogForSource(selectedCatalog);
        String player = MusicAppRegistry.lyricCatalogForSource(currentSource);
        if (forceSelectedCatalog && !selected.isEmpty()) {
            priority.add(selected);
            ordered.add(selected);
            return new CatalogPlan(ordered, priority, true);
        }
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
