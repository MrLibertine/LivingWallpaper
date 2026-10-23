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
import com.livingwallpaper.renderer.gl.VertexAttribute
import com.livingwallpaper.renderer.gl.VertexMesh
import com.livingwallpaper.renderer.particle.FireflySystem
import com.livingwallpaper.renderer.particle.RainSystem
import com.livingwallpaper.renderer.scene.layer.FireflyLayer
import com.livingwallpaper.renderer.scene.layer.FullscreenLayer
import com.livingwallpaper.renderer.scene.layer.LineLayer
import com.livingwallpaper.renderer.scene.layer.SilhouetteLayer

/**
 * Forest Night 森林雨夜。
 *
 * 绘制顺序（计划书 3.5）：天空背景 → 远景 → 中景 → 天气/粒子 → 前景。
 * 所有图层都是实时几何 + 实时粒子，没有静态贴图或视频。
 */
class ForestScene(private val shaders: ShaderLibrary) : Scene, ParallaxAware {

    private var config: SceneConfig = SceneConfig.DEFAULT.clamped()

    private val wind = Wind()
    private val parallax = ParallaxController()
    private val rain = RainSystem()
    private val fireflies = FireflySystem()

    private var skyLayer: FullscreenLayer? = null
    private var silhouetteLayer: SilhouetteLayer? = null
    private var rainLayer: LineLayer? = null
    private var fireflyLayer: FireflyLayer? = null

    private var mountainMesh: VertexMesh? = null
    private var farTreeMesh: VertexMesh? = null
    private var midTreeMesh: VertexMesh? = null
    private var nearTreeMesh: VertexMesh? = null

    private var width = 1
    private var height = 1
    private var elapsedSeconds = 0f

    private var skyTop = floatArrayOf(0.043f, 0.086f, 0.149f)
    private var skyBottom = floatArrayOf(0.122f, 0.227f, 0.302f)
    private var skyAccent = floatArrayOf(0.612f, 0.769f, 1.0f)

    private var mountainColor = MOUNTAIN_BASE
    private var farTreeColor = FAR_TREE_BASE
    private var midTreeColor = MID_TREE_BASE
    private var nearTreeColor = NEAR_TREE_BASE
    private var rainColor = RAIN_BASE
    private var fireflyColor = FIREFLY_BASE

    override fun initialize() {
        skyLayer = FullscreenLayer(
            shaders.source(ShaderLibrary.FULLSCREEN_VERT),
            shaders.source(ShaderLibrary.GRADIENT_FRAG)
        ).also { it.initialize() }

        silhouetteLayer = SilhouetteLayer(
            shaders.source(ShaderLibrary.SILHOUETTE_VERT),
            shaders.source(ShaderLibrary.SILHOUETTE_FRAG)
        ).also { it.initialize() }

        rainLayer = LineLayer(
            shaders.source(ShaderLibrary.RAIN_VERT),
            shaders.source(ShaderLibrary.RAIN_FRAG),
            capacityVertices = RainSystem.DEFAULT_CAPACITY * RainSystem.VERTICES_PER_DROP
        ).also { it.initialize() }

        fireflyLayer = FireflyLayer(
            shaders.source(ShaderLibrary.FIREFLY_VERT),
            shaders.source(ShaderLibrary.FIREFLY_FRAG)
        ).also { it.initialize() }

        mountainMesh = meshFrom(ForestGeometry.buildMountains(MOUNTAIN_SEED))
        farTreeMesh = meshFrom(
            ForestGeometry.buildTrees(FAR_TREE_SEED, 22, -0.16f, 0.28f, 0.44f, 0.09f, 0.15f)
        )
        midTreeMesh = meshFrom(
            ForestGeometry.buildTrees(MID_TREE_SEED, 15, -0.30f, 0.46f, 0.70f, 0.13f, 0.21f)
        )
        nearTreeMesh = meshFrom(
            ForestGeometry.buildTrees(NEAR_TREE_SEED, 8, -0.58f, 0.78f, 1.18f, 0.19f, 0.31f)
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

        // 性能档位与“减少动态效果”真实地改变粒子数量与摆动幅度
        val particleScale = clamped.effectiveParticleScale()
        wind.strength = clamped.windStrength * clamped.effectiveMotionScale()
        parallax.strength = clamped.parallaxStrength

        // 只有雨天才有雨；切换天气配置即生效
        rain.intensity =
            if (clamped.weather == WeatherType.RAIN) clamped.rainIntensity * particleScale else 0f
        rain.wind = wind.strength
        fireflies.density = clamped.particleDensity * particleScale
        fireflies.wind = wind.strength

        updateColors()
    }

    override fun update(deltaTimeSeconds: Float) {
        // 视差用真实时间推进；动画速度只影响场景内的运动
        parallax.update(deltaTimeSeconds)

        val scaled = (deltaTimeSeconds * config.animationSpeed).coerceIn(0f, 0.2f)
        elapsedSeconds += scaled
        wind.update(scaled)
        rain.update(scaled)
        fireflies.update(scaled)
    }

    override fun render() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        // 1) 天空：不透明
        GLES30.glDisable(GLES30.GL_BLEND)
        skyLayer?.renderGradient(elapsedSeconds, width, height, skyTop, skyBottom, skyAccent)

        // 2) 剪影与雨滴：普通 alpha 混合
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        val sway = wind.swayAmplitude
        val offsetX = parallax.offsetX
        val offsetY = parallax.offsetY
        val silhouette = silhouetteLayer ?: return

        silhouette.render(
            mountainMesh ?: return,
            offsetX * MOUNTAIN_PARALLAX,
            offsetY * MOUNTAIN_PARALLAX * VERTICAL_PARALLAX_RATIO,
            0f,
            elapsedSeconds,
            mountainColor,
            1f
        )
        silhouette.render(
            farTreeMesh ?: return,
            offsetX * FAR_PARALLAX,
            offsetY * FAR_PARALLAX * VERTICAL_PARALLAX_RATIO,
            sway * 0.55f,
            elapsedSeconds,
            farTreeColor,
            1f
        )
        silhouette.render(
            midTreeMesh ?: return,
            offsetX * MID_PARALLAX,
            offsetY * MID_PARALLAX * VERTICAL_PARALLAX_RATIO,
            sway * 0.85f,
            elapsedSeconds,
            midTreeColor,
            1f
        )

        // 3) 雨滴
        rainLayer?.render(rain.vertices, rain.vertexCount, rainColor)

        // 4) 萤火虫：加色混合，让光晕叠加更自然
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        fireflyLayer?.render(fireflies, elapsedSeconds, fireflyColor)

        // 5) 前景树
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        silhouette.render(
            nearTreeMesh ?: return,
            offsetX * NEAR_PARALLAX,
            offsetY * NEAR_PARALLAX * VERTICAL_PARALLAX_RATIO,
            sway,
            elapsedSeconds,
            nearTreeColor,
            1f
        )
    }

