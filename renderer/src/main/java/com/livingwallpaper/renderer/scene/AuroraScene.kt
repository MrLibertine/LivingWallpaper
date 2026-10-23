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
import com.livingwallpaper.renderer.particle.SnowSystem
import com.livingwallpaper.renderer.scene.layer.FullscreenLayer
import com.livingwallpaper.renderer.scene.layer.LineLayer
import com.livingwallpaper.renderer.scene.layer.SilhouetteLayer

/**
 * Aurora Lake 极光雪山湖。
 *
 * 绘制顺序（计划书 3.5）：天空 → 远景 → 中景 → 水面 → 天气粒子。
 * 图层：极光夜空（星星 + 两道流动帘幕）→ 三层雪山剪影 → 湖面倒影
 * （极光与星点的镜像）→ 飘雪。全部为实时几何与实时粒子。
 */
class AuroraScene(private val shaders: ShaderLibrary) : Scene, ParallaxAware {

    private var config: SceneConfig = SceneConfig.DEFAULT.clamped()

    private val wind = Wind()
    private val parallax = ParallaxController()
    private val snow = SnowSystem()

    private var skyLayer: FullscreenLayer? = null
    private var mountainLayer: SilhouetteLayer? = null
    private var snowLayer: LineLayer? = null
    private var lakeProgram: ShaderProgram? = null
    private var lakeMesh: VertexMesh? = null

    private var farMountains: VertexMesh? = null
    private var midMountains: VertexMesh? = null
    private var nearMountains: VertexMesh? = null

    private var width = 1
    private var height = 1
    private var elapsedSeconds = 0f

    private var skyTop = floatArrayOf(0.016f, 0.024f, 0.055f)
    private var skyBottom = floatArrayOf(0.075f, 0.098f, 0.176f)
    private var farMountainColor = FAR_MOUNTAIN_BASE
    private var midMountainColor = MID_MOUNTAIN_BASE
    private var nearMountainColor = NEAR_MOUNTAIN_BASE
    private var lakeDeepColor = LAKE_DEEP_BASE
    private var lakeShallowColor = LAKE_SHALLOW_BASE

    private var starDensity = 0.6f
    private var auroraStrength = 0.8f
    private var cloudStrength = 0.4f
    private var auroraTint = 0.25f

    /** 三层雪山脊线参数，打包给湖面着色器做倒影：(振幅, 相位×3) × 3 层。 */
    private val ridgeParams = buildRidgeParams()

    override fun initialize() {
        skyLayer = FullscreenLayer(
            shaders.source(ShaderLibrary.FULLSCREEN_VERT),
            shaders.source(ShaderLibrary.AURORA_SKY_FRAG)
        ).also { it.initialize() }

        mountainLayer = SilhouetteLayer(
            shaders.source(ShaderLibrary.SILHOUETTE_VERT),
            shaders.source(ShaderLibrary.SILHOUETTE_FRAG)
        ).also { it.initialize() }

        snowLayer = LineLayer(
            shaders.source(ShaderLibrary.RAIN_VERT),
            shaders.source(ShaderLibrary.RAIN_FRAG),
            capacityVertices = SnowSystem.DEFAULT_CAPACITY * SnowSystem.VERTICES_PER_FLAKE
        ).also { it.initialize() }

        // 三层全宽雪山，端点在屏幕外避免生硬切边
        farMountains = mountainMesh(FAR_MOUNTAIN_SEED, FAR_MOUNTAIN_AMPLITUDE)
        midMountains = mountainMesh(MID_MOUNTAIN_SEED, MID_MOUNTAIN_AMPLITUDE)
        nearMountains = mountainMesh(NEAR_MOUNTAIN_SEED, NEAR_MOUNTAIN_AMPLITUDE)

        lakeProgram = ShaderProgram(
            shaders.source(ShaderLibrary.QUAD_VERT),
            shaders.source(ShaderLibrary.AURORA_LAKE_FRAG)
        )

        lakeMesh = VertexMesh(
            strideFloats = 2,
            attributes = listOf(VertexAttribute(location = 0, size = 2, offsetFloats = 0)),
            capacityVertices = 6
        ).also {
            val quad = floatArrayOf(
                -1f, -1f, 1f, -1f, 1f, LAKE_LINE_NDC,
                -1f, -1f, 1f, LAKE_LINE_NDC, -1f, LAKE_LINE_NDC
            )
            System.arraycopy(quad, 0, it.data, 0, quad.size)
            it.setVertexCount(6)
            it.initialize()
            it.upload()
        }

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

        // 雪量：粒子密度为主，天气额外加成（雨天＝雪势最大）
        val weatherBoost = when (clamped.weather) {
            WeatherType.RAIN -> 1.0f
            WeatherType.CLOUDY -> 0.70f
            WeatherType.CLEAR -> 0.45f
        }
        snow.intensity = (0.25f + clamped.particleDensity * 0.75f) *
            weatherBoost * clamped.effectiveParticleScale()
        snow.wind = wind.strength

        // 云越少极光越清晰
        cloudStrength = when (clamped.weather) {
            WeatherType.CLEAR -> 0.20f
            WeatherType.CLOUDY -> 0.95f
            WeatherType.RAIN -> 0.80f
        }
        auroraStrength = 0.35f + clamped.lightIntensity * 0.95f
        auroraTint = 0.15f + clamped.lightIntensity * 0.35f
        starDensity = 0.35f + clamped.particleDensity * 0.55f

        updateColors()
    }

