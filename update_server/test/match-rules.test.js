"use strict";

// 匹配规则市场服务端测试（issue #174）。
//
// 使用独立的 http.createServer + 临时 STATE_DIR 直接驱动 match-rules.js，
// 不依赖 server.js（当前工作区里 server.js 的依赖不完整，无法整体启动）。

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const {test} = require("node:test");

const {createMatchRulesHandler} = require("../match-rules");

const ADMIN_TOKEN = "test-admin-token-174";
const RULE_DIR_NAME = "match-rules";

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function jsonBody(value) {
  return Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
}

/** 启动一个只挂载 match-rules handler 的测试服务，返回 baseUrl 与清理函数。 */
async function startHarness(options = {}) {
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-match-rules-test-"));
  const handler = createMatchRulesHandler({
    stateDir,
    adminToken: ADMIN_TOKEN,
    now: options.now,
  });
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || "127.0.0.1"}`);
    const helpers = {
      sendJson(response, status, value) {
        const body = jsonBody(value);
        response.writeHead(status, {
          "content-type": "application/json; charset=utf-8",
          "content-length": body.length,
          "cache-control": "no-store",
        });
        response.end(body);
      },
      readJson(request, maximumBytes = 16 * 1024) {
        return new Promise((resolve, reject) => {
          const chunks = [];
          let length = 0;
          let tooLarge = false;
          request.on("data", (chunk) => {
            length += chunk.length;
            if (length > maximumBytes) {
              tooLarge = true;
              chunks.length = 0;
            } else if (!tooLarge) {
              chunks.push(chunk);
            }
          });
          request.on("end", () => {
            if (tooLarge) { reject(new Error("request body is too large")); return; }
            try {
              resolve(JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"));
            } catch (_error) {
              reject(new Error("invalid json"));
            }
          });
          request.on("error", reject);
        });
      },
      clientIp() {
        return "127.0.0.1";
      },
    };
    const handled = await handler(req, res, url, helpers);
    if (!handled) {
      res.writeHead(404, {"content-type": "text/plain; charset=utf-8"});
      res.end("not found");
    }
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  return {
    stateDir,
    handler,
    baseUrl: `http://127.0.0.1:${port}`,
    rulesDir: path.join(stateDir, RULE_DIR_NAME),
    async request(pathname, options = {}) {
      const headers = {...(options.headers || {})};
      let body;
      if (options.raw !== undefined) {
        body = options.raw;
        if (headers["content-type"] === undefined) {
          headers["content-type"] = "application/json";
        }
      } else if (options.body !== undefined) {
        body = JSON.stringify(options.body);
        headers["content-type"] = "application/json";
      }
      const response = await fetch(`${this.baseUrl}${pathname}`, {
        method: options.method || "GET",
        headers,
        body,
      });
      const text = await response.text();
      let payload = null;
      if (text) {
        try { payload = JSON.parse(text); } catch (_error) { payload = null; }
      }
      return {status: response.status, headers: response.headers, payload, text};
    },
    async close() {
      // node 内置 fetch 会保持 keep-alive 连接，先断开再等 close，否则测试会挂住。
      if (typeof server.closeAllConnections === "function") server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
      fs.rmSync(stateDir, {recursive: true, force: true});
    },
  };
}

function samplePack(overrides = {}) {
  return {
    schemaVersion: 1,
    id: "echo-split-artist-title",
    name: "回声：拆分歌手与歌名",
    version: 1,
    author: "测试作者",
    description: "把「歌手 - 歌名」拆成两段",
    minVersionCode: 12,
    scope: {
      packages: ["com.example.echo", "com.example.player"],
      sources: ["netease", "qqmusic"],
    },
    rules: [
      {
        id: "split-dash",
        when: [
          {field: "title", op: "regex", value: "(.{1,40})\\s*[-–—]\\s*(.{1,80})"},
          {field: "artist", op: "equalsAny", value: ["车载音乐", "未知歌手", "群星"]},
        ],
        then: [{
          type: "extract",
          pattern: "(.{1,40})\\s*[-–—]\\s*(.{1,80})",
          targets: {artist: 1, title: 2},
        }],
      },
      {
        id: "trim-title",
        when: [{field: "title", op: "endsWith", value: " 官方版"}],
        then: [{type: "stripSuffix", pattern: "\\s*官方版\\s*$"}],
      },
    ],
    ...overrides,
  };
}

