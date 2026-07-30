package com.zuoqirun.lyricscompanion;

/** Selects a local streaming recognizer first and an optional cloud recognizer second. */
public final class RealtimeCaptionEngine {
    private static final LocalSpeechRecognizer UNAVAILABLE = new UnavailableRecognizer("本地语音模型未安装");
    private static volatile LocalSpeechRecognizer localRecognizer = UNAVAILABLE;
    private static volatile CloudSpeechRecognizer cloudRecognizer;

    private LocalSpeechRecognizer active;
    private boolean usingCloud;
    private boolean allowCloud;
    private LocalSpeechRecognizer.Listener clientListener;

    public static void installLocalRecognizer(LocalSpeechRecognizer recognizer) {
        localRecognizer = recognizer == null ? UNAVAILABLE : recognizer;
    }

    public static void installCloudRecognizer(CloudSpeechRecognizer recognizer) {
        cloudRecognizer = recognizer;
    }

    synchronized void start(boolean allowCloud, LocalSpeechRecognizer.Listener listener) throws Exception {
        this.allowCloud = allowCloud;
        this.clientListener = listener;
        active = localRecognizer;
        usingCloud = false;
        if (!active.isAvailable() && allowCloud && cloudRecognizer != null && cloudRecognizer.isAvailable()) {
            active = cloudRecognizer;
            usingCloud = true;
        }
        if (!active.isAvailable()) throw new IllegalStateException(active.displayName());
        startActiveRecognizer();
    }

    synchronized void acceptPcm16(byte[] pcm, int length, int sampleRateHz) throws Exception {
        if (active != null) active.acceptPcm16(pcm, length, sampleRateHz);
    }

    synchronized void stop() {
        if (active != null) active.stop();
        active = null;
        clientListener = null;
    }

    synchronized String activeName() { return active == null ? "" : active.displayName(); }
    synchronized boolean isUsingCloud() { return usingCloud; }

    private void startActiveRecognizer() throws Exception {
        if (active == null || clientListener == null) return;
        active.start(new LocalSpeechRecognizer.Listener() {
            @Override public void onPartial(String text, String language) {
                LocalSpeechRecognizer.Listener listener = listener();
                if (listener != null) listener.onPartial(text, language);
            }
            @Override public void onFinal(String text, String language) {
                LocalSpeechRecognizer.Listener listener = listener();
                if (listener != null) listener.onFinal(text, language);
            }
            @Override public void onError(Throwable error) {
                if (!switchToCloud()) {
                    LocalSpeechRecognizer.Listener listener = listener();
                    if (listener != null) listener.onError(error);
                }
            }
        });
    }

    private synchronized boolean switchToCloud() {
        if (usingCloud || !allowCloud || cloudRecognizer == null || !cloudRecognizer.isAvailable()) return false;
        try {
            if (active != null) active.stop();
            active = cloudRecognizer;
            usingCloud = true;
            RealtimeCaptionStore.status(RealtimeCaptionState.Status.FALLING_BACK, active.displayName(),
                    "本地识别异常，正在切换云端识别");
            startActiveRecognizer();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private synchronized LocalSpeechRecognizer.Listener listener() { return clientListener; }

    private static final class UnavailableRecognizer implements LocalSpeechRecognizer {
        private final String message;
        UnavailableRecognizer(String message) { this.message = message; }
        @Override public boolean isAvailable() { return false; }
        @Override public String displayName() { return message; }
        @Override public void start(Listener listener) { }
        @Override public void acceptPcm16(byte[] pcm, int length, int sampleRateHz) { }
        @Override public void stop() { }
    }
}
