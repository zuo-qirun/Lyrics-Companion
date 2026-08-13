package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class MusicStateStore {
    private static final String TAG = "LyricsMusicState";
    private static final Object LOCK = new Object();
    private static final ExecutorService LYRIC_EXECUTOR = Executors.newCachedThreadPool();
    private static final ExecutorService ART_EXECUTOR = Executors.newSingleThreadExecutor();

    private static Context appContext;
    private static MultiSourceLyricClient lyricClient;
    private static boolean active;
    private static boolean playing;
    private static String source = "media";
    private static String sourceName = "音乐播放器";
    private static String sourcePackage = "";
    private static String mediaId = "";
    private static String title = "";
    private static String artist = "";
    private static Bitmap albumArt;
    private static String albumArtUri = "";
    private static String mediaUri = "";
    private static String loadingAlbumArtUri = "";
    private static long durationMs = -1L;
    private static long basePositionMs;
    private static long lastReportedPositionMs = -1L;
    private static long positionUpdatedAtElapsedMs;
    private static float playbackSpeed;
    private static long trackGeneration;
    private static String trackKey = "";
    private static LrcTimeline timeline = LrcTimeline.EMPTY;
    private static boolean lyricLoadFinished;
    private static String lyricSourceName = "";
    private static String liveSessionLyric = "";
    private static boolean netEaseAutoScrollUnsupported;
    private static Future<?> lyricLoadTask;

    private MusicStateStore() {}

    static void initialize(Context context) {
        synchronized (LOCK) {
            if (lyricClient == null) {
                appContext = context.getApplicationContext();
                lyricClient = new MultiSourceLyricClient(appContext);
            }
        }
    }

    static void update(Context context, String newSource, String newSourceName,
                       MusicPlaybackData data) {
        update(context, newSource, newSourceName, "", data);
    }

    static void update(Context context, String newSource, String newSourceName,
                       String newSourcePackage, MusicPlaybackData data) {
        initialize(context);
        if (data == null) {
            clear();
            return;
        }
        String normalizedSource = TextUtils.isEmpty(newSource) ? "media" : newSource;
        String rawTitle = data.title;
        String rawArtist = data.artist;
        String sodaDynamicTitle = "soda".equals(normalizedSource)
                ? sodaTitleFromDynamicArtist(rawArtist) : "";
        boolean sodaHasCompositeIdentity = !TextUtils.isEmpty(sodaDynamicTitle)
                && !sameIdentityText(rawTitle, sodaDynamicTitle);
        String newTitle = sodaHasCompositeIdentity ? sodaDynamicTitle : rawTitle;
        String newArtist = sodaHasCompositeIdentity
                ? sodaStableArtist(newTitle, rawArtist) : rawArtist;
        String incomingLiveSessionLyric = sodaHasCompositeIdentity ? rawTitle : "";
        String newMediaId = data.mediaId;
        Bitmap newAlbumArt = data.albumArt;
        String newAlbumArtUri = data.albumArtUri;
        String newMediaUri = data.mediaUri;
        long newDuration = data.durationMs;
        int stateValue = data.state;
        boolean statePresent = data.statePresent;
        long newPosition = !statePresent || data.positionMs < 0L ? 0L : data.positionMs;
        long reportedPositionTime = !statePresent ? 0L : data.positionUpdatedAtElapsedMs;
        float newSpeed = data.speed;
        String normalizedSourceName = TextUtils.isEmpty(newSourceName)
                ? "音乐播放器" : newSourceName;
        String normalizedSourcePackage = newSourcePackage == null ? "" : newSourcePackage.trim();
        String selectedCatalog = AppPreferences.lyricCatalog(context, normalizedSource,
                normalizedSourcePackage);
        boolean forcedPlayerCatalog = AppPreferences.hasForcedPlayerPackageCatalog(context,
                normalizedSourcePackage);
        boolean playerCatalogFallback = AppPreferences.playerCatalogFallback(context);
        long generationToLoad = -1L;
        long generationForAlbumArt = -1L;
        boolean trackChangedForLog = false;
        boolean playbackChangedForLog = false;
        String playbackStateForLog = "";
        synchronized (LOCK) {
            boolean sameSource = TextUtils.equals(source, normalizedSource);
            boolean netEaseUnsupported = isNetEaseAutoScrollUnsupported(
                    normalizedSource, rawTitle);
            if (netEaseUnsupported && sameSource && !TextUtils.isEmpty(title)) {
                // NetEase replaces TITLE with this status when it has no scrollable lyric.
                // Preserve the track identity, but never use the status as a live lyric line.
                newTitle = title;
                newArtist = artist;
                newMediaId = mediaId;
                newDuration = durationMs;
                incomingLiveSessionLyric = "";
            } else if (!sodaHasCompositeIdentity && shouldKeepLiveLyricTrackIdentity(
                    normalizedSource, sameSource, title, newTitle,
                    artist, newArtist, durationMs, newDuration,
                    mediaId, newMediaId)) {
                // Some players replace TITLE with the current lyric. Keep a one-field lyric
                // mutation, but never suppress a complete title+artist replacement or a
                // stable player track-ID change.
                Log.i(TAG, "Ignoring live-lyric metadata: " + newTitle + " / " + newArtist);
                if (!sameIdentityText(newTitle, title)) incomingLiveSessionLyric = newTitle;
                newTitle = title;
                if (!TextUtils.isEmpty(artist)) newArtist = artist;
                if (!TextUtils.isEmpty(mediaId)) newMediaId = mediaId;
                if (durationMs > 0L) newDuration = durationMs;
            }
            if (sameSource && TextUtils.isEmpty(newTitle) && !TextUtils.isEmpty(title)) {
                // Several automotive players publish a playback-state-only update after the
                // complete metadata. Treat it as a partial update instead of erasing the track.
                newTitle = title;
                newArtist = artist;
                newMediaId = mediaId;
                newDuration = durationMs;
                newAlbumArtUri = albumArtUri;
                newMediaUri = mediaUri;
                if (newAlbumArt == null) newAlbumArt = albumArt;
            } else if (sameSource && sameIdentityText(newTitle, title)) {
                if (TextUtils.isEmpty(newArtist)) newArtist = artist;
                if (TextUtils.isEmpty(newMediaId)) newMediaId = mediaId;
                if (newDuration <= 0L) newDuration = durationMs;
                if (TextUtils.isEmpty(newAlbumArtUri)) newAlbumArtUri = albumArtUri;
                if (TextUtils.isEmpty(newMediaUri)) newMediaUri = mediaUri;
            }
            boolean newActive = isDisplayableSession(newTitle, stateValue);
            String newTrackKey = lyricTrackKey(normalizedSource, newTitle, newArtist,
                    newDuration, newMediaId, selectedCatalog, playerCatalogFallback);
            boolean changed = !TextUtils.equals(trackKey, newTrackKey);
            long now = SystemClock.elapsedRealtime();
            long estimatedPosition = currentPositionLocked();
            boolean reportedPositionChanged = !changed
                    && hasMeaningfulPositionChange(lastReportedPositionMs, newPosition);
            boolean sampledProgress = reportedPositionChanged
                    && newPosition > lastReportedPositionMs;
            boolean newPlaying = isPositionAdvancing(newTitle, statePresent, stateValue,
                    sampledProgress);
            float effectiveSpeed = newPlaying ? (newSpeed > 0f ? newSpeed : 1f) : 0f;
            long positionToStore = newPosition;
            long positionTimeToStore = reportedPositionTime > 0L
                    ? reportedPositionTime : now;
            if (!changed && newPlaying && reportedPositionTime <= 0L
                    && !reportedPositionChanged) {
                // Metadata-only automotive sessions commonly keep returning the same raw
                // position. Preserve our monotonic estimate instead of resetting it every poll.
                positionToStore = Math.max(newPosition, estimatedPosition);
                positionTimeToStore = now;
            }
            boolean playbackModeChanged = playing != newPlaying;
            trackChangedForLog = changed;
            playbackChangedForLog = playbackModeChanged;
            playbackStateForLog = "state=" + stateValue + " statePresent=" + statePresent
                    + " active=" + newActive + " playing=" + newPlaying
                    + " speed=" + effectiveSpeed + " positionMs=" + positionToStore
                    + " durationMs=" + newDuration;
            active = newActive;
            playing = newPlaying;
            source = normalizedSource;
            sourceName = normalizedSourceName;
            sourcePackage = normalizedSourcePackage;
            mediaId = safe(newMediaId);
            title = safe(newTitle);
            artist = safe(newArtist);
            if (newAlbumArt != null) albumArt = newAlbumArt;
            albumArtUri = safe(newAlbumArtUri);
            mediaUri = safe(newMediaUri);
            durationMs = newDuration > 0L ? newDuration : -1L;
            basePositionMs = positionToStore;
            lastReportedPositionMs = newPosition;
            positionUpdatedAtElapsedMs = positionTimeToStore;
            playbackSpeed = effectiveSpeed;
            boolean wasNetEaseUnsupported = netEaseAutoScrollUnsupported;
            if (changed) {
                trackKey = newTrackKey;
                timeline = LrcTimeline.EMPTY;
                lyricLoadFinished = false;
                lyricSourceName = "";
                liveSessionLyric = "";
                if (newAlbumArt == null) albumArt = null;
                loadingAlbumArtUri = "";
                generationToLoad = ++trackGeneration;
                cancelLyricLoadLocked();
            }
            if (netEaseUnsupported) {
                netEaseAutoScrollUnsupported = true;
                timeline = LrcTimeline.EMPTY;
                lyricLoadFinished = true;
                lyricSourceName = "";
                liveSessionLyric = "";
                cancelLyricLoadLocked();
                generationToLoad = -1L;
            } else {
                netEaseAutoScrollUnsupported = false;
                if (wasNetEaseUnsupported && !changed) {
                    timeline = LrcTimeline.EMPTY;
                    lyricLoadFinished = false;
                    lyricSourceName = "";
                    liveSessionLyric = "";
                    generationToLoad = ++trackGeneration;
                    cancelLyricLoadLocked();
                }
            }
            if (!netEaseUnsupported && usesLiveTitleMetadata(normalizedSource)
                    && !TextUtils.isEmpty(incomingLiveSessionLyric)) {
                liveSessionLyric = incomingLiveSessionLyric.trim();
            }
            if (changed || playbackModeChanged) {
                Log.i(TAG, "Position sync state=" + stateValue + " advancing=" + newPlaying
                        + " speed=" + effectiveSpeed + " position=" + positionToStore
                        + " reportedTime=" + reportedPositionTime);
            }
            if (changed) {
                Log.i(TAG, "Track identity source=" + normalizedSource + " title=" + newTitle
                        + " artist=" + newArtist + " duration=" + newDuration
                        + " mediaId=" + newMediaId);
            }
            if (albumArt == null && !TextUtils.isEmpty(albumArtUri)
                    && !TextUtils.equals(albumArtUri, loadingAlbumArtUri)) {
                loadingAlbumArtUri = albumArtUri;
                generationForAlbumArt = trackGeneration;
            }
        }
        if (trackChangedForLog) {
            DiagnosticLog.record(context, "Playback", "track changed source="
                    + normalizedSource + " app=" + normalizedSourceName + " title=" + newTitle
                    + " artist=" + newArtist + " mediaId=" + newMediaId + " "
                    + playbackStateForLog + " albumArt=" + (newAlbumArt != null)
                    + " albumArtUri=" + !TextUtils.isEmpty(newAlbumArtUri));
        } else if (playbackChangedForLog) {
            DiagnosticLog.record(context, "Playback", "mode changed " + playbackStateForLog);
        }
        if (generationToLoad >= 0L && !TextUtils.isEmpty(newTitle)) {
            scheduleLyricLoad(generationToLoad, normalizedSource, normalizedSourcePackage,
                    newMediaId, newMediaUri,
                    newTitle, newArtist, newDuration, selectedCatalog, playerCatalogFallback,
                    forcedPlayerCatalog);
            if (newAlbumArt == null && TextUtils.isEmpty(newAlbumArtUri)) {
                scheduleCatalogAlbumArtLoad(generationToLoad, newTitle, newArtist, newDuration);
            }
        }
        if (generationForAlbumArt >= 0L) {
            scheduleAlbumArtLoad(generationForAlbumArt, newAlbumArtUri);
        }
        AudioSpectrumSource.setPlaybackActive(context.getApplicationContext(), playing);
    }

    static void clear() {
        boolean hadState;
        synchronized (LOCK) {
            hadState = active || !TextUtils.isEmpty(title);
            active = false;
            playing = false;
            source = "media";
            sourceName = "音乐播放器";
            sourcePackage = "";
            mediaId = "";
            title = "";
            artist = "";
            albumArt = null;
            albumArtUri = "";
            mediaUri = "";
            loadingAlbumArtUri = "";
            durationMs = -1L;
            basePositionMs = 0L;
            lastReportedPositionMs = -1L;
            positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime();
            playbackSpeed = 0f;
            trackKey = "";
            timeline = LrcTimeline.EMPTY;
            lyricLoadFinished = false;
            lyricSourceName = "";
            liveSessionLyric = "";
            netEaseAutoScrollUnsupported = false;
            trackGeneration++;
            cancelLyricLoadLocked();
        }
        if (appContext != null) AudioSpectrumSource.setPlaybackActive(appContext, false);
        if (hadState && appContext != null) {
            DiagnosticLog.record(appContext, "Playback", "state cleared: no usable session");
        }
    }

    static MusicSnapshot snapshot(int lyricOffsetMs) {
        synchronized (LOCK) {
            long position = currentPositionLocked();
            return snapshotLocked(position, Math.max(0L, position + lyricOffsetMs));
        }
    }

    static String activeSourceId() {
        synchronized (LOCK) {
            return source;
        }
    }

    static MusicSnapshot snapshotForLyricBrowse(int lyricOffsetMs, long lyricPositionMs) {
        synchronized (LOCK) {
            long position = Math.max(0L, lyricPositionMs - lyricOffsetMs);
            return snapshotLocked(position, Math.max(0L, lyricPositionMs));
        }
    }

    static long shiftLyricPosition(long lyricPositionMs, int direction) {
        synchronized (LOCK) {
            return timeline.shiftedPosition(lyricPositionMs, direction);
        }
    }

    private static MusicSnapshot snapshotLocked(long position, long lyricPosition) {
        boolean catalogLyricAvailable = !timeline.isEmpty();
        boolean liveLyricAvailable = isLiveSessionLyricFallbackAvailable(source,
                lyricLoadFinished, timeline, liveSessionLyric);
        LrcTimeline.At at = liveLyricAvailable
                ? LrcTimeline.liveLine(liveSessionLyric) : timeline.at(lyricPosition);
        String displayedLyricSource = liveLyricAvailable
                ? liveSessionLyricSourceName(source) : lyricSourceName;
        return new MusicSnapshot(active, playing, sourceName, title, artist, albumArt, durationMs,
                position, lyricLoadFinished, catalogLyricAvailable || liveLyricAvailable,
                displayedLyricSource, at);
    }

    static void reloadLyrics(Context context) {
        reloadLyrics(context, null, null);
    }

    static void reloadLyrics(Context context, String overrideTitle, String overrideArtist) {
        reloadLyrics(context, overrideTitle, overrideArtist, null);
    }

    static void reloadLyrics(Context context, String overrideTitle, String overrideArtist,
                             String selectedCatalogOverride) {
        initialize(context);
        long generation;
        String requestedSource;
        String requestedSourcePackage;
        String requestedMediaId;
        String requestedMediaUri;
        String requestedTitle;
        String requestedArtist;
        long requestedDuration;
        String selectedCatalog;
        boolean playerCatalogFallback = AppPreferences.playerCatalogFallback(context);
        boolean forcedPlayerCatalog;
        synchronized (LOCK) {
            if (TextUtils.isEmpty(title)) return;
            requestedSource = source;
            requestedSourcePackage = sourcePackage;
            selectedCatalog = selectedCatalogOverride == null
                    ? AppPreferences.lyricCatalog(context, requestedSource, sourcePackage)
                    : selectedCatalogOverride;
            forcedPlayerCatalog = selectedCatalogOverride == null
                    && AppPreferences.hasForcedPlayerPackageCatalog(context, sourcePackage);
            requestedMediaId = mediaId;
            requestedMediaUri = mediaUri;
            requestedTitle = TextUtils.isEmpty(overrideTitle) ? title : overrideTitle.trim();
            requestedArtist = overrideArtist == null ? artist : overrideArtist.trim();
            requestedDuration = durationMs;
            trackKey = lyricTrackKey(requestedSource, title, artist,
                    requestedDuration, requestedMediaId, selectedCatalog,
                    playerCatalogFallback);
            timeline = LrcTimeline.EMPTY;
            lyricLoadFinished = false;
            lyricSourceName = "";
            liveSessionLyric = "";
            netEaseAutoScrollUnsupported = false;
            generation = ++trackGeneration;
            cancelLyricLoadLocked();
        }
        DiagnosticLog.record(context, "Lyrics", "manual reload generation=" + generation
                + " source=" + requestedSource + " selected=" + selectedCatalog
                + " playerFallback=" + playerCatalogFallback + " title=" + requestedTitle);
        scheduleLyricLoad(generation, requestedSource, requestedSourcePackage,
                requestedMediaId, requestedMediaUri, requestedTitle,
                requestedArtist, requestedDuration, selectedCatalog, playerCatalogFallback,
                forcedPlayerCatalog);
    }

    static String describe(Context context) {
        MusicSnapshot snapshot = snapshot(AppPreferences.lyricOffsetMs(context));
        if (!snapshot.active) return "等待兼容的音乐播放器";
        String lyricState = snapshot.lyricAvailable ? "歌词已就绪"
                : snapshot.lyricLoaded ? "未匹配到歌词" : "正在匹配歌词";
        if (snapshot.lyricAvailable && !snapshot.lyricSourceName.isEmpty()) {
            lyricState += " · " + snapshot.lyricSourceName;
        }
        return snapshot.sourceName + " · " + (snapshot.playing ? "播放中" : "已暂停")
                + "\n" + snapshot.title
                + (snapshot.artist.isEmpty() ? "" : " · " + snapshot.artist)
                + "\n" + lyricState;
    }

    static String diagnosticDetails() {
        synchronized (LOCK) {
            long updatedAgeMs = positionUpdatedAtElapsedMs <= 0L ? -1L
                    : Math.max(0L, SystemClock.elapsedRealtime() - positionUpdatedAtElapsedMs);
            return "trackGeneration=" + trackGeneration
                    + "\nsourceId=" + source
                    + "\nsourcePackage=" + sourcePackage
                    + "\nmediaIdPresent=" + !TextUtils.isEmpty(mediaId)
                    + "\nalbumArtLoaded=" + (albumArt != null)
                    + "\nalbumArtUriPresent=" + !TextUtils.isEmpty(albumArtUri)
                    + "\nloadingAlbumArt=" + !TextUtils.isEmpty(loadingAlbumArtUri)
                    + "\nbasePositionMs=" + basePositionMs
                    + "\nlastReportedPositionMs=" + lastReportedPositionMs
                    + "\npositionUpdateAgeMs=" + updatedAgeMs
                    + "\nplaybackSpeed=" + playbackSpeed
                    + "\nlyricLoadFinished=" + lyricLoadFinished
                    + "\nlyricLineCount=" + timeline.lineCount()
                    + "\nlyricLoadTaskActive=" + (lyricLoadTask != null
                    && !lyricLoadTask.isDone())
                    + "\nnetEaseAutoScrollUnsupported=" + netEaseAutoScrollUnsupported
                    + "\nliveSessionLyricPresent=" + !TextUtils.isEmpty(liveSessionLyric);
        }
    }

    private static void scheduleLyricLoad(long generation, String requestedSource,
                                          String requestedSourcePackage,
                                          String requestedMediaId, String requestedMediaUri,
                                          String requestedTitle, String requestedArtist,
                                          long requestedDuration, String selectedCatalog,
                                          boolean playerCatalogFallback,
                                          boolean forcedPlayerCatalog) {
        synchronized (LOCK) {
            if (generation != trackGeneration) return;
            lyricLoadTask = LYRIC_EXECUTOR.submit(() -> {
                long startedAt = SystemClock.elapsedRealtime();
                DiagnosticLog.record(appContext, "Lyrics", "load task started generation="
                        + generation + " source=" + requestedSource + " title=" + requestedTitle);
                try {
                    MultiSourceLyricClient.Result result = lyricClient.load(requestedSource,
                            selectedCatalog, playerCatalogFallback, forcedPlayerCatalog,
                            requestedSourcePackage, requestedMediaId, requestedMediaUri,
                            requestedTitle, requestedArtist, requestedDuration);
                    synchronized (LOCK) {
                        if (generation != trackGeneration) {
                            DiagnosticLog.record(appContext, "Lyrics", "load result discarded generation="
                                    + generation + " currentGeneration=" + trackGeneration);
                            return;
                        }
                        timeline = result.timeline;
                        lyricSourceName = result.sourceName;
                        lyricLoadFinished = true;
                    }
                    DiagnosticLog.record(appContext, "Lyrics", "load task finished generation="
                            + generation + " provider=" + result.providerId + " lines="
                            + result.timeline.lineCount() + " elapsedMs="
                            + (SystemClock.elapsedRealtime() - startedAt));
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to load lyric for " + requestedTitle, error);
                    synchronized (LOCK) {
                        if (generation == trackGeneration) lyricLoadFinished = true;
                    }
                    DiagnosticLog.record(appContext, "Lyrics", "load task failed generation="
                            + generation + " elapsedMs="
                            + (SystemClock.elapsedRealtime() - startedAt) + " error="
                            + error.getClass().getSimpleName() + ": "
                            + (error.getMessage() == null ? "" : error.getMessage()));
                }
            });
        }
    }

    private static void cancelLyricLoadLocked() {
        if (lyricLoadTask != null) {
            lyricLoadTask.cancel(true);
            lyricLoadTask = null;
        }
    }

    private static void scheduleAlbumArtLoad(long generation, String address) {
        ART_EXECUTOR.execute(() -> {
            Bitmap loaded = AlbumArtLoader.load(appContext, address);
            synchronized (LOCK) {
                if (loaded == null) return;
                if (TextUtils.equals(address, loadingAlbumArtUri)) loadingAlbumArtUri = "";
                if (generation == trackGeneration && TextUtils.equals(address, albumArtUri)) {
                    albumArt = loaded;
                }
            }
        });
    }

    private static void scheduleCatalogAlbumArtLoad(long generation, String requestedTitle,
                                                     String requestedArtist,
                                                     long requestedDuration) {
        ART_EXECUTOR.execute(() -> {
            String address = CoverArtSearchClient.find(requestedTitle, requestedArtist,
                    requestedDuration);
            if (TextUtils.isEmpty(address)) return;
            Bitmap loaded = AlbumArtLoader.load(appContext, address);
            if (loaded == null) return;
            synchronized (LOCK) {
                if (generation == trackGeneration && albumArt == null) albumArt = loaded;
            }
        });
    }

    private static long currentPositionLocked() {
        long position = Math.max(0L, basePositionMs);
        if (active && playing && playbackSpeed != 0f) {
            position += (long) ((SystemClock.elapsedRealtime() - positionUpdatedAtElapsedMs)
                    * playbackSpeed);
        }
        if (durationMs > 0L) position = Math.min(position, durationMs);
        return Math.max(0L, position);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    /**
     * Some automotive MediaSession implementations publish metadata and position updates while
     * leaving playbackState at STATE_NONE. The notification listener already treats those
     * sessions as usable, so hiding them here made every such player look unsupported.
     */
    static boolean isDisplayableSession(String sessionTitle, int stateValue) {
        return sessionTitle != null && !sessionTitle.trim().isEmpty()
                && stateValue != MusicPlaybackData.STATE_STOPPED
                && stateValue != MusicPlaybackData.STATE_ERROR;
    }

    static boolean isPositionAdvancing(String sessionTitle, boolean statePresent,
                                       int stateValue, boolean sampledProgress) {
        if (stateValue == MusicPlaybackData.STATE_PLAYING
                || stateValue == MusicPlaybackData.STATE_FAST_FORWARDING
                || stateValue == MusicPlaybackData.STATE_REWINDING) return true;
        // A real PlaybackState object with STATE_NONE is a common car-player substitute for
        // PLAYING. A missing PlaybackState is not enough evidence to start a clock at zero.
        return sampledProgress || statePresent && stateValue == MusicPlaybackData.STATE_NONE
                && sessionTitle != null && !sessionTitle.trim().isEmpty();
    }

    static boolean hasMeaningfulPositionChange(long previousPosition, long newPosition) {
        return previousPosition >= 0L && Math.abs(newPosition - previousPosition) > 100L;
    }

    static String lyricTrackKey(String source, String title, String artist, long durationMs,
                                String mediaId, String selectedCatalog,
                                boolean playerCatalogFallback) {
        String directMediaId = "";
        if ("netease".equals(source)) {
            long songId = NetEaseLyricClient.parseSongId(mediaId);
            if (songId > 0L) directMediaId = Long.toString(songId);
        } else if ("soda".equals(source)) {
            directMediaId = SodaLyricClient.trackId(mediaId);
        }
        // Duration and opaque media IDs often arrive late or oscillate on car players. Soda's
        // numeric track ID is the catalog ID used by its lyric endpoint, so it is stable enough
        // to distinguish consecutive songs even when title/artist metadata arrives in stages.
        return safe(source) + "\n" + identityText(title) + "\n" + identityText(artist)
                + "\n" + directMediaId + "\n" + safe(selectedCatalog)
                + "\n" + playerCatalogFallback;
    }

    static boolean shouldKeepSodaTrackIdentity(String incomingSource, boolean sameSource,
                                               String currentTitle, String incomingTitle,
                                               String currentArtist, String incomingArtist,
                                               long currentDuration, long incomingDuration) {
        return shouldKeepSodaTrackIdentity(incomingSource, sameSource,
                currentTitle, incomingTitle, currentArtist, incomingArtist,
                currentDuration, incomingDuration, "", "");
    }

    static boolean shouldKeepSodaTrackIdentity(String incomingSource, boolean sameSource,
                                               String currentTitle, String incomingTitle,
                                               String currentArtist, String incomingArtist,
                                               long currentDuration, long incomingDuration,
                                               String currentMediaId, String incomingMediaId) {
        return shouldKeepLiveLyricTrackIdentity(incomingSource, sameSource,
                currentTitle, incomingTitle, currentArtist, incomingArtist,
                currentDuration, incomingDuration, currentMediaId, incomingMediaId);
    }

    static boolean shouldKeepLiveLyricTrackIdentity(String incomingSource, boolean sameSource,
                                                     String currentTitle, String incomingTitle,
                                                     String currentArtist, String incomingArtist,
                                                     long currentDuration, long incomingDuration,
                                                     String currentMediaId, String incomingMediaId) {
        if (!sameSource || !usesLiveTitleMetadata(incomingSource)
                || safe(currentTitle).trim().isEmpty()) return false;
        if (!"soda".equals(incomingSource)) {
            return shouldKeepTitleAsLiveLyric(currentTitle, incomingTitle,
                    currentArtist, incomingArtist, currentDuration, incomingDuration,
                    currentMediaId, incomingMediaId);
        }
        String currentTrackId = SodaLyricClient.trackId(currentMediaId);
        String incomingTrackId = SodaLyricClient.trackId(incomingMediaId);
        if (!currentTrackId.isEmpty() && !incomingTrackId.isEmpty()
                && !currentTrackId.equals(incomingTrackId)) {
            return false;
        }
        boolean titleChanged = !safe(incomingTitle).trim().isEmpty()
                && !sameIdentityText(currentTitle, incomingTitle);
        boolean artistChanged = !safe(currentArtist).trim().isEmpty()
                && !safe(incomingArtist).trim().isEmpty()
                && !sameIdentityText(currentArtist, incomingArtist);
        if (!titleChanged && !artistChanged) return false;
        boolean durationChanged = currentDuration > 0L && incomingDuration > 0L
                && Math.abs(currentDuration - incomingDuration) > 2_000L;
        // Live-lyric mode mutates one identity field at a time. A simultaneous title+artist
        // replacement is a real track switch even when DURATION is stale or temporarily absent.
        return !(titleChanged && artistChanged || durationChanged && titleChanged);
    }

    /** Conservative generic rule: only TITLE changes while all track identity evidence holds. */
    private static boolean shouldKeepTitleAsLiveLyric(String currentTitle, String incomingTitle,
                                                       String currentArtist, String incomingArtist,
                                                       long currentDuration, long incomingDuration,
                                                       String currentMediaId, String incomingMediaId) {
        if (!safe(currentMediaId).trim().isEmpty()
                && !safe(incomingMediaId).trim().isEmpty()
                && !safe(currentMediaId).equals(safe(incomingMediaId))) {
            return false;
        }
        boolean titleChanged = !safe(incomingTitle).trim().isEmpty()
                && !sameIdentityText(currentTitle, incomingTitle);
        boolean artistChanged = !safe(currentArtist).trim().isEmpty()
                && !safe(incomingArtist).trim().isEmpty()
                && !sameIdentityText(currentArtist, incomingArtist);
        boolean durationChanged = currentDuration > 0L && incomingDuration > 0L
                && Math.abs(currentDuration - incomingDuration) > 2_000L;
        return titleChanged && !artistChanged && !durationChanged;
    }

    static String sodaStableArtist(String stableTitle, String rawArtist) {
        String titleValue = safe(stableTitle).trim();
        String artistValue = safe(rawArtist).trim();
        if (titleValue.isEmpty() || artistValue.length() <= titleValue.length()
                || !artistValue.regionMatches(true, 0, titleValue, 0, titleValue.length())) {
            return artistValue;
        }
        String suffix = artistValue.substring(titleValue.length()).trim();
        if (suffix.matches("^[—–\\-·|:：,，].*")) {
            return suffix.replaceFirst("^[—–\\-·|:：,，]+\\s*", "").trim();
        }
        return artistValue;
    }

    static String sodaTitleFromDynamicArtist(String rawArtist) {
        String value = safe(rawArtist).trim();
        int separator = value.lastIndexOf(" — ");
        if (separator < 1) separator = value.lastIndexOf(" – ");
        if (separator < 1) separator = value.lastIndexOf(" - ");
        return separator < 1 ? "" : value.substring(0, separator).trim();
    }

    static boolean isLiveSessionLyricFallbackAvailable(String source, boolean loadFinished,
                                                        LrcTimeline catalogTimeline,
                                                        String liveLyric) {
        return usesLiveTitleMetadata(source) && (loadFinished || "soda".equals(source))
                && (catalogTimeline == null || catalogTimeline.isEmpty())
                && !TextUtils.isEmpty(liveLyric);
    }

    static boolean isNetEaseAutoScrollUnsupported(String source, String rawTitle) {
        if (!"netease".equals(source) || rawTitle == null) return false;
        String normalized = rawTitle.replaceAll("\\s+", "");
        return normalized.contains("该歌词不支持自动滚动");
    }

    private static boolean usesLiveTitleMetadata(String source) {
        // Any MediaSession publisher may use title as its current lyric. The track-identity
        // checks above are intentionally source-neutral so this remains safe for unknown apps.
        return !TextUtils.isEmpty(source);
    }

    private static String liveSessionLyricSourceName(String source) {
        if ("qqmusic".equals(source)) return "QQ 实时歌词";
        if ("soda".equals(source)) return "汽水实时歌词";
        if ("kugou".equals(source)) return "酷狗实时歌词";
        if ("kuwo".equals(source)) return "酷我实时歌词";
        if ("netease".equals(source)) return "网易云实时歌词";
        return "播放器实时歌词";
    }

    private static boolean sameIdentityText(String left, String right) {
        String normalizedLeft = identityText(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(identityText(right));
    }

    private static String identityText(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }
}
