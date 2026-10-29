# 开发日志

> 每次里程碑结束更新。记录：改动文件、构建命令、测试结果、已验证 / 未验证项、剩余问题。
> 未做过的测试一律写“未测试”，不使用随机数或硬编码数据冒充实测结果。

## 环境基线（P0）

本机初始状态与补装结果：

| 项目 | 初始状态 | 处理结果 |
|---|---|---|
| JDK | 仅 JDK 8（1.8.0_411），`JAVA_HOME` 未设置 | 安装 Temurin JDK 17.0.20.1 到 `D:\Android\jdk-17` |
| Android SDK | 未安装，仅在 PATH 中找到孤立的 JRE 8 | 安装 cmdline-tools 13114758 + `platforms;android-35` + `build-tools;35.0.0` + `platform-tools` 到 `D:\Android\Sdk` |
| Gradle | 未安装 | 使用 Gradle 8.9 生成 Wrapper（`gradlew`），仓库内不提交发行包 |
| Android Studio / 模拟器 | 未安装（仅有 JetBrains Rider） | 未安装；暂不使用 IDE，直接用命令行构建 |
| 真机 / 模拟器 | `adb devices` 列表为空 | **未验证**：无任何设备或模拟器可用于安装与真机测试 |
| 磁盘 / 内存 | D 盘 190 GB 可用，内存 23.8 GB | 满足构建需求 |

网络：可访问 `services.gradle.org`、`dl.google.com`、`repo1.maven.org`、`github.com`；系统代理 `127.0.0.1:1080`，
已在 `~/.gradle/gradle.properties` 配置 `systemProp.*` 代理与 `org.gradle.java.home`（不入仓库，避免绑定本机环境）。

常用命令（本机）：

```bat
set JAVA_HOME=D:\Android\jdk-17
set ANDROID_HOME=D:\Android\Sdk
D:\LivingWallpaper\gradlew.bat -p D:\LivingWallpaper :app:assembleDebug
```

SDK 组件安装记录：

```bat
D:\Android\sdkm.bat --install platform-tools platforms;android-35 build-tools;35.0.0
```

## P0 + P1 — 工程初始化与首页（进行中）

### 版本选择（已核对可下载，未凭空编写）

| 组件 | 版本 | 说明 |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | 与 Gradle 8.9、JDK 17 兼容的稳定组合 |
| Gradle | 8.9 | AGP 8.7.x 官方支持范围内 |
| Kotlin | 2.0.21 | Compose Compiler 插件随 Kotlin 插件提供 |
| Compose BOM | 2024.10.01 | 统一 Compose 运行时 / UI / Material3 版本 |
| androidx.core / lifecycle / activity | 1.15.0 / 2.8.7 / 1.9.3 | `enableEdgeToEdge()` 需要 activity ≥ 1.8 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 29 | minSdk 29 = Android 10，符合计划要求 |
| JDK | 17 | AGP 8.x 要求 JDK 17 |

已通过 HTTP HEAD 请求逐个确认上述 artifact 在 Maven / Google Maven 上真实存在。

已知告警：Lint 提示 AGP 有更新版本 9.4.1。当前刻意保持在 8.x 稳定线，
升级 AGP 9 会连带要求新的 Gradle / Kotlin 组合，属于 P2 之后统一评估的事项。

### 改动文件

新增：

