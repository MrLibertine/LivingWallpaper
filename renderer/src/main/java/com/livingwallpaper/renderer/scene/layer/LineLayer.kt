package com.livingwallpaper.renderer.scene.layer

import android.opengl.GLES30
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.gl.VertexAttribute
import com.livingwallpaper.renderer.gl.VertexMesh

/**
 * 线段渲染层：雨滴、流星共用。
 *
 * 顶点格式 (x, y, alpha)，两个顶点一条线段；CPU 每帧把粒子系统输出的
 * FloatArray 拷入动态 VBO，只做一次 glBufferSubData。
 */
class LineLayer(
    private val vertexSource: String,
    private val fragmentSource: String,
    private val capacityVertices: Int
) {

    companion object {
        const val FLOATS_PER_VERTEX = 3
        private const val U_COLOR = "uColor"
    }

    private var program: ShaderProgram? = null
    private var mesh: VertexMesh? = null

    fun initialize() {
        program = ShaderProgram(vertexSource, fragmentSource)
        mesh = VertexMesh(
            strideFloats = FLOATS_PER_VERTEX,
            attributes = listOf(
                VertexAttribute(location = 0, size = 2, offsetFloats = 0),
                VertexAttribute(location = 1, size = 1, offsetFloats = 2)
            ),
            capacityVertices = capacityVertices,
            dynamic = true,
            drawMode = GLES30.GL_LINES
        ).also { it.initialize() }
    }

    /** @param vertices 粒子系统输出；@param vertexCount 实际顶点数。 */
    fun render(vertices: FloatArray, vertexCount: Int, color: FloatArray) {
        val current = program ?: return
        val geometry = mesh ?: return
        if (vertexCount <= 0) return
        val safeCount = vertexCount.coerceAtMost(geometry.capacity)

        System.arraycopy(vertices, 0, geometry.data, 0, safeCount * FLOATS_PER_VERTEX)
        geometry.setVertexCount(safeCount)
        geometry.upload()

        current.use()
        GLES30.glUniform3f(current.uniformLocation(U_COLOR), color[0], color[1], color[2])
        geometry.draw()
    }

    fun release() {
        mesh?.release()
        mesh = null
        program?.delete()
        program = null
    }
}
