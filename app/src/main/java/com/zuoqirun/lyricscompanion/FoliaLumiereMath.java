// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native translations of lumiere/{program,lumiereStructure,lumiereUnitLayout,lumiereRandom}.ts
// and lumiere/light/rig.ts. Attribution: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, deterministic camera, timing and light formulas shared by Canvas and source parity tests. */
final class FoliaLumiereMath {
    private FoliaLumiereMath() { }
    static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    static double smooth(double x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static double sine(double x) { return (1 - Math.cos(Math.PI * clamp(x))) / 2; }
    static double cubic(double x) { x = clamp(x); return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }
    static double mix(double a, double b, double k) { return a + (b - a) * k; }
    static double step(double a, double b, double x) { return smooth((x - a) / (b - a)); }
    static double fract(double x) { return x - Math.floor(x); }
    static double wrap(double x, double min, double max) { return min + fract((x - min) / (max - min)) * (max - min); }

    static final class Random {
        private int state;
        Random(String seed) { state = hash(seed); }
        double next() {
            state += 0x6d2b79f5;
            int t = (state ^ (state >>> 15)) * (1 | state);
            t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
            return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
        }
        void skip(int count) { state += count * 0x6d2b79f5; }
    }

    static int hash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) hash = (hash ^ value.charAt(i)) * 0x01000193;
        return hash;
    }

    static final class Shot {
        int first, last, profile, paragraph;
        long start, end;
        boolean bridge;
    }

    static final class Paragraph {
        int first, last;
        long start, end;
        String kind;
        boolean opening;
        final List<Shot> shots = new ArrayList<>();
    }

    static List<Paragraph> compile(List<LrcTimeline.Line> lines, long duration, String seed) {
        List<Paragraph> output = new ArrayList<>();
        if (lines.isEmpty()) return output;
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            long gap = lines.get(i).timeMs - end(lines, i - 1);
            if (gap > 0) gaps.add(gap);
        }
        Collections.sort(gaps);
        double median = gaps.isEmpty() ? 500 : gaps.size() % 2 == 0
                ? (gaps.get(gaps.size() / 2 - 1) + gaps.get(gaps.size() / 2)) / 2.0 : gaps.get(gaps.size() / 2);
        long threshold = (long) Math.max(1250, Math.min(3500, median * 2.5));
        int first = 0;
        for (int i = 1; i <= lines.size(); i++) {
            if (i == lines.size() || lines.get(i).timeMs - end(lines, i - 1) >= threshold) {
                split(lines, first, i, output);
                first = i;
            }
        }
        if (lines.get(0).timeMs >= 4000) {
            Paragraph intro = new Paragraph();
            intro.first = intro.last = 0;
            intro.kind = "break";
            intro.start = 0;
            intro.end = lines.get(0).timeMs;
            output.add(0, intro);
        }
        List<Integer> recent = new ArrayList<>();
        String family = "";
        int lyricParagraph = 0;
        int lyricParagraphCount = output.size() - (output.get(0).first == output.get(0).last ? 1 : 0);
        for (int p = 0; p < output.size(); p++) {
            Paragraph part = output.get(p);
            part.start = p == 0 ? 0 : lines.get(part.first).timeMs;
            part.end = p + 1 < output.size() ? lines.get(output.get(p + 1).first).timeMs
                    : Math.max(duration, end(lines, lines.size() - 1));
            if (part.first < part.last) part.kind = classify(lines, part, lyricParagraph++, lyricParagraphCount);
            Paragraph previous = p == 0 ? null : output.get(p - 1);
            part.opening = previous == null || part.start - (previous.first == previous.last
                    ? previous.end : end(lines, previous.last - 1)) >= 2500;
            int i = part.first;
            while (i < part.last) {
                int stop = i + 1;
                if (stop < part.last && end(lines, i) - lines.get(i).timeMs < 3600
                        && end(lines, stop) - lines.get(stop).timeMs < 3600
                        && end(lines, stop) - lines.get(i).timeMs <= 7000) stop++;
                Shot shot = new Shot();
                shot.paragraph = p;
                shot.first = i; shot.last = stop;
                shot.start = i == part.first ? part.start : lines.get(i).timeMs;
                long next = stop < part.last ? lines.get(stop).timeMs : part.end;
                long lyricEnd = end(lines, stop - 1);
                shot.end = next - lyricEnd >= 4000 ? lyricEnd + 600 : Math.max(next, lyricEnd);
                part.shots.add(shot);
                if (shot.end < next) addBridges(part, shot.end, next, p);
                i = stop;
            }
            if (part.shots.isEmpty()) addBridges(part, part.start, part.end, p);
            for (int s = 0; s < part.shots.size(); s++) {
                Shot shot = part.shots.get(s);
                shot.profile = cast(seed, p, s, part.kind, shot.bridge, recent, family);
                recent.add(shot.profile);
                if (recent.size() > 3) recent.remove(0);
                family = FoliaLumiereProfiles.FAMILIES[shot.profile];
            }
        }
        return output;
    }

    private static void addBridges(Paragraph part, long start, long end, int paragraph) {
        int count = end - start > 16000 ? Math.max(2, (int) Math.round((end - start) / 10000.0)) : 1;
        for (int i = 0; i < count; i++) {
            Shot bridge = new Shot(); bridge.paragraph = paragraph; bridge.bridge = true;
            bridge.first = bridge.last = part.last;
            bridge.start = start + (end - start) * i / count;
            bridge.end = start + (end - start) * (i + 1) / count;
            part.shots.add(bridge);
        }
    }

    private static void split(List<LrcTimeline.Line> lines, int first, int last, List<Paragraph> output) {
        if (last - first > 6 || last - first > 1 && end(lines, last - 1) - lines.get(first).timeMs > 18000) {
            int at = Math.min(first + 4, last - 1);
            long largest = Long.MIN_VALUE;
            for (int i = first + 2; i < last - 1; i++) {
                long gap = lines.get(i).timeMs - end(lines, i - 1);
                if (gap > largest) { largest = gap; at = i; }
            }
            split(lines, first, at, output); split(lines, at, last, output);
        } else {
            Paragraph p = new Paragraph(); p.first = first; p.last = last; output.add(p);
        }
    }

    private static String classify(List<LrcTimeline.Line> lines, Paragraph p, int index, int count) {
        if (index == count - 1) return "outro";
        double duration = (end(lines, p.last - 1) - lines.get(p.first).timeMs) / 1000.0;
        int words = 0, punctuation = 0;
        for (int i = p.first; i < p.last; i++) {
            String text = lines.get(i).text;
            for (String word : text.split("[\\s\\p{Punct}]+")) if (!word.isEmpty()) {
                words += word.matches("[\\x00-\\x7F]+") ? 1 : word.codePointCount(0, word.length());
            }
            for (int j = 0; j < text.length(); j++) if ("!?！？…".indexOf(text.charAt(j)) >= 0) punctuation++;
        }
        return duration <= 3.5 || words <= 3 ? "breath" : punctuation >= 2 || words / Math.max(duration, 1) > 2.5 ? "lift" : "verse";
    }

    static int cast(String seed, int paragraph, int shot, String kind, boolean bridge, List<Integer> recent, String family) {
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < FoliaLumiereProfiles.size(); i++) choices.add(i);
        if (bridge) choices = narrow(choices, i -> ("motes".equals(FoliaLumiereProfiles.FAMILIES[i])
                || "astral".equals(FoliaLumiereProfiles.FAMILIES[i]) || "zenith".equals(FoliaLumiereProfiles.FAMILIES[i]))
                && !"loud".equals(FoliaLumiereProfiles.MOODS[i]), 1);
        if (!"break".equals(kind)) choices = narrow(choices, i -> "outro".equals(kind)
                ? "quiet".equals(FoliaLumiereProfiles.MOODS[i])
                : "chorus".equals(kind) || "lift".equals(kind)
                    ? !"quiet".equals(FoliaLumiereProfiles.MOODS[i]) : !"loud".equals(FoliaLumiereProfiles.MOODS[i]), 1);
        choices = narrow(choices, i -> !recent.contains(i), 1);
        if (!family.isEmpty()) choices = narrow(choices, i -> !family.equals(FoliaLumiereProfiles.FAMILIES[i]), 3);
        Random rng = new Random(seed + ':' + paragraph + ':' + shot + ":cast");
        return choices.get((int) (rng.next() * choices.size()));
    }

    private interface Keep { boolean accepts(int index); }
    private static List<Integer> narrow(List<Integer> choices, Keep keep, int minimum) {
        List<Integer> filtered = new ArrayList<>();
        for (int choice : choices) if (keep.accepts(choice)) filtered.add(choice);
        return filtered.size() >= minimum ? filtered : choices;
    }
    static long end(List<LrcTimeline.Line> lines, int index) {
        if (index < 0) return 0;
        LrcTimeline.Line line = lines.get(index);
        long duration = Math.max(1, line.durationMs > 0 ? line.durationMs
                : index + 1 < lines.size() ? lines.get(index + 1).timeMs - line.timeMs : 5000);
        long rawEnd = line.timeMs + duration;
        long lastWordEnd = line.words.isEmpty() ? rawEnd
                : line.words.get(line.words.size() - 1).startMs + line.words.get(line.words.size() - 1).durationMs;
        long renderEnd;
        if (duration < 100) renderEnd = Math.max(rawEnd, line.timeMs + 67);
        else if (duration < 180) {
            long enter = Math.max(45, Math.min(60, Math.round(duration * 0.45)));
            long exit = Math.max(30, Math.min(40, Math.round(duration * 0.22)));
            renderEnd = Math.max(rawEnd, Math.max(Math.max(line.timeMs + enter + 10,
                    Math.max(lastWordEnd, line.timeMs) + 30), rawEnd - exit) + exit);
        } else {
            long exit = Math.max(180, Math.min(320, Math.round(Math.max(duration, 120) * 0.18)));
            renderEnd = Math.max(rawEnd, Math.max(Math.max(lastWordEnd, line.timeMs) + 60, rawEnd - exit) + exit);
        }
        return index + 1 < lines.size() ? Math.max(line.timeMs, Math.min(renderEnd, lines.get(index + 1).timeMs)) : renderEnd;
    }

    static void camera(double[] result, double time, double start, double end, double push,
                       double driftX, double driftY, double width, double height) {
        double k = sine((time - start) / Math.max(0.001, end - start));
        double fx = (Math.sin(time * 0.21 + 0.4) * 0.6 + Math.sin(time * 0.53 + 1.9) * 0.4) * 0.006;
        double fy = (Math.cos(time * 0.17 + 1.1) * 0.6 + Math.sin(time * 0.47 + 0.3) * 0.4) * 0.006;
        result[0] = width / 2 + (driftX * k + fx) * width;
        result[1] = height / 2 + (driftY * k + fy) * height;
        result[2] = (1 + push * k) * (1 + 0.008 * Math.sin(time * 0.31 + 0.7));
        result[3] = 0.004 * Math.sin(time * 0.13 + 2.1);
    }

    static double hash11(double input) {
        double p = fract(input * 0.1031); p *= p + 33.33; p *= p + p; return fract(p);
    }
    static double hash12(double x, double y) {
        double a = fract(x * 0.1031), b = fract(y * 0.1031), c = a;
        double d = a * (b + 33.33) + b * (c + 33.33) + c * (a + 33.33);
        return fract((a + d + b + d) * (c + d));
    }
    static double noise1(double x) {
        double i = Math.floor(x), u = smooth(x - i); return mix(hash11(i), hash11(i + 1), u);
    }
    static double noise2(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), ux = smooth(x - ix), uy = smooth(y - iy);
        return mix(mix(hash12(ix, iy), hash12(ix + 1, iy), ux), mix(hash12(ix, iy + 1), hash12(ix + 1, iy + 1), ux), uy);
    }
    static double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise2(x, y); norm += amp;
            double nx = (0.8 * x + 0.6 * y) * 2.03 + 17.1;
            y = (-0.6 * x + 0.8 * y) * 2.03 + 9.2; x = nx; amp *= 0.5;
        }
        return sum / Math.max(norm, 0.0001);
    }
    static double band(double x, double duty) { double f = fract(x); return Math.min(1, 1 - step(duty - 0.06, duty + 0.06, f) + step(0.94, 1, f)); }
    static double gobo(int pattern, double frequency, double duty, double phase, double u, double v) {
        double result;
        if (pattern == 1) result = band(u * frequency * 0.5 + phase, duty);
        else if (pattern == 2) result = step(duty * 0.5, duty * 0.5 + 0.08, Math.abs(u));
        else if (pattern == 3) result = band(u * frequency * 0.5 + v * frequency * 0.8 + phase, duty)
                * band(u * frequency * 0.5 - v * frequency * 0.8 - phase, duty);
        else if (pattern == 4) result = step(duty - 0.12, duty + 0.12, noise2(u * frequency, v * frequency * 0.6 + phase));
        else return 1;
        return 0.12 + 0.88 * result;
    }
    static double away(double drift, double direction) {
        if (direction == 0) return drift;
        double sign = direction > 0 ? 1 : -1, along = drift * sign;
        return drift + Math.abs(direction) * (Math.sqrt(along * along + 0.000025) - 0.005 - along) * sign;
    }
    static double held(double vx, double vy, double age, boolean y) {
        double speed = Math.hypot(vx, vy);
        if (speed < 1e-9) return 0;
        double phase = speed * age / 0.018, along = 0.018 * Math.sin(phase), turn = 0.018 * (1 - Math.cos(phase));
        return y ? (along * vy + turn * vx) / speed : (along * vx - turn * vy) / speed;
    }
    static double decay(double time, double litAt, double delay, double strength) {
        double age = time - litAt - delay; return age <= 0 ? 0 : strength * 0.08 * Math.pow(age, 1.5);
    }
}
