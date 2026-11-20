# 匹配规则市场 · 实现规格（v1）

本文件是 issue #174「元数据匹配规则 + 规则市场」这一轮实现的唯一契约。三个执行方
（update_server、Android 匹配核心、Android 规则界面）按本文件并行开发，接口不一致时
以本文件为准；确实需要改动时先改本文件再改代码。

## 0. 目标与边界

- 应用内可以：查看当前播放器元数据 → 用内置规则 + 市场规则 + 个人规则生成清洗后的
  歌名/歌手候选 → 参与本地 LRC 与六个在线词库的匹配。
- 个人可以：在应用内用图形向导或 JSON 代码编写规则、预览、保存启用、提交到规则市场。
- 市场规则：提交后进入待审核，管理员审核通过后出现在市场，其他用户一键下载启用。
- 规则 JSON **只存服务器**（`STATE_DIR/match-rules/`），不写入 Git 仓库；GitHub 只放
  一个「匹配规则提交/反馈」Issue 模板，作为没有应用内上传条件时的补充渠道。
- 不引入新依赖、不使用 API 19 以上才有的类（`java.util.Base64` 不可用，用
  `android.util.Base64`；`java.util.Objects` 部分方法不可用；`Pattern` 支持 Java 7 语法）。
- 所有面向用户的文字用中文；代码注释可中文。

## 1. 规则 JSON（schemaVersion = 1）

```json
{
  "schemaVersion": 1,
  "id": "echo-artist-title-split",
  "name": "回声：歌手名塞进歌名",
  "version": 1,
  "author": "zuoqirun",
  "description": "把「歌手 - 歌名」拆开，并忽略「车载音乐」这类文件夹名歌手",
  "minVersionCode": 0,
  "scope": {"packages": ["com.example.echo"], "sources": []},
  "rules": [
    {
      "id": "split-dash",
      "when": [
        {"field": "title", "op": "regex", "value": "(.{1,40})\\s*[-–—]\\s*(.{1,80})"},
        {"field": "artist", "op": "equalsAny", "value": ["车载音乐", "车载", "未知", "未知歌手", "群星"]}
      ],
      "then": [
        {"type": "extract", "pattern": "(.{1,40})\\s*[-–—]\\s*(.{1,80})",
         "targets": {"artist": 1, "title": 2}}
      ]
    }
  ]
}
```

字段约束：

| 字段 | 规则 |
| --- | --- |
| `schemaVersion` | 必须为 1 |
| `id` | 3–64 字符，`^[a-z0-9][a-z0-9._-]*$` |
| `name` | 1–40 字符 |
| `version` | 1–9999 整数 |
| `author` | 0–40 字符 |
| `description` | 0–200 字符 |
| `minVersionCode` | 0–999999 整数，缺省 0；应用 versionCode 小于它时忽略该规则包 |
| `scope.packages` | 最多 20 个，包名 `[A-Za-z0-9._]+`，空数组/缺省 = 不限制包名 |
| `scope.sources` | 最多 8 个，取值 `netease/qqmusic/kugou/kuwo/soda/migu`，空 = 不限制 |
| `rules` | 1–64 条 |

单条规则：

- `id`：1–40 字符，同一个规则包内唯一。
- `when`：0–6 个条件，全部 AND；为空表示无条件。
  - `field`：`title` / `artist` / `album` / `package` / `source`
  - `op`：
    - `equals`：忽略大小写、去首尾空白后全等
    - `equalsAny`：`value` 为字符串数组，任一等即通过
    - `contains` / `containsAny`：包含
    - `startsWith` / `endsWith`
    - `regex`：`Pattern.CASE_INSENSITIVE`，`find()` 语义，长度 ≤200
    - `empty`：该字段为空（此时忽略 `value`）
  - `value`：字符串；`equalsAny/containsAny` 时为字符串数组（1–20 个）
