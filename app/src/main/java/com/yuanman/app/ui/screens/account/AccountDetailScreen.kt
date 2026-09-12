@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

/** 超额警示琥珀 —— 与账户页警示同值 Color(0xFFFF9800) */
private val DetailAmberColor = Color(0xFFFF9800)

/** 本月收支卡默认预览行数，超出的部分折叠在「展开全部」之后 */
private const val MonthRecordsPreviewCount = 5

/**
 * 账户详情 二级页（账户 Tab 账户行点击进入）。
 *
 * 布局（自上而下，v0.0.4 重排：操作不再悬浮，记录卡不再无限铺开）：
 *  1. 概览卡：图标 / 名称 / 类型 / 对账状态 chip + 大余额（内联隐私小眼睛）+ 本月收入·支出·结余三列；
 *  2. 操作行：[资金对账]（待核对时转琥珀「去对账」）/ [转入转出] 两枚等宽主按钮；
 *  3. 本月收支卡：当月经该账户入出账的流水（默认预览 5 笔，可展开全部 / 收起）；
 *  4. 对账记录卡：卡头行尾「周期」胶囊（即改即存）→ 每期一条（对账日 / 差额 / 是否已校正期初），可删除
 *     （删除后最近核对时间回退，账目校正不撤销）。
 *
 * 顶栏右侧操作：编辑账户（铅笔）/ 删除账户（垃圾桶）；删除有二次确认，被攒钱计划圈为专款时 toast 拦截。
 * 状态栏 inset 由本页 Scaffold（contentWindowInsets = statusBars）统一处理。
 */