function minimalPack(id, version = 1) {
  return {
    schemaVersion: 1,
    id,
    name: "测试规则包",
    version,
    rules: [
      {
        id: "only-rule",
        when: [{field: "title", op: "contains", value: "测试"}],
        then: [{type: "trim", field: "title"}],
      },
    ],
  };
}

function pendingFiles(rulesDir) {
  const dir = path.join(rulesDir, "pending");
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((name) => name.endsWith(".json"));
}

function approvedFiles(rulesDir) {
  const dir = path.join(rulesDir, "approved");
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((name) => name.endsWith(".json"));
}

async function submit(harness, pack, extra = {}) {
  return harness.request("/api/match-rules/submit", {
    method: "POST",
    body: {rule: pack, ...extra},
  });
}

async function adminReview(harness, body, token = ADMIN_TOKEN) {
  return harness.request("/api/match-rules/admin/review", {
    method: "POST",
    headers: token === null ? {} : {"x-admin-token": token},
    body,
  });
}

test("提交合法规则包返回 201、写入 pending 记录与 submissions.jsonl", async () => {
  const harness = await startHarness({now: () => Date.parse("2026-11-21T10:00:00.000Z")});
  try {
    const pack = samplePack({futureField: "向后兼容的未知字段应被忽略"});
    const result = await submit(harness, pack, {author: "提交者甲", description: "市场提交说明"});
    assert.equal(result.status, 201);
    assert.equal(result.headers.get("x-content-type-options"), "nosniff");
    assert.equal(result.payload.ok, true);
    assert.match(result.payload.id, /^r-[a-f0-9]{8}$/);
    assert.match(result.payload.token, /^[a-f0-9]{32}$/);
    assert.equal(result.payload.status, "pending");

    const pendingPath = path.join(harness.rulesDir, "pending", `${result.payload.id}.json`);
    assert.equal(fs.existsSync(pendingPath), true);
    const record = JSON.parse(fs.readFileSync(pendingPath, "utf8"));
    assert.equal(record.id, result.payload.id);
    assert.equal(record.status, "pending");
    assert.equal(record.rule.id, pack.id);
    assert.equal(record.rule.version, 1);
    assert.equal(record.author, "提交者甲");
    assert.equal(record.description, "市场提交说明");
    assert.equal(record.submittedAt, "2026-11-21T10:00:00.000Z");
    assert.equal(record.tokenHash, sha256Hex(result.payload.token));
    assert.equal(String(record.tokenHash).length, 64);

    const submissions = fs.readFileSync(path.join(harness.rulesDir, "submissions.jsonl"), "utf8")
      .split(/\r?\n/).filter(Boolean);
    assert.equal(submissions.length, 1);
    assert.equal(JSON.parse(submissions[0]).id, result.payload.id);

    // 审核前的目录必须看不到这个包。
    const catalog = await harness.request("/api/match-rules/catalog.json");
    assert.equal(catalog.status, 200);
    assert.equal(catalog.payload.ok, true);
    assert.equal(catalog.payload.schemaVersion, 1);
    assert.deepEqual(catalog.payload.rules, []);
    assert.equal(approvedFiles(harness.rulesDir).length, 0);

    // 未命中路由交还给上层，方法错误返回 405。
    const unknown = await harness.request("/api/match-rules/not-a-route");
    assert.equal(unknown.status, 404);
    assert.equal(unknown.text, "not found");
    const wrongMethod = await harness.request("/api/match-rules/submit");
    assert.equal(wrongMethod.status, 405);
    assert.equal(wrongMethod.payload.ok, false);
    assert.equal(wrongMethod.headers.get("allow"), "POST");
  } finally {
    await harness.close();
  }
});

