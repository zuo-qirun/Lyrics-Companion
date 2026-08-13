package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/** One process-wide listener for the system output mix, shared by every lyrics surface. */
final class AudioSpectrumSource {
    private static final String TAG = "LyricsSpectrum";
    private static final float[] SILENCE = new float[SpectrumMath.BAND_COUNT];
    private static final SpectrumMath.Analyzer ANALYZER = new SpectrumMath.Analyzer();
    private static volatile Frame latestFrame = new Frame(SILENCE, false);
    private static Visualizer visualizer;
    private static String state = "待命";
    private static boolean playbackActive;

    private AudioSpectrumSource() { }

    static synchronized void sync(Context context) {
        sync(context, false);
    }

    static synchronized void sync(Context context, boolean fullscreen) {
        boolean requested = fullscreen && AppPreferences.spectrumEnabled(context, false)
                && AppPreferences.compactUseRealSpectrum(context, false)
                || AppPreferences.mainEnabled(context)
                && AppPreferences.spectrumEnabled(context, false)
                && AppPreferences.compactUseRealSpectrum(context, false)
                || AppPreferences.secondaryEnabled(context)
                && AppPreferences.spectrumEnabled(context, true)
                && AppPreferences.compactUseRealSpectrum(context, true)
                || AppPreferences.topLyricStrip(context)
                && AppPreferences.topLyricSpectrum(context)
                && AppPreferences.compactUseRealSpectrum(context, false)
                || AppPreferences.bottomSpectrum(context)
                && AppPreferences.compactUseRealSpectrum(context, false);
        if (!requested) {
            stop("待命");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            stop("未授权录音权限");
            return;
        }
        if (!playbackActive) {
            stop("播放暂停：已释放真实频谱采集");
            return;
        }
        if (visualizer != null) return;
        try {
            Visualizer candidate = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            boolean highRate = "high".equals(AppPreferences.realSpectrumCaptureRate(context));
            int captureSize = chooseCaptureSize(range, highRate);
            if (candidate.setCaptureSize(captureSize) != Visualizer.SUCCESS) {
                candidate.release();
                stop("设备拒绝频谱采样");
                return;
            }
            // Low mode is suitable for old head units. High mode trades CPU for smoother bars
            // while still staying below the former half-max-rate continuous capture path.
            int requestedRate = highRate ? 30_000 : 10_000;
            int captureRate = Math.max(1, Math.min(requestedRate,
                    Visualizer.getMaxCaptureRate()));
            int listenerResult = candidate.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override public void onWaveFormDataCapture(Visualizer ignored,
                                                                    byte[] waveform,
                                                                    int samplingRate) { }

                        @Override public void onFftDataCapture(Visualizer ignored, byte[] fft,
                                                               int samplingRate) {
                            latestFrame = new Frame(ANALYZER.process(fft, samplingRate), true,
                                    SystemClock.elapsedRealtime());
                        }
                    }, captureRate, false, true);
            if (listenerResult != Visualizer.SUCCESS) {
                candidate.release();
                stop("设备拒绝频谱回调");
                return;
            }
            candidate.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            if (candidate.setEnabled(true) != Visualizer.SUCCESS) {
                candidate.release();
                stop("设备拒绝启用频谱效果");
                return;
            }
            visualizer = candidate;
            latestFrame = new Frame(SILENCE, false);
            state = highRate ? "高频率采集：30 Hz / 512 点"
                    : "低频率采集：10 Hz / 128 点";
        } catch (Throwable error) {
            Log.w(TAG, "Unable to capture system audio spectrum", error);
            stop("当前 ROM 不支持系统音频频谱");
        }
    }

    static synchronized void release() { stop("待命"); }

    static Frame latestFrame() {
        Frame frame = latestFrame;
        if (frame.live && SystemClock.elapsedRealtime() - frame.capturedAtMs > 1_500L) {
            return new Frame(frame.levels, false);
        }
        return frame;
    }

    static synchronized void setPlaybackActive(Context context, boolean active) {
        if (playbackActive == active) return;
        playbackActive = active;
        sync(context);
    }

    static String status(Context context, boolean enabled) {
        if (!enabled) return "虚拟律动：无需录音权限";
        if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return "未授权：将显示虚拟律动";
        return state;
    }

    private static int chooseCaptureSize(int[] range, boolean highRate) {
        int minimum = range != null && range.length > 0 ? range[0] : 128;
        int maximum = range != null && range.length > 1 ? range[1] : 1024;
        int target = highRate ? 512 : 128;
        int selected = 1;
        while (selected < target) selected *= 2;
        return Math.max(minimum, Math.min(maximum, selected));
    }

    private static void stop(String nextState) {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Throwable ignored) { }
            visualizer = null;
        }
        latestFrame = new Frame(SILENCE, false);
        state = nextState;
    }

    static final class Frame {
        final float[] levels;
        final boolean live;
        final long capturedAtMs;

        Frame(float[] levels, boolean live) {
            this(levels, live, live ? SystemClock.elapsedRealtime() : 0L);
        }

        Frame(float[] levels, boolean live, long capturedAtMs) {
            this.levels = levels;
            this.live = live;
            this.capturedAtMs = capturedAtMs;
        }
    }
}
