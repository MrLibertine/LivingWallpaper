package com.livingwallpaper.renderer.scene

import android.opengl.GLES30
import android.opengl.GLUtils
import android.graphics.Bitmap
import android.util.Log
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SkyColor
import com.livingwallpaper.core.model.WeatherType
import com.livingwallpaper.renderer.animation.ParallaxAware
import com.livingwallpaper.renderer.animation.ParallaxController
import com.livingwallpaper.renderer.animation.Wind
import com.livingwallpaper.renderer.gl.ShaderLibrary
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.scene.layer.FullscreenLayer

/**
 * City Dusk 城市暮雨。
 *
 * 底图是一张照片（assets/textures/rain_glass_bg.jpg），画面颜色全部由它决定；
 * 着色器只负责在照片前面加一层挂满雨水的玻璃：
 *
 * 1. 大水滴往下爬，头上一枚凸透镜把照片揉弯，身后拖一条水痕；
 * 2. 一层不动的细水珠，靠亮边和珠身的明暗被认出来；
 * 3. 细亮的雨丝往下落。
 *
 * 贴图在 GL 线程首次 initialize 时上传，mipmap 由 GL 生成；
 * 片元着色器只做一次采样，所有折射都靠在采样点上做偏移。
 */
class CityDuskScene(private val shaders: ShaderLibrary) : Scene, ParallaxAware {

    private var config: SceneConfig = SceneConfig.DEFAULT.clamped()

    private val wind = Wind()
    private val parallax = ParallaxController()

    private var glassLayer: FullscreenLayer? = null

    private var width = 1
    private var height = 1
    private var elapsedSeconds = 0f

    private var baseTexture = 0
    private var baseWidth = 1
    private var baseHeight = 1

    private var glowColor = floatArrayOf(1.0f, 0.62f, 0.26f)
    private var skyTop = floatArrayOf(0.05f, 0.10f, 0.15f)

    private var rainWetness = 0.5f
    private var lightAmount = 0.5f
    private var hazeAmount = 0.5f

    override fun initialize() {
        glassLayer = FullscreenLayer(
            shaders.source(ShaderLibrary.FULLSCREEN_VERT),
            shaders.source(ShaderLibrary.CITY_DUSK_FRAG)
        ).also { it.initialize() }

        baseTexture = uploadBaseTexture()
        applyConfig(config)
    }

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    override fun applyConfig(config: SceneConfig) {
        val clamped = config.clamped()
        this.config = clamped

        wind.strength = clamped.windStrength * clamped.effectiveMotionScale()
        parallax.strength = clamped.parallaxStrength

        // 雨量直接决定玻璃上有多少水珠，这是这个场景唯一的"天气"表达
        val weatherWet = when (clamped.weather) {
            WeatherType.RAIN -> 1.0f
            WeatherType.CLOUDY -> 0.55f
            WeatherType.CLEAR -> 0.25f
        }
        rainWetness = (0.25f + clamped.rainIntensity * 0.75f) *
            weatherWet * clamped.effectiveMotionScale()
        if (clamped.reduceMotion) rainWetness *= 0.6f

        lightAmount = clamped.lightIntensity
        hazeAmount = when (clamped.weather) {
            WeatherType.RAIN -> 0.85f
            WeatherType.CLOUDY -> 0.55f
            WeatherType.CLEAR -> 0.25f
        }

        updateColors()
    }

    override fun update(deltaTimeSeconds: Float) {
        parallax.update(deltaTimeSeconds)
        val scaled = (deltaTimeSeconds * config.animationSpeed).coerceIn(0f, 0.2f)
        elapsedSeconds += scaled
        wind.update(scaled)
    }

    override fun render() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, baseTexture)

        glassLayer?.render(width, height) { program ->
            GLES30.glUniform1f(program.uniformLocation(U_TIME), elapsedSeconds)
            GLES30.glUniform2f(
                program.uniformLocation(U_OFFSET),
                parallax.offsetX,
                parallax.offsetY
            )
            GLES30.glUniform1i(program.uniformLocation(U_BASE), 0)
            GLES30.glUniform2f(
                program.uniformLocation(U_BASE_SIZE),
                baseWidth.toFloat(),
                baseHeight.toFloat()
            )
            uniform3f(program, U_SKY_TOP, skyTop)
            uniform3f(program, U_GLOW_COLOR, glowColor)
            GLES30.glUniform1f(program.uniformLocation(U_LIGHT), lightAmount)
            GLES30.glUniform1f(program.uniformLocation(U_RAIN), rainWetness)
            GLES30.glUniform1f(program.uniformLocation(U_WIND), wind.strength)
            GLES30.glUniform1f(program.uniformLocation(U_HAZE), hazeAmount)
        }
    }

    override fun release() {
        glassLayer?.release()
        glassLayer = null
        if (baseTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(baseTexture), 0)
            baseTexture = 0
        }
    }

    override fun setParallaxEnabled(enabled: Boolean) {
        parallax.enabled = enabled
        if (!enabled) parallax.reset()
    }

    override fun setTilt(tiltX: Float, tiltY: Float) {
        parallax.setTilt(tiltX, tiltY)
    }

    /** 上传底图。读不到就退化为纯黑底（不崩，只是画面没有内容）。 */
    private fun uploadBaseTexture(): Int {
        val bitmap: Bitmap? = shaders.bitmap(ShaderLibrary.CITY_DUSK_BASE)
        if (bitmap == null) {
            Log.w(TAG, "底图缺失：${ShaderLibrary.CITY_DUSK_BASE}")
            return 0
        }
        baseWidth = bitmap.width
        baseHeight = bitmap.height

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        // 照片是屏幕尺寸量级，缩小时必须走 mipmap，否则远处的草叶会闪
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        // 上传完就释放：屏幕分辨率级的照片常驻内存没有必要
        bitmap.recycle()
        return ids[0]
    }

    private fun updateColors() {
        val rgb = config.skyRgb()
        val light = config.lightIntensity

        // 灯光的暖色是这个场景的骨架，只让天空色轻微参与
        glowColor = mix(GLOW_BASE, rgb, 0.18f)

        // 玻璃上方反着的天光
        skyTop = mix(SkyColor.scale(rgb, 0.55f + 0.35f * light), SKY_TOP_BASE, 0.55f)
    }

    private fun uniform3f(program: ShaderProgram, name: String, value: FloatArray) {
        GLES30.glUniform3f(program.uniformLocation(name), value[0], value[1], value[2])
    }

    private fun mix(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        floatArrayOf(
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        )

    private companion object {
        private const val TAG = "CityDuskScene"
        private const val U_TIME = "uTime"
        private const val U_OFFSET = "uOffset"
        private const val U_BASE = "uBase"
        private const val U_BASE_SIZE = "uBaseSize"
        private const val U_SKY_TOP = "uSkyTop"
        private const val U_GLOW_COLOR = "uGlowColor"
        private const val U_LIGHT = "uLight"
        private const val U_RAIN = "uRain"
        private const val U_WIND = "uWind"
        private const val U_HAZE = "uHaze"

        private val GLOW_BASE = floatArrayOf(1.0f, 0.62f, 0.26f)
        private val SKY_TOP_BASE = floatArrayOf(0.10f, 0.20f, 0.28f)
    }
}