- `settings.gradle.kts` — 仓库名、插件与依赖仓库、仅 `include(":app")`
- `build.gradle.kts` — 根工程只声明插件（不 apply）
- `gradle/libs.versions.toml` — 版本目录，所有版本集中管理
- `gradle.properties` — 项目级配置（不含本机路径/代理）
- `gradle/wrapper/gradle-wrapper.properties` + `gradle-wrapper.jar`、`gradlew`、`gradlew.bat`
- `local.properties` — `sdk.dir`（不入库）
- `.gitignore`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable/ic_launcher.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/java/com/livingwallpaper/app/MainActivity.kt`
- `app/src/main/java/com/livingwallpaper/app/ui/HomeScreen.kt`
- `app/src/main/java/com/livingwallpaper/app/ui/theme/Theme.kt`
- `app/src/main/java/com/livingwallpaper/app/ui/theme/Color.kt`
- `app/src/main/java/com/livingwallpaper/app/ui/theme/Type.kt`
- `app/src/test/java/com/livingwallpaper/app/BuildConfigTest.kt`

未创建：`core/` `renderer/` `wallpaper/` `ai/` `benchmark/` 等模块。
按执行规范第 1、4 条，这些模块在对应里程碑真正落地时再 `include`，避免先造一堆空模块。

### 首页内容（P1）

- 顶部 `TopAppBar`，Compose + Material 3，支持深色/浅色主题。
- 预览卡片：**明确标注**为 OpenGL ES 渲染器占位（计划 P2/P3），只用渐变色块 + 文案，
  不使用静态图片或视频冒充实时渲染。
- 功能入口：`设置为壁纸(P2)` / `AI 创造(P6)` / `场景库(P4)` / `性能中心(P7)`，
  点击后 Snackbar 提示所处里程碑，不做假功能。
- 内置场景列表：Forest Night / Sunset Ocean / Star Valley 的名称、特性描述与计划里程碑。
- 构建信息：直接读取 `BuildConfig` 的 `APPLICATION_ID`、`VERSION_NAME`、`VERSION_CODE`、
  `BUILD_TYPE`、`MIN_SDK_VERSION`、`TARGET_SDK_VERSION`、`COMPILE_SDK_VERSION`，全部为真实值。

### 构建与测试命令

```bat
D:\LivingWallpaper\gradlew.bat -p D:\LivingWallpaper :app:assembleDebug
D:\LivingWallpaper\gradlew.bat -p D:\LivingWallpaper :app:assembleRelease
D:\LivingWallpaper\gradlew.bat -p D:\LivingWallpaper :app:testDebugUnitTest
D:\LivingWallpaper\gradlew.bat -p D:\LivingWallpaper :app:lintDebug
```

### 结果

| 任务 | 结果 |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL，输出 `app/build/outputs/apk/debug/app-debug.apk`（约 23.7 MB） |
| `:app:assembleRelease` | BUILD SUCCESSFUL，输出 `app/build/outputs/apk/release/app-release-unsigned.apk`（约 17.7 MB，未签名） |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL，3 个用例通过（校验 BuildConfig 注入值） |
| `:app:lintDebug` | BUILD SUCCESSFUL，无 Error；剩余 1 处 `RedundantLabel` 告警已修复，AGP 版本更新提示为有意保留 |

### 已验证 / 未验证

**已验证：**

- Gradle 配置脚本可编译，依赖可从远端仓库解析。
- Kotlin + Compose + Material 3 代码可编译，生成 debug / release 两个变体的 APK。
- JVM Target 与 JDK 17 一致，无 `Inconsistent JVM-target` 错误。
- 单元测试通道可用（3 个用例实际执行并通过）。
- Lint 无 Error。

**未验证（当前环境不具备条件，不能宣称通过）：**

- APK 安装、启动、首页实际渲染效果 —— **未测试**，无真机 / 模拟器。
- 系统动态壁纸设置流程 —— 未实现（P2）。
- 任何 FPS、内存、帧耗时指标 —— **未测量**，P1 无渲染器，也不伪造数据。
- 旋转 / 锁屏 / Surface 重建等生命周期行为 —— 未测试。
- Android Studio 打开工程 —— 本机未安装 Android Studio，未测试。

## P2 — WallpaperService + EGL/GL 最小可运行版

**新增文件**：`renderer/` 与 `wallpaper/` 两个模块；`renderer` 内含
`EGLManager`、`ShaderProgram`、`VertexMesh`、`GLRenderer`、`FrameTiming`、
`AssetShaders`、全屏渐变场景与两个着色器；`wallpaper` 内含
`LivingWallpaperService`、Manifest 注册、`res/xml/wallpaper.xml`、缩略图 PNG。

**关键技术点**

- EGL 配置优先请求 `EGL_OPENGL_ES3_BIT`，失败再退回 `EGL_OPENGL_ES2_BIT`，
  上下文固定请求 `EGL_CONTEXT_CLIENT_VERSION = 3`。
- 任一步失败都会调用 `release()` 清理已创建的部分资源，不留半初始化状态。
- 所有 EGL 常量显式定义为本地常量，避免依赖不同 API Level 上可能缺失的静态字段。
- 首页预览改用 `AndroidExternalSurface`；不可用 `awaitClose`（该 API 不存在），
  实际使用 `try { awaitCancellation() } finally { renderer.stop() }`。

**修复的问题**

1. `build.gradle.kts` 里自定义的 `compileSdkVersion` 局部变量与
   `AndroidExtension` 上同名的废弃属性冲突（Kotlin 解析到扩展属性，类型变成 String）。
   改名为 `sdkCompile / sdkTarget / sdkMin`。
2. `GLRenderer { ... }` 被 Kotlin 解析为把尾随 lambda 传给最后一个参数 `threadName: String`。
   显式写成 `GLRenderer(sceneFactory = { ... })`。
3. 子模块引用 `com.android.library` 报 “Error resolving plugin”：
   必须在根工程 `plugins { alias(...) apply false }` 里统一声明版本。

**结果**：`assembleDebug` / `assembleRelease` 成功；3 个 BuildConfig 用例通过；
Lint 无 Error。合并后的 Manifest 中确认存在带 `BIND_WALLPAPER` 权限的服务、
标准 Intent Filter 与 `@xml/wallpaper` 元数据。

---

## P3 — core:model + Forest Night

**新增**：`core/model`（`SceneConfig` / `SceneType` / `WeatherType` / `SkyColor`）；
`renderer` 内 `Wind`、`ParallaxController`、`ParallaxAware`、`RainSystem`、
`FireflySystem`、`ForestGeometry`、`ForestScene`，以及
`silhouette / rain / firefly` 三对着色器。

**关键技术点**

- 顶点格式统一 `(x, y, sway)`；山脉与三层树木用固定种子生成，形态可复现。
- 摆动在顶点着色器里按 `aSway` 计算，CPU 每帧只更新两个 uniform。
- 视差偏移按图层深度系数缩放（远山 0.020 → 近树 0.085）。
- `WallpaperService` 只在场景实现 `ParallaxAware` 时才注册加速度计，
  不可见时立即注销。

**修复的问题（由单元测试发现，属真实缺陷）**

1. `SceneConfig.clamped()` 先 `coerceIn` 再判断有限性：
   `Float.POSITIVE_INFINITY` 会被夹成 1f 而不是回退默认值，
   `NaN` 则因为「与任何值比较都是 false」直接透传。
   改为先判断 `isFinite()`，并补了专门的回归测试。
2. `dropsFallDownward` 用例不稳定：单条雨滴可能刚好在观测窗口内回绕到顶部。
   改为统计「向下移动的雨滴占比」，允许个别回绕。

**结果**：45 个用例通过。

---

## P4 — Sunset Ocean + Star Valley

**新增**：`OceanScene`、`StarValleyScene`、`MeteorSystem`、
`sunset.frag`、`ocean.frag`、`star_sky.frag`、`quad.vert`；
`RainLayer` 泛化为 `LineLayer`（雨滴与流星共用），
`GradientLayer` 泛化为 `FullscreenLayer`（渐变/夕阳/星空共用同一套全屏几何）。

**修复的问题**

1. Kotlin 构造器参数未声明 `val` 时，在成员函数中不可见
   （只在属性初始化器里可见）。四个图层类的 `vertexSource / fragmentSource`
   与 `LineLayer` 的 `capacity` 都补上了 `private val`。
2. 流星最初用「每帧按槽位掷骰子」触发生成，导致生成数量随对象池槽位数放大。
   改为按「每秒生成数」积分触发，并补了帧率无关性测试。

**结果**：renderer 用例增至 52 个，全部通过。

---

## P5 — 参数编辑器 + 场景库 + DataStore

**新增**：`core/data`（`SceneRepository` 接口、`DataStoreSceneRepository`、
`SceneConfigPreferences`、`SceneRepositoryProvider`）；
App 侧 `SceneViewModel`、`AppRoot`（底部导航）、
`PreviewScreen` / `SceneLibraryScreen` / `ParameterScreen`。

**关键技术点**

- `GLRenderer` 改为接收 `(SceneType) -> Scene` 工厂 + 初始配置，
  配置经 `ConcurrentLinkedQueue` 下发，场景类型变化时由 GL 线程释放旧场景并重建。
- 壁纸 Engine 用 `repository.snapshot()` 同步取初值（不做阻塞 IO），
  随后用 Flow 持续跟随；UI 与壁纸共用进程内同一个仓库实例。
- `preferencesDataStore` 委托 + `stateIn(Eagerly)`，
  使 `snapshot()` 在对象创建后极短时间内即可读到磁盘真实值。

**修复的问题**

1. `dataStore.updateData` 的 transform 收到的是不可变 `Preferences`，
   最初写入 `toMutablePreferences()` 的副本却返回了原对象，改动丢失。
   改为返回可变副本。
2. `ParameterScreen` 的 `onUpdate { spec.apply(this, it) }`：
   带接收者的 lambda 没有隐式 `it`，编译报错。改为显式命名参数。
3. `SceneViewModel.kt` 中引用 `R.string` 缺少 import。

**结果**：57 个用例通过（新增 DataStore 映射测试 5 个）。

---

## P6 — 自然语言控制（规则模式）

**新增**：`ai` 模块（`AIProvider`、`GenerationResult`、
`InstructionPatch`、`RuleBasedInstructionParser`、`SceneConfigJsonParser`、
`RuleBasedProvider`、`PromptBuilder`）；App 侧 `AiScreen` 与 `AiUiState`。
`core:model` 启用 `kotlinx-serialization`，`SceneConfig` 标记 `@Serializable`。

**关键技术点**

- 解析分两层：指令 → `InstructionPatch` → `SceneConfig`。
  中间层可测试、可解释，且统一走 `clamped()`。
- 关键词附近开窗（前 5 后 10 字符）匹配「大/小/停/最大」等修饰词；
  找不到方向词就不做改动，避免误伤只提到名词的句子。
- 无法理解时抛 `InstructionNotUnderstoodException`，调用方保留原配置并提示。
- JSON 解析允许部分字段与未知字段，失败只修复一次（裁多余文本 + 去尾逗号）。

**与计划书的偏差**：`AIProvider` 返回值由 `SceneConfig` 改为 `GenerationResult`
（命中规则说明 + 是否变化 + 真实耗时）。理由见 `docs/architecture.md` 第 7 节。

**修复的问题**

1. FPS 正则只支持「60 帧 / 60fps」，不支持「帧率调到 60」。
   增加 `FPS_BEFORE` 正则；初版误取 `groupValues[2]`，
   实际第一个分组是非捕获组，应为 `groupValues[1]`。
2. 测试用例「来一场雨」不含任何天气关键词 → 改为「下雨了」。

**结果**：ai 模块 33 个用例通过。

---

## P7 — 性能档位与监控

**新增**：`PerformanceLevel`、`SceneConfig.performanceLevel / reduceMotion`、
`RenderStats` + `RenderStatsHolder`、`PerformanceScreen`。

**关键技术点**

- 档位真实改变 `particleScale` / `motionScale`，作用于雨滴、萤火虫、流星的
  密度与强度以及风的摆动幅度；`reduceMotion` 直接归零。
- 帧间隔取相邻两帧的真实时间差（包含限帧 sleep），
  因此 `measuredFps` 反映用户实际看到的刷新率。
- 丢帧判定：间隔 > 目标帧预算的 1.5 倍。
- 内存取 `Runtime` 与 `ActivityManager`，每秒刷新，无估算。

**结果**：100 个用例全部通过。

---

## P8 — 交付物

**新增**：`README.md`、`docs/architecture.md`、`.github/workflows/android.yml`。

**未做**：LICENSE（项目计划未要求，README 已明确说明）；
README 截图（没有真机截图，不放占位图冒充）。

### 最终验证状态

| 项目 | 状态 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease` | 通过（未签名） |
| `testDebugUnitTest`（全模块） | 通过，100 个用例，0 失败 |
| `:app:lintDebug` | 报告 `No issues found.`（0 Error / 0 Warning） |

