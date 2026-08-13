"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const {ConfigShareStore} = require("../config-share");

test("configuration shares expire and normalize human codes", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-config-share-"));
  try {
    const store = new ConfigShareStore(directory, 24 * 60 * 60 * 1000);
    const entry = store.create({description: "  测试配置  ", config: {settings: {a: 1}}}, 1000);
    assert.equal(store.get(`${entry.code.slice(0, 4)}-${entry.code.slice(4)}`, 2000).description,
      "测试配置");
    assert.equal(store.get(entry.code, 1000 + 24 * 60 * 60 * 1000 + 1), null);
  } finally { fs.rmSync(directory, {recursive: true, force: true}); }
});
