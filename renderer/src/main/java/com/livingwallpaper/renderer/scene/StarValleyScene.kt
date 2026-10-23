package com.livingwallpaper.renderer.scene

import android.opengl.GLES30
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SkyColor
import com.livingwallpaper.core.model.WeatherType
import com.livingwallpaper.renderer.animation.ParallaxAware
import com.livingwallpaper.renderer.animation.ParallaxController
import com.livingwallpaper.renderer.animation.Wind
import com.livingwallpaper.renderer.geometry.ForestGeometry
import com.livingwallpaper.renderer.gl.ShaderLibrary
import com.livingwallpaper.renderer.gl.ShaderProgram
import com.livingwallpaper.renderer.gl.VertexAttribute
import com.livingwallpaper.renderer.gl.VertexMesh
import com.livingwallpaper.renderer.particle.MeteorSystem
import com.livingwallpaper.renderer.particle.RainSystem
import com.livingwallpaper.renderer.scene.layer.FullscreenLayer
import com.livingwallpaper.renderer.scene.layer.LineLayer
import com.livingwallpaper.renderer.scene.layer.SilhouetteLayer

/**
 * Star Valley 星空山谷。
 *
 * 图层：星空天空（星星闪烁 + 缓慢云层）→ 远山 → 近山 → 流星 → 雨天时的雨滴。
 * 流星与雨滴共用同一个线段渲染层，只是粒子系统不同。
 */
class StarValleyScene(private val shaders: ShaderLibrary) : Scene, ParallaxAware {

    private var config: SceneConfig = SceneConfig.DEFAULT.clamped()

    private val wind = Wind()
    private val parallax = ParallaxController()
    private val meteors = MeteorSystem()
    private val rain = RainSystem()

    private var skyLayer: FullscreenLayer? = null
    private var silhouetteLayer: SilhouetteLayer? = null
    private var meteorLayer: LineLayer? = null
    private var rainLayer: LineLayer? = null

    private var farMountains: VertexMesh? = null
    private var nearMountains: VertexMesh? = null

    private var width = 1
    private var height = 1
    private var elapsedSeconds = 0f

    private var skyTop = floatArrayOf(0.016f, 0.024f, 0.055f)
    private var skyBottom = floatArrayOf(0.075f, 0.098f, 0.176f)
    private var farMountainColor = floatArrayOf(0.086f, 0.106f, 0.165f)
    private var nearMountainColor = floatArrayOf(0.035f, 0.047f, 0.078f)

    private var starDensity = 0.6f
    private var cloudStrength = 0.5f

    override fun initialize() {
        skyLayer = FullscreenLayer(
            shaders.source(ShaderLibrary.FULLSCREEN_VERT),
            shaders.source(ShaderLibrary.STAR_SKY_FRAG)
        ).also { it.initialize() }

        silhouetteLayer = SilhouetteLayer(
            shaders.source(ShaderLibrary.SILHOUETTE_VERT),
            shaders.source(ShaderLibrary.SILHOUETTE_FRAG)
        ).also { it.initialize() }

        meteorLayer = LineLayer(
            shaders.source(ShaderLibrary.RAIN_VERT),
            shaders.source(ShaderLibrary.RAIN_FRAG),
            capacityVertices = MeteorSystem.DEFAULT_CAPACITY * MeteorSystem.VERTICES_PER_METEOR
        ).also { it.initialize() }

        rainLayer = LineLayer(
            shaders.source(ShaderLibrary.RAIN_VERT),
            shaders.source(ShaderLibrary.RAIN_FRAG),
            capacityVertices = RainSystem.DEFAULT_CAPACITY * RainSystem.VERTICES_PER_DROP
        ).also { it.initialize() }

        farMountains = meshFrom(
            ForestGeometry.buildMountains(FAR_MOUNTAIN_SEED, baseY = -0.10f, amplitude = 1.15f)
        )
        nearMountains = meshFrom(
            ForestGeometry.buildMountains(NEAR_MOUNTAIN_SEED, baseY = -0.34f, amplitude = 0.85f)
        )

        applyConfig(config)
    }

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    override fun applyConfig(config: SceneConfig) {
        val clamped = config.clamped()
        this.config = clamped

        val particleScale = clamped.effectiveParticleScale()
        wind.strength = clamped.windStrength * clamped.effectiveMotionScale()
        parallax.strength = clamped.parallaxStrength

        meteors.density = clamped.particleDensity * particleScale
        meteors.wind = wind.strength
        rain.intensity =
            if (clamped.weather == WeatherType.RAIN) clamped.rainIntensity * particleScale else 0f
        rain.wind = wind.strength

        starDensity = 0.35f + clamped.particleDensity * 0.65f
        cloudStrength = when (clamped.weather) {
            WeatherType.CLEAR -> 0.25f
            WeatherType.CLOUDY -> 0.90f
            WeatherType.RAIN -> 0.80f
        }

        updateColors()
    }

