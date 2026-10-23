package com.livingwallpaper.renderer.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 渲染统计。所有数值都来自实际测量，没有任何估算或写死的“好看数字”。
 *
 * 帧间隔取渲染循环里连续两帧的真实时间差（包含为限制帧率而做的 sleep），
 * 因此 measuredFps 反映用户实际看到的刷新率，而不是 GPU 的瞬时绘制速度。
 */
data class RenderStats(
    /** 累计渲染帧数。 */
    val framesRendered: Long = 0,
    /** 最近一帧的间隔（毫秒）。 */
    val lastFrameMillis: Float = 0f,
    /** 帧间隔的指数移动平均（毫秒）。 */
    val averageFrameMillis: Float = 0f,
    /** 累计丢帧数：间隔超过目标帧预算 1.5 倍视为一次丢帧。 */
    val droppedFrames: Long = 0,
    /** 由平均帧间隔换算的实测帧率。 */
    val measuredFps: Float = 0f,
    /** 已采样帧数。为 0 时表示还没有任何实测数据。 */
    val sampleCount: Long = 0
) {
    val hasSamples: Boolean get() = sampleCount > 0L

    companion object {
        val EMPTY = RenderStats()
    }
}

/**
 * 进程内共享的统计容器。App 内预览与系统壁纸在同一进程，
 * 因此性能页展示的就是当前真实在跑的那个渲染循环的统计。
 */
object RenderStatsHolder {

    private const val EMA_ALPHA = 0.1f
    private const val DROP_THRESHOLD_RATIO = 1.5f

    private val _stats = MutableStateFlow(RenderStats.EMPTY)
    val stats: StateFlow<RenderStats> = _stats.asStateFlow()

    @Volatile private var frames = 0L
    @Volatile private var dropped = 0L
    @Volatile private var samples = 0L
    @Volatile private var ema = 0f

    fun onFrame(frameMillis: Float, targetFps: Int) {
        if (!frameMillis.isFinite() || frameMillis <= 0f) return
        frames++
        samples++
        ema = if (samples == 1L) frameMillis else ema * (1f - EMA_ALPHA) + frameMillis * EMA_ALPHA

        val budget = 1000f / targetFps.coerceIn(1, 240)
        if (frameMillis > budget * DROP_THRESHOLD_RATIO) dropped++

        _stats.value = RenderStats(
            framesRendered = frames,
            lastFrameMillis = frameMillis,
            averageFrameMillis = ema,
            droppedFrames = dropped,
            measuredFps = if (ema > 0f) 1000f / ema else 0f,
            sampleCount = samples
        )
    }

    /** 渲染循环结束/重建时调用，避免把旧统计继续展示成当前状态。 */
    fun reset() {
        frames = 0L
        dropped = 0L
        samples = 0L
        ema = 0f
        _stats.value = RenderStats.EMPTY
    }

    fun snapshot(): RenderStats = _stats.value
}