补充修复：删除未使用字符串 `preview_title`、`section_scenes`、`section_build`；
新增 `res/xml/data_extraction_rules.xml` 与 `res/xml/backup_rules.xml`
并在 Manifest 声明 `android:dataExtractionRules` / `android:fullBackupContent`
（关闭云端备份，避免配置随备份被跨设备恢复）。

### 真机验证（2026-10-22，Xiaomi 14 Pro / HyperOS · Android 16 / API 36 / Adreno 750 · GLES 3.2）

以下全部为真机实测（截图存于 `docs/shots/`，01–40 号为验证过程留档）：

视觉改进（用户反馈"落日海岸有点丑"，53/54 号截图为改后效果；
二次反馈"很割裂"后重构为倒影模型（55 号）；
三次反馈"上下粗暴切分两半"并提供参考照片后做结构性改造，57 号为最终效果）：
参考照片分析——协调感来自：山影打断地平线直线、反射光域占海面大面积、
细碎浪花、全画面统一暖色系。据此：
1) `OceanScene` 挂两层远山剪影（复用 SilhouetteLayer + ForestGeometry.buildMountains，
   后者新增 xLeft/xRight 参数，两侧山体中间留太阳豁口；半透明营造大气雾感，
   海面 quad 覆盖山体水平线以下部分）；
