"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const CODE_PATTERN = /^[A-HJ-NP-Z2-9]{8}$/;

class ConfigShareStore {
  constructor(directory, ttlMs = 90 * 24 * 60 * 60 * 1000) {
    this.directory = directory;
    this.ttlMs = Math.max(24 * 60 * 60 * 1000, Number(ttlMs) || 0);
    fs.mkdirSync(directory, {recursive: true});
  }

  create(value, now = Date.now()) {
    const description = String(value.description || "").trim().slice(0, 200);
    const config = value.config;
    if (!config || typeof config !== "object" || Array.isArray(config)) {
      throw new Error("invalid configuration");
    }
    const encoded = JSON.stringify(config);
    if (Buffer.byteLength(encoded) > 64 * 1024) throw new Error("configuration is too large");
    let code;
    do { code = randomCode(); } while (fs.existsSync(this.file(code)));
    const entry = {schemaVersion: 1, code, description,
      createdAt: new Date(now).toISOString(), expiresAt: new Date(now + this.ttlMs).toISOString(),
      config};
    fs.writeFileSync(this.file(code), JSON.stringify(entry), {encoding: "utf8", flag: "wx"});
    return entry;
  }

  get(rawCode, now = Date.now()) {
    const code = String(rawCode || "").trim().toUpperCase().replace(/[-\s]/g, "");
    if (!CODE_PATTERN.test(code)) return null;
    const file = this.file(code);
    if (!fs.existsSync(file)) return null;
    try {
      const entry = JSON.parse(fs.readFileSync(file, "utf8"));
      if (Date.parse(entry.expiresAt) <= now) { fs.rmSync(file, {force: true}); return null; }
      return entry;
    } catch (_error) { return null; }
  }

  file(code) { return path.join(this.directory, `${code}.json`); }
}

function randomCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.randomBytes(8);
  let code = "";
  for (const byte of bytes) code += alphabet[byte % alphabet.length];
  return code;
}

module.exports = {CODE_PATTERN, ConfigShareStore};
