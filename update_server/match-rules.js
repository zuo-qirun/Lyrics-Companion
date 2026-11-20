"use strict";

// 匹配规则市场（issue #174）服务端。
//
// 规则包只落在 STATE_DIR/match-rules/，不进入 Git 仓库。这个模块刻意不依赖
// server.js：测试里可以用独立的 http.createServer 直接跑起来，生产环境由
// server.js 传入 {sendJson, readJson, clientIp} 辅助函数。
//
// 数据模型（与 docs/MATCH_RULES_MARKET_SPEC.md 第 3 节一致）：
// - 提交记录 id 形如 r-xxxxxxxx（响应、状态查询、管理接口、下载路径都用它）；
// - version 冲突按规则包自身的 rule.id 判断：同一 rule.id 的新版本必须更大；
// - approved/<id>.json 是对外发布副本，pending/<id>.json 保留提交/审核记录
//   （含 token 摘要与审核备注，供 status 查询；永不对外返回 token 摘要）。

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const API_PREFIX = "/api/match-rules";
const PACK_MAX_BYTES = 64 * 1024;
const REQUEST_MAX_BYTES = 192 * 1024;
const REPORTS_MAX = 1000;
const MAX_RULES = 64;
const MAX_CONDITIONS = 6;
const MAX_ACTIONS = 8;
const MAX_REGEX_LENGTH = 200;
const MAX_REGEX_COUNT = 64;
const MAX_VALUE_LENGTH = 200;
const MAX_SCOPE_PACKAGES = 20;
const MAX_SCOPE_SOURCES = 8;
const MAX_ALIAS_ENTRIES = 50;
const MAX_SUBMISSIONS_PER_HOUR = 5;
const RATE_WINDOW_MS = 60 * 60 * 1000;
const RECORD_ID_PATTERN = /^r-[a-f0-9]{8}$/;
const PACK_ID_PATTERN = /^[a-z0-9][a-z0-9._-]*$/;
const PACKAGE_PATTERN = /^[A-Za-z0-9._]+$/;
const AUTHOR_FIELDS = new Set(["title", "artist", "album"]);
const INPUT_FIELDS = new Set(["title", "artist", "album", "package", "source"]);
const SOURCE_VALUES = new Set(["netease", "qqmusic", "kugou", "kuwo", "soda", "migu"]);
const CONDITION_OPS = new Set(["equals", "equalsAny", "contains", "containsAny",
  "startsWith", "endsWith", "regex", "empty"]);
const ACTION_OPS = new Set(["extract", "replace", "set", "clear", "trim", "stripSuffix",
  "aliasArtist"]);

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function currentIso(nowMs) {
  return new Date(nowMs).toISOString();
}

/** 规则包 id：3–64 字符，小写字母/数字开头，只允许小写字母、数字、点、下划线、连字符。 */
function packIdError(value) {
  if (typeof value !== "string") return "规则包 id 必须是字符串";
  if (value.length < 3 || value.length > 64) return "规则包 id 长度必须是 3–64 字符";
  if (!PACK_ID_PATTERN.test(value)) {
    return "规则包 id 只能使用小写字母、数字、点、下划线、连字符，并以字母或数字开头";
  }
  return "";
}

function scopeValidationError(scope) {
  if (scope === undefined) return "";
  if (!isPlainObject(scope)) return "scope 必须是 JSON 对象";
  if (scope.packages !== undefined) {
    if (!Array.isArray(scope.packages) || scope.packages.length > MAX_SCOPE_PACKAGES) {
      return `scope.packages 必须是最多 ${MAX_SCOPE_PACKAGES} 项的数组`;
    }
    for (const value of scope.packages) {
      if (typeof value !== "string" || value.length === 0 || value.length > MAX_VALUE_LENGTH
        || !PACKAGE_PATTERN.test(value)) {
        return "scope.packages 中包含不合法的包名";
      }
    }
  }
  if (scope.sources !== undefined) {
    if (!Array.isArray(scope.sources) || scope.sources.length > MAX_SCOPE_SOURCES) {
      return `scope.sources 必须是最多 ${MAX_SCOPE_SOURCES} 项的数组`;
    }
    for (const value of scope.sources) {
      if (!SOURCE_VALUES.has(value)) {
        return "scope.sources 只能使用 netease/qqmusic/kugou/kuwo/soda/migu";
      }
    }
  }
  return "";
}

/**
 * 扫描正则中的分组与量词，拒绝 `(.*)+` / `(.+)+` / `(\d+)+` 这类嵌套量词（ReDoS 风险）。
 * 只把「无上界」的内层量词（*、+、{n,}）算作危险；`(.{1,40})+` 这种有界写法放行。
 */
