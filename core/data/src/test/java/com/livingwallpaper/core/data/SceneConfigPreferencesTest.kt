package com.livingwallpaper.core.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneConfigPreferencesTest {

    private fun roundTrip(config: SceneConfig): SceneConfig {
        val mutable = mutablePreferencesOf()
        SceneConfigPreferences.write(mutable, config)
        return SceneConfigPreferences.read(mutable as Preferences)
    }

    @Test
    fun emptyPreferencesFallsBackToDefaults() {
        val config = SceneConfigPreferences.read(emptyPreferences())
        assertEquals(SceneConfig.DEFAULT.clamped(), config)
    }

    @Test
    fun roundTripPreservesValues() {
        val config = SceneConfig(
            sceneId = "star-valley",
            sceneType = SceneType.STAR_VALLEY,
            skyColor = "#123456",
            weather = WeatherType.CLOUDY,
            rainIntensity = 0.25f,
            windStrength = 0.75f,
            lightIntensity = 0.4f,
            animationSpeed = 1.5f,
            parallaxStrength = 0.1f,
            particleDensity = 0.9f,
            targetFps = 60
        ).clamped()

        assertEquals(config, roundTrip(config))
    }

    @Test
    fun writeStoresClampedValues() {
        val config = SceneConfig(rainIntensity = 5f, targetFps = 45).clamped()
        val result = roundTrip(config)
        assertEquals(1f, result.rainIntensity, 0.0001f)
        assertEquals(30, result.targetFps)
    }

    @Test
    fun unknownEnumNamesFallBackToDefaults() {
        val preferences = preferencesOf(
            stringPreferencesKey("scene_type") to "NOT_A_SCENE",
            stringPreferencesKey("weather") to "NOT_A_WEATHER"
        )
        val config = SceneConfigPreferences.read(preferences)
        assertEquals(SceneType.FOREST, config.sceneType)
        assertEquals(WeatherType.RAIN, config.weather)
    }

    @Test
    fun invalidSkyColorIsSanitizedOnRead() {
        val preferences = preferencesOf(stringPreferencesKey("sky_color") to "oops")
        val config = SceneConfigPreferences.read(preferences)
        assertEquals(SceneConfig.DEFAULT_SKY_COLOR, config.skyColor)
    }
}
