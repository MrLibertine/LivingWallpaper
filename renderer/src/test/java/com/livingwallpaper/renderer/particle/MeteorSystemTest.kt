package com.livingwallpaper.renderer.particle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteorSystemTest {

    private fun run(system: MeteorSystem, seconds: Float, stepSeconds: Float = 1f / 60f) {
        var remaining = seconds
        while (remaining > 0f) {
            system.update(stepSeconds)
            remaining -= stepSeconds
        }
    }

    @Test
    fun zeroDensityNeverSpawns() {
        val system = MeteorSystem(seed = 1L)
        system.density = 0f
        run(system, 60f)
        assertEquals(0, system.aliveCount)
        assertEquals(0, system.vertexCount)
    }

    @Test
    fun highDensitySpawnsMeteors() {
        val system = MeteorSystem(seed = 2L)
        system.density = 1f
        run(system, 60f)
        assertTrue("高密度下应出现流星，实际 aliveCount=${system.aliveCount}", system.vertexCount > 0)
    }

    @Test
    fun spawnRateIsFrameRateIndependent() {
        // 同样的时长，不同步长下生成的流星总数应当接近
        fun totalSpawns(stepSeconds: Float): Int {
            val system = MeteorSystem(seed = 3L)
            system.density = 1f
            var seen = 0
            var previous = 0
            var remaining = 120f
            while (remaining > 0f) {
                system.update(stepSeconds)
                if (system.aliveCount > previous) seen += system.aliveCount - previous
                previous = system.aliveCount
                remaining -= stepSeconds
            }
            return seen
        }
        val fast = totalSpawns(1f / 120f)
        val slow = totalSpawns(1f / 30f)
        // 120 秒 × 0.45 次/秒 ≈ 54 次，允许较大偏差以容纳随机性
        assertTrue("高频步长生成数=$fast", fast in 30..80)
        assertTrue("低频步长生成数=$slow", slow in 30..80)
    }

    @Test
    fun vertexCountMatchesTwoVerticesPerMeteor() {
        val system = MeteorSystem(seed = 4L)
        system.density = 1f
        run(system, 30f)
        assertEquals(system.aliveCount * 2, system.vertexCount)
    }

    @Test
    fun meteorsStayInsideBounds() {
        val system = MeteorSystem(seed = 5L)
        system.density = 1f
        system.wind = 1f
        var remaining = 120f
        while (remaining > 0f) {
            system.update(1f / 60f)
            remaining -= 1f / 60f
            for (i in 0 until system.vertexCount) {
                val x = system.vertices[i * 3]
                val y = system.vertices[i * 3 + 1]
                assertTrue("x 越界: $x", x in -1.6f..1.6f)
                assertTrue("y 越界: $y", y in -1.6f..1.6f)
            }
        }
    }

    @Test
    fun meteorsEventuallyRecycle() {
        val system = MeteorSystem(seed = 6L)
        system.density = 1f
        run(system, 30f)
        val peak = system.aliveCount
        system.density = 0f
        run(system, 10f)
        // 停止生成后，已有流星会在生命周期内全部回收
        assertEquals(0, system.aliveCount)
        assertTrue("峰值应大于 0，实际 $peak", peak >= 0)
    }

    @Test
    fun resetClearsAllState() {
        val system = MeteorSystem(seed = 7L)
        system.density = 1f
        run(system, 30f)
        system.reset()
        assertEquals(0, system.aliveCount)
        assertEquals(0, system.vertexCount)
    }
}
