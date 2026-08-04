# Lyrics Companion（歌词伴侣）

一个采用 Material Design 3 的独立 Android 歌词显示应用。Android 5.0 以上读取系统标准 `MediaSession`，Android 4.4 使用系统 `RemoteController` 读取播放器发布的 `RemoteControlClient` 元数据；随后按歌名、歌手和时长跨乐库匹配歌词，并同时支持：

- 主屏可拖动悬浮窗；轻触可返回设置页。
- 非默认 `Display` 上的副屏歌词悬浮层；可在有触摸能力的副屏上直接拖动。
- 播放器封面 Bitmap、`content://`、本地文件和网络封面 URI 的异步加载与缓存。
- YRC 逐字时间轴按完整 Unicode 字符阶梯点亮；普通 LRC 按整句切换，避免伪造匀速逐字效果。
- 网易云、QQ 音乐、酷狗、酷我、汽水音乐歌词结果本地缓存 30 天。
- 可自动识别播放器词库，也可手动指定网易云、QQ、酷狗、酷我或汽水；手动词库无结果时可选择是否回退到播放器同源词库。
- 切换播放器或曲目时每 600ms 重新选择活跃会话，降低旧会话、旧歌词残留概率。
- 通知监听服务会记录系统媒体读取健康状态，并在主界面或悬浮窗服务启动时自动请求重连；短暂空会话保留当前歌词 5 秒，避免系统控制中心刷新时闪空。
- 歌词伴侣经典、Refined Now Playing、Apple Music-like Lyrics、紧凑单行、PiPWindow 和自定义布局六种悬浮窗风格。
- 可视化布局编辑器：在模拟渲染区拖动内容块，拖入备选区即可隐藏。
- 横屏宽度达到 600dp 时，主设置页与 Refined 设置自动切换为双列；布局编辑器同步切换为左右拖拽区域。
- AMap Companion 同款手感的副屏位置摇杆，以及窗口整体大小、字号、封面、背景和同步参数。
- 匿名实时在线人数与 App 内意见反馈；服务器未上线时自动降级，不影响歌词显示。

## 副屏原理

副屏路径与 `zuo-qirun/amap-companion` 的仪表屏投屏方式一致：

1. 用 `DisplayManager` 枚举系统中的非默认屏幕，并允许用户指定 Display ID。
2. 对目标屏调用 `createDisplayContext(display)`。
3. 从副屏上下文获取独立的 `WindowManager`。
4. 使用 `TYPE_APPLICATION_OVERLAY` 直接把歌词 View 添加到该屏幕，并用主界面摇杆持续微调坐标。

它不做截图、录屏或视频编码，同一份媒体/歌词状态由主屏和副屏各自绘制。副屏断开时窗口会立即释放；重新接入后会按已保存设置恢复。

## 歌词链路

歌词实现源自 `Amap-for-ESP32/android_forwarder` 的成熟逻辑，并扩展为多乐库回退：

```text
NotificationListenerService
  -> API 21+: MediaSessionManager.getActiveSessions()
  -> API 19-20: RemoteController / RemoteControlClient
  -> 选择正在播放且有元数据的会话
  -> 当前播放器同源乐库优先匹配
  -> 网易云 / QQ 音乐 / 酷狗 / 酷我 / 汽水按优先级依次兜底
  -> 优先解析 YRC，失败时回退 LRC
  -> 按 PlaybackState 的位置和速度实时插值
  -> 主屏悬浮窗 + 副屏 WindowManager 同步绘制
```

支持网易云音乐、QQ 音乐、酷狗、酷我、Spotify、汽水音乐、咪咕音乐、小米音乐、华为音乐、Apple Music、YouTube Music、Amazon Music，以及其他正确发布系统媒体元数据的播放器。网易云、QQ 音乐、酷狗、酷我和汽水会优先使用各自乐库；汽水可直接使用 MediaSession 曲目 ID 获取原生逐字歌词与中文翻译，ID 不可用时按歌名、歌手和时长搜索，随后再依次查询其余乐库。Android 4.4 上能否识别取决于播放器是否兼容旧式 `RemoteControlClient`。

