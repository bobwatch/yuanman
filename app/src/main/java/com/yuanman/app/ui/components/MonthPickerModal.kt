package com.yuanman.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
    var selectedYear by remember(initialYear) { mutableIntStateOf(initialYear) }
    var selectedMonth by remember(initialMonth) { mutableIntStateOf(initialMonth) }

    val (currentYear, currentMonth) = remember { DateTimeUtils.getCurrentYearMonth() }

    ModalBottomSheet(
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
                text = "选择查看月份",
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
                IconButton(onClick = { selectedYear -= 1 }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一年")
                }

                Text(
                    text = "${selectedYear} 年",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )

                IconButton(onClick = { selectedYear += 1 }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一年")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1-12月网格选择
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(12) { index ->
                    val month = index + 1
                    val isSelected = selectedYear == selectedYear && selectedMonth == month
                    val isCurrent = selectedYear == currentYear && month == currentMonth

                    Box(
                        modifier = Modifier
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                            .clickable {
                                selectedMonth = month
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${month}月",
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 快捷操作和确定按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        selectedYear = currentYear
                        selectedMonth = currentMonth
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("回到本月")
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定")
                }
            }
        }
    }
}
