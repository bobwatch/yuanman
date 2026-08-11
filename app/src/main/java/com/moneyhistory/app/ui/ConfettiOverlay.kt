package com.moneyhistory.app.ui

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
import java.util.Random

private data class ConfettiParticle(
    val x: Float,      // 屏宽比例 0..1
    val y: Float,      // 屏高比例
    val vx: Float,     // 屏宽比例 / 秒
    val vy: Float,     // 屏高比例 / 秒
    val color: Color,
    val sizePx: Float
)

/**
 * 目标达成的全屏撒花动画：Canvas 自绘粒子，withFrameMillis 驱动，
 * 2 秒自动消失（后半程渐隐）。
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

    LaunchedEffect(Unit) {
        val random = Random()
        particles = List(80) {
            ConfettiParticle(
                x = random.nextFloat(),
                y = -random.nextFloat() * 0.3f,
                vx = (random.nextFloat() - 0.5f) * 0.15f,
                vy = 0.35f + random.nextFloat() * 0.45f,
                color = ChartPalette[random.nextInt(ChartPalette.size)],
                sizePx = 12f + random.nextFloat() * 14f
            )
        }
        val startTime = withFrameMillis { it }
        var lastFrame = startTime
        while (lastFrame - startTime < 2000L) {
            withFrameMillis { frameTime ->
                val dt = ((frameTime - lastFrame) / 1000f).coerceIn(0f, 0.1f)
                lastFrame = frameTime
                val elapsed = (frameTime - startTime) / 2000f
                alpha = (1f - elapsed).coerceIn(0f, 1f)
                particles = particles.map { p ->
                    p.copy(x = p.x + p.vx * dt, y = p.y + p.vy * dt)
                }
            }
        }
        onFinished()
    }

    Canvas(modifier.fillMaxSize()) {
        particles.forEach { p ->
            drawRect(
                color = p.color,
                topLeft = Offset(p.x * size.width, p.y * size.height),
                size = Size(p.sizePx, p.sizePx * 0.6f),
                alpha = alpha
            )
        }
    }
}
