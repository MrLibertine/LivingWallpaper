package com.livingwallpaper.renderer.particle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainSystemTest {

    private fun step(system: RainSystem, seconds: Float, stepSeconds: Float = 1f / 60f) {
        var remaining = seconds
        while (remaining > 0f) {
            system.update(stepSeconds)
            remaining -= stepSeconds
        }
    }

    @Test
    fun zeroIntensity_producesNoVertices() {
        val system = RainSystem()
        system.intensity = 0f
        system.update(1f / 60f)
        assertEquals(0, system.activeCount)
        assertEquals(0, system.vertexCount)
    }

    @Test
    fun intensityScalesActiveCount() {
        val system = RainSystem(capacity = 100)
        system.intensity = 0.5f
        assertEquals(50, system.activeCount)
        system.intensity = 1f
        assertEquals(100, system.activeCount)
        system.intensity = 2f
        assertEquals(100, system.activeCount)
    }

    @Test
    fun vertexCountMatchesTwoVerticesPerDrop() {
        val system = RainSystem(capacity = 64)
        system.intensity = 1f
        system.update(1f / 60f)
        assertEquals(system.activeCount * 2, system.vertexCount)
    }

    @Test
    fun dropsStayInsideBoundsOverLongRun() {
        val system = RainSystem(capacity = 300, seed = 42L)
        system.intensity = 1f
        system.wind = 1f
        step(system, 30f)
        for (i in 0 until system.vertexCount) {
            val x = system.vertices[i * 3]
            val y = system.vertices[i * 3 + 1]
            assertTrue("x 越界: $x", x in -1.3f..1.3f)
            assertTrue("y 越界: $y", y in -1.4f..1.45f)
        }
    }

    @Test
    fun dropsFallDownward() {
        val system = RainSystem(capacity = 16, seed = 7L)
        system.intensity = 1f
        system.wind = 0f
        // 先用 dt=0 生成一次顶点快照，此时没有任何位移
        system.update(0f)
        val before = FloatArray(system.activeCount)
        for (i in 0 until system.activeCount) {
            before[i] = system.vertices[i * 6 + 1]
        }

        system.update(0.05f)
        var movedDown = 0
        for (i in 0 until system.activeCount) {
            if (system.vertices[i * 6 + 1] < before[i]) movedDown++
        }
        // 个别雨滴可能刚好在这一帧回绕到顶部，因此只要求“大多数”向下
        assertTrue(
            "多数雨滴应向下移动，实际 $movedDown/${system.activeCount}",
            movedDown * 2 >= system.activeCount
        )
    }

    @Test
    fun tailAlphaIsZeroToFormStreak() {
        val system = RainSystem(capacity = 8)
        system.intensity = 1f
        system.update(1f / 60f)
        assertEquals(0f, system.vertices[5], 0.0001f)
        assertTrue(system.vertices[2] > 0f)
    }

    @Test
    fun invalidDeltaIsIgnored() {
        val system = RainSystem(capacity = 8)
        system.intensity = 1f
        system.update(Float.NaN)
        assertTrue(system.vertexCount > 0)
        val y = system.vertices[1]
        assertTrue("NaN delta 不应产生 NaN 坐标", y.isFinite())
    }
}

class FireflySystemTest {

    @Test
    fun zeroDensity_producesNoVertices() {
        val system = FireflySystem()
        system.density = 0f
        system.update(1f / 60f)
        assertEquals(0, system.activeCount)
        assertEquals(0, system.vertexCount)
    }

    @Test
    fun densityScalesActiveCount() {
        val system = FireflySystem(capacity = 100)
        system.density = 0.25f
        assertEquals(25, system.activeCount)
    }

    @Test
    fun firefliesStayInsideBoundsOverLongRun() {
        val system = FireflySystem(capacity = 120, seed = 99L)
        system.density = 1f
        system.wind = 1f
        var remaining = 60f
        while (remaining > 0f) {
            system.update(1f / 60f)
            remaining -= 1f / 60f
        }
        for (i in 0 until system.vertexCount) {
            val base = i * 4
            val x = system.vertices[base]
            val y = system.vertices[base + 1]
            assertTrue("x 越界: $x", x in -1.1f..1.1f)
            assertTrue("y 越界: $y", y in -0.6f..0.6f)
        }
    }

    @Test
    fun particlesRespawnAfterLifetime() {
        val system = FireflySystem(capacity = 10, seed = 5L)
        system.density = 1f
        // 生命周期最长 16 秒，跑 40 秒必然触发重新生成
        var remaining = 40f
        while (remaining > 0f) {
            system.update(1f / 30f)
            remaining -= 1f / 30f
        }
        assertEquals(10, system.vertexCount)
        for (i in 0 until system.vertexCount) {
            assertTrue(system.vertices[i * 4].isFinite())
            assertTrue(system.vertices[i * 4 + 1].isFinite())
        }
    }

    @Test
    fun elapsedTimeAccumulates() {
        val system = FireflySystem()
        system.density = 0.5f
        system.update(0.25f)
        assertTrue(system.elapsedSeconds > 0f)
    }
}
