package com.yuanman.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Restore
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuanmanDatePickerSheet(
    initialDateMillis: Long,
    onDateSelected: ((year: Int, month: Int, day: Int) -> Unit)? = null,
    onDateTimeSelected: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val initialCalendar = remember(initialDateMillis) {
        Calendar.getInstance().apply {
            timeInMillis = initialDateMillis
        }
    }

    // 选中的年月日
    var selectedYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH) + 1) } // 1-12
    var selectedDay by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }

    // 当前日历正在浏览的年月
    var viewingYear by remember { mutableIntStateOf(selectedYear) }
    var viewingMonth by remember { mutableIntStateOf(selectedMonth) }

    // 选中的时分
    val isDateTimeMode = onDateTimeSelected != null
    var selectedHour by remember { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }

    // 用于触发滚轮平滑回位的重置信号
    var resetTimeTrigger by remember { mutableIntStateOf(0) }

    // 是否展开完整月份日历网格
    var showFullMonthCalendar by remember { mutableStateOf(false) }

    // 计算最终毫秒时间戳
    val finalDateTimeMillis = remember(selectedYear, selectedMonth, selectedDay, isDateTimeMode, selectedHour, selectedMinute) {
        Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth - 1)
            set(Calendar.DAY_OF_MONTH, selectedDay)
            if (isDateTimeMode) {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    // 今天、昨天、前天快捷时间对象
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
    val dayBeforeCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -2) }

    fun isDateEqual(y: Int, m: Int, d: Int, target: Calendar): Boolean {
        return y == target.get(Calendar.YEAR) &&
               m == (target.get(Calendar.MONTH) + 1) &&
               d == target.get(Calendar.DAY_OF_MONTH)
    }

    val isToday = isDateEqual(selectedYear, selectedMonth, selectedDay, todayCal)
    val isYesterday = isDateEqual(selectedYear, selectedMonth, selectedDay, yesterdayCal)
    val isDayBefore = isDateEqual(selectedYear, selectedMonth, selectedDay, dayBeforeCal)

    fun selectDate(target: Calendar) {
        selectedYear = target.get(Calendar.YEAR)
        selectedMonth = target.get(Calendar.MONTH) + 1
        selectedDay = target.get(Calendar.DAY_OF_MONTH)
        viewingYear = selectedYear
        viewingMonth = selectedMonth
    }

    fun submitSelection() {
        if (onDateTimeSelected != null) {
            onDateTimeSelected(finalDateTimeMillis)
        } else {
            onDateSelected?.invoke(selectedYear, selectedMonth, selectedDay)
        }
        onDismiss()
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🌟 1. 顶栏：标题与取消按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDateTimeMode) "选择记账时间" else "选择日期",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.outline)
                }
            }

            // 🌟 2. 极速一键日期切换卡片（今天 / 昨天 / 前天 / 更多日历）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 今天
                QuickDateCard(
                    title = "今天",
                    subTitle = "${todayCal.get(Calendar.MONTH) + 1}月${todayCal.get(Calendar.DAY_OF_MONTH)}日",
                    isSelected = isToday,
                    modifier = Modifier.weight(1f),
                    onClick = { selectDate(todayCal) }
                )

                // 昨天
                QuickDateCard(
                    title = "昨天",
                    subTitle = "${yesterdayCal.get(Calendar.MONTH) + 1}月${yesterdayCal.get(Calendar.DAY_OF_MONTH)}日",
                    isSelected = isYesterday,
                    modifier = Modifier.weight(1f),
                    onClick = { selectDate(yesterdayCal) }
                )

                // 前天
                QuickDateCard(
                    title = "前天",
                    subTitle = "${dayBeforeCal.get(Calendar.MONTH) + 1}月${dayBeforeCal.get(Calendar.DAY_OF_MONTH)}日",
                    isSelected = isDayBefore,
                    modifier = Modifier.weight(1f),
                    onClick = { selectDate(dayBeforeCal) }
                )

                // 更多日历按钮
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (showFullMonthCalendar || (!isToday && !isYesterday && !isDayBefore)) {
                        primaryColor.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (showFullMonthCalendar || (!isToday && !isYesterday && !isDayBefore)) primaryColor.copy(alpha = 0.6f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFullMonthCalendar = !showFullMonthCalendar }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "更多日期",
                            modifier = Modifier.size(18.dp),
                            tint = if (showFullMonthCalendar || (!isToday && !isYesterday && !isDayBefore)) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!isToday && !isYesterday && !isDayBefore) "${selectedMonth}月${selectedDay}日" else "更多日期",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (showFullMonthCalendar || (!isToday && !isYesterday && !isDayBefore)) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 🌟 3. 中文本地化轻量级月历网格 (支持展开/折叠，自由选择任意日期)
            AnimatedVisibility(
                visible = showFullMonthCalendar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 年月导航条
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (viewingMonth == 1) {
                                        viewingMonth = 12
                                        viewingYear--
                                    } else {
                                        viewingMonth--
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
                            }

                            Text(
                                text = "${viewingYear}年${viewingMonth}月",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = {
                                    if (viewingMonth == 12) {
                                        viewingMonth = 1
                                        viewingYear++
                                    } else {
                                        viewingMonth++
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
                            }
                        }

                        // 星期表头 (高对比度清晰中文)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { dayName ->
                                Text(
                                    text = dayName,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }

                        // 当月日期网格
                        val firstDayOfWeek = remember(viewingYear, viewingMonth) {
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, viewingYear)
                                set(Calendar.MONTH, viewingMonth - 1)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }.let {
                                (it.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                            }
                        }
                        val daysInMonth = remember(viewingYear, viewingMonth) {
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, viewingYear)
                                set(Calendar.MONTH, viewingMonth - 1)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }.getActualMaximum(Calendar.DAY_OF_MONTH)
                        }

                        val totalSlots = firstDayOfWeek + daysInMonth
                        val rowCount = (totalSlots + 6) / 7

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (r in 0 until rowCount) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (c in 0..6) {
                                        val dayIndex = r * 7 + c - firstDayOfWeek + 1
                                        if (dayIndex in 1..daysInMonth) {
                                            val isDaySelected = viewingYear == selectedYear &&
                                                    viewingMonth == selectedMonth &&
                                                    dayIndex == selectedDay

                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.2f)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isDaySelected) primaryColor else Color.Transparent
                                                    )
                                                    .clickable {
                                                        selectedYear = viewingYear
                                                        selectedMonth = viewingMonth
                                                        selectedDay = dayIndex
                                                    }
                                            ) {
                                                Text(
                                                    text = dayIndex.toString(),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isDaySelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🌟 4. 上下平滑滚动式时分滚轮选择器 (Butter-Smooth Wheel Time Picker)
            if (isDateTimeMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 标题栏与快捷「当前时刻」回位按键
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = primaryColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "记账时分",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 快捷回位当前时刻按钮
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = primaryColor.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val now = Calendar.getInstance()
                                        selectedHour = now.get(Calendar.HOUR_OF_DAY)
                                        selectedMinute = now.get(Calendar.MINUTE)
                                        resetTimeTrigger++
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = primaryColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "当前时刻",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryColor
                                    )
                                }
                            }
                        }

                        // 🌟 丝滑双列上下滚动滚轮
                        WheelTimePicker(
                            selectedHour = selectedHour,
                            selectedMinute = selectedMinute,
                            resetTrigger = resetTimeTrigger,
                            onHourChanged = { selectedHour = it },
                            onMinuteChanged = { selectedMinute = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // 🌟 5. 底部信息预览与大确定按键 (Thumb-Friendly Confirm Button)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val previewFormat = if (isDateTimeMode) {
                    SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                } else {
                    SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                }
                Text(
                    text = "已选时间：${previewFormat.format(finalDateTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Button(
                    onClick = { submitSelection() },
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
 * 🌟 高质感双列上下滚动滚轮选择器 (Wheel Time Picker)
 */
@Composable
private fun WheelTimePicker(
    selectedHour: Int,
    selectedMinute: Int,
    resetTrigger: Int,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleCount: Int = 3
) {
    val totalHeight = itemHeight * visibleCount
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
        contentAlignment = Alignment.Center
    ) {
        // 中间选中项高亮底色与边框
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = primaryColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(itemHeight)
        ) {}

        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 小时滚轮 (00..23)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight),
                contentAlignment = Alignment.Center
            ) {
                SingleWheelColumn(
                    range = 0..23,
                    targetValue = selectedHour,
                    resetTrigger = resetTrigger,
                    onValueSelected = onHourChanged,
                    itemHeight = itemHeight,
                    suffix = "时"
                )
            }

            // 分隔冒号
            Text(
                text = ":",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // 分钟滚轮 (00..59)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight),
                contentAlignment = Alignment.Center
            ) {
                SingleWheelColumn(
                    range = 0..59,
                    targetValue = selectedMinute,
                    resetTrigger = resetTrigger,
                    onValueSelected = onMinuteChanged,
                    itemHeight = itemHeight,
                    suffix = "分"
                )
            }
        }
    }
}

