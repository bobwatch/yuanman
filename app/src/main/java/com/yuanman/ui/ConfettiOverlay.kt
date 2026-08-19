package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import java.util.Random
import kotlin.math.sin

private data class ConfettiParticle(
    val x: Float,        // 屏宽比例 0..1
    val y: Float,        // 屏高比例
    val vx: Float,       // 屏宽比例 / 秒
    val vy: Float,       // 屏高比例 / 秒
    val color: Color,
    val sizePx: Float,
    val rotation: Float,  // 当前旋转角（弧度）
    val angularVel: Float, // 角速度（弧度 / 秒）
    val swayPhase: Float, // 水平摆动相位
    val swayAmp: Float,   // 摆动幅度（屏宽比例）
    val round: Boolean    // 圆形粒子（与矩形混搭）
)

/**
 * 目标达成的全屏撒花动画：Canvas 自绘粒子，withFrameMillis 驱动，
 * 2 秒自动消失（后半程渐隐）。粒子旋转下落 + 水平摆动，矩形/圆形混搭。
 */
@Composable
fun ConfettiOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var particles by remember { mutableStateOf(emptyList<ConfettiParticle>()) }
    var alpha by remember { mutableFloatStateOf(1f) }
    // 撒花颜色走主题色板：深色主题用提亮版，彩纸在深底上同样醒目
    val palette = chartPalette()
    // 庆祝时刻给重触感：与目标达成/勋章解锁的「落定」体感一致
    val view = LocalView.current

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        val random = Random()
        particles = List(80) {
            ConfettiParticle(
                x = random.nextFloat(),
                y = -random.nextFloat() * 0.3f,
                vx = (random.nextFloat() - 0.5f) * 0.15f,
                vy = 0.35f + random.nextFloat() * 0.45f,
                color = palette[random.nextInt(palette.size)],
                sizePx = 10f + random.nextFloat() * 14f,
                rotation = random.nextFloat() * 6.28f,
                angularVel = (random.nextFloat() - 0.5f) * 12f,
                swayPhase = random.nextFloat() * 6.28f,
                swayAmp = 0.01f + random.nextFloat() * 0.03f,
                round = random.nextBoolean()
            )
        }
        try {
            val startTime = withFrameMillis { it }
            var lastFrame = startTime
            while (lastFrame - startTime < 2000L) {
                withFrameMillis { frameTime ->
                    val dt = ((frameTime - lastFrame) / 1000f).coerceIn(0f, 0.1f)
                    lastFrame = frameTime
                    val elapsed = (frameTime - startTime) / 2000f
                    alpha = (1f - elapsed).coerceIn(0f, 1f)
                    particles = particles.map { p ->
                        p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            rotation = p.rotation + p.angularVel * dt
                        )
                    }
                }
            }
        } finally {
            // 动画中途被取消（页面重组/销毁）也必须收尾：不清理的话
            // 撒花开关会一直停在 true，下次进来挡住整屏
            onFinished()
        }
    }

    Canvas(modifier.fillMaxSize()) {
        particles.forEach { p ->
            // 水平正弦摆动，下落轨迹更自然
            val drawX = (p.x + sin(p.swayPhase + p.y * 30f) * p.swayAmp) * size.width
            val drawY = p.y * size.height
            withTransform({
                translate(drawX, drawY)
                rotate(p.rotation * 57.29578f)
            }) {
                if (p.round) {
                    drawCircle(
                        color = p.color,
                        radius = p.sizePx / 2f,
                        alpha = alpha
                    )
                } else {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(-p.sizePx / 2f, -p.sizePx * 0.3f),
                        size = Size(p.sizePx, p.sizePx * 0.6f),
                        alpha = alpha
                    )
                }
            }
        }
    }
}
