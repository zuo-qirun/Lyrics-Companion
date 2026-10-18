// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native evaluator for the pure arithmetic recipes in lumiere/{rigs,lineart} at 9279251.
// Attribution and the corresponding source: third_party/folia/NOTICE.md.
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

/**
 * Executes upstream's finite geometry recipes once when a shot/viewport is prepared.
 * The bundled program contains only numbers, arrays, object construction and arithmetic;
 * it has no browser, JavaScript runtime, file access, network access or drawing capability.
 * Generated paths are subsequently drawn by the hardware Canvas without evaluating recipes.
 */
final class FoliaLumiereGeometry {
    private FoliaLumiereGeometry() { }
    private interface Function { Object call(List<Object> arguments); }
    private static final Object MISSING = new Object();

    private static final class Environment {
        final Environment parent;
        final Map<String, Object> values = new LinkedHashMap<>();
        Environment(Environment parent) { this.parent = parent; }
        Object get(String name) {
            if (values.containsKey(name)) return values.get(name);
            if (parent != null) return parent.get(name);
            if ("undefined".equals(name)) return MISSING;
            if ("LUMIERE_NEUTRAL_OFFSET".equals(name)) return 0.0;
            throw new IllegalStateException("Unknown Lumiere recipe variable: " + name);
        }
        void assign(String name, Object value) {
            if (values.containsKey(name) || parent == null) values.put(name, value);
            else parent.assign(name, value);
        }
    }

    private static final class Signal extends RuntimeException {
        final String kind;
        final Object value;
        Signal(String kind, Object value) { this.kind = kind; this.value = value; }
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class Recipe implements Function {
        final Object[] parameters;
        final Object body;
        final Environment closure;
        Recipe(Object[] parameters, Object body, Environment closure) {
            this.parameters = parameters; this.body = body; this.closure = closure;
        }
        @Override public Object call(List<Object> arguments) {
            Environment local = new Environment(closure);
            for (int i = 0; i < parameters.length; i++) {
                Object parameter = parameters[i];
                if (parameter instanceof Object[] && "rest".equals(((Object[]) parameter)[0])) {
                    bind(((Object[]) parameter)[1], new ArrayList<>(arguments.subList(Math.min(i, arguments.size()), arguments.size())), local);
                    break;
                }
                bind(parameter, i < arguments.size() ? arguments.get(i) : MISSING, local);
            }
            try { evaluate(body, local); }
            catch (Signal signal) { if ("r".equals(signal.kind)) return signal.value; throw signal; }
            return MISSING;
        }
    }

    private static final class Holder {
        static final List<Object> PROFILES = load();
    }

    static JSONObject build(int index, double aspect, String sceneSeed, int shotIndex) {
        return build(index, aspect, sceneSeed + ":" + shotIndex + ":" + FoliaLumiereProfiles.KINDS[index]);
    }

    /** Prefix is the exact `${options.seed}:${index}:${shot.profile.kind}` from upstream scene.ts. */
    static JSONObject build(int index, double aspect, String prefix) {
        @SuppressWarnings("unchecked") Map<String, Object> profile = (Map<String, Object>) Holder.PROFILES.get(index);
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : profile.entrySet()) {
            if (!(entry.getValue() instanceof Function)) output.put(entry.getKey(), entry.getValue());
        }
        output.put("rig", invoke(profile.get("light"), Collections.singletonList(context(aspect, prefix + ":light"))));
        output.put("art", invoke(profile.get("lineArt"), Collections.singletonList(context(aspect, prefix + ":art"))));
        return (JSONObject) json(output);
    }

