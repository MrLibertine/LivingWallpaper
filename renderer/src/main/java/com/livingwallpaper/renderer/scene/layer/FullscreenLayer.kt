package com.livingwallpaper.renderer.scene.layer

import android.opengl.GLES30
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.gl.VertexAttribute
import com.livingwallpaper.renderer.gl.VertexMesh

/**
 * 全屏图层：用覆盖裁剪空间的大三角形做一次 Draw Call，
 * 具体的 uniform 由调用方通过 [render] 的 lambda 设置，
 * 因此天空渐变、夕阳、星空等不同片元着色器可以共用同一套几何。
 */
class FullscreenLayer(
    private val vertexSource: String,
    private val fragmentSource: String
) {

    private var program: ShaderProgram? = null
    private var mesh: VertexMesh? = null

    fun initialize() {
        program = ShaderProgram(vertexSource, fragmentSource)
        val triangles = VertexMesh(
            strideFloats = 2,
            attributes = listOf(VertexAttribute(location = 0, size = 2, offsetFloats = 0)),
            capacityVertices = 3
        )
        triangles.data[0] = -1f
        triangles.data[1] = -1f
        triangles.data[2] = 3f
        triangles.data[3] = -1f
        triangles.data[4] = -1f
        triangles.data[5] = 3f
        triangles.setVertexCount(3)
        triangles.initialize()
        triangles.upload()
        mesh = triangles
    }

    /**
     * @param uniforms 在 glUseProgram 之后调用，用于写入该片元着色器需要的 uniform。
     */
    fun render(width: Int, height: Int, uniforms: (ShaderProgram) -> Unit) {
        val current = program ?: return
        val geometry = mesh ?: return
        GLES30.glViewport(0, 0, width, height)
        current.use()
        GLES30.glUniform2f(current.uniformLocation(U_RESOLUTION), width.toFloat(), height.toFloat())
        uniforms(current)
        geometry.draw()
    }

    /** 渐变天空的便捷方法。 */
    fun renderGradient(
        elapsedSeconds: Float,
        width: Int,
        height: Int,
        topColor: FloatArray,
        bottomColor: FloatArray,
        accentColor: FloatArray
    ) {
        render(width, height) { program ->
            GLES30.glUniform1f(program.uniformLocation(U_TIME), elapsedSeconds)
            uniform3f(program, U_TOP_COLOR, topColor)
            uniform3f(program, U_BOTTOM_COLOR, bottomColor)
            uniform3f(program, U_ACCENT_COLOR, accentColor)
        }
    }

    fun release() {
        mesh?.release()
        mesh = null
        program?.delete()
        program = null
    }

    private fun uniform3f(program: ShaderProgram, name: String, value: FloatArray) {
        GLES30.glUniform3f(program.uniformLocation(name), value[0], value[1], value[2])
    }

    companion object {
        const val U_TIME = "uTime"
        const val U_RESOLUTION = "uResolution"
        const val U_TOP_COLOR = "uTopColor"
        const val U_BOTTOM_COLOR = "uBottomColor"
        const val U_ACCENT_COLOR = "uAccentColor"
    }
}
