# LivingWallpaper（灵境 AI 动态壁纸）— Codex 实施计划书

> 目标：构建真正可安装、可设置为 Android 系统动态壁纸的 Kotlin 原生应用。使用 OpenGL ES 实时渲染，并通过自然语言修改场景参数。本文可直接交给 Codex 作为项目任务说明。

## 1. 项目概览

- **平台**：Android 10+（minSdk 29），优先完成 Android；iOS 仅作为后续独立场景预览与壁纸素材导出应用，不宣称可实现同等系统动态壁纸。
- **语言与 UI**：Kotlin、Jetpack Compose、Material 3。
- **核心引擎**：`WallpaperService`、OpenGL ES 3.0、EGL、GLSL ES、粒子系统。
- **架构**：MVVM、Repository、Kotlin Coroutines/Flow、DataStore、Gradle Kotlin DSL。
- **AI**：先做真实可用的规则解析器，后续可选接入经过兼容性验证的 MNN 本地模型；规则解析不能冒充大模型。
- **交付**：源代码、可构建 APK、3 个场景、设置系统壁纸、参数编辑、自然语言控制、测试、README、架构文档、录屏指引。
- **预计周期**：基础版 14–21 天，MNN 端侧模型额外预留 3–7 天；时间仅为估算。

### 1.1 产品价值

壁纸不是循环播放的视频，而是通过 GPU 实时渲染的可交互场景。用户可以输入“雨小一点、天空偏紫色、增加萤火虫”，应用把指令转换成受约束的 `SceneConfig`，校验后实时应用到渲染引擎。

### 1.2 最终演示

1. App 首页实时预览森林雨夜。
2. 切换极光雪山湖、星空山谷。
3. 调节雨量、风速、光照、视差、帧率。
4. 输入自然语言，画面确实发生变化。
5. 通过系统界面设置为动态壁纸，退出 App 后仍可运行。
6. 展示真实 FPS、帧耗时、粒子数量、内存指标。
7. **只有真实集成 MNN 后**，才演示离线本地 LLM 解析指令。

## 2. 产品需求（PRD）

### 2.1 页面

| 页面 | 需求 | 验收标准 |
|---|---|---|
| 首页 | 当前场景动态预览、设置壁纸、AI 创造、场景库、性能设置入口 | 预览持续渲染，按钮可用 |
| 场景库 | Forest Night、Aurora Lake、Star Valley 的封面/预览/应用 | 三场景均可切换，且不会黑屏 |
| AI 创造 | 文本输入、生成、加载、错误、预览、保存 | 输入“雨小一点”可使实际雨量减少 |
| 参数编辑 | 雨量、风速、动画速度、天空颜色、光照、视差、粒子密度 | 滑块变化实时影响渲染；按场景显示适用参数 |
| 性能中心 | 15/30/60 FPS 档位、帧耗时、粒子数、内存、质量模式 | 使用真实测量数据，不能随机模拟 |

### 2.2 内置场景

**Forest Night 森林雨夜**：夜空渐变、远山、森林多层景深、雨滴、萤火虫、风吹树木、视差。

**Aurora Lake 极光雪山湖**：极光帘幕与星空、三层雪山剪影、湖面倒影与波光、飘雪。

> **实施偏差**：原计划的 Sunset Ocean 日落海岸在 P4 阶段实现后，
> 因视觉效果未达预期由用户决策整体删除，替换为 Aurora Lake。
> 相关枚举、着色器、AI 规则与文案均已同步移除，下文涉及 Ocean 的历史描述保留原样。

**Star Valley 星空山谷**：夜空、山脉、星星闪烁、流星、缓慢云层。

先把森林雨夜做精致，再扩展其他场景；禁止用单张静态图或循环 MP4 假装实时渲染。

### 2.3 质量与安全约束

- 用户可手动选择场景；自然语言输出只控制已支持的参数，不执行任意代码或未经验证的 Shader。
- AI 返回非法 JSON 时，最多尝试一次修复；失败应提示并保留原配置。
- 不将私有云 API Key 硬编码进 APK。若使用云端 Provider，应经安全代理服务，或仅供用户自行配置测试密钥。
- 无网络时规则解析器、壁纸与手动参数编辑仍可工作。
- 壁纸不可见时暂停持续渲染和非必要传感器监听。
- 提供关闭视差/减少动态效果选项。

