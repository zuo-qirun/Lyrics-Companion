# 2026-10-21 闪退与内存排查

本轮基于 `beta` 的 `57565514bc7bcfe23922bb05a23bb57a7bbc2ff4`。读取了 GitHub 当前 issue 正文、评论及反馈后台崩溃堆栈；管理员凭据不写入源码、文档或诊断附件。本报告记录发布前的本地验证快照；2026-10-22 按维护者要求进入 main 正式版发布流程，实际版本与构建状态以对应 GitHub Actions / Release 为准。推送与发布不会自动视为车机验收或关闭 issue。

## 崩溃证据与修复

| 现象 | 证据 | 处理 |
| --- | --- | --- |
| 凝彩反色 `setPixels()` 使用已回收位图 | #144；后台 `ea72444f-6670-4938-b794-c198349310a1`，Android 11 / 5756551；同类记录也见 Android 9 | 旧 4 MiB LRU 会在插入过大的输出时立即淘汰并 recycle，随后同一调用继续 setPixels。改为有界引用缓存，最多保留一张超预算输出；淘汰不销毁仍由帧持有的位图 |
| 柔化遮罩 `drawBitmap()` 使用已回收位图 | 后台 `b42f4522-46db-4233-a85c-d32c9fd0b96a`，Android 9；`FoliaOriginalCanvasRenderer.drawMask()` | 遮罩严格遵守缓存预算，超预算图可由当前绘制局部变量使用；淘汰和 clear 仅释放缓存引用 |
| 蓝牙广播解析失败导致进程退出 | 后台 `9269034f-e103-4802-be7c-bf456bc4d3d5`，`Bundle.keySet()` 解析不存在的 `PlaybackStateCompat` | 在广播边界处理 BadParcelableException / 类型错误；先验证整个惰性 Bundle，再修改记住的曲目信息。未知厂商对象不会被当作已适配接口 |
| 解锁前 Application 创建失败 | Android 9 模拟器复现 `SharedPreferences in credential encrypted storage are not available until after user is unlocked`；异常早于旧版 CrashReporter 安装 | 取消无实际用途的 direct-boot 接收声明与 LOCKED_BOOT_COMPLETED 订阅，正常 BOOT_COMPLETED / USER_UNLOCKED 恢复保留；提前安装 CrashReporter |
| 旧版凝彩 `fromIndex > toIndex` | 后台旧 `99c1a0f` 堆栈 | 当前基线已修正 slice 的空区间与负下标，并有既有回归测试；没有重复修改 |

