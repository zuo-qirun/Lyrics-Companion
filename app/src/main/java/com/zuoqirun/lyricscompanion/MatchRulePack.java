package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 一个匹配规则包：元数据 + 作用域 + 一组匹配规则。
 *
 * <p>规则包来自三处：App 内置、市场安装、用户自定义。JSON 是唯一持久化格式，
 * 解析阶段会做完整校验，错误消息为中文、可直接展示给用户。</p>
 */
final class MatchRulePack {

    static final int SCHEMA_VERSION = 1;
    static final int MAX_PACK_BYTES = 32 * 1024;
    static final int MAX_PACKAGES = 20;
    static final int MAX_SOURCES = 8;
    static final int MAX_RULES = 64;
    static final int MAX_REGEXES = 64;

    private static final Pattern PACK_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^[A-Za-z0-9._]+$");
    private static final Pattern BACK_REFERENCE_PATTERN = Pattern.compile("\\\\[1-9]");

    private static final Set<String> KNOWN_SOURCES = new LinkedHashSet<String>();

    static {
        KNOWN_SOURCES.add("netease");
        KNOWN_SOURCES.add("qqmusic");
        KNOWN_SOURCES.add("kugou");
        KNOWN_SOURCES.add("kuwo");
        KNOWN_SOURCES.add("soda");
        KNOWN_SOURCES.add("migu");
    }

    final int schemaVersion;
    final String id;
    final String name;
    final int version;
    final String author;
    final String description;
    final int minVersionCode;
    final List<String> packages;
    final List<String> sources;
    final List<MatchRule> rules;

