package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 操作成功的对勾微动效：屏幕中央偏上 Canvas 自绘
 * （圆形描边 300ms + 对勾路径，随后 200ms 淡出）+ 确认触感。
 *
 * 由 [trigger] 自增计数驱动：每次 +1 播放一次（0 或重复值不触发）；
 * [tint] 决定对勾颜色（收入绿勾、其余品牌蓝勾）。
 */
@Composable
fun SuccessOverlay(
    trigger: Int,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val color = tint
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var active by remember { mutableStateOf(false) }
    // 初始不同步播放：只响应 trigger 的变化，避免进入页面时误播
    var lastHandled by remember { mutableStateOf(trigger) }

    LaunchedEffect(trigger) {
        if (trigger == lastHandled) return@LaunchedEffect
        lastHandled = trigger
        active = true
        // 走系统触感通道：尊重「触摸振动」设置（直接调 Vibrator 会无视开关）
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        progress.snapTo(0f)
        alpha.snapTo(1f)
        progress.animateTo(1f, tween(300))
        alpha.animateTo(0f, tween(200))
        active = false
    }

    if (active) {
        // 对勾位置按屏幕高度取比例（视觉中心偏上），不用固定像素偏移：
        // 小屏/大字号下页头更高时也不会与页头重叠
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val p = progress.value
            val a = alpha.value
            Canvas(
                Modifier
                    .size(96.dp)
                    .offset(y = -(maxHeight * 0.09f))
            ) {
                val strokeWidth = 6.dp.toPx()
                // 圆形描边：前 60% 时间
                val circleProgress = (p / 0.6f).coerceAtMost(1f)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * circleProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    alpha = a
                )
                // 对勾：后 40% 时间，两笔画
                val checkProgress = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                if (checkProgress > 0f) {
                    val w = size.width
                    val h = size.height
                    val p1 = Offset(w * 0.28f, h * 0.52f)
                    val p2 = Offset(w * 0.45f, h * 0.68f)
                    val p3 = Offset(w * 0.74f, h * 0.34f)
                    val seg1 = (checkProgress / 0.5f).coerceIn(0f, 1f)
                    if (seg1 > 0f) {
                        drawLine(
                            color = color,
                            start = p1,
                            end = lerp(p1, p2, seg1),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                            alpha = a
                        )
                    }
                    val seg2 = ((checkProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    if (seg2 > 0f) {
                        drawLine(
                            color = color,
                            start = p2,
                            end = lerp(p2, p3, seg2),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                            alpha = a
                        )
                    }
                }
            }
        }
    }
}
