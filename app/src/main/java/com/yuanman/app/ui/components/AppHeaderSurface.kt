package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AppHeaderSurface —— 应用统一的顶部 Header 底纹卡（v0.0.4 视觉主题化）
 *
 * 提取自首页 FinancialOverviewCard 的视觉语言并沉淀为可复用组件，供各页面 Header 统一采用：
 *  - 低对比斜向细纹理（两道透明度交错的 1dp 斜线组，间距 22dp）；
 *  - 右上/左下两处主色柔光晕（alpha 0.035 / 0.022，两层叠加增强层次）；
 *  - 素面 surface 底色 + 1dp outlineVariant(0.35) 细描边（沿既定描边纪律）；
 *  - 可选柔和投影 [shadowElevation]（默认 3dp，营造卡片浮起感）。
 *
 * 用法：用它包裹页面顶部 Header 的原内容（标题行/金额区等），形状按页面边缘场景传参；
 * 内部不做任何 padding/statusBars 处理，由调用方沿用原布局参数。
 */
@Composable
fun AppHeaderSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
    shadowElevation: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textureLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.022f)
    val textureGlowColor = primaryColor.copy(alpha = 0.035f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .shadow(elevation = shadowElevation, shape = shape, clip = false)
            .clip(shape)
            .background(surfaceColor)
            .drawBehind {
                // 与首页 FinancialOverviewCard 同口径：先铺底色再叠纹理与光晕
                drawRect(surfaceColor)

                val spacing = 22.dp.toPx()
                val lineWidth = 1.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = textureLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = lineWidth
                    )
                    drawLine(
                        color = textureLineColor.copy(alpha = 0.012f),
                        start = Offset(x + size.height * 0.45f, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineWidth
                    )
                    x += spacing
                }

                drawCircle(
                    color = textureGlowColor,
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width * 0.96f, size.height * 0.04f)
                )
                drawCircle(
                    color = textureGlowColor.copy(alpha = 0.022f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.02f, size.height * 0.98f)
                )
            }
            .border(width = 1.dp, color = borderColor, shape = shape),
        content = content
    )
}