test("非法规则包一律返回 400 且不落盘", async () => {
  const harness = await startHarness();
  try {
    const invalidPacks = [
      ["schemaVersion 错误", samplePack({schemaVersion: 2})],
      ["id 不合法", samplePack({id: "Bad_ID"})],
      ["name 超长", samplePack({name: "长".repeat(41)})],
      ["version 越界", samplePack({version: 10000})],
      ["description 类型错误", samplePack({description: 123})],
      ["scope 包名非法", samplePack({scope: {packages: ["bad package!"]}})],
      ["scope 来源非法", samplePack({scope: {sources: ["unknown-source"]}})],
      ["rules 为空", samplePack({rules: []})],
      ["规则 id 重复", samplePack({
        rules: [
          {id: "dup", when: [], then: [{type: "trim", field: "title"}]},
          {id: "dup", when: [], then: [{type: "trim", field: "title"}]},
        ],
      })],
      ["未知动作", samplePack({
        rules: [{id: "rename", when: [], then: [{type: "rename", field: "title"}]}],
      })],
      ["动作用 op 而非 type", samplePack({
        rules: [{id: "old-op", when: [], then: [{op: "trim", field: "title"}]}],
      })],
      ["命名捕获组", samplePack({
        rules: [{
          id: "named-group",
          when: [],
          then: [{type: "extract", pattern: "(?<name>.+)", targets: {title: 1}}],
        }],
      })],
      ["反向引用", samplePack({
        rules: [{
          id: "backref",
          when: [{field: "title", op: "regex", value: "(a)\\1"}],
          then: [{type: "trim", field: "title"}],
        }],
      })],
      ["嵌套量词", samplePack({
        rules: [{
          id: "nested",
          when: [{field: "title", op: "regex", value: "(.*)+"}],
          then: [{type: "trim", field: "title"}],
        }],
      })],
      ["extract targets 组号越界", samplePack({
        rules: [{
          id: "bad-target",
          when: [],
          then: [{type: "extract", pattern: "(a)", targets: {title: 10}}],
        }],
      })],
      ["动作作用到 package", samplePack({
        rules: [{id: "bad-field", when: [], then: [{type: "set", field: "package", value: "x"}]}],
      })],
      ["条件 op 非法", samplePack({
        rules: [{
          id: "bad-op",
          when: [{field: "title", op: "matches", value: "x"}],
          then: [{type: "trim", field: "title"}],
        }],
      })],
      ["超长规则包（>64KB）", (() => {
        const rules = [];
        for (let index = 0; index < 20; index += 1) {
          const map = {};
          for (let entry = 0; entry < 50; entry += 1) {
            map[`${index}-${entry}`.padEnd(40, "k")] = `${index}-${entry}`.padEnd(40, "v");
          }
          rules.push({id: `alias-${index}`, then: [{type: "aliasArtist", map}]});
        }
        return samplePack({id: "oversized-rule-pack", version: 2, rules});
      })()],
    ];
    for (const [label, pack] of invalidPacks) {
      const result = await submit(harness, pack);
      assert.equal(result.status, 400, `${label} 应返回 400`);
      assert.equal(result.payload.ok, false, `${label} 应返回 ok:false`);
      assert.equal(typeof result.payload.error, "string");
      assert.ok(result.payload.error.length > 0);
      assert.equal(result.headers.get("x-content-type-options"), "nosniff");
    }
    const oversized = invalidPacks[invalidPacks.length - 1][1];
    assert.ok(Buffer.byteLength(JSON.stringify(oversized)) > 64 * 1024);

    const invalidJson = await harness.request("/api/match-rules/submit", {
      method: "POST",
      raw: "{not json",
    });
    assert.equal(invalidJson.status, 400);
    assert.equal(invalidJson.payload.ok, false);

    assert.deepEqual(pendingFiles(harness.rulesDir), []);
    assert.deepEqual(approvedFiles(harness.rulesDir), []);
  } finally {
    await harness.close();
  }
});

