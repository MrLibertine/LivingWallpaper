package com.livingwallpaper.ai.parser

import com.livingwallpaper.core.model.SceneConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 解析模型返回的 JSON 配置。
 *
 * 契约：
 * - 允许部分字段（缺字段用默认值补齐）；
 * - 允许未知字段（忽略，不因新增字段而失败）；
 * - 数值非法时强制回退到默认值而不是抛异常；
 * - 解析失败只允许尝试一次修复（裁掉前后多余文本、去掉尾逗号），
 *   仍失败则返回 null，由调用方保留原配置（计划书：不静默改错）。
 */
class SceneConfigJsonParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {

    fun parse(raw: String): JsonParseResult {
        val direct = runCatching { json.decodeFromString<SceneConfig>(raw) }
        if (direct.isSuccess) {
            val config = direct.getOrNull() ?: return JsonParseResult(null, false, "解析结果为空")
            return JsonParseResult(config.clamped(), repaired = false, error = null)
        }

        val repairedSource = repair(raw)
        if (repairedSource == null) {
            return JsonParseResult(
                config = null,
                repaired = false,
                error = describe(direct.exceptionOrNull())
            )
        }

        val retried = runCatching { json.decodeFromString<SceneConfig>(repairedSource) }
        val config = retried.getOrNull()
        return if (config == null) {
            JsonParseResult(null, repaired = true, error = describe(retried.exceptionOrNull()))
        } else {
            JsonParseResult(config.clamped(), repaired = true, error = null)
        }
    }

    /**
     * 一次修复尝试：截取最外层花括号，并删除 }、] 前的尾逗号。
     * 只做这两件确定安全的事，不做任何猜测性的字段补全。
     */
    internal fun repair(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = raw.substring(start, end + 1)
        val withoutTrailingCommas = body.replace(Regex(""",\s*([}\]])"""), "$1")
        return if (withoutTrailingCommas == raw) null else withoutTrailingCommas
    }

    private fun describe(throwable: Throwable?): String = when (throwable) {
        null -> "未知解析错误"
        is SerializationException -> throwable.message ?: "JSON 结构与 SceneConfig 不匹配"
        else -> "${throwable::class.simpleName}: ${throwable.message}"
    }
}

data class JsonParseResult(
    val config: SceneConfig?,
    val repaired: Boolean,
    val error: String?
)
