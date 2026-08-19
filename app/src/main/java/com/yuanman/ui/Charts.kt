package com.yuanman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.MoneyUtils
import com.yuanman.R
import com.yuanman.ui.theme.LocalDarkTheme
import com.yuanman.ui.theme.expenseAmountColor
import android.view.HapticFeedbackConstants
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * 金额缩写（图表标签用）：≥1 万用 [wanUnit]（中文环境「万」），
 * 百万级用 [mUnit]（英文环境「M」，避免 1000.0k 的怪写法），≥1000 用 [kUnit]，
 * 否则取整。单位文案走字符串资源（英文环境 wanUnit 为空串，自动落到 M/k）。
 */
internal fun abbrevYuan(cents: Long, wanUnit: String, kUnit: String, mUnit: String): String {
    val yuan = cents / 100f
    val locale = Locale.getDefault()
    return when {
        yuan >= 10000f && wanUnit.isNotEmpty() ->
            String.format(locale, "%.1f%s", yuan / 10000f, wanUnit)
        yuan >= 1_000_000f && mUnit.isNotEmpty() ->
            String.format(locale, "%.1f%s", yuan / 1_000_000f, mUnit)
        yuan >= 1000f -> String.format(locale, "%.1f%s", yuan / 1000f, kUnit)
        // 不足 1 元的柱子保留一位小数：整取会显示「0」，柱子却立在那，图表撒谎
        yuan < 1f -> String.format(locale, "%.1f", yuan)
        else -> String.format(locale, "%.0f", yuan)
    }
}

/** 环形图的一个切片。 */
data class ChartSlice(val label: String, val value: Float)

/** 趋势柱状图的一个月份点。 */
data class MonthPoint(val label: String, val amountCents: Long, val isCurrent: Boolean)

/** 分类占比环形图（drawArc 描边成环，中心显示总额）。[sliceColors] 可逐片指定配色，
 *  缺省用 [ChartPalette]（默认图表色板）。[sliceDescription] 可自定义每片读屏文案：
 *  默认「label 数值」，金额类图表传它换成本地化分类名 + 格式化金额。 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    sliceColors: List<Color>? = null,
    sliceDescription: ((ChartSlice) -> String)? = null
) {
    // TalkBack 读出每类占比与中心数值（中心可能是金额合计，也可能是心情天数——
    // 统一用「标题: 数值」格式，避免把心情天数误读成「合计」）
    val desc = stringResource(
        R.string.chart_donut_desc,
        slices.joinToString(", ") { slice ->
            sliceDescription?.invoke(slice) ?: "${slice.label} ${slice.value.toInt()}"
        },
        "$centerTitle: $centerValue"
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

/** 近 6 个月趋势柱状图：当前月主色高亮，柱顶显示金额缩写。
 *  触控柱子弹出完整金额气泡（手指按住/拖动跟随）；
 *  原地轻点柱子触发 [onBarTap]（跳转到该月明细）。 */
