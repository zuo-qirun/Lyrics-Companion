package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One selection per track; view recreation, seeking and pause do not change it. */
final class FoliaEffectSelector {
    private final Random random;
    private String track = "", configuration = "", selected = "claddagh";
    private boolean hasSelection;
    private final TrackIdentity trackIdentity = new TrackIdentity();
    FoliaEffectSelector(Random random) { this.random = random; }

    static String[] pool(String encoded) {
        List<String> values = new ArrayList<>();
        if (encoded != null) for (String item : encoded.split(",")) {
            String mode = item.trim();
            for (String supported : FoliaModeCatalog.VALUES) if (supported.equals(mode) && !values.contains(mode)) values.add(mode);
        }
        return values.isEmpty() ? FoliaModeCatalog.VALUES.clone() : values.toArray(new String[0]);
    }

    String select(String identity, boolean enabled, String manual, String encodedPool) {
        if (!enabled) { configuration = ""; return FoliaModeCatalog.normalize(manual); }
        if (identity == null || identity.isEmpty()) return hasSelection ? selected : FoliaModeCatalog.normalize(manual);
        String key = encodedPool == null ? "" : encodedPool;
        if (identity.equals(track) && key.equals(configuration)) return selected;
        String[] choices = pool(key);
        List<String> candidates = new ArrayList<>();
        for (String value : choices) if (!hasSelection || choices.length == 1 || !value.equals(selected)) candidates.add(value);
        selected = candidates.get(random.nextInt(candidates.size()));
        hasSelection = true;
        track = identity; configuration = key;
        return selected;
    }

    String selectTrack(String source, String title, String artist, boolean enabled, String manual, String encodedPool) {
        return select(trackIdentity.update(source, title, artist), enabled, manual, encodedPool);
    }
}
