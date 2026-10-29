package com.livingwallpaper.renderer.gl

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 着色器源码与贴图缓存。assets 读取发生在调用线程（通常为 GL 线程首次渲染前），
 * 文件都不大且只读一次，之后缓存复用。
 */
class ShaderLibrary(private val context: Context) {

    private val cache = ConcurrentHashMap<String, String>()
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()

    fun source(path: String): String = cache.getOrPut(path) {
        AssetShaders.read(context, path)
    }

    /** 读取贴图。解码失败返回 null（不缓存失败），由场景决定如何降级。 */
    fun bitmap(path: String): Bitmap? {
        bitmaps[path]?.let { return it }
        val loaded = try {
            AssetShaders.readBitmap(context, path)
        } catch (e: Exception) {
            null
        } ?: return null
        bitmaps[path] = loaded
        return loaded
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

        /** 晴空草地：隔着挂满雨水的玻璃看外面的草地与天空。 */
        const val MEADOW_FRAG = "shaders/meadow_glass.frag"

        /** 晴空草地的底图：一张照片，玻璃上的水盖在它前面。 */
        const val MEADOW_BASE = "textures/meadow_bg.jpg"
    }
}
