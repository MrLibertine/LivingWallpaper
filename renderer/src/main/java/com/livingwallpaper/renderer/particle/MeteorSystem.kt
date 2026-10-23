package com.livingwallpaper.renderer.particle

import kotlin.math.sin
import kotlin.random.Random

/**
 * 流星粒子系统（纯 CPU 计算）。
 *
 * 与雨滴的区别：流星是“低频突发事件”——按每秒生成概率逐个触发，
 * 每颗有独立生命周期与拖尾，生命结束后回到对象池。
 *
 * 顶点格式与雨滴完全一致 (x, y, alpha)，因此共用同一个着色器与渲染层。
 */
class MeteorSystem(
    private val capacity: Int = DEFAULT_CAPACITY,
    seed: Long = DEFAULT_SEED
) {

    private val posX = FloatArray(capacity)
    private val posY = FloatArray(capacity)
    private val dirX = FloatArray(capacity)
    private val dirY = FloatArray(capacity)
    private val speed = FloatArray(capacity)
    private val trail = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val active = BooleanArray(capacity)
    private var random = Random(seed)

    private var spawnAccumulator = 0f

    /** 顶点输出：每颗流星 2 个顶点 (x, y, alpha)。 */
    val vertices = FloatArray(capacity * VERTICES_PER_METEOR * FLOATS_PER_VERTEX)

    var vertexCount: Int = 0
        private set

    /** 同时存在的流星数量，仅用于观测。 */
    var aliveCount: Int = 0
        private set

    var density: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    var wind: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    fun reset(newSeed: Long = DEFAULT_SEED) {
        random = Random(newSeed)
        active.fill(false)
        spawnAccumulator = 0f
        vertexCount = 0
        aliveCount = 0
    }

    fun update(deltaSeconds: Float) {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.1f) else 0f

        // 按“每秒生成个数”积分，保证与帧率无关、且不会因对象池槽位数放大
        spawnAccumulator += density * SPAWNS_PER_SECOND_AT_FULL_DENSITY * dt
        while (spawnAccumulator >= 1f) {
            spawnAccumulator -= 1f
            spawn()
        }

        var cursor = 0
        var alive = 0
        for (i in 0 until capacity) {
            if (!active[i]) continue

            life[i] -= dt
            if (life[i] <= 0f) {
                active[i] = false
                continue
            }

            val step = speed[i] * dt
            posX[i] += dirX[i] * step
            posY[i] += dirY[i] * step

            // 飞出画面即回收
            if (posY[i] < BOTTOM_LIMIT || posX[i] < LEFT_LIMIT || posX[i] > RIGHT_LIMIT) {
                active[i] = false
                continue
            }

            alive++
            // 生命中段最亮，出生与消失时渐隐
            val progress = 1f - (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val alpha = sin(progress * 3.1415927f) * MAX_ALPHA

            vertices[cursor++] = posX[i]
            vertices[cursor++] = posY[i]
            vertices[cursor++] = alpha

            vertices[cursor++] = posX[i] - dirX[i] * trail[i]
            vertices[cursor++] = posY[i] - dirY[i] * trail[i]
            vertices[cursor++] = 0f
        }
        aliveCount = alive
        vertexCount = alive * VERTICES_PER_METEOR
    }

    private fun spawn() {
        var index = -1
        for (i in 0 until capacity) {
            if (!active[i]) {
                index = i
                break
            }
        }
        if (index < 0) return

        val angle = MIN_ANGLE + random.nextFloat() * (MAX_ANGLE - MIN_ANGLE)
        val radians = Math.toRadians(angle.toDouble())
        dirX[index] = kotlin.math.cos(radians).toFloat()
        dirY[index] = -kotlin.math.sin(radians).toFloat()

        posX[index] = SPAWN_MIN_X + random.nextFloat() * (SPAWN_MAX_X - SPAWN_MIN_X)
        posY[index] = SPAWN_MIN_Y + random.nextFloat() * (SPAWN_MAX_Y - SPAWN_MIN_Y)
        speed[index] = SPEED_MIN + random.nextFloat() * (SPEED_MAX - SPEED_MIN) * (1f + wind * 0.4f)
        trail[index] = TRAIL_MIN + random.nextFloat() * (TRAIL_MAX - TRAIL_MIN)
        maxLife[index] = LIFE_MIN + random.nextFloat() * (LIFE_MAX - LIFE_MIN)
        life[index] = maxLife[index]
        active[index] = true
    }

    companion object {
        const val DEFAULT_CAPACITY = 12
        const val DEFAULT_SEED = 31415L
        const val VERTICES_PER_METEOR = 2
        const val FLOATS_PER_VERTEX = 3

        private const val SPAWNS_PER_SECOND_AT_FULL_DENSITY = 0.45f
        private const val MAX_ALPHA = 0.9f

        // 出射角度（度）：60°~80° 表示向右下方斜飞
        private const val MIN_ANGLE = 60f
        private const val MAX_ANGLE = 80f

        private const val SPAWN_MIN_X = -0.15f
        private const val SPAWN_MAX_X = 1.05f
        private const val SPAWN_MIN_Y = 0.55f
        private const val SPAWN_MAX_Y = 1.05f

        private const val SPEED_MIN = 0.75f
        private const val SPEED_MAX = 1.55f
        private const val TRAIL_MIN = 0.22f
        private const val TRAIL_MAX = 0.5f
        private const val LIFE_MIN = 0.8f
        private const val LIFE_MAX = 1.7f

        private const val BOTTOM_LIMIT = -1.2f
        private const val LEFT_LIMIT = -1.3f
        private const val RIGHT_LIMIT = 1.3f
    }
}