    override fun update(deltaTimeSeconds: Float) {
        parallax.update(deltaTimeSeconds)
        val scaled = (deltaTimeSeconds * config.animationSpeed).coerceIn(0f, 0.2f)
        elapsedSeconds += scaled
        wind.update(scaled)
        snow.update(scaled)
    }

    override fun render() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)

        // 1) 极光夜空
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
            GLES30.glUniform1f(program.uniformLocation(U_AURORA_STRENGTH), auroraStrength)
            GLES30.glUniform1f(program.uniformLocation(U_CLOUD_STRENGTH), cloudStrength)
            GLES30.glUniform1f(program.uniformLocation(U_LAKE_LINE), (LAKE_LINE_NDC + 1f) * 0.5f)
        }

        // 2) 雪山剪影：山脚直接压到湖平线以下，与水里的倒影连成一体。
        //    之前山脚雾化会在交界处留一条亮带，反而把山和倒影切断。
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val silhouette = mountainLayer ?: return
        val px = parallax.offsetX
        silhouette.render(
            farMountains ?: return, px * FAR_PARALLAX, 0f, 0f, elapsedSeconds,
            farMountainColor, 0.78f
        )
        silhouette.render(
            midMountains ?: return, px * MID_PARALLAX, 0f, 0f, elapsedSeconds,
            midMountainColor, 0.85f
        )
        silhouette.render(
            nearMountains ?: return, px * NEAR_PARALLAX, 0f, 0f, elapsedSeconds,
            nearMountainColor, 1f
        )

        // 3) 湖面：不透明，覆盖山体在水平线以下的部分
        GLES30.glDisable(GLES30.GL_BLEND)
        val program = lakeProgram ?: return
        val mesh = lakeMesh ?: return
        program.use()
        GLES30.glUniform1f(program.uniformLocation(U_TIME), elapsedSeconds)
        GLES30.glUniform1f(program.uniformLocation(U_HORIZON), LAKE_LINE_NDC)
        GLES30.glUniform1f(program.uniformLocation(U_AURORA_STRENGTH), auroraStrength)
        GLES30.glUniform1f(program.uniformLocation(U_AURORA_TINT), auroraTint)
        GLES30.glUniform1f(program.uniformLocation(U_WIND), wind.strength)
        uniform3f(program, U_DEEP_COLOR, lakeDeepColor)
        uniform3f(program, U_SHALLOW_COLOR, lakeShallowColor)
        uniform3f(program, U_HORIZON_COLOR, skyBottom)
        uniform3f(program, U_MOUNTAIN_COLOR, nearMountainColor)
        GLES30.glUniform4fv(program.uniformLocation(U_RIDGE), 3, ridgeParams, 0)
        GLES30.glUniform1f(program.uniformLocation(U_RIDGE_BASE), MOUNTAIN_BASE_Y)
        GLES30.glUniform1f(program.uniformLocation(U_RIDGE_SHIFT), px * MID_PARALLAX)
        mesh.draw()

        // 4) 飘雪：加色混合让雪花在暗背景上发光
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        snowLayer?.render(snow.vertices, snow.vertexCount, SNOW_COLOR)
    }

    override fun release() {
        farMountains?.release()
        midMountains?.release()
        nearMountains?.release()
        lakeMesh?.release()
        farMountains = null
        midMountains = null
        nearMountains = null
        lakeMesh = null
        skyLayer?.release()
        mountainLayer?.release()
        snowLayer?.release()
        lakeProgram?.delete()
        skyLayer = null
        mountainLayer = null
        snowLayer = null
        lakeProgram = null
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
        skyTop = SkyColor.scale(rgb, 0.30f + 0.35f * light)
        skyBottom = SkyColor.scale(rgb, 0.95f + 0.55f * light)
        // 雪山：远山被极光映亮偏青，近山更暗更蓝；加大基色权重保证山形可辨
        farMountainColor = mix(SkyColor.scale(rgb, 1.35f), FAR_MOUNTAIN_BASE, 0.72f)
        midMountainColor = mix(SkyColor.scale(rgb, 0.95f), MID_MOUNTAIN_BASE, 0.75f)
        nearMountainColor = mix(SkyColor.scale(rgb, 0.55f), NEAR_MOUNTAIN_BASE, 0.80f)
        lakeDeepColor = mix(LAKE_DEEP_BASE, rgb, 0.25f)
        lakeShallowColor = mix(LAKE_SHALLOW_BASE, rgb, 0.30f)
    }

    private fun mountainMesh(seed: Long, amplitude: Float): VertexMesh {
        val vertices = ForestGeometry.buildMountains(
            seed = seed,
            samples = 56,
            baseY = MOUNTAIN_BASE_Y,
            amplitude = amplitude,
            xLeft = -1.35f,
            xRight = 1.35f
        )
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

    private companion object {
        private const val U_TIME = "uTime"
        private const val U_SKY_OFFSET = "uSkyOffset"
        private const val U_TOP_COLOR = "uTopColor"
        private const val U_BOTTOM_COLOR = "uBottomColor"
        private const val U_STAR_DENSITY = "uStarDensity"
        private const val U_AURORA_STRENGTH = "uAuroraStrength"
        private const val U_AURORA_TINT = "uAuroraTintG"
        private const val U_CLOUD_STRENGTH = "uCloudStrength"
        private const val U_LAKE_LINE = "uLakeLine"
        private const val U_HORIZON = "uHorizon"
        private const val U_DEEP_COLOR = "uDeepColor"
        private const val U_SHALLOW_COLOR = "uShallowColor"
        private const val U_HORIZON_COLOR = "uHorizonColor"
        private const val U_MOUNTAIN_COLOR = "uMountainColor"
        private const val U_RIDGE = "uRidge[0]"
        private const val U_RIDGE_BASE = "uRidgeBase"
        private const val U_RIDGE_SHIFT = "uRidgeShift"
        private const val U_WIND = "uWind"

        /** 湖平线（NDC y）：湖面占下方约 47%，上方留给天空与雪山。 */
        private const val LAKE_LINE_NDC = 0.06f
        // 山脚压到湖平线以下：露出的部分会被不透明湖面盖住，
        // 这样山与倒影在湖平线处严丝合缝地接上
        private const val MOUNTAIN_BASE_Y = LAKE_LINE_NDC - 0.02f

        private const val FAR_MOUNTAIN_SEED = 512019L
        private const val MID_MOUNTAIN_SEED = 881204L
        private const val NEAR_MOUNTAIN_SEED = 330617L
        private const val FAR_MOUNTAIN_AMPLITUDE = 0.55f
        private const val MID_MOUNTAIN_AMPLITUDE = 0.80f
        private const val NEAR_MOUNTAIN_AMPLITUDE = 0.45f

        private const val FAR_PARALLAX = 0.020f
        private const val MID_PARALLAX = 0.032f
        private const val NEAR_PARALLAX = 0.050f
        private const val SKY_PARALLAX = 0.018f

        // 远山只比夜空亮一档：原来太亮太均匀，读起来像一片半透明的雾
        private val FAR_MOUNTAIN_BASE = floatArrayOf(0.120f, 0.158f, 0.232f)
        private val MID_MOUNTAIN_BASE = floatArrayOf(0.092f, 0.122f, 0.190f)
        private val NEAR_MOUNTAIN_BASE = floatArrayOf(0.060f, 0.085f, 0.150f)
        // 远湖亮、近水暗：这条纵向梯度本身就是"有深度的水"的读数；
        // 同时让雪山倒影（暗）在亮带上切出形状，倒影才看得见
        private val LAKE_DEEP_BASE = floatArrayOf(0.014f, 0.022f, 0.045f)
        private val LAKE_SHALLOW_BASE = floatArrayOf(0.062f, 0.092f, 0.155f)
        private val SNOW_COLOR = floatArrayOf(0.82f, 0.90f, 1.0f)

        private fun buildRidgeParams(): FloatArray {
            val seeds = longArrayOf(FAR_MOUNTAIN_SEED, MID_MOUNTAIN_SEED, NEAR_MOUNTAIN_SEED)
            val amps = floatArrayOf(
                FAR_MOUNTAIN_AMPLITUDE,
                MID_MOUNTAIN_AMPLITUDE,
                NEAR_MOUNTAIN_AMPLITUDE
            )
            val out = FloatArray(12)
            for (i in 0..2) {
                val phases = ForestGeometry.mountainPhases(seeds[i])
                out[i * 4] = amps[i]
                out[i * 4 + 1] = phases[0]
                out[i * 4 + 2] = phases[1]
                out[i * 4 + 3] = phases[2]
            }
            return out
        }
    }
}
