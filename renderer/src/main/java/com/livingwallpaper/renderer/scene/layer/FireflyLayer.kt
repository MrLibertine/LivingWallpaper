package com.livingwallpaper.renderer.scene.layer

import android.opengl.GLES30
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.gl.VertexAttribute
import com.livingwallpaper.renderer.gl.VertexMesh
import com.livingwallpaper.renderer.particle.FireflySystem

/**
 * 萤火虫渲染层：点精灵（GL_POINTS），光晕在片元着色器里按径向衰减生成，
 * 不使用贴图，避免引入二进制资源。
 */
class FireflyLayer(
    private val vertexSource: String,
    private val fragmentSource: String,
    private val capacity: Int = FireflySystem.DEFAULT_CAPACITY
) {

    private var program: ShaderProgram? = null
    private var mesh: VertexMesh? = null

    fun initialize() {
        program = ShaderProgram(vertexSource, fragmentSource)
        mesh = VertexMesh(
            strideFloats = FireflySystem.FLOATS_PER_VERTEX,
            attributes = listOf(
                VertexAttribute(location = 0, size = 2, offsetFloats = 0),
                VertexAttribute(location = 1, size = 1, offsetFloats = 2),
                VertexAttribute(location = 2, size = 1, offsetFloats = 3)
            ),
            capacityVertices = capacity,
            dynamic = true,
            drawMode = GLES30.GL_POINTS
        ).also { it.initialize() }
    }

    fun render(system: FireflySystem, timeSeconds: Float, color: FloatArray) {
        val current = program ?: return
        val geometry = mesh ?: return
        val count = system.vertexCount
        if (count == 0) return

        System.arraycopy(system.vertices, 0, geometry.data, 0, count * FireflySystem.FLOATS_PER_VERTEX)
        geometry.setVertexCount(count)
        geometry.upload()

        current.use()
        GLES30.glUniform1f(current.uniformLocation(U_TIME), timeSeconds)
        GLES30.glUniform3f(current.uniformLocation(U_COLOR), color[0], color[1], color[2])
        geometry.draw()
    }

    fun release() {
        mesh?.release()
        mesh = null
        program?.delete()
        program = null
    }

    private companion object {
        const val U_TIME = "uTime"
        const val U_COLOR = "uColor"
    }
}
