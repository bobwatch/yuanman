package com.moneyhistory.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.ui.theme.LocalDarkTheme
import java.util.Locale

/** 图表分类配色（10 色，循环使用）。 */
val ChartPalette = listOf(
    Color(0xFF2AABEE), Color(0xFF34A853), Color(0xFFFF9800), Color(0xFFE53935),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFC107), Color(0xFF795548),
    Color(0xFF607D8B), Color(0xFFEC407A)
)

/** 深色主题专用：整体提亮一档，深底上保持可读（默认色板棕/蓝灰在深底对比不足）。 */
private val ChartPaletteDark = listOf(
    Color(0xFF4FC3F7), Color(0xFF66BB6A), Color(0xFFFFB74D), Color(0xFFEF5350),
    Color(0xFFBA68C8), Color(0xFF4DD0E1), Color(0xFFFFD54F), Color(0xFFA1887F),
    Color(0xFF90A4AE), Color(0xFFF06292)
)

/** 按当前主题取图表色板。 */
@Composable
fun chartPalette(): List<Color> =
    if (LocalDarkTheme.current) ChartPaletteDark else ChartPalette

/**
 * 金额缩写（图表标签用）：≥1 万用 [wanUnit]（中文环境「万」），≥1000 用 [kUnit]（k），
 * 否则取整。单位文案走字符串资源（英文环境 wanUnit 为空串，自动落到 k）。
 */
internal fun abbrevYuan(cents: Long, wanUnit: String, kUnit: String): String {
    val yuan = cents / 100f
    val locale = Locale.getDefault()
    return when {
        yuan >= 10000f && wanUnit.isNotEmpty() ->
            String.format(locale, "%.1f%s", yuan / 10000f, wanUnit)
        yuan >= 1000f -> String.format(locale, "%.1f%s", yuan / 1000f, kUnit)
        else -> String.format(locale, "%.0f", yuan)
    }
}

/** 环形图的一个切片。 */
data class ChartSlice(val label: String, val value: Float)

/** 趋势柱状图的一个月份点。 */
data class MonthPoint(val label: String, val amountCents: Long, val isCurrent: Boolean)

/** 分类占比环形图（drawArc 描边成环，中心显示总额）。[sliceColors] 可逐片指定配色，
 *  缺省用 [ChartPalette]（默认图表色板）。 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    sliceColors: List<Color>? = null
) {
    // TalkBack 读出每类占比与合计
    val desc = stringResource(
        R.string.chart_donut_desc,
        slices.joinToString(", ") { "${it.label} ${it.value.toInt()}" },
        centerValue
    )
    Box(
        modifier = modifier.semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        // 扫入动画：数据变化（切 Tab / 换月）时整环从 0 转到 1
        val progress = remember(slices) { Animatable(0f) }
        LaunchedEffect(slices) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        // 色板在组合期取好（Canvas 绘制 lambda 里不能调组合函数）
        val palette = sliceColors ?: chartPalette()
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
                val sweep = slice.value / total * 360f * progress.value
                if (sweep > 0f) {
                    drawArc(
                        color = palette[i % palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                }
                startAngle += slice.value / total * 360f
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 中心金额按长度降字号：大额长串不省略号截断（环形内径有限）
            val centerStyle = when {
                centerValue.length <= 8 -> MaterialTheme.typography.titleLarge
                centerValue.length <= 11 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(
                text = centerValue,
                style = centerStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val wanUnit = stringResource(R.string.chart_abbrev_wan)
    val kUnit = stringResource(R.string.chart_abbrev_k)
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // TalkBack 读出各月金额
    val desc = stringResource(
        R.string.chart_trend_desc,
        points.joinToString(", ") { "${it.label} ${abbrevYuan(it.amountCents, wanUnit, kUnit)}" }
    )
    // 柱生长动画：数据变化时从底部一起长高，金额标签跟随柱顶
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier.semantics { contentDescription = desc }
    ) {
        if (points.isEmpty()) return@Canvas
        // 按 density 换算，适配不同屏幕文字密度
        textPaint.textSize = 10.sp.toPx()
        val max = points.maxOf { it.amountCents }
        val anim = progress.value
        val topLabelHeight = 34f
        val bottomLabelHeight = 34f
        val chartHeight = (size.height - topLabelHeight - bottomLabelHeight).coerceAtLeast(1f)
        val slotWidth = size.width / points.size
        val barWidth = slotWidth * 0.5f
        points.forEachIndexed { i, p ->
            // 0 金额月份不画「假柱」（底部 3px 会被误读为有少量数据），只保留月份标签
            if (p.amountCents > 0L) {
                val barHeight = p.amountCents.toFloat() / max * chartHeight * anim
                val left = slotWidth * i + (slotWidth - barWidth) / 2f
                val top = topLabelHeight + (chartHeight - barHeight)
                drawRoundRect(
                    color = if (p.isCurrent) primary else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight.coerceAtLeast(3f)),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    abbrevYuan(p.amountCents, wanUnit, kUnit),
                    left + barWidth / 2f,
                    top - 8f,
                    textPaint
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                p.label,
                slotWidth * i + slotWidth / 2f,
                size.height - 6f,
                textPaint
            )
        }
    }
}

/** 本月每日走势：折线 + 渐变填充面积图，X 轴抽样日期标签。
 *  [todayIndex] 非空时在该数据点画主色实心圆（查看当月时标记「今天」）。 */
@Composable
fun DailyLineChart(
    dailyCents: List<Long>,
    dayLabels: List<Int>,
    modifier: Modifier = Modifier,
    todayIndex: Int? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // TalkBack 读出有记录的天数与合计（逐日数值太长，只报摘要）
    val desc = stringResource(
        R.string.chart_daily_desc,
        dailyCents.count { it > 0L },
        MoneyUtils.formatCents(dailyCents.sum())
    )
    // 从左向右展开动画：clip 宽度按进度增长，折线像被「画」出来
    val progress = remember(dailyCents) { Animatable(0f) }
    LaunchedEffect(dailyCents) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier.semantics { contentDescription = desc }
    ) {
        if (dailyCents.isEmpty()) return@Canvas
        // 按 density 换算，适配不同屏幕文字密度
        textPaint.textSize = 9.sp.toPx()
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
        clipRect(right = size.width * progress.value) {
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
            // 「今天」高亮：表面色底圈 + 主色实心，一眼定位当前位置
            if (todayIndex != null && todayIndex in dailyCents.indices) {
                val todayPoint = pointAt(todayIndex)
                drawCircle(color = surfaceColor, radius = 8f, center = todayPoint)
                drawCircle(color = primary, radius = 5f, center = todayPoint)
            }
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
}
