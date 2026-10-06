package com.zuoqirun.lyricscompanion;

import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, font-aware glyph advances; shaping-sensitive scripts stay as a complete text run. */
final class LyricGlyphCache {
    static final class Layout {
        final String[] glyphs;
        final float[] offsets;
        final float[] widths;
        final boolean[] visible;
        final float width;

        Layout(String[] glyphs, float[] offsets, float[] widths, float width) {
            this.glyphs = glyphs;
            this.offsets = offsets;
            this.widths = widths;
            this.width = width;
            visible = new boolean[glyphs.length];
            for (int i = 0; i < glyphs.length; i++) visible[i] = !glyphs[i].trim().isEmpty();
        }
    }

    private final Map<Key, Layout> cache = new LinkedHashMap<Key, Layout>(48, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Layout> entry) {
            return size() > 48;
        }
    };
    private final Key lookup = new Key();

    void clear() { cache.clear(); }

    Layout get(String text, Paint paint) {
        lookup.set(text, paint);
        Layout cached = cache.get(lookup);
        if (cached != null) return cached;
        float totalWidth = paint.measureText(text);
        Layout result;
        if (text.length() > 512 || needsShaping(text)) {
            result = new Layout(new String[]{text}, new float[]{0f}, new float[]{totalWidth}, totalWidth);
        } else {
            ArrayList<String> glyphs = new ArrayList<>();
            ArrayList<Integer> ends = new ArrayList<>();
            for (int start = 0; start < text.length();) {
                int end = LyricEffectRules.glyphEnd(text, start);
                glyphs.add(text.substring(start, end));
                ends.add(end);
                start = end;
            }
            float[] characterWidths = new float[text.length()];
            paint.getTextWidths(text, characterWidths);
            float[] offsets = new float[glyphs.size()];
            float[] widths = new float[glyphs.size()];
            int start = 0;
            float offset = 0f;
            for (int i = 0; i < ends.size(); i++) {
                offsets[i] = offset;
                while (start < ends.get(i)) widths[i] += characterWidths[start++];
                offset += widths[i];
            }
            // Preserve the full run width for centre/right alignment even with font kerning.
            result = new Layout(glyphs.toArray(new String[0]), offsets, widths, totalWidth);
        }
        cache.put(new Key(text, paint), result);
        return result;
    }

    private static boolean needsShaping(String text) {
        for (int i = 0; i < text.length(); i++) {
            char code = text.charAt(i);
            if (code >= 0x0600 && code <= 0x0EFF || code >= 0xFB50 && code <= 0xFDFF
                    || code >= 0xFE70 && code <= 0xFEFC) return true;
        }
        return false;
    }

    private static final class Key {
        String text;
        Typeface font;
        int size;
        int scale;
        int skew;
        boolean bold;

        Key() { }

        Key(String text, Paint paint) {
            set(text, paint);
        }

        void set(String text, Paint paint) {
            this.text = text;
            font = paint.getTypeface();
            size = Float.floatToIntBits(paint.getTextSize());
            scale = Float.floatToIntBits(paint.getTextScaleX());
            skew = Float.floatToIntBits(paint.getTextSkewX());
            bold = paint.isFakeBoldText();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return text.equals(key.text) && font == key.font && size == key.size
                    && scale == key.scale && skew == key.skew && bold == key.bold;
        }

        @Override public int hashCode() {
            int hash = text.hashCode();
            hash = 31 * hash + System.identityHashCode(font);
            hash = 31 * hash + size;
            hash = 31 * hash + scale;
            hash = 31 * hash + skew;
            return 31 * hash + (bold ? 1 : 0);
        }
    }
}