2) `ocean.frag` 反射光域大幅加宽（近景宽度 0.40→0.62 NDC），加白色浪花波前
   （3 条正弦蜿蜒波前随时间向观察者推进，波峰+噪声双重调制避免连成白斑）；
3) 山体被夕阳大气染色（远层≈地平线色暗版，近层深紫棕）。
`ForestGeometry.buildMountains` 改动向后兼容（默认参数不变），森林场景不受影响。

**调色方法改为数据驱动（关键转折）：** 此前靠主观判断调色，反复返工。
改为用 `docs/analyze.py` 提取参考照片与本机截图的像素指标
（纵向亮度剖面、地平线亮度跳变、平均饱和度、构图比例），逐项对齐：

| 指标 | 参考照片 | 初始 | 最终 |
|---|---|---|---|
| 地平线亮度跳变 | 0.070 | 0.213 | **0.094** |
| 平均饱和度 | 0.48 | 0.25 | **0.50** |
| 天空顶部亮度 | 0.711（明亮暖橙） | 0.305（暗蓝灰） | **0.642** |
| 海面色系 | 暖棕金 | 灰蓝 | 暖棕金 |
| 海平线位置 | 36% | 65%（上下对半） | 36% |

**视觉复核闭环（切换到多模态模型后完成，compare-ref-vs-ours.png 为证）：**
真正"看到"画面后暴露出指标测不到的问题，逐项修复：
1) 太阳圆盘直径约为画面 30%（参考图仅 5%）——缩到 0.022-0.030 半径、光晕收紧，
   高度改为从山脊后升起（0.085）；
2) 山体曾像两块对称补丁/吞掉太阳——改为三层全宽山脊（端点全部移出屏幕避免竖直切边），
   峰高压到屏幕 30% 附近，颜色改为受光亮暖色（参考图远山 lum 0.671 比天空还亮，
   不是深色剪影），山脚加雾化渐变（SilhouetteLayer 新增 uFadeBase/uFadeRange，
   默认关闭不影响森林场景）；
3) 海面曾像金色噪点——波纹密度砍 70%、阈值提高，基色平整化；
4) 天空云带 bug：旧 band 在 vUv.y>0.62 衰减，而天空区恰在 0.64 以上，云全被压没——
   改为越高云越多；
5) 海面色系修正：参考图远海实为低饱和灰绿（(0.51,0.47,0.41)），
   浓橙只属于光路与沙滩——SHALLOW/DEEP 降饱和提灰度。
`docs/sample_ref.py` 按区域采样参考图真实颜色（行平均会被沙滩/光带污染）。
最终：海天亮度跳变 0.014（参考 0.070）、平均饱和 0.45（参考 0.48）。
`deploy.py` 优化：install 后壁纸未被重置时跳过 livepicker 流程（该流程在
HyperOS 上不稳定，偶发列表点击无响应，重试逻辑已加）。
`:renderer:testDebugUnitTest` 回归通过。

**场景替换：Sunset Ocean → Aurora Lake 极光雪山湖（用户决策）：**
日落海岸为摄影级写实参照，程序化渲染难以企及，用户决定删除并换新主题。
删除：`OceanScene.kt`、`sunset.frag`、`ocean.frag`、`SceneType.OCEAN`、
`SUNSET_FRAG/OCEAN_FRAG` 常量、`scene_sunset_ocean(_desc)` 文案、AI 日落关键词。
新增：`AuroraScene.kt`（极光夜空 → 三层雪山 → 湖面倒影 → 飘雪）、
`aurora_sky.frag`（星星 + 两道流动极光帘幕 + 薄云 + 地平线冷雾）、
`aurora_lake.frag`（极光/星点倒影 + 水平波纹）、`SnowSystem`（慢速飘落 + 正弦摇摆）、
`SceneType.AURORA`、"aurora-lake"、飘雪参数滑杆、AI 关键词（极光/雪山/湖泊/aurora）。
`SnowSystemTest` 8 个用例，其中一个暴露并修复了 `reset()` 未清零顶点计数的缺陷
（否则下一帧会绘制到陈旧坐标）。

**Aurora Lake 真机迭代（65 号截图起，共 4 轮目视收敛）：**
v1 极光弱到不可见（帘幕位置偏上+增益不足）且雪花线过长像流星雨 →
增益 ×3.2、帘幕下移、雪花线缩短、湖水/山体提亮；
v2 极光铺满整屏无层次、湖面倒影成"百叶窗"竖纹、山被盖住 →
rays 阈值收紧产生亮暗带、横向包络让帘幕有尽头、倒影模糊化（低频 wobble）；
v3 湖面倒影里的"星星"渲染成规则方块（floor 网格 + step 无距离衰减）→
改点状星体（distance 衰减），极光下缘拖尾收窄、加横向包络；
v4 湖面竖纹仍偏规则、雪花太淡、地平线亮带突兀 → 倒影扰动幅度加大、
湖面整体压暗（uHorizonColor×0.72）、冷雾减弱、雪花加长提亮、远山 alpha 提高。
最终效果（64 号截图）：帘幕有尽头与亮暗带、星空可辨、湖面柔和倒影、
山脊剪影可见、海天跳变 0.037。全量回归通过、无 FATAL。

