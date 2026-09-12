package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuanman.app.data.model.CategoryIconHelper

@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: Long,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    val bgColor = Color(colorHex)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (BrandAccountIcons.isBrand(iconName)) {
            // 品牌账户（微信/支付宝/银联）：固有配色渲染，不随主题色单色化
            BrandAccountIcon(iconName, size = iconSize)
        } else {
            // 优先复用应用根组合中预置的进程级 painter（矢量节点树整个进程只建一次），
            // 避免图标密集页面（分类网格等）每次进入都重新构建 painter 造成卡顿
            val cachedPainter = IconPainterCache.get(iconName)
            if (cachedPainter != null) {
                Icon(
                    painter = cachedPainter,
                    contentDescription = null,
                    tint = bgColor,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    imageVector = CategoryIconHelper.getIcon(iconName),
                    contentDescription = null,
                    tint = bgColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
