# 元数据匹配规则设计（issue #174）

> 状态：方案讨论稿，未实现。范围：播放器元数据清洗、查询候选生成、手动修正记忆、规则下发/编辑/分享。
> 结论先行：方向可行，但建议把「音频匹配规则」改名为「元数据匹配规则」，并把「正则返回歌名/歌手」扩展为「规则产出清洗后的字段 + 查询候选」；评分策略与别名表不进用户可编辑规则，先做内置默认规则修好 #174，再上规则系统与市场。

## 1. 背景与失败点

issue #174 的三类典型样本：

| 样本 | 播放器给出 | 期望 | 现有行为 |
| --- | --- | --- | --- |
| 回声音乐 | title=`张学友 - 吻别`，artist=`车载音乐` | title=`吻别`，artist=`张学友` | 查询串 `张学友 - 吻别 车载音乐`，六词库全不命中；手动修正不记忆 |
| 比亚迪车机 | title=`单身情歌（01）`，本地 `单身情歌.lrc` | 去掉纯数字括号序号 | `QUALITY_OR_VERSION_TAG` 不覆盖纯数字，`candidateNames()` 无清洗名 |
| LX Music | `千年之恋` / `A-Lin/黄霄雲`，候选 `千年之恋 (live)` / `黄丽玲,黄霄雲` | 艺名↔本名、歌手拆分后命中 | `matchScore` 标题 contains=55，歌手 −45，加噪声 −60，远低于阈值 100 |

现有链路（`app/src/main/java/com/zuoqirun/lyricscompanion/`）：

1. 采集：`ModernMediaSessionReader.playbackData(...)` :204-239（title/artist/mediaId/duration，extras 只读酷我 `AUDIO_LYRIC` :228-231）；API 19 走 `LegacyRemoteControllerReader`；DFTC 路径 `DftcMediaSessionReader` :249-265 artist 恒空。
2. 汇总：`MusicStateStore.update(...)` :89-455，复合歌手拆分 :103-109，`SessionMetadataRules.usableTitle` :19-24 过滤占位。
3. 查询：`MultiSourceLyricClient.load(...)` :68-173；本地 `LocalLyricClient.load(...)` :174-235 与 `candidateNames` :433-446；在线 `catalogQueries(...)` :239-255 = 原始 query + `LocalTrackQueryRules.fallbackQueries()` :27 + 简繁变体，query×provider 双层循环 :134-171。
4. 打分：全部 6 个 provider 复用 `NetEaseLyricClient.matchScore(...)` :165-190，阈值 ≥100；`plausibleMatch` :141-163 还会拒绝时长差 >15s、版本噪声不一致。
5. 缓存：`MatchedLyricCache.key(provider, title, artist, duration, directId, package)` :45-57。

缺口：

- 清洗规则硬编码且分散在 `LocalTrackQueryRules`（文件名层）、`SessionMetadataRules`（占位层）、`NetEaseLyricClient.normalize/hasVersionNoise`（打分层），没有统一入口。
- `MusicPlaybackData` 没有 `album`，没有播放器包名/原始 extras；诊断只有一段字符串（`MainActivity` :1096-1107），看不到规则与候选详情。
- 手动修正 `MusicStateStore.reloadLyrics` :689-742 只对当前 generation 生效，不落盘（`trackKey` :721 仍用原始 title/artist），所以每次轮询都被播放器元数据覆盖——这是 issue 里「不记忆」的直接原因。
- 没有远程规则机制；但 `LyricSourceRules.java` 已经提供了一个可复用的下发范式（见 §7）。

## 2. 总体设计

### 2.1 三层规则来源

| 层 | 内容 | 谁维护 | 何时生效 | 可信度 |
| --- | --- | --- | --- | --- |
| A. 内置默认规则 | 通用清洗、别名表、软/硬版本噪声、打分修正 | 应用代码 | 始终，先于其他层 | 最高，随包测试 |
| B. 审核规则包 | 按播放器包名/来源的清洗与查询候选 | 开发者 + 维护者审核 | 服务器下发或导入分享码 | 中，只允许数据动作 |
| C. 个人规则 | 同一 schema 的子集，图形化生成 | 用户自己 | 本地立即生效 | 低，只作用于本机 |

合并顺序：A → B（同一作用域可叠加，按包版本排序）→ C（个人规则优先，作用域默认只匹配指定播放器包名）。**原始元数据永远保留为 query 0**，任何规则都只能追加候选，不能删掉基线查询，避免规则写坏后连原来能命中的歌也丢了。

### 2.2 处理流程

```
MediaSession / 通知 / 本地文件
        │  (raw)
        ▼
SessionMetadataRules 占位过滤（不变）
        │
        ▼
MatchRuleEngine：内置规则 → 下发包 → 个人规则
        │  产出：cleanTitle/cleanArtist/cleanAlbum + 每一步快照
        ▼
查询候选：原始对（query 0）→ 清洗对 → 拆分/去序号/别名变体 → 上限 8 条
        │
        ▼
本地匹配 LocalLyricClient.candidateNames（用清洗后的名字）
        │
        ▼
在线匹配 catalogQueries（原始查询仍排第一）
        │
        ▼
matchScore（内置打分修正：别名/拆分/软噪声；不读用户规则）
        │
        ▼
命中缓存 / 诊断（记录应用的规则、候选、分数）
```