**第 5 轮：用户反馈"又是割裂感"→ 定位到湖平线硬边（根因与日落时相同）：**
湖面顶部被极光倒影顶亮（depth→0 处 stretch=1），亮度 0.14→0.35 突跳 2.5 倍。
修复遵循"湖顶=天空底"原则：湖顶颜色直接取 uHorizonColor（无缝），
倒影在 depth<0.3 处被雾遮住（smoothstep 淡出），峰值移到中景，
湖水基色整体压暗一档（天亮水暗）。修复后天空 0.141 → 地平线 0.146 →
湖远 0.237（渐变），无硬边。全量回归通过。

**第 6 轮：用户反馈"极光有方块感、不细腻"→ 噪声质量问题：**
根因是 `sin(dot)` 版 hash + smoothstep 插值在高倍频（26×）下露出网格块。
修复：hash 换 Dave Hoskins 版本（分布更均匀），插值升级为 quintic（二阶连续），
极光射线从 fbm 高频采样改为**多频正弦叠加 + 低频域扭曲**（正弦无限平滑，
从根本上没有网格），湖面倒影用同一组正弦保持形态对齐且幂次更低（更柔）。
方框感消除，帘幕呈现平滑弯曲的光柱。

**第 7 轮：用户反馈"倒影不真实、湖面过于平静"→ 倒影模型升级：**
旧倒影只是把竖条纹照搬下来。重写湖面着色器：
1) 真镜像——水面 depth 处反射的天空高度 = 湖平线 + depth×0.42
   （近处水面映高空偏紫、远湖映地平线偏绿，符合镜像几何）；
2) 波浪把倒影切碎成断续水平条带（sin+noise 双重扰动采样位置）；
3) 水面起伏——横向波纹（透视间距：近稀远密）+ 波峰反光 + 随时间推进，
   死水变活水。全量回归通过。

**第 8 轮：用户反馈"起伏太规整、统一了"→ 波纹随机化：**
单一频率正弦的横向条纹等间距像百叶窗。改为三组不同频率/相位的正弦叠加
（0.61×/1.0×/1.73×），相位被噪声推歪，波纹强度再加一层噪声调制
（局部有浪、局部平静）；倒影切碎同样双频+双层噪声。

**遗留视觉项（可继续打磨）：** 太阳可偏左更贴参考构图；参考图的对角线海岸
（右侧沙滩）需要新几何，成本较高；浪花线可再明显一些。

配套工具：`docs/deploy.py`（构建→装机→按 UI 文字精确匹配重设壁纸→截图，
装机后系统会把壁纸重置，必须重设）、`docs/analyze.py`（指标对比）、
`docs/peek.py`（快速看色彩分布）、`docs/parse_ui.py`（UI 层级解析）。
`:renderer:testDebugUnitTest` 回归通过。

| 项目 | 结果 |
|---|---|
| APK 安装 + 冷启动 | 通过，无崩溃 / ANR，logcat 无 FATAL 与 GL/EGL 错误 |
| 预览页 OpenGL 渲染 | 通过：Forest Night / Sunset Ocean / Star Valley 三场景均正常出帧，非黑屏 |
| 场景热切换 | 通过：场景库选择 → 预览即时重建 |
| 参数实时生效 | 通过：天气 雨→晴，预览雨滴实时消失 |
| AI 规则模式 | 通过：示例指令解析正确（≤1ms）、应用后场景切换正确 |
| 配置持久化 | 通过：force-stop 后冷启动仍为 AI 应用后的 Sunset Ocean |
| 性能档位 | 通过：省电档生效（粒子 0.35 / 动态 0.40 / 建议 15） |
| 减少动态效果 | 通过：开启后预览雨滴消失 |
| 实测性能 | 均衡档 30FPS：实测 29.3–29.7 FPS，平均帧间隔 33.7–34.1ms，累计丢帧 0；Java 堆 9/256 MB |
| 应用内 Surface 重建 | 通过：页面切换反复启停 GL 线程无异常 |

**真机限制说明（定位与绕行，见 41–52 号截图）：**

- HyperOS 上 `ACTION_CHANGE_LIVE_WALLPAPER` 直达式预览 Activity
  （`com.android.wallpaper.livepicker/.LiveWallpaperChange`）秒退不可用。
  但标准选择器 `ACTION_SET_WALLPAPER` / `LIVE_WALLPAPER_CHOOSER`
  （`LiveWallpaperActivity` 列表 + `LiveWallpaperPreview` 预览）在 HyperOS 上**完全可用**：
  列表展示"灵境动态壁纸"→ 预览页正常渲染本应用 Engine → 点击"设置壁纸"成功设置，
  `dumpsys wallpaper` 确认 `mWallpaperComponent` 切换为本应用服务。
  应用侧"设置为壁纸"按钮后续可考虑补充 `ACTION_SET_WALLPAPER` 兜底入口。
- HyperOS 下 adb `user_rotation` 无法强制该应用旋转，旋转未验证。

**壁纸端到端（Engine 上屏）实测通过：**