## 3. 技术方案

### 3.1 组件

| 层 | 组件 | 作用 |
|---|---|---|
| UI | Jetpack Compose | 首页、编辑器、场景库、性能面板 |
| 壁纸系统 | `WallpaperService.Engine` | 接收 Surface、可见性、尺寸、销毁等回调 |
| 渲染 | EGL + OpenGL ES 3.0 | GPU 绘制、Shader、纹理、批量粒子 |
| 状态 | `SceneConfig` + Repository | 唯一可信配置源；UI 与壁纸同步 |
| 持久化 | DataStore | 场景配置与用户偏好 |
| AI | `AIProvider` + Parser + Validator | 自然语言转结构化配置 |
| 本地 AI（扩展） | MNN Provider | 经过实测的兼容模型推理 |
| 性能 | Benchmark/Monitor | FPS、帧耗时、粒子数、内存 |

### 3.2 关键数据流

```text
用户文字/滑块
      │
      ▼
SceneConfig 生成/更新
      │
      ▼
Schema 验证 + 范围约束
      │
      ▼
SceneRepository / DataStore
      │
      ├──────────────► Compose 预览
      │
      └──────────────► Wallpaper Engine
                               │
                               ▼
                         GL 渲染线程
                               │
                               ▼
                         Android Surface
```

跨线程传递配置时使用不可变快照、线程安全队列或原子引用；OpenGL API 只在拥有上下文的渲染线程执行。

### 3.3 数据模型

```kotlin
@Serializable
data class SceneConfig(
    val version: Int = 1,
    val sceneId: String,
    val sceneType: SceneType,
    val skyColor: String,
    val weather: WeatherType,
    val rainIntensity: Float,
    val windStrength: Float,
    val lightIntensity: Float,
    val animationSpeed: Float,
    val parallaxStrength: Float,
    val particleDensity: Float,
    val targetFps: Int
)

enum class SceneType { FOREST, AURORA, STAR_VALLEY }
enum class WeatherType { CLEAR, RAIN, CLOUDY }
```

范围：雨量、风速、光照、视差、粒子密度均为 0.0–1.0；动画速度 0.1–2.0；帧率 15、30、60。颜色校验 `#RRGGBB`（可按需要扩展）。所有外部输入先校验再应用。

### 3.4 WallpaperService

- 在 `AndroidManifest.xml` 注册需要 `android.permission.BIND_WALLPAPER` 的 Service、`android.service.wallpaper.WallpaperService` Intent Filter 和 `res/xml/wallpaper.xml` 元数据。
- 实现 `onCreateEngine`、`onSurfaceCreated`、`onSurfaceChanged`、`onVisibilityChanged`、`onSurfaceDestroyed`、`onDestroy`。
- 管理 EGL 上下文、Surface、渲染线程、资源重建与释放；不能在主线程无限循环绘制。
- “设置为壁纸”使用 Android 系统壁纸预览/选择流程，由用户确认。
- App 内预览和系统壁纸共享渲染逻辑，但可以使用各自独立的 EGL Surface；不共享不安全的 GL 对象。

### 3.5 渲染系统

建议先搭建以下最小接口，后续按需细化：

```kotlin
interface Scene {
    fun initialize()
    fun update(deltaTimeSeconds: Float)
    fun render()
    fun applyConfig(config: SceneConfig)
    fun release()
}
```

推荐渲染顺序：天空背景 → 远景 → 中景 → 天气/粒子 → 前景。使用 VBO、批量绘制、粒子对象池，减少 Draw Call 和每帧对象分配。

- **雨滴**：位置、速度、长度、透明度；受风速和雨量控制。
- **萤火虫**：位置、漂移速度、闪烁相位、生命周期。
- **流星**：生成概率、方向、速度、拖尾、生命周期。
- **视差**：`SensorManager` 姿态数据 → 滤波 → 分层偏移；关闭时零偏移。
- **动画**：使用单调时钟计算 deltaTime，处理恢复时的超大时间差。

### 3.6 AI 模块

```kotlin
interface AIProvider {
    suspend fun generateSceneConfig(
        prompt: String,
        currentConfig: SceneConfig
    ): SceneConfig
}
```

