package com.livingwallpaper.renderer.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * EGL 上下文与窗口 Surface 的最小封装。
 *
 * 约束：
 * - 所有方法必须在拥有该上下文的渲染线程调用（EGL 不是线程安全的）。
 * - [release] 必须被调用，否则会泄漏 EGL 资源。
 */
class EGLManager {

    companion object {
        private const val TAG = "EGLManager"

        // EGL 常量：显式定义，避免依赖不同 API Level 上可能缺失的静态字段。
        private const val EGL_OPENGL_ES2_BIT = 0x0004
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        private const val EGL_WIDTH = 0x3057
        private const val EGL_HEIGHT = 0x3056
    }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null

    val isInitialized: Boolean
        get() = display != EGL14.EGL_NO_DISPLAY &&
            context != EGL14.EGL_NO_CONTEXT &&
            windowSurface != EGL14.EGL_NO_SURFACE

    /**
     * 创建 Display / Context（OpenGL ES 3.0）/ WindowSurface，并 makeCurrent。
     * 任一步失败都会清理已创建的部分资源并返回 false，不留半初始化状态。
     */
    fun initialize(surface: Surface): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            logFailure("eglGetDisplay")
            release()
            return false
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            logFailure("eglInitialize")
            release()
            return false
        }

        val config = chooseConfig() ?: run {
            logFailure("eglChooseConfig")
            release()
            return false
        }
        this.config = config

        val contextAttribs = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            logFailure("eglCreateContext")
            release()
            return false
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        windowSurface = EGL14.eglCreateWindowSurface(display, config, surface, surfaceAttribs, 0)
        if (windowSurface == EGL14.EGL_NO_SURFACE) {
            logFailure("eglCreateWindowSurface")
            release()
            return false
        }

        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            logFailure("eglMakeCurrent")
            release()
            return false
        }

        Log.i(TAG, "EGL initialized: ${querySurfaceSize()}")
        return true
    }

    /**
     * 重建窗口表面（保留 Display 与 Context）。
     *
     * 窗口表面会随壁纸窗口尺寸/生命周期变化而失效（`eglSwapBuffers` 返回 false），
     * 此时不能直接结束渲染线程，否则壁纸会冻结在最后一帧（视觉上表现为画面停住、
     * 甚至只剩半屏）。这里销毁旧的窗口表面，用同一个 Context 重新绑定。
     */
    fun recreateWindowSurface(surface: Surface): Boolean {
        val currentConfig = config
        if (display == EGL14.EGL_NO_DISPLAY || context == EGL14.EGL_NO_CONTEXT || currentConfig == null) {
            return false
        }
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        if (windowSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, windowSurface)
            windowSurface = EGL14.EGL_NO_SURFACE
        }
        windowSurface = EGL14.eglCreateWindowSurface(
            display,
            currentConfig,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        if (windowSurface == EGL14.EGL_NO_SURFACE) {
            logFailure("eglCreateWindowSurface(recreate)")
            return false
        }
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            logFailure("eglMakeCurrent(recreate)")
            EGL14.eglDestroySurface(display, windowSurface)
            windowSurface = EGL14.EGL_NO_SURFACE
            return false
        }
        Log.i(TAG, "EGL window surface 已重建: ${querySurfaceSize()}")
        return true
    }

    private fun chooseConfig(): EGLConfig? {
        // 优先请求 ES3 可渲染配置，失败再退回 ES2 位（多数驱动仍允许创建 ES3 上下文）。
        val renderableTypes = intArrayOf(EGL_OPENGL_ES3_BIT, EGL_OPENGL_ES2_BIT)
        for (renderableType in renderableTypes) {
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, configCount, 0)
            if (ok && configCount[0] > 0) {
                return configs[0]
            }
        }
        return null
    }

    /** 交换缓冲，把当前帧提交给 SurfaceFlinger。 */
    fun swapBuffers(): Boolean {
        if (!isInitialized) return false
        val ok = EGL14.eglSwapBuffers(display, windowSurface)
        if (!ok) {
            Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
        return ok
    }

    /** 查询窗口 Surface 当前尺寸；Surface 尺寸变化时由渲染循环据此更新视口。 */
    fun querySurfaceSize(): Pair<Int, Int> {
        if (!isInitialized) return 0 to 0
        val value = IntArray(1)
        var width = 0
        var height = 0
        if (EGL14.eglQuerySurface(display, windowSurface, EGL_WIDTH, value, 0)) width = value[0]
        if (EGL14.eglQuerySurface(display, windowSurface, EGL_HEIGHT, value, 0)) height = value[0]
        return width to height
    }

    /** 释放全部 EGL 资源。可重复调用。 */
    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (windowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, windowSurface)
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        windowSurface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        config = null
        display = EGL14.EGL_NO_DISPLAY
    }

    private fun logFailure(step: String) {
        Log.e(TAG, "$step failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
    }
}