@Composable
fun TrendBarChart(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier,
    onBarTap: ((Int) -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val wanUnit = stringResource(R.string.chart_abbrev_wan)
    val kUnit = stringResource(R.string.chart_abbrev_k)
    val mUnit = stringResource(R.string.chart_abbrev_m)
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // 气泡里的完整金额：主色加粗，与缩写标签区分开
    val amountPaint = remember(primary) {
        android.graphics.Paint().apply {
            color = primary.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    // TalkBack 读出各月金额
    val desc = stringResource(
        R.string.chart_trend_desc,
        points.joinToString(", ") { "${it.label} ${abbrevYuan(it.amountCents, wanUnit, kUnit, mUnit)}" }
    )
    // 柱生长动画：数据变化时从底部一起长高，金额标签跟随柱顶
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    // 触控选中的月份（按住/拖动跟随，松手后保留；数据变化时重置）
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val view = LocalView.current
    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .pointerInput(points, onBarTap) {
                if (points.isEmpty()) return@pointerInput
                // 轻点跳转只认「画了柱子的月份」：按柱体矩形判断，空白区/
                // 0 金额月份的标签区不响应，避免想滚动却跳走（拖动查值不受限）
                fun barRect(index: Int): Rect? {
                    if (index !in points.indices) return null
                    if (points[index].amountCents <= 0L) return null
                    val max = points.maxOf { it.amountCents }
                    val slotW = size.width.toFloat() / points.size
                    val barW = slotW * 0.5f
                    val chartH = (size.height.toFloat() - 68f).coerceAtLeast(1f)
                    val barH = points[index].amountCents.toFloat() / max * chartH
                    val left = slotW * index + (slotW - barW) / 2f
                    val top = 34f + (chartH - barH)
                    return Rect(left, top, left + barW, top + barH)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 不消费事件：页面纵向滚动不受影响（拖动只取 x 定位柱位）
                    val slotW = size.width.toFloat() / points.size
                    val downIndex = (down.position.x / slotW).toInt()
                        .coerceIn(0, points.size - 1)
                    // 位移超过触控阈值视为「拖动查数值」，否则松手即「点按跳转」；
                    // x/y 双向都算：手指落在柱子上纵向滚动页面时不算点按
                    val slop = 8.dp.toPx()
                    var moved = false
                    var sel = downIndex
                    selectedIndex = sel
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) {
                            if (!moved && onBarTap != null) {
                                // 松手位置必须在柱体矩形内才算「点柱子」：
                                // 起点在柱上但滑出柱体同样不算
                                val rect = barRect(sel)
                                if (rect != null && rect.contains(change.position)) {
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.KEYBOARD_TAP
                                    )
                                    onBarTap(sel)
                                }
                            }
                            break
                        }
                        if (abs(change.position.x - down.position.x) > slop ||
                            abs(change.position.y - down.position.y) > slop
                        ) {
                            moved = true
                        }
                        sel = (change.position.x / slotW).toInt()
                            .coerceIn(0, points.size - 1)
                        selectedIndex = sel
                    }
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas
        // 按 density 换算，适配不同屏幕文字密度
        textPaint.textSize = 10.sp.toPx()
        amountPaint.textSize = 12.sp.toPx()
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
                    abbrevYuan(p.amountCents, wanUnit, kUnit, mUnit),
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
        // 选中月份：柱描主色边 + 顶部气泡显示完整金额
        selectedIndex?.let { sel ->
            if (sel in points.indices) {
                val p = points[sel]
                if (p.amountCents > 0L) {
                    val barHeight = p.amountCents.toFloat() / max * chartHeight * anim
                    val left = slotWidth * sel + (slotWidth - barWidth) / 2f
                    val top = topLabelHeight + (chartHeight - barHeight)
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(left - 2f, top - 2f),
                        size = Size(barWidth + 4f, barHeight + 4f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(width = 2f)
                    )
                    drawValueCallout(
                        centerX = left + barWidth / 2f,
                        anchorY = top,
                        line1 = MoneyUtils.formatCents(p.amountCents),
                        line2 = p.label,
                        amountPaint = amountPaint,
                        textPaint = textPaint,
                        fill = surfaceColor,
                        border = outlineColor
                    )
                }
            }
        }
    }
}

/** 本月每日走势：折线 + 渐变填充面积图，X 轴抽样日期标签。
 *  [todayIndex] 非空时在该数据点画主色实心圆（查看当月时标记「今天」）。
 *  触控任意日期弹出当日金额气泡（手指按住/拖动跟随）。 */
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
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // 气泡里的金额：主色加粗，与日期行区分开
    val amountPaint = remember(primary) {
        android.graphics.Paint().apply {
            color = primary.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val daySuffix = stringResource(R.string.chart_touch_day)
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
    // 触控选中的日期（按住/拖动跟随，松手后保留；数据变化时重置）
    var selectedIndex by remember(dailyCents) { mutableStateOf<Int?>(null) }
    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .pointerInput(dailyCents) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 不消费事件：页面纵向滚动不受影响（拖动只取 x 定位日期）
                    fun indexAt(x: Float): Int {
                        val n = dailyCents.size
                        if (n <= 1) return 0
                        return (x / (size.width.toFloat() / (n - 1)))
                            .roundToInt()
                            .coerceIn(0, n - 1)
                    }
                    selectedIndex = indexAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        selectedIndex = indexAt(change.position.x)
                    }
                }
            }
    ) {
        if (dailyCents.isEmpty()) return@Canvas
        // 按 density 换算，适配不同屏幕文字密度
        textPaint.textSize = 9.sp.toPx()
        amountPaint.textSize = 11.sp.toPx()
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
                val label = day.toString()
                // 首日/末日标签居中绘在画布边缘会被裁掉一半：横向内移半个字符宽
                val halfW = textPaint.measureText(label) / 2f
                val x = pointAt(index).x.coerceIn(halfW, size.width - halfW)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    size.height - 4f,
                    textPaint
                )
            }
            // 选中日期：竖向参考线 + 高亮圆 + 金额气泡
            selectedIndex?.let { sel ->
                if (sel in dailyCents.indices) {
                    val p = pointAt(sel)
                    drawLine(
                        color = primary.copy(alpha = 0.35f),
                        start = Offset(p.x, 0f),
                        end = Offset(p.x, chartHeight),
                        strokeWidth = 2f
                    )
                    drawCircle(color = surfaceColor, radius = 9f, center = p)
                    drawCircle(color = primary, radius = 6f, center = p)
                    drawValueCallout(
                        centerX = p.x,
                        anchorY = p.y,
                        line1 = MoneyUtils.formatCents(dailyCents[sel]),
                        line2 = String.format(Locale.getDefault(), daySuffix, sel + 1),
                        amountPaint = amountPaint,
                        textPaint = textPaint,
                        fill = surfaceColor,
                        border = outlineColor
                    )
                }
            }
        }
    }
}

