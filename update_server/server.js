"use strict";

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const path = require("path");
const {spawn} = require("child_process");
const {loadEnv} = require("./release-sync-config");
const {FeedbackStore, OnlineTracker, normalizeClientId} = require("./community");

loadEnv(path.join(__dirname, ".env"));

const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 8788);
const publicDir = path.resolve(__dirname, "public");
const templatePath = path.join(__dirname, "update.template.json");
const manifestPath = path.join(publicDir, "update.json");
const versionsPath = path.join(publicDir, "versions.json");
const syncScript = path.join(__dirname, "sync-release.js");
const stateDir = process.env.STATE_DIR
  ? path.resolve(process.env.STATE_DIR) : path.resolve(__dirname, "state");
const feedbackPath = path.join(stateDir, "feedback.jsonl");
const autoSync = process.env.AUTO_SYNC !== "0";
const syncIntervalMs = Math.max(60_000, Number(process.env.SYNC_INTERVAL_MS || 300_000));
const onlineTtlMs = Math.max(30_000, Number(process.env.ONLINE_TTL_MS || 120_000));
const feedbackIntervalMs = Math.max(10_000,
  Number(process.env.FEEDBACK_INTERVAL_MS || 60_000));
const onlineTracker = new OnlineTracker(onlineTtlMs);
const feedbackStore = new FeedbackStore(feedbackPath, feedbackIntervalMs);
let syncing = false;
let lastSync = null;

function baseUrl(req) {
  if (process.env.PUBLIC_BASE_URL) return process.env.PUBLIC_BASE_URL.replace(/\/+$/, "");
  const protocol = String(req.headers["x-forwarded-proto"] || "http").split(",")[0].trim();
  return `${protocol}://${req.headers.host}`;
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

function readManifest(req, github) {
  const source = fs.existsSync(manifestPath) ? manifestPath : templatePath;
  const manifest = JSON.parse(fs.readFileSync(source, "utf8"));
  const apkPath = path.resolve(publicDir, manifest.apkPath || "apk/lyrics_companion.apk");
  if (github && manifest.githubApkUrl) {
    manifest.apkUrl = manifest.githubApkUrl;
    manifest.downloadChannel = "github";
  } else if (apkPath.startsWith(publicDir + path.sep) && fs.existsSync(apkPath)) {
    const relative = path.relative(publicDir, apkPath).replace(/\\/g, "/");
    manifest.apkUrl = new URL(`/${relative}`, `${baseUrl(req)}/`).toString();
    manifest.downloadChannel = github ? "server-fallback" : "server";
    manifest.sha256 = sha256(apkPath);
    manifest.size = fs.statSync(apkPath).size;
  }
  const changelog = path.join(publicDir, "CHANGELOG.md");
  if (github && manifest.githubChangelogUrl) {
    manifest.changelogUrl = manifest.githubChangelogUrl;
  } else if (fs.existsSync(changelog)) {
    manifest.changelogUrl = new URL("/CHANGELOG.md", `${baseUrl(req)}/`).toString();
    manifest.changelogText = fs.readFileSync(changelog, "utf8").trim();
  }
  delete manifest.apkPath;
  delete manifest.githubApkUrl;
  delete manifest.githubChangelogUrl;
  return manifest;
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
      next.apkUrl = new URL(`/${String(next.apkPath).replace(/\\/g, "/")}`,
        `${baseUrl(req)}/`).toString();
      next.downloadChannel = github ? "server-fallback" : "server";
      next.sha256 = sha256(local);
      next.size = fs.statSync(local).size;
    }
    delete next.apkPath;
    delete next.githubApkUrl;
    return next;
  });
  return data;
}

function runSync(reason) {
  if (!autoSync || syncing) return;
  syncing = true;
  const startedAt = new Date().toISOString();
  const child = spawn(process.execPath, [syncScript], {cwd: __dirname,
    env: process.env, stdio: ["ignore", "pipe", "pipe"]});
  child.stdout.pipe(process.stdout);
  child.stderr.pipe(process.stderr);
  child.on("close", (code) => {
    syncing = false;
    lastSync = {reason, code, startedAt, finishedAt: new Date().toISOString()};
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
        autoSyncEnabled: autoSync, syncIntervalMs, syncing, lastSync,
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
        sendJson(res, 201, {ok: true, id: entry.id, receivedAt: entry.createdAt});
      } catch (error) {
        const rateLimited = error.code === "RATE_LIMITED";
        sendJson(res, rateLimited ? 429 : 400, {ok: false, error: error.message});
      }
      return;
    }
    if (req.method !== "GET") { sendText(res, 405, "method not allowed"); return; }
    if (url.pathname === "/update.json") { sendJson(res, 200, readManifest(req, false)); return; }
    if (url.pathname === "/update-github.json") { sendJson(res, 200, readManifest(req, true)); return; }
    if (url.pathname === "/versions.json") { sendJson(res, 200, readVersions(req, false)); return; }
    if (url.pathname === "/versions-github.json") { sendJson(res, 200, readVersions(req, true)); return; }
    const route = url.pathname === "/" ? "/index.html"
      : url.pathname === "/versions" ? "/versions.html" : url.pathname;
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
