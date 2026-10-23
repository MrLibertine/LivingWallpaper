package com.livingwallpaper.ai.api

import com.livingwallpaper.core.model.SceneConfig

/**
 * 自然语言 → 场景配置的抽象。
 *
 * 当前唯一实现是规则解析器（[com.livingwallpaper.ai.provider.RuleBasedProvider]），
 * 它不是大模型，UI 必须明确标注“规则模式”。
 *
 * 未来接入本地模型（MNN）或云端代理时，实现同一接口即可替换，
 * 上层无需改动。
 *
 * 与计划书 3.6 的差异：返回值由 SceneConfig 改为 [GenerationResult]。
 * 原因：UI 需要展示“哪些规则被命中 / 实际改了什么”，
 * 否则无法向用户证明指令被真正理解，只能靠画面猜测。
 */
interface AIProvider {

    /** 稳定标识，用于持久化用户选择。 */
    val id: String

    /** 展示名称。 */
    val displayName: String

    /** 是否为真实的端侧/云端模型推理（规则解析器恒为 false）。 */
    val isModel: Boolean
        get() = false

    /**
     * 把自然语言指令转换成新配置。
     *
     * @throws InstructionNotUnderstoodException 指令无法被理解时抛出，调用方应保留原配置。
     */
    suspend fun generateSceneConfig(prompt: String, currentConfig: SceneConfig): GenerationResult
}

data class GenerationResult(
    /** 生成后的配置，已归一化。 */
    val config: SceneConfig,
    /** 命中的规则说明，用于向用户解释发生了什么。 */
    val explanations: List<String>,
    /** 与原配置相比是否发生了变化。 */
    val changed: Boolean,
    /** 真实耗时（毫秒），由实现测量，不得伪造。 */
    val elapsedMillis: Long
)

class InstructionNotUnderstoodException(message: String) : IllegalArgumentException(message)
