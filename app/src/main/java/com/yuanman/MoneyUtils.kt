package com.yuanman

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 金额与时间的显示/解析工具。
 * 内部一律使用 Long（分）计算，只在显示层格式化为「¥xx.xx」。
 */
object MoneyUtils {

    // 千分位金额格式：大额一眼可读（¥1,234.56）；DecimalFormat 对 BigDecimal
    // 走精确格式化，不会像 double 那样产生浮点尾差
    private val centsFormat = DecimalFormat("#,##0.00").apply {
        decimalFormatSymbols = decimalFormatSymbols.apply {
            decimalSeparator = '.'
            groupingSeparator = ','
        }
    }

    /** 把「分」格式化为类似 ¥12.50 的字符串（不带正负号，正负由调用方加）。 */
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return sign + "¥" + centsFormat.format(BigDecimal(abs).movePointLeft(2))
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
