package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.launch

/**
 * 周选择器：年份切换栏 + 周网格（每格显示周序号与起止日期），点击即选中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPickerModal(
    visible: Boolean,
    initialYear: Int,
    initialWeek: Int,
    onWeekSelected: (year: Int, week: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var shownYear by remember(initialYear) { mutableIntStateOf(initialYear) }
    val gridState = rememberLazyGridState()

    val (currentYear, currentWeek) = remember { DateTimeUtils.getCurrentYearWeek() }
    val maxWeeks = DateTimeUtils.getMaxWeeksInYear(shownYear)

    // 打开时定位到当前选中周所在位置
    LaunchedEffect(Unit) {
        val targetIndex = (initialWeek - 1).coerceIn(0, maxWeeks - 1).coerceAtLeast(0)
        gridState.scrollToItem(targetIndex)
    }

    fun goToCurrentWeek() {
        shownYear = currentYear
        coroutineScope.launch {
            val max = DateTimeUtils.getMaxWeeksInYear(currentYear)
            gridState.scrollToItem((currentWeek - 1).coerceIn(0, max - 1).coerceAtLeast(0))
        }
    }

    fun selectWeek(week: Int) {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } finally {
                onWeekSelected(shownYear, week)
                onDismiss()
            }
        }
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择查看周",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 年份切换栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shownYear -= 1 }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一年")
                }

                Text(
                    text = "${shownYear} 年",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )

                IconButton(onClick = { shownYear += 1 }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一年")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 $maxWeeks 周 · 点击周次即可查看",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                TextButton(
                    onClick = { goToCurrentWeek() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("回到本周")
                }
            }

            // 周网格
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(maxWeeks) { index ->
                    val week = index + 1
                    val isSelected = shownYear == initialYear && week == initialWeek
                    val isCurrent = shownYear == currentYear && week == currentWeek
                    val rangeText = DateTimeUtils.formatWeekRangeShort(
                        DateTimeUtils.getWeekStartTimestamp(shownYear, week),
                        DateTimeUtils.getWeekEndTimestamp(shownYear, week)
                    )

                    Column(
                        modifier = Modifier
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                            .clickable { selectWeek(week) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "第${week}周",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        )
                        Text(
                            text = rangeText,
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            }
                        )
                    }
                }
            }
        }
    }
}
