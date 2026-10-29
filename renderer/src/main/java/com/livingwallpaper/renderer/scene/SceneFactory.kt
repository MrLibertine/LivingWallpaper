package com.livingwallpaper.renderer.scene

import android.content.Context
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.renderer.gl.ShaderLibrary

/**
 * 场景构造入口。App 内预览与系统壁纸共用同一份实现，
 * 但各自持有独立的 EGL Surface 与 GL 线程（不共享 GL 对象）。
 *
 * 读取 assets 发生在调用线程（主线程），文件只有 1KB 量级；
 * 真正的 GL 资源创建全部发生在渲染线程。
 */
object SceneFactory {

    fun createGradient(context: Context): Scene = GradientScene.create(ShaderLibrary(context))

    fun createForest(context: Context): Scene = ForestScene(ShaderLibrary(context))

    fun createAurora(context: Context): Scene = AuroraScene(ShaderLibrary(context))

    fun createStarValley(context: Context): Scene = StarValleyScene(ShaderLibrary(context))

    fun createCityDusk(context: Context): Scene = CityDuskScene(ShaderLibrary(context))

    fun create(type: SceneType, context: Context): Scene = when (type) {
        SceneType.FOREST -> createForest(context)
        SceneType.AURORA -> createAurora(context)
        SceneType.STAR_VALLEY -> createStarValley(context)
        SceneType.CITY_DUSK -> createCityDusk(context)
    }

    /** 当前默认场景（P3 起为森林雨夜）。 */
    fun createDefault(context: Context): Scene = createForest(context)
}
