package com.livingwallpaper.ai.prompt

import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 用于未来接入端侧/云端模型的提示词构造与输出校验。
 *
 * 当前项目只启用规则模式，这里提供的能力用于：
 * 1) 明确“模型只能输出这个 JSON 结构”，把自由度限制在可校验范围内；
 * 2) 保证任何模型输出都走 SceneConfigJsonParser 的归一化与修复流程。
 */
object PromptBuilder {

    private val json = Json { prettyPrint = true }

    /** 生成要求模型只输出 JSON 的提示词。 */
    fun build(current: SceneConfig, instruction: String): String {
        val currentJson = json.encodeToString(SceneConfig.serializer(), current)
        return buildString {
            appendLine("你是一个壁纸场景参数生成器。只输出一个 JSON 对象，不要输出任何解释、Markdown 或代码块。")
            appendLine()
            appendLine("当前配置：")
            appendLine(currentJson)
            appendLine()
            appendLine("字段约束：")
            appendLine("- sceneType 取值：${SceneType.entries.joinToString { "\"${it.name}\"" }}")
            appendLine("- weather 取值：${WeatherType.entries.joinToString { "\"${it.name}\"" }}")
            appendLine("- skyColor 必须是 #RRGGBB 格式")
            appendLine("- rainIntensity / windStrength / lightIntensity / parallaxStrength / particleDensity 取值 0..1")
            appendLine("- animationSpeed 取值 ${SceneConfig.MIN_ANIMATION_SPEED}..${SceneConfig.MAX_ANIMATION_SPEED}")
            appendLine("- targetFps 只能是 ${SceneConfig.SUPPORTED_FPS.joinToString(" / ")}")
            appendLine()
            appendLine("用户指令：$instruction")
        }
    }

    /** 生成一份最小示例，用于提示词中的 few-shot 与 Schema 校验测试。 */
    fun exampleJson(): String = buildJsonObject {
        put("sceneType", SceneType.FOREST.name)
        put("weather", WeatherType.RAIN.name)
        put("skyColor", SceneConfig.DEFAULT_SKY_COLOR)
        putJsonObject("params") {
            put("rainIntensity", 0.7)
            put("windStrength", 0.4)
            put("lightIntensity", 0.5)
            put("animationSpeed", 1.0)
            put("parallaxStrength", 0.5)
            put("particleDensity", 0.6)
            put("targetFps", 30)
        }
    }.toString()
}