### 2.3 命名与边界

- 统一叫 **元数据匹配规则（Match Rules）**。「音频匹配」容易被理解为音频指纹/听歌识曲，而本 issue 需要的只是字符串清洗与查询构造。
- 规则是**数据**，不是代码。开发者「纯代码」模式指直接编辑 JSON（可从 GitHub 拉取），永远不在设备上执行 JS/脚本。
- 用户/远程规则只允许：改 title、artist、album，追加查询候选，按 scope 启用/停用。
- **不允许**规则改词库 URL、改 provider 顺序、改全局阈值、写文件、联网。别名表与软噪声策略作为内置代码 + 单独下发的 `aliases` 数据存在，见 §3.4、§4.3。

## 3. 规则数据模型（JSON）

### 3.1 包结构

```json
{
  "schemaVersion": 1,
  "id": "echo-player-cleanup",
  "name": "回声音乐元数据清洗",
  "version": 2,
  "channel": "stable",
  "author": "community 或开发者署名",
  "description": "处理“歌手 - 歌名塞进 title、文件夹名当歌手”",
  "minVersionCode": 240,
  "scope": {
    "packages": ["com.echo.music"],
    "sources": ["echo"]
  },
  "rules": [
    {
      "id": "split-artist-title",
      "when": [
        { "field": "artist", "equalsAny": ["", "车载音乐", "未知", "Unknown"] }
      ],
      "then": [
        {
          "type": "extract",
          "from": "title",
          "regex": "^(.{1,40}?)\\s+[-–—－]\\s+(.+)$",
          "targets": { "artist": 1, "title": 2 }
        }
      ],
      "stop": true
    },
    {
      "id": "drop-folder-artist",
      "when": [
        { "field": "artist", "equalsAny": ["车载音乐", "我的音乐"] }
      ],
      "then": [
        { "type": "clear", "field": "artist" }
      ]
    },
    {
      "id": "strip-ordinal-suffix",
      "when": [
        { "field": "title", "regex": "\\s*[（(]\\d{1,3}[)）]\\s*$" }
      ],
      "then": [
        {
          "type": "replace",
          "field": "title",
          "regex": "\\s*[（(]\\d{1,3}[)）]\\s*$",
          "replacement": ""
        }
      ]
    }
  ]
}
```

字段约束：

- `schemaVersion` 目前 1；未知版本整包拒绝（不猜）。
- `scope.packages` 为空表示不按包名限制，但必须至少指定 `sources` 或给出明确 `scope`，避免误伤全部播放器；内置规则例外。`sources` 允许 `local`、`notification`、`dftc` 等采集来源，便于覆盖本地文件与通知回退。
- 审核包的启用策略：`scope.packages` 非空且渠道匹配时默认启用；全局作用域（无 packages）默认不自动启用，需用户确认。个人规则与分享码包一律需用户确认后启用。
- `minVersionCode` 由客户端比较，低于它的包忽略。
- 单包上限：32 KB、64 条规则、每条 2 KB、正则 ≤200 字符；服务端与客户端都校验。
- 每条规则 `id` 必填且包内唯一；`when` 可选（全部为 AND）；`then` 至少 1 个动作。
- `stop: true` 表示「该规则至少有一个动作生效后，跳过后续规则」；动作全部 no-op 时不停。

### 3.2 动作（`then`）

| op | 参数 | 说明 |
| --- | --- | --- |
| `extract` | `from`、`regex`、`targets` | 从字段抽取；`targets` 是 `{字段: 组号}`，只允许 1–9 的数字组 |
| `replace` | `field`、`regex`、`replacement` | Java `Matcher.replaceFirst` 语义，`$1`…`$9` |
| `set` | `field`、`value` | 字面量，支持 `$n` 引用当前字段最近一次匹配的组 |
| `clear` | `field` | 清空 artist/album（title 不允许清空） |
| `alias` | `field`、`map` | 仅个人/审核包可用的显式替换表；例如 `{"黄丽玲":"A-Lin"}` |
| `trim` | `field` | 去首尾空白与 Unicode 零宽字符 |

**不使用命名捕获组** `(?<name>...)`：旧 Android 对命名组的支持并不一致，而本项目要兼容 API 19，统一用编号 `$1`/`$2` 更稳妥。

### 3.3 条件（`when`）

扁平数组，每项一个字段一个运算符，全部 AND：

| 运算符 | 适用字段 | 说明 |
| --- | --- | --- |
| `equalsAny` | title/artist/album/package/source | 去空白后精确比较（不做大小写/全半角归一是刻意的，避免条件意外扩大） |
| `containsAny` | title/artist/album | 子串 |
| `regex` | title/artist/album | 一句话说清：`Pattern.compile` + `find()`；用于拆分/去序号前的门控 |
| `in` | package/source | 集合包含，包名选择器生成 |
| `range` | durationMs | `{min,max}` |

