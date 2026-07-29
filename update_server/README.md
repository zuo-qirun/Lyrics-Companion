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

要求 Node.js 18+。以下示例使用独立目录、8788 端口和 `lyrics-companion.zuoqirun.top`：

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
curl http://127.0.0.1:8788/health
curl http://127.0.0.1:8788/update.json
curl http://127.0.0.1:8788/versions.json
```

网页入口：`/` 为最新版，`/versions` 为历史版本。`/update-github.json` 和 `/versions-github.json` 会让客户端下载 GitHub 资源；默认端点优先使用服务器本地镜像。

## systemd

先根据服务器用户与目录调整 `deploy/*.service`，然后：

```bash
sudo cp deploy/lyrics-companion-*.service /etc/systemd/system/
sudo cp deploy/lyrics-companion-sync.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lyrics-companion-update.service
sudo systemctl enable --now lyrics-companion-sync.timer
```

反向代理需把 `https://lyrics-companion.zuoqirun.top` 转发到 `127.0.0.1:8788`，并保留 `Host` 与 `X-Forwarded-Proto`。若使用其他域名，修改 `.env` 的 `PUBLIC_BASE_URL`，同时更新 App 中的默认更新地址。

## 更新协议

客户端请求 `/update.json`，校验：

- `packageName` 必须为 `com.zuoqirun.lyricscompanion`；
- `versionCode` 必须大于本地版本；
- 下载大小与 `sha256` 必须匹配；
- APK 内部包名必须与当前应用一致。

校验通过后，App 使用 Android `PackageInstaller` 提交安装会话，车机拦截时回退到系统 APK 安装器。