    private static Object json(Object value) {
        if (value instanceof Map) {
            JSONObject result = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getValue() == MISSING || entry.getValue() instanceof Function) continue;
                try { result.put(String.valueOf(entry.getKey()), json(entry.getValue())); }
                catch (JSONException error) { throw new IllegalStateException(error); }
            }
            return result;
        }
        if (value instanceof List) {
            JSONArray result = new JSONArray();
            for (Object item : (List<?>) value) result.put(item == MISSING ? JSONObject.NULL : json(item));
            return result;
        }
        return value == null ? JSONObject.NULL : value;
    }

    private static Map<String, Object> context(double aspect, String seed) {
        Map<String, Object> context = new LinkedHashMap<>();
        FoliaLumiereMath.Random random = new FoliaLumiereMath.Random(seed);
        context.put("aspect", aspect);
        context.put("random", (Function) arguments -> random.next());
        return context;
    }

    private static List<Object> load() {
        try {
            JSONObject source = new JSONObject(FoliaLumiereGeometryProgram.source());
            JSONArray modules = source.getJSONArray("modules");
            List<Environment> environments = new ArrayList<>();
            Environment standard = standard();
            for (int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                Environment local = new Environment(standard);
                JSONArray imports = module.getJSONArray("imports");
                for (int j = 0; j < imports.length(); j++) {
                    JSONArray binding = imports.getJSONArray(j);
                    local.values.put(binding.getString(0), environments.get(binding.getInt(1)).get(binding.getString(2)));
                }
                Object[] code = (Object[]) decode(module.getJSONArray("code"));
                for (Object instruction : code) evaluate(instruction, local);
                environments.add(local);
            }
            @SuppressWarnings("unchecked") List<Object> profiles = (List<Object>) environments.get(source.getInt("entry")).get("LUMIERE_PROFILES");
            return profiles;
        } catch (JSONException error) { throw new IllegalStateException("Invalid bundled Lumiere geometry program", error); }
    }

    private static Object decode(Object json) throws JSONException {
        if (json == JSONObject.NULL) return null;
        if (json instanceof JSONArray) {
            JSONArray source = (JSONArray) json;
            Object[] output = new Object[source.length()];
            for (int i = 0; i < output.length; i++) output[i] = decode(source.get(i));
            return output;
        }
        return json;
    }

    private static Object evaluate(Object instruction, Environment environment) {
        if (instruction == null) return MISSING;
        Object[] n = (Object[]) instruction;
        String op = (String) n[0];
        switch (op) {
            case "l": return n[1];
            case "v": return environment.get((String) n[1]);
            case "g": return property(evaluate(n[1], environment), evaluate(n[2], environment));
            case "a": return arguments(n, 1, environment);
            case "o": {
                Map<String, Object> result = new LinkedHashMap<>();
                for (int i = 1; i < n.length; i++) {
                    Object[] item = (Object[]) n[i];
                    if ("s".equals(item[0])) {
                        Object value = evaluate(item[1], environment);
                        if (value instanceof Map) {
                            @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) value;
                            result.putAll(values);
                        }
                    } else result.put(string(evaluate(item[1], environment)), evaluate(item[2], environment));
                }
                return result;
            }
            case "c": return invoke(evaluate(n[1], environment), arguments(n, 2, environment));
            case "f": return new Recipe((Object[]) n[1], n[2], environment);
            case "t": return evaluate(truth(evaluate(n[1], environment)) ? n[2] : n[3], environment);
            case "b": {
                Object a = evaluate(n[2], environment);
                if ("&&".equals(n[1]) && !truth(a) || "||".equals(n[1]) && truth(a)
                        || "??".equals(n[1]) && a != null && a != MISSING) return a;
                return binary((String) n[1], a, evaluate(n[3], environment));
            }
            case "u": {
                Object value = evaluate(n[2], environment);
                switch ((String) n[1]) {
                    case "-": return -number(value);
                    case "+": return number(value);
                    case "!": return !truth(value);
                    case "~": return ~(int) number(value);
                    default: throw new IllegalStateException("Unknown recipe unary operator " + n[1]);
                }
            }
            case "=": {
                Object value = evaluate(n[3], environment);
                if (!"=".equals(n[1])) value = binary(((String) n[1]).substring(0, ((String) n[1]).length() - 1), evaluate(n[2], environment), value);
                assign((Object[]) n[2], value, environment);
                return value;
            }
            case "++": {
                Object previous = evaluate(n[2], environment);
                double next = number(previous) + number(n[1]);
                assign((Object[]) n[2], next, environment);
                return Boolean.TRUE.equals(n[3]) ? next : previous;
            }
            case "d": bind(n[1], evaluate(n[2], environment), environment); return MISSING;
            case "q": {
                Object result = MISSING;
                for (int i = 1; i < n.length; i++) result = evaluate(n[i], environment);
                return result;
            }
            case "r": throw new Signal("r", evaluate(n[1], environment));
            case "if": return evaluate(truth(evaluate(n[1], environment)) ? n[2] : n[3], environment);
            case "for": {
                Environment loop = new Environment(environment);
                evaluate(n[1], loop);
                while (n[2] == null || truth(evaluate(n[2], loop))) {
                    try { evaluate(n[4], new Environment(loop)); }
                    catch (Signal signal) { if ("break".equals(signal.kind)) break; if (!"continue".equals(signal.kind)) throw signal; }
                    evaluate(n[3], loop);
                }
                return MISSING;
            }
            case "each": {
                for (Object value : list(evaluate(n[2], environment))) {
                    Environment loop = new Environment(environment); bind(n[1], value, loop);
                    try { evaluate(n[3], loop); }
                    catch (Signal signal) { if ("break".equals(signal.kind)) break; if (!"continue".equals(signal.kind)) throw signal; }
                }
                return MISSING;
            }
            case "break": case "continue": throw new Signal(op, MISSING);
            default: throw new IllegalStateException("Unknown native geometry opcode: " + op);
        }
    }

    private static List<Object> arguments(Object[] values, int first, Environment environment) {
        List<Object> result = new ArrayList<>();
        for (int i = first; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Object[] && "s".equals(((Object[]) value)[0])) result.addAll(list(evaluate(((Object[]) value)[1], environment)));
            else result.add(evaluate(value, environment));
        }
        return result;
    }

    private static void bind(Object binding, Object value, Environment environment) {
        if (binding == null) return;
        if (binding instanceof String) { environment.values.put((String) binding, value); return; }
        Object[] pattern = (Object[]) binding;
        switch ((String) pattern[0]) {
            case "default": bind(pattern[1], value == MISSING ? evaluate(pattern[2], environment) : value, environment); break;
            case "object":
                for (int i = 1; i < pattern.length; i++) {
                    Object[] entry = (Object[]) pattern[i];
                    bind(entry[1], property(value, entry[0]), environment);
                }
                break;
            case "array":
                for (int i = 1; i < pattern.length; i++) bind(pattern[i], property(value, (double) (i - 1)), environment);
                break;
            default: throw new IllegalStateException("Unknown native geometry binding " + pattern[0]);
        }
    }

    private static void assign(Object[] target, Object value, Environment environment) {
        if ("v".equals(target[0])) { environment.assign((String) target[1], value); return; }
        Object object = evaluate(target[1], environment), key = evaluate(target[2], environment);
        if (object instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) object;
            map.put(string(key), value);
        } else list(object).set((int) number(key), value);
    }

    private static Object property(Object object, Object key) {
        String name = string(key);
        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            return map.containsKey(name) ? map.get(name) : MISSING;
        }
        if (object instanceof List) {
            List<Object> values = list(object);
            if ("length".equals(name)) return (double) values.size();
            if (key instanceof Number || name.matches("[0-9]+")) {
                int index = (int) number(key);
                return index >= 0 && index < values.size() ? values.get(index) : MISSING;
            }
            return (Function) args -> arrayMethod(values, name, args);
        }
        if (object == null || object == MISSING) return MISSING;
        throw new IllegalStateException("Unknown Lumiere recipe property " + name + " on " + object);
    }

    private static Object arrayMethod(List<Object> values, String name, List<Object> args) {
        switch (name) {
            case "map": case "flatMap": case "filter": case "forEach": {
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    Object value = invoke(args.get(0), Arrays.asList(values.get(i), (double) i, values));
                    if ("filter".equals(name)) { if (truth(value)) result.add(values.get(i)); }
                    else if ("flatMap".equals(name) && value instanceof List) result.addAll(list(value));
                    else if (!"forEach".equals(name)) result.add(value);
                }
                return result;
            }
            case "push": values.addAll(args); return (double) values.size();
            case "reverse": Collections.reverse(values); return values;
            case "slice": {
                int first = args.isEmpty() ? 0 : sliceIndex(args.get(0), values.size());
                int last = args.size() < 2 ? values.size() : sliceIndex(args.get(1), values.size());
                return new ArrayList<>(values.subList(first, Math.max(first, last)));
            }
            case "sort": {
                Function compare = (Function) args.get(0);
                Collections.sort(values, (a, b) -> Double.compare(number(compare.call(Arrays.asList(a, b))), 0));
                return values;
            }
            default: throw new IllegalStateException("Unknown Lumiere recipe array method: " + name);
        }
    }

    private static int sliceIndex(Object value, int length) {
        int index = (int) number(value);
        return Math.max(0, Math.min(length, index < 0 ? length + index : index));
    }
    private static Object invoke(Object function, List<Object> args) {
        if (!(function instanceof Function)) throw new IllegalStateException("Not a native Lumiere geometry function: " + function);
        return ((Function) function).call(args);
    }
    @SuppressWarnings("unchecked") private static List<Object> list(Object value) { return (List<Object>) value; }
    private static double number(Object value) {
        if (value == null) return 0;
        if (value == MISSING) return Double.NaN;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException ignored) { return Double.NaN; }
    }
    private static String string(Object value) {
        if (value instanceof Number) {
            double n = number(value);
            return n == Math.rint(n) ? Long.toString((long) n) : Double.toString(n);
        }
        return String.valueOf(value);
    }
    private static boolean truth(Object value) {
        return value != null && value != MISSING && !Boolean.FALSE.equals(value)
                && (!(value instanceof Number) || number(value) != 0 && !Double.isNaN(number(value)))
                && (!(value instanceof String) || !((String) value).isEmpty());
    }
    private static Object binary(String operator, Object a, Object b) {
        double x = number(a), y = number(b);
        switch (operator) {
            case "+": return a instanceof String || b instanceof String ? string(a) + string(b) : x + y;
            case "-": return x - y;
            case "*": return x * y;
            case "/": return x / y;
            case "%": return x % y;
            case "**": return Math.pow(x, y);
            case "<": return x < y;
            case "<=": return x <= y;
            case ">": return x > y;
            case ">=": return x >= y;
            case "===": case "==": return a instanceof Number && b instanceof Number ? x == y : a == b || a != null && a.equals(b);
            case "!==": case "!=": return !(Boolean) binary("===", a, b);
            case "&&": case "||": case "??": return b;
            case "|": return (int) x | (int) y;
            case "&": return (int) x & (int) y;
            case "^": return (int) x ^ (int) y;
            default: throw new IllegalStateException("Unknown Lumiere recipe operator: " + operator);
        }
    }

    private static Environment standard() {
        Environment standard = new Environment(null);
        Map<String, Object> math = new LinkedHashMap<>();
        math.put("PI", Math.PI);
        for (String name : new String[]{"sin", "cos", "tan", "atan", "atan2", "exp", "sqrt", "abs", "min", "max", "floor", "ceil", "round", "hypot", "pow"}) {
            math.put(name, (Function) args -> {
                double a = number(args.get(0)), b = args.size() > 1 ? number(args.get(1)) : 0;
                switch (name) {
                    case "sin": return Math.sin(a); case "cos": return Math.cos(a); case "tan": return Math.tan(a);
                    case "atan": return Math.atan(a); case "atan2": return Math.atan2(a, b); case "exp": return Math.exp(a);
                    case "sqrt": return Math.sqrt(a); case "abs": return Math.abs(a);
                    case "floor": return Math.floor(a); case "ceil": return Math.ceil(a); case "round": return Math.floor(a + 0.5);
                    case "pow": return Math.pow(a, b); case "hypot": { double sum = 0; for (Object v : args) sum += number(v) * number(v); return Math.sqrt(sum); }
                    case "min": { double min = a; for (Object v : args) min = Math.min(min, number(v)); return min; }
                    case "max": { double max = a; for (Object v : args) max = Math.max(max, number(v)); return max; }
                    default: throw new IllegalStateException(name);
                }
            });
        }
        standard.values.put("Math", math);
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("from", (Function) args -> {
            int size = (int) number(property(args.get(0), "length"));
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) result.add(invoke(args.get(1), Arrays.asList(MISSING, (double) i)));
            return result;
        });
        standard.values.put("Array", array);
        return standard;
    }
}
