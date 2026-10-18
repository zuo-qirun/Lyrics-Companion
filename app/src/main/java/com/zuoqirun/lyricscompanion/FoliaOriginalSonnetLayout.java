// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Adapted from sonnetTypographyLayout.ts / sonnetShotFlowLayouts.ts (9279251).
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Measured semantic boxes and timeline-ordered native shot layouts. */
final class FoliaOriginalSonnetLayout {
    interface Measurer { float width(String text, float size, int role); }
    static final int SUPPORT = 0, SEMI_HERO = 1, HERO = 2, DECORATION = 3;
    static final class Box {
        FoliaOriginalSonnetProgram.Segment segment;
        int role, index;
        boolean vertical, flowVertical;
        float size, width, height, x, y, rotation, enterX, enterY;
        float verticalWidth, verticalHeight, verticalSize;
        float timingPhase;
    }
    private FoliaOriginalSonnetLayout() { }

    /** Uses the original role/measurement/packing/decorating recipes with native font metrics. */
    static List<Box> buildExact(FoliaOriginalSonnetProgram.Shot shot, float width, float height,
                                float fontScale, Measurer measurer,
                                FoliaOriginalSonnetGeometryProgram recipes) throws JSONException {
        JSONArray lines = new JSONArray();
        int words = 0;
        for (FoliaOriginalSonnetProgram.CompiledLine line : shot.lines) {
            JSONArray segments = new JSONArray();
            for (FoliaOriginalSonnetProgram.Segment segment : line.segments) {
                if (segment.text.trim().isEmpty()) continue;
                if (segment.word) words++;
                JSONArray graphemes = new JSONArray();
                for (int index = segment.first; index < segment.end; index++) {
                    graphemes.put(new JSONObject().put("char", segment.timeline.glyphs[index])
                            .put("startTime", segment.timeline.starts[index] / 1000.0)
                            .put("endTime", segment.timeline.ends[index] / 1000.0));
                }
                segments.put(new JSONObject().put("text", segment.text).put("isWordLike", segment.word)
                        .put("startOffset", segment.timeline.offsets[segment.first])
                        .put("endOffset", segment.timeline.offsets[segment.end])
                        .put("startTime", segment.start() / 1000.0).put("endTime", segment.finish() / 1000.0)
                        .put("graphemes", graphemes));
            }
            if (segments.length() > 0) lines.put(segments);
        }
        double heroScale = "type-impact".equals(shot.kind) ? 1.55 : "quiet-tableau".equals(shot.kind) ? 0.82 : 1;
        double base = Math.max(24, Math.min(112, width / Math.max(7, Math.max(1, words) * 2.15) * heroScale * fontScale));
        JSONArray placements = recipes.typography(lines, shot.kind, "verse", width, height, base,
                (value, size, weight) -> measurer.width(value, (float) size,
                        weight >= 900 ? HERO : weight <= 300 ? DECORATION : SUPPORT));
        List<Box> boxes = new ArrayList<>();
        for (int index = 0; index < placements.length(); index++) {
            JSONObject placement = placements.getJSONObject(index);
            Box box = new Box();
            box.index = placement.getInt("segmentIndex");
            box.segment = shot.segments.get(box.index);
            String role = placement.getString("role");
            box.role = "hero".equals(role) ? HERO : "semi-hero".equals(role) ? SEMI_HERO : "decoration".equals(role) ? DECORATION : SUPPORT;
            box.size = (float) (base * placement.getDouble("fontScale"));
            box.width = (float) placement.getDouble("measuredWidth");
            box.height = (float) placement.getDouble("measuredHeight");
            box.x = (float) placement.getDouble("x"); box.y = (float) placement.getDouble("y");
            box.enterX = (float) placement.getDouble("enterX"); box.enterY = (float) placement.getDouble("enterY");
            box.rotation = (float) placement.getDouble("rotation");
            box.vertical = placement.getBoolean("vertical");
            box.flowVertical = "vertical".equals(placement.getString("layoutDirection"));
            box.timingPhase = (float) placement.getDouble("timingPhase");
            boxes.add(box);
        }
        return boxes;
    }

