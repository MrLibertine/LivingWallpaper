package com.livingwallpaper.renderer.animation

import kotlin.math.sin

/**
 * 风力模型：由 windStrength 驱动，叠加低频阵风，供树木摆动使用。
 * 纯 CPU 计算，可单元测试。
 */
class Wind {

    /** 风力强度 0..1。 */
    var strength: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    private var phase = 0f

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.5f) else 0f
        phase += dt * (BASE_FREQUENCY + strength * GUST_FREQUENCY)
        if (phase > TWO_PI) phase -= TWO_PI
    }

    /** 当前阵风系数 0..1。 */
    val gust: Float
        get() = 0.5f + 0.5f * sin(phase)

    /** 树冠摆动幅度（NDC 单位）。风力为 0 时返回 0，画面完全静止。 */
    val swayAmplitude: Float
        get() = strength * (BASE_SWAY + GUST_SWAY * gust)

    fun reset() {
        phase = 0f
    }

    private companion object {
        const val BASE_FREQUENCY = 0.35f
        const val GUST_FREQUENCY = 0.9f
        const val BASE_SWAY = 0.006f
        const val GUST_SWAY = 0.010f
        const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
