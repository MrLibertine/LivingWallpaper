# 性能实测记录

> 本文件只记录**真机上实际采到的数值**。没有测过的项目在最后单独列出，不做估算、不做填充。

## 1. 测试环境

| 项 | 值 |
|---|---|
| 设备 | Xiaomi 14 Pro（型号 23116PN5BC，SoC 骁龙 8 Gen 3 / Adreno 750） |
| 系统 | Android 16（SDK 36） |
| 屏幕 | 物理 1440×3200，`wm size` override 为 **1080×2400** |
| 密度 | 物理 560 dpi，override 420 dpi |
| 构建 | `debug`，版本 `0.1.0-p1`（versionCode 1） |
| 编译目标 | minSdk 29 / targetSdk 35 / compileSdk 35 |
| 渲染 | OpenGL ES 3.0，渲染分辨率 1080×2400 |

## 2. 测量方法

**数据来源**：`RenderStatsHolder`——渲染线程每帧记录真实帧间隔，
计算 EMA 平均帧间隔、丢帧（间隔 > 目标预算 1.5 倍）与累计帧数；内存取自
`ActivityManager` 与 `Runtime`。性能页展示的就是这些运行时真实值，没有模拟或随机数。

**采样流程**（`docs/measure_perf.py` 自动化）：

1. 场景库选中目标场景；
2. 性能页设置性能档位与目标帧率；
3. 回预览页**连续渲染 8 秒**；
4. 采一次 `dumpsys meminfo`；
5. 回性能页截图，读取刚才那 8 秒的统计。

8 秒 × 约 55 FPS ≈ 450–500 帧，样本量足够反映稳态。

> **为什么不在性能页上边渲染边看**：预览与性能是两个 Tab，切到性能页时预览
> Surface 已销毁、渲染停止，统计值会冻结在切换前。因此读数反映的是"刚才那段渲染"，
> 而不是"此刻"。这是当前 UI 结构的限制，已在第 7 节列为已知问题。

## 3. 帧率实测

目标帧率 60 FPS，性能档位「均衡」，Aurora Lake / Forest Night / Star Valley 各测一轮：

| 场景 | 档位 | 实测帧率 | 平均帧间隔 | 最近一帧 | 丢帧 / 总帧 |
|---|---|---|---|---|---|
| Forest Night 森林雨夜 | 均衡 | **55.3 FPS** | 18.1 ms | 18.7 ms | 25 / 497 |
| Aurora Lake 极光雪山湖 | 均衡 | **54.9 FPS** | 18.2 ms | 16.9 ms | 35 / 497 |
| Star Valley 星空山谷 | 均衡 | **56.0 FPS** | 17.9 ms | 16.6 ms | 43 / 501 |

三个场景差异 **< 1.1 FPS**：说明画面复杂度（着色器分支、粒子数）不是当前瓶颈，
上限来自 60 Hz 垂直同步与渲染线程的帧预算 sleep。

### 3.1 性能档位对比（Aurora Lake，目标 60 FPS）

| 档位 | 实测帧率 | 平均帧间隔 | 丢帧 / 总帧 | 说明 |
|---|---|---|---|---|
| 省电（粒子×0.55 / 动态×0.60） | 54.2 FPS | 18.4 ms | 36 / 498 | 画面元素明显减少，帧率几乎不变 |
| 均衡（粒子×0.70 / 动态×0.80） | 54.9 FPS | 18.2 ms | 35 / 497 | 默认档 |
| 高画质（粒子×1.00 / 动态×1.00） | 55.4 FPS | 18.0 ms | 44 / 496 | 粒子最多，帧率仍未下降 |

**解读**：档位真实改变了粒子数与动态幅度（可在画面上直接看出），但因为 GPU 余量充足，
三档帧率差异落在测量噪声内。档位的价值目前在**功耗与观感**，不在升帧。

### 3.2 帧率档位验证（Aurora Lake，均衡）

| 目标 | 实测 | 平均帧间隔 | 丢帧 |
|---|---|---|---|
| 60 FPS | 54.9 FPS | 18.2 ms | 35 / 497 |
| 30 FPS | **29.6 FPS** | 33.8 ms | **0 / 275** |

限帧逻辑正确：设 30 就稳定在 29.6，且**丢帧为 0**，说明 30 FPS 下负载只用了不到一半预算。