test("同一 IP 每小时最多 5 次提交，第 6 次返回 429，窗口过后恢复", async () => {
  let clock = Date.parse("2026-11-21T08:00:00.000Z");
  const harness = await startHarness({now: () => clock});
  try {
    for (let index = 0; index < 5; index += 1) {
      const result = await submit(harness, minimalPack(`rate-pack-${index}`, 1));
      assert.equal(result.status, 201, `第 ${index + 1} 次提交应成功`);
    }
    const limited = await submit(harness, minimalPack("rate-pack-6", 1));
    assert.equal(limited.status, 429);
    assert.equal(limited.payload.ok, false);
    assert.match(limited.payload.error, /每小时/);
    assert.equal(pendingFiles(harness.rulesDir).length, 5);

    clock += 61 * 60 * 1000;
    const recovered = await submit(harness, minimalPack("rate-pack-6", 1));
    assert.equal(recovered.status, 201);
  } finally {
    await harness.close();
  }
});

test("匹配目录在校验通过前不可见；approve 后可见，ETag 304，下载 sha256 一致", async () => {
  const harness = await startHarness();
  try {
    const pack = samplePack();
    const submitted = await submit(harness, pack);
    assert.equal(submitted.status, 201);
    const id = submitted.payload.id;
    const token = submitted.payload.token;

    // 审核前：目录为空、下载 404。
    const before = await harness.request("/api/match-rules/catalog.json");
    assert.equal(before.status, 200);
    assert.deepEqual(before.payload.rules, []);
    const missing = await harness.request(`/api/match-rules/packs/${id}/1.json`);
    assert.equal(missing.status, 404);
    assert.equal(missing.payload.ok, false);
    assert.equal(missing.headers.get("x-content-type-options"), "nosniff");

    // 审核通过。
    const approved = await adminReview(harness, {
      id,
      action: "approve",
      note: "规则合理，通过",
    });
    assert.equal(approved.status, 200);
    assert.equal(approved.payload.ok, true);
    assert.equal(approved.payload.id, id);
    assert.equal(approved.payload.status, "approved");

    const approvedPath = path.join(harness.rulesDir, "approved", `${id}.json`);
    assert.equal(fs.existsSync(approvedPath), true);
    const approvedRecord = JSON.parse(fs.readFileSync(approvedPath, "utf8"));
    assert.equal(approvedRecord.id, id);
    assert.equal(approvedRecord.status, "approved");
    assert.equal(approvedRecord.sha256, sha256Hex(JSON.stringify(pack)));
    assert.equal(approvedRecord.size, Buffer.byteLength(JSON.stringify(pack)));
    assert.equal(approvedRecord.downloads, 0);
    assert.equal(approvedRecord.author, "测试作者");
    assert.equal(fs.readFileSync(approvedPath, "utf8").includes("tokenHash"), false);

    // 目录出现该包，且带缓存头与 ETag。
    const catalog = await harness.request("/api/match-rules/catalog.json");
    assert.equal(catalog.status, 200);
    assert.equal(catalog.headers.get("cache-control"), "public, max-age=300");
    assert.equal(catalog.headers.get("x-content-type-options"), "nosniff");
    const etag = catalog.headers.get("etag");
    assert.match(etag, /^"[a-f0-9]{32}"$/);
    assert.equal(catalog.payload.rules.length, 1);
    const entry = catalog.payload.rules[0];
    assert.equal(entry.id, id);
    assert.equal(entry.name, pack.name);
    assert.equal(entry.version, 1);
    assert.equal(entry.author, "测试作者");
    assert.equal(entry.sha256, approvedRecord.sha256);
    assert.equal(entry.size, approvedRecord.size);
    assert.deepEqual(entry.packages, pack.scope.packages);

    // If-None-Match 命中则 304。
    const notModified = await harness.request("/api/match-rules/catalog.json", {
      headers: {"if-none-match": etag},
    });
    assert.equal(notModified.status, 304);
    assert.equal(notModified.text, "");

    // 下载规则包，sha256 与目录/文件一致。
    const download = await harness.request(`/api/match-rules/packs/${id}/1.json`);
    assert.equal(download.status, 200);
    assert.equal(download.headers.get("cache-control"), "public, max-age=86400");
    assert.equal(download.payload.ok, true);
    assert.equal(download.payload.id, id);
    assert.equal(download.payload.version, 1);
    assert.deepEqual(download.payload.rule, pack);
    assert.equal(download.payload.sha256, approvedRecord.sha256);
    assert.equal(download.payload.sha256, sha256Hex(JSON.stringify(download.payload.rule)));

    // 响应必须是紧凑 JSON：客户端按响应原文里 "rule" 子串校验 SHA-256，
    // pretty-print 会让子串带缩进而校验失败。
    const ruleKeyIndex = download.text.indexOf('"rule"');
    assert.ok(ruleKeyIndex > 0);
    const ruleStart = download.text.indexOf("{", download.text.indexOf(":", ruleKeyIndex));
    let depth = 0;
    let inString = false;
    let escaped = false;
    let ruleEnd = -1;
    for (let index = ruleStart; index < download.text.length; index += 1) {
      const char = download.text[index];
      if (inString) {
        if (escaped) escaped = false;
        else if (char === "\\") escaped = true;
        else if (char === '"') inString = false;
        continue;
      }
      if (char === '"') inString = true;
      else if (char === "{") depth += 1;
      else if (char === "}") {
        depth -= 1;
        if (depth === 0) {
          ruleEnd = index + 1;
          break;
        }
      }
    }
    const rawRule = download.text.slice(ruleStart, ruleEnd);
    assert.equal(rawRule, JSON.stringify(download.payload.rule));
    assert.equal(sha256Hex(rawRule), download.payload.sha256);

    const downloadEtag = download.headers.get("etag");
    assert.match(downloadEtag, /^"[a-f0-9]{32}"$/);
    const cachedDownload = await harness.request(`/api/match-rules/packs/${id}/1.json`, {
      headers: {"if-none-match": downloadEtag},
    });
    assert.equal(cachedDownload.status, 304);

    // 版本不匹配时 404。
    const wrongVersion = await harness.request(`/api/match-rules/packs/${id}/2.json`);
    assert.equal(wrongVersion.status, 404);

    // 状态查询（token 正确）。
    const status = await harness.request(
      `/api/match-rules/status?id=${encodeURIComponent(id)}&token=${encodeURIComponent(token)}`,
    );
    assert.equal(status.status, 200);
    assert.equal(status.payload.status, "approved");
    assert.equal(status.payload.note, "规则合理，通过");
  } finally {
    await harness.close();
  }
});

