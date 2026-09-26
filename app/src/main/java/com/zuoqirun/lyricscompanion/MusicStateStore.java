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
import java.util.concurrent.atomic.AtomicReference;

final class MusicStateStore {
    private static final String TAG = "LyricsMusicState";
    private static final Object LOCK = new Object();
    private static final ExecutorService LYRIC_EXECUTOR = Executors.newCachedThreadPool();
    private static final ExecutorService ART_EXECUTOR = Executors.newSingleThreadExecutor();
    /** One arbitration line per channel change; the debounce inside the arbiter bounds the rest. */
    private static final long SOURCE_LOG_INTERVAL_MS = 2_000L;

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
    private static AtomicReference<Thread> lyricLoadThread = new AtomicReference<>();
    private static boolean usingSessionTimeline;
    private static boolean sessionTimelineAllowed = true;
    private static boolean notificationProgressUnknown;
    private static final SessionChannelArbiter CHANNEL_ARBITER = new SessionChannelArbiter();
    /** Watches a matched timeline for "matched but will not scroll" (issue #44). */
    private static final StuckLineDetector STUCK_LINE_DETECTOR = new StuckLineDetector();
    private static long lastSourceLogElapsedMs;

    private MusicStateStore() {}

    static String activeLyricOffsetKey() {
        synchronized (LOCK) {
            if (title == null || title.trim().isEmpty()) return "";
            return MatchedLyricCache.key("track", title, artist, durationMs, "", sourcePackage);
        }
    }

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
        String compositeTitle = readsTitleFromArtist(context, normalizedSource)
                ? titleFromCompositeArtist(rawArtist) : "";
        boolean hasCompositeIdentity = !TextUtils.isEmpty(compositeTitle)
                && !sameIdentityText(rawTitle, compositeTitle);
        String newTitle = hasCompositeIdentity ? compositeTitle : rawTitle;
        String newArtist = hasCompositeIdentity
                ? stableArtistFromComposite(newTitle, rawArtist) : rawArtist;
        String incomingLiveSessionLyric = data.sessionLyricPresent
                ? data.sessionLyric : hasCompositeIdentity ? rawTitle : "";
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
            long now = SystemClock.elapsedRealtime();
            // A Bluetooth AVRCP broadcast and a MediaSession publisher (CarPlay and friends) can
            // both describe the same playback. Letting each write through swaps the track key on
            // every broadcast, which drops the lyric timeline and redraws the panel — the reported
            // flicker. Only one channel owns the state; the other is ignored until it wins.
            SessionChannelArbiter.Signal incomingSignal = SessionChannelArbiter.Signal.of(
                    normalizedSource, normalizedSourcePackage, data);
            SessionChannelArbiter.Decision decision = CHANNEL_ARBITER.decide(incomingSignal, now);
            if (decision.startsFlap) {
                DiagnosticLog.record(context, "Playback", "active source flapping from="
                        + decision.flapFrom + " to=" + decision.flapTo
                        + " kept=" + decision.signal.describe()
                        + " holdMs=" + SessionChannelArbiter.HOLD_MS);
            } else if (decision.accepted && !"same_publisher".equals(decision.reason)
                    && now - lastSourceLogElapsedMs >= SOURCE_LOG_INTERVAL_MS) {
                lastSourceLogElapsedMs = now;
                DiagnosticLog.record(context, "Playback", "active source " + decision.reason
                        + " source=" + normalizedSource + " package="
                        + normalizedSourcePackage);
            }
            if (!decision.accepted) return;
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
            } else if (!hasCompositeIdentity && anchoredCompositeIdentity(
                    title, newTitle, newArtist, sameSource,
                    stateValue == MusicPlaybackData.STATE_PLAYING, mediaId, newMediaId)) {
                // LX-X Music 这类播放器把当前歌词行写进 TITLE、同时把 ARTIST 变成「歌名 - 歌手」：两个
                // 字段一起变，通用规则会当成换歌，歌词就在「匹配到」和「暂无匹配歌词」之间来回跳
                // （issue #68）。这里把它当作「歌词行 + 复合歌手」的实时歌词更新：标题锚定在已匹配的
                // 歌名上，歌手取去掉前缀的干净版本，原始 TITLE 作为实时歌词显示。
                Log.i(TAG, "Anchored composite identity from artist prefix: " + newTitle);
                DiagnosticLog.record(context, "Playback",
                        "anchored identity from prefix artist=" + newArtist);
                if (!sameIdentityText(newTitle, title)) incomingLiveSessionLyric = newTitle;
                newTitle = title;
                newArtist = stableArtistFromComposite(title, newArtist);
                if (!TextUtils.isEmpty(mediaId)) newMediaId = mediaId;
                if (durationMs > 0L) newDuration = durationMs;
            } else if (!hasCompositeIdentity && shouldKeepLiveLyricTrackIdentity(
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
            boolean changed = !TextUtils.equals(trackKey, newTrackKey)
                    || !TextUtils.equals(sourcePackage, normalizedSourcePackage);
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
            boolean transientZeroPosition = !changed && estimatedPosition > 2_500L
                    && lastReportedPositionMs > 2_500L && newPosition <= 1_000L
                    // Navigation prompts on several head units briefly replace the session
                    // with a state-only update at position zero. Keep the same track's clock
                    // instead of restarting secondary-display lyrics from the first line.
                    && (stateValue != MusicPlaybackData.STATE_STOPPED
                    && stateValue != MusicPlaybackData.STATE_ERROR);
            if (!changed && (transientZeroPosition || newPlaying && reportedPositionTime <= 0L
                    && !reportedPositionChanged)) {
                // Metadata-only automotive sessions commonly keep returning the same raw
                // position. Preserve our monotonic estimate instead of resetting it every poll
                // or when a transient navigation session reports position zero.
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
            notificationProgressUnknown = !data.statePresent && data.positionMs < 0L;
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
                usingSessionTimeline = false;
                sessionTimelineAllowed = true;
                timeline = LrcTimeline.EMPTY;
                trackTimelineLocked(timeline);
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
                trackTimelineLocked(timeline);
                lyricLoadFinished = true;
                lyricSourceName = "";
                liveSessionLyric = "";
                cancelLyricLoadLocked();
                generationToLoad = -1L;
            } else {
                netEaseAutoScrollUnsupported = false;
                if (wasNetEaseUnsupported && !changed) {
                    timeline = LrcTimeline.EMPTY;
                    trackTimelineLocked(timeline);
                    lyricLoadFinished = false;
                    lyricSourceName = "";
                    liveSessionLyric = "";
                    generationToLoad = ++trackGeneration;
                    cancelLyricLoadLocked();
                }
            }
            if (!netEaseUnsupported && data.sessionLyricPresent) {
                liveSessionLyric = incomingLiveSessionLyric.trim();
            } else if (!netEaseUnsupported && usesLiveTitleMetadata(normalizedSource)
                    && !TextUtils.isEmpty(incomingLiveSessionLyric)) {
                liveSessionLyric = incomingLiveSessionLyric.trim();
            }
            if (sessionTimelineAllowed && "kuwo".equals(normalizedSource)
                    && (selectedCatalog.isEmpty() || "auto".equals(selectedCatalog)
                    || "kuwo".equals(selectedCatalog)) && !data.sessionTimeline.isEmpty()) {
                if (timeline != data.sessionTimeline) {
                    timeline = data.sessionTimeline;
                    trackTimelineLocked(timeline);
                    lyricSourceName = "酷我播放器歌词";
                    lyricLoadFinished = true;
                    usingSessionTimeline = true;
                    DiagnosticLog.record(context, "Kuwo", "session lines=" + timeline.lineCount());
                }
                // Still let the worker check user-provided local LRC first.
                if (!AppPreferences.localLyricEnabled(context)) {
                    cancelLyricLoadLocked();
                    generationToLoad = -1L;
                }
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
            notificationProgressUnknown = false;
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
            trackTimelineLocked(timeline);
            lyricLoadFinished = false;
            lyricSourceName = "";
            liveSessionLyric = "";
            netEaseAutoScrollUnsupported = false;
            trackGeneration++;
            usingSessionTimeline = false;
            sessionTimelineAllowed = true;
            CHANNEL_ARBITER.reset();
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

    /**
     * Whether a write would win channel arbitration, without writing anything. The notification
     * listener asks before it remembers a player package or logs an active-player change, so a
     * write that {@link #update} is about to reject leaves no side effects behind. Informational
     * only: {@code update} always decides again and is the authority.
     */
    static boolean isChannelAccepted(String newSource, String newSourcePackage,
                                     MusicPlaybackData data) {
        if (data == null) return true;
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            return CHANNEL_ARBITER.wouldAccept(SessionChannelArbiter.Signal.of(
                    TextUtils.isEmpty(newSource) ? "media" : newSource,
                    newSourcePackage == null ? "" : newSourcePackage.trim(), data), now);
        }
    }

    static MusicSnapshot snapshotForLyricBrowse(int lyricOffsetMs, long lyricPositionMs) {
        synchronized (LOCK) {
            long position = Math.max(0L, lyricPositionMs - lyricOffsetMs);
            return snapshotLocked(position, Math.max(0L, lyricPositionMs), false);
        }
    }

    static long shiftLyricPosition(long lyricPositionMs, int direction) {
        synchronized (LOCK) {
            return timeline.shiftedPosition(lyricPositionMs, direction);
        }
    }

    private static MusicSnapshot snapshotLocked(long position, long lyricPosition) {
        return snapshotLocked(position, lyricPosition, true);
    }

    /**
     * @param observePlayback false for the lyric browser, which scrubs an arbitrary position: those
     *                        synthetic positions must not feed the stuck-line detection.
     */
    private static MusicSnapshot snapshotLocked(long position, long lyricPosition,
                                                boolean observePlayback) {
        LrcTimeline.At catalogAt = timeline.at(lyricPosition);
        boolean liveLyricAvailable = isLiveSessionLyricFallbackAvailable(source,
                lyricLoadFinished, timeline, liveSessionLyric);
        // Every hand-over below needs a live lyric that really renders (issue #52): without one the
        // panel would leave a usable matched timeline for an empty current line and show 「即将开始」
        // for the rest of the track.
        boolean liveLyricUsable = isLiveLyricUsable(liveSessionLyric);
        if (!liveLyricAvailable && liveLyricUsable && observePlayback) {
            // The matched timeline is the one on screen: only now can "it never scrolls" (issue #44)
            // apply. A true result hands the display to the live lyric below, exactly like the
            // existing "nothing matched" fallback does. The playback clock is the unshifted
            // position, so a lyric offset cannot look like a seek.
            liveLyricAvailable = observeStuckLineLocked(catalogAt.lineStartMs, position,
                    SystemClock.elapsedRealtime());
        }
        boolean catalogLyricAvailable = !timeline.isEmpty();
        LrcTimeline.At at = atLocked(catalogAt, liveSessionLyric, liveLyricAvailable);
        String displayedLyricSource = liveLyricAvailable
                ? liveSessionLyricSourceName(source) : lyricSourceName;
        return new MusicSnapshot(active, playing, sourceName, title, artist, albumArt, durationMs,
                position, lyricLoadFinished, catalogLyricAvailable || liveLyricAvailable,
                displayedLyricSource, at);
    }

    /**
     * The line the panel renders: the player's live lyric when it takes over, the matched timeline
     * line otherwise.
     *
     * <p>{@code liveLyricAvailable} is the verdict of
     * {@link #isLiveSessionLyricFallbackAvailable(String, boolean, LrcTimeline, String, boolean)},
     * which never hands over on a blank live lyric (issue #52), so a live {@code At} always has text
     * to show. Deciding here keeps that invariant in one place, and — taking both inputs as
     * arguments — testable without the playback globals.</p>
     */
    static LrcTimeline.At atLocked(LrcTimeline.At catalogAt, String liveLyric,
                                   boolean liveLyricAvailable) {
        return liveLyricAvailable ? LrcTimeline.liveLine(liveLyric) : catalogAt;
    }

    /**
     * Feeds one frame to the stuck-line detection and answers whether the matched timeline has to
     * be given up (issue #44).
     *
     * <p>Returns {@code false} — leaving the behaviour of every snapshot untouched — while the
     * preference is off, and while no timeline is matched (that case already falls back). A
     * verdict is written to the diagnostic log once per timeline.</p>
     *
     * @param lineId     start timestamp of the line the timeline shows, its identity
     * @param positionMs the real playback position, which the stuck clock is measured against
     */
    private static boolean observeStuckLineLocked(long lineId, long positionMs,
                                                  long nowElapsedMs) {
        if (timeline.isEmpty() || appContext == null
                || !AppPreferences.stuckLyricFallback(appContext)) return false;
        STUCK_LINE_DETECTOR.onFrame(StuckLineDetector.Signal.of(lineId, positionMs,
                liveSessionLyric, playing, durationMs, nowElapsedMs));
        if (!STUCK_LINE_DETECTOR.timelineUnusable()) return false;
        if (STUCK_LINE_DETECTOR.consumeFallbackNotice()) {
            DiagnosticLog.record(appContext, "Lyrics", STUCK_LINE_DETECTOR.describe());
        }
        return true;
    }

    /**
     * Points the stuck-line detection at the timeline now in play. Called wherever {@code timeline}
     * is replaced — including with {@code LrcTimeline.EMPTY} — so a verdict is never carried over
     * to a different match.
     */
    private static void trackTimelineLocked(LrcTimeline value) {
        STUCK_LINE_DETECTOR.onTimeline(value == null ? 0 : value.lineCount(),
                timelineSpanMs(value));
    }

    /**
     * Total span of a timeline: the last line's start plus how long that line is shown.
     * {@link LrcTimeline} exposes no total duration, so the last line is located with
     * {@code at(Long.MAX_VALUE)}, where a missing per-line duration already falls back to the 5 s
     * hold. The value only feeds the coarse {@link StuckLineDetector#DURATION_RATIO}× comparison,
     * so that approximation does not matter.
     */
    private static long timelineSpanMs(LrcTimeline value) {
        if (value == null || value.isEmpty()) return 0L;
        LrcTimeline.At last = value.at(Long.MAX_VALUE);
        return last.lineStartMs + Math.max(0L, last.lineDurationMs);
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
            if ("kuwo".equals(requestedSource)
                    && (!TextUtils.isEmpty(overrideTitle) || overrideArtist != null)) {
                requestedMediaId = "";
            }
            requestedMediaUri = mediaUri;
            requestedTitle = TextUtils.isEmpty(overrideTitle) ? title : overrideTitle.trim();
            requestedArtist = overrideArtist == null ? artist : overrideArtist.trim();
            requestedDuration = durationMs;
            trackKey = lyricTrackKey(requestedSource, title, artist,
                    requestedDuration, mediaId, selectedCatalog,
                    playerCatalogFallback);
            timeline = LrcTimeline.EMPTY;
            trackTimelineLocked(timeline);
            lyricLoadFinished = false;
            lyricSourceName = "";
            liveSessionLyric = "";
            netEaseAutoScrollUnsupported = false;
            usingSessionTimeline = false;
            sessionTimelineAllowed = false;
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
        boolean unknownProgress;
        synchronized (LOCK) { unknownProgress = notificationProgressUnknown; }
        return snapshot.sourceName + " · " + (unknownProgress ? "已识别歌曲，播放器未提供进度"
                : snapshot.playing ? "播放中" : "已暂停")
                + "\n" + snapshot.title
                + (snapshot.artist.isEmpty() ? "" : " · " + snapshot.artist)
                + "\n" + lyricState
                // 「为什么没有歌词」的一句话结论（issue #62）：U 盘 / 视频场景最常见的原因直接写出来。
                + "\n" + MediaDiagnosisRules.verdictText(MediaDiagnosisRules.classify(
                snapshot.active, !snapshot.title.isEmpty(), !unknownProgress,
                snapshot.lyricLoaded, snapshot.lyricAvailable, snapshot.lyricSourceName),
                snapshot.lyricSourceName);
    }

    static String diagnosticDetails() {
        synchronized (LOCK) {
            long updatedAgeMs = positionUpdatedAtElapsedMs <= 0L ? -1L
                    : Math.max(0L, SystemClock.elapsedRealtime() - positionUpdatedAtElapsedMs);
            return "trackGeneration=" + trackGeneration
                    + "\nsourceId=" + source
                    + "\nsourcePackage=" + sourcePackage
                    + "\nmediaIdPresent=" + !TextUtils.isEmpty(mediaId)
                    // 内嵌歌词完全依赖播放器给的 mediaUri：这一行让"拿不到路径"和"文件里没有标签"
                    // 一眼可分（issue #25）。
                    + "\nmediaUriPresent=" + !TextUtils.isEmpty(mediaUri)
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
                    + "\nliveSessionLyricPresent=" + !TextUtils.isEmpty(liveSessionLyric)
                    // 长度把"播放器没发歌词 / 只发了空白 / 真有词"三种情况分开，空白串正是
                    // 面板停在「即将开始」的那一种（issue #52）。
                    + "\nliveSessionLyricLength=" + liveSessionLyric.length()
                    + "\nliveSessionLyricUsable=" + isLiveLyricUsable(liveSessionLyric)
                    // U 盘 / 视频这类「播放器不给元数据」的场景一眼可判（issue #62）。
                    + "\ndiagnosis=" + MediaDiagnosisRules.verdictText(
                    MediaDiagnosisRules.classify(active, !TextUtils.isEmpty(title),
                            !notificationProgressUnknown, lyricLoadFinished,
                            !timeline.isEmpty() || isLiveLyricUsable(liveSessionLyric),
                            lyricSourceName), lyricSourceName)
                    + "\nkuwoWordChannelBackoffMs=" + KuwoLyricClient.wordChannelBlockedForMs();
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
            final boolean bypassMatchedCache = !sessionTimelineAllowed;
            final AtomicReference<Thread> requestThread = new AtomicReference<>();
            lyricLoadThread = requestThread;
            lyricLoadTask = LYRIC_EXECUTOR.submit(() -> {
                requestThread.set(Thread.currentThread());
                long startedAt = SystemClock.elapsedRealtime();
                DiagnosticLog.record(appContext, "Lyrics", "load task started generation="
                        + generation + " source=" + requestedSource + " title=" + requestedTitle);
                try {
                    MultiSourceLyricClient.Result result = lyricClient.load(requestedSource,
                            selectedCatalog, playerCatalogFallback, forcedPlayerCatalog,
                            requestedSourcePackage, requestedMediaId, requestedMediaUri,
                            requestedTitle, requestedArtist, requestedDuration, () -> {
                                synchronized (LOCK) {
                                    return generation == trackGeneration && usingSessionTimeline
                                            ? timeline : LrcTimeline.EMPTY;
                                }
                            }, bypassMatchedCache);
                    synchronized (LOCK) {
                        if (generation != trackGeneration || usingSessionTimeline
                                && !"local".equals(result.providerId)) {
                            DiagnosticLog.record(appContext, "Lyrics", "load result discarded generation="
                                    + generation + " currentGeneration=" + trackGeneration);
                            return;
                        }
                        timeline = result.timeline;
                        trackTimelineLocked(timeline);
                        lyricSourceName = result.sourceName;
                        lyricLoadFinished = true;
                        if ("local".equals(result.providerId)) {
                            usingSessionTimeline = false;
                            sessionTimelineAllowed = false;
                        }
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
                } finally {
                    requestThread.set(null);
                }
            });
        }
    }

    private static void cancelLyricLoadLocked() {
        if (lyricLoadTask != null) {
            lyricLoadTask.cancel(true);
            Thread thread = lyricLoadThread.get();
            if (thread != null) LyricHttp.cancel(thread);
            lyricLoadTask = null;
        }
        // 换歌时也把还在跑的「缓存后台升级」掐掉：那是上一首歌的请求，别让它继续占线程（issue #74）。
        MultiSourceLyricClient.cancelPendingUpgrade();
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
        } else if ("kuwo".equals(source)) {
            directMediaId = KuwoLyricParser.trackId(mediaId);
        }
        // Duration and opaque media IDs often arrive late or oscillate on car players. Soda/Kuwo's
        // numeric track ID is the catalog ID used by their lyric endpoints, so it is stable enough
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

    /**
     * 「锚定复合身份」判据（issue #68）。
     *
     * <p>LX-X Music（落雪音乐）这类播放器会把**当前歌词行**写进 TITLE，同时把 ARTIST 变成「歌名 - 歌手」。
     * 两个字段一起变，通用规则（只认单字段实时歌词，{@link #shouldKeepLiveLyricTrackIdentity}）会判定成
     * 换歌，于是歌词在「匹配到」和「暂无匹配歌词」之间来回跳。
     *
     * <p>命中条件：来源没变、正在播放、已存标题非空、进来的 ARTIST 以该标题开头（说明它是复合串）、
     * 进来的 TITLE 既不是同一个标题、也不是「歌名 - 歌手」这种结构化标题，且播放器没有给出不同的稳定
     * mediaId。命中时把标题锚定在已匹配的歌名上，ARTIST 取去掉前缀的干净版本，原始 TITLE 当实时歌词。
     */
    static boolean anchoredCompositeIdentity(String storedTitle, String incomingTitle,
                                             String incomingArtist, boolean sameSource,
                                             boolean playing, String storedMediaId,
                                             String incomingMediaId) {
        String stored = safe(storedTitle).trim();
        String title = safe(incomingTitle).trim();
        String artist = safe(incomingArtist).trim();
        if (!sameSource || !playing) return false;
        if (stored.isEmpty() || title.isEmpty() || artist.isEmpty()) return false;
        if (artist.length() <= stored.length()) return false;
        if (!artist.regionMatches(true, 0, stored, 0, stored.length())) return false;
        if (sameIdentityText(title, stored)) return false;
        if (LocalTrackQueryRules.looksLikeStructuredTrackTitle(title)) return false;
        String storedId = safe(storedMediaId).trim();
        String incomingId = safe(incomingMediaId).trim();
        return storedId.isEmpty() || incomingId.isEmpty() || storedId.equals(incomingId);
    }

    static String stableArtistFromComposite(String stableTitle, String rawArtist) {        String titleValue = safe(stableTitle).trim();
        String artistValue = safe(rawArtist).trim();
        if (titleValue.isEmpty() || artistValue.length() <= titleValue.length()
                || !artistValue.regionMatches(true, 0, titleValue, 0, titleValue.length())) {
            return artistValue;
        }
        String suffix = artistValue.substring(titleValue.length()).trim();
        if (suffix.matches("^[—–\\-·•|｜/:：,，].*")) {
            return suffix.replaceFirst("^[—–\\-·•|｜/:：,，]+\\s*", "").trim();
        }
        return artistValue;
    }

    /**
     * Separator shapes seen between the song name and the artist in a composite artist slot,
     * grouped by how safe they are to split on and tried group by group, last match first.
     */
    private static final String[][] COMPOSITE_SEPARATORS = {
            {" — ", " – ", " - "},
            {"·", "•"},
            {" | ", "｜"},
            {"：", ":"},
            {"/", "|", "-"},
    };

    /**
     * The song name inside a composite artist slot such as {@code "稻香 - 周杰伦"} or
     * {@code "稻香·周杰伦"}, or {@code ""} when the text carries no separator to split on.
     *
     * <p>A bare {@code -} or {@code /} is also how plenty of artist names are spelled ({@code
     * A-Lin}, {@code AC/DC}, {@code Lo-Fi}), so those two only count as a separator when there is
     * Chinese text around them — the "歌名-歌手" shape this exists for. Spaced hyphens and the
     * remaining punctuation are unambiguous enough to split on as they are.
     */
    static String titleFromCompositeArtist(String rawArtist) {
        String value = safe(rawArtist).trim();
        for (String[] group : COMPOSITE_SEPARATORS) {
            int index = -1;
            String token = "";
            for (String candidate : group) {
                int found = value.lastIndexOf(candidate);
                if (found >= 1 && found > index) {
                    index = found;
                    token = candidate;
                }
            }
            if (index < 1) continue;
            String title = value.substring(0, index).trim();
            String artist = value.substring(index + token.length()).trim();
            if (title.isEmpty() || artist.isEmpty()) continue;
            if (isAmbiguousSeparator(token) && !containsHan(value)) continue;
            return title;
        }
        return "";
    }

    /** A separator that is also common inside a single artist name. */
    private static boolean isAmbiguousSeparator(String token) {
        return "-".equals(token) || "/".equals(token) || "|".equals(token);
    }

    private static boolean containsHan(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 0x4E00 && character <= 0x9FFF) return true;      // CJK Unified
            if (character >= 0x3400 && character <= 0x4DBF) return true;      // Extension A
        }
        return false;
    }

    /**
     * The fallback for callers that do not run the stuck-line detection
     * ({@link StuckLineDetector}): the matched timeline is never rejected, so the live lyric only
     * takes over while nothing is matched.
     */
    static boolean isLiveSessionLyricFallbackAvailable(String source, boolean loadFinished,
                                                        LrcTimeline catalogTimeline,
                                                        String liveLyric) {
        return isLiveSessionLyricFallbackAvailable(source, loadFinished, catalogTimeline,
                liveLyric, false);
    }

    /**
     * @param timelineUnusable the matched timeline was rejected by {@link StuckLineDetector}
     *                         (issue #44): "matched but it never scrolls" now falls back exactly
     *                         like "nothing matched" does. Callers that do not run the detection
     *                         pass {@code false} and keep the original behaviour.
     */
    static boolean isLiveSessionLyricFallbackAvailable(String source, boolean loadFinished,
                                                        LrcTimeline catalogTimeline,
                                                        String liveLyric, boolean timelineUnusable) {
        if ("dftc_media".equals(source)) return isLiveLyricUsable(liveLyric);
        return usesLiveTitleMetadata(source) && (loadFinished || "soda".equals(source))
                && (timelineUnusable || catalogTimeline == null || catalogTimeline.isEmpty())
                && isLiveLyricUsable(liveLyric);
    }

    /**
     * Whether the player published a live lyric that can actually be shown.
     *
     * <p>A blank string is not a lyric. {@link LrcTimeline#liveLine(String)} trims what it renders,
     * so accepting a blank one here let the display hand the panel over to an empty current line:
     * {@code LyricsPanelView.currentText()} then fell through to 「即将开始」, and because the
     * fallback kept answering "available" it never came back to the catalog timeline it had just
     * abandoned — the panel stayed on that text for the whole track (issue #52). Deciding on the
     * trimmed text keeps the two sides consistent.</p>
     */
    static boolean isLiveLyricUsable(String liveLyric) {
        return liveLyric != null && !liveLyric.trim().isEmpty();
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

    /**
     * Whether a composite artist slot should be reparsed into the track identity.
     *
     * <p>汽水音乐 always does this — its observed metadata depends on it. For everything else the
     * user opts in: the preference is for the channels whose title slot carries a lyric line,
     * which is AVRCP and the generic MediaSession bucket unknown apps land in. Players with their
     * own lyric channel keep the plain "title = song, artist = artist" mapping.
     */
    private static boolean readsTitleFromArtist(Context context, String source) {
        if ("soda".equals(source)) return true;
        if (!AppPreferences.compositeIdentityFromArtist(context)) return false;
        return "bluetooth".equals(source) || "media".equals(source);
    }

    private static String liveSessionLyricSourceName(String source) {
        if ("qqmusic".equals(source)) return "QQ 实时歌词";
        if ("soda".equals(source)) return "汽水实时歌词";
        if ("kugou".equals(source)) return "酷狗实时歌词";
        if ("kuwo".equals(source)) return "酷我实时歌词";
        if ("netease".equals(source)) return "网易云实时歌词";
        if ("ximalaya".equals(source)) return "喜马拉雅实时歌词";
        if ("dftc_media".equals(source)) return "车机播放器实时歌词";
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
