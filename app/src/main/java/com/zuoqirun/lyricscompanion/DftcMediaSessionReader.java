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
            playerTitle = aidlString(AIDL_GET_NAME);
            playerType = aidlString(AIDL_GET_TYPE);
            playerStatus = aidlInt(AIDL_GET_STATUS);
            callback.onReadSuccess(playerTitle.isEmpty() ? 0 : 1);
            if (playerTitle.isEmpty() || playerStatus < 0) {
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

    private void emitCurrentSession() {
        long now = SystemClock.elapsedRealtime();
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
        lastUsableSessionElapsedMs = now;
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

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
