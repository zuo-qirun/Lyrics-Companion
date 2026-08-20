package com.zuoqirun.lyricscompanion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.util.Locale;

/**
 * Reads the Dongfeng head-unit player, which does not publish a usable MediaSession.
 *
 * <p>The player AIDL supplies the authoritative title and playback state. Its online backend
 * (WecarFlow) supplies richer metadata, progress and the current lyric line. Wecar data is only
 * accepted while the Dongfeng player reports the same title, so another Wecar client cannot
 * accidentally take over the overlay.</p>
 */
final class DftcMediaSessionReader implements MusicSessionReader {
    private static final String TAG = "LyricsDftcMedia";
    private static final String PLAYER_PACKAGE = "com.dftc.media";
    private static final String PLAYER_SERVICE_ACTION = "com.dftc.media.mediaService";
    private static final String PLAYER_SERVICE = "com.dftc.media.MusicAIDLService";
    private static final String PLAYER_DESCRIPTOR = "com.dftc.media.MusicAIDL";
    private static final String WECAR_PACKAGE = "com.tencent.wecarflow";
    private static final String WECAR_SERVICE =
            "com.tencent.wecarflow.launcherwidget.UpdateWidgetService";
    private static final String WECAR_ACTION = "com.tencent.wecarflow.service_init";

    private static final int AIDL_CONTINUE = 4;
    private static final int AIDL_PAUSE = 5;
    private static final int AIDL_NEXT = 6;
    private static final int AIDL_PREVIOUS = 7;
    private static final int AIDL_GET_NAME = 10;
    private static final int AIDL_GET_TYPE = 11;
    private static final int AIDL_GET_STATUS = 12;

    private static final int WECAR_GET_CURRENT = 1014;
    private static final int WECAR_REQUEST_PLAY_STATE = 1028;
    private static final int WECAR_CONNECTION_ADD = 3001;
    private static final int WECAR_CONNECTION_REMOVE = 3002;
    private static final int WECAR_ON_PLAY = 2003;
    private static final int WECAR_ON_PAUSE = 2004;
    private static final int WECAR_ON_STOP = 2005;
    private static final int WECAR_ON_PROGRESS = 2006;
    private static final int WECAR_MEDIA_CHANGE = 2010;
    private static final int WECAR_PLAY_STATE = 2023;
    private static final int WECAR_LYRIC_CHANGED = 2051;

    private static final long WECAR_METADATA_MAX_AGE_MS = 15_000L;
    private static final long WECAR_LYRIC_MAX_AGE_MS = 60_000L;
    private static final long WECAR_REFRESH_MS = 3_000L;

    private final Context context;
    private final Callback callback;
    private final Handler handler;
    private final Messenger receiveMessenger;

    private boolean started;
    private boolean playerBound;
    private boolean wecarBound;
    private IBinder playerBinder;
    private Messenger wecarSender;
    private long lastWecarRequestElapsedMs;
    private long lastUsableSessionElapsedMs;

    private String playerTitle = "";
    private String playerType = "";
    private int playerStatus = -1;
    private String wecarMediaId = "";
    private String wecarTitle = "";
    private String wecarArtist = "";
    private String wecarImage = "";
    private String wecarMediaType = "";
    private long wecarDurationMs = -1L;
    private long wecarPositionMs;
    private int wecarState = MusicPlaybackData.STATE_NONE;
    private long wecarUpdatedElapsedMs;
    private String wecarLyric = "";
    private String wecarLyricTitle = "";
    private long wecarLyricUpdatedElapsedMs;
    private boolean wecarLyricReceived;

