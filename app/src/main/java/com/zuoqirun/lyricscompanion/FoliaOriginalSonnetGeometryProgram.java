// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// The bundled finite formula IR is compiled from Folia 9279251's Sonnet geometry sources.
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Native geometry recipe evaluator. Its vocabulary is arithmetic, seeded selections, bounded
 * iteration and vector scene construction; it contains no JS engine, browser or executable code.
 * Recipes run only when a shot/viewport changes. Android's hardware Canvas owns every frame.
 */
final class FoliaOriginalSonnetGeometryProgram {
    // Collision sweeps in the authored collage packer can visit 14 rings × 400 slots
    // at each of seven fit sizes. The budget bounds them without truncating those recipes.
    private static final int MAX_OPERATIONS = 50000000;
    private final JSONObject modules;
    private final Map<String, Scope> loaded = new LinkedHashMap<>();
    private final Scope builtins = new Scope(null);
    private int operations;
    private long randomState;
    private int randomCalls;
    interface TextMeasurer { double width(String text, double size, int weight); }
    private TextMeasurer textMeasurer;

    FoliaOriginalSonnetGeometryProgram(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"folia-sonnet-geometry-ir-1".equals(root.getString("format"))) {
            throw new JSONException("Unsupported Sonnet geometry format");
        }
        modules = root.getJSONObject("modules");
        installBuiltins();
        operations = MAX_OPERATIONS;
        module("sonnetShotMg");
    }

    JSONObject build(String kind, double width, double height, long seed) throws JSONException {
        operations = MAX_OPERATIONS;
        randomState = seed & 0xffffffffL;
        randomCalls = 0;
        Map<String, Object> theme = object("primaryColor", "#000001", "secondaryColor", "#000002");
        Map<String, Object> pixi = pixi();
        Object sceneValue = invoke(module("sonnetShotMg").get("buildSonnetShotMg"),
                Arrays.asList(pixi, kind, theme, width, height, (double) seed, object("values", (Function) a -> new ArrayList<>())));
        Graphic scene = (Graphic) sceneValue;
        mark(scene.get("geoLayer"), "geometry");
        mark(scene.get("fixedGeoLayer"), "fixed");
        mark(scene.get("particleLayer"), "particles");
        markMasks(scene);
        JSONArray nodes = new JSONArray();
        for (Object child : list(scene.get("children"))) nodes.put(serialize((Graphic) child));
        return new JSONObject().put("nodes", nodes).put("randomCalls", randomCalls);
    }

    JSONArray typography(JSONArray lines, String kind, String paragraphKind, double width,
                         double height, double baseSize, TextMeasurer measurer) {
        operations = MAX_OPERATIONS;
        textMeasurer = measurer;
        Map<String, Object> options = object("lines", unwrap(lines), "shotKind", kind,
                "paragraphKind", paragraphKind, "width", width, "height", height,
                "baseFontSize", baseSize, "fontFamily", "native", "fontWeight", null);
        return new JSONArray(list(invoke(module("sonnetTypographyLayout").get("resolveSonnetTypographyLayout"), Collections.singletonList(options))));
    }

    JSONObject textFixedGeometry(long seed, boolean chorus, double fontSize, double layoutWidth) throws JSONException {
        operations = MAX_OPERATIONS;
        Map<String, Object> options = object("seed", (double) seed, "isChorusEffect", chorus,
                "fontSize", fontSize, "layoutWidth", layoutWidth,
                "theme", object("primaryColor", 1.0, "secondaryColor", 2.0, "accentColor", 3.0));
        Graphic graphic = (Graphic) invoke(module("sonnetTextFixedGeo").get("buildSonnetTextFixedGeo"), Arrays.asList(pixi(), options));
        return serialize(graphic);
    }

    private static Object unwrap(Object json) {
        if (json == JSONObject.NULL) return null;
        if (json instanceof JSONArray) {
            List<Object> items = new ArrayList<>();
            JSONArray array = (JSONArray) json;
            for (int i = 0; i < array.length(); i++) items.add(unwrap(array.opt(i)));
            return items;
        }
        if (json instanceof JSONObject) {
            Map<String, Object> items = new LinkedHashMap<>();
            JSONObject object = (JSONObject) json;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) { String key = keys.next(); items.put(key, unwrap(object.opt(key))); }
            return items;
        }
        return json;
    }

    private static void mark(Object node, String motion) {
        if (node instanceof Graphic) ((Graphic) node).put("motion", motion);
    }

    private static void markMasks(Graphic node) {
        if (node.get("mask") instanceof Graphic) ((Graphic) node.get("mask")).put("isMask", true);
        for (Object child : list(node.get("children"))) markMasks((Graphic) child);
    }

    private JSONObject serialize(Graphic node) throws JSONException {
        JSONObject json = new JSONObject();
        for (String key : new String[]{"x", "y", "rotation", "alpha", "motion", "animated", "isMask"}) {
            Object value = node.get(key);
            if (value != null) json.put(key, value);
        }
        Map<?, ?> scale = (Map<?, ?>) node.get("scale"), pivot = (Map<?, ?>) node.get("pivot");
        json.put("scaleX", scale.get("x")).put("scaleY", scale.get("y"));
        json.put("pivotX", pivot.get("x")).put("pivotY", pivot.get("y"));
        json.put("draws", new JSONArray(node.draws));
        JSONArray children = new JSONArray();
        for (Object child : list(node.get("children"))) children.put(serialize((Graphic) child));
        json.put("children", children);
        if (node.get("mask") instanceof Graphic) json.put("mask", serialize((Graphic) node.get("mask")));
        return json;
    }

    private Scope module(String id) {
        Scope present = loaded.get(id);
        if (present != null) return present;
        JSONObject description = modules.optJSONObject(id);
        if (description == null) throw new IllegalStateException("Missing geometry module " + id);
        Scope scope = new Scope(builtins);
        loaded.put(id, scope);
        JSONArray imports = description.optJSONArray("imports");
        for (int i = 0; i < imports.length(); i++) {
            JSONArray entry = imports.optJSONArray(i);
            scope.values.put(entry.optString(0), "@".equals(entry.optString(1))
                    ? nativeImport(entry.optString(2)) : module(entry.optString(1)).get(entry.optString(2)));
        }
        executeBlock(description.optJSONArray("code"), scope);
        return scope;
    }

    private Function nativeImport(String name) {
        if ("normalizeFontWeight".equals(name)) return a -> argument(a, 0) instanceof Number ? argument(a, 0) : null;
        if ("measureText".equals(name)) return a -> {
            String specification = string(argument(a, 1));
            int weight = Integer.parseInt(specification.substring(0, specification.indexOf(' ')));
            return textMeasurer.width(string(argument(a, 0)), number(argument(a, 2)), weight);
        };
        if ("AnimatedGraphics".equals(name)) return a -> new Graphic(true);
        throw new IllegalStateException("Unknown native geometry import " + name);
    }

    private void tick() {
        if (--operations <= 0) throw new IllegalStateException("Sonnet geometry operation limit");
    }

    private Object value(Object input, Scope scope) {
        tick();
        if (input == null || input == JSONObject.NULL) return null;
        if (!(input instanceof JSONArray)) return input;
        JSONArray expr = (JSONArray) input;
        String tag = expr.optString(0);
        switch (tag) {
            case "str": return expr.optString(1);
            case "regex": return Pattern.compile(expr.optString(1));
            case "get": return scope.get(expr.optString(1));
            case "prop": return property(value(expr.opt(1), scope), value(expr.opt(2), scope));
            case "fn": {
                JSONArray params = expr.optJSONArray(1);
                Object body = expr.opt(2);
                return (Function) arguments -> {
                    Scope local = new Scope(scope);
                    for (int i = 0; i < params.length(); i++) {
                        JSONArray param = params.optJSONArray(i);
                        Object argument = i < arguments.size() ? arguments.get(i) : null;
                        if (argument == null && !param.isNull(1)) argument = value(param.opt(1), local);
                        bind(param.opt(0), argument, local);
                    }
                    try { execute(body, local); } catch (Return returned) { return returned.value; }
                    return null;
                };
            }
            case "call": {
                Object function = value(expr.opt(1), scope);
                List<Object> arguments = new ArrayList<>();
                for (int i = 2; i < expr.length(); i++) {
                    Object arg = expr.opt(i);
                    if (arg instanceof JSONArray && "spread".equals(((JSONArray) arg).optString(0))) arguments.addAll(list(value(((JSONArray) arg).opt(1), scope)));
                    else arguments.add(value(arg, scope));
                }
                return invoke(function, arguments);
            }
            case "list": {
                List<Object> output = new ArrayList<>();
                for (int i = 1; i < expr.length(); i++) {
                    Object item = expr.opt(i);
                    if (item instanceof JSONArray && "spread".equals(((JSONArray) item).optString(0))) output.addAll(list(value(((JSONArray) item).opt(1), scope)));
                    else output.add(value(item, scope));
                }
                return output;
            }
            case "obj": {
                Map<String, Object> output = new LinkedHashMap<>();
                for (int i = 1; i < expr.length(); i++) {
                    JSONArray item = expr.optJSONArray(i);
                    Object itemValue = value(item.opt(1), scope);
                    if ("...".equals(item.optString(0))) output.putAll(map(itemValue));
                    else output.put(item.optString(0), itemValue);
                }
                return output;
            }
            case "if": return value(expr.opt(truth(value(expr.opt(1), scope)) ? 2 : 3), scope);
            case "op": {
                String operator = expr.optString(1);
                Object left = value(expr.opt(2), scope);
                if ("&&".equals(operator)) return truth(left) ? value(expr.opt(3), scope) : left;
                if ("||".equals(operator)) return truth(left) ? left : value(expr.opt(3), scope);
                if ("??".equals(operator)) return left == null ? value(expr.opt(3), scope) : left;
                if ("=".equals(operator)) return assign(expr.opt(2), value(expr.opt(3), scope), scope);
                Object right = value(expr.opt(3), scope);
                if (Arrays.asList("+=", "-=", "*=", "/=", "^=").contains(operator)) return assign(expr.opt(2), binary(operator.substring(0, 1), left, right), scope);
                return binary(operator, left, right);
            }
            case "unary": {
                String operator = expr.optString(1);
                Object operand = value(expr.opt(2), scope);
                if ("!".equals(operator)) return !truth(operand);
                if ("-".equals(operator)) return -number(operand);
                if ("+".equals(operator)) return number(operand);
                if ("~".equals(operator)) return (double) ~int32(operand);
                if ("typeof".equals(operator)) return operand instanceof Number ? "number" : operand instanceof String ? "string" : "object";
                double next = number(operand) + ("++".equals(operator) ? 1 : -1);
                assign(expr.opt(2), next, scope);
                return expr.optBoolean(3) ? operand : next;
            }
            case "template": {
                StringBuilder output = new StringBuilder(expr.optString(1));
                for (int i = 2; i < expr.length(); i += 2) output.append(string(value(expr.opt(i), scope))).append(expr.optString(i + 1));
                return output.toString();
            }
            default: throw new IllegalStateException("Unknown geometry expression " + tag);
        }
    }

    private Object invoke(Object function, List<Object> arguments) {
        tick();
        if (!(function instanceof Function)) throw new IllegalStateException("Invalid geometry function " + function);
        return ((Function) function).call(arguments);
    }

    private void executeBlock(JSONArray code, Scope scope) {
        for (int i = 1; i < code.length(); i++) execute(code.opt(i), scope);
    }

    private void execute(Object input, Scope scope) {
        if (input == null || input == JSONObject.NULL) return;
        tick();
        JSONArray code = (JSONArray) input;
        switch (code.optString(0)) {
            case "block": executeBlock(code, new Scope(scope)); return;
            case "let":
                for (int i = 1; i < code.length(); i++) {
                    JSONArray declaration = code.optJSONArray(i);
                    bind(declaration.opt(0), value(declaration.opt(1), scope), scope);
                }
                return;
            case "do": value(code.opt(1), scope); return;
            case "if": execute(code.opt(truth(value(code.opt(1), scope)) ? 2 : 3), scope); return;
            case "return": throw new Return(value(code.opt(1), scope));
            case "break": throw Break.INSTANCE;
            case "continue": throw Continue.INSTANCE;
            case "throw": throw new IllegalStateException(string(value(code.opt(1), scope)));
            case "for": {
                Scope loop = new Scope(scope);
                execute(code.opt(1), loop);
                while (code.isNull(2) || truth(value(code.opt(2), loop))) {
                    try { execute(code.opt(4), loop); } catch (Break end) { break; } catch (Continue next) { /* increment below */ }
                    value(code.opt(3), loop);
                }
                return;
            }
            case "each": {
                for (Object item : list(value(code.opt(2), scope))) {
                    Scope iteration = new Scope(scope);
                    bind(code.opt(1), item, iteration);
                    try { execute(code.opt(3), iteration); } catch (Break end) { break; } catch (Continue next) { /* next item */ }
                }
                return;
            }
            case "switch": {
                Object selected = value(code.opt(1), scope);
                int start = -1, fallback = -1;
                for (int i = 2; i < code.length(); i++) {
                    JSONArray branch = code.optJSONArray(i);
                    if (branch.isNull(0)) fallback = i;
                    else if (equal(selected, value(branch.opt(0), scope))) { start = i; break; }
                }
                if (start < 0) start = fallback;
                if (start >= 0) try {
                    for (int i = start; i < code.length(); i++) execute(code.optJSONArray(i).opt(1), scope);
                } catch (Break end) { /* end selection */ }
                return;
            }
            default: throw new IllegalStateException("Unknown geometry statement " + code.optString(0));
        }
    }

    private void bind(Object pattern, Object item, Scope scope) {
        if (pattern instanceof String) { scope.values.put((String) pattern, item); return; }
        if (!(pattern instanceof JSONArray)) return;
        JSONArray structured = (JSONArray) pattern;
        boolean array = "array".equals(structured.optString(0));
        for (int i = 1; i < structured.length(); i++) {
            if (array) bind(structured.opt(i), property(item, (double) (i - 1)), scope);
            else {
                JSONArray pair = structured.optJSONArray(i);
                bind(pair.opt(1), property(item, pair.optString(0)), scope);
            }
        }
    }

    private Object assign(Object target, Object next, Scope scope) {
        JSONArray ref = (JSONArray) target;
        if ("list".equals(ref.optString(0))) {
            List<Object> values = list(next);
            for (int i = 1; i < ref.length(); i++) assign(ref.opt(i), i - 1 < values.size() ? values.get(i - 1) : null, scope);
        } else if ("get".equals(ref.optString(0))) scope.set(ref.optString(1), next);
        else if ("prop".equals(ref.optString(0))) {
            Object owner = value(ref.opt(1), scope), key = value(ref.opt(2), scope);
            if (owner instanceof Map) map(owner).put(string(key), next);
            else if (owner instanceof List) {
                List<Object> list = list(owner);
                if ("length".equals(key)) {
                    int size = (int) number(next);
                    while (list.size() > size) list.remove(list.size() - 1);
                    while (list.size() < size) list.add(null);
                    return next;
                }
                int index = (int) number(key);
                while (list.size() <= index) list.add(null);
                list.set(index, next);
            } else throw new IllegalStateException("Invalid geometry assignment");
        } else throw new IllegalStateException("Invalid geometry reference");
        return next;
    }

    private Object property(Object owner, Object key) {
        String name = string(key);
        if (owner instanceof Graphic) {
            Graphic graphic = (Graphic) owner;
            if ("display".equals(name)) return graphic;
            if (graphic.containsKey(name)) return graphic.get(name);
            return (Function) arguments -> graphic.command(name, arguments);
        }
        if (owner instanceof Map) return map(owner).get(name);
        if (owner instanceof List) {
            List<Object> values = list(owner);
            switch (name) {
                case "length": return (double) values.size();
                case "entries": return (Function) a -> {
                    List<Object> entries = new ArrayList<>();
                    for (int i = 0; i < values.size(); i++) entries.add(Arrays.asList((double) i, values.get(i)));
                    return entries;
                };
                case "push": return (Function) a -> { values.addAll(a); return (double) values.size(); };
                case "unshift": return (Function) a -> { values.addAll(0, a); return (double) values.size(); };
                case "join": return (Function) a -> {
                    StringBuilder output = new StringBuilder();
                    for (int i = 0; i < values.size(); i++) { if (i > 0) output.append(string(argument(a, 0))); if (values.get(i) != null) output.append(string(values.get(i))); }
                    return output.toString();
                };
                case "flat": return (Function) a -> {
                    List<Object> output = new ArrayList<>();
                    for (Object item : values) { if (item instanceof List) output.addAll(list(item)); else output.add(item); }
                    return output;
                };
                case "slice": return (Function) a -> {
                    int start = (int) number(argument(a, 0)), end = a.size() > 1 ? (int) number(a.get(1)) : values.size();
                    if (start < 0) start = values.size() + start;
                    if (end < 0) end = values.size() + end;
                    start = Math.max(0, Math.min(values.size(), start)); end = Math.max(start, Math.min(values.size(), end));
                    return new ArrayList<>(values.subList(start, end));
                };
                case "splice": return (Function) a -> {
                    int start = (int) number(argument(a, 0)), count = (int) number(argument(a, 1));
                    if (start < 0) start = values.size() + start;
                    start = Math.max(0, Math.min(values.size(), start));
                    List<Object> removed = new ArrayList<>();
                    while (count-- > 0 && start < values.size()) removed.add(values.remove(start));
                    if (a.size() > 2) values.addAll(start, a.subList(2, a.size()));
                    return removed;
                };
                case "sort": return (Function) a -> {
                    Collections.sort(values, (first, second) -> (int) Math.signum(number(invoke(argument(a, 0), Arrays.asList(first, second)))));
                    return values;
                };
                case "filter": return (Function) a -> {
                    List<Object> output = new ArrayList<>();
                    for (int i = 0; i < values.size(); i++) if (truth(invoke(argument(a, 0), Arrays.asList(values.get(i), (double) i, values)))) output.add(values.get(i));
                    return output;
                };
                case "every": return (Function) a -> {
                    for (int i = 0; i < values.size(); i++) if (!truth(invoke(argument(a, 0), Arrays.asList(values.get(i), (double) i, values)))) return false;
                    return true;
                };
                case "findIndex": return (Function) a -> {
                    for (int i = 0; i < values.size(); i++) if (truth(invoke(argument(a, 0), Arrays.asList(values.get(i), (double) i, values)))) return (double) i;
                    return -1.0;
                };
                case "reduce": return (Function) a -> {
                    Object accumulator = a.size() > 1 ? a.get(1) : values.get(0);
                    for (int i = a.size() > 1 ? 0 : 1; i < values.size(); i++) accumulator = invoke(a.get(0), Arrays.asList(accumulator, values.get(i), (double) i, values));
                    return accumulator;
                };
                case "includes": return (Function) a -> { for (Object v : values) if (equal(v, argument(a, 0))) return true; return false; };
                case "forEach": return (Function) a -> {
                    for (int i = 0; i < values.size(); i++) invoke(argument(a, 0), Arrays.asList(values.get(i), (double) i, values));
                    return null;
                };
                case "map": return (Function) a -> {
                    List<Object> output = new ArrayList<>();
                    for (int i = 0; i < values.size(); i++) output.add(invoke(argument(a, 0), Arrays.asList(values.get(i), (double) i, values)));
                    return output;
                };
                default:
                    try { int index = Integer.parseInt(name); return index >= 0 && index < values.size() ? values.get(index) : null; }
                    catch (NumberFormatException error) { throw new IllegalStateException("Unknown geometry list property " + name); }
            }
        }
        if (owner instanceof String) {
            String text = (String) owner;
            if ("length".equals(name)) return (double) text.length();
            if ("charCodeAt".equals(name)) return (Function) a -> (double) text.charAt((int) number(argument(a, 0)));
            if ("trim".equals(name)) return (Function) a -> text.trim();
        }
        if (owner instanceof Pattern && "test".equals(name)) return (Function) a -> ((Pattern) owner).matcher(string(argument(a, 0))).find();
        if (owner == null) return null;
        throw new IllegalStateException("Unknown geometry property " + name + " on " + owner.getClass().getSimpleName() + " " + owner);
    }

    private void installBuiltins() {
        builtins.values.put("undefined", null);
        builtins.values.put("Infinity", Double.POSITIVE_INFINITY);
        Map<String, Object> math = object("PI", Math.PI);
        for (String name : new String[]{"min", "max", "sin", "cos", "sqrt", "abs", "pow", "hypot", "floor", "ceil", "trunc", "atan2", "imul", "round"}) {
            math.put(name, (Function) a -> math(name, a));
        }
        builtins.values.put("Math", math);
        builtins.values.put("Array", object("from", (Function) a -> {
            Object source = argument(a, 0);
            List<Object> input;
            if (source instanceof String) {
                input = new ArrayList<>();
                String text = (String) source;
                for (int index = 0; index < text.length();) { int cp = text.codePointAt(index); input.add(new String(Character.toChars(cp))); index += Character.charCount(cp); }
            } else input = source instanceof List ? list(source) : new ArrayList<>(Collections.nCopies((int) number(property(source, "length")), null));
            if (a.size() < 2) return input;
            List<Object> output = new ArrayList<>();
            for (int i = 0; i < input.size(); i++) output.add(invoke(a.get(1), Arrays.asList(input.get(i), (double) i)));
            return output;
        }));
        builtins.values.put("Object", object("entries", (Function) a -> {
            List<Object> entries = new ArrayList<>();
            for (Map.Entry<String, Object> e : map(argument(a, 0)).entrySet()) entries.add(Arrays.asList(e.getKey(), e.getValue()));
            return entries;
        }));
        builtins.values.put("Error", (Function) a -> string(argument(a, 0)));
    }

    private Map<String, Object> pixi() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("setValue", (Function) a -> {
            shared.put("value", "#000002".equals(argument(a, 0)) ? 2.0 : 1.0);
            return shared;
        });
        shared.put("toNumber", (Function) a -> shared.get("value"));
        return object("Container", (Function) a -> new Graphic(false), "Graphics", (Function) a -> new Graphic(false),
                "Sprite", (Function) a -> new Graphic(false), "Color", object("shared", shared));
    }

    private static Object math(String name, List<Object> a) {
        double x = number(argument(a, 0)), y = number(argument(a, 1));
        switch (name) {
            case "min": { double v = Double.POSITIVE_INFINITY; for (Object n : a) v = Math.min(v, number(n)); return v; }
            case "max": { double v = Double.NEGATIVE_INFINITY; for (Object n : a) v = Math.max(v, number(n)); return v; }
            case "sin": return Math.sin(x);
            case "cos": return Math.cos(x);
            case "sqrt": return Math.sqrt(x);
            case "abs": return Math.abs(x);
            case "pow": return Math.pow(x, y);
            case "hypot": { double v = 0; for (Object n : a) v = Math.hypot(v, number(n)); return v; }
            case "floor": return Math.floor(x);
            case "ceil": return Math.ceil(x);
            case "round": return Math.floor(x + 0.5);
            case "trunc": return x < 0 ? Math.ceil(x) : Math.floor(x);
            case "atan2": return Math.atan2(x, y);
            case "imul": return (double) (int32(argument(a, 0)) * int32(argument(a, 1)));
            default: throw new IllegalStateException("Unknown geometry math " + name);
        }
    }

    private static Object binary(String op, Object left, Object right) {
        double a = number(left), b = number(right);
        switch (op) {
            case "+": return left instanceof String || right instanceof String ? string(left) + string(right) : a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/": return a / b;
            case "%": return a % b;
            case "<": return a < b;
            case ">": return a > b;
            case "<=": return a <= b;
            case ">=": return a >= b;
            case "===": return equal(left, right);
            case "!==": return !equal(left, right);
            case "^": return (double) (int32(left) ^ int32(right));
            case "&": return (double) (int32(left) & int32(right));
            case ">>": return (double) (int32(left) >> (int32(right) & 31));
            case "<<": return (double) (int32(left) << (int32(right) & 31));
            case "|": return (double) (int32(left) | int32(right));
            case ">>>": return (double) ((int32(left) >>> (int32(right) & 31)) & 0xffffffffL);
            default: throw new IllegalStateException("Unknown geometry arithmetic " + op);
        }
    }

    double nextRandom() { return random(); }

    private double random() {
        randomCalls++;
        randomState = (randomState * 1664525L + 1013904223L) & 0xffffffffL;
        return randomState / 4294967296.0;
    }

    private final class Graphic extends LinkedHashMap<String, Object> {
        final boolean animated;
        final List<JSONObject> draws = new ArrayList<>();
        List<Map<String, Object>> current = new ArrayList<>();
        double length, lastX, lastY;
        boolean painted;

        Graphic(boolean animated) {
            this.animated = animated;
            put("animated", animated);
            put("x", 0.0); put("y", 0.0); put("rotation", 0.0); put("alpha", 1.0);
            put("children", new ArrayList<>());
            Map<String, Object> scale = point(1), pivot = point(0);
            put("scale", scale); put("pivot", pivot);
            put("position", object("set", (Function) a -> {
                put("x", argument(a, 0)); put("y", a.size() > 1 ? a.get(1) : a.get(0)); return null;
            }));
            put("anchor", point(0));
        }

        Map<String, Object> point(double initial) {
            Map<String, Object> point = object("x", initial, "y", initial);
            point.put("set", (Function) a -> { point.put("x", argument(a, 0)); point.put("y", a.size() > 1 ? a.get(1) : a.get(0)); return null; });
            return point;
        }

        Object command(String name, List<Object> a) {
            double x = number(argument(a, 0)), y = number(argument(a, 1));
            if ("addChild".equals(name)) { list(get("children")).addAll(a); return argument(a, 0); }
            if ("fill".equals(name) || "stroke".equals(name)) {
                if (!current.isEmpty()) try {
                    draws.add(new JSONObject().put("type", name).put("path", new JSONArray(current))
                            .put("length", length).put("options", new JSONObject(map(argument(a, 0)))));
                    if (animated) { current = new ArrayList<>(); length = 0; }
                    else painted = true;
                } catch (JSONException error) { throw new IllegalStateException(error); }
                return this;
            }
            if (painted) { current = new ArrayList<>(); length = 0; painted = false; }
            Map<String, Object> step;
            switch (name) {
                case "moveTo": step = object("type", name, "x", x, "y", y); lastX = x; lastY = y; break;
                case "lineTo": {
                    double len = Math.hypot(x - lastX, y - lastY);
                    step = object("type", name, "x", x, "y", y, "len", len, "lastX", lastX, "lastY", lastY);
                    length += len; lastX = x; lastY = y; break;
                }
                case "quadraticCurveTo": {
                    double tx = number(argument(a, 2)), ty = number(argument(a, 3));
                    double len = Math.hypot(x - lastX, y - lastY) + Math.hypot(tx - x, ty - y);
                    step = object("type", name, "cx", x, "cy", y, "tx", tx, "ty", ty, "len", len, "lastX", lastX, "lastY", lastY);
                    length += len; lastX = tx; lastY = ty; break;
                }
                case "bezierCurveTo": {
                    double c2x = number(argument(a, 2)), c2y = number(argument(a, 3)), tx = number(argument(a, 4)), ty = number(argument(a, 5));
                    double len = Math.hypot(x - lastX, y - lastY) + Math.hypot(c2x - x, c2y - y) + Math.hypot(tx - c2x, ty - c2y);
                    step = object("type", name, "c1x", x, "c1y", y, "c2x", c2x, "c2y", c2y, "tx", tx, "ty", ty, "len", len, "lastX", lastX, "lastY", lastY);
                    length += len; lastX = tx; lastY = ty; break;
                }
                case "arc": {
                    double r = number(argument(a, 2)), start = number(argument(a, 3)), end = number(argument(a, 4));
                    boolean anticlockwise = truth(argument(a, 5));
                    double diff = end - start;
                    if (anticlockwise && diff > 0) diff -= Math.PI * 2;
                    else if (!anticlockwise && diff < 0) diff += Math.PI * 2;
                    double len = Math.abs(diff) * r;
                    step = object("type", name, "cx", x, "cy", y, "r", r, "start", start, "end", end, "anticlockwise", anticlockwise, "len", len, "diff", diff);
                    length += len; lastX = x + Math.cos(end) * r; lastY = y + Math.sin(end) * r; break;
                }
                case "circle": {
                    double r = number(argument(a, 2));
                    if (!animated) { step = object("type", name, "x", x, "y", y, "r", r); break; }
                    double start = random() * Math.PI * 2, diff = random() > 0.5 ? -Math.PI * 2 : Math.PI * 2;
                    command("moveTo", Arrays.asList(x + Math.cos(start) * r, y + Math.sin(start) * r));
                    double len = Math.PI * 2 * r;
                    step = object("type", "arc", "cx", x, "cy", y, "r", r, "start", start, "end", start + diff, "anticlockwise", diff < 0, "len", len, "diff", diff);
                    length += len; lastX = x + Math.cos(start + diff) * r; lastY = y + Math.sin(start + diff) * r; break;
                }
                case "rect": {
                    double w = number(argument(a, 2)), h = number(argument(a, 3));
                    if (!animated) { step = object("type", name, "x", x, "y", y, "w", w, "h", h); break; }
                    current.add(object("type", "rect_hint", "x", x, "y", y, "w", w, "h", h));
                    command("moveTo", Arrays.asList(x, y)); command("lineTo", Arrays.asList(x + w, y));
                    command("lineTo", Arrays.asList(x + w, y + h)); command("lineTo", Arrays.asList(x, y + h));
                    command("lineTo", Arrays.asList(x, y)); return this;
                }
                default: throw new IllegalStateException("Unknown geometry vector operation " + name);
            }
            current.add(step);
            return this;
        }
    }

    private static Object argument(List<Object> a, int index) { return index < a.size() ? a.get(index) : null; }
    private static boolean truth(Object value) { return value != null && !Boolean.FALSE.equals(value) && (!(value instanceof Number) || number(value) != 0 && !Double.isNaN(number(value))) && (!(value instanceof String) || !((String) value).isEmpty()); }
    private static boolean equal(Object a, Object b) { return a instanceof Number && b instanceof Number ? number(a) == number(b) : a == null ? b == null : a.equals(b); }
    private static double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : Boolean.TRUE.equals(value) ? 1 : value == null || Boolean.FALSE.equals(value) ? 0 : Double.NaN; }
    private static int int32(Object value) { return (int) (long) number(value); }
    private static String string(Object value) { return value == null ? "null" : value instanceof Number && number(value) == Math.rint(number(value)) ? Long.toString((long) number(value)) : String.valueOf(value); }
    @SuppressWarnings("unchecked") private static List<Object> list(Object value) { return (List<Object>) value; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    private static Map<String, Object> object(Object... pairs) { Map<String, Object> output = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) output.put((String) pairs[i], pairs[i + 1]); return output; }

    private interface Function { Object call(List<Object> arguments); }
    private static final class Scope {
        final Scope parent;
        final Map<String, Object> values = new LinkedHashMap<>();
        Scope(Scope parent) { this.parent = parent; }
        Object get(String name) { return values.containsKey(name) ? values.get(name) : parent == null ? null : parent.get(name); }
        void set(String name, Object value) { if (values.containsKey(name) || parent == null) values.put(name, value); else parent.set(name, value); }
    }
    private static class Flow extends RuntimeException { Flow() { super(null, null, false, false); } }
    private static final class Return extends Flow { final Object value; Return(Object value) { this.value = value; } }
    private static final class Break extends Flow { static final Break INSTANCE = new Break(); }
    private static final class Continue extends Flow { static final Continue INSTANCE = new Continue(); }
}