- 桌面壁纸实际运行 Sunset Ocean（太阳光晕、海面反光可见，51 号截图）
- 应用内切 Forest Night → 桌面壁纸实时切换（DataStore → Engine 配置热同步，52 号截图）
- 壁纸进程（与应用同进程）内存：TOTAL PSS 144.8 MB
  （EGL mtrack 20.5 MB + GL mtrack 0.9 MB + Native Heap 17.8 MB dirty + Java 堆 11.6/33 MB），
  双 GL 上下文（应用预览 + 壁纸 Engine）并存时的真实开销
- 设置完成后回桌面无崩溃，logcat 无 FATAL

---

### P0 + P1 遗留的环境说明

P0 阶段搭建的环境（JDK 17 / Android SDK 35 / Gradle 8.9 Wrapper）在后续所有阶段沿用，
未再改动。唯一持续存在的限制是**没有真机或模拟器**，
因此 P2–P7 每个阶段的“已验证”都只覆盖编译、单元测试与 Lint。


## 第 9 轮：湖面"不像湖"的根因与修复

现象：用户第一反应是"渲染错了"。像素度量此前一直被两个问题污染——
1. 桌面截图含应用图标，把湖面中位数顶高（曾据此得出"湖比天空亮 1.31"的误判）；
2. 锁屏在这台机器上用的是 MIUI 画报壁纸，不是我们的动态壁纸。

改用 livepicker 预览页全屏截图（docs/shot_preview.py）后看清楚了三个真实原因：

1. **极光倒影加得太狠**：specular*1.7 + glow 0.075 让湖面成了一面比天空还亮的
   紫色光幕，纵向射线过长，看起来像"挂着的帘子"而不是水。
2. **缺雪山倒影**：湖里只有天空倒影，没有任何与岸上景物对应的形状，
   湖面就是一块无内容的渐变——这是"像渲染错误"的直接原因。
   现在把三层山脊线参数（振幅+相位）作为 uniform 传给湖面着色器，
   在片元里复现同一条山脊线，以湖平线为轴翻下来，边界被波纹切碎。
3. **山脚雾化亮带**：SilhouetteLayer 的底部雾化在湖平线上方留了一条亮带，
   把山和倒影切断。现在山脚直接压到湖平线以下（MOUNTAIN_BASE_Y = LAKE_LINE-0.02），
   露出部分被不透明湖面盖住，山与倒影无缝相连；雾化彻底移除。

同时：远湖亮、近水暗的纵向梯度（LAKE_SHALLOW/DEEP_BASE）恢复"有深度的水"读数；
波峰改为加色（暗底上做乘法看不出层次）；极光倒影降到点睛强度（specular*0.55 + glow 0.042）。

工具：docs/shot_preview.py（全屏干净截图）、diag_lake.py 改为逐行中位数抗 UI 污染、
支持传图路径。最终湖面/天空亮度比 0.89（夜景区间合理），山体倒影肉眼可辨。

回归：app:assembleDebug + renderer/ai 单测全部通过，logcat 无 FATAL。


## 第 10 轮：湖与山"层级反了"的修复

现象：用户看到"湖面好像在山上"。逐列采样（f=0.40~0.56，x=0.28/0.55/0.80）定位到两个真实 bug：

1. **MIRROR 常量与 depth 的归一化尺度不一致**：
   `depth = (uHorizon - y) / (uHorizon + 1.0)` 的跨度是 1.06，
   而倒影换算用的是硬编码 `MIRROR = 0.84`，
   导致山体倒影比山本体高出 26%，极光倒影也采到了错误的天空高度。
   改成统一的 `lakeSpan()`（= uHorizon + 1.0）。
2. **倒影边界被 chop 纵向推得过远**：`chop * 2.2` 在深处可把边界推低 ±0.23 depth，
   倒影高度达到山本体的 3 倍，且与山同色、过渡带 77px——
   山与倒影融成一块连续的暗色形体，水线消失，看上去就是"湖盖在山上"。
   改成波浪主要横向推采样点（wobble*0.40），纵向只留 0.55 抖动，
   过渡带收窄到 0.038 depth，倒影高度回到与山 1:1。

另加**水线高光**：岸线处水面以掠射角反射天空，画一条细亮边
（smoothstep(0.016,0.001) 的 shoreline，mix 到 uHorizonColor*1.20）。
它是"这里是水面"最直白的读数，也把山与倒影的分界标出来；
倒影色调改为 uMountainColor*0.62 + uShallowColor*0.10，与山上本体拉开色差。

修复后采样：山(暗) → 水线(亮 0.13/0.17/0.31) → 倒影(暗，高度≈山) → 更亮的水，
层级关系一眼可读。回归测试全部通过。


## 第 11 轮：以回退后的版本为基线，修「流光长在山上」与水面网格

第 10 轮的改动（lakeSpan / 水线高光 / 波浪横向推）经用户确认全部回退，
当前基线 = 第 9 轮结束时的版本。

用户反馈：湖面怪，像层级错了，流光长在山上。定位到三个原因：

1. **极光帘幕的下缘正好切在湖平线上**。
   `body = exp(-|d|*3.2) * smoothstep(-0.34, -0.02, d)`，下缘 d=-0.34
   对应 y = center-0.34 = 湖平线。山体只挡住了下面那一截，
   于是流光柱子的可见下端正好终止在脊线上，看起来像光从山顶长出来。
   修复：加高度裁剪 `smoothstep(uLakeLine+0.17, uLakeLine+0.33, uv.y)`
   （天空与湖面用同一条，保证倒影一致），流光在脊线上方 8% 屏高处就已淡出。

2. **水面成了「栅栏 + 梯子」的方格**。
   竖向射线（`sin(x*30)` 等距）× 横向波带（`pow(depth,0.7)*34` 等距）交叉成网格。
   修复：倒影采样点横向随波扭动（`x + chop*1.4`）、射线指数 2.0→1.4 变钝、
   加大尺度强弱噪声（有的水面泛绿、有的全黑）。

