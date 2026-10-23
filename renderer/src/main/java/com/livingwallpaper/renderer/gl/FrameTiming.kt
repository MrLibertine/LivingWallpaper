package com.livingwallpaper.renderer.gl

/**
 * 帧时间计算工具。全部为纯函数，便于在 JVM 单元测试中验证。
 *
 * 设计要点：
 * - 使用单调时钟（[System.nanoTime]）计算 deltaTime，避免系统时间跳变。
 * - 恢复渲染、长时间卡顿后 deltaTime 可能出现巨大数值，必须夹紧，
 *   否则粒子/动画会一帧跳完。
 */
object FrameTiming {

    /** deltaTime 上限：超过则认为是一次长时间暂停，按 100ms 处理。 */
    const val MAX_DELTA_SECONDS: Float = 0.1f

    private const val NANOS_PER_SECOND = 1_000_000_000.0

    fun deltaSeconds(
        previousNanos: Long,
        currentNanos: Long,
        maxDeltaSeconds: Float = MAX_DELTA_SECONDS
    ): Float {
        val delta = (currentNanos - previousNanos) / NANOS_PER_SECOND
        if (delta <= 0.0) return 0f
        if (delta >= maxDeltaSeconds) return maxDeltaSeconds
        return delta.toFloat()
    }

    /**
     * 目标帧率对应的单帧预算（毫秒）。用于渲染循环主动让出时间，
     * 使 15/30 FPS 档位真实生效（eglSwapBuffers 只限制上限，不会主动降帧）。
     */
    fun frameBudgetMillis(targetFps: Int): Long {
        val fps = targetFps.coerceIn(1, 240)
        return 1000L / fps
    }
}
