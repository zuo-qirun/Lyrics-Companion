package com.zuoqirun.lyricscompanion;

interface MusicSessionReader {
    interface Callback {
        void onReadSuccess(int sessionCount);
        void onReadError(String message, Throwable error);
        void onSession(String packageName, String applicationLabel, MusicPlaybackData data);
        void onNoSession();
    }

    void start();
    void refresh();
    boolean dispatchControl(MediaControlAction action);
    void stop();
}
