package com.yuanman

/**
 * 文本工具的码点级处理：emoji 等增补平面字符在 Java String 里占两个
 * UTF-16 码元，按码元截断/计数会把代理对切成半个，渲染成乱码「�」。
 * 所有「输入上限」统一走这里，截断与计数口径一致。
 */

/** 按 Unicode 码点截断到 [maxCodePoints] 个字符（emoji 计 1 个）。 */
internal fun truncateByCodePoints(text: String, maxCodePoints: Int): String {
    if (maxCodePoints <= 0) return ""
    if (text.codePointCount(0, text.length) <= maxCodePoints) return text
    val end = text.offsetByCodePoints(0, maxCodePoints)
    return text.substring(0, end)
}

/** 按 Unicode 码点计长（emoji 计 1 个，而非 2 个 UTF-16 单元）。 */
internal fun codePointLength(text: String): Int = text.codePointCount(0, text.length)