/** 预算趋势图的一个月份点：当月支出 + 生效预算（0 = 该月未设置预算，折线断开）。 */
data class BudgetMonthPoint(
    val label: String,          // 月份短标签（如 "8月" / "Aug"）
    val spendingCents: Long,
    val budgetCents: Long,      // 0 = 该月没有预算
    val isCurrent: Boolean
)

/** 月度预算/支出双折线图：蓝色预算线（未设置预算的月份断开）、红色支出线 + 渐变面积。
 *  触控任意月份弹出气泡（支出金额 + 该月预算），按住/拖动跟随，松手后保留。 */
@Composable
fun BudgetTrendChart(
    points: List<BudgetMonthPoint>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val expenseColor = expenseAmountColor()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val notSetText = stringResource(R.string.budget_not_set)
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // 气泡里的支出金额：红色加粗，一眼分清「花的」和「预算的」
    val amountPaint = remember(expenseColor) {
        android.graphics.Paint().apply {
            color = expenseColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    // TalkBack 逐月读出支出与预算（未设置的月份报「未设置」）
    val entryFmt = stringResource(R.string.chart_budget_entry)
    val desc = stringResource(
        R.string.chart_budget_desc,
        points.joinToString(", ") { p ->
            String.format(
                Locale.getDefault(), entryFmt, p.label,
                MoneyUtils.formatCents(p.spendingCents),
                if (p.budgetCents > 0L) MoneyUtils.formatCents(p.budgetCents) else notSetText
            )
        }
    )
    // 从左向右展开动画：两条线像被「画」出来
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    // 触控选中的月份（按住/拖动跟随，松手后保留；数据变化时重置）
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val calloutBudgetFmt = stringResource(R.string.chart_budget_callout_budget)
    val calloutNoneText = stringResource(R.string.chart_budget_callout_none)
    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .pointerInput(points) {
                if (points.isEmpty()) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 不消费事件：页面纵向滚动不受影响（拖动只取 x 定位月份）
                    fun indexAt(x: Float): Int {
                        val n = points.size
                        if (n <= 1) return 0
                        return (x / (size.width.toFloat() / (n - 1)))
                            .roundToInt()
                            .coerceIn(0, n - 1)
                    }
                    selectedIndex = indexAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        selectedIndex = indexAt(change.position.x)
                    }
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas
        // 按 density 换算，适配不同屏幕文字密度
        textPaint.textSize = 9.sp.toPx()
        amountPaint.textSize = 11.sp.toPx()
        val max = points
            .flatMap { listOf(it.spendingCents, it.budgetCents) }
            .maxOrNull() ?: 0L
            .coerceAtLeast(1L)
        val topPad = 30f
        val bottomPad = 26f
        val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

        fun xFor(index: Int): Float = (stepX * index).coerceIn(0f, size.width)
        fun yFor(cents: Long): Float =
            chartHeight - cents.toFloat() / max * (chartHeight * 0.85f) - chartHeight * 0.05f

        // 支出线（所有月份连通，0 支出落在底部）
        val spendingPath = Path()
        points.forEachIndexed { i, p ->
            val y = yFor(p.spendingCents)
            if (i == 0) spendingPath.moveTo(xFor(i), y) else spendingPath.lineTo(xFor(i), y)
        }
        // 预算线：只连「设置了预算」的月份，按连续段拆开
        val budgetPath = Path()
        var segmentOpen = false
        points.forEachIndexed { i, p ->
            if (p.budgetCents > 0L) {
                if (!segmentOpen) {
                    segmentOpen = true
                    budgetPath.moveTo(xFor(i), yFor(p.budgetCents))
                } else {
                    budgetPath.lineTo(xFor(i), yFor(p.budgetCents))
                }
            } else {
                segmentOpen = false
            }
        }
        clipRect(right = size.width * progress.value) {
            // 支出渐变面积（低透明：面积是参考，折线才是焦点）
            val fillPath = Path().apply {
                addPath(spendingPath)
                lineTo(xFor(points.size - 1), chartHeight)
                lineTo(0f, chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(expenseColor.copy(alpha = 0.12f), expenseColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = chartHeight
                )
            )
            drawPath(
                path = spendingPath,
                color = expenseColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            drawPath(
                path = budgetPath,
                color = primary,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            // 预算点：设置的月份画实心圆，一眼看到「哪个月定了预算」
            points.forEachIndexed { i, p ->
                if (p.budgetCents > 0L) {
                    drawCircle(primary, radius = 4f, center = Offset(xFor(i), yFor(p.budgetCents)))
                }
            }
            // 支出点：当前月表面色描边放大，其余小点
            points.forEachIndexed { i, p ->
                val c = Offset(xFor(i), yFor(p.spendingCents))
                if (p.isCurrent) {
                    drawCircle(color = surfaceColor, radius = 6f, center = c)
                    drawCircle(color = expenseColor, radius = 3.5f, center = c)
                } else {
                    drawCircle(color = expenseColor, radius = 3f, center = c)
                }
            }
            // 月份标签：首末月横向内移半个字符宽，避免贴边被裁
            points.forEachIndexed { i, p ->
                val halfW = textPaint.measureText(p.label) / 2f
                val x = xFor(i).coerceIn(halfW, size.width - halfW)
                drawContext.canvas.nativeCanvas.drawText(p.label, x, size.height - 4f, textPaint)
            }
            // 选中月份：竖向参考线 + 气泡（支出金额 + 该月预算）
            selectedIndex?.let { sel ->
                if (sel in points.indices) {
                    val p = points[sel]
                    val x = xFor(sel)
                    drawLine(
                        color = primary.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 2f
                    )
                    val budgetLine = if (p.budgetCents > 0L) {
                        String.format(
                            Locale.getDefault(), calloutBudgetFmt,
                            MoneyUtils.formatCents(p.budgetCents)
                        )
                    } else {
                        calloutNoneText
                    }
                    drawValueCallout(
                        centerX = x,
                        anchorY = yFor(p.spendingCents),
                        line1 = MoneyUtils.formatCents(p.spendingCents),
                        line2 = "${p.label} · $budgetLine",
                        amountPaint = amountPaint,
                        textPaint = textPaint,
                        fill = surfaceColor,
                        border = outlineColor
                    )
                }
            }
        }
    }
}

/** 图表触控值气泡：圆角底 + 金额/日期两行文字，自动避开画布左右边缘，贴顶时翻到下方。 */
private fun DrawScope.drawValueCallout(
    centerX: Float,
    anchorY: Float,
    line1: String,
    line2: String,
    amountPaint: android.graphics.Paint,
    textPaint: android.graphics.Paint,
    fill: Color,
    border: Color
) {
    val paddingH = 10f
    val paddingV = 6f
    val lineGap = 2f
    val w1 = amountPaint.measureText(line1)
    val w2 = textPaint.measureText(line2)
    // 气泡宽度封顶为画布宽度减 12dp：窄屏 + 超长金额时 coerceIn 下界会上穿
    // 上界直接抛异常（IllegalArgumentException），先收窄再夹取
    val width = (maxOf(w1, w2) + paddingH * 2)
        .coerceAtMost((size.width - 12f).coerceAtLeast(1f))
    val height = amountPaint.textSize + textPaint.textSize + lineGap + paddingV * 2
    val left = (centerX - width / 2f).coerceIn(6f, size.width - width - 6f)
    // 默认在锚点上方，贴顶时翻到下方（气泡不越出画布）
    var top = anchorY - height - 12f
    if (top < 4f) top = anchorY + 14f
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 1f)
    )
    val line1Baseline = top + paddingV + amountPaint.textSize
    drawContext.canvas.nativeCanvas.drawText(line1, left + width / 2f, line1Baseline, amountPaint)
    drawContext.canvas.nativeCanvas.drawText(
        line2,
        left + width / 2f,
        line1Baseline + lineGap + textPaint.textSize,
        textPaint
    )
}