3. **正弦叠加在掠射角下给的是"大理石纹"**。
   等距正弦 = 梯子；等向噪声 = 斑块。水纹的正确形状是"横向拉长的细条"，
   所以波纹改用 `noise(vec2(x*7, depth*30))` + `noise(vec2(x*15, depth*58))`，
   横向频率低、纵向频率高，波峰用 smoothstep 取窄带，波谷压暗。

附带：远山/中山的基色压暗（0.230,0.290,0.400 → 0.120,0.158,0.232），
原来远山比天空亮一倍且轮廓光滑，读起来像一片半透明雾。

回归：app:assembleDebug + renderer/ai 单测通过，Lint 无告警。


## 第 12 轮：交付物补齐（文档同步 / 性能实测 / 录屏）

按计划书第 6 节的交付标准补齐缺失项，并修正文档里过期的事实描述。

**文档同步**（Ocean 场景已删除，多处仍在描述它）：

- `README.md`：场景表 Sunset Ocean → Aurora Lake；删除"未真机验证"的过期声明，
  改为真实设备与验证范围；补充已知问题。
- `docs/architecture.md`：绘制顺序表换为 Aurora Lake（天空→三层雪山→湖面→飘雪）；
  粒子表补 SnowSystem(700)；已知限制改为真机已验证 + 倒影是逐片元重算几何的取舍说明。
- `LivingWallpaper_Codex_Plan.md`：场景名、`SceneType` 枚举、`scene/` 目录注释同步，
  并加一条「实施偏差」说明 Sunset Ocean 被替换的原因（保留历史描述原样）。

**LICENSE**：从 Gradle 发行版内置的 Apache-2.0 全文中裁剪出纯条款部分（177 行），
追加版权声明；不用第三方打包页里的 bundled 许可。

**docs/performance.md**（新增）：真机实测，含测试环境、测量方法、帧率表、内存表、复现步骤。
关键数据（Xiaomi 14 Pro / Android 16 / 1080×2400）：
- 三场景 @60FPS 目标均 55 FPS 左右（Forest 55.3 / Aurora 54.9 / Star 56.0），差异 <1.1 FPS
- 省电 54.2 / 均衡 54.9 / 高画质 55.4 —— 档位不改帧率，GPU 有余量
- 30FPS 档实测 29.6 FPS、丢帧 0，限帧逻辑正确
- 壁纸态 PSS 213–216 MB（EGL 80 MB 占大头）；App 预览态 276–292 MB
- 新增 `docs/measure_perf.py` 自动化采集

**docs/ai-workflow.md**（新增）：分工、代表性 Prompt、AI 引入的错误及发现方式、验证方式、短板。

**演示录屏**：`docs/demo/demo.mp4`（1080×2400，88 秒，26 MB），
由 `docs/record_demo.py` 自动驱动（tap/swipe/screenrecord），可重复生成；
分镜图 `docs/shots/demo-storyboard.png`。

**过程中发现的真实缺陷**：通过 `ACTION_CHANGE_LIVE_WALLPAPER` 进入系统壁纸预览时，
本机 HyperOS 偶发不为壁纸服务创建 Engine（logcat 无 `onSurfaceCreated`），预览区全黑；
从系统壁纸列表入口进入则正常。已在 README 与 performance.md 记录为已知问题，
录屏脚本做了自动回退。未定位到系统侧原因。

**顺带修正的工具问题**：`docs/parse_ui.py` 只解析已有 xml，容易读到过期坐标，
导致首次录屏的按钮点击全部偏移（导航栏实际 y=2337，不是 2280）。
新增 `docs/ui_find.py`（每次实时 dump）与 `docs/mp4info.py`（无 ffprobe 时读时长）。

回归：`:app:assembleDebug`、`testDebugUnitTest`（106 用例）、`lintDebug` 全部通过。


## 第 13 轮：交付前的敏感信息与冗余清理

提交前对整个仓库做了一遍扫描（路径 / 凭证 / 个人标识），处理结果：

**删除（既有敏感信息又是死代码）**

`docs/analyze.py`、`docs/make_compare.py`、`docs/sample_ref.py`：
三者都是给已删除的 Sunset Ocean 比对参考照片用的，硬编码了本机用户目录下
IDE 扩展缓存里的参考图路径（含本机用户名与会话 UUID），
既泄露本机标识，又指向一个已不存在的参考图。
正文里提到它们的历史描述保留，作为记录。

**删除（与 deploy.py 重复且已过期）**

`docs/deploy_and_check.bat`：早期批处理，点击坐标是旧的（540,346 / 540,1076），
功能已被 `docs/deploy.py`（按 UI 文字匹配 + 重试 + 校验 mWallpaperComponent）完全覆盖。

**去除硬编码本机路径**

新增 `docs/paths.py`：仓库根目录按脚本位置推导，`adb` 走 `ADB` / `ANDROID_HOME`
环境变量；8 个脚本改为引用它（`deploy.py` / `shot_app.py` / `shot_lock.py` /
`shot_preview.py` / `ui_find.py` / `diag_lake.py` / `measure_perf.py` / `record_demo.py`）。
README 的 Windows 构建示例改为占位符，不再写死本机路径。

**顺带修掉的真 bug**

`deploy.py` 结尾仍在往 `docs/shots/64-home-ocean-v8.png` 写图并调用已删除的
`analyze.py`——一旦执行到最后一步就会抛 FileNotFoundError。改为写 `home-latest.png`。

**保留**

- `docs/development-log.md` 里的 `D:\Android\...` 路径：属于环境搭建的历史记录，
  不含用户名等个人信息。