- `then`：1–8 个动作，按顺序执行；任何动作失败只跳过该动作，不中断整个包。
  - `extract`：`pattern`（≤200）+ `targets` 对象 `{ "title|artist|album": 组号1-9 }`；
    `Pattern.CASE_INSENSITIVE`，`find()`；未匹配则该动作无效果。只允许编号组，禁止命名组。
  - `replace`：`field`（缺省 `title`）+ `pattern` + `replacement`（支持 `$1`–`$9`）+
    `all`（布尔，缺省 true）
  - `set`：`field` + `value`（字面量）
  - `clear`：`field`
  - `trim`：`field`
  - `stripSuffix`：`field`（缺省 `title`）+ 可选 `pattern`，默认
    `\\s*[（(【\\[]\\s*\\d{1,3}\\s*[)）】\\]]\\s*$`（issue #174 比亚迪场景）
  - `aliasArtist`：`map` 对象（别名→规范名，1–50 项，键值各 ≤40 字符）。按
    `/,，、&;；` 与 `feat.`/`ft.` 拆分歌手串，逐段替换后以 `/` 连接。
  - 动作只能作用于 `title/artist/album`。

安全校验（客户端与服务端都做）：

- 整包序列化 ≤32KB。
- 正则串不得含：命名组 `(?<`、反向引用 `\\[1-9]`、`(.*)+`/`(.+)+`/`(.{0,})+` 等嵌套量词、
  `\{` 与 `\}` 无法配对的情况；正则数量 ≤ 64。
- 未知字段忽略（向前兼容），但已知字段类型错误必须拒绝整包。
- 解析失败抛 `IllegalArgumentException`，消息为中文，可直接展示给用户。

## 2. Android 匹配核心（owner：core-dev）

新增文件（包 `com.zuoqirun.lyricscompanion`）：

- `MatchRule.java`：单条规则模型 + `JSONObject` 读写。
- `MatchRulePack.java`：规则包模型 + `parse(String)` / `parse(JSONObject)` /
  `toJson()` / `toJsonString()` / 校验（中文错误消息）/ `appliesTo(String packageName,
  String source)`。
- `MatchInput.java`：`title/artist/album/packageName/source`，不可变。
- `MatchOutput.java`：`primaryTitle/primaryArtist/primaryAlbum`、`List<Candidate>`、
  `List<TraceEntry>`、`boolean changed`。`Candidate` 含 `title/artist/ruleId/kind`
  （kind 固定 `"cleaned"`）。
- `MatchRuleEngine.java`：
  - `static MatchOutput apply(List<MatchRulePack> packs, MatchInput input)`
  - 按 packs 顺序执行；包不 `appliesTo` 则跳过；每条规则先判断 `when`，命中后依次执行
    `then`；字段每次变化后记录候选快照（`primary` 为最终值，候选去重，排除与原始输入相同者）。
  - 规则执行顺序稳定；不修改入参。
- `BuiltinMatchRules.java`：`static List<MatchRulePack> packs()`，内置规则包
  （`id=builtin.cleaning`，`version=1`，`author=Lyrics Companion`），始终启用、不可删除：
  1. 歌手是「车载音乐/车载/未知/未知歌手/群星/网络歌手/热门音乐/本地音乐/文件夹/
     Various Artists/Unknown/Music」且 title 含 ` - / – / — ` 分隔 → 拆成
     artist=左、title=右。
  2. 歌手为空且 title 含上述分隔 → 同样拆出 artist/title（DFTC/通知栏场景）。
  3. title 末尾 `（01）`/`(1)`/`【02】`/`[3]` 这类 1–3 位数字括号 → 去掉后缀。
  4. `aliasArtist`：`A-Lin/alin/A Lin/Alin→黄丽玲`、`G.E.M./GEM/邓紫棋→邓紫棋`、
     `黄霄雲→黄霄云`。