    override fun update(deltaTimeSeconds: Float) {
        parallax.update(deltaTimeSeconds)
        val scaled = (deltaTimeSeconds * config.animationSpeed).coerceIn(0f, 0.2f)
        elapsedSeconds += scaled
        wind.update(scaled)
        meteors.update(scaled)
        rain.update(scaled)
    }

    override fun render() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        // 1) 星空天空
        skyLayer?.render(width, height) { program ->
            GLES30.glUniform1f(program.uniformLocation(U_TIME), elapsedSeconds)
            GLES30.glUniform2f(
                program.uniformLocation(U_SKY_OFFSET),
                parallax.offsetX * SKY_PARALLAX,
                parallax.offsetY * SKY_PARALLAX * 0.5f
            )
            uniform3f(program, U_TOP_COLOR, skyTop)
            uniform3f(program, U_BOTTOM_COLOR, skyBottom)
            GLES30.glUniform1f(program.uniformLocation(U_STAR_DENSITY), starDensity)
            GLES30.glUniform1f(program.uniformLocation(U_CLOUD_STRENGTH), cloudStrength)
        }

        // 2) 山体剪影
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val silhouette = silhouetteLayer ?: return
        val offsetX = parallax.offsetX
        val offsetY = parallax.offsetY
        silhouette.render(
            farMountains ?: return,
            offsetX * FAR_PARALLAX,
            offsetY * FAR_PARALLAX * 0.5f,
            0f,
            elapsedSeconds,
            farMountainColor,
            1f
        )
        silhouette.render(
            nearMountains ?: return,
            offsetX * NEAR_PARALLAX,
            offsetY * NEAR_PARALLAX * 0.5f,
            0f,
            elapsedSeconds,
            nearMountainColor,
            1f
        )

        // 3) 流星：加色混合，拖尾更接近真实发光
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        meteorLayer?.render(meteors.vertices, meteors.vertexCount, METEOR_COLOR)

        // 4) 雨天
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        rainLayer?.render(rain.vertices, rain.vertexCount, RAIN_COLOR)
    }

    override fun release() {
        farMountains?.release()
        nearMountains?.release()
        farMountains = null
        nearMountains = null
        skyLayer?.release()
        silhouetteLayer?.release()
        meteorLayer?.release()
        rainLayer?.release()
        skyLayer = null
        silhouetteLayer = null
        meteorLayer = null
        rainLayer = null
    }

    override fun setParallaxEnabled(enabled: Boolean) {
        parallax.enabled = enabled
        if (!enabled) parallax.reset()
    }

    override fun setTilt(tiltX: Float, tiltY: Float) {
        parallax.setTilt(tiltX, tiltY)
    }

    private fun updateColors() {
        val rgb = config.skyRgb()
        val light = config.lightIntensity
        skyTop = SkyColor.scale(rgb, 0.35f + 0.45f * light)
        skyBottom = SkyColor.scale(rgb, 0.95f + 0.55f * light)
        farMountainColor = mix(SkyColor.scale(rgb, 1.25f), FAR_MOUNTAIN_BASE, 0.55f)
        nearMountainColor = mix(SkyColor.scale(rgb, 0.55f), NEAR_MOUNTAIN_BASE, 0.70f)
    }

    private fun meshFrom(vertices: FloatArray): VertexMesh {
        val mesh = VertexMesh(
            strideFloats = ForestGeometry.STRIDE_FLOATS,
            attributes = listOf(
                VertexAttribute(location = 0, size = 2, offsetFloats = 0),
                VertexAttribute(location = 1, size = 1, offsetFloats = 2)
            ),
            capacityVertices = vertices.size / ForestGeometry.STRIDE_FLOATS
        )
        System.arraycopy(vertices, 0, mesh.data, 0, vertices.size)
        mesh.setVertexCount(vertices.size / ForestGeometry.STRIDE_FLOATS)
        mesh.initialize()
        mesh.upload()
        return mesh
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

    companion object {
        private const val U_TIME = "uTime"
        private const val U_SKY_OFFSET = "uSkyOffset"
        private const val U_TOP_COLOR = "uTopColor"
        private const val U_BOTTOM_COLOR = "uBottomColor"
        private const val U_STAR_DENSITY = "uStarDensity"
        private const val U_CLOUD_STRENGTH = "uCloudStrength"

        private const val FAR_MOUNTAIN_SEED = 512019L
        private const val NEAR_MOUNTAIN_SEED = 881204L

        private const val FAR_PARALLAX = 0.022f
        private const val NEAR_PARALLAX = 0.048f
        private const val SKY_PARALLAX = 0.018f

        private val FAR_MOUNTAIN_BASE = floatArrayOf(0.086f, 0.106f, 0.165f)
        private val NEAR_MOUNTAIN_BASE = floatArrayOf(0.035f, 0.047f, 0.078f)
        private val METEOR_COLOR = floatArrayOf(1.0f, 0.96f, 0.85f)
        private val RAIN_COLOR = floatArrayOf(0.68f, 0.78f, 0.95f)
    }
}
