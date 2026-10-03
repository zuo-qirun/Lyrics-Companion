"use strict";

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const path = require("path");
const {spawn} = require("child_process");
const {loadEnv} = require("./release-sync-config");
const {DiagnosticStore, FeedbackStore, OnlineTracker, normalizeClientId} = require("./community");
const {publicBaseUrl} = require("./server-address");
const {ConfigShareStore} = require("./config-share");

loadEnv(path.join(__dirname, ".env"));

const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 8790);
const publicDir = process.env.PUBLIC_DIR
  ? path.resolve(process.env.PUBLIC_DIR) : path.resolve(__dirname, "public");
const templatePath = path.join(__dirname, "update.template.json");
const manifestPath = path.join(publicDir, "update.json");
const betaManifestPath = path.join(publicDir, "update-beta.json");
const versionsPath = path.join(publicDir, "versions.json");
const syncScript = path.join(__dirname, "sync-release.js");
const stateDir = process.env.STATE_DIR
  ? path.resolve(process.env.STATE_DIR) : path.resolve(__dirname, "state");
const feedbackPath = path.join(stateDir, "feedback.jsonl");
const feedbackRepliesPath = path.join(stateDir, "feedback-replies.jsonl");
const diagnosticsPath = path.join(stateDir, "diagnostics.jsonl");
const configSharePath = path.join(stateDir, "config-shares");
const autoSync = process.env.AUTO_SYNC !== "0";
const syncIntervalMs = Math.max(60_000, Number(process.env.SYNC_INTERVAL_MS || 300_000));
const onlineTtlMs = Math.max(30_000, Number(process.env.ONLINE_TTL_MS || 120_000));
const feedbackIntervalMs = Math.max(10_000,
  Number(process.env.FEEDBACK_INTERVAL_MS || 60_000));
const diagnosticIntervalMs = Math.max(10_000,
  Number(process.env.DIAGNOSTIC_INTERVAL_MS || 60_000));
const diagnosticRequestMaxBytes = 4 * 1024 * 1024;
const onlineTracker = new OnlineTracker(onlineTtlMs);
const feedbackStore = new FeedbackStore(feedbackPath, feedbackIntervalMs, feedbackRepliesPath);
const diagnosticStore = new DiagnosticStore(diagnosticsPath, diagnosticIntervalMs);
const configShareStore = new ConfigShareStore(configSharePath,
  Number(process.env.CONFIG_SHARE_TTL_MS || 90 * 24 * 60 * 60 * 1000));
let syncing = false;
let lastSync = null;

function baseUrl(req) {
  return publicBaseUrl(req.headers, process.env.PUBLIC_BASE_URL);
}

function sha256(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function sendJson(res, status, value) {
  const body = Buffer.from(JSON.stringify(value, null, 2) + "\n");
  res.writeHead(status, {"content-type": "application/json; charset=utf-8",
    "content-length": body.length, "cache-control": "no-store"});
  res.end(body);
}

function sendText(res, status, value) {
  const body = Buffer.from(value);
  res.writeHead(status, {"content-type": "text/plain; charset=utf-8",
    "content-length": body.length});
  res.end(body);
}

function sendFile(res, filePath, contentType) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    sendText(res, 404, "not found"); return;
  }
  res.writeHead(200, {"content-type": contentType,
    "content-length": fs.statSync(filePath).size, "cache-control": "no-store"});
  fs.createReadStream(filePath).pipe(res);
}