- `ArtistMatchRules.java`：`static List<String> segments(String)`、`static String
  canonical(String)`、`static int score(String wanted, String found)`。
  - 分隔符：`/\s*[,，、/&;；]\s*|\s+(?:feat\.?|ft\.?)\s+/`，最多 8 段。
  - 段集合有交集 → +45；整串 normalize 后相等 → +70；否则两串互相包含 → +45，
    否则 -45；wanted 为空 → 0。
- `MatchRuleStore.java`：SharedPreferences（复用 `AppPreferences.get(context)`）持久化：
  - `match_user_rules`：个人规则包 JSON 数组
  - `match_market_rules`：已下载市场规则包 JSON 数组（含 `enabled` 由下个键控制）
  - `match_market_enabled`：已启用的市场包 id 集合（`Set<String>`）
  - API：`static List<MatchRulePack> enabledPacks(Context)`（内置 + 启用市场 + 个人，
    按内置→市场→个人排序）、`static List<MatchRulePack> userPacks(Context)`、
    `static void saveUserPacks(Context,List<MatchRulePack>)`、
    `static List<MatchRulePack> marketPacks(Context)`、
    `static void installMarketPack(Context,MatchRulePack)`、`static void
    setMarketPackEnabled(Context,String,boolean)`、`static void removeMarketPack(Context,String)`、
    `static boolean isMarketPackEnabled(Context,String)`。
  - 所有写入后调用 `AppPreferences.changed()`（若存在该 API；否则直接 apply）。
- `MatchCorrectionStore.java`：`sourcePackage + identity(rawTitle, rawArtist)` →
  修正后的 title/artist，SharedPreferences `match_corrections`（JSON 对象，最多 200 条，
  优先保留最近使用）。API：
  - `static Correction find(Context, String sourcePackage, String title, String artist)`
  - `static void remember(Context, String sourcePackage, String rawTitle, String rawArtist,
    String fixedTitle, String fixedArtist)`
  - `static void clearAll(Context)` / `static int count(Context)`
- `MatchTrace.java`：进程内最近一次匹配的只读快照（同步静态字段即可）：
  `recordInput(MatchInput)`、`recordEngine(MatchOutput)`、`recordQuery(int, String title,
  String artist)`、`recordMatch(String provider, String candidateTitle, String
  candidateArtist, int score, String sourceName)`、`static String describe()`（多行中文+
  JSON 关键字段，供诊断页复制）、`static void clear()`。线程安全；容量有上限。

修改文件：

- `MusicPlaybackData.java`：增加 `final String album;`（构造器保留旧签名，新增带 album 的
  重载，旧构造器 album="")。
- `ModernMediaSessionReader.java`：读取 `MediaMetadata.METADATA_KEY_ALBUM` 传入
  `MusicPlaybackData`（取不到就空串）。
- `NetEaseLyricClient.java`：
  - `matchScore` 的歌手部分改用 `ArtistMatchRules.score`（保持 +70/+45/-45 语义）。
  - 噪声分软/硬：软 `live|现场|演唱会|acoustic|unplugged` → 候选有而原串无时 −25；
    硬 `伴奏|纯音乐|instrumental|remix|dj|sped|slowed|ktv|翻唱|升调|降调` → −60。
  - `plausibleMatch`：硬噪声不一致仍拒绝；软噪声不因此拒绝。
  - `normalize` 保持不变（其它代码依赖）。
- `MultiSourceLyricClient.java`：
  - `load(...)` 内构造 `MatchInput(sourcePackage, currentSource, title, artist, album="")`；
    可在方法签名增加 `album` 参数（从 MusicStateStore 传入），也可读 `MusicStateStore`
    快照——两种都行，但必须把 album 传给引擎。
  - `catalogQueries` 增加重载
    `static List<LocalTrackQueryRules.Query> catalogQueries(String source, String title,
    String artist, String sourcePackage)`：原始 query 固定第 0 个；随后加入规则输出的
    primary + 候选（去重）；再对每个「基础 query」追加 `fallbackQueries` 与简繁变体；
    总数上限 24（超出截断，原始永远保留）。旧的 3 参重载保留给现有测试调用。
  - 每次尝试 query 时 `MatchTrace.recordQuery(index, query.title, query.artist)`；命中时
    `MatchTrace.recordMatch(providerId, query.title, query.artist, score, sourceName)`；
    score 未知的 provider 传 -1。
  - 在方法开头 `MatchTrace.clear()` + `recordInput`，并记录引擎输出。
