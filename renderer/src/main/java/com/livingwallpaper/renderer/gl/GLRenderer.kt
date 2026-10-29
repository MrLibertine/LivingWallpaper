package com.livingwallpaper.renderer.gl

import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.renderer.performance.RenderStatsHolder
import com.livingwallpaper.renderer.scene.Scene
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 渲染线程控制器。
 *
 * 线程模型：
 * - 由壁纸 Engine 或预览组件在主线程调用 [start] / [stop]，内部创建独立的 GL 线程。
 * - 所有 OpenGL / EGL 调用只发生在 GL 线程内。
 * - 尺寸、可见性等状态通过 volatile 字段传递；[SceneConfig] 通过线程安全队列传递，
 *   在帧开始时于 GL 线程消费，绝不跨线程直接触碰场景对象。
 * - 场景类型发生变化时，在 GL 线程内释放旧场景并重建新场景。
 * - 不可见时 GL 线程在监视器上等待（不空转），恢复时重置时间基准，
 *   避免恢复瞬间产生巨大的 deltaTime。
 * - 绘制尺寸以 EGL 窗口表面的真实尺寸为准并按帧校对，[start] / [resize] 传入的值
 *   只作为提示（系统通知可能滞后或缺失）。
 * - `eglSwapBuffers` 连续失败说明窗口表面已失效，此时重建表面继续绘制，
 *   而不是让渲染循环永久退出（否则壁纸会冻结在最后一帧）。
 */
