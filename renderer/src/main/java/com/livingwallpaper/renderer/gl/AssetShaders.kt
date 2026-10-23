package com.livingwallpaper.renderer.gl

import android.content.Context
import java.io.IOException

/**
 * 从 assets 读取 GLSL 源码。
 */
object AssetShaders {

    @Throws(IOException::class)
    fun read(context: Context, path: String): String {
        context.assets.open(path).use { stream ->
            return stream.bufferedReader().readText()
        }
    }
}