## 构建

环境要求：JDK 17+、Android SDK 36、Android Build Tools，以及 Gradle 8.7+。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

也可以用 Android Studio 直接打开项目根目录。

## 自动构建与更新

仓库包含 GitHub Actions 自动测试、Lint、签名 Release 构建，以及与 AMap Companion 同协议的更新服务器。App 会从 `https://lyrics-companion.zuoqirun.top/update.json` 检查更新，校验 APK 包名、大小与 SHA-256 后选择单一安装器：检测到 Shizuku 与 InstallerX 时优先交给 InstallerX，否则使用 `PackageInstaller`，仅在明确失败后回退到 InstallerX 或系统默认安装器，避免车机同时打开多个安装流程。服务端部署、签名 Secrets 和历史版本配置见 [update_server/README.md](update_server/README.md)。

同一服务还提供 `/api/online`、`/api/online/heartbeat` 和 `/api/feedback`。
客户端只使用随机安装 ID 做短时在线去重，不读取硬件标识；服务器未部署时相关入口会显示不可用，
不会影响 MediaSession、悬浮窗或副屏歌词链路。

## 首次使用

1. 打开“歌词伴侣”。
2. 授予通知权限（Android 13+，用于前台服务通知）。
3. 点击“音乐读取权限”，在系统“通知使用权”中允许“歌词伴侣 · 音乐读取”。
4. 点击“悬浮窗权限”，允许显示在其他应用上层。
5. 打开“主屏悬浮窗”和/或“副屏歌词”。
6. 如有多块副屏，在“投屏屏幕”中选择目标 Display。
7. 打开音乐播放器并开始播放。

“悬浮窗风格与同步”中可切换四种风格；进入“可视化布局编辑器”后，将内容块拖到上方启用并定位，拖回下方备选区即可隐藏。主界面处于前台时会临时收起主屏悬浮窗，避免遮挡设置与拖拽操作。

“歌词匹配”默认为自动识别播放器。手动选择词库后，应用会优先等待该词库的匹配结果；开启“回退到播放器同源词库”时，所选词库无结果后会优先尝试从应用名称识别出的播放器词库，再使用其余词库兜底。修改设置会立即为当前歌曲重新加载歌词。

正数歌词偏移会让歌词提前，负数会让歌词延后。推荐每次调整 100–200ms。

## 平台边界

- Android 5.0 以上的播放器需发布标准 `MediaSession`；Android 4.4 的播放器需发布旧式 `RemoteControlClient`，否则普通应用无法可靠获得曲目和进度。
- 网易云、QQ 音乐、酷狗、酷我和汽水的搜索/歌词接口是在线服务且并非本项目控制；单一接口失败时会自动尝试其他乐库，全部失败时歌曲信息仍可显示。汽水接口无需读取用户账号 Cookie，但其可用性仍可能随服务端调整而变化。
- 少数深度定制车机 ROM 会限制普通应用在外接 Display 上创建 overlay；此时请先确认系统已授予悬浮窗权限，再结合 `adb logcat -s LyricsDisplay LyricsMediaSession LyricsMusicState` 排查。
- 本应用不会录制屏幕、读取通知正文或上传用户通知内容；通知监听仅用于取得系统允许访问的活跃媒体会话。

## 许可证与来源

本项目以 [GNU General Public License v3.0](LICENSE) 开源发布。歌词解析和媒体状态代码基于同一作者的 `Amap-for-ESP32` 项目衍生，并由版权所有者授权在本项目中以 GPL-3.0 发布。副屏与摇杆来自同一作者的 [zuo-qirun/amap-companion](https://github.com/zuo-qirun/amap-companion) 设计；新增视觉风格参考 [Refined Now Playing](https://github.com/solstice23/refined-now-playing-netease)、[PiPWindow](https://github.com/Lukoning/PiPWindow) 与 [Apple Music-like Lyrics](https://github.com/amll-dev/applemusic-like-lyrics)，具体边界见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
