package com.moneyhistory.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 金额与时间的显示/解析工具。
 * 内部一律使用 Long（分）计算，只在显示层格式化为「¥xx.xx」。
 */
object MoneyUtils {

    /** 把「分」格式化为类似 ¥12.50 的字符串（不带正负号，正负由调用方加）。 */
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return String.format(Locale.CHINA, "%s¥%d.%02d", sign, abs / 100, abs % 100)
    }

    /** 把「分」格式化为 12.50 形式（用于 CSV 导出，Excel 友好）。 */
    fun formatCentsPlain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "%s%d.%02d".format(sign, abs / 100, abs % 100)
    }

    /**
     * 解析用户输入的金额字符串（元）为「分」。
     * 非法输入、非正数、超大金额均返回 null。
     */
    fun parseToCents(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val value = BigDecimal(trimmed)
            if (value <= BigDecimal.ZERO || value > BigDecimal("99999999")) {
                null
            } else {
                value.multiply(BigDecimal(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 时间戳格式化为 yyyy-MM-dd HH:mm。 */
    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
