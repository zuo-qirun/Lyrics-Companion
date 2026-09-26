package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/** Uses the preferred catalog first, then checks each remaining catalog in order. */
final class MultiSourceLyricClient {
    private static final String TAG = "LyricsCatalog";
    private static final ExecutorService FALLBACK_EXECUTOR = Executors.newFixedThreadPool(6);
    /**
     * 缓存后台升级（issue #74）：它只是「顺带把更好的逐字版本补进缓存」，绝不该占着 fallback 线程池
     * 的位置——以前它 fire-and-forget 跑在 6 线程池上、线程又不登记，一首歌最长能占住一个槽位
     * ~51 秒（酷我逐字通道连接超时），切歌也取消不掉。改成独立的单线程队列：新任务开始前先取消上一
     * 个，永远只占一个自己的线程。
     */
    private static final ExecutorService UPGRADE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicReference<Thread> UPGRADE_THREAD = new AtomicReference<>();
    private final NetEaseLyricClient netease;
    private final QQMusicLyricClient qq;
    private final KugouLyricClient kugou;
    private final KuwoLyricClient kuwo;
    private final MiguLyricClient migu;
    private final SodaLyricClient soda;
    private final LocalLyricClient local;
    private final Context appContext;

    MultiSourceLyricClient(Context context) {
        appContext = context.getApplicationContext();
        LyricSourceRules.initialize(appContext);
        netease = new NetEaseLyricClient(context);
        qq = new QQMusicLyricClient(context);
        kugou = new KugouLyricClient(context);
        kuwo = new KuwoLyricClient(context);
        migu = new MiguLyricClient(context);
        soda = new SodaLyricClient(context);
        local = new LocalLyricClient(context);
    }

    Result load(String currentSource, String selectedCatalog, boolean playerCatalogFallback,
                boolean forceSelectedCatalog,
                String sourcePackage, String mediaId, String mediaUri, String title, String artist,
                long durationMs) throws Exception {
        return load(currentSource, selectedCatalog, playerCatalogFallback, forceSelectedCatalog,
                sourcePackage, mediaId, mediaUri, title, artist, durationMs, () -> LrcTimeline.EMPTY);
    }

    interface SessionTimeline { LrcTimeline current(); }

    Result load(String currentSource, String selectedCatalog, boolean playerCatalogFallback,
                boolean forceSelectedCatalog,
                String sourcePackage, String mediaId, String mediaUri, String title, String artist,
                long durationMs, SessionTimeline sessionTimeline) throws Exception {
        return load(currentSource, selectedCatalog, playerCatalogFallback, forceSelectedCatalog,
                sourcePackage, mediaId, mediaUri, title, artist, durationMs, sessionTimeline, false);
    }

