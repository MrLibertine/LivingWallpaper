package com.livingwallpaper.renderer.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * 从 assets 读取 GLSL 源码与贴图。
 */
object AssetShaders {

    @Throws(IOException::class)
    fun read(context: Context, path: String): String {
        context.assets.open(path).use { stream ->
            return stream.bufferedReader().readText()
        }
    }

    /** 解码 assets 里的图片；解不出来时抛 [IOException]，由调用方决定降级方式。 */
    @Throws(IOException::class)
    fun readBitmap(context: Context, path: String): Bitmap {
        context.assets.open(path).use { stream ->
            return BitmapFactory.decodeStream(stream)
                ?: throw IOException("无法解码贴图：$path")
        }
    }
}
