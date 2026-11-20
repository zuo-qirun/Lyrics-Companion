package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MatchRuleEngineTest {
    @Test public void appliesActionsInOrderAndProducesPrimaryOutput() throws Exception {
        JSONArray rules = new JSONArray();
        rules.put(rule("extract_names",
                when(condition("artist", MatchRule.OP_EMPTY, null)),
                extract("^(.+) - (.+)$", targets("artist", 1, "title", 2))));
        rules.put(rule("set_artist", null, set("artist", "未知")));
        MatchRulePack pack = pack("test.sequence", rules);

        MatchOutput output = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput(" 张学友 - 吻别 ", "", "", "com.test.player", "media"));

        assertTrue(output.changed);
        assertEquals("吻别", output.primaryTitle);
        assertEquals("未知", output.primaryArtist);
        assertFalse(output.candidates.isEmpty());
        assertFalse(output.trace.isEmpty());
    }

    @Test public void candidateNeverRepeatsAndNeverEqualsOriginalInput() throws Exception {
        JSONArray rules = new JSONArray();
        rules.put(rule("to_x", null, set("title", "X")));
        rules.put(rule("back_to_original", null, set("title", "原曲")));
        rules.put(rule("to_x_again", null, set("title", "X")));
        MatchRulePack pack = pack("test.candidates", rules);

        MatchOutput output = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("原曲", "", "", "com.test.player", "media"));

        assertTrue(output.changed);
        assertEquals("X", output.primaryTitle);
        assertEquals(1, output.candidates.size());
        assertEquals("X", output.candidates.get(0).title);
        assertEquals(MatchRuleEngine.CANDIDATE_KIND, output.candidates.get(0).kind);
    }

    @Test public void skipsPackWhenPackageScopeDoesNotMatch() throws Exception {
        JSONObject packJson = basePack("test.package.scope");
        JSONObject scope = new JSONObject();
        scope.put("packages", new JSONArray().put("com.byd.media"));
        packJson.put("scope", scope);
        JSONArray rules = new JSONArray();
        rules.put(rule("rename", null, set("title", "车载标题")));
        packJson.put("rules", rules);
        MatchRulePack pack = MatchRulePack.parse(packJson);

        MatchOutput matched = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("原曲", "", "", "com.byd.media", "media"));
        assertEquals("车载标题", matched.primaryTitle);

        MatchOutput skipped = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("原曲", "", "", "com.echo.music", "media"));
        assertFalse(skipped.changed);
        assertEquals("原曲", skipped.primaryTitle);
    }

    @Test public void skipsPackWhenSourceScopeDoesNotMatch() throws Exception {
        JSONObject packJson = basePack("test.source.scope");
        JSONObject scope = new JSONObject();
        scope.put("sources", new JSONArray().put("netease"));
        packJson.put("scope", scope);
        JSONArray rules = new JSONArray();
        rules.put(rule("rename", null, set("title", "网易云标题")));
        packJson.put("rules", rules);
        MatchRulePack pack = MatchRulePack.parse(packJson);

        MatchOutput matched = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("原曲", "", "", "com.test", "netease"));
        assertEquals("网易云标题", matched.primaryTitle);

        MatchOutput skipped = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("原曲", "", "", "com.test", "media"));
        assertFalse(skipped.changed);
    }

    @Test public void rejectsPackWithoutRules() {
        JSONObject packJson = basePack("test.empty.rules");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packJson));
        assertTrue(error.getMessage().contains("rules") || error.getMessage().contains("规则"));
    }

    @Test public void rejectsUnsupportedSchemaVersion() {
        JSONObject packJson = basePack("test.schema.version");
        try {
            packJson.put("schemaVersion", 2);
        } catch (Exception ignored) {
            // JSONObject.put 不会失败，这里只为让编译路径完整。
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packJson));
        assertTrue(error.getMessage().contains("schemaVersion"));
    }

    @Test public void rejectsDuplicateRuleIds() throws Exception {
        JSONObject packJson = basePack("test.duplicate.ids");
        JSONArray rules = new JSONArray();
        rules.put(rule("same_id", null, set("title", "A")));
        rules.put(rule("same_id", null, set("title", "B")));
        packJson.put("rules", rules);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packJson));
        assertTrue(error.getMessage().contains("重复"));
    }

    @Test public void rejectsKnownFieldWithWrongType() {
        JSONObject packJson = basePack("test.wrong.type");
        try {
            packJson.put("version", "1");
        } catch (Exception ignored) {
            // 同上。
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packJson));
        assertTrue(error.getMessage().contains("整数"));
    }

    @Test public void rejectsTooManyRules() throws Exception {
        JSONObject packJson = basePack("test.too.many.rules");
        JSONArray rules = new JSONArray();
        for (int i = 0; i <= MatchRulePack.MAX_RULES; i++) {
            rules.put(rule("rule_" + i, null, set("title", "A")));
        }
        packJson.put("rules", rules);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packJson));
        assertTrue(error.getMessage().contains("64"));
    }

    @Test public void rejectsOversizedPack() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 40000; i++) builder.append('a');
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(builder.toString()));
        assertTrue(error.getMessage().contains("32KB"));
    }

    @Test public void rejectsNamedCaptureGroups() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packWithRegex("(?<name>abc)")));
        assertTrue(error.getMessage().contains("命名捕获组"));
    }

    @Test public void rejectsBackReferences() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packWithRegex("(a)\\1")));
        assertTrue(error.getMessage().contains("反向引用"));
    }

    @Test public void rejectsNestedQuantifiers() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packWithRegex("(a+)+")));
        assertTrue(error.getMessage().contains("嵌套量词"));
    }

    @Test public void rejectsUnbalancedBraces() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MatchRulePack.parse(packWithRegex("a{1,2")));
        assertTrue(error.getMessage().contains("花括号"));
    }

    @Test public void ignoresUnknownFieldsAndRoundTripsJson() throws Exception {
        JSONObject packJson = basePack("test.roundtrip");
        packJson.put("futureField", "忽略我");
        packJson.put("minVersionCode", 0);
        JSONArray rules = new JSONArray();
        rules.put(rule("trim_title", null, action("trim", "title", null, null)));
        packJson.put("rules", rules);

        MatchRulePack pack = MatchRulePack.parse(packJson);
        assertNotNull(pack);
        String json = pack.toJsonString();
        MatchRulePack reparsed = MatchRulePack.parse(json);
        assertEquals(pack.id, reparsed.id);
        assertEquals(pack.version, reparsed.version);
        assertEquals(pack.rules.size(), reparsed.rules.size());
        assertEquals("test.roundtrip", reparsed.id);
    }

    @Test public void parsesCanonicalMarketPackWithTypeActions() throws Exception {
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"id\":\"echo.split-artist-title\","
                + "\"name\":\"回声：拆分歌手与歌名\","
                + "\"version\":1,"
                + "\"author\":\"测试作者\","
                + "\"rules\":[{"
                + "\"id\":\"split-dash\","
                + "\"when\":[{\"field\":\"artist\",\"op\":\"equalsAny\","
                + "\"value\":[\"车载音乐\",\"未知歌手\"]}],"
                + "\"then\":[{\"type\":\"extract\","
                + "\"pattern\":\"^(.{1,40}) - (.{1,80})$\","
                + "\"targets\":{\"artist\":1,\"title\":2}}]"
                + "}]}";

        MatchRulePack pack = MatchRulePack.parse(json);
        assertEquals("echo.split-artist-title", pack.id);
        assertEquals(1, pack.rules.size());
        assertEquals(MatchRule.ACTION_EXTRACT, pack.rules.get(0).then.get(0).type);

        MatchOutput output = MatchRuleEngine.apply(Collections.singletonList(pack),
                new MatchInput("张学友 - 吻别", "车载音乐", "", "com.echo.music", "media"));
        assertEquals("吻别", output.primaryTitle);
        assertEquals("张学友", output.primaryArtist);
    }

    private static JSONObject basePack(String id) {
        JSONObject pack = new JSONObject();
        try {
            pack.put("schemaVersion", MatchRulePack.SCHEMA_VERSION);
            pack.put("id", id);
            pack.put("name", "测试规则包");
            pack.put("version", 1);
            pack.put("author", "测试");
            pack.put("rules", new JSONArray());
        } catch (Exception ignored) {
            throw new IllegalStateException(ignored);
        }
        return pack;
    }

    private static MatchRulePack pack(String id, JSONArray rules) throws Exception {
        JSONObject pack = basePack(id);
        pack.put("rules", rules);
        return MatchRulePack.parse(pack);
    }

    private static JSONObject rule(String id, JSONArray when, JSONObject action) throws Exception {
        JSONObject rule = new JSONObject();
        rule.put("id", id);
        if (when != null) rule.put("when", when);
        JSONArray then = new JSONArray();
        then.put(action);
        rule.put("then", then);
        return rule;
    }

    private static JSONArray when(JSONObject condition) {
        JSONArray array = new JSONArray();
        array.put(condition);
        return array;
    }

    private static JSONObject condition(String field, String op, Object value) throws Exception {
        JSONObject condition = new JSONObject();
        condition.put("field", field);
        condition.put("op", op);
        if (value != null) condition.put("value", value);
        return condition;
    }

    private static JSONObject set(String field, String value) throws Exception {
        JSONObject action = new JSONObject();
        action.put("type", MatchRule.ACTION_SET);
        action.put("field", field);
        action.put("value", value);
        return action;
    }

    private static JSONObject extract(String pattern, JSONObject targets) throws Exception {
        JSONObject action = new JSONObject();
        action.put("type", MatchRule.ACTION_EXTRACT);
        action.put("pattern", pattern);
        action.put("targets", targets);
        return action;
    }

    private static JSONObject targets(String firstField, int firstGroup,
                                      String secondField, int secondGroup) throws Exception {
        JSONObject targets = new JSONObject();
        targets.put(firstField, firstGroup);
        targets.put(secondField, secondGroup);
        return targets;
    }

    private static JSONObject action(String type, String field, String value,
                                     JSONObject unused) throws Exception {
        JSONObject action = new JSONObject();
        action.put("type", type);
        if (field != null) action.put("field", field);
        if (value != null) action.put("value", value);
        return action;
    }

    private static String packWithRegex(String pattern) {
        try {
            JSONObject pack = basePack("test.regex");
            JSONArray rules = new JSONArray();
            rules.put(rule("regex_rule",
                    when(condition("title", MatchRule.OP_REGEX, pattern)),
                    set("title", "匹配")));
            pack.put("rules", rules);
            return pack.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
