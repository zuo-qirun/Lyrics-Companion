package com.zuoqirun.lyricscompanion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

/**
 * Reads the Dongfeng head-unit player, which does not publish a usable MediaSession.
 *
 * <p>Only the player's own AIDL service is used here. Do not register another WecarFlow
 * Messenger client: on the Dongfeng head unit that connection participates in the native
 * multimedia route, and a second client can make the instrument-cluster multimedia card
 * disappear. Lyrics are resolved through the existing provider catalogs using the title
 * reported by the player.</p>
 */
final class DftcMediaSessionReader implements MusicSessionReader {
    private static final String PLAYER_PACKAGE = "com.dftc.media";
    private static final String PLAYER_SERVICE_ACTION = "com.dftc.media.mediaService";
    private static final String PLAYER_SERVICE = "com.dftc.media.MusicAIDLService";
    private static final String PLAYER_DESCRIPTOR = "com.dftc.media.MusicAIDL";

    private static final int AIDL_CONTINUE = 4;
    private static final int AIDL_PAUSE = 5;
    private static final int AIDL_NEXT = 6;
    private static final int AIDL_PREVIOUS = 7;
    private static final int AIDL_GET_NAME = 10;
    private static final int AIDL_GET_TYPE = 11;
    private static final int AIDL_GET_STATUS = 12;

    /**
     * How long the last good read may be replayed while the vendor stack returns garbage.
     * WecarFlow re-routes can blank GET_NAME/GET_STATUS for a few seconds; without a hold the
     * session flaps in and out and the main/secondary overlays flicker on every poll.
     */
    private static final long TRANSIENT_HOLD_MS = 15_000L;

    private final Context context;
    private final Callback callback;

    private boolean started;
    private boolean playerBound;
    private IBinder playerBinder;
    private long lastUsableSessionElapsedMs;
    private String playerTitle = "";
    private String playerType = "";
    private int playerStatus = -1;