**第一阶段：RuleBasedProvider**，支持“雨大一点”“天空暗一点”“萤火虫多一些”“切换到星空”“动画慢一点”，并有可测试的确定性映射。UI 应明确标注“规则模式”。

**可选第二阶段：MNNProvider**，在实际验证兼容的 MNN Android Runtime + 模型 + Tokenizer 组合后集成。要求异步加载、超时/取消、推理失败回退、JSON 校验、真实耗时记录。不要仅建空类就标记已完成。

**可选云端模式**：通过安全服务代理，禁止把私有密钥直接写入仓库或 APK。

### 3.7 性能目标（均需真机验证）

| 项目 | 开发目标 |
|---|---|
| 省电模式 | 15 FPS |
| 平衡模式 | 30 FPS |
| 高画质模式 | 60 FPS（设备允许时） |
| 内存 | 普通场景尽量低于 150 MB |
| 切换场景 | 尽量低于 500 ms |
| 不可见状态 | 停止持续渲染 |
| 帧率监控 | 真实采样，不伪造 |

掉帧持续一段时间后才降低粒子密度、特效或 FPS；采用滞回策略避免画质抖动。推理任务不能阻塞 GL 线程。

## 4. 建议仓库结构

```text
LivingWallpaper/
├── app/                       # Compose UI / MainActivity / navigation
├── core/
│   ├── model/                 # SceneConfig / enums
│   ├── data/                  # Repository / DataStore
│   └── common/
├── renderer/
│   ├── gl/                    # EGLManager / GLRenderer / ShaderProgram
│   ├── scene/                 # Scene / ForestScene / AuroraScene / StarScene
│   ├── particle/              # Rain / Firefly / Meteor
│   ├── animation/             # Clock / Wind / Parallax
│   └── src/main/assets/shaders/
├── wallpaper/                 # WallpaperService / Engine
├── ai/
│   ├── api/                   # AIProvider
│   ├── prompt/
│   ├── parser/                # Parser / Validator
│   └── provider/              # RuleBasedProvider / optional MNNProvider
├── benchmark/                # PerformanceMonitor
├── docs/
│   ├── architecture.md
│   ├── development-log.md
│   ├── ai-workflow.md
│   └── performance.md
├── prompts/
├── README.md
├── LICENSE
├── settings.gradle.kts
└── build.gradle.kts
```

这是目标结构，不要一次创建大量无用空模块。Gradle 模块间依赖方向必须清晰、不能循环。

## 5. 里程碑与验收

| 阶段 | 工作 | 预计 | 完成标准 |
|---|---|---|---|
| P0 | 检查 SDK、JDK、Gradle、设备/模拟器 | 0.5–1 天 | 明确环境和兼容版本 |
| P1 | 初始化 Kotlin/Compose 工程 | 1 天 | 可构建、可打开首页 |
| P2 | WallpaperService + EGL 最小版 | 2 天 | 能设置为系统壁纸，显示动态渐变 |
| P3 | Forest Night + 雨滴/萤火虫/视差 | 3–4 天 | 真机实时渲染、可暂停恢复 |
| P4 | Sunset Ocean + Star Valley | 3–4 天 | 三场景切换正常 |
| P5 | Compose 参数编辑器 + DataStore 同步 | 2 天 | 修改后系统壁纸也更新 |
| P6 | 规则自然语言控制 + AIProvider 抽象 | 2–3 天 | 指令真正改变画面 |
| P7 | 性能档位、监控、资源释放 | 2–3 天 | 有真实测量记录 |
| P8 | 测试、README、APK、录屏脚本 | 2 天 | 可复现构建与演示 |
| 扩展 | MNN 本地模型接入 | 额外 3–7 天 | 断网状态下真实模型推理成功 |

**每阶段必须**：列出改动文件、运行构建、运行适用测试、修复错误、更新 `docs/development-log.md`，说明已验证与未验证项；不要在环境缺失时谎称测试通过。

### 5.1 必测场景

- 安装、启动、系统壁纸预览与设置。
- 切换应用、锁屏、亮屏、旋转、Surface 重建。
- 三场景切换与参数实时更新。
- 关闭网络后壁纸和规则模式仍能运行。
- 非法 JSON、异常配置、空输入、快速连续操作。
- `SceneConfig` 验证、序列化、Repository、规则解析单元测试。
- 粒子生命周期与时间步进基础测试。
- 长时间运行的内存和掉帧观察。

