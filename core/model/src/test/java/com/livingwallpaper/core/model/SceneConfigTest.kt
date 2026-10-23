package com.livingwallpaper.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneConfigTest {

    @Test
    fun clamped_constrainsZeroToOneRange() {
        val config = SceneConfig(
            rainIntensity = 3f,
            windStrength = -2f,
            lightIntensity = 0.4f,
            parallaxStrength = 1.5f,
            particleDensity = -0.2f
        ).clamped()

        assertEquals(1f, config.rainIntensity, 0.0001f)
        assertEquals(0f, config.windStrength, 0.0001f)
        assertEquals(1f, config.parallaxStrength, 0.0001f)
        assertEquals(0f, config.particleDensity, 0.0001f)
        assertEquals(0.4f, config.lightIntensity, 0.0001f)
    }

    @Test
    fun clamped_constrainsAnimationSpeed() {
        assertEquals(0.1f, SceneConfig(animationSpeed = 0f).clamped().animationSpeed, 0.0001f)
        assertEquals(2.0f, SceneConfig(animationSpeed = 99f).clamped().animationSpeed, 0.0001f)
        assertEquals(1.0f, SceneConfig(animationSpeed = 1.0f).clamped().animationSpeed, 0.0001f)
    }

    @Test
    fun clamped_snapsFpsToSupportedValues() {
        assertEquals(15, SceneConfig(targetFps = 10).clamped().targetFps)
        assertEquals(30, SceneConfig(targetFps = 24).clamped().targetFps)
        assertEquals(30, SceneConfig(targetFps = 31).clamped().targetFps)
        assertEquals(60, SceneConfig(targetFps = 120).clamped().targetFps)
    }

    @Test
    fun clamped_replacesInvalidSkyColor() {
        val invalid = SceneConfig(skyColor = "not-a-color").clamped()
        assertEquals(SceneConfig.DEFAULT_SKY_COLOR, invalid.skyColor)

        val valid = SceneConfig(skyColor = "#3A2B4C").clamped()
        assertEquals("#3A2B4C", valid.skyColor)
    }

    @Test
    fun clamped_replacesNonFiniteValues() {
        val config = SceneConfig(
            rainIntensity = Float.NaN,
            windStrength = Float.POSITIVE_INFINITY
        ).clamped()
        assertEquals(SceneConfig.DEFAULT_RAIN_INTENSITY, config.rainIntensity, 0.0001f)
        assertEquals(SceneConfig.DEFAULT_WIND_STRENGTH, config.windStrength, 0.0001f)
    }

    @Test
    fun clamped_isIdempotent() {
        val once = SceneConfig(rainIntensity = 5f, targetFps = 45).clamped()
        assertEquals(once, once.clamped())
    }

    @Test
    fun effectiveParticleScaleFollowsPerformanceLevel() {
        val low = SceneConfig(performanceLevel = PerformanceLevel.LOW).clamped()
        val high = SceneConfig(performanceLevel = PerformanceLevel.HIGH).clamped()
        assertEquals(PerformanceLevel.LOW.particleScale, low.effectiveParticleScale(), 0.0001f)
        assertEquals(1.0f, high.effectiveParticleScale(), 0.0001f)
    }

    @Test
    fun reduceMotionDisablesParticlesAndMotion() {
        val config = SceneConfig(
            rainIntensity = 1f,
            particleDensity = 1f,
            performanceLevel = PerformanceLevel.HIGH,
            reduceMotion = true
        ).clamped()
        assertEquals(0f, config.effectiveParticleScale(), 0.0001f)
        assertEquals(0f, config.effectiveMotionScale(), 0.0001f)
    }

    @Test
    fun clamped_preservesPerformanceFields() {
        val config = SceneConfig(
            performanceLevel = PerformanceLevel.LOW,
            reduceMotion = true
        ).clamped()
        assertEquals(PerformanceLevel.LOW, config.performanceLevel)
        assertTrue(config.reduceMotion)
    }

    @Test
    fun skyRgb_fallsBackToDefaultOnInvalidColor() {
        val rgb = SceneConfig(skyColor = "zzz").skyRgb()
        assertEquals(3, rgb.size)
        assertTrue(rgb.all { it in 0f..1f })
    }
}

class SkyColorTest {

    @Test
    fun isValid_acceptsSixDigitHex() {
        assertTrue(SkyColor.isValid("#0B1626"))
        assertTrue(SkyColor.isValid("#abcdef"))
    }

    @Test
    fun isValid_rejectsMalformedInput() {
        assertFalse(SkyColor.isValid("0B1626"))
        assertFalse(SkyColor.isValid("#0B162"))
        assertFalse(SkyColor.isValid("#GGGGGG"))
        assertFalse(SkyColor.isValid(""))
    }

    @Test
    fun parse_convertsHexToUnitFloat() {
        val rgb = SkyColor.parse("#FF0000")!!
        assertEquals(1f, rgb[0], 0.0001f)
        assertEquals(0f, rgb[1], 0.0001f)
        assertEquals(0f, rgb[2], 0.0001f)
    }

    @Test
    fun parse_returnsNullOnInvalidInput() {
        assertEquals(null, SkyColor.parse("#12345"))
    }

    @Test
    fun format_roundTrips() {
        val hex = "#0B1626"
        assertEquals(hex, SkyColor.format(SkyColor.parse(hex)!!))
    }

    @Test
    fun scale_clampsToUnitRange() {
        val scaled = SkyColor.scale(floatArrayOf(0.5f, 0.5f, 0.5f), 3f)
        assertTrue(scaled.all { it <= 1f })
    }
}