- `LocalLyricClient.java`：
  - 增加 `load(String mediaUri, String title, String artist, String sourcePackage)`；
    旧 3 参重载委托（package 传 ""）。
  - `candidateNames` 增加规则产生的候选名（`MatchRuleStore.enabledPacks` 引擎输出；
    package 为空时只应用不限定包名的规则包），保留原有候选项与顺序；候选名总数上限 12。
- `MusicStateStore.java`：
  - `update` 在调用 `scheduleLyricLoad` 前（约 :443）用 `MatchCorrectionStore.find` 查
    修正；命中则用修正后的 title/artist 传给 `scheduleLyricLoad`（界面仍显示原始值），
    未命中用原始值。`lyricTrackKey` 用实际用于匹配的 title/artist，避免换歌缓存错位。
  - `reloadLyrics` 增加 5 参重载
    `reloadLyrics(Context, String overrideTitle, String overrideArtist, String
    selectedCatalogOverride, boolean remember)`：`remember=true` 时先
    `MatchCorrectionStore.remember(context, sourcePackage, 原始title, 原始artist,
    overrideTitle, overrideArtist)`；原 4 参重载委托 remember=false。
  - `diagnosticDetails()` 末尾追加 `MatchTrace.describe()` 与已记住修正条数。
- 单元测试（放 `app/src/test/java/com/zuoqirun/lyricscompanion/`）：
  - `MatchRuleEngineTest`：条件/动作/作用域/候选去重/非法包拒绝（命名组、反向引用、超大）。
  - `BuiltinMatchRulesTest`：三种 issue #174 场景（回声、比亚迪、LX Music）与 DFTC 空歌手。
  - `MatchScoringTest`：歌手分段/别名命中；软噪声 −25、硬噪声 −60；LX 场景总分 ≥100；
    `plausibleMatch` 软噪声可通过。
  - `MatchCorrectionStoreTest` 不要求（需要 Context），可抽纯函数测试 key/LRU。

### 2.1 供 UI 调用的稳定签名（UI 只依赖这些）

- `MatchInput(String title, String artist, String album, String packageName, String source)`。
- `MatchOutput` 字段：`final String primaryTitle, primaryArtist, primaryAlbum; final
  List<Candidate> candidates; final boolean changed;`。
- `MatchOutput.Candidate` 字段：`final String title, artist, ruleId, kind;`。
- `MatchRulePack` 字段：`id/name/author/description/version/schemaVersion/minVersionCode`、
  `List<String> packages/sources`、`List<MatchRule> rules`；`static MatchRulePack
  parse(String json)`（失败抛 `IllegalArgumentException`）、`String toJsonString()`。
- `MatchRule` 字段：`id`、`List<Condition> when`、`List<Action> then`（UI 生成规则时直接拼
  JSON 字符串即可，不要求构造这些内部类）。
- `MatchRuleStore`：`enabledPacks(Context)`、`userPacks(Context)`、
  `saveUserPacks(Context,List<MatchRulePack>)`、`marketPacks(Context)`、
  `installMarketPack(Context,MatchRulePack)`、`setMarketPackEnabled(Context,String,boolean)`、
  `removeMarketPack(Context,String)`、`isMarketPackEnabled(Context,String)`。
- `MatchTrace.describe()`；`MatchCorrectionStore.count(Context)`、`clearAll(Context)`。
- `MusicStateStore.snapshot(int offsetMs)` 返回 `MusicSnapshot`（现有字段）；另由 core 增加
  `static String activeSourcePackage()`（当前播放器包名，无则空串）供诊断页使用。

