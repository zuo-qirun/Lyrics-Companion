# 匹配规则市场 API

歌词匹配规则市场的服务端接口，对应 `update_server/match-rules.js`，契约来自
[`docs/MATCH_RULES_MARKET_SPEC.md`](../docs/MATCH_RULES_MARKET_SPEC.md) 第 3 节。
规则包 JSON 只保存在服务端 `STATE_DIR/match-rules/`（`STATE_DIR` 默认
`update_server/state/`，已被 `.gitignore` 忽略），不进入 Git 仓库。

## 快速开始

```powershell
cd update_server
$env:ADMIN_TOKEN = "<管理令牌>"
$env:STATE_DIR = ".\state"
node server.js
```

- 公共接口（提交、目录、下载、状态、举报）不需要鉴权。
- 管理接口需要请求头 `x-admin-token: <ADMIN_TOKEN>`；未配置 `ADMIN_TOKEN` 时管理接口一律 401。
- 所有 JSON 响应都带 `X-Content-Type-Options: nosniff`。

## 数据模型

- 提交记录 id：形如 `r-1a2b3c4d`（`r-` + 8 位小写十六进制）。提交、状态查询、管理接口、
  下载路径、目录里的 `id` 都是它。
- 规则包自身还有 `rule.id`（3–64 字符）。同一个 `rule.id` 的新版本 `version` 必须比已存在
  （pending 或 approved）的版本更大，否则 `409`。
- 审核通过后，`approved/<id>.json` 是对外发布副本；`pending/<id>.json` 保留完整提交记录
  （含 token 摘要与审核备注），供 `status` 查询。token 明文只在提交响应里出现一次。

## 公共接口

### POST /api/match-rules/submit

提交规则包，服务端会独立校验（不信任客户端）。

```json
{
  "rule": { "schemaVersion": 1, "id": "my-rule", "name": "规则名", "version": 1, "rules": [] },
  "author": "作者（可选，≤40 字符）",
  "description": "提交说明（可选，≤200 字符）"
}
```

- `201`：`{"ok":true,"id":"r-1a2b3c4d","token":"<32 位十六进制>","status":"pending"}`
- `400`：`{"ok":false,"error":"<中文原因>"}`（JSON 非法、schema 校验失败、超过 64KB 等）
- `429`：同一 IP 每小时最多 5 次成功提交，超出返回 429
- `409`：同一 `rule.id` 的版本不大于已有版本

```bash
curl -sS -X POST http://127.0.0.1:8790/api/match-rules/submit \
  -H "content-type: application/json" \
  --data-binary '{
    "rule": {
      "schemaVersion": 1,
      "id": "demo-split-artist",
      "name": "示例：拆分歌手与歌名",
      "version": 1,
      "author": "demo",
      "description": "把「歌手 - 歌名」拆开",
      "scope": {"packages": ["com.example.player"], "sources": ["netease"]},
      "rules": [{
        "id": "split",
        "when": [{"field": "title", "op": "regex", "value": "(.{1,40})\\s*-\\s*(.{1,80})"}],
        "then": [{"type": "extract", "pattern": "(.{1,40})\\s*-\\s*(.{1,80})",
                  "targets": {"artist": 1, "title": 2}}]
      }]
    },
    "author": "demo",
    "description": "示例提交"
  }'
```

保存返回的 `id` 与 `token`。`token` 只用于状态查询，再次展示时请自行脱敏。

### GET /api/match-rules/catalog.json

市场目录，只包含已审核通过（`approved`）且未被下架的规则包；同一 `rule.id` 只展示最高版本。

- `200`：`{"ok":true,"schemaVersion":1,"generatedAt":"<ISO>","rules":[{"id","name","version","author","description","sha256","size","updatedAt","downloads","packages"}]}`
- 响应头：`ETag`、`Cache-Control: public, max-age=300`
- 携带 `If-None-Match` 匹配时返回 `304`（无响应体）

