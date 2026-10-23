package com.livingwallpaper.renderer.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindTest {

    @Test
    fun zeroStrengthProducesNoSway() {
        val wind = Wind()
        wind.strength = 0f
        wind.update(1f)
        assertEquals(0f, wind.swayAmplitude, 0.0001f)
    }

    @Test
    fun strengthIsClamped() {
        val wind = Wind()
        wind.strength = 5f
        assertEquals(1f, wind.strength, 0.0001f)
        wind.strength = -1f
        assertEquals(0f, wind.strength, 0.0001f)
    }

    @Test
    fun gustStaysInUnitRange() {
        val wind = Wind()
        wind.strength = 1f
        for (i in 0 until 200) {
            wind.update(1f / 60f)
            assertTrue(wind.gust in 0f..1f)
            assertTrue(wind.swayAmplitude > 0f)
        }
    }

    @Test
    fun nanStrengthFallsBackToZero() {
        val wind = Wind()
        wind.strength = Float.NaN
        assertEquals(0f, wind.strength, 0.0001f)
    }
}

class ParallaxControllerTest {

    @Test
    fun disabledOutputsZeroOffset() {
        val controller = ParallaxController()
        controller.enabled = false
        controller.strength = 1f
        controller.setTilt(1f, 1f)
        for (i in 0 until 100) controller.update(1f / 60f)
        assertEquals(0f, controller.offsetX, 0.0001f)
        assertEquals(0f, controller.offsetY, 0.0001f)
    }

    @Test
    fun offsetConvergesToScaledTilt() {
        val controller = ParallaxController()
        controller.strength = 0.5f
        controller.setTilt(1f, -0.5f)
        for (i in 0 until 600) controller.update(1f / 60f)
        assertEquals(0.5f, controller.offsetX, 0.01f)
        assertEquals(-0.25f, controller.offsetY, 0.01f)
    }

    @Test
    fun tiltIsClamped() {
        val controller = ParallaxController()
        controller.strength = 1f
        controller.setTilt(10f, -10f)
        for (i in 0 until 600) controller.update(1f / 60f)
        assertEquals(1f, controller.offsetX, 0.01f)
        assertEquals(-1f, controller.offsetY, 0.01f)
    }

    @Test
    fun nanTiltIsTreatedAsZero() {
        val controller = ParallaxController()
        controller.strength = 1f
        controller.setTilt(Float.NaN, Float.NaN)
        for (i in 0 until 100) controller.update(1f / 60f)
        assertEquals(0f, controller.offsetX, 0.0001f)
        assertEquals(0f, controller.offsetY, 0.0001f)
    }

    @Test
    fun disablingSmoothlyReturnsToZero() {
        val controller = ParallaxController()
        controller.strength = 1f
        controller.setTilt(1f, 0f)
        for (i in 0 until 300) controller.update(1f / 60f)
        assertTrue(controller.offsetX > 0.5f)

        controller.enabled = false
        for (i in 0 until 300) controller.update(1f / 60f)
        assertEquals(0f, controller.offsetX, 0.001f)
    }

    @Test
    fun resetClearsState() {
        val controller = ParallaxController()
        controller.setTilt(1f, 1f)
        for (i in 0 until 300) controller.update(1f / 60f)
        controller.reset()
        assertEquals(0f, controller.offsetX, 0.0001f)
        assertEquals(0f, controller.offsetY, 0.0001f)
    }
}
