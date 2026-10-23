package com.livingwallpaper.renderer.gl

import android.opengl.GLES30
import android.util.Log

/**
 * GLSL ES 3.0 着色器程序的编译/链接封装。
 *
 * 着色器源码由调用方传入（通常来自 assets），本类不关心来源。
 * 编译或链接失败时抛出 [IllegalStateException]，由渲染循环捕获并记录，
 * 不静默降级，避免“看起来渲染了其实没有画面”。
 */
class ShaderProgram(vertexSource: String, fragmentSource: String) {

    private var programId: Int = 0
    private val uniformCache = HashMap<String, Int>()

    init {
        val vertexShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        try {
            programId = GLES30.glCreateProgram()
            if (programId == 0) {
                throw IllegalStateException("glCreateProgram 失败")
            }
            GLES30.glAttachShader(programId, vertexShader)
            GLES30.glAttachShader(programId, fragmentShader)
            GLES30.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES30.GL_TRUE) {
                throw IllegalStateException(
                    "链接着色器程序失败：${GLES30.glGetProgramInfoLog(programId)}"
                )
            }
        } finally {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
        }
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    /** 带缓存的 uniform location 查询；缺失时返回 -1，调用方按 GLES 规范忽略即可。 */
    fun uniformLocation(name: String): Int {
        uniformCache[name]?.let { return it }
        val location = GLES30.glGetUniformLocation(programId, name)
        uniformCache[name] = location
        return location
    }

    fun delete() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        uniformCache.clear()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw IllegalStateException("glCreateShader 失败（type=$type）")
        }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val kind = if (type == GLES30.GL_VERTEX_SHADER) "顶点" else "片元"
            throw IllegalStateException("${kind}着色器编译失败：$log")
        }
        Log.d(TAG, "着色器编译成功（type=$type）")
        return shader
    }

    companion object {
        private const val TAG = "ShaderProgram"
    }
}
