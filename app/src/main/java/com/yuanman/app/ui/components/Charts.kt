package com.yuanman.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.clickableDebounce
import com.yuanman.app.data.model.CategoryStatItem
import com.yuanman.app.data.model.DailyTrendItem
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 支出/收入分类占比高阶交互环形图
 */
private data class DonutSegment(
    val item: CategoryStatItem?,
    val label: String,
    val color: Color,
    val percentage: Float,
    val totalAmount: Long
)

@Composable
fun DonutChart(
    items: List<CategoryStatItem>,
    totalAmount: Long,
    centerTitle: String = "总支出",
    selectedCategory: CategoryStatItem? = null,
    onSelectCategory: (CategoryStatItem?) -> Unit = {},
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    chartSize: Dp = 176.dp
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(items, totalAmount) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    // 环形图保留全部分类，右侧明细列表负责承载大量分类的可读信息。
    val chartSegments = items.map { item ->
        DonutSegment(
            item = item,
            label = item.category.name,
            color = Color(item.category.colorHex),
            percentage = item.percentage,
            totalAmount = item.totalAmount
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(chartSize),
            contentAlignment = Alignment.Center
        ) {
            val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(chartSegments, totalAmount) {
                        detectTapGestures { offset ->
                            if (chartSegments.isEmpty() || totalAmount <= 0L) return@detectTapGestures

                            val widthF = size.width.toFloat()
                            val heightF = size.height.toFloat()
                            val centerX = widthF / 2f
                            val centerY = heightF / 2f
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                            val strokeWidthPx = strokeWidth.toPx()
                            val radius = (min(widthF, heightF) - strokeWidthPx - 16f) / 2f
                            val innerR = radius - strokeWidthPx / 2f
                            val outerR = radius + strokeWidthPx / 2f

                            if (dist < innerR) {
                                // 点击中心重置选择
                                onSelectCategory(null)
                                return@detectTapGestures
                            }

                            if (dist >= innerR && dist <= outerR + 12f) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < -90f) {
                                    angle += 450f
                                } else {
                                    angle += 90f
                                }

                                var currentAngle = 0f
                                for (segment in chartSegments) {
                                    val sweep = segment.percentage * 360f
                                    if (angle >= currentAngle && angle <= currentAngle + sweep) {
                                        val item = segment.item
                                        if (item == null) {
                                            onSelectCategory(null)
                                        } else if (selectedCategory?.category?.id == item.category.id) {
                                            onSelectCategory(null)
                                        } else {
                                            onSelectCategory(item)
                                        }
                                        return@detectTapGestures
                                    }
                                    currentAngle += sweep
                                }
                            }
                        }
                    }
            ) {
                val strokeWidthPx = strokeWidth.toPx()
                val radius = (min(size.width, size.height) - strokeWidthPx - 16f) / 2f
                val diameter = radius * 2f
                val arcSize = Size(diameter, diameter)
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                if (chartSegments.isEmpty() || totalAmount <= 0L) {
                    drawArc(
                        color = emptyColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx)
                    )
                } else {
                    var startAngle = -90f
                    val gapAngle = if (chartSegments.size > 1) 1.5f else 0f

                    chartSegments.forEach { segment ->
                        val isSelected = selectedCategory?.category?.id == segment.item?.category?.id
                        val currentStroke = if (isSelected) strokeWidthPx + 5f else strokeWidthPx
                        val sweepAngle = (segment.percentage * 360f * animatedProgress.value) - gapAngle

                        if (sweepAngle > 0f) {
                            val drawColor = if (selectedCategory != null && !isSelected) {
                                segment.color.copy(alpha = 0.35f)
                            } else {
                                segment.color
                            }

                            drawArc(
                                color = drawColor,
                                startAngle = startAngle + (gapAngle / 2),
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = currentStroke, cap = StrokeCap.Round)
                            )
                        }
                        startAngle += segment.percentage * 360f * animatedProgress.value
                    }
                }
            }

        // 中心文字区（点击可重置）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(150.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clip(CircleShape)
                .clickable { onSelectCategory(null) }
        ) {
            if (selectedCategory != null) {
                Text(
                    text = selectedCategory.category.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(selectedCategory.category.colorHex),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val amountStr = MoneyUtils.centsToYuanString(selectedCategory.totalAmount, withGrouping = true)
                Text(
                    text = "¥$amountStr",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (amountStr.length > 9) 15.sp else 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${String.format(java.util.Locale.CHINA, "%.1f", selectedCategory.percentage * 100)}% · ${selectedCategory.count}笔",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            } else {
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                val amountStr = MoneyUtils.centsToYuanString(totalAmount, withGrouping = true)
                Text(
                    text = "¥$amountStr",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (amountStr.length > 9) 15.sp else 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        }

        if (chartSegments.isNotEmpty()) {
            DonutLegend(
                segments = chartSegments,
                selectedCategory = selectedCategory,
                onSelectCategory = onSelectCategory,
                modifier = Modifier
                    .weight(1f)
                    .height(chartSize)
            )
        } else {
            Text(
                text = "暂无分类数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f).padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun DonutLegend(
    segments: List<DonutSegment>,
    selectedCategory: CategoryStatItem?,
    onSelectCategory: (CategoryStatItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 列表到达顶部/底部后消费剩余的拖动距离，避免外层统计页面被带着滚动。
    val edgeScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(0f, available.y)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(edgeScrollConnection),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(
                items = segments,
                key = { segment -> segment.item?.category?.id ?: segment.label }
            ) { segment ->
                val item = segment.item
                val isSelected = selectedCategory?.category?.id == item?.category?.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = item != null) {
                            if (item != null) onSelectCategory(if (isSelected) null else item)
                        },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) segment.color.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(segment.color)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = segment.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) segment.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${(segment.percentage * 100).coerceAtLeast(0f).let { String.format(java.util.Locale.CHINA, "%.1f", it) }}% · ¥${MoneyUtils.centsToYuanString(segment.totalAmount, withGrouping = true)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 每日支出趋势柱状图 (带有Y轴数值参考刻度、参考基准线与平滑交互)
 */
@Composable
fun BarTrendChart(
    items: List<DailyTrendItem>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    selectedBarColor: Color = MaterialTheme.colorScheme.tertiary
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "本月暂无每日消费明细",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val actualPeak = remember(items) { items.maxOfOrNull { it.expenseAmount } ?: 0L }
    val maxExpense = remember(actualPeak) { niceChartMaximum(actualPeak) }
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(items) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 顶部数值交互指示栏
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selectedIndex != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedIndex != null && selectedIndex!! in items.indices) {
                    val selected = items[selectedIndex!!]
                    Text(
                        text = "${selected.dateFormatted}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "¥${MoneyUtils.centsToYuanString(selected.expenseAmount, withGrouping = true)}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (selected.expenseAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                    )
                } else {
                    Text(
                        text = "当前周期峰值",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "¥${MoneyUtils.centsToYuanString(actualPeak, withGrouping = true)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 图表核心区：左侧Y轴刻度 + 右侧Canvas网格与柱状体
        val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        val defaultBarColor = barColor.copy(alpha = 0.85f)
        val chartCanvasHeight = height - 50.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartCanvasHeight),
            verticalAlignment = Alignment.Bottom
        ) {
            // Y轴数值参考标签列 (顶:最高金额, 中:中位数, 底:0)
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(bottom = 12.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "¥${MoneyUtils.centsToYuanString(maxExpense, withGrouping = false).substringBefore(".")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
                Text(
                    text = "¥${MoneyUtils.centsToYuanString(maxExpense / 2, withGrouping = false).substringBefore(".")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Text(
                    text = "¥0",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            // Canvas 绘制带数值网格线的柱状图
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(items) {
                        detectTapGestures { offset ->
                            val barWidthWithGap = size.width / items.size
                            val index = (offset.x / barWidthWithGap).toInt()
                            selectedIndex = if (index in items.indices) index else null
                        }
                    }
            ) {
                val maxHeight = size.height - 12.dp.toPx()
                val midHeight = maxHeight / 2f
                val count = items.size
                val barGap = 3.5f
                val totalGap = barGap * (count - 1)
                val barWidth = max((size.width - totalGap) / count, 3.5f)

                // 1. 绘制顶部 100% 参考线
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, 2.dp.toPx()),
                    end = Offset(size.width, 2.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )

                // 2. 绘制中部 50% 参考虚线
                drawLine(
                    color = gridLineColor.copy(alpha = 0.18f),
                    start = Offset(0f, midHeight),
                    end = Offset(size.width, midHeight),
                    strokeWidth = 1.dp.toPx()
                )

                // 3. 绘制底部 0 基准线
                drawLine(
                    color = gridLineColor.copy(alpha = 0.5f),
                    start = Offset(0f, maxHeight),
                    end = Offset(size.width, maxHeight),
                    strokeWidth = 1.5.dp.toPx()
                )

                // 4. 绘制每日柱体
                items.forEachIndexed { i, item ->
                    val x = i * (barWidth + barGap)
                    val barHeight = (item.expenseAmount.toFloat() / maxExpense) * (maxHeight - 4.dp.toPx()) * animatedProgress.value
                    val isSelected = selectedIndex == i

                    val color = when {
                        isSelected -> selectedBarColor
                        item.expenseAmount > 0L -> defaultBarColor
                        else -> gridLineColor.copy(alpha = 0.15f)
                    }

                    val currentHeight = if (barHeight < 3.dp.toPx() && item.expenseAmount > 0L) 4.dp.toPx() else barHeight

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, maxHeight - currentHeight),
                        size = Size(barWidth, currentHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }

        // X轴日期标签：直接使用 ViewModel 提供的周期语义，周/月/年不再混用“日”。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val keyIndexes = when {
                items.size <= 7 -> items.indices.toList()
                else -> listOf(0, items.lastIndex / 4, items.lastIndex / 2, items.lastIndex * 3 / 4, items.lastIndex).distinct()
            }
            keyIndexes.forEach { index ->
                Text(
                    text = items[index].dateFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun niceChartMaximum(value: Long): Long {
    if (value <= 0L) return 100L
    val magnitude = 10.0.pow(kotlin.math.floor(log10(value.toDouble())))
    val normalized = value / magnitude
    val step = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return ceil(step * magnitude).toLong().coerceAtLeast(value)
}

/**
 * 分类支出排行榜列表项（支持点击联动高亮）
 */
@Composable
fun CategoryRankItem(
    item: CategoryStatItem,
    rank: Int,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val categoryColor = Color(item.category.colorHex)
    val rankBadgeColor = when (rank) {
        1 -> Color(0xFFFFB300) // 金
        2 -> Color(0xFF90A4AE) // 银
        3 -> Color(0xFFBCAAA4) // 铜
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rankTextColor = when (rank) {
        1, 2, 3 -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val itemBgColor by animateColorAsState(
        targetValue = if (isSelected) categoryColor.copy(alpha = 0.12f) else Color.Transparent,
        label = "rankItemBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(itemBgColor)
            .clickableDebounce(debounceTimeMs = 500L, enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名序号
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(rankBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = rankTextColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 分类图标
        CategoryIconView(
            iconName = item.category.iconName,
            colorHex = item.category.colorHex,
            size = 30.dp,
            iconSize = 15.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 分类名称与笔数 + 进度条
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface
                    )
                )

                Text(
                    text = MoneyUtils.formatCurrency(item.totalAmount),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { item.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.count} 笔账单",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )

                val pctText = String.format(java.util.Locale.CHINA, "%.1f%%", item.percentage * 100)
                Text(
                    text = pctText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
