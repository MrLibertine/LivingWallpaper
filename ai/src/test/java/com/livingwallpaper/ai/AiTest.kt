package com.livingwallpaper.ai.parser

import com.livingwallpaper.ai.provider.RuleBasedProvider

import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedInstructionParserTest {

    private val parser = RuleBasedInstructionParser()
    private val base = SceneConfig.DEFAULT.clamped()

    @Test
    fun blankInputIsNotMatched() {
        val result = parser.parse("   ", base)
        assertFalse(result.matched)
        assertTrue(result.explanations.isNotEmpty())
    }

    @Test
    fun unrelatedSentenceIsNotMatched() {
        val result = parser.parse("今天天气不错", base)
        assertFalse("不应把无关句子误判为命中", result.matched)
    }

    @Test
    fun switchToStarValley() {
        val result = parser.parse("切换到星空山谷", base)
        assertTrue(result.matched)
        assertEquals(SceneType.STAR_VALLEY, result.patch.apply(base).sceneType)
    }

    @Test
    fun switchToAuroraUsesDefaultSceneId() {
        val result = parser.parse("我想看极光", base)
        val applied = result.patch.apply(base)
        assertEquals(SceneType.AURORA, applied.sceneType)
        assertEquals("aurora-lake", applied.sceneId)
    }

    @Test
    fun rainStronger() {
        val result = parser.parse("雨大一点", base)
        val applied = result.patch.apply(base)
        assertEquals(base.rainIntensity + 0.2f, applied.rainIntensity, 0.0001f)
    }

    @Test
    fun rainWeaker() {
        val result = parser.parse("雨小一点", base)
        val applied = result.patch.apply(base)
        assertEquals(base.rainIntensity - 0.2f, applied.rainIntensity, 0.0001f)
    }

    @Test
    fun rainStoppedUsesAbsoluteZero() {
        val result = parser.parse("雨停", base)
        assertEquals(0f, result.patch.apply(base).rainIntensity, 0.0001f)
    }

    @Test
    fun relativeAdjustmentIsClampedAtBounds() {
        val maxed = base.copy(rainIntensity = 1f)
        val applied = parser.parse("雨大一点", maxed).patch.apply(maxed)
        assertEquals(1f, applied.rainIntensity, 0.0001f)

        val zero = base.copy(rainIntensity = 0f)
        val appliedZero = parser.parse("雨小一点", zero).patch.apply(zero)
        assertEquals(0f, appliedZero.rainIntensity, 0.0001f)
    }

    @Test
    fun windToMax() {
        val applied = parser.parse("把风速调到最大", base).patch.apply(base)
        assertEquals(1f, applied.windStrength, 0.0001f)
    }

    @Test
    fun disableParallax() {
        val applied = parser.parse("关掉视差", base).patch.apply(base)
        assertEquals(0f, applied.parallaxStrength, 0.0001f)
    }

    @Test
    fun skyColorKeyword() {
        val applied = parser.parse("天空偏紫色", base).patch.apply(base)
        assertEquals("#2B1B3D", applied.skyColor)
    }

    @Test
    fun lightDimmer() {
        val applied = parser.parse("画面暗一点", base).patch.apply(base)
        assertEquals(base.lightIntensity - 0.2f, applied.lightIntensity, 0.0001f)
    }

    @Test
    fun weatherRain() {
        val applied = parser.parse("下雨了", base).patch.apply(base)
        assertEquals(WeatherType.RAIN, applied.weather)
    }

    @Test
    fun explicitFpsIsSnappedToSupportedValue() {
        val applied = parser.parse("帧率调到 45", base).patch.apply(base)
        assertEquals(30, applied.targetFps)
    }

    @Test
    fun powerSavingPreset() {
        val applied = parser.parse("开启省电模式", base).patch.apply(base)
        assertEquals(15, applied.targetFps)
    }

    @Test
    fun fireflyDensity() {
        val applied = parser.parse("萤火虫多一些", base).patch.apply(base)
        assertEquals(base.particleDensity + 0.2f, applied.particleDensity, 0.0001f)
    }

    @Test
    fun resultNeverExceedsLegalRange() {
        val prompts = listOf(
            "雨大一点 风大一点 亮一点 视差大一点 萤火虫多一点 快一点",
            "雨停 风停 关掉视差",
            "风速最大 帧率 120"
        )
        for (prompt in prompts) {
            val applied = parser.parse(prompt, base).patch.apply(base)
            assertTrue("雨量越界：${applied.rainIntensity}", applied.rainIntensity in 0f..1f)
            assertTrue("风速越界：${applied.windStrength}", applied.windStrength in 0f..1f)
            assertTrue("光照越界：${applied.lightIntensity}", applied.lightIntensity in 0f..1f)
            assertTrue("视差越界：${applied.parallaxStrength}", applied.parallaxStrength in 0f..1f)
            assertTrue("粒子越界：${applied.particleDensity}", applied.particleDensity in 0f..1f)
            assertTrue(
                "动画速度越界：${applied.animationSpeed}",
                applied.animationSpeed in SceneConfig.MIN_ANIMATION_SPEED..SceneConfig.MAX_ANIMATION_SPEED
            )
            assertTrue("帧率非法：${applied.targetFps}", applied.targetFps in SceneConfig.SUPPORTED_FPS)
        }
    }

    @Test
    fun explanationsAreProducedForMatchedRules() {
        val result = parser.parse("雨大一点，切换到森林", base)
        assertTrue(result.matched)
        assertTrue(result.explanations.size >= 2)
    }
}

