package com.livingwallpaper.ai.parser

import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType

/**
 * 规则解析器：把中文/英文自然语言指令映射为受约束的 [InstructionPatch]。
 *
 * 明确说明：这是**确定性规则匹配**，不是大模型，不具备泛化能力。
 * 它只能理解下方显式列出的关键词与句式；无法理解时返回 matched=false，
 * 由调用方保留原配置并提示用户。
 *
 * 所有改动最终都会经过 [InstructionPatch.apply]，因此不可能产生越界值。
 */
class RuleBasedInstructionParser {

    fun parse(prompt: String, current: SceneConfig): InstructionParseResult {
        val text = prompt.trim()
        if (text.isBlank()) {
            return InstructionParseResult(
                matched = false,
                patch = InstructionPatch(),
                explanations = listOf("输入为空")
            )
        }
        val norm = text.lowercase()
        val explanations = mutableListOf<String>()
        val absolute = mutableMapOf<ParamKey, Float>()
        val relative = mutableMapOf<ParamKey, Float>()

        var sceneType: SceneType? = null
        var weather: WeatherType? = null
        var skyColor: String? = null
        var targetFps: Int? = null

        // 1) 场景切换
        when {
            norm.containsAny("森林", "雨林", "forest") -> {
                sceneType = SceneType.FOREST
                explanations += "切换场景：森林雨夜"
            }
            norm.containsAny("极光", "雪山", "雪湖", "湖泊", "湖面", "aurora", "lake") -> {
                sceneType = SceneType.AURORA
                explanations += "切换场景：极光雪山湖"
            }
            norm.containsAny("星空", "山谷", "流星", "star", "valley") -> {
                sceneType = SceneType.STAR_VALLEY
                explanations += "切换场景：星空山谷"
            }
        }

        // 2) 天气（顺序敏感：先判雨与多云，再判晴）
        when {
            norm.containsAny("下雨", "雨天", "暴雨", "rain", "rainy") -> {
                weather = WeatherType.RAIN
                explanations += "天气：雨"
            }
            norm.containsAny("多云", "阴天", "cloudy") -> {
                weather = WeatherType.CLOUDY
                explanations += "天气：多云"
            }
            norm.containsAny("晴天", "晴朗", "放晴", "clear") -> {
                weather = WeatherType.CLEAR
                explanations += "天气：晴"
            }
        }

        // 3) 天空颜色关键词
        val color = COLOR_KEYWORDS.firstOrNull { (word, _) -> norm.contains(word) }
        if (color != null) {
            skyColor = color.second
            explanations += "天空颜色：${color.second}"
        }

        // 4) 数值型参数的相对/绝对调整
        adjust(norm, listOf("雨"), ParamKey.RAIN, "雨量", relative, absolute, explanations)
        adjust(norm, listOf("风"), ParamKey.WIND, "风速", relative, absolute, explanations)
        adjust(norm, listOf("萤火虫", "飞虫"), ParamKey.PARTICLE, "萤火虫密度", relative, absolute, explanations)
        adjust(norm, listOf("流星"), ParamKey.PARTICLE, "流星密度", relative, absolute, explanations)
        adjust(norm, listOf("动画", "动态", "速度"), ParamKey.ANIMATION_SPEED, "动画速度", relative, absolute, explanations)
        adjust(norm, listOf("视差", "parallax"), ParamKey.PARALLAX, "视差强度", relative, absolute, explanations)
        adjust(norm, listOf("光", "亮度", "明暗", "亮", "暗"), ParamKey.LIGHT, "光照强度", relative, absolute, explanations)

        // 5) 帧率档位：显式数字或预设名称
        // 同时支持“60 帧 / 60fps”和“帧率调到 60”两种语序
        val requestedFps = FPS_AFTER.find(norm)?.groupValues?.get(1)
            ?: FPS_BEFORE.find(norm)?.groupValues?.get(1)
        val requested = requestedFps?.toIntOrNull()
        if (requested != null) {
            targetFps = SceneConfig.nearestSupportedFps(requested)
            explanations += "帧率档位：$targetFps FPS（输入 $requested）"
        } else {
            val preset = when {
                norm.containsAny("省电", "低帧") -> 15
                norm.containsAny("平衡", "中档") -> 30
                norm.containsAny("高画质", "高帧", "流畅") -> 60
                else -> null
            }
            if (preset != null) {
                targetFps = preset
                explanations += "帧率档位：$preset FPS"
            }
        }

        val patch = InstructionPatch(
            sceneType = sceneType,
            weather = weather,
            skyColor = skyColor,
            targetFps = targetFps,
            absolute = absolute,
            relative = relative
        )
        return InstructionParseResult(
            matched = !patch.isEmpty(),
            patch = patch,
            explanations = explanations
        )
    }

