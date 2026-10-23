package com.livingwallpaper.renderer.particle

import kotlin.math.sin
import kotlin.random.Random

/**
 * 飘雪粒子系统（纯 CPU 计算，不含 GL 调用，便于单元测试）。
 *
 * 与 [RainSystem] 结构一致：坐标使用 NDC（-1..1），每条雪花 2 个顶点
 * (x, y, alpha)，尾端 alpha 为 0 形成短线。区别在于雪花下落慢、
 * 带正弦横向摇摆，线段也短得多。
 */
class SnowSystem(
    private val capacity: Int = DEFAULT_CAPACITY,
    seed: Long = DEFAULT_SEED
) {

    private val posX = FloatArray(capacity)
    private val posY = FloatArray(capacity)
    private val speed = FloatArray(capacity)
    private val length = FloatArray(capacity)
    private val alpha = FloatArray(capacity)
    private val swayPhase = FloatArray(capacity)
    private val swayAmp = FloatArray(capacity)
    private var random = Random(seed)
    private var timeSeconds = 0f

    /** 顶点输出：每条雪花 2 个顶点，每个顶点 (x, y, alpha)。 */
    val vertices = FloatArray(capacity * VERTICES_PER_FLAKE * FLOATS_PER_VERTEX)

    /** 当前写入的顶点数。 */
    var vertexCount: Int = 0
        private set

    /** 当前活跃雪花数，由雪量强度决定。 */
    var activeCount: Int = 0
        private set

    var intensity: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
            activeCount = (field * capacity).toInt().coerceIn(0, capacity)
        }

    var wind: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    init {
        for (i in 0 until capacity) {
            respawn(i, initial = true)
        }
        intensity = 0f
    }

    fun reset(newSeed: Long = DEFAULT_SEED) {
        random = Random(newSeed)
        timeSeconds = 0f
        for (i in 0 until capacity) {
            respawn(i, initial = true)
        }
        // 必须清零顶点计数：否则下一帧会绘制到 reset 之前的陈旧坐标
        vertexCount = 0
    }

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.1f) else 0f
        timeSeconds += dt
        val drift = wind * DRIFT_GAIN
        var cursor = 0
        for (i in 0 until activeCount) {
            posY[i] -= speed[i] * dt
            // 横向摇摆：每片雪花相位不同，形成自然的飘落感
            val sway = sin(timeSeconds * SWAY_FREQ + swayPhase[i]) * swayAmp[i]
            posX[i] += (drift * speed[i] + sway) * dt

            if (posY[i] < BOTTOM_LIMIT || posX[i] > SIDE_LIMIT || posX[i] < -SIDE_LIMIT) {
                respawn(i, initial = false)
            }

            vertices[cursor++] = posX[i]
            vertices[cursor++] = posY[i]
            vertices[cursor++] = alpha[i]

            vertices[cursor++] = posX[i] - drift * length[i]
            vertices[cursor++] = posY[i] - length[i]
            vertices[cursor++] = 0f
        }
        vertexCount = activeCount * VERTICES_PER_FLAKE
    }

    private fun respawn(index: Int, initial: Boolean) {
        posX[index] = random.nextFloat() * (SPAWN_RIGHT - SPAWN_LEFT) + SPAWN_LEFT
        posY[index] = if (initial) {
            random.nextFloat() * (TOP_LIMIT - BOTTOM_LIMIT) + BOTTOM_LIMIT
        } else {
            TOP_LIMIT + random.nextFloat() * 0.3f
        }
        speed[index] = SPEED_MIN + random.nextFloat() * (SPEED_MAX - SPEED_MIN)
        length[index] = LENGTH_MIN + random.nextFloat() * (LENGTH_MAX - LENGTH_MIN)
        alpha[index] = ALPHA_MIN + random.nextFloat() * (ALPHA_MAX - ALPHA_MIN)
        swayPhase[index] = random.nextFloat() * 6.2831853f
        swayAmp[index] = SWAY_MIN + random.nextFloat() * (SWAY_MAX - SWAY_MIN)
    }

    companion object {
        const val DEFAULT_CAPACITY = 700
        const val DEFAULT_SEED = 20260115L
        const val VERTICES_PER_FLAKE = 2
        const val FLOATS_PER_VERTEX = 3

        private const val DRIFT_GAIN = 0.30f
        private const val SWAY_FREQ = 1.7f

        private const val SPAWN_LEFT = -1.15f
        private const val SPAWN_RIGHT = 1.15f
        private const val TOP_LIMIT = 1.15f
        private const val BOTTOM_LIMIT = -1.15f
        private const val SIDE_LIMIT = 1.2f

        private const val SPEED_MIN = 0.10f
        private const val SPEED_MAX = 0.26f
        private const val LENGTH_MIN = 0.006f
        private const val LENGTH_MAX = 0.016f
        private const val ALPHA_MIN = 0.30f
        private const val ALPHA_MAX = 0.75f
        private const val SWAY_MIN = 0.05f
        private const val SWAY_MAX = 0.18f
    }
}
