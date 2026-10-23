package com.livingwallpaper.renderer.particle

import kotlin.random.Random

/**
 * 雨滴粒子系统（纯 CPU 计算，不含任何 GL 调用，便于单元测试）。
 *
 * 坐标使用 NDC（-1..1），屏幕宽高比导致的视觉拉伸由线段长度体现，不做补偿。
 * 对象池容量固定，运行时不分配对象；强度变化只改变 [activeCount]。
 */
class RainSystem(
    private val capacity: Int = DEFAULT_CAPACITY,
    seed: Long = DEFAULT_SEED
) {

    private val posX = FloatArray(capacity)
    private val posY = FloatArray(capacity)
    private val speed = FloatArray(capacity)
    private val length = FloatArray(capacity)
    private val alpha = FloatArray(capacity)
    private var random = Random(seed)

    /**
     * 顶点输出：每条雨滴 2 个顶点，每个顶点 (x, y, alpha)。
     * 头端 alpha 为雨滴亮度，尾端为 0，形成拖尾渐隐。
     */
    val vertices = FloatArray(capacity * VERTICES_PER_DROP * FLOATS_PER_VERTEX)

    /** 当前写入的顶点数。 */
    var vertexCount: Int = 0
        private set

    /** 当前活跃雨滴数，由雨量强度决定。 */
    var activeCount: Int = 0
        private set

    var intensity: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
            // 对象池已全部初始化，强度变化只调整活跃数量，不重新分配
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
        for (i in 0 until capacity) {
            respawn(i, initial = true)
        }
    }

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.1f) else 0f
        val fallScale = FALL_BASE + intensity * FALL_INTENSITY_GAIN
        val slant = wind * SLANT_GAIN
        var cursor = 0
        for (i in 0 until activeCount) {
            posY[i] -= speed[i] * fallScale * dt
            posX[i] += slant * speed[i] * dt

            if (posY[i] < BOTTOM_LIMIT || posX[i] > SIDE_LIMIT || posX[i] < -SIDE_LIMIT) {
                respawn(i, initial = false)
            }

            vertices[cursor++] = posX[i]
            vertices[cursor++] = posY[i]
            vertices[cursor++] = alpha[i]

            vertices[cursor++] = posX[i] - slant * length[i]
            vertices[cursor++] = posY[i] - length[i]
            vertices[cursor++] = 0f
        }
        vertexCount = activeCount * VERTICES_PER_DROP
    }

    private fun respawn(index: Int, initial: Boolean) {
        posX[index] = random.nextFloat() * (SPAWN_RIGHT - SPAWN_LEFT) + SPAWN_LEFT
        posY[index] = if (initial) {
            random.nextFloat() * (TOP_LIMIT - BOTTOM_LIMIT) + BOTTOM_LIMIT
        } else {
            TOP_LIMIT + random.nextFloat() * 0.25f
        }
        speed[index] = SPEED_MIN + random.nextFloat() * (SPEED_MAX - SPEED_MIN)
        length[index] = LENGTH_MIN + random.nextFloat() * (LENGTH_MAX - LENGTH_MIN)
        alpha[index] = ALPHA_MIN + random.nextFloat() * (ALPHA_MAX - ALPHA_MIN)
    }

    companion object {
        const val DEFAULT_CAPACITY = 1200
        const val DEFAULT_SEED = 20241022L
        const val VERTICES_PER_DROP = 2
        const val FLOATS_PER_VERTEX = 3

        private const val FALL_BASE = 0.55f
        private const val FALL_INTENSITY_GAIN = 1.25f
        private const val SLANT_GAIN = 0.35f

        private const val SPAWN_LEFT = -1.15f
        private const val SPAWN_RIGHT = 1.15f
        private const val TOP_LIMIT = 1.1f
        private const val BOTTOM_LIMIT = -1.15f
        private const val SIDE_LIMIT = 1.2f

        private const val SPEED_MIN = 0.9f
        private const val SPEED_MAX = 1.8f
        private const val LENGTH_MIN = 0.05f
        private const val LENGTH_MAX = 0.12f
        private const val ALPHA_MIN = 0.12f
        private const val ALPHA_MAX = 0.34f
    }
}
