package com.livingwallpaper.core.model

import kotlinx.serialization.Serializable

/**
 * 性能档位。切换档位会真实改变粒子数量与动画幅度，
 * 而不是只改一个名字。
 *
 * @property particleScale 粒子数量的缩放系数，作用于雨滴/萤火虫/流星的密度与强度。
 * @property motionScale 动态效果（风吹摆动、波浪推进）的缩放系数。
 * @property suggestedFps 该档位建议的帧率，仅作为默认值，用户仍可单独设置帧率。
 */
@Serializable
enum class PerformanceLevel(
    val particleScale: Float,
    val motionScale: Float,
    val suggestedFps: Int
) {
    /** 省电：粒子最少、摆动最小。 */
    LOW(particleScale = 0.35f, motionScale = 0.4f, suggestedFps = 15),

    /** 均衡：默认档位。 */
    MEDIUM(particleScale = 0.7f, motionScale = 0.8f, suggestedFps = 30),

    /** 高画质：完整粒子与动画。 */
    HIGH(particleScale = 1.0f, motionScale = 1.0f, suggestedFps = 60);

    companion object {
        val DEFAULT: PerformanceLevel = MEDIUM

        fun fromName(name: String?): PerformanceLevel =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEFAULT
    }
}