    static List<Box> build(FoliaOriginalSonnetProgram.Shot shot, float width, float height,
                           float fontScale, Measurer measurer) {
        List<Box> boxes = new ArrayList<>();
        if (shot.segments.isEmpty()) return boxes;
        IdentityHashMap<FoliaOriginalSonnetProgram.Segment, Integer> roles = new IdentityHashMap<>();
        int words = 0, variantSeed = shot.segments.size();
        for (FoliaOriginalSonnetProgram.CompiledLine line : shot.lines) {
            List<FoliaOriginalSonnetProgram.Segment> segments = new ArrayList<>();
            for (FoliaOriginalSonnetProgram.Segment segment : line.segments) if (!segment.text.trim().isEmpty()) segments.add(segment);
            if (segments.isEmpty()) continue;
            int hero = FoliaOriginalSonnetProgram.hero(segments);
            roles.put(segments.get(hero), HERO);
            for (Integer semi : FoliaOriginalSonnetProgram.semiHeroes(segments, hero)) roles.put(segments.get(semi), SEMI_HERO);
        }
        for (FoliaOriginalSonnetProgram.Segment segment : shot.segments) {
            if (segment.word) words++;
            variantSeed += Math.max(1, segment.text.trim().length());
        }
        int editorial = variantSeed % 5, tableau = variantSeed % 4, ribbon = variantSeed % 3, collage = variantSeed % 3;
        int hero = FoliaOriginalSonnetProgram.hero(shot.segments), secondaryHero = -1;
        if (editorial == 3 && shot.segments.size() > 2) {
            float best = -1;
            for (int i = 0; i < shot.segments.size(); i++) {
                FoliaOriginalSonnetProgram.Segment segment = shot.segments.get(i);
                if (i == hero || !segment.word || segment.visibleLength() == 0) continue;
                float score = segment.score() + (Math.abs(i - hero) > 1 ? 50 : 0);
                if (score > best) { best = score; secondaryHero = i; }
            }
            if (secondaryHero < 0) editorial = 0;
        } else if (editorial == 3) editorial = 0;
        else if (editorial == 4 && shot.segments.size() < 2) editorial = 2;
        float heroScale = "type-impact".equals(shot.kind) ? 1.55f : "quiet-tableau".equals(shot.kind) ? 0.82f : 1;
        float base = Math.max(24, Math.min(112, width / Math.max(7, words * 2.15f) * heroScale * fontScale));
        for (int i = 0; i < shot.segments.size(); i++) {
            Box box = new Box(); box.segment = shot.segments.get(i); box.index = i;
            box.role = roles.containsKey(box.segment) ? roles.get(box.segment) : SUPPORT;
            if (i == secondaryHero && "editorial-column".equals(shot.kind) && editorial == 3) box.role = HERO;
            boolean emphasized = box.role == HERO || box.role == SEMI_HERO;
            float h = 3, support = 1.15f;
            switch (shot.kind) {
                case "editorial-column":
                    h = editorial == 3 ? 3.8f : editorial == 4 ? 4.2f : editorial == 2 ? 3.2f : 4;
                    support = editorial == 3 ? 1.3f : editorial == 4 ? 1.25f : 1.2f;
                    box.vertical = emphasized && editorial != 2 && editorial != 3; break;
                case "type-impact": h = 5.5f; support = 1.5f; break;
                case "fragment-collage": h = 3.2f; support = 1.35f; box.vertical = box.role == SEMI_HERO || i % 4 == 0; break;
                case "tracking-ribbon": h = 3.5f; support = 1.5f; break;
                case "mask-reveal": h = 4.5f; support = 1.6f; box.vertical = emphasized; break;
                case "poster-blocks": h = 4.4f; break;
                default: box.vertical = emphasized && (tableau == 0 || tableau == 1); break;
            }
            box.size = base * (box.role == HERO ? h : box.role == SEMI_HERO ? Math.max(support * 1.35f, h * 0.72f) : support);
            boolean cjk = containsCjk(box.segment.text);
            if (box.vertical && box.segment.visibleLength() > 1 && !cjk) {
                box.vertical = false; box.rotation = (float) Math.PI / 2;
            }
            float advance = 0, maximum = 0;
            for (int g = box.segment.first; g < box.segment.end; g++) {
                String glyph = box.segment.timeline.glyphs[g];
                float measured = Math.max(box.size * 0.2f, measurer.width(glyph, box.size, box.role));
                advance += measured; maximum = Math.max(maximum, measured);
            }
            box.width = box.vertical ? maximum : box.rotation != 0 ? box.size * 1.2f : measurer.width(box.segment.text, box.size, box.role);
            box.height = box.vertical ? (box.segment.end - box.segment.first) * box.size * 0.9f : box.rotation != 0 ? advance : box.size * 1.2f;
            float fit = Math.min(1, Math.min(width * 0.82f / Math.max(1, box.width), height * 0.82f / Math.max(1, box.height)));
            scale(box, fit);
            if ("poster-blocks".equals(shot.kind) && cjk) {
                float columnWidth = 0;
                for (int g = box.segment.first; g < box.segment.end; g++) {
                    columnWidth = Math.max(columnWidth, Math.max(box.size * 0.2f,
                            measurer.width(box.segment.timeline.glyphs[g], box.size, box.role)));
                }
                float columnHeight = Math.max(1, box.segment.end - box.segment.first) * box.size * 0.9f;
                float verticalFit = Math.min(1, Math.min(width * 0.82f / Math.max(1, columnWidth),
                        height * 0.82f / Math.max(1, columnHeight)));
                box.verticalWidth = columnWidth * verticalFit;
                box.verticalHeight = columnHeight * verticalFit;
                box.verticalSize = box.size * verticalFit;
            }
            boxes.add(box);
        }
        // Collage flattens rotated Latin words once before the global fitting ladder snapshots.
        if ("fragment-collage".equals(shot.kind)) for (int i = 0; i < boxes.size(); i++) {
            if (i == hero) continue;
            Box box = boxes.get(i);
            if (Math.abs(Math.round(box.rotation / (Math.PI / 2)) % 2) == 1) { float swap = box.width; box.width = box.height; box.height = swap; }
            box.rotation = 0;
        }
        float[] sizes = new float[boxes.size()], widths = new float[boxes.size()], heights = new float[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) { Box box = boxes.get(i); sizes[i] = box.size; widths[i] = box.width; heights[i] = box.height; }
        float flow = Math.max(16, Math.min(40, base * 0.35f)), stack = Math.max(24, flow * 1.35f);
        if ("poster-blocks".equals(shot.kind)) {
            StringBuilder signature = new StringBuilder();
            for (Box box : boxes) {
                if (signature.length() > 0) signature.append('\u241f');
                signature.append(box.segment.text);
            }
            poster(boxes, width, height, base, FoliaOriginalSonnetProgram.hash(signature.toString()));
        } else {
        for (float fit : new float[]{1, 0.92f, 0.84f, 0.76f, 0.68f, 0.6f, 0.52f}) {
            for (int i = 0; i < boxes.size(); i++) {
                Box box = boxes.get(i); box.size = sizes[i] * fit; box.width = widths[i] * fit; box.height = heights[i] * fit;
            }
            switch (shot.kind) {
                case "quiet-tableau": quiet(boxes, hero, tableau, height, stack); break;
                case "tracking-ribbon": ribbon(boxes, hero, ribbon, flow); break;
                case "editorial-column": editorial(boxes, hero, secondaryHero, editorial, width, height, flow, stack); break;
                case "fragment-collage": collage(boxes, hero, collage, fit, flow, stack); break;
                default: cross(boxes, hero, height, flow, stack); break;
            }
            boolean fits = true;
            for (Box box : boxes) if (Math.abs(box.x) + box.width / 2 > width * 0.48f + 0.5f
                    || Math.abs(box.y) + box.height / 2 > height * 0.46f + 0.5f) { fits = false; break; }
            if (fits) break;
        }
        }
        Box heroBox = boxes.get(hero); heroBox.enterX = 0; heroBox.enterY = height * 0.15f;
        if (!"quiet-tableau".equals(shot.kind) && !"poster-blocks".equals(shot.kind)) {
            List<Box> decorations = new ArrayList<>(); int index = 0;
            for (Box box : boxes) if (box.role == HERO) {
                decorations.add(decoration(box, base * Math.max(2.8f, Math.min(box.size / base * 3.5f, 5.5f)),
                        box.x - width * (0.1f - index * 0.03f), box.y - height * (0.05f - index * 0.02f),
                        -0.15f + (index % 2 == 0 ? 0 : 0.05f), -width * 0.05f, -height * 0.05f)); index++;
            }
            if (boxes.size() > 1) {
                Box other = boxes.get(boxes.size() - 1).role == HERO ? boxes.get(0) : boxes.get(boxes.size() - 1);
                decorations.add(decoration(other, base * Math.max(1.8f, Math.min(heroBox.size / base * 2.2f, 3.5f)),
                        heroBox.x + width * 0.25f, heroBox.y + height * 0.15f, 0.08f, width * 0.05f, height * 0.05f));
            }
            boxes.addAll(0, decorations);
        }
        return boxes;
    }

