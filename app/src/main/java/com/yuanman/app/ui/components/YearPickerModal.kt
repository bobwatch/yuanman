package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 年选择器：滚动年份网格，点击即选中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearPickerModal(
    visible: Boolean,
    initialYear: Int,
    onYearSelected: (year: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val (currentYear, _) = remember { DateTimeUtils.getCurrentYearMonth() }

    // 向下覆盖 40 年，向上留 1 年余量，保证历史数据年份也可达
    val floorYear = minOf(initialYear, currentYear) - 40
    val ceilingYear = maxOf(initialYear, currentYear) + 1
    val yearCount = ceilingYear - floorYear + 1
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        val targetIndex = (initialYear - floorYear).coerceIn(0, yearCount - 1)
        gridState.scrollToItem(targetIndex.coerceAtLeast(0))
    }

    fun selectYear(year: Int) {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } finally {
                onYearSelected(year)
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
                text = "选择查看年份",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(yearCount) { index ->
                    val year = floorYear + index
                    val isSelected = year == initialYear
                    val isCurrent = year == currentYear

                    Box(
                        modifier = Modifier
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                            .clickable { selectYear(year) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$year",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            )
                            if (isCurrent) {
                                Text(
                                    text = "今年",
                                    fontSize = 11.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { selectYear(currentYear) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("回到今年")
            }
        }
    }
}
