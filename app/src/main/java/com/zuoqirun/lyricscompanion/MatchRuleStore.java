package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配规则包的持久化入口。
 *
 * <p>三类来源：内置包（代码内，始终启用）、市场包（下载安装、可逐个启停）、
 * 用户包（本机编辑）。启用顺序为内置 → 市场 → 个人，同 id 只保留先出现者。</p>
 */
final class MatchRuleStore {

    static final String KEY_USER_RULES = "match_user_rules";
    static final String KEY_MARKET_RULES = "match_market_rules";
    static final String KEY_MARKET_ENABLED = "match_market_enabled";
    /** 市场目录记录 id → 本机规则包 id 的映射，用于把服务器条目和已安装包对上。 */
    static final String KEY_MARKET_RECORD_IDS = "match_market_record_ids";

    private MatchRuleStore() {
    }

    /** 当前应参与匹配的规则包，顺序固定为内置 → 市场（已启用）→ 用户。 */
    static List<MatchRulePack> enabledPacks(Context context) {
        List<MatchRulePack> result = new ArrayList<MatchRulePack>(BuiltinMatchRules.packs());
        int appVersionCode = appVersionCode(context);
        boolean hasStoredEnabledState = prefs(context).contains(KEY_MARKET_ENABLED);
        Set<String> enabledIds = marketEnabledIds(context);
        for (MatchRulePack pack : marketPacks(context)) {
            boolean enabled = hasStoredEnabledState ? enabledIds.contains(pack.id) : true;
            if (enabled && pack.isCompatible(appVersionCode) && !containsPackId(result, pack.id)) {
                result.add(pack);
            }
        }
        for (MatchRulePack pack : userPacks(context)) {
            if (pack.isCompatible(appVersionCode) && !containsPackId(result, pack.id)) {
                result.add(pack);
            }
        }
        return result;
    }

    /** 本机用户规则包。 */
    static List<MatchRulePack> userPacks(Context context) {
        return readPacks(context, KEY_USER_RULES);
    }

    /** 保存本机用户规则包（整体覆盖）。 */
    static void saveUserPacks(Context context, List<MatchRulePack> packs) {
        writePacks(context, KEY_USER_RULES, packs);
    }

    /** 已安装的市场规则包。 */
    static List<MatchRulePack> marketPacks(Context context) {
        return readPacks(context, KEY_MARKET_RULES);
    }

    /** 安装或更新市场包，默认启用。 */
    static void installMarketPack(Context context, MatchRulePack pack) {
        installMarketPack(context, pack, "");
    }

