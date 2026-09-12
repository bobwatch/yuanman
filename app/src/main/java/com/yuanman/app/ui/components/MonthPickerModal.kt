package com.yuanman.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 🌟 现代双列滚轮年月选择器 (Wheel Month Picker BottomSheet)
 *
 * 适用于首页 (HomeScreen)、账单明细 (RecordListScreen)、数据统计 (StatisticsScreen) 等场景。
 * 特性：
 *  - 双列独立滚轮：左列年份 (当前年份-11 .. 当前年份)，右列月份 (1 .. 12月)
 *  - 惯性滑动 + 逐项自动精准对齐 (SnapFlingBehavior)
 *  - 居中物理高亮框 + 跨界微触觉振动 (HapticFeedback)
 *  - 支持一键「回到本月」平滑动画回位
 *  - 禁止未来月份选择校验
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerModal(
    visible: Boolean,
    initialYear: Int,
    initialMonth: Int,
    onMonthSelected: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val (currentYear, currentMonth) = remember { DateTimeUtils.getCurrentYearMonth() }

    // 年份数据池：以当前年份为基准，默认支持回看近 12 年，不展示未来年份
    val minYear = currentYear - 11
    val years = remember(currentYear) { (minYear..currentYear).toList() }

    var selectedYear by remember(initialYear) { mutableIntStateOf(initialYear.coerceIn(minYear, currentYear)) }
    val months = remember(selectedYear, currentYear, currentMonth) {
        if (selectedYear >= currentYear) (1..currentMonth).toList() else (1..12).toList()
    }
    var selectedMonth by remember(initialMonth, selectedYear) {
        val maxMonth = if (selectedYear >= currentYear) currentMonth else 12
        mutableIntStateOf(initialMonth.coerceIn(1, maxMonth))
    }

    // 重置/回到本月的动画触发器
    var resetTrigger by remember { mutableIntStateOf(0) }

    val isCurrentMonth = selectedYear == currentYear && selectedMonth == currentMonth
    val primaryColor = MaterialTheme.colorScheme.primary

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- 1. 顶部操作栏（取消 | 标题 | 回到本月）----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            sheetState.hide()
                        } finally {
                            onDismiss()
                        }
                    }
                }) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = "选择查看月份",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 回到本月快捷胶囊
                Surface(
                    onClick = {
                        selectedYear = currentYear
                        selectedMonth = currentMonth
                        resetTrigger++
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrentMonth) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        0.5.dp,
                        if (isCurrentMonth) primaryColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Today,
                            contentDescription = null,
                            tint = if (isCurrentMonth) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "本月",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isCurrentMonth) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 2. 核心双列上下滚动滚轮 (Wheel Picker) ----
            val itemHeight = 44.dp
            val visibleCount = 3
            val totalHeight = itemHeight * visibleCount

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
                contentAlignment = Alignment.Center
            ) {
                // 中间选中项高亮吸附底色与边框
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                ) {}

                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左列：年份滚轮
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(totalHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        FiniteWheelColumn(
                            items = years,
                            selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                            resetTrigger = resetTrigger,
                            onItemSelected = { index ->
                                if (index in years.indices) {
                                    val newYear = years[index]
                                    selectedYear = newYear
                                    if (newYear >= currentYear && selectedMonth > currentMonth) {
                                        selectedMonth = currentMonth
                                    }
                                }
                            },
                            itemHeight = itemHeight,
                            itemLabel = { "$it 年" }
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 右列：月份滚轮
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        FiniteWheelColumn(
                            items = months,
                            selectedIndex = months.indexOf(selectedMonth).coerceAtLeast(0),
                            resetTrigger = resetTrigger,
                            onItemSelected = { index ->
                                if (index in months.indices) {
                                    selectedMonth = months[index]
                                }
                            },
                            itemHeight = itemHeight,
                            itemLabel = { String.format("%02d 月", it) }
                        )
                    }
                }
            }

            // ---- 3. 底部选值提示与大确定按钮 ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选时间：${selectedYear} 年 ${String.format("%02d", selectedMonth)} 月",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                sheetState.hide()
                            } finally {
                                onMonthSelected(selectedYear, selectedMonth)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确定", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 🌟 紧凑丝滑单列有限滚轮选择器
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> FiniteWheelColumn(
    items: List<T>,
    selectedIndex: Int,
    resetTrigger: Int,
    onItemSelected: (Int) -> Unit,
    itemHeight: Dp,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) { with(density) { itemHeight.toPx() } }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 首项前置 1 个 itemHeight 的 padding，使索引 0 恰好处于视口物理中心
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 精准推导处于物理中轴线的当前选中项
    val currentCenteredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val additional = if (itemHeightPx > 0f) {
                (offset / itemHeightPx).roundToInt()
            } else 0
            (listState.firstVisibleItemIndex + additional).coerceIn(0, items.lastIndex)
        }
    }

    var lastReportedIndex by remember { mutableIntStateOf(selectedIndex) }

    // 当滚轮滑动停靠或换项时，触发触觉震动与数值同步
    LaunchedEffect(currentCenteredIndex) {
        if (currentCenteredIndex != lastReportedIndex) {
            lastReportedIndex = currentCenteredIndex
            onItemSelected(currentCenteredIndex)
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    // 响应外部重置信号，如点击「回到本月」
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            val target = selectedIndex.coerceIn(0, items.lastIndex)
            lastReportedIndex = target
            listState.animateScrollToItem(target)
        }
    }

    // 当外部选中项改变或列表项缩短（如切换到今年导致未来月份移除）时自动精准对齐
    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex)
        if (target != lastReportedIndex || listState.firstVisibleItemIndex > items.lastIndex) {
            lastReportedIndex = target
            listState.scrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapFlingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == currentCenteredIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = itemLabel(item),
                    fontSize = if (isSelected) 17.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