验收：`.\gradlew.bat :app:testDebugUnitTest --no-daemon` 通过，且新增测试覆盖上述场景。

## 3. update_server（owner：server-dev）

新增文件：

- `update_server/match-rules.js`：独立模块，导出
  `createMatchRulesHandler({stateDir, adminToken, now, requireAdmin})`，返回
  `async (req, res, url, helpers) => boolean`（处理了返回 true；未处理 false）。
  `helpers` 传入 `{sendJson, readJson, clientIp}` 由 server.js 提供；模块自身也要能在测试里
  用独立 `http.createServer` 跑起来。
- `update_server/match-rules-admin.js`：命令行审核工具（见下）。
- `update_server/test/match-rules.test.js`：`node --test`，覆盖提交/校验/审核/目录/ETag/
  下载/sha256/举报/限流/权限。
- `update_server/MATCH_RULES_API.md`：接口与运维说明。

接口（全部 JSON、UTF-8）：

- `POST /api/match-rules/submit`
  body：`{rule: <规则包对象>, description?: string, author?: string}`；
  校验规则包（与服务端同一套限制；可用简易校验，不能只信客户端）；
  成功 201：`{ok:true, id:"r-xxxxxxxx", token:"<32 hex>", status:"pending"}`；
  校验失败 400：`{ok:false, error:"<中文或英文原因>"}`；
  限流 429：同一 IP 每小时最多 5 次提交。
- `GET /api/match-rules/catalog.json`
  `{ok:true, schemaVersion:1, generatedAt:"ISO", rules:[{id,name,version,author,
  description,sha256,size,updatedAt,downloads,packages:[...]}]}`；
  带 `ETag`（内容 sha256 前 32 位），支持 `If-None-Match` → 304；
  `Cache-Control: public, max-age=300`。只含已审核通过且未下架的包。
- `GET /api/match-rules/packs/<id>/<version>.json`
  `{ok:true, id, version, sha256, rule:{...}}`；ETag + `max-age=86400`；404 JSON。
- `GET /api/match-rules/status?id=<id>&token=<token>` →
  `{ok:true,id,status:"pending"|"approved"|"rejected"|"disabled",note:"",updatedAt}`；
  token 不匹配 403。
- `POST /api/match-rules/report` body `{id, reason:"..."}`（reason ≤200）→
  `{ok:true}`，落 `STATE_DIR/match-rules/reports.jsonl`（保留最近 1000 条）。
- 管理接口（请求头 `x-admin-token: <ADMIN_TOKEN>`，缺失/错误 401）：
  - `GET /api/match-rules/admin/list?status=pending|approved|all` →
    `{ok:true, items:[{id,status,submittedAt,updatedAt,author,description,rule}]}`
  - `POST /api/match-rules/admin/review` body
    `{id, action:"approve"|"reject"|"disable", note?:""}` →
    `{ok:true,id,status}`；approve 时重新校验规则包并写入 `approved/`；
    reject/disable 保留记录。
- 存储（全部在 `STATE_DIR/match-rules/`，不写仓库）：
  `pending/<id>.json`、`approved/<id>.json`、`reports.jsonl`、`submissions.jsonl`；
  `approved/<id>.json` 结构 `{id,status:"approved",rule:{...},sha256,size,submittedAt,
  updatedAt,downloads,author,description}`。启动时扫描 `approved/` 构建目录；
  审核操作后刷新。
- 安全：不信任客户端；包大小 ≤64KB（服务端再限一次）、规则包校验、id 冲突时同 id 新版本
  必须 version 更大（否则 409）；`<id>` 白名单字符；JSON 解析异常 400；所有响应
  `X-Content-Type-Options: nosniff`。
