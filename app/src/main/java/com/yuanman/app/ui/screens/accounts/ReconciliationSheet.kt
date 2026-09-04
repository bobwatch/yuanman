package com.yuanman.app.ui.screens.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountReconciliationItem
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.data.model.PeriodInfo
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationSheet(
    periodInfo: PeriodInfo,
    accounts: List<AccountEntity>,
    isLoading: Boolean = false,
    privacyMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (List<AccountReconciliationItem>, createAdjustmentRecords: Boolean) -> Unit
) {
    // 默认实际金额 = 账面金额
    val actualBalanceMap = remember {
        mutableStateMapOf<Long, Long>().apply {
            accounts.forEach { put(it.id, it.balanceCents) }
        }
    }

    var autoCreateAdjustment by remember { mutableStateOf(true) }
    var accountToAdjust by remember { mutableStateOf<AccountEntity?>(null) }

    val reconciliationItems = accounts.map { account ->
        val actual = actualBalanceMap[account.id] ?: account.balanceCents
        AccountReconciliationItem(
            account = account,
            bookBalanceCents = account.balanceCents,
            actualBalanceCents = actual,
            diffCents = actual - account.balanceCents
        )
    }

    val regularBookAsset = accounts.filter { !AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
        .sumOf { it.balanceCents }
    val liabilityBookSurplus = accounts.filter { AccountType.fromString(it.type).isLiability && it.includeInNetWorth && it.balanceCents < 0 }
        .sumOf { -it.balanceCents }
    val totalBookAsset = regularBookAsset + liabilityBookSurplus
    val totalBookDebt = accounts.filter { AccountType.fromString(it.type).isLiability && it.includeInNetWorth && it.balanceCents > 0 }
        .sumOf { it.balanceCents }
    val bookNetWorth = totalBookAsset - totalBookDebt

    val regularActualAsset = accounts.filter { !AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
        .sumOf { actualBalanceMap[it.id] ?: it.balanceCents }
    val liabilityActualSurplus = accounts.filter {
        val bal = actualBalanceMap[it.id] ?: it.balanceCents
        AccountType.fromString(it.type).isLiability && it.includeInNetWorth && bal < 0
    }.sumOf { -(actualBalanceMap[it.id] ?: it.balanceCents) }
    val totalActualAsset = regularActualAsset + liabilityActualSurplus
    val totalActualDebt = accounts.filter {
        val bal = actualBalanceMap[it.id] ?: it.balanceCents
        AccountType.fromString(it.type).isLiability && it.includeInNetWorth && bal > 0
    }.sumOf { actualBalanceMap[it.id] ?: it.balanceCents }
    val actualNetWorth = totalActualAsset - totalActualDebt
    val netWorthDiff = actualNetWorth - bookNetWorth

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            // 1. 顶栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "周期对账",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    )
                    Text(
                        text = "核对「${periodInfo.periodName}」各账户实际金额",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 对账数据实时汇总看板
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "当前记账净资产", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                            Text(
                                text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(bookNetWorth)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "实际总净资产", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                            Text(
                                text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(actualNetWorth)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (netWorthDiff != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    if (netWorthDiff != 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isPositive = netWorthDiff > 0
                        val sign = if (isPositive) "+" else "-"
                        val color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPositive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isPositive) "实际金额多于记账（可能漏记了收入）" else "实际金额少于记账（可能漏记了支出）",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color)
                                )
                                Text(
                                    text = if (privacyMode) "差额 ****" else "$sign¥${MoneyUtils.centsToYuanString(if (netWorthDiff < 0) -netWorthDiff else netWorthDiff)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 账户对账清单标题与一键核准
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账户列表（${accounts.size}）",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                TextButton(
                    onClick = {
                        accounts.forEach { actualBalanceMap[it.id] = it.balanceCents }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重置为记账金额", fontSize = 11.sp)
                }
            }

            // 4. 账户对账列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    val actual = actualBalanceMap[account.id] ?: account.balanceCents
                    val diff = actual - account.balanceCents
                    val hasDiff = diff != 0L

                    ReconciliationAccountCard(
                        account = account,
                        actualBalanceCents = actual,
                        diffCents = diff,
                        hasDiff = hasDiff,
                        privacyMode = privacyMode,
                        onClick = { accountToAdjust = account }
                    )
                }
            }

            // 5. 自动记账补齐差额开关（布局防变形，无缩放，间距自适应）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { autoCreateAdjustment = !autoCreateAdjustment }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动记账补齐差额",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "若实际金额与记账有出入，自动记一笔收支账单对齐余额",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoCreateAdjustment,
                        onCheckedChange = { autoCreateAdjustment = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. 确认完成对账按钮
            Button(
                onClick = { onConfirm(reconciliationItems, autoCreateAdjustment) },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("对账中…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("完成对账", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    // 针对单个账户输入实际金额的弹层
    if (accountToAdjust != null) {
        val target = accountToAdjust!!
        AdjustBalanceDialog(
            account = target,
            initialBalanceCents = actualBalanceMap[target.id] ?: target.balanceCents,
            privacyMode = privacyMode,
            onDismiss = { accountToAdjust = null },
            onConfirm = { newBalanceCents ->
                actualBalanceMap[target.id] = newBalanceCents
                accountToAdjust = null
            }
        )
    }
}

@Composable
private fun ReconciliationAccountCard(
    account: AccountEntity,
    actualBalanceCents: Long,
    diffCents: Long,
    hasDiff: Boolean,
    privacyMode: Boolean,
    onClick: () -> Unit
) {
    val accColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (hasDiff) 1.dp else 0.6.dp,
            if (hasDiff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                        .background(accColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AccountIconHelper.getIcon(account.icon),
                        contentDescription = null,
                        tint = accColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "记账：${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(account.balanceCents)}"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // 右侧核对后金额与差异胶囊
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "实际：${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(actualBalanceCents)}"}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (hasDiff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "修改实际金额",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }

                if (hasDiff) {
                    val isPositive = diffCents > 0
                    val sign = if (isPositive) "+" else "-"
                    val color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Text(
                        text = if (privacyMode) "差额 ****" else "差额 $sign¥${MoneyUtils.centsToYuanString(if (diffCents < 0) -diffCents else diffCents)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = color
                        )
                    )
                } else {
                    Text(
                        text = "金额一致",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustBalanceDialog(
    account: AccountEntity,
    initialBalanceCents: Long,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var inputYuanStr by remember {
        mutableStateOf(MoneyUtils.centsToYuanString(initialBalanceCents))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val isLiability = AccountType.fromString(account.type).isLiability

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "核对「${account.name}」实际金额",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "当前记账金额为 ${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(account.balanceCents)}"}，请输入目前的实际真实金额：",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )

                OutlinedTextField(
                    value = inputYuanStr,
                    onValueChange = {
                        inputYuanStr = it
                        errorMsg = null
                    },
                    prefix = { Text("¥ ", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )

                // 快捷填充按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable {
                            inputYuanStr = MoneyUtils.centsToYuanString(account.balanceCents)
                        }
                    ) {
                        Text(
                            text = if (privacyMode) "设为记账金额" else "记账金额（¥${MoneyUtils.centsToYuanString(account.balanceCents)}）",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { inputYuanStr = "0.00" }
                    ) {
                        Text(
                            text = "清零（¥0.00）",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // 负债账户溢缴（负余额）时切换符号，与账户编辑页行为一致
                    if (isLiability) {
                        val isNegative = inputYuanStr.trimStart().startsWith("-")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.clickable(enabled = inputYuanStr.isNotBlank()) {
                                inputYuanStr = if (isNegative) {
                                    inputYuanStr.trimStart().removePrefix("-")
                                } else {
                                    "-" + inputYuanStr.trimStart()
                                }
                                errorMsg = null
                            }
                        ) {
                            Text(
                                text = if (isNegative) "转待还（正数）" else "转溢缴（负数）",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val valid = if (isLiability) {
                            inputYuanStr.isBlank() || MoneyUtils.isValidSignedAmountInput(inputYuanStr)
                        } else {
                            inputYuanStr.isBlank() || MoneyUtils.isValidNonNegativeAmountInput(inputYuanStr)
                        }
                        if (inputYuanStr.isNotBlank() && !valid) {
                            errorMsg = if (isLiability) {
                                "请输入有效的金额（最多两位小数，负数表示溢缴）"
                            } else {
                                "请输入有效的非负金额（最多两位小数）"
                            }
                            return@Button
                        }
                        val cents = if (inputYuanStr.isBlank()) 0L else {
                            if (isLiability) MoneyUtils.parseSignedYuanToCents(inputYuanStr)
                            else MoneyUtils.parseYuanToCents(inputYuanStr)
                        }
                        onConfirm(cents)
                    } catch (e: Exception) {
                        errorMsg = "请输入正确的金额数字"
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