test("status 接口：token 错误或缺失返回 403，未知记录 404", async () => {
  const harness = await startHarness();
  try {
    const submitted = await submit(harness, minimalPack("status-check-pack", 1));
    assert.equal(submitted.status, 201);
    const {id, token} = submitted.payload;

    const wrongToken = await harness.request(
      `/api/match-rules/status?id=${encodeURIComponent(id)}&token=${"0".repeat(32)}`,
    );
    assert.equal(wrongToken.status, 403);
    assert.equal(wrongToken.payload.ok, false);
    assert.equal(wrongToken.headers.get("x-content-type-options"), "nosniff");

    const missingToken = await harness.request(`/api/match-rules/status?id=${encodeURIComponent(id)}`);
    assert.equal(missingToken.status, 403);

    const wrongId = await harness.request(
      `/api/match-rules/status?id=r-deadbeef&token=${encodeURIComponent(token)}`,
    );
    assert.equal(wrongId.status, 404);

    const malformedId = await harness.request(
      `/api/match-rules/status?id=../etc/passwd&token=${encodeURIComponent(token)}`,
    );
    assert.equal(malformedId.status, 404);

    const ok = await harness.request(
      `/api/match-rules/status?id=${encodeURIComponent(id)}&token=${encodeURIComponent(token)}`,
    );
    assert.equal(ok.status, 200);
    assert.deepEqual(
      {id: ok.payload.id, status: ok.payload.status},
      {id, status: "pending"},
    );
  } finally {
    await harness.close();
  }
});