    private final ServiceConnection playerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            playerBinder = service;
            playerBound = true;
            refresh();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            playerBinder = null;
        }
    };

    private final ServiceConnection wecarConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            wecarSender = new Messenger(service);
            wecarBound = true;
            sendWecar(WECAR_CONNECTION_ADD, true);
            requestWecarState(true);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            wecarSender = null;
        }
    };

    DftcMediaSessionReader(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        handler = new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message message) {
                handleWecarMessage(message);
            }
        };
        receiveMessenger = new Messenger(handler);
    }

    @Override public void start() {
        if (started || !isPlayerInstalled()) return;
        started = true;
        bindPlayer();
        bindWecar();
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
            playerTitle = aidlString(AIDL_GET_NAME);
            playerType = aidlString(AIDL_GET_TYPE);
            playerStatus = aidlInt(AIDL_GET_STATUS);
            callback.onReadSuccess(playerTitle.isEmpty() ? 0 : 1);
            requestWecarState(false);
            long now = SystemClock.elapsedRealtime();
            boolean activeWecarTrack = now - wecarUpdatedElapsedMs
                    <= WECAR_METADATA_MAX_AGE_MS
                    && sameTitle(playerTitle, wecarTitle)
                    && (wecarState == MusicPlaybackData.STATE_PLAYING
                    || wecarState == MusicPlaybackData.STATE_PAUSED);
            if (playerTitle.isEmpty() || playerStatus < 0 && !activeWecarTrack) {
                callback.onNoSession();
                return;
            }
            emitCurrentSession();
        } catch (Throwable error) {
            callback.onReadError("读取东风车机播放器失败", error);
        }
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
        if (wecarBound) sendWecar(WECAR_CONNECTION_REMOVE, true);
        if (playerBound) {
            try { context.unbindService(playerConnection); }
            catch (Throwable ignored) { }
        }
        if (wecarBound) {
            try { context.unbindService(wecarConnection); }
            catch (Throwable ignored) { }
        }
        playerBound = false;
        wecarBound = false;
        playerBinder = null;
        wecarSender = null;
        clearWecarState();
    }

    boolean hasUsableSession() {
        return !playerTitle.isEmpty()
                && SystemClock.elapsedRealtime() - lastUsableSessionElapsedMs < 5_000L;
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

    private void bindWecar() {
        if (!started || wecarBound) return;
        Intent intent = new Intent(WECAR_ACTION)
                .setComponent(new ComponentName(WECAR_PACKAGE, WECAR_SERVICE));
        try {
            wecarBound = context.bindService(intent, wecarConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable error) {
            // Wecar is optional: USB/Bluetooth playback remains available through player AIDL.
            Log.d(TAG, "WecarFlow service unavailable", error);
        }
    }

    private void emitCurrentSession() {
        long now = SystemClock.elapsedRealtime();
        boolean sameWecarTrack = now - wecarUpdatedElapsedMs <= WECAR_METADATA_MAX_AGE_MS
                && sameTitle(playerTitle, wecarTitle);
        String title = sameWecarTrack && !wecarTitle.isEmpty() ? wecarTitle : playerTitle;
        String artist = sameWecarTrack ? wecarArtist : "";
        String mediaId = sameWecarTrack && !wecarMediaId.isEmpty()
                ? wecarMediaId : "dftc:" + playerType + ":" + title;
        String artUri = sameWecarTrack ? wecarImage : "";
        long duration = sameWecarTrack ? wecarDurationMs : -1L;
        long position = sameWecarTrack ? Math.max(0L, wecarPositionMs) : 0L;
        int state = stateFromPlayerStatus(playerStatus);
        if (sameWecarTrack && playerStatus < 0 && wecarState != MusicPlaybackData.STATE_NONE) {
            state = wecarState;
        }
        boolean lyricChannelPresent = sameWecarTrack && wecarLyricReceived
                && sameTitle(title, wecarLyricTitle);
        boolean lyricFresh = lyricChannelPresent
                && now - wecarLyricUpdatedElapsedMs <= WECAR_LYRIC_MAX_AGE_MS;
        String lyric = lyricFresh ? wecarLyric : "";
        MusicPlaybackData data = new MusicPlaybackData(mediaId, title, artist, null,
                artUri, "", duration, true, state, position, now,
                state == MusicPlaybackData.STATE_PLAYING ? 1f : 0f,
                lyricChannelPresent, lyric);
        lastUsableSessionElapsedMs = now;
        callback.onSession(PLAYER_PACKAGE, "东风皓瀚播放器", data);
    }

    private void requestWecarState(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastWecarRequestElapsedMs < WECAR_REFRESH_MS) return;
        lastWecarRequestElapsedMs = now;
        sendWecar(WECAR_GET_CURRENT, true);
        sendWecar(WECAR_REQUEST_PLAY_STATE, true);
    }

    private void sendWecar(int what, boolean withReplyTo) {
        Messenger sender = wecarSender;
        if (sender == null) return;
        Message message = Message.obtain(null, what);
        if (withReplyTo) message.replyTo = receiveMessenger;
        try {
            sender.send(message);
        } catch (RemoteException error) {
            Log.d(TAG, "WecarFlow message failed what=" + what, error);
        }
    }

    private void handleWecarMessage(Message message) {
        if (!started || message == null) return;
        Bundle data = message.getData();
        if (data == null) data = new Bundle();
        long now = SystemClock.elapsedRealtime();
        switch (message.what) {
            case WECAR_MEDIA_CHANGE:
                if (data.getBoolean("media_is_null", false)) clearWecarState();
                else updateWecarMedia(data, now);
                break;
            case WECAR_ON_PLAY:
                updateWecarMedia(data, now);
                wecarState = MusicPlaybackData.STATE_PLAYING;
                break;
            case WECAR_ON_PAUSE:
                wecarState = MusicPlaybackData.STATE_PAUSED;
                wecarUpdatedElapsedMs = now;
                break;
            case WECAR_ON_STOP:
                wecarState = MusicPlaybackData.STATE_STOPPED;
                wecarUpdatedElapsedMs = now;
                break;
            case WECAR_ON_PROGRESS:
                wecarPositionMs = data.getLong("media_progress_current", wecarPositionMs);
                wecarDurationMs = data.getLong("media_progress_total", wecarDurationMs);
                wecarMediaType = value(data.getString("media_type", wecarMediaType));
                wecarUpdatedElapsedMs = now;
                break;
            case WECAR_PLAY_STATE:
                updateWecarMedia(data, now);
                wecarState = stateFromWecar(value(data.getString("play_state")));
                break;
            case WECAR_LYRIC_CHANGED:
                wecarLyric = value(data.getString("key_lyric_content"));
                wecarLyricTitle = wecarTitle;
                wecarLyricUpdatedElapsedMs = now;
                wecarLyricReceived = true;
                break;
            default:
                return;
        }
        refresh();
    }

    private void updateWecarMedia(Bundle data, long now) {
        String incomingTitle = value(data.getString("media_name"));
        if (!incomingTitle.isEmpty() && !sameTitle(incomingTitle, wecarTitle)) {
            wecarLyric = "";
            wecarLyricTitle = "";
            wecarLyricReceived = false;
        }
        if (!incomingTitle.isEmpty()) wecarTitle = incomingTitle;
        String incomingId = value(data.getString("media_uuid"));
        if (!incomingId.isEmpty()) wecarMediaId = incomingId;
        String incomingArtist = value(data.getString("media_author"));
        if (!incomingArtist.isEmpty()) wecarArtist = incomingArtist;
        String incomingImage = value(data.getString("media_image"));
        if (!incomingImage.isEmpty()) wecarImage = incomingImage;
        String incomingType = value(data.getString("media_type"));
        if (!incomingType.isEmpty()) wecarMediaType = incomingType;
        long duration = data.getLong("media_duration", -1L);
        if (duration > 0L) wecarDurationMs = duration;
        long current = data.getLong("media_duration_current", -1L);
        if (current >= 0L) wecarPositionMs = current;
        wecarUpdatedElapsedMs = now;
    }

    private void clearWecarState() {
        wecarMediaId = "";
        wecarTitle = "";
        wecarArtist = "";
        wecarImage = "";
        wecarMediaType = "";
        wecarDurationMs = -1L;
        wecarPositionMs = 0L;
        wecarState = MusicPlaybackData.STATE_NONE;
        wecarUpdatedElapsedMs = 0L;
        wecarLyric = "";
        wecarLyricTitle = "";
        wecarLyricUpdatedElapsedMs = 0L;
        wecarLyricReceived = false;
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

    private static int stateFromWecar(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("pause") || "0".equals(normalized)) {
            return MusicPlaybackData.STATE_PAUSED;
        }
        if (normalized.contains("stop")) return MusicPlaybackData.STATE_STOPPED;
        if (normalized.contains("play") || "1".equals(normalized)) {
            return MusicPlaybackData.STATE_PLAYING;
        }
        return MusicPlaybackData.STATE_NONE;
    }

    private static boolean sameTitle(String first, String second) {
        String left = identity(first);
        return !left.isEmpty() && left.equals(identity(second));
    }

    private static String identity(String value) {
        return value(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
