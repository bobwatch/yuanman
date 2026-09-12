package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.yuanman.app.data.model.CategoryIconHelper

/**
 * 暗色主题色彩自适应增强：
 * 在深黑/深灰背景下，平滑提亮低明度色彩（如深蓝、深棕、深灰等），
 * 确保在深色模式下图标具备充足对比度（>= 4.5:1），彻底解决暗色模式下发暗、看不清的问题。
 */
fun adaptColorForDarkTheme(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    if (hsl[2] < 0.65f) {
        hsl[2] = 0.65f + (hsl[2] * 0.1f)
    }
    if (hsl[1] > 0.85f) {
        hsl[1] = 0.85f
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: Long,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    val isDark = isSystemInDarkTheme()
    val rawColor = Color(colorHex)
    val displayColor = remember(rawColor, isDark) {
        if (isDark) adaptColorForDarkTheme(rawColor) else rawColor
    }
    val bgAlpha = if (isDark) 0.22f else 0.15f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(displayColor.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        if (BrandAccountIcons.isBrand(iconName)) {
            // 品牌账户（微信/支付宝/银联/QQ/淘宝/ApplePay等）：官方品牌原色渲染（自动响应深浅模式）
            BrandAccountIcon(iconName, size = iconSize)
        } else if (AccountIconHelper.isAccountIcon(iconName)) {
            // 账户专用矢量图标（现金、银行卡、信用卡、存钱罐、股票、黄金、公积金等）：随主题色着色
            Icon(
                imageVector = AccountIconHelper.getIcon(iconName)!!,
                contentDescription = null,
                tint = displayColor,
                modifier = Modifier.size(iconSize)
            )
        } else {
            // 分类图标（Material Icons，优先进程缓存 Painter）
            val cachedPainter = IconPainterCache.get(iconName)
            if (cachedPainter != null) {
                Icon(
                    painter = cachedPainter,
                    contentDescription = null,
                    tint = displayColor,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    imageVector = CategoryIconHelper.getIcon(iconName),
                    contentDescription = null,
                    tint = displayColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
