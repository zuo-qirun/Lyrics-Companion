package com.zuoqirun.lyricscompanion;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** Process-local caption state. Audio and recognized text are never persisted. */
final class RealtimeCaptionStore {
    private static final Object LOCK = new Object();
    private static final int MAX_FINAL_LINES = 3;
    private static RealtimeCaptionState state = RealtimeCaptionState.OFF;

    private RealtimeCaptionStore() {}

    static RealtimeCaptionState snapshot() {
        synchronized (LOCK) { return state; }
    }

    static void status(RealtimeCaptionState.Status status, String engine, String error) {
        synchronized (LOCK) {
            state = new RealtimeCaptionState(status, engine, state.partialText, state.language,
                    state.finalLines, SystemClock.elapsedRealtime(), error);
        }
    }

    static void partial(String text, String language, String engine) {
        synchronized (LOCK) {
            state = new RealtimeCaptionState(RealtimeCaptionState.Status.LISTENING, engine, text,
                    language, state.finalLines, SystemClock.elapsedRealtime(), "");
        }
    }

    static void finalLine(String text, String language, String engine) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;
        synchronized (LOCK) {
            List<String> lines = new ArrayList<>(state.finalLines);
            if (lines.isEmpty() || !trimmed.equals(lines.get(lines.size() - 1))) lines.add(trimmed);
            while (lines.size() > MAX_FINAL_LINES) lines.remove(0);
            state = new RealtimeCaptionState(RealtimeCaptionState.Status.LISTENING, engine, "",
                    language, lines, SystemClock.elapsedRealtime(), "");
        }
    }

    static void clear() {
        synchronized (LOCK) { state = RealtimeCaptionState.OFF; }
    }
}
