package com.yuanman.app.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.SimpleIcons
import compose.icons.TablerIcons
import compose.icons.simpleicons.Alipay
import compose.icons.simpleicons.Applepay
import compose.icons.simpleicons.Baidu
import compose.icons.simpleicons.Mastercard
import compose.icons.simpleicons.Paypal
import compose.icons.simpleicons.Taobao
import compose.icons.simpleicons.Tencentqq
import compose.icons.simpleicons.Visa
import compose.icons.simpleicons.Wechat
import compose.icons.tablericons.CreditCard

/**
 * 账户品牌官方矢量图标（基于 Simple Icons 官方正版矢量库）
 * 完全移除任何手绘矢量，保证官方高精标准渲染；
 * 并针对深色/暗色主题适配专用高辨识度配色，避免黑底看不清。
 */
object BrandAccountIcons {
    // 浅色模式品牌专属色
    val COLOR_WECHAT = Color(0xFF07C160)
    val COLOR_ALIPAY = Color(0xFF1677FF)
    val COLOR_QQ = Color(0xFF12B7F5)
    val COLOR_TAOBAO = Color(0xFFFF5000)
    val COLOR_APPLEPAY_LIGHT = Color(0xFF1D1D1F)
    val COLOR_UNIONPAY_LIGHT = Color(0xFFD32F2F)
    val COLOR_VISA_LIGHT = Color(0xFF1A1F71)
    val COLOR_MASTERCARD_LIGHT = Color(0xFFEB001B)
    val COLOR_PAYPAL_LIGHT = Color(0xFF003087)
    val COLOR_BAIDU_LIGHT = Color(0xFF2932E1)
    val COLOR_JD_LIGHT = Color(0xFFE1251B)

    // 深色模式专属高对比度配色（如 Apple Pay 变亮白、避免暗色背景隐形）
    val COLOR_WECHAT_DARK = Color(0xFF10D66B)
    val COLOR_ALIPAY_DARK = Color(0xFF3B8EFF)
    val COLOR_QQ_DARK = Color(0xFF38C8F7)
    val COLOR_TAOBAO_DARK = Color(0xFFFF6D26)
    val COLOR_APPLEPAY_DARK = Color(0xFFF5F5F7)
    val COLOR_UNIONPAY_DARK = Color(0xFFFF5252)
    val COLOR_VISA_DARK = Color(0xFF5B7FFF)
    val COLOR_MASTERCARD_DARK = Color(0xFFFF5A5F)
    val COLOR_PAYPAL_DARK = Color(0xFF2EA7E0)
    val COLOR_BAIDU_DARK = Color(0xFF4376F7)
    val COLOR_JD_DARK = Color(0xFFFF4D4F)

    private val BRAND_VECTORS: Map<String, ImageVector> = mapOf(
        "wechat" to SimpleIcons.Wechat,
        "alipay" to SimpleIcons.Alipay,
        "qq" to SimpleIcons.Tencentqq,
        "taobao" to SimpleIcons.Taobao,
        "applepay" to SimpleIcons.Applepay,
        "visa" to SimpleIcons.Visa,
        "mastercard" to SimpleIcons.Mastercard,
        "paypal" to SimpleIcons.Paypal,
        "baidu" to SimpleIcons.Baidu,
        // 兼容历史老数据中已选 unionpay 或 jd 的账户
        "unionpay" to TablerIcons.CreditCard,
        "jd" to SimpleIcons.Taobao
    )

    fun isBrand(key: String): Boolean = key in BRAND_VECTORS

    fun getBrandIcon(key: String): ImageVector? = BRAND_VECTORS[key]

    fun getBrandColor(key: String, isDark: Boolean): Color {
        return when (key) {
            "wechat" -> if (isDark) COLOR_WECHAT_DARK else COLOR_WECHAT
            "alipay" -> if (isDark) COLOR_ALIPAY_DARK else COLOR_ALIPAY
            "qq" -> if (isDark) COLOR_QQ_DARK else COLOR_QQ
            "taobao" -> if (isDark) COLOR_TAOBAO_DARK else COLOR_TAOBAO
            "applepay" -> if (isDark) COLOR_APPLEPAY_DARK else COLOR_APPLEPAY_LIGHT
            "unionpay" -> if (isDark) COLOR_UNIONPAY_DARK else COLOR_UNIONPAY_LIGHT
            "visa" -> if (isDark) COLOR_VISA_DARK else COLOR_VISA_LIGHT
            "mastercard" -> if (isDark) COLOR_MASTERCARD_DARK else COLOR_MASTERCARD_LIGHT
            "paypal" -> if (isDark) COLOR_PAYPAL_DARK else COLOR_PAYPAL_LIGHT
            "baidu" -> if (isDark) COLOR_BAIDU_DARK else COLOR_BAIDU_LIGHT
            "jd" -> if (isDark) COLOR_JD_DARK else COLOR_JD_LIGHT
            else -> if (isDark) Color.White else Color.Black
        }
    }
}

/** 按品牌 key 渲染对应的官方矢量图形（自动适配深浅主题对比度） */
@Composable
fun BrandAccountIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val isDark = isSystemInDarkTheme()
    val iconVector = BrandAccountIcons.getBrandIcon(iconKey) ?: return
    val iconColor = BrandAccountIcons.getBrandColor(iconKey, isDark)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(size)
        )
    }
}
