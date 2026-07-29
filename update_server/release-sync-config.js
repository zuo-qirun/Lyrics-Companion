"use strict";

function compileAssetPattern(value) {
  const source = String(value || "^lyrics-companion-.*\\.apk$").trim();
  const literal = source.match(/^\/(.+)\/([a-z]*)$/i);
  return literal ? new RegExp(literal[1], literal[2]) : new RegExp(source);
}

function parseHistoryLimit(value, fallback = 20) {
  if (value === undefined || value === null || value === "") return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : fallback;
}

function safeSegment(value, fallback = "release") {
  const cleaned = String(value || "").trim()
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "").slice(0, 96);
  return cleaned && !/^\.+$/.test(cleaned) ? cleaned : fallback;
}

function loadEnv(filePath) {
  const fs = require("fs");
  if (!fs.existsSync(filePath)) return;
  for (const line of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!match || process.env[match[1]] !== undefined) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"'))
        || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    process.env[match[1]] = value;
  }
}

module.exports = {compileAssetPattern, parseHistoryLimit, safeSegment, loadEnv};