function hasNestedQuantifier(pattern) {
  const groups = [];
  const stack = [];
  let escaped = false;
  for (let index = 0; index < pattern.length; index += 1) {
    const character = pattern[index];
    if (escaped) { escaped = false; continue; }
    if (character === "\\") { escaped = true; continue; }
    if (character === "[") {
      let cursor = index + 1;
      let classEscaped = false;
      while (cursor < pattern.length) {
        const inner = pattern[cursor];
        if (classEscaped) classEscaped = false;
        else if (inner === "\\") classEscaped = true;
        else if (inner === "]") break;
        cursor += 1;
      }
      index = cursor;
      continue;
    }
    if (character === "(") stack.push(index);
    else if (character === ")") {
      const start = stack.pop();
      if (start !== undefined) groups.push({start, end: index});
    }
  }
  for (const group of groups) {
    // 去掉转义字符后判断内层是否有无上界量词。
    const body = pattern.slice(group.start + 1, group.end).replace(/\\[\s\S]/g, "");
    const unbounded = /[*+]/.test(body) || /\{\d+,\s*\}/.test(body);
    if (!unbounded) continue;
    const rest = pattern.slice(group.end + 1);
    if (/^[*+]/.test(rest) || /^\{\d+(?:,\s*\d*)?\}/.test(rest)) return true;
  }
  return false;
}

function regexValidationError(pattern, counter, label) {
  if (typeof pattern !== "string" || pattern.length === 0) return `${label}的正则不能为空`;
  if (pattern.length > MAX_REGEX_LENGTH) {
    return `${label}的正则长度不能超过 ${MAX_REGEX_LENGTH} 字符`;
  }
  counter.count += 1;
  if (counter.count > MAX_REGEX_COUNT) return "规则包中的正则数量不能超过 64 个";
  if (pattern.includes("(?<")) return `${label}的正则不能使用命名捕获组或后向断言`;
  if (pattern.includes("(?{") || pattern.includes("(?(") || pattern.includes("(?#")) {
    return `${label}的正则包含不支持的语法`;
  }
  if (/\\[1-9]/.test(pattern)) return `${label}的正则不能使用反向引用`;
  const escapedOpens = pattern.split("\\{").length - 1;
  const escapedCloses = pattern.split("\\}").length - 1;
  if (escapedOpens !== escapedCloses) return `${label}的正则花括号无法配对`;
  if (hasNestedQuantifier(pattern)) return `${label}的正则不能使用嵌套量词`;
  return "";
}

function conditionValidationError(condition, label, counter) {
  if (!isPlainObject(condition)) return `${label}必须是 JSON 对象`;
  if (!INPUT_FIELDS.has(condition.field)) return `${label}的 field 不合法`;
  if (!CONDITION_OPS.has(condition.op)) return `${label}的 op 不合法`;
  if (condition.op === "empty") return "";
  const value = condition.value;
  if (condition.op === "equalsAny" || condition.op === "containsAny") {
    if (!Array.isArray(value) || value.length < 1 || value.length > 20) {
      return `${label}的 value 必须是 1–20 项的字符串数组`;
    }
    for (const item of value) {
      if (typeof item !== "string" || item.length < 1 || item.length > MAX_VALUE_LENGTH) {
        return `${label}的 value 必须由 1–${MAX_VALUE_LENGTH} 字符的字符串组成`;
      }
    }
    return "";
  }
  if (typeof value !== "string" || value.length === 0) {
    return `${label}的 value 必须是非空字符串`;
  }
  if (condition.op === "regex") return regexValidationError(value, counter, label);
  if (value.length > MAX_VALUE_LENGTH) {
    return `${label}的 value 不能超过 ${MAX_VALUE_LENGTH} 字符`;
  }
  return "";
}

function actionValidationError(action, label, counter) {
  if (!isPlainObject(action)) return `${label}必须是 JSON 对象`;
  const type = action.type;
  if (!ACTION_OPS.has(type)) {
    if (action.op !== undefined) return `${label}必须使用 type 字段而不是 op`;
    return `${label}的 type 不合法（应为 extract/replace/set/clear/trim/stripSuffix/aliasArtist）`;
  }
  if (type === "extract") {
    if (typeof action.pattern !== "string") return `${label}需要 pattern 字符串`;
    const patternError = regexValidationError(action.pattern, counter, label);
    if (patternError) return patternError;
    if (!isPlainObject(action.targets)) return `${label}的 targets 必须是 JSON 对象`;
    const targets = Object.keys(action.targets);
    if (targets.length < 1 || targets.length > 3) {
      return `${label}的 targets 必须包含 title/artist/album 中的 1–3 个字段`;
    }
    for (const field of targets) {
      if (!AUTHOR_FIELDS.has(field)) return `${label}的 targets 只能作用于 title/artist/album`;
      const group = action.targets[field];
      if (!Number.isInteger(group) || group < 1 || group > 9) {
        return `${label}的 targets 组号必须是 1–9 的整数`;
      }
    }
    return "";
  }
  if (type === "aliasArtist") {
    if (!isPlainObject(action.map)) return `${label}的 map 必须是 JSON 对象`;
    const entries = Object.entries(action.map);
    if (entries.length < 1 || entries.length > MAX_ALIAS_ENTRIES) {
      return `${label}的 map 必须包含 1–${MAX_ALIAS_ENTRIES} 项`;
    }
    for (const [key, value] of entries) {
      if (key.length < 1 || key.length > 40) return `${label}的 map 键必须是 1–40 字符`;
      if (typeof value !== "string" || value.length > 40) {
        return `${label}的 map 值必须是不超过 40 字符的字符串`;
      }
    }
    return "";
  }
  const field = action.field === undefined
    ? (type === "replace" || type === "stripSuffix" ? "title" : "")
    : action.field;
  if (!AUTHOR_FIELDS.has(field)) return `${label}的 field 不合法`;
  if (type === "replace") {
    if (typeof action.pattern !== "string") return `${label}需要 pattern 字符串`;
    const patternError = regexValidationError(action.pattern, counter, label);
    if (patternError) return patternError;
    if (typeof action.replacement !== "string" || action.replacement.length > MAX_VALUE_LENGTH) {
      return `${label}的 replacement 必须是不超过 ${MAX_VALUE_LENGTH} 字符的字符串`;
    }
    if (action.all !== undefined && typeof action.all !== "boolean") {
      return `${label}的 all 必须是布尔值`;
    }
  }
  if (type === "set") {
    if (typeof action.value !== "string" || action.value.length > MAX_VALUE_LENGTH) {
      return `${label}的 value 必须是不超过 ${MAX_VALUE_LENGTH} 字符的字符串`;
    }
  }
  if (type === "stripSuffix" && action.pattern !== undefined) {
    if (typeof action.pattern !== "string") return `${label}的 pattern 必须是字符串`;
    const patternError = regexValidationError(action.pattern, counter, label);
    if (patternError) return patternError;
  }
  return "";
}