    private MatchRulePack(int schemaVersion, String id, String name, int version, String author,
                          String description, int minVersionCode, List<String> packages,
                          List<String> sources, List<MatchRule> rules) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.minVersionCode = minVersionCode;
        this.packages = packages;
        this.sources = sources;
        this.rules = rules;
    }

    /** 解析 JSON 文本；失败抛出带中文说明的 IllegalArgumentException。 */
    static MatchRulePack parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("匹配规则包内容为空");
        }
        if (json.length() > MAX_PACK_BYTES) {
            throw new IllegalArgumentException("匹配规则包超过 32KB 上限");
        }
        JSONObject object;
        try {
            object = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("匹配规则包 JSON 格式错误：" + e.getMessage(), e);
        }
        return parse(object);
    }

    /** 解析 JSON 对象；同样做完整校验。 */
    static MatchRulePack parse(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("匹配规则包为空");
        }
        int schemaVersion = requiredInt(json, "schemaVersion", "匹配规则包的 schemaVersion", 1, Integer.MAX_VALUE);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("仅支持 schemaVersion 1，当前为 " + schemaVersion);
        }
        String id = requireString(json, "id", "匹配规则包的 id", 3, 64);
        if (!PACK_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("匹配规则包的 id 只能包含小写字母、数字、点、下划线和连字符，且以字母或数字开头");
        }
        String name = requireString(json, "name", "匹配规则包的 name", 1, 40);
        int version = requiredInt(json, "version", "匹配规则包的 version", 1, 9999);
        String author = optionalString(json, "author", "匹配规则包的 author", 0, 40, "");
        String description = optionalString(json, "description", "匹配规则包的 description", 0, 200, "");
        int minVersionCode = optionalInt(json, "minVersionCode", "匹配规则包的 minVersionCode", 0, 999999, 0);
        List<String> packages = new ArrayList<String>();
        List<String> sources = new ArrayList<String>();
        JSONObject scope = optionalObject(json, "scope", "匹配规则包的 scope");
        if (scope != null) {
            JSONArray packagesJson = optionalArray(scope, "packages", "匹配规则包的 scope.packages", MAX_PACKAGES);
            if (packagesJson != null) {
                for (int i = 0; i < packagesJson.length(); i++) {
                    String packageName = stringAt(packagesJson, i, "匹配规则包的 scope.packages");
                    if (!PACKAGE_PATTERN.matcher(packageName).matches()) {
                        throw new IllegalArgumentException("匹配规则包的 scope.packages 包含非法包名：" + packageName);
                    }
                    packages.add(packageName);
                }
            }
            JSONArray sourcesJson = optionalArray(scope, "sources", "匹配规则包的 scope.sources", MAX_SOURCES);
            if (sourcesJson != null) {
                for (int i = 0; i < sourcesJson.length(); i++) {
                    String source = stringAt(sourcesJson, i, "匹配规则包的 scope.sources").toLowerCase(Locale.ROOT);
                    if (!KNOWN_SOURCES.contains(source)) {
                        throw new IllegalArgumentException("匹配规则包的 scope.sources 包含未知音源：" + source);
                    }
                    sources.add(source);
                }
            }
        }
        JSONArray rulesJson = requiredArray(json, "rules", "匹配规则包的 rules", 1, MAX_RULES);
        List<MatchRule> rules = new ArrayList<MatchRule>();
        Set<String> ruleIds = new LinkedHashSet<String>();
        int regexCount = 0;
        for (int i = 0; i < rulesJson.length(); i++) {
            String label = "规则包 " + id + " 第 " + (i + 1) + " 条规则";
            JSONObject ruleJson = objectAt(rulesJson, i, label);
            MatchRule rule = MatchRule.fromJson(ruleJson, label);
            if (!ruleIds.add(rule.id)) {
                throw new IllegalArgumentException(label + " 的 id 重复：" + rule.id);
            }
            regexCount += rule.regexCount();
            rules.add(rule);
        }
        if (regexCount > MAX_REGEXES) {
            throw new IllegalArgumentException("匹配规则包内的正则表达式超过 " + MAX_REGEXES + " 个");
        }
        MatchRulePack pack = new MatchRulePack(SCHEMA_VERSION, id, name, version, author, description,
                minVersionCode, packages, sources, rules);
        if (pack.toJsonString().length() > MAX_PACK_BYTES) {
            throw new IllegalArgumentException("匹配规则包超过 32KB 上限");
        }
        return pack;
    }

    JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("schemaVersion", schemaVersion);
            json.put("id", id);
            json.put("name", name);
            json.put("version", version);
            json.put("author", author);
            json.put("description", description);
            json.put("minVersionCode", minVersionCode);
            if (!packages.isEmpty() || !sources.isEmpty()) {
                JSONObject scope = new JSONObject();
                if (!packages.isEmpty()) {
                    JSONArray packagesJson = new JSONArray();
                    for (String packageName : packages) {
                        packagesJson.put(packageName);
                    }
                    scope.put("packages", packagesJson);
                }
                if (!sources.isEmpty()) {
                    JSONArray sourcesJson = new JSONArray();
                    for (String source : sources) {
                        sourcesJson.put(source);
                    }
                    scope.put("sources", sourcesJson);
                }
                json.put("scope", scope);
            }
            JSONArray rulesJson = new JSONArray();
            for (MatchRule rule : rules) {
                rulesJson.put(rule.toJson());
            }
            json.put("rules", rulesJson);
            return json;
        } catch (JSONException e) {
            throw new IllegalArgumentException("匹配规则包序列化失败：" + e.getMessage(), e);
        }
    }

    String toJsonString() {
        return toJson().toString();
    }

    /** 作用域判断：包名与音源都命中（或未限定）才应用。 */
    boolean appliesTo(String packageName, String source) {
        if (!packages.isEmpty()) {
            String value = packageName == null ? "" : packageName.trim();
            if (!packages.contains(value)) {
                return false;
            }
        }
        if (!sources.isEmpty()) {
            String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
            if (!sources.contains(value)) {
                return false;
            }
        }
        return true;
    }

    /** 当前 App 版本是否满足规则包的最低版本要求。 */
    boolean isCompatible(int appVersionCode) {
        return appVersionCode >= minVersionCode;
    }

    /** 供诊断页展示的规则数量摘要。 */
    String describe() {
        return name + "（" + id + " v" + version + "，" + rules.size() + " 条规则）";
    }

    // ---- JSON 校验工具（包内共享） ----

    static String requireString(JSONObject json, String key, String label, int minLength, int maxLength) {
        String value = requireRawText(json, key, label).trim();
        if (value.length() < minLength || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " 长度必须在 " + minLength + "-" + maxLength + " 之间");
        }
        return value;
    }

    static String requireText(JSONObject json, String key, String label) {
        return requireRawText(json, key, label).trim();
    }

    static String requireRawText(JSONObject json, String key, String label) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        Object value = json.opt(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(label + " 必须是字符串");
        }
        return (String) value;
    }

    static String optionalString(JSONObject json, String key, String label, int minLength, int maxLength,
                                 String fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        Object value = json.opt(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(label + " 必须是字符串");
        }
        String text = ((String) value).trim();
        if (text.length() < minLength || text.length() > maxLength) {
            throw new IllegalArgumentException(label + " 长度必须在 " + minLength + "-" + maxLength + " 之间");
        }
        return text;
    }

    static int requiredInt(JSONObject json, String key, String label, int min, int max) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        return readInt(json.opt(key), label, min, max);
    }

    static int optionalInt(JSONObject json, String key, String label, int min, int max, int fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        return readInt(json.opt(key), label, min, max);
    }

    private static int readInt(Object value, String label, int min, int max) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(label + " 必须是整数");
        }
        Number number = (Number) value;
        double doubleValue = number.doubleValue();
        int intValue = number.intValue();
        if (doubleValue != intValue) {
            throw new IllegalArgumentException(label + " 必须是整数");
        }
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(label + " 必须在 " + min + "-" + max + " 之间");
        }
        return intValue;
    }

    static boolean optionalBoolean(JSONObject json, String key, String label, boolean fallback) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return fallback;
        }
        Object value = json.opt(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(label + " 必须是布尔值");
        }
        return ((Boolean) value).booleanValue();
    }

    static JSONArray requiredArray(JSONObject json, String key, String label, int min, int max) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        Object value = json.opt(key);
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException(label + " 必须是数组");
        }
        JSONArray array = (JSONArray) value;
        if (array.length() < min || array.length() > max) {
            throw new IllegalArgumentException(label + " 数量必须在 " + min + "-" + max + " 之间");
        }
        return array;
    }

    static JSONArray optionalArray(JSONObject json, String key, String label, int max) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (!(value instanceof JSONArray)) {
            throw new IllegalArgumentException(label + " 必须是数组");
        }
        JSONArray array = (JSONArray) value;
        if (array.length() > max) {
            throw new IllegalArgumentException(label + " 数量不能超过 " + max);
        }
        return array;
    }

    static JSONObject requiredObject(JSONObject json, String key, String label) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        return asObject(json.opt(key), label);
    }

    static JSONObject optionalObject(JSONObject json, String key, String label) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return asObject(json.opt(key), label);
    }

    private static JSONObject asObject(Object value, String label) {
        if (!(value instanceof JSONObject)) {
            throw new IllegalArgumentException(label + " 必须是对象");
        }
        return (JSONObject) value;
    }

    static JSONObject objectAt(JSONArray array, int index, String label) {
        if (array == null || index < 0 || index >= array.length()) {
            throw new IllegalArgumentException(label + " 缺失");
        }
        return asObject(array.opt(index), label);
    }

    static String stringAt(JSONArray array, int index, String label) {
        if (array == null || index < 0 || index >= array.length()) {
            throw new IllegalArgumentException(label + " 缺失");
        }
        Object value = array.opt(index);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(label + " 必须是字符串");
        }
        String text = ((String) value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        return text;
    }

    /**
     * 校验正则安全性：长度、命名组、反向引用、花括号配对与嵌套量词。
     *
     * <p>规则来自市场包，必须避免灾难性回溯与非法结构；这些限制不影响
     * 常见清洗规则写法。</p>
     */
    static void validatePattern(String pattern, String label, int maxLength) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        if (pattern.length() > maxLength) {
            throw new IllegalArgumentException(label + " 不能超过 " + maxLength + " 个字符");
        }
        if (pattern.contains("(?<")) {
            throw new IllegalArgumentException(label + " 不允许使用命名捕获组");
        }
        if (BACK_REFERENCE_PATTERN.matcher(pattern).find()) {
            throw new IllegalArgumentException(label + " 不允许使用反向引用");
        }
        validateBraces(pattern, label);
        validateNestedQuantifiers(pattern, label);
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(label + " 不是合法的正则表达式：" + e.getMessage(), e);
        }
    }

    private static void validateBraces(String pattern, String label) {
        int depth = 0;
        boolean escaped = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException(label + " 的花括号不配对");
                }
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException(label + " 的花括号不配对");
        }
    }

    private static void validateNestedQuantifiers(String pattern, String label) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '[') {
                int end = findClassEnd(pattern, i);
                if (end < 0) {
                    throw new IllegalArgumentException(label + " 的字符类不完整");
                }
                i = end;
                continue;
            }
            if (c != '(') {
                continue;
            }
            int end = findGroupEnd(pattern, i);
            if (end < 0) {
                throw new IllegalArgumentException(label + " 的括号不配对");
            }
            char next = end + 1 < pattern.length() ? pattern.charAt(end + 1) : 0;
            if (next == '*' || next == '+' || next == '?' || next == '{') {
                String inner = pattern.substring(i + 1, end);
                if (inner.indexOf('*') >= 0 || inner.indexOf('+') >= 0 || inner.indexOf('{') >= 0) {
                    throw new IllegalArgumentException(label + " 不允许嵌套量词（例如 (.*)+）");
                }
            }
        }
    }

    private static int findClassEnd(String pattern, int start) {
        for (int i = start + 1; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == ']') {
                return i;
            }
        }
        return -1;
    }

    private static int findGroupEnd(String pattern, int start) {
        int depth = 0;
        for (int i = start; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '[') {
                int classEnd = findClassEnd(pattern, i);
                if (classEnd < 0) {
                    return -1;
                }
                i = classEnd;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
