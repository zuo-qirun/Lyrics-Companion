"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");
const fs = require("fs");
const net = require("net");
const os = require("os");
const path = require("path");
const {spawn} = require("child_process");

function availablePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", () => {
      const port = probe.address().port;
      probe.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

function waitUntilListening(child) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("server start timed out")), 5_000);
    child.once("error", (error) => { clearTimeout(timeout); reject(error); });
    child.stdout.on("data", (chunk) => {
      if (String(chunk).includes("update server listening")) {
        clearTimeout(timeout);
        resolve();
      }
    });
    child.once("exit", (code) => {
      clearTimeout(timeout);
      reject(new Error(`server exited early with ${code}`));
    });
  });
}

test("HTTP server accepts heartbeats and persists bounded feedback", async () => {
  const port = await availablePort();
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-community-server-"));
  const serverDir = path.resolve(__dirname, "..");
  const child = spawn(process.execPath, ["server.js"], {
    cwd: serverDir,
    env: {...process.env, HOST: "127.0.0.1", PORT: String(port), AUTO_SYNC: "0",
      STATE_DIR: stateDir, ADMIN_TOKEN: "test-admin-token"},
    stdio: ["ignore", "pipe", "pipe"],
  });
  try {
    await waitUntilListening(child);
    const post = (pathname, body) => fetch(`http://127.0.0.1:${port}${pathname}`, {
      method: "POST", headers: {"content-type": "application/json"},
      body: JSON.stringify(body),
    });
    const first = await post("/api/online/heartbeat",
      {clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", appVersion: "test"});
    assert.equal(first.status, 200);
    assert.equal((await first.json()).online, 1);
    const second = await post("/api/online/heartbeat",
      {clientId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", appVersion: "test"});
    assert.equal((await second.json()).online, 2);
    const online = await fetch(`http://127.0.0.1:${port}/api/online`);
    assert.equal((await online.json()).online, 2);

    const update = await fetch(`http://127.0.0.1:${port}/update.json`);
    const updateManifest = await update.json();
    assert.match(updateManifest.historyUrl, /\/versions\.json$/);

    const faq = await fetch(`http://127.0.0.1:${port}/faq.json`);
    assert.equal(faq.status, 200);
    const faqDocument = await faq.json();
    assert.equal(faqDocument.schemaVersion, 1);
    assert.equal(faqDocument.items[0].id, "media-system-permission");

    const feedback = await post("/api/feedback", {
      clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      appVersion: "test", message: "本地接口集成测试反馈", contact: "",
    });
    assert.equal(feedback.status, 201);
    const ticket = await feedback.json();
    assert.equal(ticket.ok, true);
    assert.ok(ticket.replyToken);
    const stored = fs.readFileSync(path.join(stateDir, "feedback.jsonl"), "utf8").trim();
    assert.equal(JSON.parse(stored).message, "本地接口集成测试反馈");
    assert.notEqual(JSON.parse(stored).replyTokenHash, ticket.replyToken);

    const adminHeaders = {authorization: "Bearer test-admin-token", "content-type": "application/json"};
    const inbox = await fetch(`http://127.0.0.1:${port}/api/admin/feedback`, {headers: adminHeaders});
    assert.equal(inbox.status, 200);
    assert.equal((await inbox.json()).feedback.length, 1);
    const reply = await fetch(`http://127.0.0.1:${port}/api/admin/feedback/${ticket.id}/replies`, {
      method: "POST", headers: adminHeaders, body: JSON.stringify({message: "已收到，正在排查。"}),
    });
    assert.equal(reply.status, 201);
    const replies = await post("/api/feedback/replies", {tickets: [{id: ticket.id, token: ticket.replyToken}]});
    assert.equal((await replies.json()).replies[0].message, "已收到，正在排查。");

    const share = await post("/api/config/share", {description: "双屏紧凑样式",
      config: {schemaVersion: 1, settings: {main_overlay_style: {type: "string", value: "compact"}}}});
    assert.equal(share.status, 201);
    const shareBody = await share.json();
    assert.match(shareBody.code, /^[A-HJ-NP-Z2-9]{8}$/);
    const imported = await post("/api/config/import", {code: shareBody.code.toLowerCase()});
    const importedBody = await imported.json();
    assert.equal(importedBody.description, "双屏紧凑样式");
    assert.equal(importedBody.config.settings.main_overlay_style.value, "compact");

    const largeDetails = `stack trace\n${"x".repeat(200_000)}`;
    const diagnostic = await post("/api/diagnostics/crash", {clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      appVersion: "test", summary: "IllegalStateException", details: largeDetails});
    assert.equal(diagnostic.status, 201);
    const diagnostics = await fetch(`http://127.0.0.1:${port}/api/admin/diagnostics`, {headers: adminHeaders});
    const diagnosticItems = (await diagnostics.json()).diagnostics;
    assert.equal(diagnosticItems[0].kind, "crash");
    assert.equal(diagnosticItems[0].details.length, largeDetails.length);
  } finally {
    if (child.exitCode === null) {
      child.kill();
      await new Promise((resolve) => child.once("exit", resolve));
    }
    fs.rmSync(stateDir, {recursive: true, force: true});
  }
});

/** Spawn the update server against a throwaway public directory. */
async function withPublicDir(publicDir, run) {
  const port = await availablePort();
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-beta-state-"));
  const serverDir = path.resolve(__dirname, "..");
  const child = spawn(process.execPath, ["server.js"], {
    cwd: serverDir,
    env: {...process.env, HOST: "127.0.0.1", PORT: String(port), AUTO_SYNC: "0",
      STATE_DIR: stateDir, PUBLIC_DIR: publicDir},
    stdio: ["ignore", "pipe", "pipe"],
  });
  try {
    await waitUntilListening(child);
    await run(`http://127.0.0.1:${port}`);
  } finally {
    if (child.exitCode === null) {
      child.kill();
      await new Promise((resolve) => child.once("exit", resolve));
    }
    fs.rmSync(stateDir, {recursive: true, force: true});
  }
}

test("serves the beta channel from its own manifest and keeps stable untouched", async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-beta-public-"));
  const publicDir = path.join(root, "public");
  fs.mkdirSync(path.join(publicDir, "apk"), {recursive: true});
  const apkBytes = "beta-apk-bytes";
  fs.writeFileSync(path.join(publicDir, "apk", "lyrics_companion_beta.apk"), apkBytes);
  fs.writeFileSync(path.join(publicDir, "CHANGELOG-beta.md"), "# 测试版更新日志\n\n- 尝鲜构建\n");
  fs.writeFileSync(path.join(publicDir, "update-beta.json"), JSON.stringify({
    schemaVersion: 1, channel: "beta", betaAvailable: true,
    packageName: "com.zuoqirun.lyricscompanion", versionCode: 200, versionName: "20260101-beta",
    apkPath: "apk/lyrics_companion_beta.apk", changelogPath: "CHANGELOG-beta.md",
    force: false, changelog: ["尝鲜构建"], releaseTag: "apk-200-abcdef0",
    syncedAt: "2026-01-01T00:00:00.000Z",
  }, null, 2) + "\n");
  try {
    await withPublicDir(publicDir, async (base) => {
      const beta = await (await fetch(`${base}/update-beta.json`)).json();
      assert.equal(beta.channel, "beta");
      assert.equal(beta.betaAvailable, true);
      assert.equal(beta.versionCode, 200);
      assert.equal(beta.apkPath, undefined);
      assert.ok(beta.apkUrl.includes("lyrics_companion_beta.apk"));
      assert.match(beta.apkUrl, /\?v=[a-f0-9]{64}$/);
      assert.equal(beta.sha256,
        crypto.createHash("sha256").update(apkBytes).digest("hex"));
      assert.equal(beta.size, Buffer.byteLength(apkBytes));
      assert.match(beta.changelogUrl, /\/CHANGELOG-beta\.md$/);
      assert.equal(beta.changelogText, "# 测试版更新日志\n\n- 尝鲜构建");

      // A published beta must not leak into the stable channel, which still answers from the
      // checked-in template while no stable release has been synced.
      const stable = await (await fetch(`${base}/update.json`)).json();
      assert.equal(stable.channel, "stable");
      assert.equal(stable.versionCode, 1);

      const health = await (await fetch(`${base}/health`)).json();
      assert.equal(health.beta.available, true);
      assert.equal(health.beta.versionCode, 200);
      assert.equal(health.beta.releaseTag, "apk-200-abcdef0");
    });
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
});

test("answers an empty beta channel without falling back to the stable template", async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-beta-empty-"));
  const publicDir = path.join(root, "public");
  fs.mkdirSync(publicDir, {recursive: true});
  try {
    await withPublicDir(publicDir, async (base) => {
      const beta = await (await fetch(`${base}/update-beta.json`)).json();
      assert.equal(beta.channel, "beta");
      assert.equal(beta.betaAvailable, false);
      assert.equal(beta.versionCode, 0);
      assert.equal(beta.apkUrl, undefined, "beta must not point at the stable APK");

      const health = await (await fetch(`${base}/health`)).json();
      assert.equal(health.beta.available, false);
    });
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
});
