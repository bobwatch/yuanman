package com.yuanman.app.ui.screens.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

enum class AccountRecordFilter(val label: String) {
    ALL("全部"),
    EXPENSE("支出"),
    INCOME("收入"),
    TRANSFER("划转/还款"),
    ADJUSTMENT("平账")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailSheet(
    account: AccountEntity,
    records: List<RecordWithCategory>,
    privacyMode: Boolean,
    isDefaultExpense: Boolean = false,
    onToggleDefaultExpense: () -> Unit = {},
    onDismiss: () -> Unit,
    onTransfer: (AccountEntity) -> Unit,
    onEdit: (AccountEntity) -> Unit,
    onArchiveToggle: (AccountEntity) -> Unit = {}
) {
    val accType = AccountType.fromString(account.type)
    val isLiability = accType.isLiability
    val accColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    var selectedFilter by remember { mutableStateOf(AccountRecordFilter.ALL) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, selectedFilter) {
        when (selectedFilter) {
            AccountRecordFilter.ALL -> records
            AccountRecordFilter.EXPENSE -> records.filter { it.record.type == RecordType.EXPENSE.name && !it.record.isAdjustment }
            AccountRecordFilter.INCOME -> records.filter { it.record.type == RecordType.INCOME.name && !it.record.isAdjustment }
            AccountRecordFilter.TRANSFER -> records.filter { it.record.type == "TRANSFER" }
            AccountRecordFilter.ADJUSTMENT -> records.filter { it.record.isAdjustment }
        }
    }

    val totalOut = remember(records, account.id, isLiability) {
        records.filter {
            if (it.record.type == "TRANSFER" && isLiability) {
                it.record.targetAccountId == account.id
            } else {
                it.record.type == RecordType.EXPENSE.name || (it.record.type == "TRANSFER" && it.record.accountId == account.id)
            }
        }.sumOf { it.record.amount }
    }

    val totalIn = remember(records, account.id, isLiability) {
        records.filter {
            if (it.record.type == "TRANSFER" && isLiability) {
                it.record.accountId == account.id
            } else {
                it.record.type == RecordType.INCOME.name || (it.record.type == "TRANSFER" && it.record.targetAccountId == account.id)
            }
        }.sumOf { it.record.amount }
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 18.dp)
        ) {
            // 1. 账户信息大卡片
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.8.dp, accColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(accColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AccountIconHelper.getIcon(account.icon),
                                    contentDescription = null,
                                    tint = accColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = accColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = accType.groupTitle,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accColor
                                            ),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    if (account.isArchived) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "已归档",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (!account.includeInNetWorth) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "不计净资产",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (account.remark.isNotBlank()) {
                                    Text(
                                        text = account.remark,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showArchiveConfirmDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (account.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    contentDescription = if (account.isArchived) "恢复账户" else "归档账户",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onEdit(account) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "编辑账户",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = if (isLiability) "当前待还欠款" else "当前可用余额",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val displayText = if (privacyMode) "¥***" else if (isLiability) {
                                when {
                                    account.balanceCents > 0L -> "¥${MoneyUtils.centsToYuanString(account.balanceCents)}"
                                    account.balanceCents == 0L -> "¥0.00 (无欠款)"
                                    else -> "¥${MoneyUtils.centsToYuanString(-account.balanceCents)} (溢缴)"
                                }
                            } else {
                                "¥${MoneyUtils.centsToYuanString(account.balanceCents)}"
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    color = if (isLiability && account.balanceCents > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // 快速转账按钮
                        Button(
                            onClick = { onTransfer(account) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isLiability) "还款/划转" else "资金划转",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 设为默认支出账户切换卡片
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDefaultExpense) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(
                    0.6.dp,
                    if (isDefaultExpense) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleDefaultExpense() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isDefaultExpense) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (isDefaultExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isDefaultExpense) "当前默认支出账户" else "设为默认支出账户",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDefaultExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (isDefaultExpense) "首页闪电记账与未指定时优先自动记入此账户" else "点击开启，闪电记账与未指定账户时优先记入",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                    Switch(
                        checked = isDefaultExpense,
                        onCheckedChange = { onToggleDefaultExpense() },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 流水统计指示条与分类筛选 Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账户流水 (${records.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (records.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "入账: ${if (privacyMode) "¥***" else "¥${MoneyUtils.centsToYuanString(totalIn)}"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "出账: ${if (privacyMode) "¥***" else "¥${MoneyUtils.centsToYuanString(totalOut)}"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 筛选标签行
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AccountRecordFilter.values()) { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 流水列表
            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = if (records.isEmpty()) "该账户暂无关联流水" else "暂无符合条件的流水",
                        description = if (records.isEmpty()) "记账或转账时选择该账户，将在此聚合展示" else "尝试切换上方分类筛选",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredRecords, key = { it.record.id }) { item ->
                        AccountRecordItem(
                            item = item,
                            currentAccountId = account.id,
                            currentAccountIsLiability = isLiability,
                            privacyMode = privacyMode
                        )
                    }
                }
            }
        }
    }

    // 归档 / 恢复 确认对话框
    if (showArchiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmDialog = false },
            title = {
                Text(
                    text = if (account.isArchived) "恢复账户" else "归档账户",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (account.isArchived) {
                        "确定恢复「${account.name}」吗？恢复后将重新显示在日常账户列表和记账选择中。"
                    } else {
                        "确定归档「${account.name}」吗？归档后日常账户列表和记账时将不再展示该账户，但历史流水记录完好保留，可在底部「已归档账户」随时查看或恢复。"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveConfirmDialog = false
                        onArchiveToggle(account)
                    }
                ) {
                    Text(if (account.isArchived) "确认恢复" else "确认归档")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AccountRecordItem(
    item: RecordWithCategory,
    currentAccountId: Long,
    currentAccountIsLiability: Boolean,
    privacyMode: Boolean
) {
    val record = item.record
    val category = item.category
    val isExpense = record.type == RecordType.EXPENSE.name
    val isTransfer = record.type == "TRANSFER"
    val isAdjustment = record.isAdjustment

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isAdjustment) MaterialTheme.colorScheme.secondaryContainer
                            else if (isTransfer) MaterialTheme.colorScheme.tertiaryContainer
                            else if (isExpense) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAdjustment) Icons.Default.Tune
                        else if (isTransfer) Icons.Default.SwapHoriz
                        else CategoryIconHelper.getIcon(category?.iconName.orEmpty()),
                        contentDescription = null,
                        tint = if (isAdjustment) MaterialTheme.colorScheme.onSecondaryContainer
                        else if (isTransfer) MaterialTheme.colorScheme.onTertiaryContainer
                        else if (isExpense) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = if (isAdjustment) {
                            "平账校准"
                        } else if (isTransfer) {
                            if (currentAccountIsLiability) {
                                if (record.accountId == currentAccountId) "借入资金" else "还款"
                            } else if (record.accountId == currentAccountId) {
                                "转出资金"
                            } else {
                                "转入资金"
                            }
                        } else {
                            category?.name ?: "账单"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = DateTimeUtils.formatDateTime(record.recordTime),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        )
                        if (record.remark.isNotBlank()) {
                            Text(
                                text = "· ${record.remark}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 金额
            val isMoneyOut = if (isTransfer && currentAccountIsLiability) {
                record.targetAccountId == currentAccountId
            } else {
                isExpense || (isTransfer && record.accountId == currentAccountId)
            }
            val sign = if (isMoneyOut) "-" else "+"
            val color = if (isMoneyOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            Text(
                text = if (privacyMode) "$sign¥***" else "$sign¥${MoneyUtils.centsToYuanString(record.amount)}",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}