    private final ServiceConnection playerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            playerBinder = service;
            playerBound = true;
            refresh();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            playerBinder = null;
            // Android keeps the binding and reconnects it when the remote process returns.
        }
    };

    DftcMediaSessionReader(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    @Override public void start() {
        if (started || !isPlayerInstalled()) return;
        started = true;
        bindPlayer();
    }

    @Override public void refresh() {
        if (!started) return;
        IBinder binder = playerBinder;
        if (binder == null || !binder.isBinderAlive()) {
            if (!playerBound) bindPlayer();
            callback.onNoSession();
            return;
        }
        try {
            String title = aidlString(AIDL_GET_NAME);
            String type = aidlString(AIDL_GET_TYPE);
            int status = aidlInt(AIDL_GET_STATUS);
            if (isUsableRead(title, status)) {
                if (shouldAcceptTitleChange(playerTitle, playerType, title, type, status)) {
                    acceptRead(title, type, status);
                } else {
                    // The vendor swapped GET_NAME to the current lyric line of the anchored
                    // song. Keep publishing the anchored identity so the matched timeline
                    // survives; only the live status refreshes.
                    playerStatus = status;
                    lastUsableSessionElapsedMs = SystemClock.elapsedRealtime();
                    callback.onReadSuccess(1);
                    emitCurrentSession();
                }
                return;
            }
            if (shouldReuseRetainedSnapshot(playerTitle, true,
                    SystemClock.elapsedRealtime() - lastUsableSessionElapsedMs)) {
                // Transient vendor garbage (empty name / unknown status): keep showing the
                // retained track instead of reporting no-session on every 600 ms poll.
                callback.onReadSuccess(1);
                emitCurrentSession();
                return;
            }
            callback.onReadSuccess(playerTitle.isEmpty() ? 0 : 1);
            callback.onNoSession();
        } catch (Throwable error) {
            // A failed transact with a live binder rides out the same hold window; only log
            // once the retention has expired so a short WecarFlow hiccup does not spam logs.
            if (shouldReuseRetainedSnapshot(playerTitle, true,
                    SystemClock.elapsedRealtime() - lastUsableSessionElapsedMs)) {
                emitCurrentSession();
            } else {
                playerStatus = -1;
                callback.onReadError("读取东风车机播放器失败", error);
            }
        }
    }

    private void acceptRead(String title, String type, int status) {
        playerTitle = title;
        playerType = type;
        playerStatus = status;
        lastUsableSessionElapsedMs = SystemClock.elapsedRealtime();
        callback.onReadSuccess(1);
        emitCurrentSession();
    }

    /**
     * Decides whether a fresh GET_NAME value is a genuine track change. While playing, this
     * vendor alternates the title field between the real song name and the current lyric line
     * (diagnosed 2026-08-22: 《只要你过得比我好》 flipped to 词：小虫 / 曲：Solan Sister and
     * every lyric sentence, each wiping the matched timeline). A plain mutation during steady
     * playback is therefore held on the anchored identity; structured titles ("歌名 - 歌手",
     * track-number prefixes, quality tags), a player-source (type) switch, or a pause boundary
     * still pass through as real switches.
     */
    static boolean shouldAcceptTitleChange(String anchoredTitle, String anchoredType,
                                           String incomingTitle, String incomingType,
                                           int status) {
        if (anchoredTitle.isEmpty()) return true;
        if (incomingTitle.equals(anchoredTitle)) return true;
        if (!incomingType.equals(anchoredType)) return true;
        if (status != 1) return true;
        return LocalTrackQueryRules.looksLikeStructuredTrackTitle(incomingTitle);
    }

    @Override public boolean dispatchControl(MediaControlAction action) {
        IBinder binder = playerBinder;
        if (action == null || binder == null || !binder.isBinderAlive()) return false;
        try {
            int transaction;
            switch (action) {
                case PREVIOUS:
                    transaction = AIDL_PREVIOUS;
                    break;
                case NEXT:
                    transaction = AIDL_NEXT;
                    break;
                case TOGGLE_PLAY_PAUSE:
                    boolean playing = aidlInt(AIDL_GET_STATUS) == 1;
                    transaction = playing ? AIDL_PAUSE : AIDL_CONTINUE;
                    break;
                default:
                    return false;
            }
            aidlVoid(transaction);
            return true;
        } catch (Throwable error) {
            callback.onReadError("控制东风车机播放器失败", error);
            return false;
        }
    }

    @Override public void stop() {
        if (!started) return;
        started = false;
        if (playerBound) {
            try {
                context.unbindService(playerConnection);
            } catch (Throwable ignored) {
                // The remote process may have already removed the connection.
            }
        }
        playerBound = false;
        playerBinder = null;
        playerTitle = "";
        playerType = "";
        playerStatus = -1;
        lastUsableSessionElapsedMs = 0L;
    }

    boolean hasUsableSession() {
        IBinder binder = playerBinder;
        boolean binderAlive = binder != null && binder.isBinderAlive();
        if (!binderAlive) return false;
        return shouldReuseRetainedSnapshot(playerTitle, binderAlive,
                SystemClock.elapsedRealtime() - lastUsableSessionElapsedMs);
    }

    /** Whether the retained snapshot currently describes an actively playing vendor session. */
    boolean reportsPlaying() {
        return hasUsableSession() && playerStatus == 1;
    }

    private void bindPlayer() {
        if (!started || playerBound) return;
        Intent intent = new Intent(PLAYER_SERVICE_ACTION)
                .setComponent(new ComponentName(PLAYER_PACKAGE, PLAYER_SERVICE));
        try {
            playerBound = context.bindService(intent, playerConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable error) {
            callback.onReadError("连接东风车机播放器失败", error);
        }
    }

    private void emitCurrentSession() {
        int state = stateFromPlayerStatus(playerStatus);
        MusicPlaybackData data = new MusicPlaybackData(
                "dftc:" + playerType + ":" + playerTitle,
                playerTitle,
                "",
                null,
                "",
                "",
                -1L,
                true,
                state,
                0L,
                0L,
                state == MusicPlaybackData.STATE_PLAYING ? 1f : 0f);
        callback.onSession(PLAYER_PACKAGE, "东风皓瀚播放器", data);
    }

    private boolean isPlayerInstalled() {
        try {
            context.getPackageManager().getApplicationInfo(PLAYER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private String aidlString(int transaction) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(PLAYER_DESCRIPTOR);
            if (!playerBinder.transact(transaction, data, reply, 0)) return "";
            reply.readException();
            return value(reply.readString());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private int aidlInt(int transaction) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(PLAYER_DESCRIPTOR);
            if (!playerBinder.transact(transaction, data, reply, 0)) return -1;
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void aidlVoid(int transaction) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(PLAYER_DESCRIPTOR);
            if (!playerBinder.transact(transaction, data, reply, 0)) {
                throw new RemoteException("AIDL transaction rejected: " + transaction);
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static int stateFromPlayerStatus(int status) {
        if (status == 1) return MusicPlaybackData.STATE_PLAYING;
        if (status == 0) return MusicPlaybackData.STATE_PAUSED;
        return MusicPlaybackData.STATE_NONE;
    }

    static boolean isUsableRead(String title, int status) {
        return status >= 0 && title != null && !title.trim().isEmpty();
    }

    /**
     * Replay decision for transient read hiccups: only while the binder lives, a usable title
     * was seen before, and the last good read is younger than {@link #TRANSIENT_HOLD_MS}.
     */
    static boolean shouldReuseRetainedSnapshot(String retainedTitle, boolean binderAlive,
                                               long ageMs) {
        return binderAlive && ageMs >= 0L && ageMs < TRANSIENT_HOLD_MS
                && retainedTitle != null && !retainedTitle.trim().isEmpty();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