function readJson(req, maximumBytes = 16 * 1024) {
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

function clientAddress(req) {
  const forwarded = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim();
  return forwarded || String(req.socket.remoteAddress || "unknown");
}

function adminAuthorized(req) {
  const expected = String(process.env.ADMIN_TOKEN || "");
  const provided = String(req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!expected || !provided) return false;
  const expectedBytes = Buffer.from(expected);
  const providedBytes = Buffer.from(provided);
  return expectedBytes.length === providedBytes.length
    && crypto.timingSafeEqual(expectedBytes, providedBytes);
}

function requireAdmin(req, res) {
  if (!process.env.ADMIN_TOKEN) {
    sendJson(res, 503, {ok: false, error: "admin console is not configured"});
    return false;
  }
  if (!adminAuthorized(req)) {
    sendJson(res, 401, {ok: false, error: "admin authentication required"});
    return false;
  }
  return true;
}

function localApkUrl(req, relativePath, fingerprint) {
  const url = new URL(`/${relativePath}`, `${baseUrl(req)}/`);
  // The public CDN can retain a previous file at the stable APK path after a
  // new manifest is available. A content fingerprint makes every release URL
  // immutable and keeps the manifest size/SHA-256 paired with its download.
  if (fingerprint) url.searchParams.set("v", fingerprint);
  return url.toString();
}

/** 通道还没有可用清单时的占位：版本号 0，客户端据此显示"该通道暂无可更新版本"。 */
function unavailableManifest(channel) {
  return {
    schemaVersion: 1,
    channel,
    betaAvailable: false,
    packageName: "com.zuoqirun.lyricscompanion",
    versionCode: 0,
    versionName: "",
    force: false,
    changelog: [],
  };
}

/**
 * 读取某条通道的更新清单并补齐可下载字段。
 *
 * @param {object} req 用于推导公开地址的请求。
 * @param {boolean} github 是否让客户端直连 GitHub 资源。
 * @param {string} file 该通道的清单文件（正式版 `update.json`，测试版 `update-beta.json`）。
 * @param {string} channel `stable` 或 `beta`；清单文件缺失时用它决定占位还是回落模板。
 */
function readManifest(req, github, file = manifestPath, channel = "stable") {
  // The stable channel falls back to the checked-in template so a fresh deployment still
  // answers; the beta channel must not answer with the stable template's version.
  const source = fs.existsSync(file) ? file : (channel === "beta" ? "" : templatePath);
  const manifest = source ? JSON.parse(fs.readFileSync(source, "utf8"))
    : unavailableManifest(channel);
  manifest.channel = manifest.channel || channel;
  if (channel === "beta") {
    // 两条路径（同步脚本写的清单 / 文件缺失时的占位）给出同一个可用性字段。
    manifest.betaAvailable = manifest.betaAvailable !== false && Number(manifest.versionCode) > 0;
  }
  const apkPath = manifest.apkPath ? path.resolve(publicDir, manifest.apkPath) : "";
  if (github && manifest.githubApkUrl) {
    manifest.apkUrl = manifest.githubApkUrl;
    manifest.downloadChannel = "github";
  } else if (apkPath.startsWith(publicDir + path.sep) && fs.existsSync(apkPath)) {
    const relative = path.relative(publicDir, apkPath).replace(/\\/g, "/");
    const digest = sha256(apkPath);
    manifest.apkUrl = localApkUrl(req, relative, digest);
    manifest.downloadChannel = github ? "server-fallback" : "server";
    manifest.sha256 = digest;
    manifest.size = fs.statSync(apkPath).size;
  }
  const changelog = path.join(publicDir, manifest.changelogPath || "CHANGELOG.md");
  if (github && manifest.githubChangelogUrl) {
    manifest.changelogUrl = manifest.githubChangelogUrl;
  } else if (fs.existsSync(changelog)) {
    manifest.changelogUrl = new URL(`/${path.basename(changelog)}`, `${baseUrl(req)}/`).toString();
    manifest.changelogText = fs.readFileSync(changelog, "utf8").trim();
  }
  manifest.historyUrl = new URL("/versions.json", `${baseUrl(req)}/`).toString();
  delete manifest.apkPath;
  delete manifest.changelogPath;
  delete manifest.githubApkUrl;
  delete manifest.githubChangelogUrl;
  return manifest;
}

/** 测试版通道的当前状态，用于 `/health` 自检。 */
function betaStatus() {
  if (!fs.existsSync(betaManifestPath)) return {available: false};
  try {
    const manifest = JSON.parse(fs.readFileSync(betaManifestPath, "utf8"));
    const available = manifest.betaAvailable !== false && Number(manifest.versionCode) > 0;
    return {available, versionCode: Number(manifest.versionCode) || 0,
      versionName: manifest.versionName || "", releaseTag: manifest.releaseTag || "",
      syncedAt: manifest.syncedAt || ""};
  } catch (error) {
    return {available: false, error: error.message};
  }
}

function readVersions(req, github) {
  const data = fs.existsSync(versionsPath)
    ? JSON.parse(fs.readFileSync(versionsPath, "utf8"))
    : {schemaVersion: 1, generatedAt: null, versions: []};
  data.versions = (data.versions || []).map((entry) => {
    const next = {...entry};
    const local = next.apkPath ? path.resolve(publicDir, next.apkPath) : "";
    if (github && next.githubApkUrl) {
      next.apkUrl = next.githubApkUrl;
      next.downloadChannel = "github";
    } else if (local.startsWith(publicDir + path.sep) && fs.existsSync(local)) {
      const digest = sha256(local);
      next.apkUrl = localApkUrl(req, String(next.apkPath).replace(/\\/g, "/"), digest);
      next.downloadChannel = github ? "server-fallback" : "server";
      next.sha256 = digest;
      next.size = fs.statSync(local).size;
    }
    delete next.apkPath;
    delete next.githubApkUrl;
    return next;
  });
  return data;
}

/** 从同步子进程的 stderr 尾巴里挑一行最有信息量的，放进 `/health`，免得失败只有一个 code。 */
function syncErrorSummary(text) {
  const lines = String(text || "").split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const meaningful = lines.filter((line) =>
    /error|failed|timeout|HTTP \d{3}|rate limit/i.test(line));
  const pick = meaningful.length > 0 ? meaningful[meaningful.length - 1] : lines[lines.length - 1];
  return (pick || "").slice(0, 300);
}

function runSync(reason) {
  if (!autoSync || syncing) return;
  syncing = true;
  const startedAt = new Date().toISOString();
  const child = spawn(process.execPath, [syncScript], {cwd: __dirname,
    env: process.env, stdio: ["ignore", "pipe", "pipe"]});
  let stderrTail = "";
  child.stdout.pipe(process.stdout);
  child.stderr.on("data", (chunk) => {
    process.stderr.write(chunk);
    stderrTail = (stderrTail + String(chunk)).slice(-2000);
  });
  child.on("close", (code) => {
    syncing = false;
    lastSync = {reason, code, startedAt, finishedAt: new Date().toISOString(),
      ...(code === 0 ? {} : {error: syncErrorSummary(stderrTail)})};
  });
  child.on("error", (error) => {
    syncing = false;
    lastSync = {reason, code: -1, error: error.message, startedAt,
      finishedAt: new Date().toISOString()};
  });
}

const types = {".html": "text/html; charset=utf-8", ".json": "application/json; charset=utf-8",
  ".md": "text/markdown; charset=utf-8", ".apk": "application/vnd.android.package-archive",
  ".css": "text/css; charset=utf-8", ".js": "application/javascript; charset=utf-8"};

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (req.method === "GET" && url.pathname === "/health") {
      sendJson(res, 200, {ok: true, service: "lyrics-companion-update-server",
        autoSyncEnabled: autoSync, syncIntervalMs, syncing, lastSync, beta: betaStatus(),
        online: onlineTracker.count()}); return;
    }
    if (req.method === "GET" && url.pathname === "/api/online") {
      sendJson(res, 200, {ok: true, online: onlineTracker.count(),
        ttlSeconds: Math.round(onlineTtlMs / 1000), measuredAt: new Date().toISOString()});
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/online/heartbeat") {
      let body;
      try { body = await readJson(req); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      const clientId = normalizeClientId(body.clientId);
      if (!clientId) { sendJson(res, 400, {ok: false, error: "invalid client id"}); return; }
      const online = onlineTracker.heartbeat(clientId);
      sendJson(res, 200, {ok: true, online,
        ttlSeconds: Math.round(onlineTtlMs / 1000), measuredAt: new Date().toISOString()});
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/feedback") {
      let body;
      try { body = await readJson(req); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      try {
        const entry = feedbackStore.submit(body.clientId, body, Date.now(), clientAddress(req));
        sendJson(res, 201, {ok: true, id: entry.id, replyToken: entry.replyToken,
          receivedAt: entry.createdAt});
      } catch (error) {
        const rateLimited = error.code === "RATE_LIMITED";
        sendJson(res, rateLimited ? 429 : 400, {ok: false, error: error.message});
      }
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/feedback/replies") {
      let body;
      try { body = await readJson(req); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      sendJson(res, 200, {ok: true, replies: feedbackStore.repliesForTickets(body.tickets)});
      return;
    }
    if (req.method === "POST" && (url.pathname === "/api/diagnostics"
        || url.pathname === "/api/diagnostics/crash")) {
      let body;
      try { body = await readJson(req, diagnosticRequestMaxBytes); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      try {
        body.kind = url.pathname.endsWith("/crash") ? "crash" : body.kind;
        const entry = diagnosticStore.submit(body.clientId, body, Date.now(), clientAddress(req));
        sendJson(res, 201, {ok: true, id: entry.id, receivedAt: entry.createdAt});
      } catch (error) {
        sendJson(res, error.code === "RATE_LIMITED" ? 429 : 400, {ok: false, error: error.message});
      }
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/config/share") {
      let body;
      try { body = await readJson(req, 96 * 1024); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      try {
        const entry = configShareStore.create(body);
        sendJson(res, 201, {ok: true, code: entry.code, description: entry.description,
          expiresAt: entry.expiresAt});
      } catch (error) { sendJson(res, 400, {ok: false, error: error.message}); }
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/config/import") {
      let body;
      try { body = await readJson(req); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      const entry = configShareStore.get(body.code);
      if (!entry) { sendJson(res, 404, {ok: false, error: "share code not found"}); return; }
      sendJson(res, 200, {ok: true, code: entry.code, description: entry.description,
        createdAt: entry.createdAt, expiresAt: entry.expiresAt, config: entry.config});
      return;
    }
    if (url.pathname === "/api/admin/feedback") {
      if (!requireAdmin(req, res)) return;
      if (req.method !== "GET") { sendText(res, 405, "method not allowed"); return; }
      sendJson(res, 200, {ok: true, feedback: feedbackStore.list(url.searchParams.get("limit"))});
      return;
    }
    if (url.pathname === "/api/admin/diagnostics") {
      if (!requireAdmin(req, res)) return;
      if (req.method !== "GET") { sendText(res, 405, "method not allowed"); return; }
      // summary=1（`?summary`）去掉 details，只留长度与开头 300 字：给聊天机器人这类客户端用，
      // 默认仍是全量，管理面板照旧渲染完整正文。
      const rawSummary = url.searchParams.get("summary");
      const summary = (rawSummary !== null && rawSummary !== "0")
        || url.searchParams.get("fields") === "summary";
      sendJson(res, 200, {ok: true, summary,
        diagnostics: diagnosticStore.list(url.searchParams.get("limit"),
          {summary, kind: url.searchParams.get("kind") || ""})});
      return;
    }
    const diagnosticRoute = url.pathname.match(/^\/api\/admin\/diagnostics\/([a-f0-9-]{36})$/i);
    if (diagnosticRoute) {
      if (!requireAdmin(req, res)) return;
      if (req.method !== "GET") { sendText(res, 405, "method not allowed"); return; }
      const entry = diagnosticStore.get(diagnosticRoute[1]);
      if (!entry) { sendJson(res, 404, {ok: false, error: "diagnostic not found"}); return; }
      sendJson(res, 200, {ok: true, diagnostic: entry});
      return;
    }
    const replyRoute = url.pathname.match(/^\/api\/admin\/feedback\/([a-f0-9-]{36})\/replies$/i);
    if (replyRoute) {
      if (!requireAdmin(req, res)) return;
      if (req.method !== "POST") { sendText(res, 405, "method not allowed"); return; }
      let body;
      try { body = await readJson(req); }
      catch (error) { sendJson(res, 400, {ok: false, error: error.message}); return; }
      try {
        const reply = feedbackStore.addReply(replyRoute[1], body.message, Date.now());
        sendJson(res, 201, {ok: true, reply});
      } catch (error) {
        sendJson(res, error.code === "NOT_FOUND" ? 404 : 400, {ok: false, error: error.message});
      }
      return;
    }
    if (req.method !== "GET") { sendText(res, 405, "method not allowed"); return; }
    if (url.pathname === "/update.json") { sendJson(res, 200, readManifest(req, false)); return; }
    if (url.pathname === "/update-beta.json") {
      sendJson(res, 200, readManifest(req, false, betaManifestPath, "beta")); return;
    }
    if (url.pathname === "/update-github.json") { sendJson(res, 200, readManifest(req, true)); return; }
    if (url.pathname === "/update-github-beta.json") {
      sendJson(res, 200, readManifest(req, true, betaManifestPath, "beta")); return;
    }
    if (url.pathname === "/versions.json") { sendJson(res, 200, readVersions(req, false)); return; }
    if (url.pathname === "/versions-github.json") { sendJson(res, 200, readVersions(req, true)); return; }
    const route = url.pathname === "/" ? "/index.html"
      : url.pathname === "/versions" ? "/versions.html"
        : url.pathname === "/feedback" ? "/feedback.html" : url.pathname;
    const filePath = path.resolve(publicDir, `.${decodeURIComponent(route)}`);
    if (!filePath.startsWith(publicDir + path.sep)) { sendText(res, 403, "forbidden"); return; }
    sendFile(res, filePath, types[path.extname(filePath).toLowerCase()] || "application/octet-stream");
  } catch (error) {
    sendJson(res, 500, {ok: false, error: error.message});
  }
});

server.listen(port, host, () => {
  console.log(`Lyrics Companion update server listening on http://${host}:${port}`);
  if (autoSync) {
    runSync("startup");
    setInterval(() => runSync("timer"), syncIntervalMs).unref();
  }
});
