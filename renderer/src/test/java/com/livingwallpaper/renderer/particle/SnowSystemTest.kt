package com.livingwallpaper.renderer.particle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnowSystemTest {

    private fun run(system: SnowSystem, seconds: Float, stepSeconds: Float = 1f / 60f) {
        var remaining = seconds
        while (remaining > 0f) {
            system.update(stepSeconds)
            remaining -= stepSeconds
        }
    }

    @Test
    fun zeroIntensityProducesNoVertices() {
        val system = SnowSystem(seed = 1L)
        system.intensity = 0f
        run(system, 30f)
        assertEquals(0, system.activeCount)
        assertEquals(0, system.vertexCount)
    }

    @Test
    fun fullIntensityUsesWholePool() {
        val system = SnowSystem(capacity = 100, seed = 2L)
        system.intensity = 1f
        assertEquals(100, system.activeCount)
        run(system, 5f)
        assertEquals(200, system.vertexCount)
    }

    @Test
    fun intensityIsClampedToUnitRange() {
        val system = SnowSystem(capacity = 100, seed = 3L)
        system.intensity = 2f
        assertEquals(100, system.activeCount)
        system.intensity = -1f
        assertEquals(0, system.activeCount)
        system.intensity = Float.NaN
        assertEquals(0, system.activeCount)
        system.wind = Float.NaN
        assertEquals(0f, system.wind)
    }

    @Test
    fun snowFallsDownwardAndStaysInBounds() {
        val system = SnowSystem(seed = 4L)
        system.intensity = 1f
        system.wind = 1f
        run(system, 2f)
        val before = FloatArray(system.activeCount)
        for (i in 0 until system.activeCount) {
            before[i] = system.vertices[i * 6 + 1]
        }
        run(system, 2f)
        var fell = 0
        for (i in 0 until system.activeCount) {
            val y = system.vertices[i * 6 + 1]
            val x = system.vertices[i * 6]
            assertTrue("x 越界: $x", x in -1.6f..1.6f)
            assertTrue("y 越界: $y", y in -1.6f..1.6f)
            if (y < before[i]) fell++
        }
        assertTrue("雪花应整体向下飘落，实际下降数=$fell", fell > system.activeCount / 2)
    }

    @Test
    fun snowFallsMuchSlowerThanRain() {
        // 雪的关键特征：下落明显慢于雨，否则看起来就是雨线。
        // 用短窗口测位移，避免粒子落出屏幕后重生导致采样失效。
        val snow = SnowSystem(seed = 5L)
        snow.intensity = 1f
        run(snow, 0.2f)
        val snowY0 = snow.vertices[1]
        run(snow, 0.2f)
        val snowDrop = snowY0 - snow.vertices[1]

        val rain = RainSystem(seed = 5L)
        rain.intensity = 1f
        var remaining = 0.2f
        while (remaining > 0f) {
            rain.update(1f / 60f)
            remaining -= 1f / 60f
        }
        val rainY0 = rain.vertices[1]
        remaining = 0.2f
        while (remaining > 0f) {
            rain.update(1f / 60f)
            remaining -= 1f / 60f
        }
        val rainDrop = rainY0 - rain.vertices[1]

        assertTrue(
            "雪的下落速度应远小于雨：snow=$snowDrop, rain=$rainDrop",
            snowDrop > 0f && snowDrop * 3f < rainDrop
        )
    }

    @Test
    fun resetRestoresInitialPositionsAndClearsVertices() {
        val system = SnowSystem(seed = 6L)
        system.intensity = 1f
        run(system, 10f)
        system.reset()
        // reset 只重建粒子池与内部时钟，强度保持不变（与流星系统语义不同），
        // 但顶点缓冲必须清零，避免下一帧绘制到陈旧坐标
        assertEquals(0, system.vertexCount)
        run(system, 1f)
        assertEquals(system.activeCount * 2, system.vertexCount)
    }
}
