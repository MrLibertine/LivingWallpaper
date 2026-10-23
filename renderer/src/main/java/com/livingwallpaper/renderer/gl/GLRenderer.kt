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
        try {
            if (!egl.initialize(surface)) {
                lastError = "EGL 初始化失败"
                return
            }
            logGlInfo()

            var currentType = initialConfig.sceneType
            var current = sceneFactory(currentType)
            current.initialize()
            current.resize(pendingWidth, pendingHeight)
            current.applyConfig(initialConfig)
            sizeDirty = false
            scene = current
            isRunning = true

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
                        current.resize(pendingWidth, pendingHeight)
                        scene = current
                        sizeDirty = false
                    }
                    current.applyConfig(next)
                    targetFps = next.targetFps
                }

                if (sizeDirty) {
                    val (w, h) = egl.querySurfaceSize()
                    current.resize(
                        if (w > 0) w else pendingWidth,
                        if (h > 0) h else pendingHeight
                    )
                    sizeDirty = false
                }

                val nowNanos = System.nanoTime()
                val deltaSeconds = FrameTiming.deltaSeconds(previousNanos, nowNanos)
                previousNanos = nowNanos
                // 帧间隔包含为限制帧率而做的 sleep，反映用户实际看到的刷新率
                RenderStatsHolder.onFrame(deltaSeconds * 1000f, targetFps)

                current.update(deltaSeconds)
                current.render()

                if (egl.swapBuffers()) {
                    consecutiveSwapFailures = 0
                    framesRendered++
                } else {
                    consecutiveSwapFailures++
                    if (consecutiveSwapFailures >= MAX_CONSECUTIVE_SWAP_FAILURES) {
                        lastError = "eglSwapBuffers 连续失败 $consecutiveSwapFailures 次，停止渲染"
                        break
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
    }
}