    Result load(String currentSource, String selectedCatalog, boolean playerCatalogFallback,
                boolean forceSelectedCatalog, String sourcePackage, String mediaId, String mediaUri,
                String title, String artist, long durationMs, SessionTimeline sessionTimeline,
                boolean bypassMatchedCache) throws Exception {
        if (AppPreferences.localLyricEnabled(appContext)) {
            LocalLyricClient.Hit localHit = local.load(mediaUri, title, artist);
            if (!localHit.timeline.isEmpty()) {
                // A tag inside an audio file found by name (the player gave us no readable path)
                // is a different link of the chain than a .lrc file: say which one matched.
                String sourceName = localHit.embedded ? "内嵌歌词" : "本地 LRC";
                DiagnosticLog.record(appContext, "Lyrics", "provider=local result=matched source="
                        + sourceName + " lines=" + localHit.timeline.lineCount());
                return new Result(localHit.timeline, sourceName, "local");
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
        List<LocalTrackQueryRules.Query> queries = catalogQueries(currentSource, title, artist);
        MatchedLyricCache matchedCache = new MatchedLyricCache(appContext);
        // Read all allowed local results before issuing even the first catalog search.
        for (String provider : bypassMatchedCache ? java.util.Collections.<String>emptyList() : plan.providers) {
            String key = MatchedLyricCache.key(provider, title, artist, durationMs,
                    directMediaId(currentSource, provider, mediaId), sourcePackage);
            LrcTimeline cached = matchedCache.read(key);
            if (!cached.isEmpty()) {
                DiagnosticLog.record(appContext, "Lyrics", "matched cache hit provider=" + provider);
                if (matchedCache.needsUpgrade(key, provider, cached)) {
                    matchedCache.markUpgradeChecked(key);
                    final String upgradeProvider = provider;
                    final String upgradeId = directMediaId(currentSource, provider, mediaId);
                    final int cachedScore = cached.qualityScore();
                    cancelPendingUpgrade();
                    UPGRADE_EXECUTOR.execute(() -> {
                        UPGRADE_THREAD.set(Thread.currentThread());
                        try {
                            Result upgraded = tryProvider(upgradeProvider, sourcePackage, upgradeId,
                                    title, artist, durationMs);
                            if (Thread.currentThread().isInterrupted()) return;
                            if (upgraded.timeline.qualityScore() > cachedScore) {
                                matchedCache.write(key, upgraded.timeline);
                            }
                        } finally {
                            UPGRADE_THREAD.compareAndSet(Thread.currentThread(), null);
                        }
                    });
                }
                return new Result(cached, "本地缓存 · " + providerLabel(provider), provider);
            }
        }
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            LocalTrackQueryRules.Query query = queries.get(queryIndex);
            if (queryIndex > 0) {
                DiagnosticLog.record(appContext, "Lyrics", "fallback query index="
                        + queryIndex + " title=" + query.title + " artist=" + query.artist);
            }
            for (String provider : plan.priority) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if ("kuwo".equals(provider)) {
                    LrcTimeline session = sessionTimeline.current();
                    if (!session.isEmpty()) return new Result(session, "酷我播放器歌词", "kuwo_session");
                }
                Log.i(TAG, "Trying catalog " + provider + ": "
                        + query.title + " / " + query.artist);
                String providerMediaId = queryIndex == 0
                        ? directMediaId(currentSource, provider, mediaId) : "";
                Result result = tryProvider(provider, sourcePackage, providerMediaId,
                        query.title, query.artist, durationMs);
                if (!result.timeline.isEmpty()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    matchedCache.write(MatchedLyricCache.key(provider, title, artist, durationMs,
                            directMediaId(currentSource, provider, mediaId), sourcePackage), result.timeline);
                    return result;
                }
                Log.i(TAG, "No lyric in catalog " + provider + ": " + query.title);
            }
            List<String> fallback = new ArrayList<>(plan.providers);
            fallback.removeAll(plan.priority);
            Result result = parallelFallback(fallback, currentSource, sourcePackage, mediaId,
                    query, queryIndex == 0, durationMs, sessionTimeline);
            if (!result.timeline.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                matchedCache.write(MatchedLyricCache.key(result.providerId, title, artist,
                        durationMs, directMediaId(currentSource, result.providerId, mediaId),
                        sourcePackage), result.timeline);
                return result;
            }
        }
        return Result.EMPTY;
    }

    private Result parallelFallback(List<String> providers, String currentSource,
                                    String sourcePackage, String mediaId,
                                    LocalTrackQueryRules.Query query, boolean firstQuery,
                                    long durationMs, SessionTimeline sessionTimeline)
            throws InterruptedException {
        if (providers.isEmpty()) return Result.EMPTY;
        List<ProviderTask> tasks = new ArrayList<>();
        for (String provider : providers) {
            String providerMediaId = firstQuery
                    ? directMediaId(currentSource, provider, mediaId) : "";
            ProviderTask task = new ProviderTask();
            task.future = FALLBACK_EXECUTOR.submit(() -> {
                task.thread.set(Thread.currentThread());
                try {
                    if ("kuwo".equals(provider)) {
                        LrcTimeline session = sessionTimeline.current();
                        if (!session.isEmpty()) {
                            return new Result(session, "酷我播放器歌词", "kuwo_session");
                        }
                    }
                    return tryProvider(provider, sourcePackage, providerMediaId,
                            query.title, query.artist, durationMs);
                } finally { task.thread.set(null); }
            });
            tasks.add(task);
        }
        try {
            // Futures run together, but only an earlier catalog can beat a later one.
            for (ProviderTask task : tasks) {
                Result result;
                try { result = task.future.get(); }
                catch (ExecutionException error) { continue; }
                if (!result.timeline.isEmpty()) return result;
            }
            return Result.EMPTY;
        } finally {
            for (ProviderTask task : tasks) {
                Thread thread = task.thread.get();
                task.future.cancel(true);
                if (thread != null) LyricHttp.cancel(thread);
            }
        }
    }