class GLRenderer(
    private val sceneFactory: (SceneType) -> Scene,
    private val initialConfig: SceneConfig,
    threadName: String = "livingwallpaper-gl"
) {

    private val monitor = Object()
    private val configQueue = ConcurrentLinkedQueue<SceneConfig>()

    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var targetFps = initialConfig.targetFps
    @Volatile private var pendingWidth = 1
    @Volatile private var pendingHeight = 1
    @Volatile private var sizeDirty = true

    private var thread: Thread? = null
    private val name = threadName

    /** 最近一次导致渲染循环终止的错误，供 UI 或日志查询。 */
    @Volatile var lastError: String? = null
        private set

    /** 渲染循环是否正在运行。 */
    @Volatile var isRunning: Boolean = false
        private set

    /** 已渲染帧数（真实计数，用于后续性能统计；不用于伪造 FPS）。 */
    @Volatile var framesRendered: Long = 0
        private set

    /** 提交新配置。可在任意线程调用，真正生效在下一帧的 GL 线程。 */
    fun applyConfig(config: SceneConfig) {
        configQueue.offer(config.clamped())
    }

    /**
     * 启动渲染线程。
     *
     * [width] / [height] 只是起始提示：真正的绘制尺寸在 GL 线程内从 EGL 窗口表面读取，
     * 因为 `SurfaceHolder` 在创建瞬间给出的尺寸可能尚未完成布局。
     */
    fun start(surface: Surface, width: Int, height: Int) {
        if (thread != null) {
            Log.w(TAG, "start() 被重复调用，忽略")
            return
        }
        stopped = false
        paused = false
        lastError = null
        pendingWidth = width.coerceAtLeast(1)
        pendingHeight = height.coerceAtLeast(1)
        sizeDirty = true
        val renderThread = Thread({ loop(surface, initialConfig = initialConfig) }, name)
        thread = renderThread
        renderThread.start()
    }

    /** 通知尺寸变化（系统回调）。仅作为提示，最终以 EGL 窗口表面尺寸为准。 */
    fun resize(width: Int, height: Int) {
        pendingWidth = width.coerceAtLeast(1)
        pendingHeight = height.coerceAtLeast(1)
        sizeDirty = true
    }

    /** 壁纸不可见时暂停持续渲染。 */
    fun setVisible(visible: Boolean) {
        paused = !visible
        if (!paused) {
            synchronized(monitor) { monitor.notifyAll() }
        }
    }

    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceIn(MIN_FPS, MAX_FPS)
    }

    fun stop() {
        stopped = true
        synchronized(monitor) { monitor.notifyAll() }
        val renderThread = thread ?: return
        thread = null
        renderThread.join(JOIN_TIMEOUT_MS)
        if (renderThread.isAlive) {
            Log.w(TAG, "渲染线程未在 ${JOIN_TIMEOUT_MS}ms 内退出，尝试中断")
            renderThread.interrupt()
        }
        isRunning = false
    }

    private fun loop(surface: Surface, initialConfig: SceneConfig) {
        RenderStatsHolder.reset()
        val egl = EGLManager()
        var scene: Scene? = null
        var consecutiveSwapFailures = 0
        var surfaceRebuilds = 0
        var framesSinceRebuild = 0
        try {
            if (!egl.initialize(surface)) {
                lastError = "EGL 初始化失败"
                return
            }
            logGlInfo()

            var currentType = initialConfig.sceneType
            var current = sceneFactory(currentType)
            current.initialize()
            current.applyConfig(initialConfig)
            scene = current
            isRunning = true

            var surfaceWidth = 0
            var surfaceHeight = 0
            var framesSinceSizeCheck = 0
            // 尺寸变化/恢复可见后需要清一次屏，避免旧缓冲区内容残留在画面里
            var needsClear = true

            // 把绘制尺寸对齐到 EGL 窗口表面的真实尺寸。
            //
            // 只依赖 onSurfaceChanged 是不够的：系统在桌面过渡动画、分辨率/显示尺寸切换、
            // Surface 重建等场景下会改变壁纸窗口尺寸，恢复时如果回调缺失或早于真实缓冲区
            // 变化，绘制区域就会永久停在旧尺寸。此时屏幕其余部分是「没有被绘制过」的
            // 缓冲区（黑洞），表现就是壁纸只显示一部分。所以按帧轮询真实尺寸来自愈。
            fun syncSurfaceSize() {
                // 先降脏标记：查询期间若又有 resize() 到达，下一帧会再同步一次
                sizeDirty = false
                val (queriedWidth, queriedHeight) = egl.querySurfaceSize()
                val width = if (queriedWidth > 0) queriedWidth else pendingWidth
                val height = if (queriedHeight > 0) queriedHeight else pendingHeight
                if (width == surfaceWidth && height == surfaceHeight) return
                Log.i(TAG, "绘制尺寸 ${surfaceWidth}x$surfaceHeight -> ${width}x$height")
                surfaceWidth = width
                surfaceHeight = height
                needsClear = true
                current.resize(width, height)
            }

            syncSurfaceSize()

            var previousNanos = System.nanoTime()
            while (!stopped) {
                if (paused) {
                    synchronized(monitor) {
                        while (paused && !stopped) {
                            monitor.wait(PAUSE_WAIT_MS)
                        }
                    }
                    // 恢复后重置时间基准
                    previousNanos = System.nanoTime()
                    // 不可见期间缓冲区内容不可靠（可能被系统回收或改写），恢复后清一帧
                    needsClear = true
                }
                if (stopped) break

                // 消费配置队列：类型变化就重建场景，否则只更新参数
                while (true) {
                    val next = configQueue.poll() ?: break
                    if (next.sceneType != currentType) {
                        current.release()
                        currentType = next.sceneType
                        current = sceneFactory(currentType)
                        current.initialize()
                        current.resize(surfaceWidth, surfaceHeight)
                        scene = current
                    }
                    current.applyConfig(next)
                    targetFps = next.targetFps
                }

                if (sizeDirty || framesSinceSizeCheck >= SIZE_CHECK_INTERVAL_FRAMES) {
                    syncSurfaceSize()
                    framesSinceSizeCheck = 0
                } else {
                    framesSinceSizeCheck++
                }

                val nowNanos = System.nanoTime()
                val deltaSeconds = FrameTiming.deltaSeconds(previousNanos, nowNanos)
                previousNanos = nowNanos
                // 帧间隔包含为限制帧率而做的 sleep，反映用户实际看到的刷新率
                RenderStatsHolder.onFrame(deltaSeconds * 1000f, targetFps)

                // 视口按真实表面尺寸设置：场景内部只负责内容，
                // 这样即使某个场景漏设视口，也不会画到窗口之外/只画一半。
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                if (needsClear) {
                    // glClear 不受视口限制，整块缓冲区都会被覆盖
                    GLES30.glClearColor(0f, 0f, 0f, 1f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    needsClear = false
                }

                current.update(deltaSeconds)
                current.render()

                if (egl.swapBuffers()) {
                    consecutiveSwapFailures = 0
                    framesRendered++
                    framesSinceRebuild++
                    if (framesSinceRebuild >= SURFACE_REBUILD_FORGIVE_FRAMES) {
                        // 稳定渲染一段时间后，恢复重建预算，避免长时间运行被累计耗尽
                        framesSinceRebuild = 0
                        surfaceRebuilds = 0
                    }
                } else {
                    consecutiveSwapFailures++
                    if (consecutiveSwapFailures >= MAX_CONSECUTIVE_SWAP_FAILURES) {
                        if (surfaceRebuilds >= MAX_SURFACE_REBUILDS) {
                            lastError = "eglSwapBuffers 连续失败 $consecutiveSwapFailures 次，停止渲染"
                            break
                        }
                        // 窗口表面失效（尺寸突变 / Surface 被回收 / 不可见期间被系统处理）：
                        // 直接退出会让壁纸冻结在最后一帧，这里重建表面后继续绘制。
                        surfaceRebuilds++
                        framesSinceRebuild = 0
                        consecutiveSwapFailures = 0
                        Log.w(TAG, "窗口表面失效，第 $surfaceRebuilds 次尝试重建")
                        try {
                            Thread.sleep(SURFACE_REBUILD_DELAY_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        if (egl.recreateWindowSurface(surface)) {
                            surfaceWidth = 0
                            surfaceHeight = 0
                            syncSurfaceSize()
                        }
                    }
                }

                val elapsedMillis = (System.nanoTime() - nowNanos) / 1_000_000L
                val sleepMillis = FrameTiming.frameBudgetMillis(targetFps) - elapsedMillis
                if (sleepMillis > 0) {
                    try {
                        Thread.sleep(sleepMillis)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.toString()
            Log.e(TAG, "渲染循环异常终止", t)
        } finally {
            try {
                scene?.release()
            } catch (t: Throwable) {
                Log.e(TAG, "释放场景资源失败", t)
            }
            egl.release()
            isRunning = false
            Log.i(TAG, "渲染循环结束，共渲染 $framesRendered 帧")
        }
    }

    private fun logGlInfo() {
        Log.i(TAG, "GL_VERSION=${GLES30.glGetString(GLES30.GL_VERSION)}")
        Log.i(TAG, "GL_RENDERER=${GLES30.glGetString(GLES30.GL_RENDERER)}")
        Log.i(TAG, "GLSL_VERSION=${GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION)}")
    }

    companion object {
        private const val TAG = "GLRenderer"
        private const val MIN_FPS = 1
        private const val MAX_FPS = 240
        private const val JOIN_TIMEOUT_MS = 2_000L
        private const val PAUSE_WAIT_MS = 200L
        private const val MAX_CONSECUTIVE_SWAP_FAILURES = 3

        /** 每隔多少帧主动查询一次 EGL 窗口表面尺寸（自愈，兜住丢失的 onSurfaceChanged）。 */
        private const val SIZE_CHECK_INTERVAL_FRAMES = 10

        /** 窗口表面重建的最大连续尝试次数，超过则彻底停止（避免无限空转）。 */
        private const val MAX_SURFACE_REBUILDS = 6

        /** 重建窗口表面前的等待时间，给系统完成窗口尺寸/可见性变更。 */
        private const val SURFACE_REBUILD_DELAY_MS = 120L

        /** 稳定渲染多少个成功帧后，重置窗口表面重建预算。 */
        private const val SURFACE_REBUILD_FORGIVE_FRAMES = 120
    }
}
