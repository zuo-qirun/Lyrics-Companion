package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.util.Log;

/** One process-wide listener for the system output mix, shared by every lyrics surface. */
final class AudioSpectrumSource {
    private static final String TAG = "LyricsSpectrum";
    private static final float[] SILENCE = new float[SpectrumMath.BAND_COUNT];
    private static final SpectrumMath.Analyzer ANALYZER = new SpectrumMath.Analyzer();
    private static volatile Frame latestFrame = new Frame(SILENCE, false);
    private static Visualizer visualizer;
    private static String state = "待命";

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
        if (visualizer != null) return;
        try {
            Visualizer candidate = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = chooseCaptureSize(range);
            if (candidate.setCaptureSize(captureSize) != Visualizer.SUCCESS) {
                candidate.release();
                stop("设备拒绝频谱采样");
                return;
            }
            int captureRate = Math.max(10_000, Visualizer.getMaxCaptureRate() / 2);
            int listenerResult = candidate.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override public void onWaveFormDataCapture(Visualizer ignored,
                                                                    byte[] waveform,
                                                                    int samplingRate) { }

                        @Override public void onFftDataCapture(Visualizer ignored, byte[] fft,
                                                               int samplingRate) {
                            latestFrame = new Frame(ANALYZER.process(fft, samplingRate), true);
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
            latestFrame = new Frame(SILENCE, true);
            state = "正在采集系统音频";
        } catch (Throwable error) {
            Log.w(TAG, "Unable to capture system audio spectrum", error);
            stop("当前 ROM 不支持系统音频频谱");
        }
    }

    static synchronized void release() { stop("待命"); }

    static Frame latestFrame() { return latestFrame; }

    static String status(Context context, boolean enabled) {
        if (!enabled) return "虚拟律动：无需录音权限";
        if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return "未授权：将显示虚拟律动";
        return state;
    }

    private static int chooseCaptureSize(int[] range) {
        int minimum = range != null && range.length > 0 ? range[0] : 128;
        int maximum = range != null && range.length > 1 ? range[1] : 1024;
        int selected = 1;
        while (selected * 2 <= maximum && selected < 1024) selected *= 2;
        return Math.max(minimum, selected);
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

        Frame(float[] levels, boolean live) {
            this.levels = levels;
            this.live = live;
        }
    }
}
