package com.zuoqirun.lyricscompanion;

/** Adapter point for a bundled offline streaming ASR model. */
public interface LocalSpeechRecognizer {
    boolean isAvailable();
    String displayName();
    void start(Listener listener) throws Exception;
    void acceptPcm16(byte[] pcm, int length, int sampleRateHz) throws Exception;
    void stop();

    interface Listener {
        void onPartial(String text, String language);
        void onFinal(String text, String language);
        void onError(Throwable error);
    }
}
