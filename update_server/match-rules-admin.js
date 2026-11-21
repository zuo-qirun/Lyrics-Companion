"use strict";

// 匹配规则市场管理命令行（issue #174）。
//
// 用法：
//   node match-rules-admin.js list [pending|approved|all]
//   node match-rules-admin.js approve <id> [note]
//   node match-rules-admin.js reject <id> [note]
//   node match-rules-admin.js disable <id> [note]
//
// 环境变量：
//   BASE_URL     服务端地址，默认 http://127.0.0.1:8790
//   ADMIN_TOKEN  管理令牌（必填；本脚本不会打印它）
//
// 只调用服务端的 /api/match-rules/admin/* 接口，不直接读写 STATE_DIR。

const BASE_URL = String(process.env.BASE_URL || "http://127.0.0.1:8790").replace(/\/+$/, "");
const ADMIN_TOKEN = String(process.env.ADMIN_TOKEN || "").trim();
const REQUEST_TIMEOUT_MS = 15000;

function usage() {
  console.log([
    "匹配规则市场管理工具",
    "",
    "用法：",
    "  node match-rules-admin.js list [pending|approved|rejected|disabled|all]",
    "  node match-rules-admin.js approve <id> [内部备注]",
    "  node match-rules-admin.js enable <id> [内部备注]     # 重新上架",
    "  node match-rules-admin.js reject <id> [内部备注]",
    "  node match-rules-admin.js disable <id> [内部备注]",
    "  node match-rules-admin.js reopen <id> [内部备注]     # 退回待审",
    "  node match-rules-admin.js reply <id> <回复内容>      # 公开回复，不改状态",
    "",
    "环境变量：",
    `  BASE_URL     服务端地址（当前 ${BASE_URL}）`,
    "  ADMIN_TOKEN  管理令牌（必填，不会打印）",
    "",
    "示例：",
    "  BASE_URL=http://127.0.0.1:8790 ADMIN_TOKEN=*** node match-rules-admin.js list pending",
    "  node match-rules-admin.js approve r-1a2b3c4d 规则合理，通过",
    "  node match-rules-admin.js reply r-1a2b3c4d 请去掉正则里的命名捕获组",
  ].join("\n"));
}

function shortError(response) {
  const status = response ? response.status : 0;
  if (status === 401) return "管理令牌缺失或错误（HTTP 401）";
  if (status === 404) return "规则不存在（HTTP 404）";
  if (status === 400) return "请求参数不合法（HTTP 400）";
  if (status === 409) return "版本冲突（HTTP 409）";
  if (status >= 500) return `服务端内部错误（HTTP ${status}）`;
  return `请求失败（HTTP ${status}）`;
}

async function callApi(pathname, options = {}) {
  if (!ADMIN_TOKEN) {
    throw new Error("缺少环境变量 ADMIN_TOKEN；管理命令需要管理令牌（令牌不会被打印）");
  }
  let response;
  try {
    response = await fetch(`${BASE_URL}${pathname}`, {
      method: options.method || "GET",
      headers: {
        "x-admin-token": ADMIN_TOKEN,
        ...(options.body ? {"content-type": "application/json"} : {}),
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    throw new Error(`无法连接服务端 ${BASE_URL}：${error.message}`);
  }
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch (_error) {
    payload = null;
  }
  return {response, payload, text};
}

function describeItem(item) {
  const version = item.rule && Number.isFinite(Number(item.rule.version))
    ? `v${Number(item.rule.version)}` : "v?";
  const name = item.rule && item.rule.name ? String(item.rule.name) : "（未命名规则包）";
  const author = item.author ? `作者：${item.author}` : "作者：未填写";
  const createdAt = String(item.submittedAt || "").replace("T", " ").slice(0, 19);
  const updatedAt = String(item.updatedAt || "").replace("T", " ").slice(0, 19);
  return `- ${item.id} [${item.status}] ${version} ${name} — ${author}（提交 ${createdAt}，更新 ${updatedAt}）`;
}

async function commandList(status) {
  const filter = status || "all";
  if (!["pending", "approved", "rejected", "disabled", "all"].includes(filter)) {
    console.error("list 的状态只能是 pending、approved、rejected、disabled 或 all");
    process.exitCode = 1;
    return;
  }
  const {response, payload, text} = await callApi(`/api/match-rules/admin/list?status=${filter}`);
  if (!response.ok || !payload || !Array.isArray(payload.items)) {
    const detail = payload && payload.error ? `：${payload.error}` : "";
    console.error(`${shortError(response)}${detail}`);
    if (!payload && text) console.error(text.slice(0, 300));
    process.exitCode = 1;
    return;
  }
  const items = payload.items;
  if (items.length === 0) {
    console.log("没有符合条件的规则包。");
    return;
  }
  const counts = {pending: 0, approved: 0, rejected: 0, disabled: 0};
  for (const item of items) {
    if (counts[item.status] !== undefined) counts[item.status] += 1;
  }
  console.log(`共 ${items.length} 条（pending ${counts.pending} / approved ${counts.approved}`
    + ` / rejected ${counts.rejected} / disabled ${counts.disabled}）：`);
  for (const item of items) console.log(describeItem(item));
}

async function commandReview(action, id, note, message) {
  const {response, payload, text} = await callApi("/api/match-rules/admin/review", {
    method: "POST",
    body: {
      id,
      action,
      note: note || "",
      message: message || undefined,
    },
  });
  if (!response.ok || !payload || payload.ok !== true) {
    const detail = payload && payload.error ? `：${payload.error}` : "";
    console.error(`${shortError(response)}${detail}`);
    if (!payload && text) console.error(text.slice(0, 300));
    process.exitCode = 1;
    return;
  }
  const labels = {
    approve: "已通过并上架",
    enable: "已重新上架",
    reject: "已驳回",
    disable: "已下架",
    reopen: "已退回待审",
    reply: "已回复提交者",
  };
  const reply = payload.messages && payload.messages.length
    ? `，现有 ${payload.messages.length} 条公开回复` : "";
  console.log(`${labels[action]}：${payload.id}（当前状态 ${payload.status}${reply}）`);
}

async function main(argv) {
  const [command, ...rest] = argv;
  if (!command || command === "-h" || command === "--help" || command === "help") {
    usage();
    if (!command) process.exitCode = 1;
    return;
  }
  if (command === "list") {
    await commandList(rest[0]);
    return;
  }
  if (command === "reply") {
    const id = rest[0];
    const message = rest.slice(1).join(" ").trim();
    if (!id || !message) {
      console.error("reply 需要规则 id 和回复内容，例如：reply r-1a2b3c4d 请补充作用域");
      usage();
      process.exitCode = 1;
      return;
    }
    await commandReview("reply", id, "", message);
    return;
  }
  if (["approve", "enable", "reject", "disable", "reopen"].includes(command)) {
    const id = rest[0];
    if (!id) {
      console.error(`${command} 需要规则 id，例如 r-1a2b3c4d`);
      usage();
      process.exitCode = 1;
      return;
    }
    const note = rest.slice(1).join(" ").trim();
    await commandReview(command, id, note);
    return;
  }
  console.error(`未知命令：${command}`);
  usage();
  process.exitCode = 1;
}

main(process.argv.slice(2)).catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