function ruleValidationError(rule, index, counter) {
  const label = `第 ${index + 1} 条规则`;
  if (!isPlainObject(rule)) return `${label}必须是 JSON 对象`;
  if (typeof rule.id !== "string" || rule.id.length < 1 || rule.id.length > 40) {
    return `${label}的 id 必须是 1–40 字符的字符串`;
  }
  if (rule.when !== undefined && !Array.isArray(rule.when)) return `${label}的 when 必须是数组`;
  const when = rule.when || [];
  if (when.length > MAX_CONDITIONS) return `${label}的条件不能超过 ${MAX_CONDITIONS} 个`;
  for (let conditionIndex = 0; conditionIndex < when.length; conditionIndex += 1) {
    const error = conditionValidationError(when[conditionIndex],
      `${label}第 ${conditionIndex + 1} 个条件`, counter);
    if (error) return error;
  }
  if (!Array.isArray(rule.then) || rule.then.length < 1 || rule.then.length > MAX_ACTIONS) {
    return `${label}的 then 必须是 1–${MAX_ACTIONS} 个动作`;
  }
  for (let actionIndex = 0; actionIndex < rule.then.length; actionIndex += 1) {
    const error = actionValidationError(rule.then[actionIndex],
      `${label}第 ${actionIndex + 1} 个动作`, counter);
    if (error) return error;
  }
  return "";
}

/**
 * 服务端独立校验规则包（不信任客户端），返回中文错误消息；合法时返回空串。
 * 与 docs/MATCH_RULES_MARKET_SPEC.md 第 1 节的客户端校验保持同一套限制。
 */
function validateRulePack(pack) {
  if (!isPlainObject(pack)) return "规则包必须是 JSON 对象";
  if (pack.schemaVersion !== 1) return "schemaVersion 必须为 1";
  const idError = packIdError(pack.id);
  if (idError) return idError;
  if (typeof pack.name !== "string" || pack.name.length < 1 || pack.name.length > 40) {
    return "name 必须是 1–40 字符的字符串";
  }
  if (!Number.isInteger(pack.version) || pack.version < 1 || pack.version > 9999) {
    return "version 必须是 1–9999 的整数";
  }
  if (pack.author !== undefined
    && (typeof pack.author !== "string" || pack.author.length > 40)) {
    return "author 必须是不超过 40 字符的字符串";
  }
  if (pack.description !== undefined
    && (typeof pack.description !== "string" || pack.description.length > 200)) {
    return "description 必须是不超过 200 字符的字符串";
  }
  if (pack.minVersionCode !== undefined
    && (!Number.isInteger(pack.minVersionCode) || pack.minVersionCode < 0
      || pack.minVersionCode > 999999)) {
    return "minVersionCode 必须是 0–999999 的整数";
  }
  const scopeError = scopeValidationError(pack.scope);
  if (scopeError) return scopeError;
  if (!Array.isArray(pack.rules) || pack.rules.length < 1 || pack.rules.length > MAX_RULES) {
    return `rules 必须是 1–${MAX_RULES} 条的数组`;
  }
  const counter = {count: 0};
  const ruleIds = new Set();
  for (let index = 0; index < pack.rules.length; index += 1) {
    const error = ruleValidationError(pack.rules[index], index, counter);
    if (error) return error;
    const ruleId = pack.rules[index].id;
    if (ruleIds.has(ruleId)) return `规则 id 重复：${ruleId}`;
    ruleIds.add(ruleId);
  }
  return "";
}

function readJsonFile(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (_error) {
    return null;
  }
}

