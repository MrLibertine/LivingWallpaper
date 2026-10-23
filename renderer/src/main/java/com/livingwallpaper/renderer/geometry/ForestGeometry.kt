package com.livingwallpaper.renderer.geometry

import kotlin.math.sin
import kotlin.random.Random

/**
 * 森林雨夜场景的静态网格生成。
 *
 * 顶点格式统一为 (x, y, sway)：
 * - x、y 使用 NDC（-1..1），横向故意超出屏幕范围，为视差平移留出余量；
 * - sway 表示“随风摆动的程度”，0 为根部固定，1 为树冠，值为 (y - baseY) / height。
 *
 * 生成使用固定种子，保证同一场景每次形态一致（可复现，也便于排查）。
 */
object ForestGeometry {

    const val STRIDE_FLOATS = 3

    /**
     * 山脊线的三个随机相位（与 [buildMountains] 内部完全一致）。
     *
     * 水面倒影需要在片元着色器里复现同一条山脊线，所以相位必须能在
     * CPU 侧取出并作为 uniform 传下去，而不是让着色器另猜一组。
     */
    fun mountainPhases(seed: Long): FloatArray {
        val random = Random(seed)
        return floatArrayOf(
            random.nextFloat() * 6.2831853f,
            random.nextFloat() * 6.2831853f,
            random.nextFloat() * 6.2831853f
        )
    }

    /** 给定相位与振幅，求山脊线在 x 处的高度（NDC y）。 */
    fun ridgeHeight(x: Float, baseY: Float, amplitude: Float, phases: FloatArray): Float =
        baseY +
            0.115f * amplitude +
            0.075f * amplitude * sin(x * 2.1f + phases[0]) +
            0.045f * amplitude * sin(x * 4.7f + phases[1]) +
            0.022f * amplitude * sin(x * 9.3f + phases[2])

    /** 远景山脉：由多组正弦叠加生成山脊线，三角化为实体剪影。 */
    fun buildMountains(
        seed: Long,
        samples: Int = DEFAULT_SAMPLES,
        baseY: Float = DEFAULT_BASE_Y,
        amplitude: Float = DEFAULT_AMPLITUDE,
        xLeft: Float = MOUNTAIN_LEFT,
        xRight: Float = MOUNTAIN_RIGHT
    ): FloatArray {
        val phases = mountainPhases(seed)
        val bottom = -1.0f

        fun ridge(x: Float): Float = ridgeHeight(x, baseY, amplitude, phases)

        val segments = (samples - 1).coerceAtLeast(1)
        val out = FloatArray(segments * 6 * STRIDE_FLOATS)
        var cursor = 0
        val step = (xRight - xLeft) / segments
        for (i in 0 until segments) {
            val x0 = xLeft + step * i
            val x1 = x0 + step
            val y0 = ridge(x0)
            val y1 = ridge(x1)

            // 三角形 A：(x0,y0) (x0,bottom) (x1,y1)
            cursor = write(out, cursor, x0, y0)
            cursor = write(out, cursor, x0, bottom)
            cursor = write(out, cursor, x1, y1)
            // 三角形 B：(x1,y1) (x0,bottom) (x1,bottom)
            cursor = write(out, cursor, x1, y1)
            cursor = write(out, cursor, x0, bottom)
            cursor = write(out, cursor, x1, bottom)
        }
        return out
    }

    /**
     * 树层：每棵树 = 树干矩形 + 三层针叶三角形，共 15 个顶点。
     * 树按 x 排序生成，避免同层内出现明显的随机遮挡错乱。
     */
    fun buildTrees(
        seed: Long,
        count: Int,
        baseY: Float,
        minHeight: Float,
        maxHeight: Float,
        minWidth: Float,
        maxWidth: Float
    ): FloatArray {
        val random = Random(seed)
        val out = FloatArray(count * VERTS_PER_TREE * STRIDE_FLOATS)
        var cursor = 0

        val positions = FloatArray(count)
        for (i in 0 until count) {
            positions[i] = TREE_LEFT + (TREE_RIGHT - TREE_LEFT) * (i + random.nextFloat()) / count
        }
        positions.sort()

        for (i in 0 until count) {
            val x = positions[i]
            val height = minHeight + random.nextFloat() * (maxHeight - minHeight)
            val width = minWidth + random.nextFloat() * (maxWidth - minWidth)
            val trunkHalf = width * 0.11f
            val trunkTop = baseY + height * 0.34f

            fun swayAt(y: Float): Float = ((y - baseY) / height).coerceIn(0f, 1f)

            // 树干（两个三角形组成的矩形）
            cursor = write(out, cursor, x - trunkHalf, baseY, swayAt(baseY))
            cursor = write(out, cursor, x + trunkHalf, baseY, swayAt(baseY))
            cursor = write(out, cursor, x + trunkHalf, trunkTop, swayAt(trunkTop))
            cursor = write(out, cursor, x - trunkHalf, baseY, swayAt(baseY))
            cursor = write(out, cursor, x + trunkHalf, trunkTop, swayAt(trunkTop))
            cursor = write(out, cursor, x - trunkHalf, trunkTop, swayAt(trunkTop))

            // 针叶：三层递减宽度
            val layers = floatArrayOf(1.0f, 0.76f, 0.52f)
            for (level in 0..2) {
                val halfWidth = width * layers[level]
                val y0 = baseY + height * (0.20f + level * 0.24f)
                val y1 = baseY + height * (0.52f + level * 0.24f)
                cursor = write(out, cursor, x - halfWidth, y0, swayAt(y0))
                cursor = write(out, cursor, x + halfWidth, y0, swayAt(y0))
                cursor = write(out, cursor, x, y1, swayAt(y1))
            }
        }
        return out
    }

    private fun write(out: FloatArray, cursor: Int, x: Float, y: Float, sway: Float = 0f): Int {
        out[cursor] = x
        out[cursor + 1] = y
        out[cursor + 2] = sway
        return cursor + STRIDE_FLOATS
    }

    const val VERTS_PER_TREE = 15

    private const val DEFAULT_SAMPLES = 64
    private const val DEFAULT_BASE_Y = -0.22f
    private const val DEFAULT_AMPLITUDE = 1.0f
    private const val MOUNTAIN_LEFT = -1.3f
    private const val MOUNTAIN_RIGHT = 1.3f
    private const val TREE_LEFT = -1.2f
    private const val TREE_RIGHT = 1.2f
}
