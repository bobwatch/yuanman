package com.yuanman.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.WarmAffirmation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddRecord: (RecordType) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val defaultType by viewModel.defaultRecordType.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    // 长按快捷菜单状态
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddRecord(defaultType) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一笔", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 76.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部月度收支概览卡片 (玉润微光质感)
            MonthSummaryHeaderCard(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                totalExpense = uiState.summary.totalExpense,
                totalIncome = uiState.summary.totalIncome,
                balance = uiState.summary.balance,
                monthlyBudget = uiState.monthlyBudget,
                budgetUsedPercent = uiState.budgetUsedPercent,
                remainingBudgetCents = uiState.remainingBudgetCents,
                dailyAvailableCents = uiState.dailyAvailableCents,
                remainingDays = uiState.remainingDays,
                isPrivacyMode = uiState.isPrivacyMode,
                onTogglePrivacy = { viewModel.togglePrivacy() },
                onPrevMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onMonthClick = { showMonthPicker = true },
                onCardClick = onNavigateToStatistics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // 每日治愈温暖心语卡片
            AffirmationBannerCard(
                affirmation = uiState.affirmation,
                onRefresh = { viewModel.nextAffirmation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 账单明细列表
            if (uiState.groupedRecords.isEmpty()) {
                EmptyStateView(
                    title = "${uiState.selectedYear}年${uiState.selectedMonth}月暂无账单",
                    description = "点击下方「记一笔」按钮，记录生活中的每一笔美好与温度",
                    actionButtonText = "立即记一笔",
                    onActionClick = { onNavigateToAddRecord(defaultType) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    uiState.groupedRecords.forEach { (dayTimestamp, records) ->
                        val daySum = uiState.daySummaries[dayTimestamp] ?: Pair(0L, 0L)

                        item(key = "header_$dayTimestamp") {
                            DateGroupHeader(
                                timestamp = dayTimestamp,
                                totalExpense = daySum.first,
                                totalIncome = daySum.second
                            )
                        }

                        items(
                            items = records,
                            key = { "record_${it.record.id}" }
                        ) { item ->
                            RecordCardItem(
                                item = item,
                                isPrivacyMode = uiState.isPrivacyMode,
                                onClick = { onNavigateToDetail(item.record.id) },
                                onLongClick = { activeMenuRecord = item }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // 月份选择器弹窗
    MonthPickerModal(
        visible = showMonthPicker,
        initialYear = uiState.selectedYear,
        initialMonth = uiState.selectedMonth,
        onMonthSelected = { year, month ->
            viewModel.selectMonth(year, month)
            showMonthPicker = false
        },
        onDismiss = { showMonthPicker = false }
    )

    // 长按快捷操作底部弹层
    if (activeMenuRecord != null) {
        val target = activeMenuRecord!!
        ModalBottomSheet(
            onDismissRequest = { activeMenuRecord = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "账单操作 · ${target.category?.name ?: "未分类"} ¥${MoneyUtils.centsToYuanString(target.record.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 1. 再记一笔相同
                ListItem(
                    headlineContent = { Text("复制再记一笔") },
                    supportingContent = { Text("以此分类与金额为模板快速新增一条今日账单") },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                        }
                )

                // 2. 查看详情
                ListItem(
                    headlineContent = { Text("查看详情") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val id = target.record.id
                            activeMenuRecord = null
                            onNavigateToDetail(id)
                        }
                )

                // 3. 删除账单
                ListItem(
                    headlineContent = { Text("删除该账单", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            recordToDelete = target
                            activeMenuRecord = null
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 删除确认弹窗
    ConfirmDeleteDialog(
        visible = recordToDelete != null,
        title = "删除账单",
        message = "确定要删除分类为「${recordToDelete?.category?.name}」金额为「${MoneyUtils.formatCurrency(recordToDelete?.record?.amount ?: 0L)}」的账单吗？",
        onConfirm = {
            recordToDelete?.let { viewModel.deleteRecord(it) }
            recordToDelete = null
        },
        onDismiss = { recordToDelete = null }
    )
}

@Composable
fun MonthSummaryHeaderCard(
    year: Int,
    month: Int,
    totalExpense: Long,
    totalIncome: Long,
    balance: Long,
    monthlyBudget: Long,
    budgetUsedPercent: Float,
    remainingBudgetCents: Long,
    dailyAvailableCents: Long,
    remainingDays: Int,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryContainer.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                // 顶部：应用标语 + 隐私眼睛 + 月份选择器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "沅满记账",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )

                        // 隐私小眼睛
                        Surface(
                            shape = CircleShape,
                            color = primaryColor.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onTogglePrivacy() }
                        ) {
                            Icon(
                                imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "隐私模式",
                                tint = primaryColor,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(16.dp)
                            )
                        }

                        // 统计胶囊
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = "数据统计",
                                    tint = primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "统计 ›",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryColor
                                    )
                                )
                            }
                        }
                    }

                    // 月份快捷切换控件 ( < 2026年8月 > )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = onPrevMonth,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "上个月",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "${year}年${month}月",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMonthClick() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "下个月",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 中部：本月总支出
                Text(
                    text = "本月总支出 (元)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isPrivacyMode) "¥ ****" else MoneyUtils.centsToYuanString(totalExpense, withGrouping = true),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 月度预算进度温度计
                if (monthlyBudget > 0L) {
                    val budgetColor = when {
                        budgetUsedPercent <= 0.7f -> primaryColor
                        budgetUsedPercent <= 0.95f -> Color(0xFFFF9800) // 橙色适度节制
                        else -> MaterialTheme.colorScheme.error // 红色超支
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "月预算已用 ${(budgetUsedPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = budgetColor
                            )

                            Text(
                                text = if (isPrivacyMode) "剩余 ****" else "剩余 ¥${MoneyUtils.centsToYuanString(remainingBudgetCents.coerceAtLeast(0L))} · 今日建议 ¥${MoneyUtils.centsToYuanString(dailyAvailableCents)}/天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { budgetUsedPercent.coerceIn(0f, 1f) },
                            color = budgetColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(10.dp))

                // 底部两栏：收入 与 结余
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本月收入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPrivacyMode) "¥ ****" else MoneyUtils.formatCurrency(totalIncome, isExpense = false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "本月结余",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val balanceColor = when {
                            balance > 0L -> MaterialTheme.colorScheme.primary
                            balance < 0L -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val balanceText = if (isPrivacyMode) {
                            "¥ ****"
                        } else {
                            if (balance < 0L) "-${MoneyUtils.formatCurrency(-balance)}" else MoneyUtils.formatCurrency(balance)
                        }
                        Text(
                            text = balanceText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = balanceColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 每日治愈温暖心语卡片
 */
@Composable
fun AffirmationBannerCard(
    affirmation: WarmAffirmation,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onRefresh() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = affirmation.emoji,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedContent(
                    targetState = affirmation,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "affirmationAnim"
                ) { target ->
                    Column {
                        Text(
                            text = target.quote,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "换一句",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "换一句",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RecordCardItem(
    item: RecordWithCategory,
    isPrivacyMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val record = item.record
    val category = item.category
    val isExpense = record.type == RecordType.EXPENSE.name

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(item) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            CategoryIconView(
                iconName = category?.iconName ?: "other",
                colorHex = category?.colorHex ?: 0xFF607D8BL,
                size = 42.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 分类名称、备注、支付方式
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category?.name ?: "未分类",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (record.paymentMethod.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = record.paymentMethod,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeUtils.formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (record.remark.isNotBlank()) {
                        Text(
                            text = " · ${record.remark}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 金额展示
            AmountDisplay(
                amountInCents = record.amount,
                type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME,
                showSign = true,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                isPrivacyMode = isPrivacyMode
            )
        }
    }
}
