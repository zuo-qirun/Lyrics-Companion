"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {compileAssetPattern, parseHistoryLimit, safeSegment, loadEnv} =
  require("./release-sync-config");

loadEnv(path.join(__dirname, ".env"));

const githubRepo = process.env.GITHUB_REPO || "zuo-qirun/Lyrics-Companion";
const githubToken = process.env.GITHUB_TOKEN || "";
const releaseTag = process.env.RELEASE_TAG || "latest";
const assetPattern = compileAssetPattern(process.env.ASSET_PATTERN);
const manifestAsset = process.env.MANIFEST_ASSET || "release-update.json";
const changelogAsset = process.env.CHANGELOG_ASSET || "CHANGELOG.md";
const historyLimit = parseHistoryLimit(process.env.HISTORY_RELEASE_LIMIT, 20);
const force = process.argv.includes("--force") || process.env.FORCE_SYNC === "1";
// 与 server.js 一样允许 PUBLIC_DIR 覆盖：同步结果写到哪里，服务端就从哪里读。
const publicDir = process.env.PUBLIC_DIR
  ? path.resolve(process.env.PUBLIC_DIR) : path.resolve(__dirname, "public");
const apkDir = path.join(publicDir, "apk");
const historyDir = path.join(apkDir, "history");
const latestApk = path.join(apkDir, "lyrics_companion.apk");
// 测试版（beta）通道：单独一份清单与 APK，正式版清单不受影响。
const betaManifestPath = path.join(publicDir, "update-beta.json");
const betaApk = path.join(apkDir, "lyrics_companion_beta.apk");
const betaChangelogPath = path.join(publicDir, "CHANGELOG-beta.md");
// GitHub 的 `/releases/latest` 会跳过 prerelease，所以测试版要在最近一批 release 里自己找。
const betaScanLimit = Math.max(1, Math.min(100, Number(process.env.BETA_RELEASE_SCAN) || 100));

function log(message) { console.log(`[release-sync] ${message}`); }

const RETRYABLE_CODES = new Set([
  "ECONNRESET", "ECONNREFUSED", "ECONNABORTED", "ETIMEDOUT", "EHOSTUNREACH",
  "ENETUNREACH", "ENOTFOUND", "EAI_AGAIN", "EPIPE", "EPROTO",
  "UND_ERR_CONNECT_TIMEOUT", "UND_ERR_SOCKET", "UND_ERR_HEADERS_TIMEOUT",
]);
const RETRYABLE_RESPONSE_STATUSES = new Set([408, 425, 429, 500, 502, 503, 504]);

function retryDelayMs(attempt) {
  const configuredBase = Number(process.env.SYNC_RETRY_BASE_DELAY_MS);
  const configuredCap = Number(process.env.SYNC_RETRY_MAX_DELAY_MS);
  const base = Number.isFinite(configuredBase) && configuredBase >= 0 ? configuredBase : 2000;
  const cap = Number.isFinite(configuredCap) && configuredCap >= 0 ? configuredCap : 15000;
  return Math.min(base * (2 ** attempt), cap);
}

function retryCount() {
  const raw = Number(process.env.SYNC_MAX_RETRIES);
  return Number.isFinite(raw) && raw >= 0 ? Math.floor(raw) : 3;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRetryableFetchError(error) {
  if (!(error instanceof TypeError)) return false;
  const cause = error.cause;
  if (!cause) return false;
  if (typeof cause.code === "string" && RETRYABLE_CODES.has(cause.code)) return true;
  if (typeof cause.message === "string") {
    return /ECONNRESET|ETIMEDOUT|connect timeout|Client network socket disconnected|ENOTFOUND|EAI_AGAIN|ECONNREFUSED|socket hang up/i.test(cause.message);
  }
  return false;
}

function isRetryableResponse(response) {
  return Boolean(response) && RETRYABLE_RESPONSE_STATUSES.has(response.status);
}

async function discardResponse(response) {
  if (!response || !response.body || typeof response.body.cancel !== "function") return;
  try {
    await response.body.cancel();
  } catch (error) {
    // The next retry must not be blocked by a failed best-effort body cancellation.
  }
}

async function fetchWithRetry(url, options = {}, fetchImpl = fetch) {
  const maxRetries = retryCount();
  let lastError;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetchImpl(url, options);
      if (!isRetryableResponse(response) || attempt === maxRetries) return response;
      await discardResponse(response);
      const delay = retryDelayMs(attempt);
      log(`retry ${attempt + 1}/${maxRetries} for ${url} after ${delay}ms (HTTP ${response.status})`);
      await sleep(delay);
    } catch (error) {
      if (!isRetryableFetchError(error)) throw error;
      lastError = error;
      if (attempt === maxRetries) break;
      const delay = retryDelayMs(attempt);
      const causeCode = (error.cause && error.cause.code) || "network error";
      log(`retry ${attempt + 1}/${maxRetries} for ${url} after ${delay}ms (${causeCode})`);
      await sleep(delay);
    }
  }
  throw lastError;
}