`package`/`source` 来自采集层新增字段（见 §4.2）。`extras` 在 v1 **不作为条件**，只用于诊断展示，防止规则依赖播放器私有键并泄露数据。

### 3.4 别名与打分数据（单独下发，审核制）

LX 那个例子本质是「歌手别名 + 复合歌手拆分 + 软噪声」问题，不适合让每个用户写正则：

```json
{
  "schemaVersion": 1,
  "version": 7,
  "channel": "stable",
  "artistAliases": [
    ["黄丽玲", "A-Lin"],
    ["alin", "黄丽玲"],
    ["陈奕迅", "Eason Chan"]
  ],
  "softNoiseTags": ["live", "现场", "演唱会", "unplugged"],
  "hardNoiseTags": ["伴奏", "纯音乐", "remix", "dj", "sped", "slowed", "ktv", "升调", "降调"]
}
```

- 别名表用于 `matchScore` 的歌手比较（规范化后集合求交），不直接改查询串；查询串仍用原始歌手，避免把「A-Lin」硬替换成「黄丽玲」导致其他词库不命中。
- `softNoiseTags` / `hardNoiseTags` 只影响打分：候选标题核心相同且命中 soft tag 时，扣分从 −60 降为 −25；hard tag 保持 −60（伴奏/纯音乐没有可对齐的歌词，必须拒绝）。
- 该文件只接受服务器下发/内置，个人规则不能改（否则用户 A 的别名会污染所有歌曲的匹配）。

这样规则包保持「字符串处理」的简单语义，匹配策略仍是集中、可测试、可回滚的代码或审核数据。

### 3.5 版本与兼容

- 包内 `version` 单调递增；同 id 只保留最高版本，降级需用户手动。
- 客户端忽略未知字段（前向兼容），但对未知 `op` / `schemaVersion` 整包拒绝。
- 规则变更后，把包版本号并入 `MatchedLyricCache.key`，一次性失效旧缓存；本地规则变更也 bump 一个本地 revisions 计数。
- 规则包可以声明 `replaces: ["old-pack-id"]` 做合并迁移；不实现则靠人工下架。

## 4. Android 端引擎

### 4.1 新类与职责（纯 Java，可 JVM 单测）

建议新增：

- `MatchRuleEngine.java`：解析/校验/编译规则包，`apply(MatchInput) → MatchOutput`。无 Android 类型，仿 `TrackIdentityRules` 的纯函数风格。
- `MatchRulePack.java`：不可变模型（id/version/scope/rules/compiled patterns）。
- `MatchInput.java` / `MatchOutput.java`：输入 title/artist/album/durationMs/package/source/mediaUri；输出 clean 字段、逐步快照、显式追加查询。
- `BuiltinMatchRules.java`：内置规则与别名（Java 常量 + 与 JSON 同构的测试 fixture），保证离线可用。
- `MatchRulesClient.java`：拉 catalog/pack，校验 sha256，缓存（仿 `LyricSourceRules`）。
- `MatchCorrectionStore.java`：手动修正记忆（见 §4.4）。
- `MatchTrace.java`：诊断采集（见 §5）。
- `MatchRuleEditorActivity.java`、`MatchDiagnosticsActivity.java`：两个新界面。

现有类改动集中而小：

- `MultiSourceLyricClient.catalogQueries` :239-255 改为调用 `MatchRuleEngine.queries(input)`，旧签名保留重载给测试；`LocalTrackQueryRules.fallbackQueries` 退化为内置规则实现，避免行为分叉。
- `LocalLyricClient.candidateNames` :433-446 接收清洗后的 title/artist（或直接接收 `MatchOutput`），加入去序号、拆分后的候选名；`load` :174 的调用方在 `MusicStateStore.scheduleLyricLoad` :820-882 传新参数。
- `MusicPlaybackData` 增加 `album`、`sourcePackage`、可选 `extras` 快照；`ModernMediaSessionReader` :204-239 读取 `METADATA_KEY_ALBUM`/`METADATA_KEY_ALBUM_ARTIST`，用 `controller.getExtras()`（:228 已有范例）取白名单键。
- `MusicStateStore.update` :89-455 在根上构造 `MatchInput`，把 `MatchOutput` 传给查询与本地匹配；**身份判定仍用原始值**，避免规则改动导致「同一首歌被当成新歌」反复重建歌词。
- `MatchedLyricCache.key` :45-57 增加规则版本因子。
- `AppPreferences` 增加全局 key（不要用 `displayWriteKey` 的屏×样式前缀，规则是跨屏的）。

### 4.2 接入点原则

1. **原始查询永远排第一**：`catalogQueries` 返回 `[original] + cleaned variants`，命中逻辑不变。
2. **本地文件优先用清洗名**：比亚迪样本的 `.lrc` 就叫 `单身情歌.lrc`，`candidateNames` 必须包含去序号名；同时保留原始名，避免误删真实括号内容。
3. **显示元数据保持原样**：浮层继续显示播放器给的 title/artist（用户认的是这个）；诊断页再展示清洗结果，并可加「已按规则 X 匹配」的小字。
4. **规则只影响匹配会话**，不写回播放器、不改 `MusicPlaybackData` 原字段。
5. **失败回落**：规则产出空 title、超长、含控制字符、或全部候选都未命中时，行为与没有规则完全一致。

