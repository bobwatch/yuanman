package com.yuanman.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Branded pull-to-refresh feedback without Material's morphing container.
 * The explicit pill surface avoids the white polygon/block artifact seen on light themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuanmanPullRefreshIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier
) {
    // 在组件内部读取手势状态，避免下拉的每一帧让整个页面内容一起重组。
    val reveal = state.progress.coerceIn(0f, 1f)
    val isRefreshing = state.isRefreshing
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val smoothProgress by animateFloatAsState(
        targetValue = reveal,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "refresh_pull_progress"
    )
    val message = when {
        isRefreshing -> "正在整理账本"
        reveal >= 0.78f -> "松手刷新"
        else -> "继续下拉"
    }

    AnimatedVisibility(
        visible = isRefreshing || reveal > 0f,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(80)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(90)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(90)
        )
    ) {
        Surface(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = if (isLightTheme) 0.dp else 2.dp,
            shadowElevation = if (isLightTheme) 0.dp else 5.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isLightTheme) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { smoothProgress.coerceAtLeast(0.06f) },
                            modifier = Modifier.size(15.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(7.dp))
                // 文案多长胶囊就多宽，状态切换时由外层弹簧自然伸缩。
                Crossfade(
                    targetState = message,
                    animationSpec = tween(durationMillis = 100),
                    label = "refresh_message"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
