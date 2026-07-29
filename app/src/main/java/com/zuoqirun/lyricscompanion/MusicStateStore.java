package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
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
    private static String mediaId = "";
    private static String title = "";
    private static String artist = "";
    private static Bitmap albumArt;
    private static String albumArtUri = "";
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
                       MediaMetadata metadata, PlaybackState state) {
        initialize(context);
        if (metadata == null && state == null) {
            clear();
            return;
        }
        String newTitle = firstNonEmpty(metadata,
                MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String newArtist = firstNonEmpty(metadata,
                MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String newMediaId = firstNonEmpty(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
        Bitmap newAlbumArt = firstBitmap(metadata,
                MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String newAlbumArtUri = firstNonEmpty(metadata,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        long newDuration = metadata == null ? -1L
                : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
        boolean statePresent = state != null;
        long newPosition = !statePresent || state.getPosition() < 0L ? 0L : state.getPosition();
        long reportedPositionTime = !statePresent ? 0L : state.getLastPositionUpdateTime();
        float newSpeed = state == null ? 0f : state.getPlaybackSpeed();
        String normalizedSource = TextUtils.isEmpty(newSource) ? "media" : newSource;
        String normalizedSourceName = TextUtils.isEmpty(newSourceName)
                ? "音乐播放器" : newSourceName;
        String selectedCatalog = AppPreferences.lyricCatalog(context);
        boolean playerCatalogFallback = AppPreferences.playerCatalogFallback(context);
        long generationToLoad = -1L;
        long generationForAlbumArt = -1L;
        synchronized (LOCK) {
            boolean sameSource = TextUtils.equals(source, normalizedSource);
            if (sameSource && "soda".equals(normalizedSource)
                    && !sameIdentityText(newTitle, title)
                    && timeline.containsLyricText(newTitle)) {
                // Soda Music exposes the currently playing lyric line through TITLE on some
                // phone/car builds. It is display metadata, not a track change.
                Log.i(TAG, "Ignoring Soda lyric line published as title: " + newTitle);
                newTitle = title;
            }
            if (sameSource && TextUtils.isEmpty(newTitle) && !TextUtils.isEmpty(title)) {
                // Several automotive players publish a playback-state-only update after the
                // complete metadata. Treat it as a partial update instead of erasing the track.
                newTitle = title;
                newArtist = artist;
                newMediaId = mediaId;
                newDuration = durationMs;
                newAlbumArtUri = albumArtUri;
                if (newAlbumArt == null) newAlbumArt = albumArt;
            } else if (sameSource && sameIdentityText(newTitle, title)) {
                if (TextUtils.isEmpty(newArtist)) newArtist = artist;
                if (TextUtils.isEmpty(newMediaId)) newMediaId = mediaId;
                if (newDuration <= 0L) newDuration = durationMs;
                if (TextUtils.isEmpty(newAlbumArtUri)) newAlbumArtUri = albumArtUri;
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
            active = newActive;
            playing = newPlaying;
            source = normalizedSource;
            sourceName = normalizedSourceName;
            mediaId = safe(newMediaId);
            title = safe(newTitle);
            artist = safe(newArtist);
            if (newAlbumArt != null) albumArt = newAlbumArt;
            albumArtUri = safe(newAlbumArtUri);
            durationMs = newDuration > 0L ? newDuration : -1L;
            basePositionMs = positionToStore;
            lastReportedPositionMs = newPosition;
            positionUpdatedAtElapsedMs = positionTimeToStore;
            playbackSpeed = effectiveSpeed;
            if (changed) {
                trackKey = newTrackKey;
                timeline = LrcTimeline.EMPTY;
                lyricLoadFinished = false;
                lyricSourceName = "";
                if (newAlbumArt == null) albumArt = null;
                loadingAlbumArtUri = "";
                generationToLoad = ++trackGeneration;
                cancelLyricLoadLocked();
            }
            if (changed || playbackModeChanged) {
                Log.i(TAG, "Position sync state=" + stateValue + " advancing=" + newPlaying
                        + " speed=" + effectiveSpeed + " position=" + positionToStore
                        + " reportedTime=" + reportedPositionTime);
            }
            if (albumArt == null && !TextUtils.isEmpty(albumArtUri)
                    && !TextUtils.equals(albumArtUri, loadingAlbumArtUri)) {
                loadingAlbumArtUri = albumArtUri;
                generationForAlbumArt = trackGeneration;
            }
        }
        if (generationToLoad >= 0L && !TextUtils.isEmpty(newTitle)) {
            scheduleLyricLoad(generationToLoad, normalizedSource, newMediaId,
                    newTitle, newArtist, newDuration, selectedCatalog, playerCatalogFallback);
            if (newAlbumArt == null && TextUtils.isEmpty(newAlbumArtUri)) {
                scheduleCatalogAlbumArtLoad(generationToLoad, newTitle, newArtist, newDuration);
            }
        }
        if (generationForAlbumArt >= 0L) {
            scheduleAlbumArtLoad(generationForAlbumArt, newAlbumArtUri);
        }
    }

    static void clear() {
        synchronized (LOCK) {
            active = false;
            playing = false;
            source = "media";
            sourceName = "音乐播放器";
            mediaId = "";
            title = "";
            artist = "";
            albumArt = null;
            albumArtUri = "";
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
            trackGeneration++;
            cancelLyricLoadLocked();
        }
    }

    static MusicSnapshot snapshot(int lyricOffsetMs) {
        synchronized (LOCK) {
            long position = currentPositionLocked();
            return snapshotLocked(position, Math.max(0L, position + lyricOffsetMs));
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
        LrcTimeline.At at = timeline.at(lyricPosition);
        return new MusicSnapshot(active, playing, sourceName, title, artist, albumArt, durationMs,
                position, lyricLoadFinished, !timeline.isEmpty(), lyricSourceName, at);
    }

    static void reloadLyrics(Context context) {
        initialize(context);
        long generation;
        String requestedSource;
        String requestedMediaId;
        String requestedTitle;
        String requestedArtist;
        long requestedDuration;
        String selectedCatalog = AppPreferences.lyricCatalog(context);
        boolean playerCatalogFallback = AppPreferences.playerCatalogFallback(context);
        synchronized (LOCK) {
            if (TextUtils.isEmpty(title)) return;
            requestedSource = source;
            requestedMediaId = mediaId;
            requestedTitle = title;
            requestedArtist = artist;
            requestedDuration = durationMs;
            trackKey = lyricTrackKey(requestedSource, requestedTitle, requestedArtist,
                    requestedDuration, requestedMediaId, selectedCatalog,
                    playerCatalogFallback);
            timeline = LrcTimeline.EMPTY;
            lyricLoadFinished = false;
            lyricSourceName = "";
            generation = ++trackGeneration;
            cancelLyricLoadLocked();
        }
        scheduleLyricLoad(generation, requestedSource, requestedMediaId, requestedTitle,
                requestedArtist, requestedDuration, selectedCatalog, playerCatalogFallback);
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

    private static void scheduleLyricLoad(long generation, String requestedSource,
                                          String requestedMediaId,
                                          String requestedTitle, String requestedArtist,
                                          long requestedDuration, String selectedCatalog,
                                          boolean playerCatalogFallback) {
        synchronized (LOCK) {
            if (generation != trackGeneration) return;
            lyricLoadTask = LYRIC_EXECUTOR.submit(() -> {
                try {
                    MultiSourceLyricClient.Result result = lyricClient.load(requestedSource,
                            selectedCatalog, playerCatalogFallback, requestedMediaId,
                            requestedTitle, requestedArtist, requestedDuration);
                    synchronized (LOCK) {
                        if (generation != trackGeneration) return;
                        timeline = result.timeline;
                        lyricSourceName = result.sourceName;
                        lyricLoadFinished = true;
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to load lyric for " + requestedTitle, error);
                    synchronized (LOCK) {
                        if (generation == trackGeneration) lyricLoadFinished = true;
                    }
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

    private static String firstNonEmpty(MediaMetadata metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            String value = metadata.getString(key);
            if (!TextUtils.isEmpty(value)) return value.trim();
        }
        return "";
    }

    private static Bitmap firstBitmap(MediaMetadata metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            Bitmap bitmap = metadata.getBitmap(key);
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    /**
     * Some automotive MediaSession implementations publish metadata and position updates while
     * leaving playbackState at STATE_NONE. The notification listener already treats those
     * sessions as usable, so hiding them here made every such player look unsupported.
     */
    static boolean isDisplayableSession(String sessionTitle, int stateValue) {
        return sessionTitle != null && !sessionTitle.trim().isEmpty()
                && stateValue != PlaybackState.STATE_STOPPED
                && stateValue != PlaybackState.STATE_ERROR;
    }

    static boolean isPositionAdvancing(String sessionTitle, boolean statePresent,
                                       int stateValue, boolean sampledProgress) {
        if (stateValue == PlaybackState.STATE_PLAYING
                || stateValue == PlaybackState.STATE_FAST_FORWARDING
                || stateValue == PlaybackState.STATE_REWINDING) return true;
        // A real PlaybackState object with STATE_NONE is a common car-player substitute for
        // PLAYING. A missing PlaybackState is not enough evidence to start a clock at zero.
        return sampledProgress || statePresent && stateValue == PlaybackState.STATE_NONE
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
        }
        // Duration and non-NetEase media IDs often arrive late or oscillate on car players.
        // They refine search ranking but must not turn the same song into a new generation.
        return safe(source) + "\n" + identityText(title) + "\n" + identityText(artist)
                + "\n" + directMediaId + "\n" + safe(selectedCatalog)
                + "\n" + playerCatalogFallback;
    }

    private static boolean sameIdentityText(String left, String right) {
        return !identityText(left).isEmpty()
                && TextUtils.equals(identityText(left), identityText(right));
    }

    private static String identityText(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }
}