let authDisabled = false;

/**
 * token 被 GitHub 拒了才允许匿名重试；配额类 403（未过期但已用尽）不该退化成匿名再打一遍。
 *
 * <p>这段逻辑原先只存在于线上部署的 `sync-release.js` 里（线上与仓库分叉），现在回灌进仓库，
 * 免得覆盖部署时把它弄丢。
 */
function authFailure(status, detail) {
  if (status === 401) return true;
  if (status !== 403) return false;
  return /bad credentials|requires authentication|invalid token|token.{0,20}(expired|revoked)/i.test(detail || "");
}

function firstLine(text) {
  return String(text || "").split(/\r?\n/).find((line) => line.trim()) || "";
}

async function request(url, options = {}) {
  const {json = false, accept = "application/vnd.github+json", allowAnonymousRetry = true} = options;
  const headers = {"user-agent": "lyrics-companion-release-sync", accept};
  const useAuth = Boolean(githubToken) && !authDisabled;
  if (useAuth) headers.authorization = `Bearer ${githubToken}`;
  const response = await fetchWithRetry(url, {headers, redirect: "follow"});
  if (response.ok) return json ? response.json() : Buffer.from(await response.arrayBuffer());
  const detail = await response.text().catch(() => "");
  if (useAuth && allowAnonymousRetry && authFailure(response.status, detail)) {
    const reason = firstLine(detail) || `HTTP ${response.status}`;
    log(`GITHUB_TOKEN rejected (HTTP ${response.status}: ${reason}); continuing anonymously`);
    authDisabled = true;
    return request(url, {...options, allowAnonymousRetry: false});
  }
  throw new Error(`HTTP ${response.status}: ${url}${detail ? ` - ${firstLine(detail)}` : ""}`);
}

async function githubJson(route) {
  return request(`https://api.github.com/repos/${githubRepo}${route}`, {json: true});
}

/** GitHub API 的 asset 直链：第一跳走 api.github.com（比 github.com 稳），需要 octet-stream。 */
function assetApiUrl(repo, asset) {
  if (!asset || !asset.id) return "";
  return `https://api.github.com/repos/${repo}/releases/assets/${asset.id}`;
}

/**
 * 抓一个 release asset：先走 `browser_download_url`（不吃 API 配额），失败再退到 API asset 直链。
 *
 * <p>线上日志里失败**全部**是 `github.com/.../releases/download/...` 的 `UND_ERR_CONNECT_TIMEOUT`
 * （undici 选中不通的 IP 就超时），而 `api.github.com` 一直稳；换入口比单纯加退避更管用。
 */
async function fetchAsset(asset, options = {}) {
  const attempts = [
    asset.browser_download_url ? {url: asset.browser_download_url, accept: ""} : null,
    assetApiUrl(githubRepo, asset) ? {url: assetApiUrl(githubRepo, asset),
      accept: "application/octet-stream"} : null,
  ].filter(Boolean);
  let lastError;
  for (const attempt of attempts) {
    try {
      return await request(attempt.url,
        {json: options.json, allowAnonymousRetry: options.allowAnonymousRetry,
          ...(attempt.accept ? {accept: attempt.accept} : {})});
    } catch (error) {
      lastError = error;
      log(`asset fetch failed via ${new URL(attempt.url).host}: ${error.message}`);
    }
  }
  throw lastError;
}

