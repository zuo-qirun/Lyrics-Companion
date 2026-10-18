// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Typed geometry collection primitives for the development-time generated Java formulas.
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** No parsing/evaluation of scripts at runtime: generated Java calls this small vector builder. */
class FoliaTemperaGeometryOps {
    interface Fn { Object apply(Object... values); }
    static final class Cell { Object v; Cell(Object value) { v = value; } }
    static final class Spread { final Object value; Spread(Object value) { this.value = value; } }
    static final Fn HASH = values -> FoliaTemperaMotion.random((long) number(arg(values, 0)), (int) number(arg(values, 1)), (int) number(arg(values, 2)));
    static final Fn COLOR = values -> arg(values, 1);
    static final Fn GRADIENT = values -> arg(values, 1);
    static final Fn PROFILE = values -> object("sharedDecor", !String.valueOf(arg(values, 0)).startsWith("monogatari-"));
    static final Fn HASH_STRING = values -> FoliaTemperaMotion.hash(string(arg(values, 0)));
    static final Fn MIX_SEED = values -> ((long) (((int) (long) number(arg(values, 0)) ^ (int) (long) number(arg(values, 1))) * (int) 2654435761L)) & 0xffffffffL;
    static final Fn STRING = values -> string(arg(values, 0));
    static final Fn SEGMENTS = values -> get(arg(values, 0), "nativeSegments");
    static final Fn RENDER_END = values -> get(arg(values, 0), "renderEndTime");
    static final Object ENTER_STYLES = array("slide", "from-left", "from-right", "from-above", "from-below", "swing", "stamp");
    static final Fn CREATE_MEASURE = values -> values[0];
    static final Fn BUILD_WORD = values -> {
        Object ctx = values[0], segment = values[1];
        List<Object> glyphs = list(get(segment, "graphemes"));
        if (glyphs.isEmpty()) return null;
        double font = number(values[4]) * number(values[5]);
        List<Double> raw = new ArrayList<>(); double rawTotal = 0;
        for (Object glyph : glyphs) {
            String text = string(get(glyph, "char"));
            double width = text.trim().isEmpty() ? font * .3 : Math.max(font * .08, number(call(get(ctx, "measure"), text, font)));
            raw.add(width); rawTotal += width;
        }
        String visible = string(get(segment, "text")).replaceAll("\\s+$", "");
        double shaped = Math.max(font * .08, number(call(get(ctx, "measure"), visible, font)));
        double correction = rawTotal > 0 ? shaped / rawTotal : 1, offset = 0;
        List<Object> placed = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            Object glyph = glyphs.get(i); double width = raw.get(i) * correction;
            placed.add(object("char", get(glyph, "char"), "startTime", get(glyph, "startTime"), "endTime", get(glyph, "endTime"), "offset", offset, "width", width)); offset += width;
        }
        return object("lineIndex", values[2], "segmentIndex", values[3], "text", get(segment, "text"),
                "startOffset", get(segment, "startOffset"), "endOffset", get(segment, "endOffset"), "leadingGap", 0d,
                "scale", values[5], "width", offset, "glyphs", placed,
                "startTime", get(placed.get(0), "startTime"), "endTime", get(placed.get(placed.size() - 1), "endTime"));
    };
    static final Fn CHOOSE = values -> {
        List<Object> choices = list(values[0]); int start = (int) (FoliaTemperaMotion.hash(string(values[1])) % choices.size());
        for (int offset = 0; offset < choices.size(); offset++) {
            Object candidate = choices.get((start + offset) % choices.size());
            if (!equal(candidate, arg(values, 2))) return candidate;
        }
        return choices.get(start);
    };

    static Object arg(Object[] values, int index) { return index < values.length ? values[index] : null; }
    static double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : value == null ? 0 : value instanceof Boolean ? (Boolean) value ? 1 : 0 : Double.parseDouble(String.valueOf(value)); }
    static boolean truth(Object value) { return value != null && (!(value instanceof Boolean) || (Boolean) value) && (!(value instanceof Number) || number(value) != 0 && !Double.isNaN(number(value))) && (!(value instanceof String) || !((String) value).isEmpty()); }
    static Object fallback(Object value, Object alternative) { return value == null ? alternative : value; }
    static String string(Object value) { return value instanceof Number && number(value) == Math.rint(number(value)) ? String.valueOf((long) number(value)) : String.valueOf(value); }
    static Object add(Object a, Object b) { return a instanceof String || b instanceof String ? string(a) + string(b) : number(a) + number(b); }
    static Object increment(Cell cell, int by) { Object previous = cell.v; cell.v = number(previous) + by; return previous; }
    static Object numberMethod(String name, Object value) { return "isFinite".equals(name) ? value instanceof Number && !Double.isNaN(number(value)) && !Double.isInfinite(number(value)) : value instanceof Number && Double.isNaN(number(value)); }
    static Pattern regex(String expression) {
        int end = expression.lastIndexOf('/'); String flags = expression.substring(end + 1);
        int mode = flags.indexOf('i') >= 0 ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        if (flags.indexOf('s') >= 0) mode |= Pattern.DOTALL;
        return Pattern.compile(expression.substring(1, end), mode);
    }
    static boolean equal(Object a, Object b) { return a instanceof Number && b instanceof Number ? number(a) == number(b) : Objects.equals(a, b); }
    static Spread spread(Object value) { return new Spread(value); }
    static void discard(Object value) { }

    static List<Object> array(Object... values) {
        List<Object> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Spread) result.addAll(list(((Spread) value).value)); else result.add(value);
        }
        return result;
    }
    static Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Spread) result.putAll(map(((Spread) values[i]).value));
            else result.put(String.valueOf(values[i]), values[++i]);
        }
        return result;
    }
    @SuppressWarnings("unchecked") static List<Object> list(Object value) { return (List<Object>) value; }
    static Object mapFrom(Object entries) { Map<String, Object> out = new LinkedHashMap<>(); for (Object entry : list(entries)) out.put(string(get(entry, 0d)), get(entry, 1d)); return out; }
    @SuppressWarnings("unchecked") static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    static Object get(Object owner, Object key) {
        if (owner == null) return null;
        if (owner instanceof String && "length".equals(key)) return (double) ((String) owner).length();
        if (owner instanceof List) {
            if ("length".equals(key)) return (double) list(owner).size();
            int index = (int) number(key); return index >= 0 && index < list(owner).size() ? list(owner).get(index) : null;
        }
        return map(owner).get(string(key));
    }
    static Object put(Object owner, Object key, Object value) {
        if (owner instanceof List) list(owner).set((int) number(key), value); else map(owner).put(string(key), value);
        return value;
    }
    static Object call(Object function, Object... values) { return ((Fn) function).apply(array(values).toArray()); }
    static Object arrayFrom(Object... values) {
        Object spec = values[0], callback = arg(values, 1);
        if (spec instanceof String) {
            List<Object> result = new ArrayList<>(); String text = (String) spec;
            for (int offset = 0; offset < text.length();) { int cp = text.codePointAt(offset); String part = new String(Character.toChars(cp)); offset += Character.charCount(cp); result.add(part); }
            return result;
        }
        int length = (int) number(get(spec, "length"));
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < length; i++) result.add(call(callback, null, (double) i));
        return result;
    }
    static Object method(Object owner, String name, Object... values) {
        if (owner instanceof Pattern && "test".equals(name)) return ((Pattern) owner).matcher(string(values[0])).find();
        if (owner instanceof String) {
            String text = (String) owner;
            if ("trim".equals(name)) return text.trim();
            if ("match".equals(name)) { Matcher matcher = ((Pattern) values[0]).matcher(text); List<Object> found = new ArrayList<>(); while (matcher.find()) found.add(matcher.group()); return found.isEmpty() ? null : found; }
        }
        if (owner instanceof Map) {
            if (get(owner, name) instanceof Fn) return call(get(owner, name), values);
            if ("get".equals(name)) return get(owner, values[0]);
            if ("set".equals(name)) { put(owner, values[0], values[1]); return owner; }
            return call(get(owner, name), values);
        }
        List<Object> entries = list(owner);
        switch (name) {
            case "push": entries.addAll(array(values)); return (double) entries.size();
            case "at": { int i = (int) number(values[0]); if (i < 0) i += entries.size(); return i < 0 || i >= entries.size() ? null : entries.get(i); }
            case "includes": return entries.contains(values[0]);
            case "some": { for (int i = 0; i < entries.size(); i++) if (truth(call(values[0], entries.get(i), (double) i))) return true; return false; }
            case "join": { StringBuilder joined = new StringBuilder(); String delimiter = values.length == 0 ? "," : string(values[0]); for (Object value : entries) { if (joined.length() > 0) joined.append(delimiter); joined.append(value == null ? "" : string(value)); } return joined.toString(); }
            case "sort": entries.sort((a, b) -> (int) Math.signum(number(call(values[0], a, b)))); return entries;
            case "reduce": { Object result = values[1]; for (int i = 0; i < entries.size(); i++) result = call(values[0], result, entries.get(i), (double) i); return result; }
            case "slice": {
                int first = values.length == 0 ? 0 : (int) number(values[0]);
                int last = values.length < 2 ? entries.size() : (int) number(values[1]);
                if (first < 0) first += entries.size(); if (last < 0) last += entries.size();
                return new ArrayList<>(entries.subList(Math.max(0, Math.min(entries.size(), first)), Math.max(0, Math.min(entries.size(), last))));
            }
            case "forEach": case "map": case "filter": case "flatMap": {
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    Object resolved = call(values[0], entries.get(i), (double) i);
                    if ("filter".equals(name)) { if (truth(resolved)) result.add(entries.get(i)); }
                    else if ("flatMap".equals(name)) result.addAll(list(resolved));
                    else result.add(resolved);
                }
                return result;
            }
            default: throw new IllegalArgumentException("Unsupported geometry collection operation " + name);
        }
    }
    static Object math(String name, Object... values) {
        List<Object> numbers = array(values); double a = numbers.isEmpty() ? 0 : number(numbers.get(0));
        switch (name) {
            case "min": { double v = Double.POSITIVE_INFINITY; for (Object n : numbers) v = Math.min(v, number(n)); return v; }
            case "max": { double v = Double.NEGATIVE_INFINITY; for (Object n : numbers) v = Math.max(v, number(n)); return v; }
            case "sin": return Math.sin(a); case "cos": return Math.cos(a); case "tan": return Math.tan(a);
            case "abs": return Math.abs(a); case "sqrt": return Math.sqrt(a); case "ceil": return Math.ceil(a);
            case "floor": return Math.floor(a); case "round": return Math.floor(a + .5);
            case "atan2": return Math.atan2(a, number(numbers.get(1)));
            case "hypot": { double sum = 0; for (Object n : numbers) sum += number(n) * number(n); return Math.sqrt(sum); }
            default: throw new IllegalArgumentException("Unsupported geometry math operation " + name);
        }
    }

    static final class VectorNode extends LinkedHashMap<String, Object> {
        List<Object> current = new ArrayList<>(); boolean painted;
        VectorNode() {
            put("x", 0d); put("y", 0d); put("rotation", 0d); put("children", array()); put("draws", array());
            Map<String, Object> pivot = object("x", 0d, "y", 0d);
            pivot.put("set", (Fn) a -> { pivot.put("x", arg(a, 0)); pivot.put("y", a.length > 1 ? a[1] : a[0]); return null; });
            put("pivot", pivot);
            put("position", object("set", (Fn) a -> { put("x", arg(a, 0)); put("y", a.length > 1 ? a[1] : a[0]); return null; }));
            put("addChild", (Fn) a -> { list(get("children")).add(a[0]); return a[0]; });
            for (String shape : Arrays.asList("poly", "rect", "circle", "moveTo", "lineTo")) {
                put(shape, (Fn) a -> { if (painted) { current = new ArrayList<>(); painted = false; }
                    List<Object> command = array(shape.equals("moveTo") ? "move" : shape.equals("lineTo") ? "line" : shape);
                    command.addAll(Arrays.asList(a)); current.add(command); return this; });
            }
            for (String kind : Arrays.asList("fill", "stroke")) put(kind, (Fn) a -> {
                Map<String, Object> draw = object("path", current, "fill", kind.equals("fill"), "holes", array());
                draw.putAll(map(a[0])); list(get("draws")).add(draw); painted = true; return this;
            });
            put("cut", (Fn) a -> { List<Object> draws = list(get("draws")); list(FoliaTemperaGeometryOps.get(draws.get(draws.size() - 1), "holes")).addAll(current); current = new ArrayList<>(); painted = false; return this; });
        }
    }
    static VectorNode node() { return new VectorNode(); }

    static JSONArray generate(String kind, double width, double height, long seed, double flow,
                              String motif, double hatchAngle, int crossCount, long scribbleSeed,
                              boolean showDecor) throws JSONException {
        VectorNode root = node(); int[] count = {0};
        Map<String, Object> palette = new LinkedHashMap<>();
        String[] colors = {"paper", "ink", "blockA", "blockB", "blockC", "accent", "line", "shadow", "tone1", "tone2", "tone3", "tone4"};
        for (int i = 0; i < colors.length; i++) palette.put(colors[i], (double) i + 1);
        Map<String, Object> context = object("kind", kind, "width", width, "height", height, "seed", (double) seed,
                "flowAngle", flow, "bleed", Math.max(width, height) * .17, "showDecor", showDecor,
                "palette", palette, "gradient", null, "pixi", null,
                "decor", object("motif", motif, "hatchAngle", hatchAngle, "crossCount", (double) crossCount, "scribbleSeed", (double) scribbleSeed));
        context.put("add", (Fn) a -> {
            VectorNode item = (VectorNode) a[0]; item.put("motion", a.length > 1 ? a[1] : object());
            item.put("driftPhase", FoliaTemperaMotion.random(seed, count[0]++, 173) * Math.PI * 2);
            list(get(a.length > 2 && a[2] != null ? a[2] : root, "children")).add(item); return null;
        });
        context.put("createGroup", (Fn) a -> { VectorNode group = node(); group.put("rotation", a[0]); group.put("x", a[1]); group.put("y", a[2]); list(root.get("children")).add(group); return group; });
        FoliaTemperaGeometryProgram.compose(context);
        return (JSONArray) json(root.get("children"));
    }
    static Object json(Object value) throws JSONException {
        if (value instanceof Map) {
            JSONObject out = new JSONObject();
            for (Map.Entry<String, Object> entry : map(value).entrySet()) {
                if (entry.getValue() instanceof Fn || value instanceof VectorNode && entry.getKey().equals("position")) continue;
                out.put(entry.getKey(), json(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List) { JSONArray out = new JSONArray(); for (Object item : list(value)) out.put(json(item)); return out; }
        return value == null ? JSONObject.NULL : value;
    }

    static Object fromJson(Object value) throws JSONException {
        if (value instanceof JSONObject) { Map<String, Object> out = new LinkedHashMap<>(); JSONObject input = (JSONObject) value; for (java.util.Iterator<String> keys = input.keys(); keys.hasNext();) { String key = keys.next(); out.put(key, fromJson(input.get(key))); } return out; }
        if (value instanceof JSONArray) { List<Object> out = new ArrayList<>(); JSONArray input = (JSONArray) value; for (int i = 0; i < input.length(); i++) out.add(fromJson(input.get(i))); return out; }
        return value == JSONObject.NULL ? null : value;
    }
}
