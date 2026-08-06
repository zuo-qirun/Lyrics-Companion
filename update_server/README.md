# Lyrics Companion 自动构建与更新服务器

整体链路与 AMap Companion 一致：GitHub Actions 在 `main` 更新后运行 Android 测试和 Lint；配置签名密钥后生成签名 APK、`release-update.json` 与更新日志并发布 GitHub Release。更新服务器只同步 Release，不安装 Android SDK，也不在服务器本机构建 APK。

## GitHub Actions 签名配置

在仓库 Settings → Secrets and variables → Actions 中添加：

- `ANDROID_SIGNING_KEY_BASE64`：JKS/PKCS12 文件的 Base64 内容。
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

没有上述密钥时，工作流仍会测试、Lint 并上传 Debug 构建产物，但不会创建可更新的 GitHub Release。签名密钥必须长期备份；更换密钥后 Android 无法覆盖安装旧版本。

## 服务端部署

要求 Node.js 18+。以下示例使用独立目录、8790 端口和 `lyrics-companion.zuoqirun.top`：

```bash
sudo mkdir -p /opt/lyrics-companion
sudo chown -R "$USER":"$USER" /opt/lyrics-companion
git clone https://github.com/zuo-qirun/Lyrics-Companion.git /opt/lyrics-companion
cd /opt/lyrics-companion/update_server
cp deploy/env.example .env
npm test
npm run sync:force
npm start
```

检查端点：

```bash
curl http://127.0.0.1:8790/health
curl http://127.0.0.1:8790/update.json
curl http://127.0.0.1:8790/versions.json
curl http://127.0.0.1:8790/faq.json
```

网页入口：`/` 为最新版，`/versions` 为历史版本。`/faq.json` 提供 App 内 FAQ，客户端会在成功刷新后缓存最近一次有效内容。`/update-github.json` 和 `/versions-github.json` 会让客户端下载 GitHub 资源；默认端点优先使用服务器本地镜像。

## systemd

先根据服务器用户与目录调整 `deploy/*.service`，然后：

```bash
sudo cp deploy/lyrics-companion-*.service /etc/systemd/system/
sudo cp deploy/lyrics-companion-sync.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lyrics-companion-update.service
sudo systemctl enable --now lyrics-companion-sync.timer
```

反向代理需把 `https://lyrics-companion.zuoqirun.top` 转发到 `127.0.0.1:8790`，并保留 `Host`、`X-Forwarded-Host` 与 `X-Forwarded-Proto`。`PUBLIC_BASE_URL` 应直接填写已配置 HTTPS 的域名，不要附加 `:80`；服务端也会在 HTTPS 代理头中自动移除错误的 `:80` 和默认 `:443`。若使用其他域名，同时更新 App 中的默认更新地址。

## 在线人数与反馈

App 使用随机生成并保存在本机的安装 ID，每 60 秒向
`POST /api/online/heartbeat` 发送一次匿名心跳。服务端只在内存中保留最近两分钟的安装 ID，
不读取或保存 Android 硬件标识；`GET /api/online` 返回当前去重后的在线数量。

意见反馈通过 `POST /api/feedback` 提交，正文最多 2000 字、可选联系方式最多 200 字，
同一客户端和来源默认每 60 秒只能提交一次。反馈按 JSON Lines 写入
`update_server/state/feedback.jsonl`，该目录已被 Git 忽略，不会随发布内容公开。

可在 `.env` 中调整：

```dotenv
ONLINE_TTL_MS=120000
FEEDBACK_INTERVAL_MS=60000
DIAGNOSTIC_INTERVAL_MS=60000
ADMIN_TOKEN=replace-with-a-long-random-secret
```

## 反馈收件箱、回信与诊断

访问 `https://你的域名/feedback` 可打开管理员收件箱。页面不会在未配置
`ADMIN_TOKEN` 时返回任何反馈或诊断内容；令牌只保存在浏览器当前会话中。管理员可在
收件箱中查看反馈、联系方式和版本号，并直接回信。App 只用每次反馈返回的随机回信凭据
读取本机提交过的反馈回复，服务端不会公开安装 ID。

App 会把本应用未捕获的 Java 异常堆栈先保存到私有目录，绝不会在崩溃处理器中联网。
用户在设置中明确打开“自动上传闪退诊断”后，下次启动才上传该记录；也可以手动上传
诊断快照。诊断包含应用与系统版本、设备型号、权限、内存与存储、屏幕和悬浮窗配置、
播放器及曲目元数据、歌词匹配状态和最近约 1 MB 的应用事件；不包含歌词正文、通知正文、
联系人、设备唯一标识、管理令牌或 Android 系统 logcat。单条诊断详情最多保留
1,200,000 字符，诊断接口请求体上限为 4 MB。

## 更新协议

客户端请求 `/update.json`，校验：

- `packageName` 必须为 `com.zuoqirun.lyricscompanion`；
- `versionCode` 必须大于本地版本；
- 下载大小与 `sha256` 必须匹配；
- APK 内部包名必须与当前应用一致。

校验通过后，App 只启动一个安装流程：检测到 Shizuku 与 InstallerX 时优先使用 InstallerX，否则使用 Android `PackageInstaller`；仅在安装会话明确失败后，才依次回退到 InstallerX 和系统默认 APK 安装器，避免车机安装器互相冲突。