test("admin 接口缺少或错误 x-admin-token 一律 401", async () => {
  const harness = await startHarness();
  try {
    const listWithoutToken = await harness.request("/api/match-rules/admin/list?status=all");
    assert.equal(listWithoutToken.status, 401);
    assert.equal(listWithoutToken.headers.get("x-content-type-options"), "nosniff");

    const listWrongToken = await harness.request("/api/match-rules/admin/list?status=all", {
      headers: {"x-admin-token": "wrong-admin-token"},
    });
    assert.equal(listWrongToken.status, 401);

    const reviewWithoutToken = await harness.request("/api/match-rules/admin/review", {
      method: "POST",
      body: {id: "r-deadbeef", action: "approve"},
    });
    assert.equal(reviewWithoutToken.status, 401);

    const listOk = await harness.request("/api/match-rules/admin/list?status=all", {
      headers: {"x-admin-token": ADMIN_TOKEN},
    });
    assert.equal(listOk.status, 200);
    assert.equal(listOk.payload.ok, true);
    assert.deepEqual(listOk.payload.items, []);

    const badStatus = await harness.request("/api/match-rules/admin/list?status=unknown", {
      headers: {"x-admin-token": ADMIN_TOKEN},
    });
    assert.equal(badStatus.status, 400);
  } finally {
    await harness.close();
  }
});

test("reject 保留记录，status 返回 rejected 与审核备注", async () => {
  const harness = await startHarness();
  try {
    const submitted = await submit(harness, minimalPack("reject-me-pack", 1));
    assert.equal(submitted.status, 201);
    const {id, token} = submitted.payload;

    const rejected = await adminReview(harness, {
      id,
      action: "reject",
      note: "规则描述不清晰，请补充说明后重新提交",
    });
    assert.equal(rejected.status, 200);
    assert.equal(rejected.payload.status, "rejected");

    const status = await harness.request(
      `/api/match-rules/status?id=${encodeURIComponent(id)}&token=${encodeURIComponent(token)}`,
    );
    assert.equal(status.status, 200);
    assert.equal(status.payload.status, "rejected");
    assert.equal(status.payload.note, "规则描述不清晰，请补充说明后重新提交");

    const catalog = await harness.request("/api/match-rules/catalog.json");
    assert.deepEqual(catalog.payload.rules, []);

    const pendingPath = path.join(harness.rulesDir, "pending", `${id}.json`);
    const record = JSON.parse(fs.readFileSync(pendingPath, "utf8"));
    assert.equal(record.status, "rejected");
    assert.equal(record.note, "规则描述不清晰，请补充说明后重新提交");
    assert.equal(record.rule.id, "reject-me-pack");
    assert.equal(pendingFiles(harness.rulesDir).length, 1);

    const list = await harness.request("/api/match-rules/admin/list?status=all", {
      headers: {"x-admin-token": ADMIN_TOKEN},
    });
    assert.equal(list.payload.items.length, 1);
    assert.equal(list.payload.items[0].status, "rejected");
    assert.equal("tokenHash" in list.payload.items[0], false);
  } finally {
    await harness.close();
  }
});

test("disable 后目录与下载都不可见，status 返回 disabled", async () => {
  const harness = await startHarness();
  try {
    const submitted = await submit(harness, samplePack());
    const {id, token} = submitted.payload;
    const approved = await adminReview(harness, {id, action: "approve"});
    assert.equal(approved.payload.status, "approved");
    assert.equal(approvedFiles(harness.rulesDir).length, 1);

    const disabled = await adminReview(harness, {id, action: "disable", note: "命中举报，暂时下架"});
    assert.equal(disabled.status, 200);
    assert.equal(disabled.payload.status, "disabled");
    assert.equal(approvedFiles(harness.rulesDir).length, 0);
    assert.equal(fs.existsSync(path.join(harness.rulesDir, "approved", `${id}.json`)), false);

    const catalog = await harness.request("/api/match-rules/catalog.json");
    assert.deepEqual(catalog.payload.rules, []);
    const download = await harness.request(`/api/match-rules/packs/${id}/1.json`);
    assert.equal(download.status, 404);

    const status = await harness.request(
      `/api/match-rules/status?id=${encodeURIComponent(id)}&token=${encodeURIComponent(token)}`,
    );
    assert.equal(status.payload.status, "disabled");
    assert.equal(status.payload.note, "命中举报，暂时下架");
  } finally {
    await harness.close();
  }
});

