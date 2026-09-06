package com.yuanman.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自绘下拉刷新指示器（胶囊形态）：
 * 拖动中展示主色进度弧 + 「下拉刷新 / 松开刷新」提示，刷新中切换为主色圆环 + 「正在刷新」。
 * 使用白色圆角胶囊（细描边、低阴影）呈现状态，不再使用官方 PullToRefreshContainer，
 * 彻底避免浅色主题下残留圆形背景色块与硬阴影。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuanmanPullRefreshIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isRefreshing = state.isRefreshing
    val progress = state.progress
    val verticalOffset = state.verticalOffset

    // 三态文案均为 4 字，切换时不引起胶囊宽度跳动
    val label = when {
        isRefreshing -> "正在刷新"
        progress >= 1f -> "松开刷新"
        else -> "下拉刷新"
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                // 从列表顶部随下拉位移滑入，松开刷新后停留在原位；未拖拽时完全隐藏
                translationY = verticalOffset - size.height
                alpha = if (isRefreshing) 1f else progress.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, contentColor.copy(alpha = if (isRefreshing) 0.55f else 0.3f)),
            shadowElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = contentColor,
                        strokeWidth = 2.25.dp,
                        trackColor = Color.Transparent
                    )
                } else {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        drawArc(
                            color = contentColor,
                            startAngle = -90f,
                            sweepAngle = 300f * progress.coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = 2.25.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}