function findAsset(release, predicate, description) {
  const asset = (release.assets || []).find(predicate);
  if (!asset) throw new Error(`Release asset not found: ${description}`);
  return asset;
}

function sha256(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function atomicWrite(filePath, data) {
  fs.mkdirSync(path.dirname(filePath), {recursive: true});
  const temporary = `${filePath}.tmp-${process.pid}`;
  fs.writeFileSync(temporary, data);
  fs.renameSync(temporary, filePath);
}

async function download(asset, destination, allowReuse = true) {
  if (allowReuse && !force && fs.existsSync(destination)
      && fs.statSync(destination).size === asset.size) {
    return;
  }
  log(`download ${asset.name}`);
  const buffer = await fetchAsset(asset);
  atomicWrite(destination, buffer);
}

async function releaseManifest(release, apkAsset) {
  const asset = (release.assets || []).find((item) => item.name === manifestAsset);
  if (asset) return JSON.parse((await fetchAsset(asset)).toString("utf8"));
  const tagCode = String(release.tag_name || "").match(/apk-(\d+)-/);
  return {
    schemaVersion: 1,
    packageName: "com.zuoqirun.lyricscompanion",
    versionCode: tagCode ? Number(tagCode[1]) : Math.floor(Date.parse(release.published_at) / 1000),
    versionName: release.name || release.tag_name,
    force: false,
    changelog: [release.body || `GitHub Release ${release.tag_name}`].filter(Boolean),
    commit: release.target_commitish || "",
    builtAt: release.published_at || release.created_at,
    assetName: apkAsset.name,
  };
}

async function latestRelease() {
  return releaseTag === "latest"
    ? githubJson("/releases/latest")
    : githubJson(`/releases/tags/${encodeURIComponent(releaseTag)}`);
}

async function listReleases(limit) {
  if (limit <= 0) return [];
  return githubJson(`/releases?per_page=${Math.min(limit, 100)}`);
}

/**
 * 从 release 列表里挑出最新的测试版：`/releases` 按创建时间倒序，所以第一个 prerelease 就是最新的。
 * GitHub 的 `/releases/latest` 天然跳过 prerelease，正式版通道因此不会被测试版顶掉。
 *
 * @param {Array<object>} releases GitHub `/releases` 返回的列表（可能为空）。
 * @param {(release: object) => boolean} isUsable 过滤条件：不满足的 prerelease 直接跳过，
 *   例如手工发布、没挂匹配 APK 的测试版（Codex review P2）。
 * @returns {object|null} 最新的、可用的非草稿 prerelease，没有则返回 null。
 */
function pickBetaRelease(releases, isUsable = () => true) {
  return (releases || []).find((release) =>
    Boolean(release) && release.prerelease === true && release.draft !== true
      && isUsable(release)) || null;
}

/** 该 release 是否挂了符合 `ASSET_PATTERN` 的 APK；手工发的空测试版会被这里挡掉。 */
function hasBetaApk(release) {
  return (release && release.assets || []).some((item) => {
    const matched = assetPattern.test(item.name);
    assetPattern.lastIndex = 0;
    return matched;
  });
}

/** 取该 release 里匹配的 APK 资源（调用前应确认 {@link hasBetaApk} 为真）。 */
function betaApkAsset(release) {
  const asset = (release && release.assets || []).find((item) => {
    const matched = assetPattern.test(item.name);
    assetPattern.lastIndex = 0;
    return matched;
  });
  if (!asset) throw new Error(`Release asset not found: beta APK (${release && release.tag_name})`);
  return asset;
}

/**
 * 最新的**可用**测试版候选：跳过没挂 APK 的，以及清单解析失败的 prerelease。
 *
 * <p>以前第一个 prerelease 一旦不可用（手工发布、没挂 APK、release-update.json 坏掉），
 * `findAsset()` 会直接抛错，整轮同步中止——测试版端点继续停在旧内容，连 `versions.json`
 * 和正式版兜底都写不出去（Codex review P2）。现在逐个候选往下试，全不可用就当"没有测试版"。
 *
 * @param {Array<object>} releases GitHub `/releases` 列表。
 * @returns {Promise<{release: object, apkAsset: object, manifest: object}|null>} 候选或 null。
 */
async function firstUsableBeta(releases) {
  let remaining = (releases || []).slice();
  while (remaining.length > 0) {
    const release = pickBetaRelease(remaining, hasBetaApk);
    if (!release) return null;
    try {
      const apkAsset = betaApkAsset(release);
      return {release, apkAsset, manifest: await releaseManifest(release, apkAsset)};
    } catch (error) {
      log(`skipping unusable prerelease ${release.tag_name}: ${error.message}`);
      remaining = remaining.filter((item) => item !== release);
    }
  }
  return null;
}

/** 测试版通道还没有可用构建时的清单：版本号为 0，客户端据此显示"暂无测试版"。 */
function unavailableBetaManifest() {
  return {
    schemaVersion: 1,
    channel: "beta",
    betaAvailable: false,
    packageName: "com.zuoqirun.lyricscompanion",
    versionCode: 0,
    versionName: "",
    force: false,
    changelog: [],
  };
}

/**
 * 测试版通道该服务哪一份清单：只有**测试版版本号更高**时才用 prerelease。
 *
 * <p>否则会出现「测试版用户看不到比当前更高的正式版」：正式版在测试版之后发布时，
 * `/update-beta.json` 仍指着旧的 prerelease，测试版用户拿到的 remoteVersionCode 永远
 * 不大于本地版本，也就永远等不到那次正式更新（Codex review P1）。正式版与测试版取高者。
 *
 * @param {object|null} stableManifest 当前正式版清单（`update.json` 的内容）。
 * @param {object|null} betaManifest 候选测试版清单。
 * @returns {boolean} 是否用测试版清单；版本号相同或正式版更高时返回 false。
 */
function betaChannelUsesPrerelease(stableManifest, betaManifest) {
  const betaCode = Number(betaManifest && betaManifest.versionCode) || 0;
  if (betaCode <= 0) return false;
  const stableCode = Number(stableManifest && stableManifest.versionCode) || 0;
  return betaCode > stableCode;
}

/**
 * 同步测试版通道：写 `public/update-beta.json`，内容是「正式版与测试版里版本号更高者」。
 *
 * <p>测试版更高时下载它的 APK 与更新日志，生成测试版专属清单；正式版更高（或还没有
 * prerelease）时直接把正式版清单照抄过来，让测试版用户也能收到正式更新。两者都没有时写
 * `betaAvailable: false` 的占位清单，客户端据此显示"该通道暂无可更新版本"。
 *
 * @param {{release: object, apkAsset: object, manifest: object}|null} stable 已同步的正式版。
 * @param {Array<object>} [releases] 已取好的 `/releases` 列表（省一次 API 调用）。
 * @returns {Promise<{release: object, apkAsset: object, manifest: object}|null>} 同步到的清单。
 */
async function syncBeta(stable = null, releases = null) {
  const beta = await firstUsableBeta(releases || await listReleases(betaScanLimit));
  const candidate = beta ? beta.manifest : null;
  if (!betaChannelUsesPrerelease(stable && stable.manifest, candidate)) {
    if (!stable) {
      atomicWrite(betaManifestPath, JSON.stringify({
        ...unavailableBetaManifest(), syncedAt: new Date().toISOString(),
      }, null, 2) + "\n");
      log("no prerelease and no stable release; the beta channel stays unavailable");
      return null;
    }
    // 正式版更高：测试版通道直接指向正式版（同一份 APK 与清单，只是挂在 beta 端点上）。
    const fallback = {
      ...stable.manifest,
      betaAvailable: true,
      betaSource: "stable",
      syncedAt: new Date().toISOString(),
    };
    atomicWrite(betaManifestPath, JSON.stringify(fallback, null, 2) + "\n");
    log(`beta channel serves the newer stable ${fallback.versionName} (${fallback.versionCode})`);
    return {release: stable.release, apkAsset: stable.apkAsset, manifest: fallback};
  }
  // betaChannelUsesPrerelease() 只有在 candidate.versionCode > 0 时才为真，所以 beta 一定非空。
  const {release, apkAsset} = beta;
  const changelog = (release.assets || []).find((item) => item.name === changelogAsset);
  let sameRelease = false;
  if (!force && fs.existsSync(betaManifestPath)) {
    try {
      sameRelease = JSON.parse(fs.readFileSync(betaManifestPath, "utf8")).releaseTag
        === release.tag_name;
    } catch (error) { sameRelease = false; }
  }
  await download(apkAsset, betaApk, sameRelease);
  if (changelog) {
    // The beta changelog is a separate file: public/CHANGELOG.md stays the stable archive.
    atomicWrite(betaChangelogPath, await fetchAsset(changelog));
  }
  const output = {
    ...candidate,
    schemaVersion: 1,
    channel: "beta",
    betaAvailable: true,
    betaSource: "beta",
    packageName: "com.zuoqirun.lyricscompanion",
    apkPath: "apk/lyrics_companion_beta.apk",
    changelogPath: "CHANGELOG-beta.md",
    githubApkUrl: apkAsset.browser_download_url,
    githubChangelogUrl: changelog ? changelog.browser_download_url : "",
    sha256: sha256(betaApk),
    size: fs.statSync(betaApk).size,
    releaseTag: release.tag_name,
    releaseUrl: release.html_url,
    syncedAt: new Date().toISOString(),
  };
  atomicWrite(betaManifestPath, JSON.stringify(output, null, 2) + "\n");
  log(`synced beta ${output.versionName} (${output.versionCode}) tag=${release.tag_name}`);
  return {release, apkAsset, manifest: output};
}

async function syncLatest(release) {
  const apkAsset = findAsset(release, (item) => assetPattern.test(item.name), "APK");
  assetPattern.lastIndex = 0;
  const manifest = await releaseManifest(release, apkAsset);
  const changelog = (release.assets || []).find((item) => item.name === changelogAsset);
  let sameRelease = false;
  const currentManifest = path.join(publicDir, "update.json");
  if (!force && fs.existsSync(currentManifest)) {
    try {
      sameRelease = JSON.parse(fs.readFileSync(currentManifest, "utf8")).releaseTag
        === release.tag_name;
    } catch (error) { sameRelease = false; }
  }
  await download(apkAsset, latestApk, sameRelease);
  if (changelog) atomicWrite(path.join(publicDir, "CHANGELOG.md"),
    await fetchAsset(changelog));
  const output = {
    ...manifest,
    schemaVersion: 1,
    channel: "stable",
    packageName: "com.zuoqirun.lyricscompanion",
    apkPath: "apk/lyrics_companion.apk",
    changelogPath: "CHANGELOG.md",
    githubApkUrl: apkAsset.browser_download_url,
    githubChangelogUrl: changelog ? changelog.browser_download_url : "",
    sha256: sha256(latestApk),
    size: fs.statSync(latestApk).size,
    releaseTag: release.tag_name,
    releaseUrl: release.html_url,
    syncedAt: new Date().toISOString(),
  };
  atomicWrite(path.join(publicDir, "update.json"), JSON.stringify(output, null, 2) + "\n");
  return {release, apkAsset, manifest: output};
}

/** 上一轮 `versions.json` 里按 tag 索引的条目：这一轮某条抓不下来时就沿用它。 */
function previousHistoryEntries() {
  const file = path.join(publicDir, "versions.json");
  const entries = new Map();
  if (!fs.existsSync(file)) return entries;
  try {
    for (const entry of JSON.parse(fs.readFileSync(file, "utf8")).versions || []) {
      if (entry && entry.releaseTag) entries.set(entry.releaseTag, {packageName: "com.zuoqirun.lyricscompanion",
        channel: entry.channel || "stable", ...entry});
    }
  } catch (error) {
    log(`cannot read the previous versions.json: ${error.message}`);
  }
  return entries;
}

/**
 * 同步历史清单。
 *
 * <p>每条 release 单独兜错：某个包在 `github.com` 上抓不下来时沿用上一轮这条的记录（没有就跳过），
 * 不再让整轮同步 exit 1——否则 `versions.json` 会一直停在旧内容（线上日志里反复出现）。
 *
 * @param {{release: object, manifest: object}} latest 已同步的正式版。
 * @param {{release: object, manifest: object}|null} beta 已同步的测试版通道。
 * @param {Array<object>} [releases] 已取好的 `/releases` 列表（省一次 API 调用）。
 */
async function syncHistory(latest, beta = null, releases = null) {
  const list = (releases || await listReleases(historyLimit)).slice(0, historyLimit);
  const previous = previousHistoryEntries();
  const versions = [];
  fs.mkdirSync(historyDir, {recursive: true});
  for (const release of list) {
    const apkAsset = (release.assets || []).find((item) => {
      const matched = assetPattern.test(item.name); assetPattern.lastIndex = 0; return matched;
    });
    if (!apkAsset) continue;
    try {
      const manifest = release.id === latest.release.id
        ? latest.manifest
        : beta && release.id === beta.release.id
          ? beta.manifest
          : await releaseManifest(release, apkAsset);
      const fileName = `${safeSegment(release.tag_name)}-${safeSegment(apkAsset.name, "app.apk")}`;
      const destination = path.join(historyDir, fileName);
      await download(apkAsset, destination);
      versions.push({
        packageName: "com.zuoqirun.lyricscompanion",
        channel: release.prerelease ? "beta" : "stable",
        versionCode: manifest.versionCode,
        versionName: manifest.versionName,
        force: Boolean(manifest.force),
        changelog: manifest.changelog || [release.body || ""].filter(Boolean),
        commit: manifest.commit || release.target_commitish || "",
        builtAt: manifest.builtAt || release.published_at,
        publishedAt: release.published_at,
        releaseTag: release.tag_name,
        releaseUrl: release.html_url,
        apkPath: `apk/history/${fileName}`,
        githubApkUrl: apkAsset.browser_download_url,
        sha256: sha256(destination),
        size: fs.statSync(destination).size,
      });
    } catch (error) {
      const kept = previous.get(release.tag_name);
      log(`history entry failed for ${release.tag_name}: ${error.message}`
        + (kept ? " (kept the previous entry)" : " (skipped)"));
      if (kept) versions.push(kept);
    }
  }
  versions.sort((a, b) => Number(b.versionCode) - Number(a.versionCode));
  atomicWrite(path.join(publicDir, "versions.json"), JSON.stringify({
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    source: "github-releases",
    releaseLimit: historyLimit,
    versions,
  }, null, 2) + "\n");
}

/** 从一份 `/releases` 列表里取最新正式版：等价于 GitHub 的 `/releases/latest`（列表按创建时间倒序）。 */
function latestStableRelease(releases) {
  return (releases || []).find((release) =>
    Boolean(release) && release.prerelease !== true && release.draft !== true) || null;
}

/**
 * 同步一轮。
 *
 * <p>只打**一次** `/releases`：最新正式版、最新测试版、历史条目都从同一份列表里取。
 * 以前是三处各打一次（`/releases/latest` + beta 扫描 + 历史），线上 `.env` 没有
 * `GITHUB_TOKEN` 时按匿名配额 60 次/小时算，12 轮/小时 × 3 = 36 次，很容易被别的消耗顶到
 * `HTTP 403 API rate limit exceeded`，整轮 exit 1。
 */
async function main() {
  fs.mkdirSync(apkDir, {recursive: true});
  const releases = await listReleases(Math.max(historyLimit, betaScanLimit, 1));
  // 指定 RELEASE_TAG 时才需要额外取一次（列表里可能不含这个 tag）。
  const stable = releaseTag === "latest"
    ? latestStableRelease(releases) : await latestRelease();
  if (!stable) throw new Error("no stable release found in the repository");
  const latest = await syncLatest(stable);
  const beta = await syncBeta(latest, releases);
  await syncHistory(latest, beta, releases);
  log(`synced ${latest.manifest.versionName} (${latest.manifest.versionCode})`
    + (beta ? ` and beta ${beta.manifest.versionName} (${beta.manifest.versionCode})` : ""));
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`[release-sync] ${error.stack || error.message}`);
    process.exitCode = 1;
  });
}

module.exports = {
  isRetryableFetchError, isRetryableResponse, retryDelayMs, retryCount, fetchWithRetry,
  pickBetaRelease, betaChannelUsesPrerelease, hasBetaApk, firstUsableBeta, syncBeta,
  authFailure, latestStableRelease, assetApiUrl,
};