- `server.js` 接法（最小改动，别碰维护者其它未提交逻辑）：在文件顶部加
  `const {createMatchRulesHandler} = require("./match-rules");`；在 `stateDir` 常量之后
  创建 handler（adminToken 取 `process.env.ADMIN_TOKEN || ""`）；在请求处理函数里、现有
  `/api/config/*` 路由之后加：
  `if (await matchRulesHandler(req, res, url, {sendJson, readJson, clientIp})) return;`
  （若 server.js 没有 `clientIp` 辅助函数，用 `req.socket.remoteAddress` 现算，传给模块）。
- `match-rules-admin.js`：`node match-rules-admin.js list [pending|approved|all]` /
  `node match-rules-admin.js approve <id> [note]` / `reject` / `disable`；读取
  `BASE_URL`（默认 `http://127.0.0.1:8790`）与 `ADMIN_TOKEN`，输出中文摘要，不打印 token。
- 测试约束：`server.js` 目前因维护者未提交改动 require 了不存在的 `online-stats.js`，
  **不要新建 online-stats.js，不要修它**；测试只 `require("../match-rules")` 起独立
  http server，不要 require server.js。

## 4. Android 规则界面（owner：ui-dev）

新增：

- `MatchRulesClient.java`：`API_BASE = "https://lyrics-companion.zuoqirun.top/api/match-rules"`。
  单线程 Executor + `HttpCompat.open`（复用 `ConfigurationShareClient` 的写法：7s 连接、
  10s 读取、固定长度 POST、读取错误流、JSON 解析）。方法异步回调：
  - `fetchCatalog(Callback<List<CatalogItem>>)`（解析 `rules`，ETag 可忽略）
  - `downloadPack(String id, int version, Callback<MatchRulePack>)`（校验 sha256 与 id/version）
  - `submit(MatchRulePack, String description, Callback<SubmitResult{id,token}>)`
  - `status(String id, String token, Callback<StatusResult{status,note}>)`
  - `report(String id, String reason, Callback<Boolean>)`
  失败时给中文错误（网络异常/服务器不可用/规则无效）。
- `MatchRuleEditorActivity.java`：代码 + 图形向导两种模式。
  - 顶部显示当前曲目：播放器、原始 title/artist、内置规则清洗结果（引擎输出候选）。
  - 模式切换（两个按钮/RadioGroup）：「图形向导」/「JSON 代码」。
  - 图形向导：条件（字段 Spinner、操作 Spinner、值 EditText）+ 动作（操作 Spinner、
    参数 EditText/字段选择）+「添加条件」「添加动作」「生成规则」；生成的规则追加到当前
    JSON 包（保持已有规则）。
  - JSON 代码：等宽 EditText，按钮「校验」「格式化」「插入示例」「从当前歌曲生成」
    （生成内置规则同款模板）；「预览」用当前曲目跑引擎，显示 primary/候选。
  - 「保存并启用」写到 `MatchRuleStore`（个人规则）；「提交到市场」弹作者/说明输入框后
    调 `MatchRulesClient.submit`，把 id/token 记到本地（`match_my_submissions`，
    供市场页查询状态）。
  - 「导入」支持粘贴 JSON 文本；「复制」把当前 JSON 放剪贴板。
  - 全程捕获异常，Toast 中文提示；不要在 UI 线程做网络/正则长任务（引擎执行 <20ms 可以）。
- `MatchMarketActivity.java`：
  - 列表：市场规则（名称/作者/说明/版本/已安装状态），按钮「下载并启用」「停用/移除」；
    「我的提交」区显示本地记录 id 的审核状态（刷新时查 status）。
  - 顶部「刷新市场」按钮；离线/服务器不可用显示可读提示。
  - 下载后 `MatchRuleStore.installMarketPack` + `setMarketPackEnabled(true)`。
- `MatchDiagnosticsActivity.java`：
  - 展示：播放器包名/来源、mediaId、原始 title/artist/album、时长、状态；内置规则 + 已启用
    规则对当前曲目的逐条应用结果（primary + 候选）；`MatchTrace` 的查询与命中记录；
    「复制 JSON」「清除修正记忆」（`MatchCorrectionStore.clearAll`）。
  - 复用 `MusicStateStore.snapshot` / `diagnosticDetails()` / `MatchTrace.describe()`；
    API 19 下没有的字段标注「播放器未提供」。
