package com.livingwallpaper.ai.parser

import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType
import com.livingwallpaper.core.model.defaultSceneId

/** 可被自然语言调整的参数。 */
enum class ParamKey(val min: Float, val max: Float) {
    RAIN(0f, 1f),
    WIND(0f, 1f),
    LIGHT(0f, 1f),
    ANIMATION_SPEED(SceneConfig.MIN_ANIMATION_SPEED, SceneConfig.MAX_ANIMATION_SPEED),
    PARALLAX(0f, 1f),
    PARTICLE(0f, 1f)
}

/**
 * 指令解析出的结构化改动。只包含“要改什么”，不包含渲染逻辑。
 *
 * 与直接返回 SceneConfig 相比，中间层带来的好处：
 * - 可以单元测试（给定输入断言 absolute / relative 的具体值）；
 * - 可以在 UI 上解释命中了哪些规则；
 * - 应用时统一走 [apply] + [SceneConfig.clamped]，非法值不可能进入渲染层。
 */
data class InstructionPatch(
    val sceneType: SceneType? = null,
    val weather: WeatherType? = null,
    val skyColor: String? = null,
    val targetFps: Int? = null,
    /** 绝对值设置，例如“视差关掉” → PARALLAX = 0。 */
    val absolute: Map<ParamKey, Float> = emptyMap(),
    /** 相对调整，例如“雨大一点” → RAIN += 0.2。 */
    val relative: Map<ParamKey, Float> = emptyMap()
) {

    fun isEmpty(): Boolean =
        sceneType == null && weather == null && skyColor == null && targetFps == null &&
            absolute.isEmpty() && relative.isEmpty()

    fun apply(config: SceneConfig): SceneConfig {
        var result = config
        sceneType?.let {
            result = result.copy(sceneType = it, sceneId = it.defaultSceneId)
        }
        weather?.let { result = result.copy(weather = it) }
        skyColor?.let { result = result.copy(skyColor = it) }
        targetFps?.let { result = result.copy(targetFps = it) }

        for ((key, value) in absolute) {
            result = write(result, key, value.coerceIn(key.min, key.max))
        }
        for ((key, delta) in relative) {
            val current = read(result, key)
            result = write(result, key, (current + delta).coerceIn(key.min, key.max))
        }
        return result.clamped()
    }

    private fun read(config: SceneConfig, key: ParamKey): Float = when (key) {
        ParamKey.RAIN -> config.rainIntensity
        ParamKey.WIND -> config.windStrength
        ParamKey.LIGHT -> config.lightIntensity
        ParamKey.ANIMATION_SPEED -> config.animationSpeed
        ParamKey.PARALLAX -> config.parallaxStrength
        ParamKey.PARTICLE -> config.particleDensity
    }

    private fun write(config: SceneConfig, key: ParamKey, value: Float): SceneConfig = when (key) {
        ParamKey.RAIN -> config.copy(rainIntensity = value)
        ParamKey.WIND -> config.copy(windStrength = value)
        ParamKey.LIGHT -> config.copy(lightIntensity = value)
        ParamKey.ANIMATION_SPEED -> config.copy(animationSpeed = value)
        ParamKey.PARALLAX -> config.copy(parallaxStrength = value)
        ParamKey.PARTICLE -> config.copy(particleDensity = value)
    }
}
