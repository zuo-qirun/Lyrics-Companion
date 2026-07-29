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
const publicDir = path.join(__dirname, "public");
const apkDir = path.join(publicDir, "apk");
const historyDir = path.join(apkDir, "history");
const latestApk = path.join(apkDir, "lyrics_companion.apk");

function log(message) { console.log(`[release-sync] ${message}`); }

async function request(url, json = false) {
  const headers = {"user-agent": "lyrics-companion-release-sync", "accept": "application/vnd.github+json"};
  if (githubToken) headers.authorization = `Bearer ${githubToken}`;
  const response = await fetch(url, {headers, redirect: "follow"});
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${url}`);
  return json ? response.json() : Buffer.from(await response.arrayBuffer());
}

async function githubJson(route) {
  return request(`https://api.github.com/repos/${githubRepo}${route}`, true);
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
  const buffer = await request(asset.browser_download_url);
  atomicWrite(destination, buffer);
}

async function releaseManifest(release, apkAsset) {
  const asset = (release.assets || []).find((item) => item.name === manifestAsset);
  if (asset) return JSON.parse((await request(asset.browser_download_url)).toString("utf8"));
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
    await request(changelog.browser_download_url));
  const output = {
    ...manifest,
    packageName: "com.zuoqirun.lyricscompanion",
    apkPath: "apk/lyrics_companion.apk",
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

async function syncHistory(latest) {
  const releases = await listReleases(historyLimit);
  const versions = [];
  fs.mkdirSync(historyDir, {recursive: true});
  for (const release of releases) {
    const apkAsset = (release.assets || []).find((item) => {
      const matched = assetPattern.test(item.name); assetPattern.lastIndex = 0; return matched;
    });
    if (!apkAsset) continue;
    const manifest = release.id === latest.release.id
      ? latest.manifest : await releaseManifest(release, apkAsset);
    const fileName = `${safeSegment(release.tag_name)}-${safeSegment(apkAsset.name, "app.apk")}`;
    const destination = path.join(historyDir, fileName);
    await download(apkAsset, destination);
    versions.push({
      packageName: "com.zuoqirun.lyricscompanion",
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

async function main() {
  fs.mkdirSync(apkDir, {recursive: true});
  const latest = await syncLatest(await latestRelease());
  await syncHistory(latest);
  log(`synced ${latest.manifest.versionName} (${latest.manifest.versionCode})`);
}

main().catch((error) => {
  console.error(`[release-sync] ${error.stack || error.message}`);
  process.exitCode = 1;
});