## 6. GitHub 交付标准

- `README.md`：功能、截图/视频占位、技术栈、架构、构建步骤、AI 开发记录、限制与计划。
- `docs/architecture.md`：组件依赖图、渲染线程、生命周期、数据流。
- `docs/performance.md`：设备型号、Android 版本、测试步骤、FPS/内存实测结果；没测就写“未测试”。
- `docs/ai-workflow.md`：Cursor/Trae/Codex Prompt 记录、人工修复、验证方式。
- `LICENSE`：Apache-2.0（第三方素材和模型按各自许可证处理）。
- APK 构建命令与输出位置；演示录屏 60–90 秒。

### 6.1 录屏脚本

| 时间 | 内容 |
|---|---|
| 0–10 秒 | 首页预览 |
| 10–20 秒 | 三场景切换 |
| 20–30 秒 | 调整雨量/风速/光照 |
| 30–45 秒 | 自然语言改变场景 |
| 45–60 秒 | 若已集成真实 MNN，展示断网本地模型；否则展示规则模式并明确标注 |
| 60–75 秒 | 设置为 Android 系统动态壁纸 |
| 75–90 秒 | 性能面板、代码结构 |

## 7. 给 Codex 的执行规范（必须遵守）

你是资深 Android、OpenGL ES 和端侧 AI 工程师。请在当前仓库按上述计划开发真实可运行的 Android 原生项目。

**硬性规则：**

1. **不要一次实现所有阶段。** 首先只执行 P0、P1；完成后报告并等待确认。
2. 使用稳定且相互兼容的 Android Gradle Plugin、Kotlin、Compose 和依赖版本；不能凭空编造版本。
3. 先确保能编译，再做功能、性能和视觉完善。
4. 不使用 Flutter、React Native、WebView 替代 Android 原生实现。
5. 不使用静态图/视频冒充实时 GPU 渲染。
6. 不使用随机数或硬编码假数据冒充真实性能指标。
7. 不把规则解析器冒充 LLM，不把未接入的 MNN 冒充已完成。
8. 所有 GL 操作都在正确线程执行；处理 Surface 生命周期与 EGL 资源释放。
9. 不在 APK 中硬编码私有 API Key。
10. 遇到无法构建、无法访问 SDK 或无法真机测试的情况，明确报告限制和下一步，不伪造结果。
11. 每完成一个阶段，更新开发日志，列出改动文件、构建命令、测试结果、剩余问题。
12. 有技术取舍时先说明理由，再做最小、可验证的实现。

### 当前任务：只执行 P0 + P1

1. 检查仓库和本地 Android 开发环境。
2. 确定兼容的 Gradle / Kotlin / Compose / SDK 版本。
3. 创建 Android 工程和必要的 Gradle 配置。
4. 建立最小模块结构，避免无意义空模块。
5. 创建 Compose + Material 3 首页。
6. 尝试执行 Gradle 构建和适用测试。
7. 生成 `docs/development-log.md`。
8. 报告文件变更、构建结果、环境问题和 P2 计划。
9. **完成后停止，等待用户回复“继续 P2”。**

## 8. 后续可选增强

### 8.1 AI 场景导演

支持根据自然语言生成经过 Schema 校验的时间轴关键帧，例如 60 秒内由黄昏过渡到雨夜；渲染引擎使用插值平滑切换天空颜色、光照和雨量。

### 8.2 真实天气联动

在用户明确开启后获取天气数据，将晴、多云、雨映射到场景参数；支持手动城市或手动模式，不要求必须授予定位权限。控制请求频率与耗电。

### 8.3 MNN 端侧推理

使用真实兼容的小模型把用户指令转换成 SceneConfig；记录加载时间、推理耗时、内存与失败率。与持续运行的壁纸 GL 线程解耦。

### 8.4 iOS 展示端

独立使用 SwiftUI + Metal 制作相同场景的应用内预览和壁纸素材导出；不承诺实现 Android 式持续运行的系统动态壁纸。

---

**验收总原则：**真实可运行 > 核心功能正确 > 稳定性 > 性能 > 视觉效果 > 扩展功能。
