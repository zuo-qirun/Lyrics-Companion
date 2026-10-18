// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native translation of lumiere/text/lyricEcho.ts at 9279251.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;

/** Seeded word fragments; no song/viewport is recorded and time reversal is deterministic. */
final class FoliaLumiereEchoMath {
    private FoliaLumiereEchoMath() { }
    static final class Input {
        String[] glyphs;
        double[] starts, offsets;
        List<int[]> words;
    }
    static final class Glyph {
        String text;
        double offset, dx, dy, speed, spin;
    }
    static final class Fragment {
        double birth, across, speed, scale, rotation, stretch, shear, spin;
        Glyph[] glyphs;
    }
    static final class Frame {
        double x, y, rotation, scaleX, scaleY, skew, split, presence;
    }
    static List<Fragment> build(List<Input> lines, String sceneSeed) {
        FoliaLumiereMath.Random random = new FoliaLumiereMath.Random(sceneSeed + ":echo");
        List<Fragment> fragments = new ArrayList<>();
        for (Input line : lines) for (int[] word : line.words) {
            int end = Math.min(word[1], line.glyphs.length);
            if (end <= word[0]) continue;
            StringBuilder value = new StringBuilder();
            for (int i = word[0]; i < end; i++) value.append(line.glyphs[i]);
            if (value.toString().matches("[\\s\\p{P}\\p{S}]+")) continue;
            Fragment fragment = new Fragment(); fragment.birth = line.starts[word[0]];
            fragment.across = (random.next() - .5) * 2.8; fragment.speed = .7 + random.next() * .7;
            fragment.scale = .4 + random.next() * .6; fragment.rotation = (random.next() - .5) * .9;
            fragment.stretch = .75 + random.next() * .7; fragment.shear = (random.next() - .5) * .12;
            fragment.spin = (random.next() - .5) * .08;
            double middle = (line.offsets[word[0]] + line.offsets[end]) / 2;
            fragment.glyphs = new Glyph[end - word[0]];
            for (int i = word[0]; i < end; i++) {
                Glyph glyph = new Glyph(); glyph.text = line.glyphs[i];
                double angle = random.next() * Math.PI * 2;
                glyph.dx = Math.cos(angle); glyph.dy = Math.sin(angle);
                glyph.speed = .3 + random.next() * .9; glyph.spin = (random.next() - .5) * .5;
                glyph.offset = (line.offsets[i] + line.offsets[i + 1]) / 2 - middle;
                fragment.glyphs[i - word[0]] = glyph;
            }
            fragments.add(fragment);
        }
        return fragments;
    }
    static void frame(Frame output, Fragment fragment, double time, double height,
                      double ox, double oy, double bx, double by, double halfWidth, double tanSpread) {
        double age = time - fragment.birth;
        output.presence = age < 0 || age > 7 ? 0 : FoliaLumiereMath.smooth(age / .9)
                * (1 - FoliaLumiereMath.smooth((age - 4.5) / 2.5));
        double distance = .08 + .075 * fragment.speed * age;
        double across = fragment.across * (halfWidth + distance * tanSpread + .04);
        output.x = (ox + bx * distance - by * across) * height;
        output.y = (oy + by * distance + bx * across) * height;
        output.rotation = fragment.rotation + fragment.spin * age;
        output.scaleX = fragment.scale * fragment.stretch; output.scaleY = fragment.scale;
        output.skew = fragment.shear * age;
        output.split = age > 0 ? .05 * Math.pow(age, 1.3) : 0;
    }
}
