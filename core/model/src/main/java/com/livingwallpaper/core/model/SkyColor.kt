package com.livingwallpaper.core.model

/**
 * `#RRGGBB` 颜色工具。模型层只做格式校验与解析，不做渲染。
 */
object SkyColor {

    private val HEX_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

    fun isValid(value: String): Boolean = HEX_PATTERN.matches(value)

    /** 解析为长度 3 的 RGB 数组，分量范围 0f..1f；非法输入返回 null。 */
    fun parse(value: String): FloatArray? {
        if (!isValid(value)) return null
        val r = value.substring(1, 3).toIntOrNull(16) ?: return null
        val g = value.substring(3, 5).toIntOrNull(16) ?: return null
        val b = value.substring(5, 7).toIntOrNull(16) ?: return null
        return floatArrayOf(r / 255f, g / 255f, b / 255f)
    }

    /** 由 RGB（0f..1f）生成 `#RRGGBB`，用于持久化与 UI 回显。 */
    fun format(rgb: FloatArray): String {
        require(rgb.size >= 3) { "rgb 至少需要 3 个分量" }
        fun component(value: Float): Int =
            (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(component(rgb[0]), component(rgb[1]), component(rgb[2]))
    }

    /** 按比例调暗/调亮，factor < 1 变暗，> 1 变亮。 */
    fun scale(rgb: FloatArray, factor: Float): FloatArray =
        floatArrayOf(
            (rgb[0] * factor).coerceIn(0f, 1f),
            (rgb[1] * factor).coerceIn(0f, 1f),
            (rgb[2] * factor).coerceIn(0f, 1f)
        )
}
