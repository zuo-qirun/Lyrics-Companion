package com.zuoqirun.lyricscompanion;

interface MusicSessionReader {
    interface Callback {
        void onReadSuccess(int sessionCount);
        void onReadError(String message, Throwable error);
        void onSession(String packageName, String applicationLabel, MusicPlaybackData data);
        void onNoSession();
        default void onIncompleteSession(String packageName, MusicPlaybackData data) {
            onNoSession();
        }
        default void onServiceOnlyTitle(boolean present) { }
    }

    void start();
    void refresh();
    boolean dispatchControl(MediaControlAction action);
    void stop();
}