### 4.3 内置默认规则（P0，先修 #174）

即使不下发、不开编辑器，这些也应随包生效：

1. `splitArtistTitle`：title 形如 `A - B` / `A – B` / `A — B` / `A － B`，且 artist 为空或属于「容器名」候选时，拆成 artist=A、title=B；原查询保留。容器名候选包括专辑名、`mediaUri` 目录名、内置常见词（车载音乐、我的音乐、Music、Unknown 等）。
2. `dropContainerArtist`：artist 等于专辑名/目录名，且 title 已包含该 artist 或拆分后能覆盖时，丢弃 artist。
3. `stripOrdinalSuffix`：仅当标题末尾是**唯一**一个括号纯数字 1–3 位（`（01）`、`(2)`）时删除；`（Live）`、`（伴奏）` 不在此列，交给噪声处理。
4. `normalize` 升级：NFKC 全半角归一（`java.text.Normalizer`，API 9+）+ 大小写折叠 + 零宽字符清理，统一用于身份与打分。
5. `artist compare` 升级：歌手串按 `/`、`,`、`，`、`、`、`&`、`feat.` 拆成集合；集合有交集即按命中计分；别名表规范化后比较。
6. `noise` 分级：soft noise（live/现场…）候选核心标题相同只扣 25；hard noise 保持 −60；`plausibleMatch` 不再要求「噪声必须一致」，改为 hard noise 才拒绝。
7. 时长兜底不变（±2s +35、±5s +25、±15s +5），避免为了修一个案例放宽全局阈值。

目标：回声、比亚迪、LX 三个样本在**不配置任何规则**时先能命中；规则系统是给后续新播放器的扩展，不是 #174 的前置条件。

### 4.4 手动修正记忆（issue 明确要求）

`MusicStateStore.reloadLyrics` :689-742 现在只是临时覆盖。改为：

1. 每次手动修正写入 `MatchCorrectionStore`：键 `sourcePackage + "|" + (album 或 mediaUri 目录名 或 identity(rawTitle|rawArtist))`；值 `{title, artist, catalog?, createdAt, hitCount}`；SharedPreferences `match_corrections`（JSON，最多 200 条 LRU）。
2. `MusicStateStore.update` 在构造 `MatchInput` 后先查修正表：命中则用修正值构造查询，**身份判定仍用原始值**；这样轮询不再把修正冲掉。
3. 修正值优先级：手动修正 > 个人规则 > 下发规则 > 内置规则；修正值同样保留原始 query 0（以防修正本身错了）。
4. 对话框增加两个动作：「仅本次」（原行为）与「记住（本播放器）」（新默认）；再提供「记住并生成规则」——如果修正等价于一次 title/artist 拆分，直接生成一条个人规则并让用户确认 scope，把「记忆」升级成可编辑、可分享的规则。
5. 修正表与规则都进诊断（规则 id / 修正来源），便于以后排查「为什么匹配到这首」。

### 4.5 线程与缓存

- 规则编译在加载时一次完成，`Pattern` 缓存在 pack 对象里；匹配线程只做 `find()`，不动态编译。
- 规则引擎是纯函数、无锁；包快照用 volatile 引用替换，与 `LyricSourceRules` 的 `rules` 写法一致。
- 每首歌的规则执行设软预算：最多 64 条规则、每字段最多 200 字符；累计耗时超过 20ms 则本次跳过剩余个人规则并记一条诊断（正常规则远低于此）。
- 规则版本进 `MatchedLyricCache.key`，规则更新后旧歌词缓存自然失效，不需要手动清缓存。

### 4.6 API 19 约束

- `java.util.Base64` 是 API 26；分享码/内嵌 JSON 用 `android.util.Base64`。
- 命名捕获组不用（见 §3.2）；正则只用 `Pattern` 基础能力 + `CASE_INSENSITIVE`。
- `Normalizer` API 9 可用；`MediaMetadata` extras 是 API 21+，API 19 的 RemoteController 只能拿有限字段——诊断页要明确标注「该播放器/系统版本不提供」。
- 编辑器控件沿用现有 `SettingsNavigator` + 程序化布局（`LayoutEditorActivity` 模式），不引入 RecyclerView/第三方库。
- 规则文件分享复用 FileProvider；现有 authority `${applicationId}.updatefiles` 只暴露 APK 路径，需要扩展 `update_file_paths.xml`。暂不做二维码（zxing 对 API 19 体积/维护不划算）。

## 5. 诊断：暴露 MediaSession 与默认规则尝试信息

### 5.1 采集字段

