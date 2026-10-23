package com.livingwallpaper.renderer.gl

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 着色器源码缓存。assets 读取发生在调用线程（通常为主线程），
 * 文件均为 1KB 量级且只读取一次，之后缓存复用。
 */
class ShaderLibrary(private val context: Context) {

    private val cache = ConcurrentHashMap<String, String>()

    fun source(path: String): String = cache.getOrPut(path) {
        AssetShaders.read(context, path)
    }

    companion object {
        const val FULLSCREEN_VERT = "shaders/gradient.vert"
        const val GRADIENT_FRAG = "shaders/gradient.frag"
        const val SILHOUETTE_VERT = "shaders/silhouette.vert"
        const val SILHOUETTE_FRAG = "shaders/silhouette.frag"
        const val RAIN_VERT = "shaders/rain.vert"
        const val RAIN_FRAG = "shaders/rain.frag"
        const val FIREFLY_VERT = "shaders/firefly.vert"
        const val FIREFLY_FRAG = "shaders/firefly.frag"
        const val QUAD_VERT = "shaders/quad.vert"
        const val AURORA_SKY_FRAG = "shaders/aurora_sky.frag"
        const val AURORA_LAKE_FRAG = "shaders/aurora_lake.frag"
        const val STAR_SKY_FRAG = "shaders/star_sky.frag"
    }
}
