package com.livingwallpaper.renderer.particle

import kotlin.math.sin
import kotlin.random.Random

/**
 * 萤火虫粒子系统（纯 CPU 计算）。
 *
 * 每只萤火虫有独立生命周期：生成 → 漂移 + 上下浮动 → 生命耗尽后重新生成。
 * 闪烁相位交给着色器按 uTime 计算，CPU 只维护相位常量。
 */
class FireflySystem(
    private val capacity: Int = DEFAULT_CAPACITY,
    seed: Long = DEFAULT_SEED
) {

    private val posX = FloatArray(capacity)
    private val posY = FloatArray(capacity)
    private val driftX = FloatArray(capacity)
    private val driftY = FloatArray(capacity)
    private val phase = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private var random = Random(seed)

    /** 顶点输出：每个粒子 (x, y, phase, size)。 */
    val vertices = FloatArray(capacity * FLOATS_PER_VERTEX)

    var vertexCount: Int = 0
        private set

    var activeCount: Int = 0
        private set

    /** 累计时间，用于上下浮动。 */
    var elapsedSeconds: Float = 0f
        private set

    var density: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
            val next = (field * capacity).toInt().coerceIn(0, capacity)
            if (next > activeCount) {
                for (i in activeCount until next) {
                    respawn(i)
                }
            }
            activeCount = next
        }

    var wind: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    init {
        for (i in 0 until capacity) {
            respawn(i)
        }
        density = 0f
    }

    fun reset(newSeed: Long = DEFAULT_SEED) {
        random = Random(newSeed)
        elapsedSeconds = 0f
        for (i in 0 until capacity) {
            respawn(i)
        }
    }

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.1f) else 0f
        elapsedSeconds += dt
        val pushX = wind * WIND_PUSH

        var cursor = 0
        for (i in 0 until activeCount) {
            life[i] -= dt
            if (life[i] <= 0f) {
                respawn(i)
            }

            val bob = sin(elapsedSeconds * BOB_FREQUENCY + phase[i] * 6.2831853f) * BOB_AMPLITUDE
            posX[i] += (driftX[i] + pushX) * dt
            posY[i] += (driftY[i] + bob) * dt

            if (posX[i] > SIDE_LIMIT) posX[i] = -SIDE_LIMIT
            if (posX[i] < -SIDE_LIMIT) posX[i] = SIDE_LIMIT
            if (posY[i] > TOP_LIMIT) posY[i] = TOP_LIMIT
            if (posY[i] < BOTTOM_LIMIT) posY[i] = BOTTOM_LIMIT

            vertices[cursor++] = posX[i]
            vertices[cursor++] = posY[i]
            vertices[cursor++] = phase[i]
            vertices[cursor++] = size[i]
        }
        vertexCount = activeCount
    }

    private fun respawn(index: Int) {
        posX[index] = random.nextFloat() * (SIDE_LIMIT * 2f) - SIDE_LIMIT
        posY[index] = random.nextFloat() * (TOP_LIMIT - BOTTOM_LIMIT) + BOTTOM_LIMIT
        driftX[index] = (random.nextFloat() - 0.5f) * DRIFT_SPEED
        driftY[index] = (random.nextFloat() - 0.5f) * DRIFT_SPEED
        phase[index] = random.nextFloat()
        size[index] = SIZE_MIN + random.nextFloat() * (SIZE_MAX - SIZE_MIN)
        maxLife[index] = LIFE_MIN + random.nextFloat() * (LIFE_MAX - LIFE_MIN)
        life[index] = maxLife[index]
    }

    companion object {
        const val DEFAULT_CAPACITY = 160
        const val DEFAULT_SEED = 771102L
        const val FLOATS_PER_VERTEX = 4

        private const val WIND_PUSH = 0.05f
        private const val BOB_FREQUENCY = 0.9f
        private const val BOB_AMPLITUDE = 0.02f
        private const val DRIFT_SPEED = 0.06f

        private const val SIDE_LIMIT = 1.05f
        private const val TOP_LIMIT = 0.55f
        private const val BOTTOM_LIMIT = -0.55f

        private const val SIZE_MIN = 5f
        private const val SIZE_MAX = 15f
        private const val LIFE_MIN = 6f
        private const val LIFE_MAX = 16f
    }
}
