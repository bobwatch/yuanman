package com.yuanman.app.ui.components

import androidx.compose.foundation.background
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
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.IconPalette
import com.yuanman.app.ui.theme.LocalDarkTheme

/**
 * 暗色主题色彩自适应增强：把色值在深色卡片上提亮到对比度 ≥ 4.5:1。
 * 具体映射见 [IconPalette.adaptForDarkSurface]（单调提亮，保留色与色之间的明暗关系）。
 */
fun adaptColorForDarkTheme(color: Color): Color =
    Color(IconPalette.adaptForDarkSurface(color.toArgb().toLong() and 0xFFFFFFFFL))

/**
 * 分类 / 账户 / 计划图标统一渲染单元。
 *
 * @param size 圆形底衬直径（[showBackground] 为 false 时仅作为图标容器尺寸）
 * @param iconSize 图形本身尺寸
 * @param showBackground 是否自绘底衬圆。外层已经画了圆（如选择网格的选中圆环）时必须置 false，
 *        否则会出现「大圆环套小圆」的双层圆，点选后看起来就是图标错位、变形。
 */
@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: Long,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    showBackground: Boolean = true
) {
    val isDark = LocalDarkTheme.current
    val rawColor = Color(colorHex)
    val displayColor = remember(rawColor, isDark) {
        if (isDark) adaptColorForDarkTheme(rawColor) else rawColor
    }
    val bgAlpha = if (isDark) IconPalette.DARK_TINT_ALPHA else IconPalette.LIGHT_TINT_ALPHA

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier
                        .clip(CircleShape)
                        .background(displayColor.copy(alpha = bgAlpha))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (BrandAccountIcons.isBrand(iconName)) {
            // 品牌账户（微信/支付宝/银联/QQ/ApplePay 等）：官方品牌原色渲染（自动响应深浅模式）
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