@Composable
fun AccountDetailScreen(
    viewModel: AccountViewModel,
    accountId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    val account = remember(uiState.accounts, accountId) {
        uiState.accounts.firstOrNull { it.id == accountId }
    }
    val privacy = uiState.isPrivacyMode

    // ---- 弹层状态 ----
    var showReconcileDialog by remember { mutableStateOf(false) }
    var showTransferSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showCycleSheet by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<ReconcileRecordUiModel?>(null) }
    var accountToDelete by remember { mutableStateOf(false) }
    var recordsExpanded by remember { mutableStateOf(false) }

    // 当月流水（本月收支卡数据；过滤口径与账户聚合一致：按支付方式命中账户）
    val monthRecords by viewModel.monthRecordsFlow.collectAsState()
    val accountMonthRecords = remember(monthRecords, account) {
        if (account == null) {
            emptyList()
        } else {
            monthRecords.filter { rw -> methodMatchesAccount(rw.record.paymentMethod, account) }
        }
    }
    val reconcilePending = account?.reconcileStatus?.isPending() == true

    val existingTypeLabels = remember(uiState.accounts) {
        uiState.accounts.map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = account?.name ?: "账户详情",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (account != null) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑账户",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { accountToDelete = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除账户",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (account == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyStateView(
                        title = "账户不存在或已删除",
                        description = "该账户可能已被删除，请返回账户页查看最新列表",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---- 1. 概览卡 ----
                    AccountDetailHeroCard(
                        account = account,
                        isPrivacyMode = privacy,
                        onTogglePrivacy = { viewModel.togglePrivacyMode() }
                    )

                    // ---- 2. 操作行（原右下角悬浮按钮收敛于此，滚动前即可触达）----
                    AccountDetailActionRow(
                        reconcilePending = reconcilePending,
                        onReconcile = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showReconcileDialog = true
                        },
                        onTransfer = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTransferSheet = true
                        }
                    )

                    // ---- 3. 本月收支明细（默认预览，可展开全部）----
                    MonthRecordsCard(
                        records = accountMonthRecords,
                        accountName = account.name,
                        isPrivacyMode = privacy,
                        expanded = recordsExpanded,
                        onToggleExpand = { recordsExpanded = !recordsExpanded }
                    )

                    // ---- 4. 对账记录卡（每期一条，卡头可改周期）----
                    ReconcileRecordCard(
                        records = account.reconcileRecords,
                        cycleLabel = (account.reconcileCycleOverride ?: uiState.globalReconcileCycle).label,
                        isPrivacyMode = privacy,
                        onOpenCycle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCycleSheet = true
                        },
                        onDeleteRecord = { recordToDelete = it }
                    )
                }
            }
        }
    }

    // ---- 对账周期 sheet（账户自定义覆盖；选择即生效）----
    if (showCycleSheet && account != null) {
        ReconcileCycleSheet(
            title = "对账周期 · ${account.name}",
            current = account.reconcileCycleOverride,
            globalCycle = uiState.globalReconcileCycle,
            allowFollowGlobal = true,
            onSelect = { cycle -> viewModel.setAccountReconcileCycleOverride(account.id, cycle) },
            onDismiss = { showCycleSheet = false }
        )
    }

    // ---- 资金对账对话框 ----
    if (showReconcileDialog) {
        AccountReconcileDialog(
            account = account!!,
            onDismiss = { showReconcileDialog = false },
            onConfirmReconcile = { actualCents, applyCorrection ->
                viewModel.reconcileAccount(account!!.id, actualCents, applyCorrection)
                showReconcileDialog = false
                toast.success(
                    if (applyCorrection) "已按实际余额校正期初基线" else "资金对账已记录"
                )
            }
        )
    }

    // ---- 快速转账 / 还款 ----
    if (showTransferSheet && account != null) {
        QuickTransferSheet(
            accounts = uiState.accounts,
            initialFromAccountId = if (account.balanceCents < 0L) null else account.id,
            initialToAccountId = if (account.balanceCents < 0L) account.id else null,
            onDismiss = { showTransferSheet = false },
            onConfirmTransfer = { fromId, toId, amountCents, remark ->
                viewModel.transfer(fromId, toId, amountCents, remark)
                showTransferSheet = false
                toast.success("转账记录成功")
            }
        )
    }

    // ---- 编辑账户（对账周期改由对账记录卡头「周期」胶囊设置，本表单不再内嵌下拉）----
    if (showEditSheet && account != null) {
        AddEditAccountSheet(
            accountToEdit = account,
            existingTypes = existingTypeLabels,
            onDismiss = { showEditSheet = false },
            onSave = { name, label, iconName, colorHex, balanceCents ->
                // 用最新账户快照合并保存，避免覆盖编辑过程中账户的其他改动（如对账周期）
                val latest = uiState.accounts.firstOrNull { it.id == account.id } ?: account
                val diff = balanceCents - latest.balanceCents
                viewModel.updateAccount(
                    latest.copy(
                        name = name,
                        label = label,
                        iconName = iconName,
                        colorHex = colorHex,
                        openingBalanceCents = latest.openingBalanceCents + diff,
                        balanceCents = balanceCents
                    )
                )
                showEditSheet = false
                toast.success("账户「$name」已更新")
            }
        )
    }

    // ---- 删除单条对账记录确认 ----
    recordToDelete?.let { record ->
        ConfirmDeleteDialog(
            visible = true,
            title = "删除对账记录",
            message = "确定删除这笔「" +
                DateTimeUtils.formatDate(record.asOfDate) + "」的对账记录吗？删除后最近核对时间回退；" +
                "若当时已校正期初基线，校正不撤销。",
            onConfirm = {
                account?.let { viewModel.deleteReconcileRecord(it.id, record.id) }
                recordToDelete = null
                toast.success("对账记录已删除")
            },
            onDismiss = { recordToDelete = null }
        )
    }

    // ---- 删除账户确认（含被计划占用拦截守卫）----
    if (accountToDelete) {
        ConfirmDeleteDialog(
            visible = true,
            title = "删除账户",
            message = "确定要删除账户「${account?.name}」吗？",
            onConfirm = {
                account?.let { target ->
                    val occupying = uiState.plans.firstOrNull { it.holderAccountId == target.id }
                    if (occupying != null) {
                        toast.info("「${target.name}」是攒钱计划「${occupying.name}」的专款账户，请先处理该计划")
                    } else {
                        viewModel.deleteAccount(target.id)
                        toast.success("账户已删除")
                        onBack()
                    }
                }
                accountToDelete = false
            },
            onDismiss = { accountToDelete = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 区块组件
// ---------------------------------------------------------------------------

/** 概览卡：身份行（图标/名称/类型 + 对账状态 chip）+ 大余额（内联隐私眼）+ 本月收入·支出·结余 */
@Composable
private fun AccountDetailHeroCard(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isNegative = account.balanceCents < 0L
    val masked = "¥ ••••"
    val balanceText = when {
        isPrivacyMode -> masked
        isNegative -> "-¥" + MoneyUtils.centsToYuanString(-account.balanceCents, withGrouping = true)
        else -> "¥" + MoneyUtils.centsToYuanString(account.balanceCents, withGrouping = true)
    }
    val netCents = account.inCents - account.outCents

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 行1：图标 + 名称/类型 + 对账状态 chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconView(
                    iconName = account.iconName,
                    colorHex = account.colorHex,
                    size = 42.dp,
                    iconSize = 21.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = account.label.ifBlank { "未设置类型" },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = scheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ReconcileStatusChip(status = account.reconcileStatus)
            }

            // 行2：余额大数字 + 隐私小眼睛（全局隐私开关，与账户页 hero 同源）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (!isPrivacyMode && isNegative) scheme.error else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                DetailPrivacyEye(isPrivacyMode = isPrivacyMode, onToggle = onTogglePrivacy)
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))

            // 行3：本月三列指标（收入 / 支出 / 结余）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailMetricColumn(
                    label = "本月收入",
                    value = if (isPrivacyMode) masked else "+¥" + MoneyUtils.centsToYuanString(account.inCents),
                    valueColor = scheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricDivider()
                DetailMetricColumn(
                    label = "本月支出",
                    value = if (isPrivacyMode) masked else "-¥" + MoneyUtils.centsToYuanString(account.outCents),
                    valueColor = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricDivider()
                DetailMetricColumn(
                    label = "本月结余",
                    value = when {
                        isPrivacyMode -> masked
                        netCents > 0L -> "+¥" + MoneyUtils.centsToYuanString(netCents)
                        netCents < 0L -> "-¥" + MoneyUtils.centsToYuanString(-netCents)
                        else -> "¥0.00"
                    },
                    valueColor = if (!isPrivacyMode && netCents < 0L) scheme.error else scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 对账状态 chip：FRESH 主色 / OVERDUE 琥珀 / 其余中性；文案由共享的 AccountReconcileStatus 提供 */
@Composable
private fun ReconcileStatusChip(status: AccountReconcileStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val toneColor = when (status.tone) {
        ReconcileTone.FRESH -> scheme.primary
        ReconcileTone.OVERDUE -> DetailAmberColor
        else -> scheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = toneColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, toneColor.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Text(
            text = status.text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = toneColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/** 隐私小眼睛：紧跟余额的 28dp 命中圆区，切换全局隐私模式 */
@Composable
private fun DetailPrivacyEye(
    isPrivacyMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isPrivacyMode) "显示金额" else "隐藏金额",
            tint = scheme.outline,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 指标列：小标签在上、加粗数值在下，居中；三列等宽由 weight 分配 */
@Composable
private fun DetailMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailMetricDivider() {
    VerticalDivider(
        modifier = Modifier.height(26.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * 操作行：[资金对账] + [转入转出] 两枚等宽按钮。
 * 待核对（OVERDUE / NEVER）时左侧按钮转琥珀「去对账」，与账户页提醒横幅同一套警示语言；
 * 常态为填充主色，保证「常看的操作」默认就是最强层级。
 */
@Composable
private fun AccountDetailActionRow(
    reconcilePending: Boolean,
    onReconcile: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (reconcilePending) {
            OutlinedButton(
                onClick = onReconcile,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DetailAmberColor.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DetailAmberColor.copy(alpha = 0.14f),
                    contentColor = DetailAmberColor
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("去对账", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        } else {
            Button(
                onClick = onReconcile,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("资金对账", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        OutlinedButton(
            onClick = onTransfer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "转入转出",
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = scheme.primary
            )
        }
    }
}

/** 本月收支卡：卡头笔数 + 流水清单（默认预览 5 笔，超出可展开全部 / 收起） */
@Composable
private fun MonthRecordsCard(
    records: List<RecordWithCategory>,
    accountName: String,
    isPrivacyMode: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val collapsible = records.size > MonthRecordsPreviewCount
    val visibleRecords = if (expanded) records else records.take(MonthRecordsPreviewCount)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本月收支",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (records.isEmpty()) "本月暂无" else "共 ${records.size} 笔",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = scheme.outline,
                    maxLines = 1
                )
            }

            if (records.isEmpty()) {
                Text(
                    text = "本月还没有经该账户的收支：记账时把支付方式选成「$accountName」，流水就会出现在这里。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                visibleRecords.forEachIndexed { index, recordWithCategory ->
                    if (index > 0) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    AccountRecordRow(
                        recordWithCategory = recordWithCategory,
                        isPrivacyMode = isPrivacyMode
                    )
                }

                if (collapsible) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleExpand)
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expanded) "收起" else "展开全部 ${records.size} 笔",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = scheme.primary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 单条收支行：分类图标 + 名称 / 时间·备注 | 金额（支出 - / 收入 +，隐私掩码） */
@Composable
private fun AccountRecordRow(
    recordWithCategory: RecordWithCategory,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val record = recordWithCategory.record
    val category = recordWithCategory.category
    val isExpense = record.type == "EXPENSE"
    val amountText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        val yuan = MoneyUtils.centsToYuanString(record.amount)
        if (isExpense) "-¥$yuan" else "+¥$yuan"
    }
    val dayTime = DateTimeUtils.formatMonthDay(record.recordTime) + " " + DateTimeUtils.formatTime(record.recordTime)
    val subText = if (record.remark.isNotBlank()) "$dayTime · ${record.remark}" else dayTime

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconView(
            iconName = category?.iconName ?: "other",
            colorHex = category?.colorHex ?: 0xFF607D8BL,
            size = 32.dp,
            iconSize = 16.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: "未分类",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isPrivacyMode) scheme.onSurface else if (isExpense) scheme.onSurface else scheme.primary,
            maxLines = 1
        )
    }
}

/** 对账记录卡：卡头行尾「周期」胶囊（改周期即改即存）+ 每期一条（空态给引导文案） */
@Composable
private fun ReconcileRecordCard(
    records: List<ReconcileRecordUiModel>,
    cycleLabel: String,
    isPrivacyMode: Boolean,
    onOpenCycle: () -> Unit,
    onDeleteRecord: (ReconcileRecordUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "对账记录",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                CyclePill(cycleLabel = cycleLabel, onClick = onOpenCycle)
            }

            if (records.isEmpty()) {
                Text(
                    text = "还没有对账记录：点上方「资金对账」核对一次实际余额，每期结果都会保存在这里",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                records.forEachIndexed { index, record ->
                    if (index > 0) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    ReconcileRecordRow(
                        record = record,
                        isPrivacyMode = isPrivacyMode,
                        onDelete = { onDeleteRecord(record) }
                    )
                }
            }
        }
    }
}

/** 卡头「周期」胶囊：点击打开对账周期 sheet（账户自定义覆盖 ?: 跟随全局） */
@Composable
private fun CyclePill(
    cycleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "周期",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = scheme.outline,
                maxLines = 1
            )
            Text(
                text = cycleLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = scheme.onSurface,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "设置对账周期",
                tint = scheme.outline,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 单条对账记录行：对账日期 + 差额摘要（+ 已校正徽标）+ 行尾删除 */
@Composable
private fun ReconcileRecordRow(
    record: ReconcileRecordUiModel,
    isPrivacyMode: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val diffText = when {
        record.diffCents == 0L -> "无差异"
        isPrivacyMode -> "差额 ¥ ••••"
        record.diffCents > 0L -> "实际多出 ¥" + MoneyUtils.centsToYuanString(record.diffCents, withGrouping = true)
        else -> "实际缺少 ¥" + MoneyUtils.centsToYuanString(-record.diffCents, withGrouping = true)
    }
    val diffColor = when {
        record.diffCents == 0L || isPrivacyMode -> scheme.onSurfaceVariant
        record.diffCents > 0L -> scheme.primary
        else -> scheme.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DateTimeUtils.formatDate(record.asOfDate),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = diffText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = diffColor,
                    maxLines = 1
                )
                if (record.corrected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = scheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "已校正期初",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除该期记录",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