    /**
     * 安装或更新市场包并记住服务器目录记录 id。
     *
     * <p>规则包自身的 id 来自规则 JSON（如 {@code general.numeric-suffix}），而市场目录
     * 使用服务器提交记录 id（如 {@code r-4d7a8dc1}）。这里保存两者的映射，市场页才能
     * 正确显示「已安装 / 已启用」并对对应包执行启停与移除。</p>
     */
    static void installMarketPack(Context context, MatchRulePack pack, String recordId) {
        if (pack == null) {
            return;
        }
        List<MatchRulePack> packs = new ArrayList<MatchRulePack>(marketPacks(context));
        boolean replaced = false;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).id.equals(pack.id)) {
                packs.set(i, pack);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            packs.add(pack);
        }
        writePacks(context, KEY_MARKET_RULES, packs);
        setMarketPackEnabled(context, pack.id, true);
        if (recordId != null && !recordId.trim().isEmpty()) {
            putMarketRecordId(context, pack.id, recordId.trim());
        }
    }

    /** 本机已安装市场包 id → 服务器目录记录 id 的映射。 */
    static Map<String, String> marketRecordIds(Context context) {
        Map<String, String> result = new HashMap<String, String>();
        if (context == null) {
            return result;
        }
        String json = prefs(context).getString(KEY_MARKET_RECORD_IDS, "");
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        try {
            JSONObject object = new JSONObject(json);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String packId = keys.next();
                String recordId = object.optString(packId, "").trim();
                if (!packId.trim().isEmpty() && !recordId.isEmpty()) {
                    result.put(packId, recordId);
                }
            }
        } catch (JSONException e) {
            return result;
        }
        return result;
    }

    /** 删除市场包并一并清除启停状态与目录映射。 */
    static void removeMarketPack(Context context, String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        List<MatchRulePack> packs = new ArrayList<MatchRulePack>(marketPacks(context));
        for (int i = packs.size() - 1; i >= 0; i--) {
            if (packs.get(i).id.equals(id)) {
                packs.remove(i);
            }
        }
        writePacks(context, KEY_MARKET_RULES, packs);
        Set<String> enabledIds = marketEnabledIds(context);
        enabledIds.remove(id);
        writeMarketEnabledIds(context, enabledIds);
        removeMarketRecordId(context, id);
    }

    /** 启停一个市场包。 */
    static void setMarketPackEnabled(Context context, String id, boolean enabled) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        Set<String> enabledIds = marketEnabledIds(context);
        if (enabled) {
            enabledIds.add(id);
        } else {
            enabledIds.remove(id);
        }
        writeMarketEnabledIds(context, enabledIds);
    }

    /** 市场包是否启用；从未写入过启停状态时视为启用。 */
    static boolean isMarketPackEnabled(Context context, String id) {
        if (id == null) {
            return false;
        }
        if (!prefs(context).contains(KEY_MARKET_ENABLED)) {
            return true;
        }
        return marketEnabledIds(context).contains(id);
    }

    /** 一键启用或停用全部已安装市场规则。 */
    static void setAllMarketPacksEnabled(Context context, boolean enabled) {
        Set<String> ids = new LinkedHashSet<String>();
        if (enabled) {
            for (MatchRulePack pack : marketPacks(context)) {
                ids.add(pack.id);
            }
        }
        writeMarketEnabledIds(context, ids);
    }

    /** 已安装且已启用的市场包数量。 */
    static int enabledMarketPackCount(Context context) {
        int count = 0;
        for (MatchRulePack pack : marketPacks(context)) {
            if (isMarketPackEnabled(context, pack.id)) {
                count++;
            }
        }
        return count;
    }

    // ---- 内部实现 ----

    private static List<MatchRulePack> readPacks(Context context, String key) {
        List<MatchRulePack> packs = new ArrayList<MatchRulePack>();
        if (context == null) {
            return packs;
        }
        String json = prefs(context).getString(key, "");
        if (json == null || json.trim().isEmpty()) {
            return packs;
        }
        JSONArray array;
        try {
            array = new JSONArray(json);
        } catch (JSONException e) {
            return packs;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            try {
                packs.add(MatchRulePack.parse(object));
            } catch (IllegalArgumentException e) {
                // 单包损坏时跳过，不影响其它规则包。
            }
        }
        return packs;
    }

    private static void writePacks(Context context, String key, List<MatchRulePack> packs) {
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        if (packs != null) {
            for (MatchRulePack pack : packs) {
                if (pack == null) {
                    continue;
                }
                try {
                    array.put(pack.toJson());
                } catch (IllegalArgumentException e) {
                    // 序列化失败时跳过该包。
                }
            }
        }
        prefs(context).edit().putString(key, array.toString()).apply();
        AppPreferences.changed(context);
    }

    private static Set<String> marketEnabledIds(Context context) {
        Set<String> result = new LinkedHashSet<String>();
        if (context == null) {
            return result;
        }
        String json = prefs(context).getString(KEY_MARKET_ENABLED, "");
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "").trim();
                if (!id.isEmpty()) {
                    result.add(id);
                }
            }
        } catch (JSONException e) {
            return result;
        }
        return result;
    }

    private static void writeMarketEnabledIds(Context context, Set<String> enabledIds) {
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String id : enabledIds) {
            array.put(id);
        }
        prefs(context).edit().putString(KEY_MARKET_ENABLED, array.toString()).apply();
        AppPreferences.changed(context);
    }

    private static void putMarketRecordId(Context context, String packId, String recordId) {
        Map<String, String> mapping = marketRecordIds(context);
        mapping.put(packId, recordId);
        writeMarketRecordIds(context, mapping);
    }

    private static void removeMarketRecordId(Context context, String packId) {
        Map<String, String> mapping = marketRecordIds(context);
        if (mapping.remove(packId) != null) {
            writeMarketRecordIds(context, mapping);
        }
    }

    private static void writeMarketRecordIds(Context context, Map<String, String> mapping) {
        if (context == null) {
            return;
        }
        JSONObject object = new JSONObject();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            try {
                object.put(entry.getKey(), entry.getValue());
            } catch (JSONException ignored) {
                // 单个映射写入失败时跳过。
            }
        }
        prefs(context).edit().putString(KEY_MARKET_RECORD_IDS, object.toString()).apply();
    }

    private static boolean containsPackId(List<MatchRulePack> packs, String id) {
        for (MatchRulePack pack : packs) {
            if (pack.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return AppPreferences.get(context);
    }

    private static int appVersionCode(Context context) {
        if (context == null) {
            return Integer.MAX_VALUE;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
