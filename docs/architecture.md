# 架构说明

> 与 `LivingWallpaper_Codex_Plan.md` 第 3 节对应，记录实际落地后的结构与技术决策。
> 与计划书有偏差的地方都在文中显式标注原因。

## 1. 模块划分（实际落地）

```
app/            Compose UI：预览 / 场景库 / 参数 / AI 创造 / 性能
core/model/     SceneConfig、SceneType、WeatherType、PerformanceLevel、SkyColor
core/data/      SceneRepository 接口 + DataStore 实现 + 进程内单例
renderer/       gl / scene / particle / animation / performance / geometry
wallpaper/      LivingWallpaperService、ParallaxSensor
ai/             api / parser / provider / prompt
```

`include` 只在模块真正落地时添加，仓库中没有占位空模块。

## 2. 渲染管线

```
WallpaperService.Engine / App 预览（AndroidExternalSurface）
        │  主线程
        ▼
GLRenderer.start(surface, w, h) ── 创建 GL 线程
        │  GL 线程
        ├─ EGLManager.initialize(surface)   EGLDisplay → Config → ES3 Context → WindowSurface → makeCurrent
        ├─ SceneFactory.create(sceneType)   读取 assets 中的 GLSL（主线程已完成）
        ├─ Scene.initialize()               编译着色器、创建 VBO/VAO
        └─ 循环：消费配置队列 → update(dt) → render() → eglSwapBuffers() → 按帧预算 sleep
```

- **线程边界**：所有 GL/EGL 调用只在 GL 线程内发生；
  主线程通过 volatile 字段（尺寸、可见性）与 `ConcurrentLinkedQueue<SceneConfig>`
  （配置）与渲染线程通信，不共享任何 GL 对象。
- **场景热切换**：配置到达时若 `sceneType` 变化，GL 线程内释放旧场景并重建新场景，
  避免跨线程操作 GL 资源。
- **不可见暂停**：`setVisible(false)` 后 GL 线程在监视器上 `wait()`，不空转；
  恢复时重置时间基准，避免产生巨大的 deltaTime。

## 3. 绘制顺序

| 场景 | 顺序 |
|---|---|
| Forest Night | 天空渐变 → 远山 → 远树 → 中树 → 雨滴 → 萤火虫（加色混合）→ 近树 |
| Aurora Lake | 极光夜空（星星+两道帘幕）→ 远山 → 中山 → 近山（剪影）→ 湖面（不透明，含天空/雪山倒影）→ 飘雪（加色混合） |
| Star Valley | 星空+星星+云层 → 远山 → 近山 → 流星（加色混合）→ 雨滴（雨天） |
| City Dusk | 单次全屏片元着色器完成：照片底图采样 → 玻璃层（下爬的大水滴 + 静态细水珠 + 雨丝），折射体现为对采样点的偏移 |

混合模式：不透明背景关闭 BLEND；剪影与雨滴用 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA`；
萤火虫与流星用 `SRC_ALPHA / ONE`（加色）。

## 4. 时间步进与动画

- `FrameTiming.deltaSeconds()` 使用 `System.nanoTime()`（单调），
  deltaTime 上限 100ms；负数（时钟异常）返回 0。
- `Wind` 提供低频阵风与摆动幅度；摆动在顶点着色器里按 `aSway`（0=根部，1=树冠）计算。
- `ParallaxController` 对传感器倾角做一阶低通，输出按 `parallaxStrength` 缩放；
  关闭视差时输出恒为 0 并注销传感器。
- 所有动画速度统一乘 `animationSpeed`，不受帧率影响。

## 5. 粒子系统

| 系统 | 容量 | 生命周期 |
|---|---|---|
| RainSystem | 1200 | 固定对象池，落到画面外即回到顶部重生 |
| FireflySystem | 160 | 独立 `life`，耗尽后在随机位置重生 |
| MeteorSystem | 12 | 按“每秒生成数”积分触发，飞出画面或生命结束回收 |
| SnowSystem | 700 | 缓慢下落 + 正弦横摆，短线段形式，落到画面外回顶部 |

- 运行期不分配对象，只写 `FloatArray`；渲染层每帧一次 `glBufferSubData`。
- City Dusk 不用这里的任何粒子系统：它的雨丝、水珠、下爬的大水滴全部在
  `meadow_glass.frag` 里按像素生成，CPU 每帧只更新 `uTime` 等 uniform。
- 流星的生成按时间积分而非“每帧按槽位掷骰子”，保证与帧率无关（有对应单元测试）。
- 密度/强度同时受 `PerformanceLevel.particleScale` 与 `reduceMotion` 影响。

## 6. 配置与存储

```
UI (Compose) ──► SceneViewModel ──► SceneRepository (DataStore)
                                          │
                                          ├── Flow ──► UI 实时刷新
                                          └── Flow ──► 壁纸 Engine ──► GLRenderer.applyConfig
```

- `SceneConfig` 是不可变数据类；任何入口（滑块、AI、旧数据）都必须先 `clamped()`。
- `clamped()` 先判断有限性再夹紧——`NaN` 用 `coerceIn` 是抓不住的
  （`NaN` 与任何值比较均为 false），这一点有专门的回归测试。
- 壁纸 Service 没有依赖注入框架，用 `SceneRepositoryProvider` 进程内单例保证两端一致；
  `snapshot()` 提供同步读取，避免在 `onSurfaceCreated` 里做阻塞式 IO。

## 7. AI 抽象与规则模式

- `AIProvider` 是唯一的自然语言入口，接口稳定；
  规则模式是 `isModel = false` 的默认实现，UI 明确标注“规则模式（非模型推理）”。
- 解析分两层：**指令 → `InstructionPatch`（结构化改动）→ `SceneConfig`**。
  中间层带来三个好处：可单元测试、可在 UI 解释命中了哪些规则、
  应用层统一走 `clamped()`。
- 无法理解的指令抛 `InstructionNotUnderstoodException`，调用方保留原配置。
- `SceneConfigJsonParser` 为未来的模型输出准备：允许部分字段与未知字段，
  非法值强制回退默认，解析失败只尝试**一次**修复（裁掉多余文本 + 去尾逗号）。

> **与计划书的偏差**：`AIProvider.generateSceneConfig` 的返回值由 `SceneConfig`
> 改为 `GenerationResult`（含命中规则说明、是否变化、真实耗时）。
> 原因：UI 需要证明“指令真的被理解了”，只返回配置无法向用户解释发生了什么。

## 8. 性能

- `RenderStatsHolder` 采集真实帧间隔（包含限帧 sleep，因此反映用户实际看到的刷新率）、
  EMA 平均帧间隔、丢帧（间隔 > 目标预算 1.5 倍）与累计帧数。
- `PerformanceLevel` 真实改变粒子数量与动态幅度，不是只改一个名字。
- 内存数据取自 `Runtime` 与 `ActivityManager`，每秒刷新，无估算。

## 9. 已知限制

- 已在 Xiaomi 23116PN5BC / Android 16 上完成安装、设置壁纸、场景切换与参数联动的真机验证；
  旋转、锁屏、Surface 重建与低端机降级仍未系统测试。
- 渲染统计目前是进程内单例，若同时运行预览与壁纸，两者都会写入同一份统计。
- 湖面倒影是"逐片元重算几何"，不是真的二次渲染：山脊线由 CPU 生成网格时用的
  同一组参数（振幅+相位）作为 uniform 传入，湖面着色器复现同一条曲线后以湖平线为轴翻转。
  优点是不需要 RenderTarget、开销恒定；代价是倒影无法包含体积感与遮挡关系。