- 三处对已删除脚本的文字引用：作为历史描述保留，不影响复现。

校验：8 个脚本 `py_compile` 全部通过，`diag_lake.py` 实际跑通；
全仓扫描后已无本机用户目录路径、无邮箱、无 UUID、无密钥特征串。


## 第 15 轮：修「设置了其他壁纸后，应用内『设置为壁纸』打不开任何界面」

现象：先在其他位置换成非本应用的壁纸，回到应用点「设置为壁纸」，只弹出
「已打开系统动态壁纸设置界面…」提示，实际没有任何界面打开，壁纸也没变。

**根因（真机日志 + 反编译系统 APK 确认）**：应用发的是 `ACTION_CHANGE_LIVE_WALLPAPER`（直连预览），
它被解析到 `com.android.wallpaper.livepicker/.LiveWallpaperChange`。该 Activity 启动后
**约 30ms 就自己 finish()**，logcat 给出原因：

```
W/CHANGE_LIVE_WALLPAPER(24475): No permission to change wall paper
```

把系统 APK（`/system/priv-app/LiveWallpapersPicker`）拉下来用 `dexdump -d` 看
`LiveWallpaperChange.init()` / `withoutChangePermission()` 的字节码，门槛只有两道：

1. 目标壁纸包是**系统应用** → 直接放行；
2. 否则查 **MIUI 私有 app-op `10045`**：`checkOpNoThrow(10045, uid, pkg) != MODE_ALLOWED`
   就认为「没有权限」，打印上面那行日志并 `finish()`。

而 `dumpsys appops` 显示本应用（以及 QQ、微信）的 `MIUIOP(10045)` 都是 `ignore`——
即 MIUI 对第三方应用默认关闭直连预览。与传参无关（`am start --ecn` 传真 `ComponentName`
同样失败）；直接拉起 `LiveWallpaperPreview` 也不行，它**未导出**
（`SecurityException: not exported from uid 10091`）。
而 `startActivity` 不抛异常，旧代码据此认为「已打开」，提示与事实相反。

实测把该 app-op 改成 `allow` 后，同一个 Intent 就能停在预览页并正常渲染本应用场景，
说明这就是唯一那道门；但这个 op 会被系统重置回 `ignore`（装包/进程重启后失效），
第三方应用无法自己拿到，所以「跳过列表直达预览」在这台机器上做不到。

**中间尝试过、最终放弃的方案**：直连预览优先 + 700ms 内返回判定「被 ROM 秒退」再自动
兜底到列表。实测可行（点按钮 → 直连页 12:59:35.651 启动、.678 被秒退、.702 自动开列表，
全程 51ms），但本机永远走兜底，等于凭空多一层时序判断和一次 `ActivityResult` 依赖，
代码复杂度换不来任何体验收益，按"能用最简单的方式达成同一效果"的原则回退。

**修复**（`PreviewScreen`，最终版）

- 入口固定为**列表直达**：`ACTION_LIVE_WALLPAPER_CHOOSER` → 失败再退
  `Intent.ACTION_SET_WALLPAPER`，全部失败才提示错误。不再尝试直连预览。
- 按钮下方加一行常驻说明，说明"下一步在列表里选哪一项"，避免用户看到
  小米画报/支付宝/QQ 的列表时以为点错了：
  `动态壁纸须由系统界面确认，下一步在列表中选择「%1$s」→ 设置壁纸`，
  名称由 `PackageManager` 读服务 label（`灵境动态壁纸`），不与应用名混淆。

**验证（Xiaomi 23116PN5BC / Android 16）**

- 点「设置为壁纸」→ 系统「动态壁纸」列表立即打开
  （`topResumedActivity=…/.LiveWallpaperActivity`，本应用条目在第一位）。
- 列表 → 「灵境动态壁纸」→ 预览页正常渲染本应用场景 → 「设置壁纸」→「主屏幕」→
  `dumpsys wallpaper` 的 `mWallpaperComponent` 为本应用服务，回桌面壁纸正常显示。
- 截图确认按钮下方的提示文案排版正常。
- `:app:assembleDebug` 通过；`testDebugUnitTest` 106 用例 0 失败；`:app:lintDebug` 0 issue。

### 附：为什么"点一下直接进预览页"在这台机器上做不到（已逐条排除）

| 尝试 | 结果 |
|---|---|
| `ACTION_CHANGE_LIVE_WALLPAPER` 直连预览 | 被 `MIUIOP(10045)` 拦下秒退；手动 `cmd appops set ... 10045 allow` 可通，但**系统会把该 op 重置回 `ignore`**（实测装包/进程重启后失效），不能作为方案 |
| 直接 start `LiveWallpaperPreview` | 未导出，`SecurityException: not exported from uid 10091` |
| 给列表页 `LiveWallpaperActivity` 传 `EXTRA_LIVE_WALLPAPER_COMPONENT` | 仍是列表，不会自动跳到预览（实测 13:09） |
| 应用内自己应用动态壁纸 `WallpaperManager.setWallpaperComponent()` | **公开 SDK 里不存在**（编译期 `Unresolved reference`），且需要 `SET_WALLPAPER_COMPONENT`（signature\|privileged） |

结论：动态壁纸必须由系统壁纸界面确认后才能生效（Android 的安全设计：动态壁纸会在桌面
常驻运行代码）。因此在按钮下方加了一行常驻提示，说明"下一步在列表里选哪个"，
避免用户看到小米画报/支付宝/QQ 的列表时以为点错了：

```
动态壁纸须由系统界面确认，下一步在列表中选择「灵境动态壁纸」→ 设置壁纸
```
