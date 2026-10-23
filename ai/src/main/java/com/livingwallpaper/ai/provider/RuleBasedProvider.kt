package com.livingwallpaper.ai.provider

import com.livingwallpaper.ai.api.AIProvider
import com.livingwallpaper.ai.api.GenerationResult
import com.livingwallpaper.ai.api.InstructionNotUnderstoodException
import com.livingwallpaper.ai.parser.InstructionParseResult
import com.livingwallpaper.ai.parser.RuleBasedInstructionParser
import com.livingwallpaper.core.model.SceneConfig

/**
 * 规则模式（非模型推理）。
 *
 * 行为契约：
 * - 命中规则 → 返回新配置 + 命中的规则说明；
 * - 未命中 → 抛出 [InstructionNotUnderstoodException]，调用方必须保留原配置；
 * - 耗时使用 [System.nanoTime] 实测，不写死。
 *
 * 未来接入真实模型时，实现 [AIProvider] 并替换注入即可，上层无需改动。
 */
class RuleBasedProvider(
    private val parser: RuleBasedInstructionParser = RuleBasedInstructionParser()
) : AIProvider {

    override val id: String = ID

    override val displayName: String = "规则模式（非模型推理）"

    override val isModel: Boolean = false

    override suspend fun generateSceneConfig(
        prompt: String,
        currentConfig: SceneConfig
    ): GenerationResult {
        val startedNanos = System.nanoTime()
        val result: InstructionParseResult = parser.parse(prompt, currentConfig)
        val elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L

        if (!result.matched) {
            throw InstructionNotUnderstoodException(
                "未能理解该指令。规则模式只支持场景切换、天气、天空颜色、" +
                    "雨量/风速/光照/动画速度/视差/粒子密度与帧率的显式表述。"
            )
        }

        val next = result.patch.apply(currentConfig)
        return GenerationResult(
            config = next,
            explanations = result.explanations,
            changed = next != currentConfig,
            elapsedMillis = elapsedMillis
        )
    }

    companion object {
        const val ID = "rule-based"
    }
}