test("version 冲突：同 rule.id 重复版本 409，更高版本可提交并通过", async () => {
  const harness = await startHarness();
  try {
    const first = await submit(harness, minimalPack("versioned-pack", 1));
    assert.equal(first.status, 201);
    const duplicate = await submit(harness, minimalPack("versioned-pack", 1));
    assert.equal(duplicate.status, 409);
    assert.equal(duplicate.payload.ok, false);

    const second = await submit(harness, minimalPack("versioned-pack", 2));
    assert.equal(second.status, 201);

    const approvedFirst = await adminReview(harness, {
      id: first.payload.id,
      action: "approve",
    });
    assert.equal(approvedFirst.status, 200);
    const approvedSecond = await adminReview(harness, {
      id: second.payload.id,
      action: "approve",
    });
    assert.equal(approvedSecond.status, 200);

    const catalog = await harness.request("/api/match-rules/catalog.json");
    assert.equal(catalog.payload.rules.length, 1);
    assert.equal(catalog.payload.rules[0].id, second.payload.id);
    assert.equal(catalog.payload.rules[0].version, 2);

    const list = await harness.request("/api/match-rules/admin/list?status=approved", {
      headers: {"x-admin-token": ADMIN_TOKEN},
    });
    assert.equal(list.payload.items.length, 2);
  } finally {
    await harness.close();
  }
});

test("report 写入 reports.jsonl 且只保留最近 1000 条", async () => {
  const harness = await startHarness();
  try {
    const submitted = await submit(harness, samplePack());
    const {id} = submitted.payload;
    await adminReview(harness, {id, action: "approve"});

    const firstReport = await harness.request("/api/match-rules/report", {
      method: "POST",
      body: {id, reason: "歌词匹配结果不对"},
    });
    assert.equal(firstReport.status, 200);
    assert.equal(firstReport.payload.ok, true);

    const reportsPath = path.join(harness.rulesDir, "reports.jsonl");
    let lines = fs.readFileSync(reportsPath, "utf8").split(/\r?\n/).filter(Boolean);
    assert.equal(lines.length, 1);
    assert.equal(JSON.parse(lines[0]).reason, "歌词匹配结果不对");

    const prefilled = [];
    for (let index = 0; index < 1000; index += 1) {
      prefilled.push(JSON.stringify({id, reason: `旧的举报 ${index}`, createdAt: "2026-11-20T00:00:00.000Z"}));
    }
    fs.writeFileSync(reportsPath, `${prefilled.join("\n")}\n`, "utf8");

    const latestReport = await harness.request("/api/match-rules/report", {
      method: "POST",
      body: {id, reason: "最新一条举报"},
    });
    assert.equal(latestReport.status, 200);
    lines = fs.readFileSync(reportsPath, "utf8").split(/\r?\n/).filter(Boolean);
    assert.equal(lines.length, 1000);
    assert.equal(JSON.parse(lines[999]).reason, "最新一条举报");

    const unknown = await harness.request("/api/match-rules/report", {
      method: "POST",
      body: {id: "r-deadbeef", reason: "不存在的规则"},
    });
    assert.equal(unknown.status, 404);

    const badReason = await harness.request("/api/match-rules/report", {
      method: "POST",
      body: {id, reason: "长".repeat(201)},
    });
    assert.equal(badReason.status, 400);

    const missingReason = await harness.request("/api/match-rules/report", {
      method: "POST",
      body: {id},
    });
    assert.equal(missingReason.status, 400);
  } finally {
    await harness.close();
  }
});