    private static Box decoration(Box source, float size, float x, float y, float rotation, float enterX, float enterY) {
        Box box = new Box(); box.segment = source.segment; box.index = source.index; box.role = DECORATION;
        box.size = size; box.x = x; box.y = y; box.rotation = rotation; box.enterX = enterX; box.enterY = enterY;
        box.width = source.width; box.height = source.height; return box;
    }

    private static void scale(Box box, float scale) { box.size *= scale; box.width *= scale; box.height *= scale; }

    private static void quiet(List<Box> boxes, int hero, int variant, float height, float gap) {
        Box h = boxes.get(hero); h.x = 0; h.y = variant >= 2 ? 0 : -height * 0.1f;
        float step = maxWidth(boxes) + gap + (variant == 3 ? 70 : 0), half = height * 0.46f;
        for (int side = -1; side <= 1; side += 2) {
            float y = h.y + side * (h.height / 2 + gap); int column = 0;
            for (int i = hero + side; i >= 0 && i < boxes.size(); i += side) {
                Box box = boxes.get(i);
                if (side < 0 ? y - box.height < -half : y + box.height > half) { column++; y = -side * half; }
                box.x = (variant == 1 ? -h.width / 2 + box.width / 2 : variant == 3 ? (i % 2 == 0 ? 35 : -35) : 0) - side * column * step;
                box.y = y + side * box.height / 2; y += side * (box.height + gap); box.flowVertical = variant < 2;
                box.enterX = variant == 1 ? -side * 20 : variant == 3 ? (box.x > h.x ? 30 : -30) : 0;
                box.enterY = variant == 1 || variant == 3 ? 0 : -side * 20;
            }
        }
    }

