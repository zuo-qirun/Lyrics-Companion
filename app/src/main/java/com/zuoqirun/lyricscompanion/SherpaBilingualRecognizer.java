package com.zuoqirun.lyricscompanion;

import android.content.Context;

import java.io.File;

/** Local CPU streaming recognizer backed by sherpa-onnx's official C API. */
final class SherpaBilingualRecognizer implements LocalSpeechRecognizer {
    private final Context appContext;
    private Listener listener;
    private long handle;
    private String previousPartial = "";
    private boolean nativeLoaded;

    SherpaBilingualRecognizer(Context context) { appContext = context.getApplicationContext(); }

    @Override public synchronized boolean isAvailable() {
        return BilingualCaptionModel.isInstalled(appContext) && loadNative();
    }

    @Override public String displayName() { return "中英离线识别"; }

    @Override public synchronized void start(Listener listener) throws Exception {
        if (!isAvailable()) throw new IllegalStateException("请先下载中英离线模型");
        File dir = BilingualCaptionModel.directory(appContext);
        handle = nativeOpen(new File(dir, "encoder-epoch-99-avg-1.int8.onnx").getAbsolutePath(),
                new File(dir, "decoder-epoch-99-avg-1.onnx").getAbsolutePath(),
                new File(dir, "joiner-epoch-99-avg-1.int8.onnx").getAbsolutePath(),
                new File(dir, "tokens.txt").getAbsolutePath());
        if (handle == 0L) throw new IllegalStateException("无法加载中英离线模型");
        this.listener = listener;
        previousPartial = "";
    }

    @Override public synchronized void acceptPcm16(byte[] pcm, int length, int sampleRateHz) {
        if (handle == 0L || length < 2) return;
        int samples = length / 2;
        float[] audio = new float[samples];
        for (int i = 0; i < samples; i++) {
            int low = pcm[i * 2] & 0xFF;
            int high = pcm[i * 2 + 1];
            audio[i] = ((short) ((high << 8) | low)) / 32768f;
        }
        nativeAccept(handle, audio, samples);
        String text = safe(nativeText(handle));
        boolean endpoint = nativeIsEndpoint(handle);
        if (endpoint) {
            if (!text.isEmpty() && listener != null) listener.onFinal(text, "zh-en");
            nativeReset(handle);
            previousPartial = "";
        } else if (!text.isEmpty() && !text.equals(previousPartial) && listener != null) {
            previousPartial = text;
            listener.onPartial(text, "zh-en");
        }
    }

    @Override public synchronized void stop() {
        listener = null;
        previousPartial = "";
        if (handle != 0L) { nativeClose(handle); handle = 0L; }
    }

    private boolean loadNative() {
        if (nativeLoaded) return true;
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("sherpa-onnx-c-api");
            System.loadLibrary("caption_jni");
            nativeLoaded = true;
        } catch (Throwable ignored) { nativeLoaded = false; }
        return nativeLoaded;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static native long nativeOpen(String encoder, String decoder, String joiner, String tokens);
    private static native void nativeAccept(long handle, float[] audio, int samples);
    private static native String nativeText(long handle);
    private static native boolean nativeIsEndpoint(long handle);
    private static native void nativeReset(long handle);
    private static native void nativeClose(long handle);
}
