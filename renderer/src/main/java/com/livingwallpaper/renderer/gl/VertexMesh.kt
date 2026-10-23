package com.livingwallpaper.renderer.gl

import android.opengl.GLES30
import java.nio.FloatBuffer

/** 顶点属性描述：location、分量数、在 stride 中的浮点偏移。 */
data class VertexAttribute(val location: Int, val size: Int, val offsetFloats: Int)

/**
 * 顶点缓冲 + VAO 封装。
 *
 * - 静态网格（山脉、树木）一次性上传；
 * - 动态网格（雨滴、萤火虫）每帧用 glBufferSubData 更新，避免每帧重新分配。
 *
 * 所有方法必须在 GL 线程调用。
 */
class VertexMesh(
    private val strideFloats: Int,
    private val attributes: List<VertexAttribute>,
    capacityVertices: Int,
    dynamic: Boolean = false,
    private val drawMode: Int = GLES30.GL_TRIANGLES
) {

    val capacity: Int = capacityVertices
    val data = FloatArray(capacityVertices * strideFloats)

    /** 实际使用的顶点数，必须 <= capacity。 */
    var vertexCount: Int = 0
        private set

    private val usage = if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW
    private var vbo = 0
    private var vao = 0
    private var initialized = false

    fun initialize() {
        if (initialized) return
        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        vbo = buffers[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * FLOAT_BYTES, null, usage)

        val arrays = IntArray(1)
        GLES30.glGenVertexArrays(1, arrays, 0)
        vao = arrays[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val strideBytes = strideFloats * FLOAT_BYTES
        for (attribute in attributes) {
            GLES30.glVertexAttribPointer(
                attribute.location,
                attribute.size,
                GLES30.GL_FLOAT,
                false,
                strideBytes,
                attribute.offsetFloats * FLOAT_BYTES
            )
            GLES30.glEnableVertexAttribArray(attribute.location)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        initialized = true
    }

    /** 写入 vertexCount 后调用，把 CPU 侧数据同步到 GPU。 */
    fun setVertexCount(count: Int) {
        vertexCount = count.coerceIn(0, capacity)
    }

    fun upload() {
        if (!initialized || vertexCount == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(
            GLES30.GL_ARRAY_BUFFER,
            0,
            vertexCount * strideFloats * FLOAT_BYTES,
            FloatBuffer.wrap(data, 0, vertexCount * strideFloats)
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        if (!initialized || vertexCount == 0) return
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(drawMode, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        vertexCount = 0
        initialized = false
    }

    private companion object {
        const val FLOAT_BYTES = 4
    }
}
