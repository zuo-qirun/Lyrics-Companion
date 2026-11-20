package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单条匹配规则：when 条件全部命中时，按顺序执行 then 动作。
 *
 * <p>规则完全由 JSON 描述，可在不更新 APK 的情况下由市场包或用户自定义
 * 包扩展。解析阶段做完整校验，运行阶段任何动作失败只跳过该动作。</p>
 */
final class MatchRule {

    static final String FIELD_TITLE = "title";
    static final String FIELD_ARTIST = "artist";
    static final String FIELD_ALBUM = "album";
    static final String FIELD_PACKAGE = "package";
    static final String FIELD_SOURCE = "source";

    static final String OP_EQUALS = "equals";
    static final String OP_EQUALS_ANY = "equalsAny";
    static final String OP_CONTAINS = "contains";
    static final String OP_CONTAINS_ANY = "containsAny";
    static final String OP_STARTS_WITH = "startsWith";
    static final String OP_ENDS_WITH = "endsWith";
    static final String OP_REGEX = "regex";
    static final String OP_EMPTY = "empty";

    static final String ACTION_EXTRACT = "extract";
    static final String ACTION_REPLACE = "replace";
    static final String ACTION_SET = "set";
    static final String ACTION_CLEAR = "clear";
    static final String ACTION_TRIM = "trim";
    static final String ACTION_STRIP_SUFFIX = "stripSuffix";
    static final String ACTION_ALIAS_ARTIST = "aliasArtist";

    static final String DEFAULT_STRIP_SUFFIX_PATTERN =
            "\\s*[（(【\\[]\\s*\\d{1,3}\\s*[)）】\\]]\\s*$";

    private static final int MAX_WHEN = 6;
    private static final int MAX_THEN = 8;
    private static final int MAX_PATTERN_LENGTH = 200;
    private static final int MAX_ALIAS_ENTRIES = 50;
    private static final int MAX_ALIAS_LENGTH = 40;

    final String id;
    final List<Condition> when;
    final List<Action> then;

    private MatchRule(String id, List<Condition> when, List<Action> then) {
        this.id = id;
        this.when = when;
        this.then = then;
    }

