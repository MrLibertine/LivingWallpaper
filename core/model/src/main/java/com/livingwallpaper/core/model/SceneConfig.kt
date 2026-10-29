package com.livingwallpaper.core.model

import kotlinx.serialization.Serializable

/**
 * 场景唯一配置源。UI 与壁纸引擎共享同一份不可变快照。
 *
 * 所有外部输入（AI 输出、用户滑块、存储内容）都必须先经 [clamped] 归一化，
 * 再交给渲染引擎，避免非法值进入 GL 层。
 */
@Serializable
data class SceneConfig(
    val version: Int = VERSION,
    val sceneId: String = DEFAULT_SCENE_ID,
    val sceneType: SceneType = SceneType.FOREST,
    val skyColor: String = DEFAULT_SKY_COLOR,
    val weather: WeatherType = WeatherType.RAIN,
    val rainIntensity: Float = DEFAULT_RAIN_INTENSITY,
    val windStrength: Float = DEFAULT_WIND_STRENGTH,
    val lightIntensity: Float = DEFAULT_LIGHT_INTENSITY,
    val animationSpeed: Float = DEFAULT_ANIMATION_SPEED,
    val parallaxStrength: Float = DEFAULT_PARALLAX_STRENGTH,
    val particleDensity: Float = DEFAULT_PARTICLE_DENSITY,
    val targetFps: Int = DEFAULT_TARGET_FPS,
    /** 性能档位：改变粒子数量与动态幅度（P7）。 */
    val performanceLevel: PerformanceLevel = PerformanceLevel.DEFAULT,
    /** 减少动态效果：关闭粒子与摆动，只保留最基础的画面（无障碍/省电场景）。 */
    val reduceMotion: Boolean = false
) {

    /** 把配置约束到合法范围。输入可能是 AI 输出或旧版本数据，不能假设其合法。 */
    fun clamped(): SceneConfig = copy(
        rainIntensity = rainIntensity.inRange(0f, 1f, DEFAULT_RAIN_INTENSITY),
        windStrength = windStrength.inRange(0f, 1f, DEFAULT_WIND_STRENGTH),
        lightIntensity = lightIntensity.inRange(0f, 1f, DEFAULT_LIGHT_INTENSITY),
        animationSpeed = animationSpeed.inRange(
            MIN_ANIMATION_SPEED,
            MAX_ANIMATION_SPEED,
            DEFAULT_ANIMATION_SPEED
        ),
        parallaxStrength = parallaxStrength.inRange(0f, 1f, DEFAULT_PARALLAX_STRENGTH),
        particleDensity = particleDensity.inRange(0f, 1f, DEFAULT_PARTICLE_DENSITY),
        targetFps = nearestSupportedFps(targetFps),
        performanceLevel = performanceLevel,
        reduceMotion = reduceMotion,
        skyColor = if (SkyColor.isValid(skyColor)) skyColor else DEFAULT_SKY_COLOR
    )

    /**
     * 结合性能档位与“减少动态效果”后的最终粒子系数。
     * 渲染层只能使用这个值，避免各处重复判断。
     */
    fun effectiveParticleScale(): Float = if (reduceMotion) {
        0f
    } else {
        performanceLevel.particleScale
    }

    /** 结合性能档位与“减少动态效果”后的最终动态幅度系数。 */
    fun effectiveMotionScale(): Float = if (reduceMotion) {
        0f
    } else {
        performanceLevel.motionScale
    }

    /** 天空色解析失败时回退到深蓝夜空，不让非法颜色进入渲染层。 */
    fun skyRgb(): FloatArray = SkyColor.parse(skyColor) ?: SkyColor.parse(DEFAULT_SKY_COLOR)!!

    companion object {
        const val VERSION = 1
        const val DEFAULT_SCENE_ID = "forest-night"
        const val DEFAULT_SKY_COLOR = "#0B1626"

        const val DEFAULT_RAIN_INTENSITY = 0.5f
        const val DEFAULT_WIND_STRENGTH = 0.4f
        const val DEFAULT_LIGHT_INTENSITY = 0.5f
        const val DEFAULT_ANIMATION_SPEED = 1.0f
        const val DEFAULT_PARALLAX_STRENGTH = 0.5f
        const val DEFAULT_PARTICLE_DENSITY = 0.5f
        const val DEFAULT_TARGET_FPS = 30

        const val MIN_ANIMATION_SPEED = 0.1f
        const val MAX_ANIMATION_SPEED = 2.0f

        /** 支持的帧率档位（计划书 2.1 性能中心）。 */
        val SUPPORTED_FPS: List<Int> = listOf(15, 30, 60)

        val DEFAULT: SceneConfig = SceneConfig()

        /** 把任意帧率吸附到最近的受支持档位。 */
        fun nearestSupportedFps(fps: Int): Int =
            SUPPORTED_FPS.minByOrNull { kotlin.math.abs(it - fps) } ?: DEFAULT_TARGET_FPS

        /**
         * 先判断有限性再夹紧：NaN 与 ±Infinity 必须回退到默认值，
         * 否则 NaN 会通过 coerceIn 的比较（NaN 与任何值比较都为 false）直接透传。
         */
        private fun Float.inRange(min: Float, max: Float, fallback: Float): Float =
            if (isFinite()) coerceIn(min, max) else fallback
    }
}

@Serializable
enum class SceneType { FOREST, AURORA, STAR_VALLEY, CITY_DUSK }

@Serializable
enum class WeatherType { CLEAR, RAIN, CLOUDY }

/** 场景类型对应的默认场景 id，切换场景时需要同步更新。 */
val SceneType.defaultSceneId: String
    get() = when (this) {
        SceneType.FOREST -> "forest-night"
        SceneType.AURORA -> "aurora-lake"
        SceneType.STAR_VALLEY -> "star-valley"
        SceneType.CITY_DUSK -> "city-dusk"
    }
