package com.livingwallpaper.renderer.animation

import kotlin.math.exp

/**
 * 视差控制器：把传感器姿态数据低通滤波后转换为分层偏移。
 *
 * - 关闭视差时输出恒为 0（计划书 2.3：必须提供关闭视差选项）；
 * - 输出已按 [strength] 缩放，渲染层再乘以各图层自身的深度系数。
 */
class ParallaxController {

    /** 是否启用视差。关闭后偏移会在若干帧内平滑归零。 */
    @Volatile
    var enabled: Boolean = true

    /** 视差强度 0..1。 */
    @Volatile
    var strength: Float = 0.5f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    private var targetX = 0f
    private var targetY = 0f
    private var currentX = 0f
    private var currentY = 0f

    /** 输入归一化倾角，范围期望 -1..1，超出会被夹紧，NaN 视为 0。 */
    fun setTilt(tiltX: Float, tiltY: Float) {
        targetX = sanitize(tiltX)
        targetY = sanitize(tiltY)
    }

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.5f) else 0f
        val scale = if (enabled) strength else 0f
        // 一阶低通，避免传感器噪声导致画面抖动
        val alpha = 1f - exp(-dt / SMOOTHING_SECONDS)
        currentX += (targetX * scale - currentX) * alpha
        currentY += (targetY * scale - currentY) * alpha
    }

    /** 水平视差偏移，已包含强度缩放。 */
    val offsetX: Float get() = currentX

    /** 垂直视差偏移，已包含强度缩放。 */
    val offsetY: Float get() = currentY

    fun reset() {
        targetX = 0f
        targetY = 0f
        currentX = 0f
        currentY = 0f
    }

    private fun sanitize(value: Float): Float {
        if (!value.isFinite()) return 0f
        return value.coerceIn(-1f, 1f)
    }

    private companion object {
        const val SMOOTHING_SECONDS = 0.18f
    }
}