class InstructionPatchTest {

    private val base = SceneConfig.DEFAULT.clamped()

    @Test
    fun emptyPatchLeavesConfigUnchanged() {
        assertEquals(base, InstructionPatch().apply(base))
    }

    @Test
    fun absoluteValuesAreClamped() {
        val patch = InstructionPatch(absolute = mapOf(ParamKey.RAIN to 5f))
        assertEquals(1f, patch.apply(base).rainIntensity, 0.0001f)
    }

    @Test
    fun relativeValuesAreClamped() {
        val patch = InstructionPatch(relative = mapOf(ParamKey.RAIN to -5f))
        assertEquals(0f, patch.apply(base).rainIntensity, 0.0001f)
    }

    @Test
    fun sceneSwitchUpdatesSceneId() {
        val patch = InstructionPatch(sceneType = SceneType.AURORA)
        val applied = patch.apply(base)
        assertEquals(SceneType.AURORA, applied.sceneType)
        assertEquals("aurora-lake", applied.sceneId)
    }
}

class SceneConfigJsonParserTest {

    private val parser = SceneConfigJsonParser()

    @Test
    fun parsesFullObject() {
        val raw = """
            {
              "version": 1,
              "sceneType": "AURORA",
              "weather": "CLEAR",
              "skyColor": "#123456",
              "rainIntensity": 0.2,
              "windStrength": 0.3,
              "lightIntensity": 0.4,
              "animationSpeed": 1.2,
              "parallaxStrength": 0.5,
              "particleDensity": 0.6,
              "targetFps": 60
            }
        """.trimIndent()
        val result = parser.parse(raw)
        assertEquals(null, result.error)
        assertEquals(SceneType.AURORA, result.config?.sceneType)
        assertEquals(60, result.config?.targetFps)
        assertFalse(result.repaired)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val result = parser.parse("""{"sceneType":"STAR_VALLEY"}""")
        assertEquals(SceneType.STAR_VALLEY, result.config?.sceneType)
        assertEquals(SceneConfig.DEFAULT.clamped().weather, result.config?.weather)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val result = parser.parse("""{"sceneType":"FOREST","somethingNew":123}""")
        assertEquals(SceneType.FOREST, result.config?.sceneType)
    }

    @Test
    fun illegalValuesAreClamped() {
        val result = parser.parse("""{"rainIntensity":9,"targetFps":45,"skyColor":"bad"}""")
        assertEquals(1f, result.config!!.rainIntensity, 0.0001f)
        assertEquals(30, result.config!!.targetFps)
        assertEquals(SceneConfig.DEFAULT_SKY_COLOR, result.config!!.skyColor)
    }

    @Test
    fun surroundingTextIsRepairedOnce() {
        val result = parser.parse("""好的，配置如下：{"sceneType":"AURORA"} 希望对你有帮助""")
        assertEquals(SceneType.AURORA, result.config?.sceneType)
        assertTrue(result.repaired)
    }

    @Test
    fun trailingCommaIsRepaired() {
        val result = parser.parse("""{"sceneType":"AURORA",}""")
        assertEquals(SceneType.AURORA, result.config?.sceneType)
        assertTrue(result.repaired)
    }

    @Test
    fun garbageReturnsNullWithError() {
        val result = parser.parse("这不是 JSON")
        assertEquals(null, result.config)
        assertTrue(result.error?.isNotEmpty() == true)
    }

    @Test
    fun wrongTypeIsReportedInsteadOfThrowing() {
        // 类型错误不能让调用方崩溃；要么被强制修正，要么返回可诊断的错误
        val result = parser.parse("""{"rainIntensity":"0.5"}""")
        assertTrue(
            "结果必须合法或带错误：config=${result.config}, error=${result.error}",
            result.config == null ||
                (result.config!!.rainIntensity in 0f..1f && result.error == null) ||
                (result.config == null && result.error != null)
        )
    }
}

class RuleBasedProviderTest {


    private val provider = RuleBasedProvider()
    private val base = SceneConfig.DEFAULT.clamped()

    @Test
    fun matchedInstructionChangesConfig() = kotlinx.coroutines.test.runTest {
        val result = provider.generateSceneConfig("雨大一点", base)
        assertEquals(base.rainIntensity + 0.2f, result.config.rainIntensity, 0.0001f)
        assertTrue(result.changed)
        assertTrue(result.explanations.isNotEmpty())
        assertTrue("耗时应被测量", result.elapsedMillis >= 0L)
    }

    @Test
    fun unmatchedInstructionThrows() = kotlinx.coroutines.test.runTest {
        var thrown = false
        try {
            provider.generateSceneConfig("随便写点什么", base)
        } catch (e: com.livingwallpaper.ai.api.InstructionNotUnderstoodException) {
            thrown = true
        }
        assertTrue("无法理解的指令必须抛异常，而不是随便改配置", thrown)
    }

    @Test
    fun providerIsExplicitlyNotAModel() {
        assertFalse(provider.isModel)
        assertTrue(provider.displayName.contains("规则"))
    }
}