| 来源 | 字段 | API 21+ | API 19 |
| --- | --- | --- | --- |
| MediaSession | package、sourceName、title、artist、album、albumArtist、subtitle、displayTitle、mediaId、durationMs、state、position、speed | 是 | RemoteController 有限字段，缺失标「不可用」 |
| MediaMetadata extras | 白名单键：DISPLAY_TITLE/DISPLAY_SUBTITLE/ALBUM/ALBUM_ARTIST/TRACK_NUMBER/DISC_NUMBER/YEAR/GENRE/COMPILATION/DESCRIPTION + 已知 provider 私有键（酷我 AUDIO_LYRIC 等） | 是 | 否 |
| 本地文件 | mediaUri、显示文件名、目录名、sidecar `.lrc`/`.krc` 是否存在 | 是 | 是 |
| 通知回退 | NotificationTrackParser 的 title/text/subText/label | 是 | 是 |
| 默认规则结果 | 每步规则 id、清洗后字段、查询候选列表、本地候选名 | 是 | 是 |
| 在线匹配 | provider、候选 title/artist/duration、分数拆解（标题/歌手/时长/噪声）、选中原因、缓存命中 | 是 | 是 |

### 5.2 匹配详情页

新增 `MatchDiagnosticsActivity`，入口挂两处：MainActivity 的「音乐状态与诊断」卡片 :1096-1107，以及规则编辑器页尾。

页面分区：

1. **原始信息**：上述采集字段，可一键复制 JSON。
2. **默认规则尝试**：逐步显示 `raw → 规则 id → clean`，以及最终 `queries` 列表（每条标注来源：原始/拆分/去序号/别名）。
3. **匹配结果**：本地命中文件、在线各 provider 的最佳候选与分数拆解；未命中时给 `MediaDiagnosisRules` 的结论。
4. **规则调试**：把当前曲目当输入，选一个规则包做 dry-run，显示规则命中/未命中原因；不上传、不联网（除非用户点「试搜」）。
5. **上传**：沿用现有诊断快照上传（MainActivity :2907），只上传用户主动触发的快照，规则文本随快照附带可选勾选。

### 5.3 隐私边界

- 原始 extras 可能含 token/会话数据：默认只展示白名单键；「显示全部 extras」需要二次确认，且复制/上传前提示。
- 规则分享只包含规则 JSON，不含用户媒体库、包名列表、播放历史。
- 规则市场的 fixture 只放公开歌曲名/歌手名作为测试样本，不放诊断快照。
- 诊断上传仍是显式动作，不做后台自动上传。

## 6. 两种编辑器

### 6.1 个人图形化编辑器

入口：设置 → 交互/维护分类下 `addLink("匹配规则…")`（`SettingsNavigator.addLink` :150；规则是全局设置，不能按主/副屏或样式分叉）。

界面（沿用 `LayoutEditorActivity` 的程序化构建）：

- 顶部：规则包列表（内置只读、个人可编辑、已导入分享包），每项显示作用域和启用开关。
- 条件卡：播放器（复用 `OverlayVisibilitySettingsActivity` 的包名列表/搜索/手动输入模式）、标题/歌手/专辑（包含/等于/正则）、时长范围；图形模式只暴露常用动作，正则输入放在「高级」折叠区。
- 动作卡：拆分标题（选分隔符，预览 `$1/$2`）、去掉歌手、删除尾部序号、替换文本、清空字段、加别名。
- 实时预览：上半显示当前 MediaSession 原始字段，下半显示每步之后的 title/artist 和生成的查询候选；提供「用当前播放测试」和「粘贴样本测试」两个输入源。
- 保存/撤销：每步操作可撤销（`LayoutEditorView` 没有 undo，这里要补一个简单的操作栈）；保存时整体校验，失败给中文原因（哪条规则、哪一项超限）。
- 导出/分享：`复制 JSON`、`导出文件`（FileProvider）、`生成分享码`（服务端短码，见 §7）。导入时先预览 diff 与作用域，标「未审核」并要求确认启用。
- 「从这次修正生成规则」：手动修正后弹出建议，把修正转成个人规则；无法泛化时只写修正记忆。

### 6.2 开发者模式（纯代码 + GitHub）

- 应用内「代码模式」：等宽 `EditText` 编辑整包 JSON；支持「从 GitHub 拉取 URL」——只允许 `https://raw.githubusercontent.com/` 和 `https://github.com/`（禁止重定向到其他域、超时 5s、上限 64KB）；本地校验后标「未审核」保存。
- 正式流程仍然是 GitHub PR：
  - 规则源目录：`rules/packs/<id>.json` + `rules/fixtures/<id>.json`（fixture = 输入元数据 + 期望 clean 字段 + 期望查询候选）；`rules/README.md` 写 schema 与评审标准。
  - CI 校验：JSON schema、正则安全检查（§8）、fixture 全过、包大小/条数限制、id 不重复；PR 必须带 fixture。
  - 审核：维护者 review + 合并；合并后由发布脚本写入 `update_server/public/rules/`，生成 catalog（sha256、size、minVersionCode、version、channel）。
  - 版本：改规则必须 bump `version`；同 id 旧版本在 catalog 保留一段时间用于回滚。
- 是否放主仓库：推荐放主仓库 `rules/`，与 App 版本/审核流程同一条 CI；如果规则贡献者多、不想让主仓库变嘈杂，再拆独立仓库并让 sync-release 拉取。不要放 `update_server/` 里，避免服务端代码与内容耦合。