    override fun release() {
        mountainMesh?.release()
        farTreeMesh?.release()
        midTreeMesh?.release()
        nearTreeMesh?.release()
        mountainMesh = null
        farTreeMesh = null
        midTreeMesh = null
        nearTreeMesh = null

        skyLayer?.release()
        silhouetteLayer?.release()
        rainLayer?.release()
        fireflyLayer?.release()
        skyLayer = null
        silhouetteLayer = null
        rainLayer = null
        fireflyLayer = null
    }

    override fun setParallaxEnabled(enabled: Boolean) {
        parallax.enabled = enabled
        if (!enabled) {
            parallax.reset()
        }
    }

    override fun setTilt(tiltX: Float, tiltY: Float) {
        parallax.setTilt(tiltX, tiltY)
    }

    private fun updateColors() {
        val rgb = config.skyRgb()
        val light = config.lightIntensity

        skyTop = SkyColor.scale(rgb, 0.42f + 0.55f * light)
        skyBottom = SkyColor.scale(rgb, 1.02f + 0.55f * light)
        skyAccent = SkyColor.scale(rgb, 1.8f)

        mountainColor = mix(SkyColor.scale(rgb, 0.95f), MOUNTAIN_BASE, 0.45f)
        farTreeColor = scale(FAR_TREE_BASE, 0.75f + 0.55f * light)
        midTreeColor = scale(MID_TREE_BASE, 0.75f + 0.55f * light)
        nearTreeColor = scale(NEAR_TREE_BASE, 0.75f + 0.55f * light)
        rainColor = RAIN_BASE
        fireflyColor = FIREFLY_BASE
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

    private fun scale(color: FloatArray, factor: Float): FloatArray =
        floatArrayOf(
            (color[0] * factor).coerceIn(0f, 1f),
            (color[1] * factor).coerceIn(0f, 1f),
            (color[2] * factor).coerceIn(0f, 1f)
        )

    private fun mix(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        floatArrayOf(
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        )

    companion object {
        private const val MOUNTAIN_SEED = 910417L
        private const val FAR_TREE_SEED = 20241022L
        private const val MID_TREE_SEED = 330615L
        private const val NEAR_TREE_SEED = 771102L

        private const val MOUNTAIN_PARALLAX = 0.020f
        private const val FAR_PARALLAX = 0.035f
        private const val MID_PARALLAX = 0.055f
        private const val NEAR_PARALLAX = 0.085f
        private const val VERTICAL_PARALLAX_RATIO = 0.5f

        private val MOUNTAIN_BASE = floatArrayOf(0.098f, 0.129f, 0.176f)
        private val FAR_TREE_BASE = floatArrayOf(0.055f, 0.085f, 0.098f)
        private val MID_TREE_BASE = floatArrayOf(0.031f, 0.051f, 0.055f)
        private val NEAR_TREE_BASE = floatArrayOf(0.012f, 0.020f, 0.024f)
        private val RAIN_BASE = floatArrayOf(0.72f, 0.82f, 0.95f)
        private val FIREFLY_BASE = floatArrayOf(1.0f, 0.86f, 0.45f)
    }
}