    private static void ribbon(List<Box> boxes, int hero, int variant, float gap) {
        Box h = boxes.get(hero); h.x = h.y = 0;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (h.width / 2 + gap);
            for (int i = hero + side; i >= 0 && i < boxes.size(); i += side) {
                Box box = boxes.get(i); box.flowVertical = false;
                box.x = x + side * box.width / 2;
                box.y = variant == 1 ? h.height / 2 - box.height / 2 : variant == 2 ? -h.height / 2 + box.height / 2 : i % 2 == 0 ? 10 : -10;
                x += side * (box.width + gap); box.enterX = -side * (variant == 2 ? 20 : 30); box.enterY = 0;
            }
        }
    }

    private static void editorial(List<Box> boxes, int hero, int secondary, int variant,
                                  float width, float height, float flow, float stack) {
        Box h = boxes.get(hero); h.x = h.y = 0;
        if (variant == 0) {
            h.x = -width * 0.15f;
            float beforeY = -h.height / 2 + stack * 0.5f, afterY = beforeY;
            for (int i = 0; i < boxes.size(); i++) {
                if (i == hero) continue;
                Box box = boxes.get(i); boolean before = i < hero; box.flowVertical = true;
                box.x = h.x + (before ? 1 : -1) * (h.width / 2 + flow + box.width / 2);
                box.y = (before ? beforeY : afterY) + box.height / 2;
                if (before) beforeY += box.height + stack; else afterY += box.height + stack;
                box.enterX = before ? -20 : 20; box.enterY = 0;
            }
        } else if (variant == 1) {
            float total = -stack, raw = 0;
            for (Box box : boxes) { total += box.height + stack; raw += box.height; }
            boolean single = raw * 0.52f + stack * (boxes.size() - 1) <= height * 0.92f;
            float y = single ? -total / 2 : -height * 0.46f; int rail = 0;
            for (Box box : boxes) {
                if (!single && y + box.height > height * 0.46f) { rail++; y = -height * 0.46f; }
                box.x = width * 0.28f - rail * (maxWidth(boxes) + stack) - box.width / 2;
                box.y = y + box.height / 2; y += box.height + stack;
                box.enterX = 20; box.enterY = 0; box.flowVertical = true;
            }
        } else if (variant == 2) {
            h.y = -height * 0.25f;
            float beforeWidth = -flow, beforeHeight = 0;
            for (int i = 0; i < hero; i++) { Box box = boxes.get(i); beforeWidth += box.width + flow; beforeHeight = Math.max(beforeHeight, box.height); }
            float x = -beforeWidth / 2;
            for (int i = 0; i < hero; i++) {
                Box box = boxes.get(i); box.x = x + box.width / 2; x += box.width + flow;
                box.y = h.y - h.height / 2 - stack - beforeHeight / 2; box.enterX = 0; box.enterY = -20;
            }
            float y = h.y + h.height / 2 + stack;
            for (int i = hero + 1; i < boxes.size(); i += 2) {
                Box left = boxes.get(i), right = i + 1 < boxes.size() ? boxes.get(i + 1) : null;
                left.x = -h.width * 0.25f - flow - left.width / 2; left.y = y + left.height / 2; left.enterX = -20; left.enterY = 0;
                if (right != null) { right.x = h.width * 0.25f + flow + right.width / 2; right.y = y + right.height / 2; right.enterX = 20; right.enterY = 0; }
                y += Math.max(left.height, right == null ? 0 : right.height) + stack;
            }
        } else if (variant == 3 && secondary >= 0) {
            int split = Math.min(hero, secondary) + 1;
            float h1 = maxHeight(boxes, 0, split), h2 = maxHeight(boxes, split, boxes.size());
            float w1 = row(boxes, 0, split, -(h1 + stack + h2) / 2 + h1 / 2, flow, 30);
            float w2 = row(boxes, split, boxes.size(), -(h1 + stack + h2) / 2 + h1 + stack + h2 / 2, flow, -30);
            float offset = Math.max(w1, w2) * 0.12f;
            for (int i = 0; i < boxes.size(); i++) boxes.get(i).x += i < split ? -offset : offset;
        } else {
            // Logo badge: earlier words span the page, then supports wrap around the hero pillar.
            boolean right = hero == boxes.size() - 1;
            float left = -width * 0.4f, edge = width * 0.4f;
            float y = flowRows(boxes, 0, hero, left, edge, -height * 0.34f, flow, stack);
            y += stack; h.x = right ? edge - h.width / 2 : left + h.width / 2; h.y = y + h.height / 2;
            float beside = right ? edge - h.width - flow : left + h.width + flow;
            float x = right ? left : beside, rowHeight = 0, rowLeft = x, rowRight = right ? beside : edge;
            for (int i = hero + 1; i < boxes.size(); i++) {
                Box box = boxes.get(i);
                if (x > rowLeft && x + box.width > rowRight) {
                    y += rowHeight + stack; rowHeight = 0;
                    if (y >= h.y + h.height / 2 + stack - 0.5f) { rowLeft = left; rowRight = edge; }
                    x = rowLeft;
                }
                box.x = x + box.width / 2; box.y = y + box.height / 2; x += box.width + flow;
                rowHeight = Math.max(rowHeight, box.height); box.enterX = right ? -25 : 25; box.enterY = 0;
            }
        }
    }

    private static float row(List<Box> boxes, int first, int end, float y, float gap, float enter) {
        float width = -gap;
        for (int i = first; i < end; i++) width += boxes.get(i).width + gap;
        float x = -width / 2;
        for (int i = first; i < end; i++) { Box box = boxes.get(i); box.x = x + box.width / 2; box.y = y; x += box.width + gap; box.enterX = enter; box.enterY = 0; }
        return Math.max(0, width);
    }

    private static void collage(List<Box> boxes, int hero, int variant, float scale, float flow, float stack) {
        Box h = boxes.get(hero); h.x = h.y = 0;
        float base = (float) Math.hypot(h.width, h.height) / 2 + stack;
        List<Box> placed = new ArrayList<>(); placed.add(h);
        double angle = Math.PI / 4; int support = 0, count = Math.max(1, boxes.size() - 1);
        for (int i = 0; i < boxes.size(); i++) {
            if (i == hero) continue;
            Box box = boxes.get(i);
            float radius = base + (variant == 1 ? 35 + support / (float) count * 150 : variant == 2 ? support % 2 == 1 ? 140 : 50 : 45 + support * 23 % 90) * scale;
            support++; boolean clear = false; double candidate = angle;
            for (int ring = 0; ring < 14 && !clear; ring++) {
                for (int attempt = 0; attempt < 400; attempt++) {
                    box.x = (float) Math.cos(candidate) * radius; box.y = (float) Math.sin(candidate) * radius * 0.65f;
                    clear = true;
                    for (Box other : placed) if (Math.max(Math.abs(box.x - other.x) - (box.width + other.width) / 2,
                            Math.abs(box.y - other.y) - (box.height + other.height) / 2) < flow) { clear = false; break; }
                    if (clear) break; candidate += 0.07;
                }
                if (!clear) radius += (36 + ring * 12) * scale;
            }
            angle = candidate + 0.02; placed.add(box);
            box.flowVertical = Math.abs(Math.cos(candidate)) >= Math.abs(Math.sin(candidate));
            box.enterX = (float) Math.cos(candidate) * -60; box.enterY = (float) Math.sin(candidate) * -60;
        }
    }

    private static void cross(List<Box> boxes, int hero, float height, float flow, float stack) {
        Box h = boxes.get(hero); h.x = h.y = 0;
        int top = hero / 2, right = (int) Math.ceil((boxes.size() - 1 - hero) / 2f);
        float topStretch = growColumn(boxes, 0, top, h, height, stack);
        float bottomStretch = growColumn(boxes, hero + right + 1, boxes.size(), h, height, stack);
        float x = -h.width / 2 - stack;
        for (int i = hero - 1; i >= top; i--) { Box box = boxes.get(i); box.x = x - box.width / 2; box.y = i % 2 == 0 ? 10 : -10; x -= box.width + flow; box.enterX = -30; box.enterY = 0; }
        float y = -h.height / 2 - stack;
        for (int i = top - 1; i >= 0; i--) { Box box = boxes.get(i); box.x = i % 2 == 0 ? 15 : -15; box.y = y - box.height / 2; y -= box.height + stack + topStretch; box.flowVertical = true; box.enterX = 0; box.enterY = -30; }
        x = h.width / 2 + stack;
        for (int i = hero + 1; i <= hero + right; i++) { Box box = boxes.get(i); box.x = x + box.width / 2; box.y = i % 2 == 0 ? 10 : -10; x += box.width + flow; box.enterX = 30; box.enterY = 0; }
        y = h.height / 2 + stack;
        for (int i = hero + right + 1; i < boxes.size(); i++) { Box box = boxes.get(i); box.x = i % 2 == 0 ? 15 : -15; box.y = y + box.height / 2; y += box.height + stack + bottomStretch; box.flowVertical = true; box.enterX = 0; box.enterY = 30; }
    }

    private static float growColumn(List<Box> boxes, int first, int end, Box hero, float height, float gap) {
        int count = end - first;
        if (count <= 0) return 0;
        float available = Math.max(0, height * 0.46f - hero.height / 2 - gap), total = 0;
        if (available <= 0) return 0;
        for (int i = first; i < end; i++) total += boxes.get(i).height;
        if (total + gap * (count - 1) < available * 0.72f) {
            float boost = Math.min(2.2f, (available * 0.72f - gap * (count - 1)) / Math.max(1, total));
            for (int i = first; i < end; i++) { Box box = boxes.get(i); float capped = Math.min(boost, hero.size * 0.6f / box.size); if (capped > 1.05f) scale(box, capped); }
        }
        if (count < 2) return 0;
        total = 0; for (int i = first; i < end; i++) total += boxes.get(i).height;
        return Math.max(0, Math.min(gap * 2, (available * 0.95f - total) / (count - 1) - gap));
    }

    private static final class PosterPlacement {
        Box box;
        float u, v, uSize, vSize, size;
        boolean vertical;
    }

    private static final class PosterAttempt {
        final List<PosterPlacement> placements = new ArrayList<>();
        float total;
    }

    private static final class PosterChip {
        Box box;
        float uSize, vSize, size, shrink = 1;
        boolean vertical;
    }

    /** Port of sonnetPosterBlocksLayout.ts: seeded row/column zone flow and global fitting. */
    static void poster(List<Box> boxes, float width, float height, float base, long seed) {
        float gap = Math.max(16, Math.min(40, base * 0.35f));
        boolean vertical = seed % 2 != 0;
        float u = vertical ? height * 0.8f : width * 0.84f;
        float v = vertical ? width * 0.84f : height * 0.8f;
        PosterAttempt attempt = null;
        for (float scale : new float[]{1, 0.92f, 0.84f, 0.76f, 0.68f, 0.6f, 0.52f}) {
            attempt = posterAttempt(boxes, vertical, u, v, scale, gap, gap * 1.15f, seed);
            if (attempt.total <= v + 0.5f) break;
        }
        if (attempt == null) return;
        if (attempt.total > v) {
            float fit = v / attempt.total;
            for (PosterPlacement placement : attempt.placements) {
                placement.u *= fit;
                placement.v *= fit;
                placement.uSize *= fit;
                placement.vSize *= fit;
                placement.size *= fit;
            }
            attempt.total = v;
        }
        float shift = Math.max(0, (v - attempt.total) / 2);
        for (PosterPlacement placement : attempt.placements) {
            Box box = placement.box;
            box.size = placement.size;
            if (vertical) {
                box.width = placement.vSize;
                box.height = placement.uSize;
                box.x = width * 0.42f - placement.v - shift - placement.vSize / 2;
                box.y = -height * 0.4f + placement.u + placement.uSize / 2;
            } else {
                box.width = placement.uSize;
                box.height = placement.vSize;
                box.x = -width * 0.42f + placement.u + placement.uSize / 2;
                box.y = -height * 0.4f + placement.v + shift + placement.vSize / 2;
            }
            box.rotation = 0;
            box.vertical = placement.vertical;
            box.flowVertical = vertical;
            box.enterX = vertical ? Math.min(18, base * 0.25f)
                    : (box.x < 0 ? -1 : 1) * Math.min(28, base * 0.45f);
            box.enterY = vertical ? (box.y < 0 ? -1 : 1) * Math.min(28, base * 0.45f)
                    : Math.min(18, base * 0.25f);
        }
    }

    private static PosterAttempt posterAttempt(List<Box> boxes, boolean vertical, float u, float v,
                                               float scale, float chipGap, float lineGap, long seed) {
        PosterAttempt result = new PosterAttempt();
        float cursor = 0, floatExtent = 0, floatBottom = 0;
        boolean endSide = (seed >> 1 & 1) == 1;
        for (int i = 0; i < boxes.size();) {
            if (floatBottom <= cursor) {
                floatExtent = 0;
                floatBottom = 0;
            }
            Box box = boxes.get(i);
            if (box.role == SUPPORT) {
                int end = i + 1;
                while (end < boxes.size() && boxes.get(end).role == SUPPORT) end++;
                float capacity = Math.max(chipGap * 2, u - floatExtent);
                float start = floatExtent, used = 0;
                List<PosterChip> row = new ArrayList<>();
                for (int index = i; index < end; index++) {
                    PosterChip chip = posterChip(boxes.get(index), vertical, scale);
                    float needed = used + (row.isEmpty() ? 0 : chipGap) + chip.uSize;
                    if (needed > capacity && !row.isEmpty()) {
                        cursor = flushPosterRow(result, row, used, capacity, start, cursor, chipGap, lineGap);
                        row.clear();
                        used = 0;
                    }
                    if (chip.uSize > capacity) {
                        chip.shrink = Math.max(0.5f, capacity / chip.uSize);
                        row.add(chip);
                        cursor = flushPosterRow(result, row, 0, capacity, start, cursor, chipGap, lineGap);
                        row.clear();
                        used = 0;
                    } else {
                        used += (row.isEmpty() ? 0 : chipGap) + chip.uSize;
                        row.add(chip);
                    }
                }
                if (!row.isEmpty()) cursor = flushPosterRow(result, row, used, capacity, start, cursor, chipGap, lineGap);
                i = end;
            } else {
                cursor = Math.max(cursor, floatBottom);
                floatBottom = 0;
                floatExtent = 0;
                PosterChip chip = posterChip(box, vertical, scale);
                boolean groupFollows = i + 1 < boxes.size() && boxes.get(i + 1).role == SUPPORT;
                float shrink = Math.min(1, Math.min(u * (groupFollows ? 0.62f : 0.9f) / Math.max(1, chip.uSize),
                        v * 0.66f / Math.max(1, chip.vSize)));
                PosterPlacement placement = new PosterPlacement();
                placement.box = box;
                placement.uSize = chip.uSize * shrink;
                placement.vSize = chip.vSize * shrink;
                placement.u = boxes.size() == 1 ? (u - placement.uSize) / 2
                        : groupFollows || !endSide ? 0 : u - placement.uSize;
                placement.v = cursor;
                placement.size = chip.size * shrink;
                placement.vertical = chip.vertical;
                result.placements.add(placement);
                if (groupFollows) {
                    floatExtent = placement.uSize + chipGap;
                    floatBottom = cursor + placement.vSize + lineGap;
                } else {
                    cursor += placement.vSize + lineGap;
                    endSide = !endSide;
                }
                i++;
            }
        }
        for (PosterPlacement placement : result.placements) result.total = Math.max(result.total, placement.v + placement.vSize);
        return result;
    }

    private static PosterChip posterChip(Box box, boolean vertical, float scale) {
        PosterChip chip = new PosterChip();
        chip.box = box;
        chip.vertical = vertical && box.verticalSize > 0;
        float width = (chip.vertical ? box.verticalWidth : box.width) * scale;
        float height = (chip.vertical ? box.verticalHeight : box.height) * scale;
        chip.size = (chip.vertical ? box.verticalSize : box.size) * scale;
        chip.uSize = vertical ? height : width;
        chip.vSize = vertical ? width : height;
        return chip;
    }

    private static float flushPosterRow(PosterAttempt result, List<PosterChip> row, float used,
                                       float capacity, float start, float cursor, float gap, float lineGap) {
        float rowHeight = 0;
        for (PosterChip chip : row) rowHeight = Math.max(rowHeight, chip.vSize * chip.shrink);
        float spread = row.size() > 1 && capacity - used > 0
                ? Math.min((capacity - used) / (row.size() - 1), gap * 2.5f) : 0;
        for (PosterChip chip : row) {
            PosterPlacement placement = new PosterPlacement();
            placement.box = chip.box;
            placement.u = start;
            placement.v = cursor;
            placement.uSize = chip.uSize * chip.shrink;
            placement.vSize = chip.vSize * chip.shrink;
            placement.size = chip.size * chip.shrink;
            placement.vertical = chip.vertical;
            result.placements.add(placement);
            start += placement.uSize + gap + spread;
        }
        return cursor + rowHeight + lineGap;
    }

    private static float flowRows(List<Box> boxes, int first, int end, float left, float right,
                                  float top, float gap, float stack) {
        float x = left, y = top, rowHeight = 0;
        for (int i = first; i < end; i++) {
            Box box = boxes.get(i);
            if (x > left && x + box.width > right) { y += rowHeight + stack; x = left; rowHeight = 0; }
            box.x = x + box.width / 2; box.y = y + box.height / 2; x += box.width + gap;
            rowHeight = Math.max(rowHeight, box.height); box.enterX = 25; box.enterY = 0;
        }
        return y + rowHeight;
    }

    private static float maxWidth(List<Box> boxes) { float result = 0; for (Box box : boxes) result = Math.max(result, box.width); return result; }
    private static float maxHeight(List<Box> boxes, int first, int end) { float result = 0; for (int i = first; i < end; i++) result = Math.max(result, boxes.get(i).height); return result; }
    private static boolean containsCjk(String value) {
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c >= 0x4e00 && c <= 0x9fff || c >= 0x3040 && c <= 0x30ff || c >= 0xac00 && c <= 0xd7af) return true; }
        return false;
    }
}