### 6.3 规则市场（P3，可选）

- 服务端 catalog 增加 `reviewed: true` 的包列表；客户端「规则市场」页显示名称、作者、作用域、版本、更新时间、安装量（如果采集）、启用按钮。
- 审核状态机：`draft → submitted(PR) → approved(merged) → published → deprecated/takedown`；下架只需 catalog 标 `status: "disabled"`，客户端回退到内置规则。
- 举报：`POST /api/rules/report`（限流复用 `RateLimitedStore`），管理员处理；被举报包可先远端禁用，再人工复核。
- 分享码（个人规则）与市场（审核包）分开：分享码导入的包永远标「未审核、仅本机」，不出现在市场排序里。
- 运营成本真实存在：没有审核人时，市场会退化成垃圾规则仓库。建议先做「首批内置规则 + 分享码」，市场等有稳定贡献者再上。

## 7. 服务端下发

### 7.1 可复用现状

- `update_server` 是 `node:http` 单文件服务（`server.js` :291 创建 server），无框架/无数据库，持久化是 `STATE_DIR` 下的 JSON/JSONL；静态托管在 `public/`（`server.js` :439-441）。
- `LyricSourceRules.java` 已经是「服务器下发 JSON 规则」的完整范式：`CONFIG_URL` :21、6 小时刷新 :23、SharedPreferences 缓存 :128-130、`validated()` 白名单校验 :137-189、内置 URL 始终可用。新规则下发直接照抄这套，不引入新依赖。
- 分享短码已有现成实现：`POST /api/config/share`（`server.js` :360）与 `/api/config/import` :371；`config-share.js` 的 8 位码 `CODE_PATTERN` :7、90 天 TTL、64KB 上限、落盘 `state/config-shares/<CODE>.json`。客户端 `ConfigurationShareClient` :16-39 的传输层可复用。
- 缺口（需要新建，不能假装已有）：
  - **ETag/304**：`sendJson`/`sendFile` 写死 `cache-control: no-store`，全仓无 `If-None-Match`。
  - **清单与校验**：catalog 的 sha256/size/minVersionCode/status 需要新写；客户端下载后校验 sha256（可参考 `AppUpdater` 的 APK 摘要校验）。
  - **审核/作者/举报/下架后台**：完全没有，最小闭环只能靠 GitHub PR + CI。
  - **压缩分享**：现有分享码是明文 JSON，无 gzip/Base64；API 19 下要新写 `Deflater` 编码（仓库只有 `InflaterInputStream` 解压）。
  - **启动阻塞**：当前工作区未提交改动在 `server.js:12` 引入 `require("./online-stats")`，但 `online-stats.js`、`public/stats.html|stats.js` 不存在，服务端现在无法启动；加规则路由前必须先让维护者处理这批改动（不要顺手提交/格式化它们）。

### 7.2 端点与文件

```
GET  /api/match-rules/catalog.json        # 支持 ETag/If-None-Match
GET  /api/match-rules/<id>/<version>.json # 带 sha256 响应头
GET  /api/match-rules/aliases.json        # 别名/噪声数据，reviewed only
POST /api/match-rules/share               # 个人规则分享码（可选，复用 config-share 模式）
POST /api/match-rules/import              # 取分享码内容
POST /api/match-rules/report              # 举报（限流）
```

catalog 字段：`schemaVersion`、`generatedAt`、`channel`、`packs[]`（id/name/version/author/description/scope/size/sha256/minVersionCode/status/updatedAt）、可选 `aliases`（version/sha256/url）、可选 `disabled[]`。

发布方式：

1. 审核合入 `rules/` 后，CI 生成 `update_server/public/rules/<id>/<version>.json` 与 catalog（含 sha256），一并提交/部署。
2. 紧急下架走 `POST /api/admin/match-rules/disable`（ADMIN_TOKEN），把状态写入 `STATE_DIR` 覆盖 catalog，不依赖重新部署。
3. `public/rules/` 允许被静态托管，但路由显式处理 ETag 与 `application/json`，避免 `no-store` 让客户端每次全量下载。

客户端：

- `MatchRulesClient` 启动后 24h 检查一次，或设置页「立即检查」；只在 Wi-Fi？规则很小（<64KB），不必强制；失败静默回退。
- 校验：`sha256`、大小、`schemaVersion`、`minVersionCode`、`scope` 字段合法；任何一项失败整包丢弃并保留旧包。
- 缓存：`filesDir/match-rules/index.json` + 每个包一个文件；同时存 `lastEtag`。离线时用缓存；首次安装用内置规则。
- 渠道：`stable` 跟 App stable，`beta` 跟 beta；规则页显示来源与版本。

### 7.3 安全

