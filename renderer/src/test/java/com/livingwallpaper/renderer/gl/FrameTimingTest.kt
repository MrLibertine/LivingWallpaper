package com.livingwallpaper.renderer.gl

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimingTest {

    private val nanosPerSecond = 1_000_000_000L

    @Test
    fun deltaSeconds_normalInterval() {
        val delta = FrameTiming.deltaSeconds(0L, nanosPerSecond / 60)
        assertEquals(1f / 60f, delta, 0.0001f)
    }

    @Test
    fun deltaSeconds_isClampedWhenResumedAfterLongPause() {
        // 锁屏 30 秒后恢复：不能被当成 30 秒的动画步进
        val delta = FrameTiming.deltaSeconds(0L, 30L * nanosPerSecond)
        assertEquals(FrameTiming.MAX_DELTA_SECONDS, delta, 0.0001f)
    }

    @Test
    fun deltaSeconds_negativeIntervalReturnsZero() {
        // 纳秒时钟回拨或初始化顺序异常时不应产生负的 deltaTime
        val delta = FrameTiming.deltaSeconds(nanosPerSecond, 0L)
        assertEquals(0f, delta, 0.0001f)
    }

    @Test
    fun deltaSeconds_zeroIntervalReturnsZero() {
        assertEquals(0f, FrameTiming.deltaSeconds(100L, 100L), 0.0001f)
    }

    @Test
    fun deltaSeconds_respectsCustomMax() {
        val delta = FrameTiming.deltaSeconds(0L, nanosPerSecond / 2, maxDeltaSeconds = 0.25f)
        assertEquals(0.25f, delta, 0.0001f)
    }

    @Test
    fun frameBudgetMillis_matchesTargetFps() {
        assertEquals(66L, FrameTiming.frameBudgetMillis(15))
        assertEquals(33L, FrameTiming.frameBudgetMillis(30))
        assertEquals(16L, FrameTiming.frameBudgetMillis(60))
    }

    @Test
    fun frameBudgetMillis_clampsInvalidInput() {
        assertEquals(1000L, FrameTiming.frameBudgetMillis(0))
        assertEquals(4L, FrameTiming.frameBudgetMillis(1000))
    }
}
