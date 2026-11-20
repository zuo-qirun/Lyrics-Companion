package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * App 内置的清洗规则包：不依赖网络与用户配置，始终参与匹配。
 *
 * <p>规则包本身也走 {@link MatchRulePack#parse(JSONObject)} 的完整校验，
 * 与市场包使用同一套执行路径，避免两套行为漂移。</p>
 */
final class BuiltinMatchRules {

    static final String PACK_ID = "builtin.cleaning";
    static final int PACK_VERSION = 1;

    private static final String SPLIT_PATTERN = "^(.{1,40}?)\\s*[-–—－]\\s*(.{1,80})$";

    private BuiltinMatchRules() {
    }

    /** 返回内置规则包列表（当前仅一个）。 */
    static List<MatchRulePack> packs() {
        return Collections.singletonList(buildCleaningPack());
    }

    private static MatchRulePack buildCleaningPack() {
        try {
            JSONObject pack = new JSONObject();
            pack.put("schemaVersion", MatchRulePack.SCHEMA_VERSION);
            pack.put("id", PACK_ID);
            pack.put("name", "内置清洗规则");
            pack.put("version", PACK_VERSION);
            pack.put("author", "Lyrics Companion");
            pack.put("description", "车载/未知歌手与复合标题拆分、末尾编号去除、常见歌手别名归一。");
            pack.put("minVersionCode", 0);

            JSONArray rules = new JSONArray();
            rules.put(carArtistSplitRule());
            rules.put(emptyArtistSplitRule());
            rules.put(stripTrailingNumberRule());
            rules.put(aliasArtistRule());
            pack.put("rules", rules);

            return MatchRulePack.parse(pack);
        } catch (JSONException e) {
            throw new IllegalStateException("内置匹配规则构建失败：" + e.getMessage(), e);
        }
    }

    /** 车载/未知歌手：把「歌手 - 歌名」格式的复合标题拆开。 */
    private static JSONObject carArtistSplitRule() throws JSONException {
        JSONObject rule = new JSONObject();
        rule.put("id", "car_artist_split");

        JSONObject artistCondition = new JSONObject();
        artistCondition.put("field", MatchRule.FIELD_ARTIST);
        artistCondition.put("op", MatchRule.OP_EQUALS_ANY);
        JSONArray values = new JSONArray();
        values.put("车载音乐");
        values.put("车载");
        values.put("未知歌手");
        values.put("未知艺术家");
        artistCondition.put("value", values);

        JSONArray when = new JSONArray();
        when.put(artistCondition);
        rule.put("when", when);

        JSONObject extract = new JSONObject();
        extract.put("type", MatchRule.ACTION_EXTRACT);
        extract.put("pattern", SPLIT_PATTERN);
        JSONObject targets = new JSONObject();
        targets.put(MatchRule.FIELD_ARTIST, 1);
        targets.put(MatchRule.FIELD_TITLE, 2);
        extract.put("targets", targets);

        JSONArray then = new JSONArray();
        then.put(extract);
        rule.put("then", then);
        return rule;
    }

    /** 空歌手：DFTC 等播放器只给「歌手 - 歌名」标题时拆分。 */
    private static JSONObject emptyArtistSplitRule() throws JSONException {
        JSONObject rule = new JSONObject();
        rule.put("id", "empty_artist_split");

        JSONObject artistCondition = new JSONObject();
        artistCondition.put("field", MatchRule.FIELD_ARTIST);
        artistCondition.put("op", MatchRule.OP_EMPTY);
        JSONArray when = new JSONArray();
        when.put(artistCondition);
        rule.put("when", when);

        JSONObject extract = new JSONObject();
        extract.put("type", MatchRule.ACTION_EXTRACT);
        extract.put("pattern", SPLIT_PATTERN);
        JSONObject targets = new JSONObject();
        targets.put(MatchRule.FIELD_ARTIST, 1);
        targets.put(MatchRule.FIELD_TITLE, 2);
        extract.put("targets", targets);

        JSONArray then = new JSONArray();
        then.put(extract);
        rule.put("then", then);
        return rule;
    }

    /** 去掉标题末尾的 1-3 位数字括号，如「单身情歌（01）」。 */
    private static JSONObject stripTrailingNumberRule() throws JSONException {
        JSONObject rule = new JSONObject();
        rule.put("id", "strip_trailing_number");

        JSONObject stripSuffix = new JSONObject();
        stripSuffix.put("type", MatchRule.ACTION_STRIP_SUFFIX);
        stripSuffix.put("field", MatchRule.FIELD_TITLE);
        JSONArray then = new JSONArray();
        then.put(stripSuffix);
        rule.put("then", then);
        return rule;
    }

    /** 常见歌手别名归一，便于与各大平台候选对齐。 */
    private static JSONObject aliasArtistRule() throws JSONException {
        JSONObject rule = new JSONObject();
        rule.put("id", "alias_artist");

        JSONObject map = new JSONObject();
        map.put("A-Lin", "黄丽玲");
        map.put("alin", "黄丽玲");
        map.put("A Lin", "黄丽玲");
        map.put("G.E.M.", "邓紫棋");
        map.put("GEM", "邓紫棋");
        map.put("邓紫棋", "邓紫棋");
        map.put("黄霄雲", "黄霄云");

        JSONObject aliasArtist = new JSONObject();
        aliasArtist.put("type", MatchRule.ACTION_ALIAS_ARTIST);
        aliasArtist.put("map", map);

        JSONArray then = new JSONArray();
        then.put(aliasArtist);
        rule.put("then", then);
        return rule;
    }
}