- 只下发**数据**，客户端完全不执行规则中的任何脚本/URL。
- 正则安全检查：长度 ≤200；禁止反向引用 `\1`；拒绝嵌套量词启发式（组内已有 `+/*/{n,}` 再被外层量词修饰）；拒绝 `(?{`、`(?(...)` 等非 Java 语法；编译失败整条规则丢弃并日志。
- 输入截断：title/artist/album 各 200 字符，规则匹配前截断；规则动作输出再做一次长度/字符校验。
- 大小/条数：catalog 64KB、单包 32KB、64 条规则；服务端上传接口同样限制（当前 `/api/config/share` 无鉴权无限流，是已知刷盘风险，新接口不要重复这个错误）。
- 信任分级：市场包 `reviewed=true`；分享码包 `reviewed=false`；个人规则只本机。未审核包在 UI 上必须显式提示。
- 签名（可选）：HTTPS + sha256 只能防传输损坏，防不了服务端被篡改；若市场做大，可用 ECDSA/RSA 对 catalog 签名（API 19 的 `java.security` 可用），公钥硬编码在 APK。第一版不建议做，先接受「服务器即信任根」。
- 隐私：规则包不携带用户数据；分享码只含规则文本；catalog 不采集设备标识，除非明确加统计项。

## 8. 安全与正确性（Android 侧）

- **ReDoS**：输入长度上限 + 规则数上限 + 简单嵌套量词检测 + 每首歌 20ms 软预算；超预算的个人规则本次跳过并记诊断。不要试图用 `Thread.stop` 中断正则（已废弃且危险）。
- **顺序与冲突**：规则数组顺序即执行顺序；`stop` 只在实际生效后停；同一字段多次 `set` 以最后一条为准；引擎只基于原始输入跑一次链，避免规则之间互相触发形成环。
- **误伤**：任何规则导致的命中都记录 `ruleId`；诊断页提供「这条规则不对」→ 停用该包/该规则；下发包可远端禁用。
- **空/非法输出**：clean title 为空、超长、含控制字符 → 该规则视为 no-op；所有候选都非法 → 退回原始输入。
- **恒等性**：同样的原始输入 + 同样的包版本 → 同样的输出；诊断可复现。
- **缓存**：规则版本进匹配缓存 key；修正表变更也 bump 本地 revision；失败结果不跨版本缓存。
- **回归安全**：规则引擎只在「原始查询失败之后」才增加成本（原始 query 仍第一），最坏情况是多几次网络请求；设置「每条查询最大并发/词库」沿用现有逻辑，不新增限流压力。

## 9. 分期计划

| 阶段 | 内容 | 主要文件 | 验收 | 粗估 |
| --- | --- | --- | --- | --- |
| P0 内置清洗（先修 #174） | 内置规则 + 全半角/别名/拆分打分 + 修正记忆 + 诊断增强 | `MatchRuleEngine`（或先叫 `MetadataCleaner`）、`MultiSourceLyricClient`、`LocalLyricClient`、`NetEaseLyricClient`、`MusicStateStore`、`MusicPlaybackData`、新诊断 Activity | 三个 issue 样本命中；修正跨轮询保留；单测+lint+beta 包 | 1–2 天 |
| P1 规则引擎与个人编辑器 | JSON schema/校验/引擎、图形化编辑器、代码模式、本地导入导出、预览/撤销 | `MatchRulePack`、`MatchRuleEditorActivity`、`AppPreferences`、`AndroidManifest.xml`、`update_file_paths.xml` | 创建/保存/停用/导入导出规则；非法包与危险正则被拒 | 3–5 天 |
| P2 下发与开发者流程 | catalog/ETag/sha256、RulesClient、GitHub CI 校验、发布脚本、设置项「检查更新」、beta 渠道 | `update_server`（catalog/静态路由/测试）、`.github/workflows` 新校验、`rules/` 目录、`MatchRulesClient` | 304 生效、sha256 错包拒绝、离线回退、审核包可更新 | 2–4 天 |
| P3 市场与治理 | 审核状态机、市场页、分享码、举报/下架、安装量、回滚 | `update_server`（报告/管理接口）、市场 Activity、catalog 扩展 | 包上架/下架/举报闭环；分享码导入预览 | 4–7 天 + 持续运营 |

建议发布路径：P0 直接进 beta 并关闭 #174 的主要诉求；P1/P2 作为一个功能批次；P3 视社区活跃度再决定，不承诺日期。

## 10. 测试与验收

JVM 单测（新增）：

- `BuiltinMatchRulesTest`：`张学友 - 吻别` + `车载音乐` → `吻别`/`张学友`；`单身情歌（01）` → `单身情歌`；不误删 `（Live）`；`title` 本身含 ` - ` 且 artist 正常时不拆。
- `MatchRuleEngineTest`：顺序/stop/clear/alias/上限/非法输出/未知 op/重复 id。
- `MatchScoreTest`（扩充 `NetEaseLyricClientTest`）：LX 样本 ≥100；soft/hard noise 分级；别名与复合歌手；时长组合不回归。
- `MatchCorrectionStoreTest`：键生成、LRU、命中覆盖、原始值保留。
- `LocalLyricClient` 候选名测试：去序号后能命中 `单身情歌.lrc`。
- 现有 `LocalTrackQueryRulesTest`、`MultiSourceLyricClientTest`、`TrackIdentityRulesTest`、`MediaDiagnosisRulesTest` 全部保持通过。

服务端测试（`update_server/test/`，node:test）：

