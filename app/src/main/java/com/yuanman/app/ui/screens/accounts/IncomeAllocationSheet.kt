package com.yuanman.app.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.*
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeAllocationSheet(
    accounts: List<AccountEntity>,
    savedRules: List<IncomeAllocationRule>,
    isLoading: Boolean = false,
    privacyMode: Boolean = false,
    onDismiss: () -> Unit,
    onSaveRules: (List<IncomeAllocationRule>) -> Unit,
    onExecuteAllocation: (sourceAccountId: Long, totalIncomeCents: Long, results: List<IncomeAllocationResultItem>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: 试算与执行, 1: 配置规则
    var sourceAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    // 资金操作不预填示例金额，避免用户打开页面后误触执行真实划转。
    var incomeAmountStr by remember { mutableStateOf("") }

    var currentRules by remember {
        mutableStateOf(
            if (savedRules.isNotEmpty()) savedRules
            else createDefaultRules(accounts)
        )
    }

    var showAddRuleDialog by remember { mutableStateOf(false) }

    val incomeCents = MoneyUtils.parseYuanToCents(incomeAmountStr)

    val allocationResults = remember(incomeCents, currentRules) {
        IncomeAllocationCalculator.calculate(incomeCents, currentRules)
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发薪分配",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab 切换
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("试算与分配", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("分配规则", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // 1. 试算与执行
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 入账源账户
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    val sourceAccount = accounts.firstOrNull { it.id == sourceAccountId }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = "${sourceAccount?.name ?: "请选择账户"}（${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(sourceAccount?.balanceCents ?: 0L)}"}）",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("发薪/入账源账户") },
                            trailingIcon = {
                                IconButton(onClick = { sourceMenuExpanded = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sourceMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name}（${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(acc.balanceCents)}"}）") },
                                    onClick = {
                                        sourceAccountId = acc.id
                                        sourceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 入账总金额
                    OutlinedTextField(
                        value = incomeAmountStr,
                        onValueChange = { incomeAmountStr = it },
                        label = { Text("本次收入总额（¥）") },
                        placeholder = { Text("例如 10000（请输入本次实际收入）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "根据配置规则自动分流明细：",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    // 分配结果列表
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allocationResults.forEach { item ->
                            val targetAcc = accounts.firstOrNull { it.id == item.rule.targetAccountId }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.rule.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "目标账户：${targetAcc?.name ?: "未指定"} · ${(item.actualPercentage * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                        )
                                    }

                                    Text(
                                        text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(item.allocatedAmountCents)}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (sourceAccountId > 0 && incomeCents > 0) {
                                onExecuteAllocation(sourceAccountId, incomeCents, allocationResults)
                            }
                        },
                        enabled = sourceAccountId > 0L && incomeCents > 0 && allocationResults.isNotEmpty() && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("分配中…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (incomeAmountStr.isBlank()) "请输入收入金额" else "一键转账分配",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // 2. 规则设置
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalPercent = currentRules.sumOf { (it.percentage * 100).toDouble() }.toInt()
                        Column {
                            Text(
                                text = "当前规则列表（总计 $totalPercent%）",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (totalPercent < 100) {
                                Text(
                                    text = "尚有 ${100 - totalPercent}% 收入未分配，将保留在发薪账户",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                currentRules = create503020Rules(accounts)
                                onSaveRules(currentRules)
                            }
                        ) {
                            Text("套用 50/30/20 模板", fontSize = 12.sp)
                        }
                    }

                    currentRules.forEachIndexed { index, rule ->
                        val targetAcc = accounts.firstOrNull { it.id == rule.targetAccountId }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
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
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "划入：${targetAcc?.name ?: "未关联"} · ${if (rule.type == AllocationRuleType.PERCENTAGE) "${(rule.percentage * 100).toInt()}%" else if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(rule.fixedAmountCents)}"}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val updated = currentRules.toMutableList().apply { removeAt(index) }
                                        currentRules = updated
                                        onSaveRules(updated)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除规则",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddRuleDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新增规则")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            accounts = accounts,
            onDismiss = { showAddRuleDialog = false },
            onAdd = { newRule ->
                val updated = currentRules + newRule
                currentRules = updated
                onSaveRules(updated)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
private fun AddRuleDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (IncomeAllocationRule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var percentageStr by remember { mutableStateOf("20") }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val targetAccount = accounts.firstOrNull { it.id == targetAccountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称（如：心愿储蓄、生活备用金）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = targetAccount?.name ?: "选择划入账户",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标划入账户") },
                        trailingIcon = {
                            IconButton(onClick = { accountMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { accountMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    targetAccountId = acc.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = percentageStr,
                    onValueChange = {
                        percentageStr = it
                        errorMessage = null
                    },
                    label = { Text("分配比例（%）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("10", "20", "30", "50").forEach { pct ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { percentageStr = pct }
                        ) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val percent = percentageStr.toFloatOrNull()
                    when {
                        name.isBlank() -> errorMessage = "请输入规则名称"
                        targetAccountId <= 0L -> errorMessage = "请选择目标账户"
                        percent == null || percent <= 0f || percent > 100f -> {
                            errorMessage = "请输入大于 0 且不超过 100 的比例"
                        }
                        else -> {
                            onAdd(
                                IncomeAllocationRule(
                                    name = name.trim(),
                                    targetAccountId = targetAccountId,
                                    targetAccountName = targetAccount?.name.orEmpty(),
                                    type = AllocationRuleType.PERCENTAGE,
                                    percentage = percent / 100f
                                )
                            )
                        }
                    }
                }
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun createDefaultRules(accounts: List<AccountEntity>): List<IncomeAllocationRule> {
    val checking = accounts.firstOrNull { AccountType.fromString(it.type) == AccountType.CHECKING } ?: accounts.firstOrNull()
    val investment = accounts.firstOrNull { AccountType.fromString(it.type) == AccountType.INVESTMENT } ?: accounts.getOrNull(1) ?: checking
    val savings = accounts.firstOrNull { it.id != checking?.id && it.id != investment?.id } ?: checking

    return listOf(
        IncomeAllocationRule(
            name = "生活日常开销",
            targetAccountId = checking?.id ?: 1L,
            targetAccountName = checking?.name.orEmpty(),
            percentage = 0.50f,
            sortOrder = 1
        ),
        IncomeAllocationRule(
            name = "投资定投",
            targetAccountId = investment?.id ?: 2L,
            targetAccountName = investment?.name.orEmpty(),
            percentage = 0.30f,
            sortOrder = 2
        ),
        IncomeAllocationRule(
            name = "储蓄备用金",
            targetAccountId = savings?.id ?: 1L,
            targetAccountName = savings?.name.orEmpty(),
            percentage = 0.20f,
            sortOrder = 3
        )
    )
}

private fun create503020Rules(accounts: List<AccountEntity>): List<IncomeAllocationRule> = createDefaultRules(accounts)
