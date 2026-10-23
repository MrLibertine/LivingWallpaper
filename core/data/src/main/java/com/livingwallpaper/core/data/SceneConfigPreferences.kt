package com.livingwallpaper.core.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.livingwallpaper.core.model.PerformanceLevel
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType

/**
 * [SceneConfig] 与 DataStore Preferences 之间的映射。
 *
 * 纯函数、无 Android 上下文依赖，因此可以单元测试。
 * 读取时对枚举做容错：未知名称回退到默认值，绝不让非法值进入渲染层。
 */
internal object SceneConfigPreferences {

    private val KEY_VERSION = intPreferencesKey("version")
    private val KEY_SCENE_ID = stringPreferencesKey("scene_id")
    private val KEY_SCENE_TYPE = stringPreferencesKey("scene_type")
    private val KEY_SKY_COLOR = stringPreferencesKey("sky_color")
    private val KEY_WEATHER = stringPreferencesKey("weather")
    private val KEY_RAIN = floatPreferencesKey("rain_intensity")
    private val KEY_WIND = floatPreferencesKey("wind_strength")
    private val KEY_LIGHT = floatPreferencesKey("light_intensity")
    private val KEY_ANIM_SPEED = floatPreferencesKey("animation_speed")
    private val KEY_PARALLAX = floatPreferencesKey("parallax_strength")
    private val KEY_PARTICLE = floatPreferencesKey("particle_density")
    private val KEY_FPS = intPreferencesKey("target_fps")
    private val KEY_PERFORMANCE_LEVEL = stringPreferencesKey("performance_level")
    private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")

    fun read(preferences: Preferences): SceneConfig {
        val default = SceneConfig.DEFAULT
        return SceneConfig(
            version = preferences[KEY_VERSION] ?: default.version,
            sceneId = preferences[KEY_SCENE_ID] ?: default.sceneId,
            sceneType = parseEnum<SceneType>(preferences[KEY_SCENE_TYPE]) ?: default.sceneType,
            skyColor = preferences[KEY_SKY_COLOR] ?: default.skyColor,
            weather = parseEnum<WeatherType>(preferences[KEY_WEATHER]) ?: default.weather,
            rainIntensity = preferences[KEY_RAIN] ?: default.rainIntensity,
            windStrength = preferences[KEY_WIND] ?: default.windStrength,
            lightIntensity = preferences[KEY_LIGHT] ?: default.lightIntensity,
            animationSpeed = preferences[KEY_ANIM_SPEED] ?: default.animationSpeed,
            parallaxStrength = preferences[KEY_PARALLAX] ?: default.parallaxStrength,
            particleDensity = preferences[KEY_PARTICLE] ?: default.particleDensity,
            targetFps = preferences[KEY_FPS] ?: default.targetFps,
            performanceLevel = PerformanceLevel.fromName(preferences[KEY_PERFORMANCE_LEVEL]),
            reduceMotion = preferences[KEY_REDUCE_MOTION] ?: default.reduceMotion
        ).clamped()
    }

    fun write(preferences: MutablePreferences, config: SceneConfig) {
        val safe = config.clamped()
        preferences[KEY_VERSION] = safe.version
        preferences[KEY_SCENE_ID] = safe.sceneId
        preferences[KEY_SCENE_TYPE] = safe.sceneType.name
        preferences[KEY_SKY_COLOR] = safe.skyColor
        preferences[KEY_WEATHER] = safe.weather.name
        preferences[KEY_RAIN] = safe.rainIntensity
        preferences[KEY_WIND] = safe.windStrength
        preferences[KEY_LIGHT] = safe.lightIntensity
        preferences[KEY_ANIM_SPEED] = safe.animationSpeed
        preferences[KEY_PARALLAX] = safe.parallaxStrength
        preferences[KEY_PARTICLE] = safe.particleDensity
        preferences[KEY_FPS] = safe.targetFps
        preferences[KEY_PERFORMANCE_LEVEL] = safe.performanceLevel.name
        preferences[KEY_REDUCE_MOTION] = safe.reduceMotion
    }

    private inline fun <reified T : Enum<T>> parseEnum(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