    static MatchRule fromJson(JSONObject json, String label) {
        if (json == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String id = MatchRulePack.requireString(json, "id", label + " 的 id", 1, 40);
        List<Condition> when = new ArrayList<Condition>();
        JSONArray whenJson = MatchRulePack.optionalArray(json, "when", label + " 的 when", MAX_WHEN);
        if (whenJson != null) {
            for (int i = 0; i < whenJson.length(); i++) {
                JSONObject conditionJson = MatchRulePack.objectAt(whenJson, i, label + " 第 " + (i + 1) + " 个条件");
                when.add(Condition.fromJson(conditionJson, label + " 第 " + (i + 1) + " 个条件"));
            }
        }
        JSONArray thenJson = MatchRulePack.requiredArray(json, "then", label + " 的 then", 1, MAX_THEN);
        List<Action> then = new ArrayList<Action>();
        for (int i = 0; i < thenJson.length(); i++) {
            JSONObject actionJson = MatchRulePack.objectAt(thenJson, i, label + " 第 " + (i + 1) + " 个动作");
            then.add(Action.fromJson(actionJson, label + " 第 " + (i + 1) + " 个动作"));
        }
        return new MatchRule(id, when, then);
    }

    JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            JSONArray whenJson = new JSONArray();
            for (Condition condition : when) {
                whenJson.put(condition.toJson());
            }
            if (!when.isEmpty()) {
                json.put("when", whenJson);
            }
            JSONArray thenJson = new JSONArray();
            for (Action action : then) {
                thenJson.put(action.toJson());
            }
            json.put("then", thenJson);
            return json;
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("规则序列化失败：" + e.getMessage(), e);
        }
    }

    /** 统计该规则包含的正则表达式数量（用于整包上限校验）。 */
    int regexCount() {
        int count = 0;
        for (Condition condition : when) {
            if (condition.regex != null) {
                count++;
            }
        }
        for (Action action : then) {
            if (action.pattern != null) {
                count++;
            }
        }
        return count;
    }

    boolean matches(MatchInput input, String[] fields) {
        for (Condition condition : when) {
            if (!condition.matches(fields, input)) {
                return false;
            }
        }
        return true;
    }

    static boolean isWritableField(String field) {
        return FIELD_TITLE.equals(field) || FIELD_ARTIST.equals(field) || FIELD_ALBUM.equals(field);
    }

    static boolean isKnownField(String field) {
        return isWritableField(field) || FIELD_PACKAGE.equals(field) || FIELD_SOURCE.equals(field);
    }

    static int fieldIndex(String field) {
        if (FIELD_TITLE.equals(field)) {
            return 0;
        }
        if (FIELD_ARTIST.equals(field)) {
            return 1;
        }
        if (FIELD_ALBUM.equals(field)) {
            return 2;
        }
        return -1;
    }

    static String fieldLabel(String field) {
        if (FIELD_ARTIST.equals(field)) {
            return "歌手";
        }
        if (FIELD_ALBUM.equals(field)) {
            return "专辑";
        }
        return "标题";
    }

    /** when 条件：字段值比较。 */
    static final class Condition {

        final String field;
        final String op;
        final List<String> values;
        final Pattern regex;

        private Condition(String field, String op, List<String> values, Pattern regex) {
            this.field = field;
            this.op = op;
            this.values = values;
            this.regex = regex;
        }

        static Condition fromJson(JSONObject json, String label) {
            String field = MatchRulePack.requireString(json, "field", label + " 的 field", 1, 40);
            if (!isKnownField(field)) {
                throw new IllegalArgumentException(label + " 的 field 不支持：" + field);
            }
            String op = MatchRulePack.requireString(json, "op", label + " 的 op", 1, 40);
            List<String> values = new ArrayList<String>();
            Pattern regex = null;
            if (OP_EQUALS.equals(op) || OP_CONTAINS.equals(op)
                    || OP_STARTS_WITH.equals(op) || OP_ENDS_WITH.equals(op)) {
                values.add(MatchRulePack.requireText(json, "value", label + " 的 value"));
            } else if (OP_EQUALS_ANY.equals(op) || OP_CONTAINS_ANY.equals(op)) {
                JSONArray valueJson = MatchRulePack.requiredArray(json, "value", label + " 的 value", 1, 20);
                for (int i = 0; i < valueJson.length(); i++) {
                    values.add(MatchRulePack.stringAt(valueJson, i, label + " 的 value"));
                }
            } else if (OP_REGEX.equals(op)) {
                String patternText = MatchRulePack.requireRawText(json, "value", label + " 的 value");
                MatchRulePack.validatePattern(patternText, label + " 的正则", MAX_PATTERN_LENGTH);
                regex = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
            } else if (OP_EMPTY.equals(op)) {
                // empty 不需要 value。
            } else {
                throw new IllegalArgumentException(label + " 的 op 不支持：" + op);
            }
            return new Condition(field, op, values, regex);
        }

        boolean matches(String[] fields, MatchInput input) {
            String current = current(fields, input);
            if (OP_EQUALS.equals(op)) {
                return equalsIgnoreCase(current, values.get(0));
            }
            if (OP_EQUALS_ANY.equals(op)) {
                for (String value : values) {
                    if (equalsIgnoreCase(current, value)) {
                        return true;
                    }
                }
                return false;
            }
            if (OP_CONTAINS.equals(op)) {
                return lower(current).contains(lower(values.get(0)));
            }
            if (OP_CONTAINS_ANY.equals(op)) {
                String lowered = lower(current);
                for (String value : values) {
                    if (lowered.contains(lower(value))) {
                        return true;
                    }
                }
                return false;
            }
            if (OP_STARTS_WITH.equals(op)) {
                return lower(current).startsWith(lower(values.get(0)));
            }
            if (OP_ENDS_WITH.equals(op)) {
                return lower(current).endsWith(lower(values.get(0)));
            }
            if (OP_REGEX.equals(op)) {
                return regex != null && regex.matcher(current).find();
            }
            if (OP_EMPTY.equals(op)) {
                return current.trim().isEmpty();
            }
            return false;
        }

        private String current(String[] fields, MatchInput input) {
            int index = fieldIndex(field);
            if (index >= 0) {
                return fields[index] == null ? "" : fields[index];
            }
            if (FIELD_PACKAGE.equals(field)) {
                return input == null ? "" : input.packageName;
            }
            if (FIELD_SOURCE.equals(field)) {
                return input == null ? "" : input.source;
            }
            return "";
        }

        JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("field", field);
                json.put("op", op);
                if (OP_EQUALS_ANY.equals(op) || OP_CONTAINS_ANY.equals(op)) {
                    JSONArray array = new JSONArray();
                    for (String value : values) {
                        array.put(value);
                    }
                    json.put("value", array);
                } else if (OP_REGEX.equals(op)) {
                    json.put("value", regex == null ? "" : regex.pattern());
                } else if (!OP_EMPTY.equals(op)) {
                    json.put("value", values.get(0));
                }
                return json;
            } catch (org.json.JSONException e) {
                throw new IllegalArgumentException("条件序列化失败：" + e.getMessage(), e);
            }
        }
    }

    /** then 动作：对 title/artist/album 之一做确定性变换。 */
    static final class Action {

        final String type;
        final String field;
        final String value;
        final String patternText;
        final String replacement;
        final boolean all;
        final Map<String, Integer> targets;
        final Map<String, String> aliasLookup;
        final Pattern pattern;

        private Action(String type, String field, String value, String patternText, String replacement,
                       boolean all, Map<String, Integer> targets, Map<String, String> aliasLookup,
                       Pattern pattern) {
            this.type = type;
            this.field = field;
            this.value = value;
            this.patternText = patternText;
            this.replacement = replacement;
            this.all = all;
            this.targets = targets;
            this.aliasLookup = aliasLookup;
            this.pattern = pattern;
        }

        static Action fromJson(JSONObject json, String label) {
            String type = MatchRulePack.requireString(json, "type", label + " 的 type", 1, 40);
            if (ACTION_EXTRACT.equals(type)) {
                String patternText = MatchRulePack.requireRawText(json, "pattern", label + " 的 pattern");
                MatchRulePack.validatePattern(patternText, label + " 的正则", MAX_PATTERN_LENGTH);
                JSONObject targetsJson = MatchRulePack.requiredObject(json, "targets", label + " 的 targets");
                Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
                Iterator<String> keys = targetsJson.keys();
                while (keys.hasNext()) {
                    String target = keys.next();
                    if (!isWritableField(target)) {
                        throw new IllegalArgumentException(label + " 的 targets 包含未知字段：" + target);
                    }
                    int group = MatchRulePack.requiredInt(targetsJson, target, label + " 的 targets." + target, 1, 9);
                    targets.put(target, Integer.valueOf(group));
                }
                if (targets.isEmpty()) {
                    throw new IllegalArgumentException(label + " 的 targets 至少需要 1 个字段");
                }
                return new Action(type, "", "", patternText, "", false, targets, null,
                        Pattern.compile(patternText));
            }
            if (ACTION_REPLACE.equals(type)) {
                String field = MatchRulePack.optionalString(json, "field", label + " 的 field", 1, 40,
                        FIELD_TITLE);
                if (!isWritableField(field)) {
                    throw new IllegalArgumentException(label + " 的 field 不支持：" + field);
                }
                String patternText = MatchRulePack.requireRawText(json, "pattern", label + " 的 pattern");
                MatchRulePack.validatePattern(patternText, label + " 的正则", MAX_PATTERN_LENGTH);
                String replacement = MatchRulePack.requireRawText(json, "replacement", label + " 的 replacement");
                boolean all = MatchRulePack.optionalBoolean(json, "all", label + " 的 all", true);
                return new Action(type, field, "", patternText, replacement, all, null, null,
                        Pattern.compile(patternText));
            }
            if (ACTION_SET.equals(type)) {
                String field = MatchRulePack.requireString(json, "field", label + " 的 field", 1, 40);
                if (!isWritableField(field)) {
                    throw new IllegalArgumentException(label + " 的 field 不支持：" + field);
                }
                String value = MatchRulePack.requireRawText(json, "value", label + " 的 value");
                return new Action(type, field, value, null, "", false, null, null, null);
            }
            if (ACTION_CLEAR.equals(type) || ACTION_TRIM.equals(type)) {
                String field = MatchRulePack.requireString(json, "field", label + " 的 field", 1, 40);
                if (!isWritableField(field)) {
                    throw new IllegalArgumentException(label + " 的 field 不支持：" + field);
                }
                return new Action(type, field, "", null, "", false, null, null, null);
            }
            if (ACTION_STRIP_SUFFIX.equals(type)) {
                String field = MatchRulePack.optionalString(json, "field", label + " 的 field", 1, 40,
                        FIELD_TITLE);
                if (!isWritableField(field)) {
                    throw new IllegalArgumentException(label + " 的 field 不支持：" + field);
                }
                String patternText = MatchRulePack.optionalString(json, "pattern", label + " 的 pattern", 0,
                        MAX_PATTERN_LENGTH, DEFAULT_STRIP_SUFFIX_PATTERN);
                MatchRulePack.validatePattern(patternText, label + " 的正则", MAX_PATTERN_LENGTH);
                return new Action(type, field, "", patternText, "", false, null, null,
                        Pattern.compile(patternText));
            }
            if (ACTION_ALIAS_ARTIST.equals(type)) {
                JSONObject mapJson = MatchRulePack.requiredObject(json, "map", label + " 的 map");
                Map<String, String> aliasLookup = new LinkedHashMap<String, String>();
                Iterator<String> keys = mapJson.keys();
                int total = 0;
                while (keys.hasNext()) {
                    String key = keys.next();
                    total++;
                    if (total > MAX_ALIAS_ENTRIES) {
                        throw new IllegalArgumentException(label + " 的 map 超过 " + MAX_ALIAS_ENTRIES + " 项");
                    }
                    String aliasKey = key == null ? "" : key.trim();
                    if (aliasKey.isEmpty() || aliasKey.length() > MAX_ALIAS_LENGTH) {
                        throw new IllegalArgumentException(label + " 的 map 键长度必须在 1-" + MAX_ALIAS_LENGTH + " 之间");
                    }
                    String aliasValue = mapJson.optString(key, "").trim();
                    if (aliasValue.length() > MAX_ALIAS_LENGTH) {
                        throw new IllegalArgumentException(label + " 的 map 值长度不能超过 " + MAX_ALIAS_LENGTH);
                    }
                    aliasLookup.put(aliasKey.toLowerCase(Locale.ROOT), aliasValue);
                }
                if (aliasLookup.isEmpty()) {
                    throw new IllegalArgumentException(label + " 的 map 至少需要 1 项");
                }
                return new Action(type, FIELD_ARTIST, "", null, "", false, null, aliasLookup, null);
            }
            throw new IllegalArgumentException(label + " 的 type 不支持：" + type);
        }

        /** 执行动作，返回是否产生变化；动作异常时只跳过该动作。 */
        boolean apply(String[] fields, MatchInput input) {
            try {
                if (ACTION_EXTRACT.equals(type)) {
                    return applyExtract(fields);
                }
                int index = fieldIndex(field);
                if (index < 0) {
                    return false;
                }
                if (ACTION_SET.equals(type)) {
                    if (fields[index].equals(value)) {
                        return false;
                    }
                    fields[index] = value;
                    return true;
                }
                if (ACTION_CLEAR.equals(type)) {
                    if (fields[index].isEmpty()) {
                        return false;
                    }
                    fields[index] = "";
                    return true;
                }
                if (ACTION_TRIM.equals(type)) {
                    String trimmed = fields[index].trim();
                    if (trimmed.equals(fields[index])) {
                        return false;
                    }
                    fields[index] = trimmed;
                    return true;
                }
                if (ACTION_REPLACE.equals(type)) {
                    Matcher matcher = pattern.matcher(fields[index]);
                    if (!matcher.find()) {
                        return false;
                    }
                    String replaced = all ? matcher.replaceAll(replacement) : matcher.replaceFirst(replacement);
                    if (replaced.equals(fields[index])) {
                        return false;
                    }
                    fields[index] = replaced;
                    return true;
                }
                if (ACTION_STRIP_SUFFIX.equals(type)) {
                    Matcher matcher = pattern.matcher(fields[index]);
                    if (!matcher.find()) {
                        return false;
                    }
                    String replaced = matcher.replaceFirst("");
                    if (replaced.equals(fields[index])) {
                        return false;
                    }
                    fields[index] = replaced;
                    return true;
                }
                if (ACTION_ALIAS_ARTIST.equals(type)) {
                    return applyAliasArtist(fields);
                }
                return false;
            } catch (RuntimeException e) {
                return false;
            }
        }

        private boolean applyExtract(String[] fields) {
            Matcher matcher = pattern.matcher(fields[0]);
            if (!matcher.find()) {
                return false;
            }
            boolean changed = false;
            for (Map.Entry<String, Integer> entry : targets.entrySet()) {
                int group = entry.getValue().intValue();
                if (group > matcher.groupCount()) {
                    continue;
                }
                String extracted = matcher.group(group);
                if (extracted == null) {
                    continue;
                }
                int index = fieldIndex(entry.getKey());
                if (index < 0) {
                    continue;
                }
                String value = extracted.trim();
                if (!value.equals(fields[index])) {
                    fields[index] = value;
                    changed = true;
                }
            }
            return changed;
        }

        private boolean applyAliasArtist(String[] fields) {
            String current = fields[1];
            List<String> segments = ArtistMatchRules.segments(current);
            if (segments.isEmpty()) {
                return false;
            }
            boolean mappedAny = false;
            StringBuilder builder = new StringBuilder();
            for (String segment : segments) {
                String mapped = aliasLookup.get(segment.toLowerCase(Locale.ROOT).trim());
                if (builder.length() > 0) {
                    builder.append('/');
                }
                if (mapped != null && !mapped.equals(segment)) {
                    mappedAny = true;
                    builder.append(mapped);
                } else {
                    builder.append(segment);
                }
            }
            if (!mappedAny) {
                return false;
            }
            String joined = builder.toString();
            if (joined.equals(current)) {
                return false;
            }
            fields[1] = joined;
            return true;
        }

        JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                if (!field.isEmpty()) {
                    json.put("field", field);
                }
                if (ACTION_EXTRACT.equals(type) && targets != null) {
                    JSONObject targetsJson = new JSONObject();
                    for (Map.Entry<String, Integer> entry : targets.entrySet()) {
                        targetsJson.put(entry.getKey(), entry.getValue().intValue());
                    }
                    json.put("targets", targetsJson);
                }
                if (ACTION_ALIAS_ARTIST.equals(type) && aliasLookup != null) {
                    JSONObject mapJson = new JSONObject();
                    for (Map.Entry<String, String> entry : aliasLookup.entrySet()) {
                        mapJson.put(entry.getKey(), entry.getValue());
                    }
                    json.put("map", mapJson);
                }
                if (patternText != null) {
                    json.put("pattern", patternText);
                }
                if (ACTION_REPLACE.equals(type)) {
                    json.put("replacement", replacement);
                    json.put("all", all);
                }
                if (ACTION_SET.equals(type)) {
                    json.put("value", value);
                }
                return json;
            } catch (org.json.JSONException e) {
                throw new IllegalArgumentException("动作序列化失败：" + e.getMessage(), e);
            }
        }
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