- catalog 200/ETag/304、sha256 正确性、超限拒绝、disabled 列表、admin 鉴权、分享码 TTL/大小/限流。

CI：

- 新 workflow `validate-rules.yml`：PR 改动 `rules/**` 时跑 schema + 安全正则 + fixtures；主 workflow 增加一步 `node --check`（已有）。

真机验收（单元测试不能替代）：

- 回声/比亚迪/LX 对应真实播放器；API 19 车机/设备；浮层与副屏不回归；规则更新后匹配缓存正确失效；诊断页在 API 19 上正确标注不可用字段；手动修正记忆在切歌/轮询后仍生效。

## 11. 与原始设想的逐条取舍

| 你的设想 | 建议 |
| --- | --- |
| 新增「音频匹配规则」 | ✅ 做，但叫「元数据匹配规则」；不做音频指纹 |
| 服务器下发 | ✅ 复用 `LyricSourceRules` + 静态 catalog，补 ETag/sha256/审核 |
| 用户可编辑、可分享 | ✅ 个人图形化规则 + JSON/文件/分享码；分享包标「未审核」 |
| 主体使用正则 | ✅ 正则用于条件与抽取，但动作用结构化 type；**不用命名组**，避免 API 19 问题 |
| 使用 JSON 包装 | ✅ schemaVersion + 严格上限 + 前向兼容 |
| 最终返回歌曲名/歌手 | ⚠️ 返回「清洗后的 title/artist + 查询候选列表」；单个返回值覆盖不了拆分、去序号、别名三种情况 |
| 应用暴露全部 MediaSession 信息 | ✅ 诊断页展示 + 复制 JSON；API 19 缺失字段标注；extras 白名单 + 谨慎展开 |
| 暴露默认规则尝试匹配的歌曲信息 | ✅ 诊断页逐步展示规则、候选、分数拆解；需要新增 `MatchTrace` 采集 |
| 开发者编辑器（纯代码、GitHub 拉取、审核后上架） | ✅ 应用内 JSON 代码模式 + GitHub PR + CI + 审核；拉取 URL 限 GitHub 域 |
| 个人编辑器（图形化、可分享） | ✅ 图形化条件/动作卡 + 实时预览 + 从修正生成规则 |
| 应用内「规则市场」 | ⚠️ 建议 P3；先「内置规则 + 分享码」，市场需要审核运营，否则会变成垃圾规则仓库 |
| 修正记忆 | ✅ 独立 `MatchCorrectionStore`，按包名+专辑/目录或原始 title+artist 记忆；可升级为个人规则 |

## 12. 需要拍板的问题

1. 规则包源目录放主仓库 `rules/`，还是独立仓库？（影响 CI 与审核流程）
2. 是否接受边界：用户/远程规则只做清洗与候选查询，评分策略/别名表由内置代码与审核数据控制？
3. 市场是否首发？我建议 P0 先内置规则修 #174，P2 下发，P3 市场。
4. 个人规则默认作用域：仅当前播放器包名（建议）还是全局？
5. 是否允许规则条件使用 MediaSession extras？建议 v1 只展示、不参与匹配。
6. 手动修正记忆是否跨播放器共享同一首歌？建议按包名隔离，另给「全局修正」开关。

## 附录 A：最小个人规则（分享用）

```json
{
  "schemaVersion": 1,
  "id": "user-echo-cleanup",
  "name": "回声：把“歌手 - 歌名”拆开",
  "version": 1,
  "channel": "personal",
  "author": "本机用户",
  "minVersionCode": 240,
  "scope": { "packages": ["com.echo.music"] },
  "rules": [
    {
      "id": "split",
      "when": [{ "field": "artist", "equalsAny": ["车载音乐"] }],
      "then": [{
        "type": "extract", "from": "title",
        "regex": "^(.{1,40}?)\\s+[-–—－]\\s+(.+)$",
        "targets": { "artist": 1, "title": 2 }
      }],
      "stop": true
    }
  ]
}
```

## 附录 B：与现有代码的对应关系

| 设计件 | 现有落点 |
| --- | --- |
| 清洗/查询 | `LocalTrackQueryRules.cleanFileTitle` :67、`fallbackQueries` :27、`MultiSourceLyricClient.catalogQueries` :239 |
| 本地候选 | `LocalLyricClient.candidateNames` :433 |
| 打分 | `NetEaseLyricClient.matchScore` :165、`plausibleMatch` :141；QQ/Kugou/Kuwo/Migu/Soda/CoverArt 全部复用 |
| 手动修正 | `MainActivity.showLyricRematchDialog` :3032、`MusicStateStore.reloadLyrics` :689 |
| 远程配置范式 | `LyricSourceRules` :21/:23/:128-130/:137-189 |
| 分享短码 | `ConfigurationShareClient` :16-39、`server.js` :360/:371、`config-share.js` |
| 设置页 | `SettingsNavigator` :55/:150、`SettingsCatalog` :8-13、`LayoutEditorActivity` :31-52 |
| 诊断 | `DiagnosticLog` :26/:54、`MediaDiagnosisRules` :12-77、`MainActivity` :1096-1107/:2907 |