```bash
curl -sS http://127.0.0.1:8790/api/match-rules/catalog.json
curl -sS -o /dev/null -w "%{http_code}\n" \
  -H 'If-None-Match: "<上次响应的 ETag>"' \
  http://127.0.0.1:8790/api/match-rules/catalog.json
```

### GET /api/match-rules/packs/&lt;id&gt;/&lt;version&gt;.json

下载规则包正文。

- `200`：`{"ok":true,"id":"r-xxxxxxxx","version":1,"sha256":"<sha256(JSON.stringify(rule))>","rule":{...}}`
- 响应体是**紧凑 JSON**（无缩进空格），客户端可直接按响应原文里 `"rule"` 对应的子串计算 SHA-256，结果必然等于 `sha256` 字段；不要改成 pretty-print，否则子串带缩进会导致校验失败。
- 响应头：`ETag`、`Cache-Control: public, max-age=86400`
- 规则不存在、版本不匹配、未审核或已下架：`404` JSON
- 每次成功下载会累加目录里的 `downloads` 计数

```bash
curl -sS http://127.0.0.1:8790/api/match-rules/packs/r-1a2b3c4d/1.json
```

### GET /api/match-rules/status?id=&lt;id&gt;&token=&lt;token&gt;

查询自己提交的审核状态与审核回复。

- `200`：`{"ok":true,"id","status":"pending|approved|rejected|disabled","updatedAt","messages":[{"at":"<ISO 时间>","text":"<审核员回复>"}]}`
- `messages` 是审核员写给提交者的公开回复（按时间正序，最多 20 条，单条 ≤500 字符）；
  内部备注 `note` 不会出现在这里。
- `403`：token 不匹配或缺失
- `404`：记录不存在

```bash
curl -sS "http://127.0.0.1:8790/api/match-rules/status?id=r-1a2b3c4d&token=<token>"
```

### POST /api/match-rules/report

举报已发布规则包，落到 `reports.jsonl`，只保留最近 1000 条。

```json
{"id": "r-1a2b3c4d", "reason": "匹配结果错误（≤200 字符）"}
```

- `200`：`{"ok":true}`
- `400` / `404`：参数不合法 / 规则不存在

```bash
curl -sS -X POST http://127.0.0.1:8790/api/match-rules/report \
  -H "content-type: application/json" \
  --data-binary '{"id":"r-1a2b3c4d","reason":"匹配结果错误"}'
```

## 管理接口

请求头：`x-admin-token: <ADMIN_TOKEN>`；缺失或错误返回 `401`。

### GET /api/match-rules/admin/list?status=pending|approved|rejected|disabled|all

列出规则包（默认 `all`）。每项包含
`{id,status,submittedAt,updatedAt,author,description,note,messages,rule}`，不返回 token 摘要。
`note` 是管理员内部备注；`messages` 是给提交者的公开回复。

### POST /api/match-rules/admin/review

```json
{
  "id": "r-1a2b3c4d",
  "action": "approve | enable | reject | disable | reopen | reply",
  "note": "内部备注（可选，≤300 字符，仅管理员可见）",
  "message": "回复提交者（可选，≤500 字符，公开）"
}
```

也可以省略 `action`，直接传 `status: "pending|approved|rejected|disabled"`。

- `approve` / `enable`：重新做完整规则包校验，通过后写入 `approved/<id>.json` 并刷新目录；
  校验失败 `400`，版本冲突 `409`。`enable` 用于把已下架/已驳回归还上架。
- `reject`：置为 `rejected`，保留提交记录
- `disable`：置为 `disabled`，删除对外发布副本，目录与下载立即不可见
- `reopen`：退回 `pending` 重新审核
- `reply`：只追加公开回复，不改变当前状态（必须提供非空 `message`）
- 成功：`{"ok":true,"id":"r-1a2b3c4d","status":"...","messages":[...]}`
- 非法 `action` 返回 `400`；`reply` 缺少 `message` 返回 `400`

