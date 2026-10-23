package com.livingwallpaper.renderer.scene.layer

import android.opengl.GLES30
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.gl.VertexMesh

/**
 * 剪影层：山脉与树木共用同一个着色器，只是网格与颜色不同。
 *
 * 摆动完全在顶点着色器里按 aSway 计算，CPU 每帧只更新两个 uniform。
 */
class SilhouetteLayer(
    private val vertexSource: String,
    private val fragmentSource: String
) {

    private var program: ShaderProgram? = null

    fun initialize() {
        program = ShaderProgram(vertexSource, fragmentSource)
    }

    fun render(
        mesh: VertexMesh,
        offsetX: Float,
        offsetY: Float,
        sway: Float,
        timeSeconds: Float,
        color: FloatArray,
        alpha: Float,
        fadeBaseY: Float = 0f,
        fadeRange: Float = 0f
    ) {
        val current = program ?: return
        current.use()
        GLES30.glUniform2f(current.uniformLocation(U_OFFSET), offsetX, offsetY)
        GLES30.glUniform1f(current.uniformLocation(U_TIME), timeSeconds)
        GLES30.glUniform1f(current.uniformLocation(U_SWAY), sway)
        GLES30.glUniform1f(current.uniformLocation(U_ALPHA), alpha)
        GLES30.glUniform1f(current.uniformLocation(U_FADE_BASE), fadeBaseY)
        GLES30.glUniform1f(current.uniformLocation(U_FADE_RANGE), fadeRange)
        GLES30.glUniform3f(
            current.uniformLocation(U_COLOR),
            color[0],
            color[1],
            color[2]
        )
        mesh.draw()
    }

    fun release() {
        program?.delete()
        program = null
    }

    private companion object {
        const val U_OFFSET = "uOffset"
        const val U_TIME = "uTime"
        const val U_SWAY = "uSway"
        const val U_ALPHA = "uAlpha"
        const val U_COLOR = "uColor"
        const val U_FADE_BASE = "uFadeBase"
        const val U_FADE_RANGE = "uFadeRange"
    }
}
