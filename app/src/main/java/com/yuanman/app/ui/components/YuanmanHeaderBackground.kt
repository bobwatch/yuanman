package com.yuanman.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 首页财务看板使用的轻量纹理 header 背景。
 *
 * 把状态栏也纳入背景范围，页面切换时三个功能页的顶部不会出现一块突兀的纯色区域。
 */
@Composable
fun YuanmanHeaderBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.022f)
    val glowColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val outlineColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                drawRect(surfaceColor)

                val spacing = 22.dp.toPx()
                val lineWidth = 1.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = lineWidth
                    )
                    drawLine(
                        color = lineColor.copy(alpha = 0.012f),
                        start = Offset(x + size.height * 0.45f, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineWidth
                    )
                    x += spacing
                }

                drawCircle(
                    color = glowColor,
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width * 0.96f, size.height * 0.04f)
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.022f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.02f, size.height * 0.98f)
                )
            }
            // 边框也属于整块背景，不能放在状态栏内边距之后，否则会在交界处画出横线。
            .border(width = 1.dp, color = outlineColor, shape = shape)
            // 先绘制整块背景，再将 header 内容避开状态栏；这样纹理会连续铺到屏幕顶部。
            .statusBarsPadding(),
        content = content
    )
}