```bash
ADMIN_TOKEN=<令牌> node match-rules-admin.js list pending
ADMIN_TOKEN=<令牌> node match-rules-admin.js approve r-1a2b3c4d "规则合理，通过"
ADMIN_TOKEN=<令牌> node match-rules-admin.js disable r-1a2b3c4d "命中举报"
ADMIN_TOKEN=<令牌> node match-rules-admin.js enable r-1a2b3c4d
ADMIN_TOKEN=<令牌> node match-rules-admin.js reply r-1a2b3c4d "请补充作用域后再提交"
```

`note` 是内部备注（只给管理员看）；给提交者的公开回复用 `message`（网页审核台有独立输入框），
或者用 CLI 的 `reply` 子命令。网页审核台地址：`https://lyrics-companion.zuoqirun.top/rules/admin`。

命令行工具读取 `BASE_URL`（默认 `http://127.0.0.1:8790`）与 `ADMIN_TOKEN`，中文输出，
不会打印令牌。等价的 curl 调用：

```bash
curl -sS http://127.0.0.1:8790/api/match-rules/admin/list?status=pending \
  -H "x-admin-token: <管理令牌>"

curl -sS -X POST http://127.0.0.1:8790/api/match-rules/admin/review \
  -H "x-admin-token: <管理令牌>" -H "content-type: application/json" \
  --data-binary '{"id":"r-1a2b3c4d","action":"approve","note":"内部备注","message":"规则合理，已上架"}'
```

## 存储结构

```
STATE_DIR/
└── match-rules/
    ├── pending/<id>.json      # 提交记录：status/tokenHash/note/messages/rule/sha256/size/时间
    ├── approved/<id>.json     # 对外发布副本：id/status/rule/sha256/size/时间/downloads/author/description/messages
    ├── submissions.jsonl      # 提交审计日志（id/ruleId/version/author/submittedAt）
    └── reports.jsonl          # 举报记录，只保留最近 1000 条
```

- 启动时扫描 `approved/` 建立目录缓存；审核操作后重新加载。
- `approved/` 中缺失或状态非 `approved` 的文件不会进入目录。
- 备份/迁移直接复制 `STATE_DIR/match-rules/` 即可。

## 规则包校验（服务端）

服务端按与客户端一致的 schema 独立校验，任何一项不满足都返回 `400`：

- `schemaVersion` 必须为 `1`；`id` 3–64 字符 `^[a-z0-9][a-z0-9._-]*$`；`name` 1–40；
  `version` 1–9999；`author` ≤40；`description` ≤200；`minVersionCode` 0–999999。
- `scope.packages` ≤20 个 `[A-Za-z0-9._]+`；`scope.sources` ≤8 个，取值
  `netease/qqmusic/kugou/kuwo/soda/migu`。
- `rules` 1–64 条，规则 `id` 1–40 且包内唯一；`when` 0–6 个条件，`then` 1–8 个动作；
  动作只能作用于 `title/artist/album`。
- 正则长度 ≤200、总数 ≤64；禁止命名捕获组 `(?<`、反向引用 `\[1-9]`、嵌套量词
  （`(.*)+`、`(.+)+`、`(\d+)+` 等）以及配对不上转义花括号。
- 整个规则包序列化后 ≤64KB（客户端另有 32KB 的提交上限）。
- 未知字段忽略（向前兼容），已知字段类型错误拒绝整包。

## 测试

```powershell
cd update_server
node --test test/match-rules.test.js
```

测试用独立的 `http.createServer` + 临时 `STATE_DIR` 直接驱动 `match-rules.js`，覆盖：
提交成功、非法包 400、IP 限流 429、审核前目录不可见、approve 后可见、ETag 304、
下载 sha256 一致、token 403、admin 401、reject 后状态、version 冲突、举报落盘与截断等。
