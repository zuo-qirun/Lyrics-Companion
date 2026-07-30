package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable UI state for captions generated from captured playback audio. */
public final class RealtimeCaptionState {
    public enum Status { OFF, NEEDS_PERMISSION, STARTING, LISTENING, FALLING_BACK, ERROR }

    public static final RealtimeCaptionState OFF = new RealtimeCaptionState(Status.OFF, "", "",
            "", Collections.<String>emptyList(), 0L, "");

    public final Status status;
    public final String engineName;
    public final String partialText;
    public final String language;
    public final List<String> finalLines;
    public final long updatedElapsedMs;
    public final String error;

    RealtimeCaptionState(Status status, String engineName, String partialText, String language,
                         List<String> finalLines, long updatedElapsedMs, String error) {
        this.status = status == null ? Status.OFF : status;
        this.engineName = safe(engineName);
        this.partialText = safe(partialText);
        this.language = safe(language);
        this.finalLines = Collections.unmodifiableList(new ArrayList<>(finalLines == null
                ? Collections.<String>emptyList() : finalLines));
        this.updatedElapsedMs = updatedElapsedMs;
        this.error = safe(error);
    }

    public boolean isVisible() {
        return status != Status.OFF;
    }

    public String currentText() {
        if (!partialText.isEmpty()) return partialText;
        return finalLines.isEmpty() ? "" : finalLines.get(finalLines.size() - 1);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
