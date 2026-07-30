package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Captures opt-in playback audio on API 29+ and streams PCM to the configured caption engine. */
/** Isolated API 29 service; it is never started on the API 19-28 compatibility path. */
@TargetApi(Build.VERSION_CODES.Q)
public final class RealtimeCaptionService extends Service {
    private static final String TAG = "LyricsCaptions";
    private static final String CHANNEL_ID = "realtime_captions";
    private static final int NOTIFICATION_ID = 42;
    private static final String ACTION_START = "com.zuoqirun.lyricscompanion.action.CAPTION_START";
    private static final String ACTION_STOP = "com.zuoqirun.lyricscompanion.action.CAPTION_STOP";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final int SAMPLE_RATE_HZ = 16_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private MediaProjection projection;
    private AudioRecord recorder;
    private Thread captureThread;
    private RealtimeCaptionEngine engine;
    private long lastAudibleElapsedMs;

    public static boolean isSupported() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q; }

    static void start(Context context, int resultCode, Intent resultData) {
        Intent intent = new Intent(context, RealtimeCaptionService.class).setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_RESULT_DATA, resultData);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable error) {
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.ERROR, "", "无法启动字幕服务："
                    + message(error));
        }
    }

    static void stop(Context context) {
        try { context.startService(new Intent(context, RealtimeCaptionService.class).setAction(ACTION_STOP)); }
        catch (Throwable ignored) { context.stopService(new Intent(context, RealtimeCaptionService.class)); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action) || !AppPreferences.realtimeCaptionsEnabled(this)) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!isSupported()) {
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.ERROR, "", "系统播放音频捕获需要 Android 10 或更高版本");
            stopSelf();
            return START_NOT_STICKY;
        }
        Intent data = intent == null ? null : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        int resultCode = intent == null ? 0 : intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        if (data == null || resultCode == 0) {
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.NEEDS_PERMISSION, "", "请授权系统音频捕获");
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        stopCapture();
        // Loading the ~200 MB model may take several seconds; never block the service main thread.
        new Thread(() -> beginCapture(resultCode, data), "LyricsCaptionStart").start();
        return START_NOT_STICKY; // MediaProjection consent cannot be silently recreated after process death.
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @SuppressLint("ForegroundServiceType")
    private void startInForeground() {
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("歌词伴侣正在生成字幕")
                .setContentText("正在捕获允许录制的系统播放音频")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), pendingIntentFlags()))
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(NOTIFICATION_ID, notification);
    }

    @SuppressLint("MissingPermission")
    private void beginCapture(int resultCode, Intent data) {
        try {
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.STARTING, "本地语音识别", "");
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new IllegalStateException("系统未提供媒体投影服务");
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("系统音频捕获授权无效");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    RealtimeCaptionStore.status(RealtimeCaptionState.Status.ERROR, "", "系统已停止音频捕获授权");
                    stopSelf();
                }
            }, mainHandler);
            AudioPlaybackCaptureConfiguration configuration = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build();
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            int bufferSize = Math.max(AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), SAMPLE_RATE_HZ * 2);
            recorder = new AudioRecord.Builder().setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(configuration).build();
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("无法初始化系统音频捕获");
            engine = new RealtimeCaptionEngine();
            engine.start(AppPreferences.cloudFallbackEnabled(this), new LocalSpeechRecognizer.Listener() {
                @Override public void onPartial(String text, String language) {
                    RealtimeCaptionStore.partial(text, language, engine == null ? "" : engine.activeName());
                }
                @Override public void onFinal(String text, String language) {
                    RealtimeCaptionStore.finalLine(text, language, engine == null ? "" : engine.activeName());
                }
                @Override public void onError(Throwable error) { fail("识别引擎异常：" + message(error)); }
            });
            recorder.startRecording();
            running = true;
            lastAudibleElapsedMs = SystemClock.elapsedRealtime();
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.LISTENING, engine.activeName(), "");
            captureThread = new Thread(this::captureLoop, "LyricsCaptionCapture");
            captureThread.start();
        } catch (Throwable error) {
            fail("无法开始系统音频字幕：" + message(error));
        }
    }

    private void captureLoop() {
        byte[] buffer = new byte[3_200];
        while (running && recorder != null) {
            int count = recorder.read(buffer, 0, buffer.length);
            if (count <= 0) continue;
            if (containsAudibleSample(buffer, count)) lastAudibleElapsedMs = SystemClock.elapsedRealtime();
            if (SystemClock.elapsedRealtime() - lastAudibleElapsedMs > 5_000L) {
                RealtimeCaptionStore.status(RealtimeCaptionState.Status.LISTENING,
                        engine == null ? "" : engine.activeName(), "未捕获到可识别的系统音频；该播放器可能禁止捕获");
            }
            try { if (engine != null) engine.acceptPcm16(buffer, count, SAMPLE_RATE_HZ); }
            catch (Throwable error) { fail("识别引擎异常：" + message(error)); return; }
        }
    }

    private void fail(String detail) {
        Log.w(TAG, detail);
        RealtimeCaptionStore.status(RealtimeCaptionState.Status.ERROR,
                engine == null ? "" : engine.activeName(), detail);
        mainHandler.post(() -> { stopCapture(); stopSelf(); });
    }

    private void stopCapture() {
        running = false;
        if (recorder != null) {
            try { recorder.stop(); } catch (Throwable ignored) { }
            recorder.release(); recorder = null;
        }
        if (engine != null) { engine.stop(); engine = null; }
        if (projection != null) { try { projection.stop(); } catch (Throwable ignored) { } projection = null; }
        captureThread = null;
        if (!AppPreferences.realtimeCaptionsEnabled(this)) RealtimeCaptionStore.clear();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "实时字幕", NotificationManager.IMPORTANCE_LOW));
    }

    private int pendingIntentFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static boolean containsAudibleSample(byte[] audio, int length) {
        for (int i = 1; i < length; i += 2) {
            int sample = (audio[i] << 8) | (audio[i - 1] & 0xFF);
            if (Math.abs(sample) > 180) return true;
        }
        return false;
    }

    private static String message(Throwable error) {
        if (error == null) return "未知错误";
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }
}