    private static final class ProviderTask {
        final AtomicReference<Thread> thread = new AtomicReference<>();
        Future<Result> future;
    }

    /** 取消上一次还在跑的缓存升级（换歌时顺手做，别让旧曲目的请求白占一条线程，issue #74）。 */
    static void cancelPendingUpgrade() {
        Thread thread = UPGRADE_THREAD.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
            LyricHttp.cancel(thread);
        }
    }

    /**
     * Ordered lookup queries for one track: the player metadata first (index 0 keeps the direct
     * media-id lookup), then filename-derived fallbacks, then Simplified-script variants for
     * every candidate. This supports players that report Traditional metadata - Spotify being
     * the common case - while mainland catalogs are indexed under Simplified titles.
     */
    static List<LocalTrackQueryRules.Query> catalogQueries(String source, String title,
                                                          String artist) {
        List<LocalTrackQueryRules.Query> queries = new ArrayList<>();
        queries.add(new LocalTrackQueryRules.Query(title, artist));
        for (LocalTrackQueryRules.Query fallback
                : LocalTrackQueryRules.fallbackQueries(source, title, artist)) {
            addQuery(queries, fallback.title, fallback.artist);
        }
        int originalQueryCount = queries.size();
        for (int i = 0; i < originalQueryCount; i++) {
            LocalTrackQueryRules.Query query = queries.get(i);
            for (LocalTrackQueryRules.Query variant : scriptVariants(query.title, query.artist)) {
                addQuery(queries, variant.title, variant.artist);
            }
        }
        return queries;
    }

    /** Both fields converted first, then one field at a time; empty when the text is Simplified. */
    private static List<LocalTrackQueryRules.Query> scriptVariants(String title, String artist) {
        List<LocalTrackQueryRules.Query> variants = new ArrayList<>(3);
        String simplifiedTitle = ChineseScriptConverter.toSimplified(title);
        String simplifiedArtist = ChineseScriptConverter.toSimplified(artist);
        boolean titleChanged = simplifiedTitle != null && !simplifiedTitle.equals(title);
        boolean artistChanged = simplifiedArtist != null && !simplifiedArtist.equals(artist);
        if (titleChanged && artistChanged) {
            variants.add(new LocalTrackQueryRules.Query(simplifiedTitle, simplifiedArtist));
        }
        if (titleChanged) variants.add(new LocalTrackQueryRules.Query(simplifiedTitle, artist));
        if (artistChanged) variants.add(new LocalTrackQueryRules.Query(title, simplifiedArtist));
        return variants;
    }

    private static void addQuery(List<LocalTrackQueryRules.Query> queries, String title,
                                 String artist) {
        if (title == null || title.isEmpty()) return;
        String key = queryKey(title, artist);
        for (LocalTrackQueryRules.Query existing : queries) {
            if (queryKey(existing.title, existing.artist).equals(key)) return;
        }
        queries.add(new LocalTrackQueryRules.Query(title, artist));
    }

    /** Case- and punctuation-insensitive identity, mirroring the fallback-query de-duplication. */
    private static String queryKey(String title, String artist) {
        return normalizeQueryText(title) + "|" + normalizeQueryText(artist);
    }

    private static String normalizeQueryText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static String providerLabel(String provider) {
        switch (provider) {
            case "netease": return "网易云音乐";
            case "qqmusic": return "QQ 音乐";
            case "kugou": return "酷狗音乐";
            case "kuwo": return "酷我音乐";
            case "migu": return "咪咕音乐";
            case "soda": return "汽水音乐";
            default: return provider;
        }
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
                    timeline = kuwo.load(mediaId, title, artist, durationMs);
                    label = "酷我音乐";
                    break;
                case "migu":
                    timeline = migu.load(mediaId, title, artist, durationMs);
                    label = "咪咕音乐";
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
            if (error instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return Result.EMPTY;
            }
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
                "netease", "qqmusic", "kugou", "kuwo", "soda", "migu"));
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
                && ("netease".equals(provider) || "soda".equals(provider)
                || "kuwo".equals(provider) || "migu".equals(provider)) ? mediaId : "";
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