function writeJsonFile(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), {recursive: true});
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`,
    {encoding: "utf8", mode: 0o600});
}

function appendJsonLine(filePath, entry) {
  fs.mkdirSync(path.dirname(filePath), {recursive: true});
  fs.appendFileSync(filePath, `${JSON.stringify(entry)}\n`, {encoding: "utf8", mode: 0o600});
}

function trimJsonLines(filePath, maximum) {
  let lines;
  try {
    lines = fs.readFileSync(filePath, "utf8").split(/\r?\n/).filter(Boolean);
  } catch (_error) {
    return;
  }
  if (lines.length <= maximum) return;
  fs.writeFileSync(filePath, `${lines.slice(-maximum).join("\n")}\n`, {encoding: "utf8"});
}

/** 模块自带的最小 readJson，只在调用方没有提供 helper 时使用（测试/独立运行）。 */
function fallbackReadJson(req, maximumBytes = 16 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let length = 0;
    let tooLarge = false;
    req.on("data", (chunk) => {
      length += chunk.length;
      if (length > maximumBytes) {
        tooLarge = true;
        chunks.length = 0;
      } else if (!tooLarge) {
        chunks.push(chunk);
      }
    });
    req.on("end", () => {
      if (tooLarge) { reject(new Error("request body is too large")); return; }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"));
      } catch (_error) {
        reject(new Error("invalid json"));
      }
    });
    req.on("error", reject);
  });
}

function etagMatches(header, etag) {
  const value = String(header || "").trim();
  if (!value) return false;
  return value.split(",").some((candidate) => {
    const trimmed = candidate.trim();
    if (trimmed === "*") return true;
    const normalized = trimmed.startsWith("W/") ? trimmed.slice(2) : trimmed;
    return normalized === etag;
  });
}

function toBytes(value) {
  return Buffer.byteLength(typeof value === "string" ? value : JSON.stringify(value));
}

/**
 * 创建匹配规则市场请求处理器。
 *
 * @param {object} options
 * @param {string} options.stateDir 服务端状态目录（规则写入 `<stateDir>/match-rules/`）。
 * @param {string} [options.adminToken] 管理接口令牌，请求头 `x-admin-token`。
 * @param {function|number} [options.now] 可注入的时钟（毫秒时间戳或返回时间戳的函数）。
 * @param {function} [options.requireAdmin] 可选：替代内置的管理员校验，返回布尔值。
 * @returns {function} `async (req, res, url, helpers) => boolean`，未命中返回 false。
 */
function createMatchRulesHandler(options = {}) {
  if (!options.stateDir) throw new Error("stateDir is required");
  const stateDir = path.resolve(String(options.stateDir));
  const adminToken = String(options.adminToken || "");
  const requireAdmin = typeof options.requireAdmin === "function" ? options.requireAdmin : null;
  const injectedNow = options.now;
  const rootDir = path.join(stateDir, "match-rules");
  const pendingDir = path.join(rootDir, "pending");
  const approvedDir = path.join(rootDir, "approved");
  const submissionsPath = path.join(rootDir, "submissions.jsonl");
  const reportsPath = path.join(rootDir, "reports.jsonl");
  fs.mkdirSync(pendingDir, {recursive: true});
  fs.mkdirSync(approvedDir, {recursive: true});

  /** @type {Map<string, object>} 已发布规则包（按提交记录 id 索引）。 */
  const approvedRecords = new Map();
  /** @type {Map<string, number[]>} 每个 IP 最近的成功提交时间。 */
  const submissionsByIp = new Map();
  let catalogGeneratedAt = 0;

  function nowMs() {
    let value;
    if (typeof injectedNow === "function") value = injectedNow();
    else if (injectedNow !== undefined) value = injectedNow;
    else value = Date.now();
    if (value instanceof Date) return value.getTime();
    const number = Number(value);
    return Number.isFinite(number) ? number : Date.now();
  }

  function pendingFile(id) { return path.join(pendingDir, `${id}.json`); }

  function approvedFile(id) { return path.join(approvedDir, `${id}.json`); }

  function readPendingRecords() {
    const records = [];
    let names = [];
    try {
      names = fs.readdirSync(pendingDir);
    } catch (_error) {
      return records;
    }
    for (const name of names) {
      if (!name.endsWith(".json")) continue;
      const record = readJsonFile(path.join(pendingDir, name));
      if (record && RECORD_ID_PATTERN.test(String(record.id || ""))) records.push(record);
    }
    return records;
  }

  function readPendingRecord(id) {
    return readJsonFile(pendingFile(id));
  }

  /** 扫描 approved/ 构建目录；审核操作后调用，generatedAt 随审核时间刷新。 */
  function refreshApproved() {
    approvedRecords.clear();
    let names = [];
    try {
      names = fs.readdirSync(approvedDir);
    } catch (_error) {
      names = [];
    }
    for (const name of names) {
      if (!name.endsWith(".json")) continue;
      const record = readJsonFile(path.join(approvedDir, name));
      if (!record) continue;
      if (!RECORD_ID_PATTERN.test(String(record.id || ""))) continue;
      if (record.status !== "approved") continue;
      if (!isPlainObject(record.rule)) continue;
      approvedRecords.set(record.id, record);
    }
    catalogGeneratedAt = nowMs();
  }

  function findRecord(id) {
    const pending = readPendingRecord(id);
    if (pending) return pending;
    return approvedRecords.get(id) || null;
  }

  function newRecordId() {
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const id = `r-${crypto.randomBytes(4).toString("hex")}`;
      if (!fs.existsSync(pendingFile(id)) && !fs.existsSync(approvedFile(id))) return id;
    }
    throw new Error("failed to allocate submission id");
  }

  function tokenMatches(record, token) {
    const expected = String(record.tokenHash || "");
    if (!expected) return false;
    const actual = sha256Hex(String(token || ""));
    const expectedBytes = Buffer.from(expected, "hex");
    const actualBytes = Buffer.from(actual, "hex");
    return expectedBytes.length === actualBytes.length
      && crypto.timingSafeEqual(expectedBytes, actualBytes);
  }

  function adminAllowed(req) {
    if (requireAdmin) {
      try { return Boolean(requireAdmin(req)); } catch (_error) { return false; }
    }
    if (!adminToken) return false;
    const provided = String(req.headers["x-admin-token"] || "");
    const expectedBytes = Buffer.from(adminToken);
    const providedBytes = Buffer.from(provided);
    return expectedBytes.length === providedBytes.length
      && crypto.timingSafeEqual(expectedBytes, providedBytes);
  }

  /** 同一 rule.id 的版本必须递增（pending 或 approved 中已有的最大版本为基准）。 */
  function versionConflict(ruleId, version) {
    let maximum = 0;
    for (const record of readPendingRecords()) {
      if (record.status !== "pending") continue;
      if (!record.rule || record.rule.id !== ruleId) continue;
      maximum = Math.max(maximum, Number(record.rule.version) || 0);
    }
    for (const record of approvedRecords.values()) {
      if (record.status !== "approved") continue;
      if (record.rule.id !== ruleId) continue;
      maximum = Math.max(maximum, Number(record.rule.version) || 0);
    }
    return maximum >= version ? maximum : 0;
  }

  function rateLimited(ip) {
    const nowValue = nowMs();
    const recent = (submissionsByIp.get(ip) || [])
      .filter((time) => nowValue - time < RATE_WINDOW_MS);
    submissionsByIp.set(ip, recent);
    return recent.length >= MAX_SUBMISSIONS_PER_HOUR;
  }

  function recordSubmission(ip) {
    const nowValue = nowMs();
    const recent = (submissionsByIp.get(ip) || [])
      .filter((time) => nowValue - time < RATE_WINDOW_MS);
    recent.push(nowValue);
    submissionsByIp.set(ip, recent);
    if (submissionsByIp.size > 5000) {
      for (const [key, times] of submissionsByIp) {
        if (times.length === 0 || nowValue - times[times.length - 1] >= RATE_WINDOW_MS) {
          submissionsByIp.delete(key);
        }
      }
    }
  }

  function writeBody(res, status, body, headers = {}) {
    res.writeHead(status, {
      "content-type": "application/json; charset=utf-8",
      "content-length": body.length,
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      ...headers,
    });
    res.end(body);
  }

  /** 每个请求一个响应器：普通 JSON 走 server.js 的 sendJson，需要缓存头时自己写。 */
  function createSender(res, sendJsonHelper) {
    return {
      json(status, value, headers = {}) {
        const hasExtraHeaders = Object.keys(headers).length > 0;
        if (sendJsonHelper && !hasExtraHeaders) {
          try { res.setHeader("x-content-type-options", "nosniff"); } catch (_error) { /* ignore */ }
          sendJsonHelper(res, status, value);
          return;
        }
        writeBody(res, status, Buffer.from(`${JSON.stringify(value, null, 2)}\n`), headers);
      },
      raw(status, body, headers = {}) {
        writeBody(res, status, body, headers);
      },
      empty(status, headers = {}) {
        res.writeHead(status, {"x-content-type-options": "nosniff", ...headers});
        res.end();
      },
      notFound() {
        this.json(404, {ok: false, error: "规则包不存在"});
      },
    };
  }

  function catalogPayload() {
    const latestByRuleId = new Map();
    for (const record of approvedRecords.values()) {
      if (record.status !== "approved") continue;
      if (!isPlainObject(record.rule)) continue;
      const key = String(record.rule.id || record.id);
      const current = latestByRuleId.get(key);
      if (!current || Number(record.rule.version) > Number(current.rule.version)) {
        latestByRuleId.set(key, record);
      }
    }
    const packages = (record) => {
      const scope = isPlainObject(record.rule.scope) ? record.rule.scope : {};
      return Array.isArray(scope.packages) ? scope.packages.slice(0, MAX_SCOPE_PACKAGES) : [];
    };
    const rules = [...latestByRuleId.values()].map((record) => ({
      id: record.id,
      name: String(record.rule.name || ""),
      version: Number(record.rule.version) || 1,
      author: String(record.author || ""),
      description: String(record.description || ""),
      sha256: String(record.sha256 || ""),
      size: Number(record.size) || 0,
      updatedAt: String(record.updatedAt || ""),
      downloads: Number(record.downloads) || 0,
      packages: packages(record),
    }));
    rules.sort((left, right) => {
      const leftTime = String(left.updatedAt || "");
      const rightTime = String(right.updatedAt || "");
      if (leftTime !== rightTime) return leftTime < rightTime ? 1 : -1;
      return left.id.localeCompare(right.id);
    });
    return {
      ok: true,
      schemaVersion: 1,
      generatedAt: currentIso(catalogGeneratedAt),
      rules,
    };
  }

  function adminItems(filter) {
    const records = new Map();
    for (const record of readPendingRecords()) records.set(record.id, record);
    for (const record of approvedRecords.values()) {
      if (record.status === "approved") records.set(record.id, record);
    }
    let entries = [...records.values()];
    if (filter === "pending") entries = entries.filter((record) => record.status === "pending");
    else if (filter === "approved") entries = entries.filter((record) => record.status === "approved");
    entries.sort((left, right) => String(right.submittedAt || "")
      .localeCompare(String(left.submittedAt || "")));
    return entries.map((record) => ({
      id: record.id,
      status: record.status,
      submittedAt: String(record.submittedAt || ""),
      updatedAt: String(record.updatedAt || ""),
      author: String(record.author || ""),
      description: String(record.description || ""),
      rule: record.rule,
    }));
  }

  async function handleSubmit(req, res, url, helpers, sender) {
    let body;
    try {
      body = await helpers.readJson(req, REQUEST_MAX_BYTES);
    } catch (_error) {
      sender.json(400, {ok: false, error: "请求体必须是合法的 JSON"});
      return true;
    }
    if (!isPlainObject(body)) {
      sender.json(400, {ok: false, error: "请求体必须是 JSON 对象"});
      return true;
    }
    const rule = body.rule;
    if (body.description !== undefined && typeof body.description !== "string") {
      sender.json(400, {ok: false, error: "description 必须是字符串"});
      return true;
    }
    if (body.author !== undefined && typeof body.author !== "string") {
      sender.json(400, {ok: false, error: "author 必须是字符串"});
      return true;
    }
    if (!rule) {
      sender.json(400, {ok: false, error: "缺少 rule 规则包对象"});
      return true;
    }
    let serialized;
    try {
      serialized = JSON.stringify(rule);
    } catch (_error) {
      sender.json(400, {ok: false, error: "规则包无法序列化"});
      return true;
    }
    if (serialized === undefined || toBytes(serialized) > PACK_MAX_BYTES) {
      sender.json(400, {ok: false, error: "规则包不能超过 64KB"});
      return true;
    }
    const validationError = validateRulePack(rule);
    if (validationError) {
      sender.json(400, {ok: false, error: validationError});
      return true;
    }
    const ip = String(helpers.clientIp(req) || "unknown").trim() || "unknown";
    if (rateLimited(ip)) {
      sender.json(429, {ok: false, error: "提交过于频繁：同一 IP 每小时最多提交 5 次，请稍后再试"});
      return true;
    }
    const conflictVersion = versionConflict(rule.id, rule.version);
    if (conflictVersion) {
      sender.json(409, {
        ok: false,
        error: `同一规则包已存在 v${conflictVersion}，新提交的 version 必须大于 ${conflictVersion}`,
      });
      return true;
    }
    const author = String((body.author !== undefined && body.author !== ""
      ? body.author : (rule.author || ""))).trim().slice(0, 40);
    const description = String((body.description !== undefined && body.description !== ""
      ? body.description : (rule.description || ""))).trim().slice(0, 200);
    const submittedAt = nowMs();
    const id = newRecordId();
    const token = crypto.randomBytes(16).toString("hex");
    const record = {
      id,
      status: "pending",
      rule,
      sha256: sha256Hex(serialized),
      size: toBytes(serialized),
      submittedAt: currentIso(submittedAt),
      updatedAt: currentIso(submittedAt),
      author,
      description,
      tokenHash: sha256Hex(token),
      note: "",
    };
    try {
      writeJsonFile(pendingFile(id), record);
      appendJsonLine(submissionsPath, {
        id,
        ruleId: String(rule.id || ""),
        version: Number(rule.version) || 0,
        author,
        submittedAt: record.submittedAt,
      });
    } catch (error) {
      sender.json(500, {ok: false, error: "规则包保存失败"});
      return true;
    }
    recordSubmission(ip);
    sender.json(201, {ok: true, id, token, status: "pending"});
    return true;
  }

  async function handleReport(req, res, url, helpers, sender) {
    let body;
    try {
      body = await helpers.readJson(req, REQUEST_MAX_BYTES);
    } catch (_error) {
      sender.json(400, {ok: false, error: "请求体必须是合法的 JSON"});
      return true;
    }
    if (!isPlainObject(body)) {
      sender.json(400, {ok: false, error: "请求体必须是 JSON 对象"});
      return true;
    }
    const id = String(body.id || "").trim();
    if (!RECORD_ID_PATTERN.test(id)) {
      sender.json(400, {ok: false, error: "规则 id 不合法"});
      return true;
    }
    const reason = typeof body.reason === "string" ? body.reason.trim() : "";
    if (reason.length < 1 || reason.length > 200) {
      sender.json(400, {ok: false, error: "reason 必须是 1–200 字符的字符串"});
      return true;
    }
    if (!findRecord(id)) {
      sender.json(404, {ok: false, error: "规则不存在"});
      return true;
    }
    try {
      appendJsonLine(reportsPath, {
        id,
        reason,
        createdAt: currentIso(nowMs()),
      });
      trimJsonLines(reportsPath, REPORTS_MAX);
    } catch (_error) {
      sender.json(500, {ok: false, error: "举报保存失败"});
      return true;
    }
    sender.json(200, {ok: true});
    return true;
  }

  async function handleReview(req, res, url, helpers, sender) {
    let body;
    try {
      body = await helpers.readJson(req, REQUEST_MAX_BYTES);
    } catch (_error) {
      sender.json(400, {ok: false, error: "请求体必须是合法的 JSON"});
      return true;
    }
    if (!isPlainObject(body)) {
      sender.json(400, {ok: false, error: "请求体必须是 JSON 对象"});
      return true;
    }
    const id = String(body.id || "").trim();
    if (!RECORD_ID_PATTERN.test(id)) {
      sender.json(400, {ok: false, error: "规则 id 不合法"});
      return true;
    }
    const action = String(body.action || "");
    if (!["approve", "reject", "disable"].includes(action)) {
      sender.json(400, {ok: false, error: "action 只能是 approve、reject 或 disable"});
      return true;
    }
    const note = body.note === undefined ? "" : String(body.note).trim().slice(0, 300);
    const pending = readPendingRecord(id);
    const approved = approvedRecords.get(id) || null;
    const rule = pending ? pending.rule : (approved ? approved.rule : null);
    if (!rule) {
      sender.json(404, {ok: false, error: "规则不存在"});
      return true;
    }
    const nowValue = nowMs();
    const updatedAt = currentIso(nowValue);
    // approve 前重新校验整包：客户端校验不可信，pending 文件也可能被人工改过。
    if (action === "approve") {
      const validationError = validateRulePack(rule);
      if (validationError) {
        sender.json(400, {ok: false, error: `规则包校验失败：${validationError}`});
        return true;
      }
      let serialized;
      try {
        serialized = JSON.stringify(rule);
      } catch (_error) {
        sender.json(400, {ok: false, error: "规则包无法序列化"});
        return true;
      }
      if (toBytes(serialized) > PACK_MAX_BYTES) {
        sender.json(400, {ok: false, error: "规则包不能超过 64KB"});
        return true;
      }
      const ruleId = String(rule.id || "");
      const version = Number(rule.version) || 0;
      for (const other of approvedRecords.values()) {
        if (other.id === id || other.status !== "approved") continue;
        if (String(other.rule.id || "") !== ruleId) continue;
        if (Number(other.rule.version) >= version) {
          sender.json(409, {
            ok: false,
            error: `已发布版本 v${other.rule.version}，新版本号必须更大`,
          });
          return true;
        }
      }
      const submittedAt = String((pending && pending.submittedAt)
        || (approved && approved.submittedAt) || updatedAt);
      const record = {
        id,
        status: "approved",
        rule,
        sha256: sha256Hex(serialized),
        size: toBytes(serialized),
        submittedAt,
        updatedAt,
        downloads: Number((approved && approved.downloads) || 0),
        author: String((pending && pending.author) || (approved && approved.author) || ""),
        description: String((pending && pending.description)
          || (approved && approved.description) || ""),
      };
      try {
        writeJsonFile(approvedFile(id), record);
        writeJsonFile(pendingFile(id), {
          ...(pending || {}),
          id,
          status: "approved",
          rule,
          sha256: record.sha256,
          size: record.size,
          submittedAt,
          updatedAt,
          author: record.author,
          description: record.description,
          note,
          tokenHash: String((pending && pending.tokenHash) || ""),
        });
      } catch (_error) {
        sender.json(500, {ok: false, error: "审核结果保存失败"});
        return true;
      }
      refreshApproved();
      sender.json(200, {ok: true, id, status: "approved"});
      return true;
    }
    const status = action === "reject" ? "rejected" : "disabled";
    try {
      if (approved) {
        fs.rmSync(approvedFile(id), {force: true});
        refreshApproved();
      }
      writeJsonFile(pendingFile(id), {
        id,
        status,
        rule,
        sha256: String((pending && pending.sha256) || (approved && approved.sha256) || ""),
        size: Number((pending && pending.size) || (approved && approved.size) || 0),
        submittedAt: String((pending && pending.submittedAt)
          || (approved && approved.submittedAt) || updatedAt),
        updatedAt,
        author: String((pending && pending.author) || (approved && approved.author) || ""),
        description: String((pending && pending.description)
          || (approved && approved.description) || ""),
        note,
        tokenHash: String((pending && pending.tokenHash) || ""),
      });
    } catch (_error) {
      sender.json(500, {ok: false, error: "审核结果保存失败"});
      return true;
    }
    sender.json(200, {ok: true, id, status});
    return true;
  }

  function handleCatalog(req, res, sender) {
    const payload = catalogPayload();
    const body = Buffer.from(`${JSON.stringify(payload, null, 2)}\n`);
    const etag = `"${sha256Hex(body).slice(0, 32)}"`;
    const headers = {etag, "cache-control": "public, max-age=300"};
    if (etagMatches(req.headers["if-none-match"], etag)) {
      sender.empty(304, headers);
      return true;
    }
    sender.raw(200, body, headers);
    return true;
  }

  function handlePack(req, res, url, sender) {
    const route = url.pathname.match(/^\/api\/match-rules\/packs\/([^/]+)\/(\d+)\.json$/);
    if (!route) return false;
    const id = route[1];
    const version = Number(route[2]);
    if (!RECORD_ID_PATTERN.test(id)) {
      sender.notFound();
      return true;
    }
    const record = approvedRecords.get(id);
    if (!record || record.status !== "approved"
      || Number(record.rule.version) !== version) {
      sender.notFound();
      return true;
    }
    const etag = `"${String(record.sha256 || "").slice(0, 32)}"`;
    const headers = {etag, "cache-control": "public, max-age=86400"};
    if (etagMatches(req.headers["if-none-match"], etag)) {
      sender.empty(304, headers);
      return true;
    }
    record.downloads = (Number(record.downloads) || 0) + 1;
    try {
      writeJsonFile(approvedFile(id), record);
    } catch (_error) {
      // 下载计数写失败不影响返回规则正文。
    }
    sender.json(200, {
      ok: true,
      id,
      version,
      sha256: String(record.sha256 || ""),
      rule: record.rule,
    }, headers);
    return true;
  }

  function handleStatus(req, res, url, sender) {
    const id = String(url.searchParams.get("id") || "").trim();
    const token = String(url.searchParams.get("token") || "");
    if (!RECORD_ID_PATTERN.test(id)) {
      sender.json(404, {ok: false, error: "提交记录不存在"});
      return true;
    }
    const record = findRecord(id);
    if (!record) {
      sender.json(404, {ok: false, error: "提交记录不存在"});
      return true;
    }
    if (!tokenMatches(record, token)) {
      sender.json(403, {ok: false, error: "token 不匹配"});
      return true;
    }
    sender.json(200, {
      ok: true,
      id: record.id,
      status: record.status,
      note: String(record.note || ""),
      updatedAt: String(record.updatedAt || ""),
    });
    return true;
  }

  async function handler(req, res, url, helpers = {}) {
    const pathname = url && typeof url.pathname === "string" ? url.pathname : "";
    if (pathname !== API_PREFIX && !pathname.startsWith(`${API_PREFIX}/`)) return false;
    const method = String(req.method || "").toUpperCase();
    const sendJsonHelper = typeof helpers.sendJson === "function" ? helpers.sendJson : null;
    const readJsonHelper = typeof helpers.readJson === "function"
      ? helpers.readJson : fallbackReadJson;
    const clientIpHelper = typeof helpers.clientIp === "function"
      ? helpers.clientIp : (request) => String((request.socket && request.socket.remoteAddress)
        || "unknown");
    const sender = createSender(res, sendJsonHelper);
    const context = {
      sendJson: sendJsonHelper,
      readJson: readJsonHelper,
      clientIp: clientIpHelper,
    };
    const methodNotAllowed = (allowed) => {
      sender.json(405, {ok: false, error: "method not allowed"}, {allow: allowed});
      return true;
    };
    try {
      if (pathname === `${API_PREFIX}/submit`) {
        return method === "POST" ? handleSubmit(req, res, url, context, sender)
          : methodNotAllowed("POST");
      }
      if (pathname === `${API_PREFIX}/catalog.json`) {
        return method === "GET" ? handleCatalog(req, res, sender) : methodNotAllowed("GET");
      }
      if (pathname === `${API_PREFIX}/status`) {
        return method === "GET" ? handleStatus(req, res, url, sender) : methodNotAllowed("GET");
      }
      if (pathname === `${API_PREFIX}/report`) {
        return method === "POST" ? handleReport(req, res, url, context, sender)
          : methodNotAllowed("POST");
      }
      if (pathname === `${API_PREFIX}/admin/list`) {
        if (method !== "GET") return methodNotAllowed("GET");
        if (!adminAllowed(req)) {
          sender.json(401, {ok: false, error: "admin authentication required"});
          return true;
        }
        const status = String(url.searchParams.get("status") || "all");
        if (!["pending", "approved", "all"].includes(status)) {
          sender.json(400, {ok: false, error: "status 只能是 pending、approved 或 all"});
          return true;
        }
        sender.json(200, {ok: true, items: adminItems(status)});
        return true;
      }
      if (pathname === `${API_PREFIX}/admin/review`) {
        if (method !== "POST") return methodNotAllowed("POST");
        if (!adminAllowed(req)) {
          sender.json(401, {ok: false, error: "admin authentication required"});
          return true;
        }
        return handleReview(req, res, url, context, sender);
      }
      if (pathname.startsWith(`${API_PREFIX}/packs/`)) {
        if (method !== "GET") return methodNotAllowed("GET");
        if (handlePack(req, res, url, sender)) return true;
        sender.notFound();
        return true;
      }
      return false;
    } catch (_error) {
      if (!res.headersSent) {
        sender.json(500, {ok: false, error: "服务器内部错误"});
      } else if (!res.writableEnded) {
        res.end();
      }
      return true;
    }
  }

  refreshApproved();
  return handler;
}

module.exports = {createMatchRulesHandler};