- 修改 `MainActivity.java`：在「音乐状态与诊断」卡片（约 :1096-1107）的「修正歌曲信息并
  重新匹配」按钮之后，追加三个按钮：「匹配规则」「规则市场」「匹配诊断」，分别启动上面三个
  Activity；在 `showLyricRematchDialog`（:3032）里加 CheckBox「记住这次修正（按播放器）」
  （默认勾选），确认时调用新的 5 参 `reloadLyrics(..., remember)`；配色/字号沿用
  `text()`/`button()` 辅助方法与现有 Material 组件，API 19 安全。
- 修改 `DisplaySettingsActivity.java`：在「当前歌曲校正」卡片（:520）之后加「匹配规则」
  卡片（`addLink` 或按钮），入口同上；必须给 `SettingsCatalog.Category.MAINTENANCE`。
- 修改 `AndroidManifest.xml`：注册三个 Activity（`android:exported="false"`，
  `android:theme` 沿用主主题；父 Activity 可指向 MainActivity）。

约束：所有 Activity 用 Java 代码构建界面（仓库无 Activity XML 布局）；不要新增依赖；
不要改 `MatchRule*` 核心类的语义（只调用）；网络在后台线程，回调后 `runOnUiThread`。

### 4.1 MatchRulesClient 稳定签名（编辑器与市场页共用）

```java
interface Callback<T> { void complete(Result<T> result); }
final class Result<T> { final boolean ok; final T value; final String message; }

static void fetchCatalog(Callback<List<CatalogItem>> cb);
static void downloadPack(String id, int version, Callback<MatchRulePack> cb);
static void submit(MatchRulePack pack, String description, String author,
                   Callback<SubmitResult> cb);
static void status(String id, String token, Callback<StatusResult> cb);
static void report(String id, String reason, Callback<Boolean> cb);
static void rememberSubmission(Context context, String id, String token, String name);
static List<Submission> submissions(Context context);   // 本地记录，按时间倒序
static void updateSubmissionStatus(Context context, String id, String status);

final class CatalogItem { String id, name, author, description, sha256;
                          int version, downloads; long size; }
final class SubmitResult { String id, token, status; }
final class StatusResult { String status, note; }
final class Submission { String id, token, name, status; long submittedAt; }
```

`submit` 的 `pack` 由编辑器传入；客户端负责序列化 `{rule: pack.toJson(), description,
author}` 并解析 201 响应。`rememberSubmission` 写 SharedPreferences
`match_my_submissions`（JSON 数组，最多 100 条）；市场页读同一键。

## 5. GitHub 模板（owner：lead）

`.github/ISSUE_TEMPLATE/match_rule.yml`：Issue Form，字段：规则名称、适用播放器（包名）、
问题描述（原始元数据/期望结果）、规则 JSON、是否同意收录到规则市场、测试过的歌曲。
说明里写清：优先在应用内「匹配规则 → 提交到市场」上传；此模板用于无法应用内上传或反馈规则
问题。`config.yml` 不改。

## 6. 验收与构建

1. `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过（lint 只要求
   不新增 error；warning 记录在报告里）。
2. `cd update_server; node --test test/match-rules.test.js` 通过；`node --check
   server.js match-rules.js` 通过。
3. 手动冒烟（模拟器或手机）：编辑规则 → 保存 → 诊断页可见规则效果 → 手动修正勾选记住 →
   重进播放器后仍生效；市场页在服务器不可达时给出中文错误而不是崩溃。
4. release 构建：`APP_SIGNING_*` 由 lead 从本地 `secret.md` 注入（不打印、不提交）；
   `APP_VERSION_CODE` 取当前线上 beta + 1；`.\gradlew.bat :app:assembleRelease`。
5. 安装到 192.168.1.41（adb 连接可用时）：`adb install -r -d`。
