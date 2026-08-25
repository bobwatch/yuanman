package com.yuanman.app.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

object MoneyUtils {
    private val decimalFormat = DecimalFormat("#,##0.00")
    private val plainDecimalFormat = DecimalFormat("0.00")

    /**
     * 将“分”转换为标准元字符串（如 1234 -> "12.34"）
     */
    fun centsToYuanString(cents: Long, withGrouping: Boolean = false): String {
        val bd = BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return if (withGrouping) decimalFormat.format(bd) else plainDecimalFormat.format(bd)
    }

    /**
     * 格式化展示金额，带人民币符号（如 "¥12.34"、"-¥12.34"、"+¥500.00"）
     */
    fun formatCurrency(
        cents: Long,
        showSign: Boolean = false,
        isExpense: Boolean = true,
        withGrouping: Boolean = true
    ): String {
        val amountStr = centsToYuanString(cents, withGrouping)
        return when {
            showSign && isExpense -> "-¥$amountStr"
            showSign && !isExpense -> "+¥$amountStr"
            else -> "¥$amountStr"
        }
    }

    /**
     * 将用户输入的金额字符串（如 "12.34"、"100"、"0.5"）安全转换为“分”
     */
    fun parseYuanToCents(yuanStr: String): Long {
        val trimmed = yuanStr.trim()
        if (trimmed.isEmpty()) return 0L
        return try {
            val bd = BigDecimal(trimmed)
            if (bd <= BigDecimal.ZERO) return 0L
            bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 校验输入的金额是否合法且大于 0，最多两位小数
     */
    fun isValidAmountInput(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val regex = Regex("""^[0-9]+(\.[0-9]{1,2})?$""")
        if (!regex.matches(trimmed)) return false
        return try {
            val bd = BigDecimal(trimmed)
            bd > BigDecimal.ZERO
        } catch (e: Exception) {
            false
        }
    }
}
