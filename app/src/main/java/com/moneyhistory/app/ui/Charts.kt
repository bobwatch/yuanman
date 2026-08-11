package com.moneyhistory.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/** 图表分类配色（10 色，循环使用）。 */
val ChartPalette = listOf(
    Color(0xFF2AABEE), Color(0xFF34A853), Color(0xFFFF9800), Color(0xFFE53935),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFC107), Color(0xFF795548),
    Color(0xFF607D8B), Color(0xFFEC407A)
)

/** 金额缩写（图表标签用）：≥1000 元显示 3.2k，否则取整。 */
internal fun abbrevYuan(cents: Long): String {
    val yuan = cents / 100f
    return if (yuan >= 1000f) {
        String.format(Locale.CHINA, "%.1fk", yuan / 1000f)
    } else {
        String.format(Locale.CHINA, "%.0f", yuan)
    }
}

/** 环形图的一个切片。 */
data class ChartSlice(val label: String, val value: Float)

/** 趋势柱状图的一个月份点。 */
data class MonthPoint(val label: String, val amountCents: Long, val isCurrent: Boolean)

/** 分类占比环形图（drawArc 描边成环，中心显示总额）。 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val strokeWidth = size.minDimension * 0.16f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            slices.forEachIndexed { i, slice ->
                val sweep = slice.value / total * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = ChartPalette[i % ChartPalette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 近 6 个月趋势柱状图：当前月主色高亮，柱顶显示金额缩写。 */
@Composable
fun TrendBarChart(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val max = points.maxOf { it.amountCents }.coerceAtLeast(1L)
        val topLabelHeight = 34f
        val bottomLabelHeight = 34f
        val chartHeight = (size.height - topLabelHeight - bottomLabelHeight).coerceAtLeast(1f)
        val slotWidth = size.width / points.size
        val barWidth = slotWidth * 0.5f
        points.forEachIndexed { i, p ->
            val barHeight = p.amountCents.toFloat() / max * chartHeight
            val left = slotWidth * i + (slotWidth - barWidth) / 2f
            val top = topLabelHeight + (chartHeight - barHeight)
            drawRoundRect(
                color = if (p.isCurrent) primary else barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight.coerceAtLeast(3f)),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                abbrevYuan(p.amountCents),
                left + barWidth / 2f,
                top - 8f,
                textPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                p.label,
                left + barWidth / 2f,
                size.height - 6f,
                textPaint
            )
        }
    }
}

/** 本月每日走势：折线 + 渐变填充面积图，X 轴抽样日期标签。 */
@Composable
fun DailyLineChart(
    dailyCents: List<Long>,
    dayLabels: List<Int>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Canvas(modifier) {
        if (dailyCents.isEmpty()) return@Canvas
        val max = (dailyCents.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val labelHeight = 32f
        val chartHeight = (size.height - labelHeight).coerceAtLeast(1f)
        val stepX = if (dailyCents.size > 1) {
            size.width / (dailyCents.size - 1)
        } else {
            size.width
        }

        fun pointAt(index: Int): Offset {
            val x = (stepX * index).coerceIn(0f, size.width)
            val y = chartHeight - dailyCents[index].toFloat() / max * (chartHeight * 0.85f) -
                chartHeight * 0.05f
            return Offset(x, y)
        }

        val linePath = Path()
        dailyCents.indices.forEach { i ->
            val p = pointAt(i)
            if (i == 0) linePath.moveTo(p.x, p.y) else linePath.lineTo(p.x, p.y)
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(pointAt(dailyCents.size - 1).x, chartHeight)
            lineTo(0f, chartHeight)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.3f), primary.copy(alpha = 0f)),
                startY = 0f,
                endY = chartHeight
            )
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        dayLabels.forEach { day ->
            val index = (day - 1).coerceIn(0, dailyCents.size - 1)
            drawContext.canvas.nativeCanvas.drawText(
                day.toString(),
                pointAt(index).x,
                size.height - 4f,
                textPaint
            )
        }
    }
}