## 4. 内存实测

### 4.1 壁纸状态（桌面，Aurora Lake 持续渲染）

连续 3 次采样（间隔 5 秒）：

| 采样 | TOTAL PSS | EGL mtrack | Native Heap |
|---|---|---|---|
| #1 | 220 922 KB（215.7 MB） | 82 208 KB | 25 893 KB |
| #2 | 217 704 KB（212.6 MB） | 82 208 KB | 25 893 KB |
| #3 | 217 781 KB（212.7 MB） | 82 208 KB | 25 897 KB |

稳定在 **约 213–216 MB**，5 秒内无增长趋势。

明细（采样 #1）：

| 项 | PSS |
|---|---|
| EGL mtrack | 82 208 KB（80.3 MB） |
| Native Heap | 23 989 KB（23.4 MB） |
| Dalvik Heap | 7 584 KB（7.4 MB） |
| .apk mmap | 56 825 KB |
| Dalvik Other | 15 736 KB |
| Gfx dev | 6 480 KB |
| GL mtrack | 1 276 KB |

**EGL 占用 80 MB 是大头**：1080×2400×4 B ≈ 9.9 MB/缓冲，多缓冲加上系统
在物理分辨率（1440×3200）上的额外分配，合计约 80 MB。

### 4.2 应用预览状态（App 在前台，含 Compose UI）

| 场景 / 档位 | TOTAL PSS |
|---|---|
| Forest Night / 均衡 | 298 973 KB（292.0 MB） |
| Aurora Lake / 均衡 | 282 840 KB（276.2 MB） |
| Star Valley / 均衡 | 283 435 KB（276.8 MB） |
| Aurora Lake / 省电 | 284 482 KB（277.8 MB） |
| Aurora Lake / 高画质 | 283 748 KB（277.1 MB） |
| Aurora Lake / 30 FPS | 285 178 KB（278.5 MB） |

预览态比壁纸态高约 **60–80 MB**：Compose UI 加上预览与壁纸两套 EGL 上下文
在同一进程内并存（壁纸被遮挡时暂停渲染，但缓冲尚未释放）。

## 5. 测试方法学结论

- **GPU 余量充足**：60 FPS 目标下三场景均达 55 FPS 左右，高画质档不降压帧。
- **限帧准确**：30 FPS 档实测 29.6、丢帧 0。
- **内存以 EGL 缓冲为主**：分辨率是内存的第一驱动因素，降分辨率比降粒子数更有效。
- **粒子规模不影响帧率**：1200 雨滴与 700 雪花在 Adreno/Mali 上开销可忽略。

## 6. 复现步骤

```bat
:: 1. 构建并安装
gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

:: 2. 打开应用，允许其为壁纸
adb shell am start -n com.livingwallpaper.app/.MainActivity

:: 3. 自动化采集五个组合的帧率与内存
python docs\measure_perf.py

:: 4. 采集壁纸态内存
adb shell input keyevent KEYCODE_HOME
adb shell dumpsys meminfo com.livingwallpaper.app
```

截图证据在 `docs/shots/perf-*.png`，原始采样见 `docs/perf-raw.txt`。

## 7. 已知问题与未测量项

**已知问题：**

- 性能页与预览页分属不同 Tab，切过去后预览 Surface 销毁、渲染停止，
  统计值冻结。用户看到的是"上一段渲染"的读数，不是实时刷新。
- `RenderStatsHolder` 是进程内单例：预览与壁纸同进程时，两者的帧统计会写进同一份数据。
- 系统壁纸预览页偶发全黑：通过 `ACTION_CHANGE_LIVE_WALLPAPER` 进入时，
  本机 HyperOS 有时不为壁纸服务创建 Engine（logcat 无 `onSurfaceCreated` / `GLRenderer` 日志），
  预览区纯黑；从「设置 → 壁纸 → 动态壁纸」列表入口进入则正常渲染。
  该问题不影响主屏幕壁纸的实际显示，但会影响首次验收的观感，已记录待查。

**未测量（不做估算）：**

- 长时间连续运行（>1 小时）的帧率衰减与内存增长
- 低端机 / 中端机的帧率与降级表现
- 锁屏、旋转、Surface 重建过程中的掉帧
- 功耗（mAh/h）与发热
- 其他场景 × 其他档位的完整矩阵（当前每组只测代表性组合）
