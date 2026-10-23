package com.livingwallpaper.renderer.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderStatsTest {

    @Test
    fun emptyStatsHasNoSamples() {
        RenderStatsHolder.reset()
        assertFalse(RenderStatsHolder.snapshot().hasSamples)
        assertEquals(0f, RenderStatsHolder.snapshot().measuredFps, 0.0001f)
    }

    @Test
    fun steadySixtyFpsProducesAboutSixtyFps() {
        RenderStatsHolder.reset()
        // 60 FPS 对应 16.67ms，跑 200 帧让 EMA 收敛
        repeat(200) { RenderStatsHolder.onFrame(1000f / 60f, targetFps = 60) }
        val stats = RenderStatsHolder.snapshot()
        assertTrue(stats.hasSamples)
        assertEquals(60f, stats.measuredFps, 1.0f)
        assertEquals(200L, stats.framesRendered)
    }

    @Test
    fun slowFramesAreCountedAsDropped() {
        RenderStatsHolder.reset()
        // 目标 60 FPS（预算 16.67ms），单帧 100ms 远超 1.5 倍预算
        repeat(5) { RenderStatsHolder.onFrame(100f, targetFps = 60) }
        assertEquals(5L, RenderStatsHolder.snapshot().droppedFrames)
    }

    @Test
    fun framesWithinBudgetAreNotDropped() {
        RenderStatsHolder.reset()
        repeat(10) { RenderStatsHolder.onFrame(16f, targetFps = 60) }
        assertEquals(0L, RenderStatsHolder.snapshot().droppedFrames)
    }

    @Test
    fun emaSmoothsOutliers() {
        RenderStatsHolder.reset()
        repeat(100) { RenderStatsHolder.onFrame(20f, targetFps = 60) }
        val stable = RenderStatsHolder.snapshot().averageFrameMillis
        RenderStatsHolder.onFrame(500f, targetFps = 60)
        val afterSpike = RenderStatsHolder.snapshot().averageFrameMillis
        // 单次尖峰不应让平均值直接跳到 500ms
        assertTrue("EMA 应平滑尖峰：stable=$stable, after=$afterSpike", afterSpike < 200f)
        assertEquals(500f, RenderStatsHolder.snapshot().lastFrameMillis, 0.0001f)
    }

    @Test
    fun invalidFrameTimesAreIgnored() {
        RenderStatsHolder.reset()
        RenderStatsHolder.onFrame(Float.NaN, 60)
        RenderStatsHolder.onFrame(-1f, 60)
        RenderStatsHolder.onFrame(0f, 60)
        assertFalse(RenderStatsHolder.snapshot().hasSamples)
    }

    @Test
    fun resetClearsAccumulatedState() {
        RenderStatsHolder.reset()
        repeat(10) { RenderStatsHolder.onFrame(16f, 60) }
        assertTrue(RenderStatsHolder.snapshot().hasSamples)
        RenderStatsHolder.reset()
        assertEquals(0L, RenderStatsHolder.snapshot().framesRendered)
        assertEquals(0L, RenderStatsHolder.snapshot().droppedFrames)
        assertFalse(RenderStatsHolder.snapshot().hasSamples)
    }
}