/**
 * 🌟 极速丝滑且高亮时刻精准毫秒级对齐的单列滚轮
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleWheelColumn(
    range: IntRange,
    targetValue: Int,
    resetTrigger: Int,
    onValueSelected: (Int) -> Unit,
    itemHeight: Dp,
    suffix: String = ""
) {
    val density = LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) { with(density) { itemHeight.toPx() } }

    val count = range.count()
    val repeatCount = 1000
    val totalItems = count * repeatCount
    val initialCenter = (repeatCount / 2) * count + (targetValue - range.first).coerceIn(0, count - 1)

    // visibleCount = 3 时，初始首项索引为 initialCenter - 1，使得 targetValue 完美居中
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialCenter - 1).coerceAtLeast(0)
    )
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 🌟 精准计算物理几何中心对应的项：以滑动位移跨过 50% itemHeightPx 为界瞬间切换高亮与数值
    val currentCenteredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val additional = if (itemHeightPx > 0f) {
                (offset / itemHeightPx).roundToInt()
            } else 0
            listState.firstVisibleItemIndex + 1 + additional
        }
    }

    var lastReportedValue by remember { mutableIntStateOf(targetValue) }

    // 当用户滑动停止或吸附时，派发当前数值变化并触发轻微触觉反馈
    LaunchedEffect(currentCenteredIndex) {
        val computedValue = ((currentCenteredIndex % count) + count) % count + range.first
        if (computedValue != lastReportedValue) {
            lastReportedValue = computedValue
            onValueSelected(computedValue)
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    // 当外部点击「当前时刻」时，平滑动画回位到目标时刻
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            val targetBase = (listState.firstVisibleItemIndex / count) * count
            val targetIndex = targetBase + (targetValue - range.first) - 1
            listState.animateScrollToItem((targetIndex).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapFlingBehavior,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(totalItems) { index ->
            val value = (index % count) + range.first
            val isSelected = index == currentCenteredIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        val target = (index - 1).coerceAtLeast(0)
                        coroutineScope.launch {
                            listState.animateScrollToItem(target)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d %s", value, suffix),
                    fontSize = if (isSelected) 18.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickDateCard(
    title: String,
    subTitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (isSelected) primaryColor.copy(alpha = 0.6f) else Color.Transparent
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subTitle,
                fontSize = 11.sp,
                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.outline
            )
        }
    }
}