    /**
     * 在关键词附近开窗匹配“增强/减弱/关闭/最大”等修饰词。
     * 找不到任何方向词时不做改动，避免误伤只提到名词的句子。
     */
    private fun adjust(
        text: String,
        keywords: List<String>,
        key: ParamKey,
        label: String,
        relative: MutableMap<ParamKey, Float>,
        absolute: MutableMap<ParamKey, Float>,
        explanations: MutableList<String>
    ) {
        val index = indexOfKeyword(text, keywords) ?: return
        val start = (index - WINDOW_BEFORE).coerceAtLeast(0)
        val end = (index + WINDOW_AFTER).coerceAtMost(text.length)
        val window = text.substring(start, end)

        when {
            window.containsAny("停", "停止", "关闭", "关掉", "不要", "去掉", "没有", "关") -> {
                absolute[key] = key.min
                explanations += "$label：关闭"
            }
            window.containsAny("最大", "最多", "拉满") -> {
                absolute[key] = key.max
                explanations += "$label：最大"
            }
            window.containsAny("最小") -> {
                absolute[key] = key.min
                explanations += "$label：最小"
            }
            else -> {
                val direction = directionIn(window) ?: return
                val step = if (window.containsAny("很", "大幅", "特别", "超级")) LARGE_STEP else STEP
                val delta = direction * step
                relative[key] = (relative[key] ?: 0f) + delta
                explanations += "$label：${if (direction > 0) "+" else "-"}$step"
            }
        }
    }

    private fun directionIn(window: String): Int? {
        val stronger = window.containsAny(*STRONGER_WORDS)
        val weaker = window.containsAny(*WEAKER_WORDS)
        return when {
            stronger && !weaker -> 1
            weaker && !stronger -> -1
            else -> null
        }
    }

    private fun indexOfKeyword(text: String, keywords: List<String>): Int? {
        var best: Int? = null
        for (keyword in keywords) {
            val index = text.indexOf(keyword)
            if (index >= 0 && (best == null || index < best)) best = index
        }
        return best
    }

    private fun String.containsAny(vararg words: String): Boolean = words.any { contains(it) }

    companion object {
        private const val STEP = 0.2f
        private const val LARGE_STEP = 0.35f
        private const val WINDOW_BEFORE = 5
        private const val WINDOW_AFTER = 10

        private val FPS_AFTER = Regex("""(\d{1,3})\s*(?:fps|帧)""")
        private val FPS_BEFORE = Regex("""(?:帧率|fps)\D{0,4}(\d{1,3})""")

        private val COLOR_KEYWORDS = listOf(
            "紫" to "#2B1B3D",
            "蓝" to "#0B1626",
            "青" to "#123243",
            "绿" to "#1E3326",
            "橙" to "#3A2A20",
            "暖" to "#3A2A20"
        )

        private val STRONGER_WORDS = arrayOf(
            "大", "强", "多", "快", "高", "亮", "增", "浓",
            "more", "bigger", "stronger", "increase", "higher", "brighter", "faster"
        )
        private val WEAKER_WORDS = arrayOf(
            "小", "弱", "少", "慢", "低", "暗", "减", "淡",
            "less", "smaller", "weaker", "decrease", "lower", "dimmer", "slower"
        )
    }
}

data class InstructionParseResult(
    val matched: Boolean,
    val patch: InstructionPatch,
    val explanations: List<String>
)