位图所有权原则与 Android 官方说明一致：仍被 Canvas / View 系统使用的 Bitmap 不能提前 recycle。参见 [Bitmap API](https://developer.android.com/reference/android/graphics/Bitmap#recycle())。

## GitHub Bug 处理范围

| Issue | 本地实现 | 验收边界 |
| --- | --- | --- |
| #144 | 凝彩大位图缓存与绘制所有权修复 | 模拟器实际执行超预算 CPU 反色；待车机验证 |
| #150 | 网络词库质量判定，重新校验旧匹配缓存与网易云原始缓存 | 单元测试覆盖稀疏、占位与正常逐行结果；没有声称已在报告者网易云车机版验收 |
| #145 | 失效 Display ID 回退，并保留原偏好供重连恢复；页面提示实际回退目标 | 单元测试与模拟器虚拟屏恢复；物理仪表需验收 |
| #142 | 服务内按 Display ID 去重；选择页排除副屏占用项并补校验；失败拆窗保留所有权 | 不按名称强行合并不同 ID 的真屏；外部镜像重影不属于已修复范围 |
| #146 | 字号、容器、padding、margin 同步缩放；按钮换行与成组纵排；缩放标记保证重复应用不翻倍 | 模拟器 200% 设置页检查；仍需用户车机尺寸 / 字体复核 |
| #139 | 名单方向改变前明确说明，提供保留 / 清空 / 取消 | 名单仍为共享集合，界面已说明清空影响另一屏 |
| #151 | 背景强调色与歌词当前色解耦 | 背景跟随封面 / 原主题强调色；不会跟随当前歌词槽位 |
| #137 | 对比度基准加入封面原始平均色、背景类型、亮度、遮罩与不透明度 | 属于可读性修正和描边兜底；无法采样任意下层地图 / 动态壁纸，不保证每个动态像素满足统一对比度 |
| #141 | 顶部条独立选择固定深 / 浅色文字，默认沿用应用 / 系统昼夜 | 公开 Android 接口无法读取其他窗口实际状态栏图标颜色，未实现伪造的自动识别 |
| #128 | 通用行数控件按能力隐藏 | 紧凑的下一句 / 翻译设置继续生效 |
| #135 | 修复已复现的解锁前启动崩溃；增加生命周期及正常图标分流退出日志 | 原车机“多点几次才打开”的所有路径尚未复现，不据此关闭 issue |

## 内存检查

- 修复凝彩 clear 后残留的 alpha / premultiplied 行环形数组引用。
- 清理还原版文章布局、背景、表情引用和 Shader 缓存；提交给硬件帧的图片交给最后持有者释放。
- 隐现背景的六层渐变改为缓存，动画只更新首层的矩阵；抖动纹理与暗角复用 Shader。
- Application 的低内存 / trim 回调记录 Java / 原生分配量，并通过弱引用集合通知已附着面板释放缓存。
- MainActivity 销毁时取消 Handler 待执行消息，避免延时任务继续持有页面。
- 原生堆 Alloc、原生堆保留容量和进程 PSS 分别观察；单个快照或 allocator 容量上升不能单独证明泄漏。

## 本地检查

命令：`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`。

- 测试总数 602：595 通过，7 跳过，0 失败；没有启用实时词库网络测试。
- Android Lint：0 error，46 warnings；debug APK 构建成功。
- Android 9 / API 28 与 Android 12 / API 31：超预算 CPU 反色、遮罩所有权、无法解包的厂商 Parcelable、隐现 Shader 复用与 clear 检查通过。
- 两个平台的虚拟屏恢复断言通过；API 28 在虚拟屏清理后的 Activity 事务中出现系统 WMS 的 `DisplayContent.getOrientation()` 异常，API 31 未出现。该现象与业务断言分别记录，不能视为物理多屏验收。

## 商籁连续播放采样

Android 9 / API 28 模拟器使用 Demo Player 的真实 MediaPlayer 循环播放与 MediaSession，正式 `LyricsPanelView` 显示还原版 Folia / 商籁，并启用隐现背景。每 30 秒记录原生 Alloc、Java 堆、实际绘制帧数、播放位置、歌词行数与监听读取年龄；每约 60 秒记录 dumpsys meminfo 的 Alloc / Heap Size / PSS。

连续播放 20.43 分钟、21 个 meminfo 采样点，进程 PID 始终为 5609，未发生进程重启或 Java 闪退。以下单位为 MiB（KiB / 1024）：

| 指标 | 起点 | 采样峰值 | 终点 |
| --- | ---: | ---: | ---: |
| 原生 Heap Alloc | 44.14 | 44.81 | 43.96 |
| 原生 Heap Size | 52.75 | 63.25 | 61.75 |
| 原生 Heap PSS | 44.01 | 49.70 | 44.07 |

Heap Size 确实扩容，但途中从 63.25 MiB 回落到 61.25 MiB；Alloc 和 PSS 没有同幅度持续累积。以 Size - Alloc 估算的空闲容量从 8.61 MiB 增至 17.79 MiB，扩容部分主要落在空闲容量。现象更符合分配器保留空闲容量、场景切换分配与回收造成的波动；这属于依据曲线作出的推断，并非原生分配调用栈的完整归因，不能据此声称所有内存泄漏均已排除。

连续播放 APK SHA-256 为 `31D034687C1C8979A2E1139B95BC2961C9762AAE8365ABF01011E0E9F3F4FA4A`。测试进行中仅继续修正了设置页 Spinner 的固定高度裁字，未改变任何渲染或内存路径；该最终 UI 修补版已重新通过单元测试 / Lint / 构建，并在 API 31 模拟器确认 200% 选中文字完整显示，APK SHA-256 为 `0A1BF4C22E06E27362693FBE8834AE4225BA03B47047A5FF85E0D93ABCC09376`。

隐藏与恢复阶段单独采样，不混入上面的连续播放统计。首轮恢复命令错误地新建第二个测试页面，并使用默认样式，该轮恢复数据已判无效，留存为 `recovery-invalid-extra-activity.*` 供审计。修正后先清理测试页面栈，只保留一个商籁页面，再直接将原任务移到前台；这轮的起始条件与 20 分钟连续播放阶段不同，不能直接比较绝对基线。原始数据、曲线和截图位于未纳入 Git 的 `app/build/diagnostics-20261021/`。

修正后的同进程、同 Activity 对照（单位 MiB）：

| 阶段 | Alloc | Heap Size | Native PSS |
| --- | ---: | ---: | ---: |
| 单页面播放 | 39.56 | 63.25 | 45.23 |
| 隐藏 30 秒 | 38.02 | 62.25 | 40.24 |
| RUNNING_LOW 后 30 秒 | 38.20 | 62.25 | 40.24 |
| 原页面恢复播放 2 分钟 | 36.55 | 61.75 | 41.24 |
| 再次隐藏 30 秒 | 36.76 | 61.25 | 38.69 |
| 再次 RUNNING_LOW 后 30 秒 | 36.69 | 61.25 | 38.68 |
| 诊断性 GC 与 managed heap dump 后 | 37.46 | 61.75 | 38.49 |

恢复前后 `ActivityRecord{35f4e5e}` 一致，任务栈只含这个测试页面；隐藏时帧数保持 1445，恢复后继续增至 3336。堆转储本身也会分配内存，最后一行不是纯 GC 的无扰动观测。该轮没有复现隐现循环持续抬高 Alloc 的现象，但 allocator 仍保留约 24 MiB 空闲容量；GC 不保证把空闲容量归还系统。

### 已确认的 Android 9 页面残留

`am dumpheap -g` 后的 HPROF 中仍有一个 `mDestroyed=true` 的 MainActivity。排除弱引用 referent 后找到以下强引用链：

```text
GC Root: ViewGroup$ViewLocationHolder class
  -> static sPool -> SimplePool.mPool[9]
  -> ViewLocationHolder.mRoot
  -> Toolbar.mPopupContext
  -> destroyed MainActivity
```

这是本次快照中实际存在的页面保留，尚未消除，不能把本轮描述为“完全没有泄漏”。[Android 9 AOSP ViewGroup 源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r61/core/java/android/view/ViewGroup.java#L7959-L8102) 的该对象池最多 32 项，`clear()` 清了 mView 和坐标，却未清 mRoot，与这条引用链一致。它与每帧场景资源持续累积是不同的路径，目前没有证据证明此系统池会随音乐播放无限增长。应用公开 API 无法直接清理该私有池；本轮保留为已定位的系统层残留风险，没有加入侵入性的隐藏 API 反射补丁。HPROF、最短引用链与解析脚本保存在诊断目录，后续可进一步验证其他 ROM / API 版本。

连续播放日志在 1204 秒记录到 16751 次实际绘制，约 13.9 FPS；设置请求为 60 FPS，但 SwiftShader 和宿主负载下没有达到该帧率。该测试不能替代真实车机 GPU 驱动、硬件 60 FPS、长篇歌词、厂商播放器和多物理屏长时间运行验收。
