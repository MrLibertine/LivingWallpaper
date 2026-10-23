package com.livingwallpaper.renderer.scene

import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SkyColor
import com.livingwallpaper.renderer.gl.ShaderLibrary
import com.livingwallpaper.renderer.scene.layer.FullscreenLayer

/**
 * P2 最小可验证场景：全屏实时动态渐变。
 *
 * 它存在的目的是证明“Surface 上的画面由 GPU 逐帧计算”：
 * 颜色完全由片元着色器根据 uTime 计算，没有任何贴图或视频。
 *
 * P3 起森林雨夜场景在内部结构上使用同一套渐变层作为天空背景。
 */
class GradientScene(
    vertexSource: String,
    fragmentSource: String
) : Scene {

    private val layer = FullscreenLayer(vertexSource, fragmentSource)

    private var config = SceneConfig.DEFAULT.clamped()
    private var width = 1
    private var height = 1
    private var elapsedSeconds = 0f

    private var topColor = TOP_COLOR
    private var bottomColor = BOTTOM_COLOR
    private var accentColor = ACCENT_COLOR

    override fun initialize() {
        layer.initialize()
        applyConfig(config)
    }

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    override fun applyConfig(config: SceneConfig) {
        val clamped = config.clamped()
        this.config = clamped
        val rgb = clamped.skyRgb()
        topColor = SkyColor.scale(rgb, 0.45f + 0.55f * clamped.lightIntensity)
        bottomColor = SkyColor.scale(rgb, 1.05f + 0.55f * clamped.lightIntensity)
        accentColor = SkyColor.scale(rgb, 1.8f)
    }

    override fun update(deltaTimeSeconds: Float) {
        elapsedSeconds += (deltaTimeSeconds * config.animationSpeed).coerceIn(0f, 0.2f)
    }

    override fun render() {
        layer.renderGradient(elapsedSeconds, width, height, topColor, bottomColor, accentColor)
    }

    override fun release() {
        layer.release()
    }

    companion object {
        private val TOP_COLOR = floatArrayOf(0.043f, 0.086f, 0.149f) // #0B1626
        private val BOTTOM_COLOR = floatArrayOf(0.122f, 0.227f, 0.302f) // #1F3A4D
        private val ACCENT_COLOR = floatArrayOf(0.612f, 0.769f, 1.0f) // #9CC4FF

        fun create(shaders: ShaderLibrary): GradientScene = GradientScene(
            shaders.source(ShaderLibrary.FULLSCREEN_VERT),
            shaders.source(ShaderLibrary.GRADIENT_FRAG)
        )
    }
}
