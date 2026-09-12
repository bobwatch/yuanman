@file:OptIn(ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.YuanmanPullRefreshIndicator
import com.yuanman.app.utils.MoneyUtils

/**
 * 沅满记账 · 账户 Tab
 *
 * 编排层职责：
 * 1. 顶部资产快照 Header（AccountSnapshotHero）：净资产大数字右侧内联隐私小眼睛；
 *    总资产行行尾 = 发薪分配 / 账户核对两个入口胶囊（经回调注入，未注入则不渲染）。
 * 2. 待核对提醒横条：作为列表首 item **随内容滚动**（不吸顶）；行尾 (X) 二次确认
 *    「跳过本期核对」后按周期静默（每账户各自周期期末恢复，持久化）。
 * 3. 滚动区 = 攒钱计划区块（区块头右钮「＋ 新建计划」+ 横向小卡行 / 空计划宽幽灵卡，
 *    点击即开新建表单；计划小卡：点击 → 计划详情二级页、长按 → 快捷操作面板
 *    [存一笔/取一笔/编辑/删除]）+ 资金账户区块
 *    （区块头「＋ 新建账户」+ 分组账户清单）。
 * 4. 账户行交互：**点击 → 账户详情二级页**（展示对账记录/周期/操作）；**长按 → 账户操作面板**
 *    （转账还款 / 资金对账 / 编辑 / 删除）；负余额行内保留 [还款] 快捷钮。
 *
 * 本文件不含任何类型内容判断：label 只透传字符串，还款由余额符号驱动。
 */
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onOpenAccountDetail: ((accountId: Long) -> Unit)? = null,
    onOpenPlanDetail: ((planId: Long) -> Unit)? = null,
    onOpenPaycheckRun: (() -> Unit)? = null,
    onOpenAccountReconcile: (() -> Unit)? = null,
    onOpenAssetPanorama: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    // ---- 弹层状态 ----
    var showAddSheet by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountUiModel?>(null) }
    var showTransferSheet by remember { mutableStateOf(false) }
    var transferFromId by remember { mutableStateOf<Long?>(null) }
    var transferToId by remember { mutableStateOf<Long?>(null) }
    var accountToReconcile by remember { mutableStateOf<AccountUiModel?>(null) }
    var accountInMenu by remember { mutableStateOf<AccountUiModel?>(null) } // 长按 → 操作面板
    var showPendingSheet by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountUiModel?>(null) }
    // 计划弹层：长按面板 → 存一笔/取一笔/编辑/删除（点击小卡则进详情页；头部「新建计划」直接开新建表单）
    var planInMenu by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var planDepositTarget by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var planWithdrawTarget by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var planFormVisible by remember { mutableStateOf(false) }
    var planFormEdit by remember { mutableStateOf<SavingPlanUiModel?>(null) } // 非空 = 编辑该计划
    var planPresetName by remember { mutableStateOf("") }
    var planPresetTargetCents by remember { mutableStateOf(0L) }
    var planPresetColorHex by remember { mutableStateOf<Long?>(null) }
    var planToDelete by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    // 「跳过本期核对」二次确认
    var showSkipConfirm by remember { mutableStateOf(false) }

    // ---- 派生数据（待核对 = 生效周期下 OVERDUE/NEVER 且未跳过本期；判定只出自共享口径）----
    val nowMs = remember { System.currentTimeMillis() }
    val pendingAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { account ->
            account.reconcileStatus.isPending() &&
                (account.reconcileTipSkipUntil ?: 0L) <= nowMs
        }
    }
    // 表单「已用类型」快捷 chips 数据源：全账户既有类型字符串，按账户列表首次出现排序
    val existingTypeLabels = remember(uiState.accounts) {
        uiState.accounts.map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    // 专款账户余额查询表（计划卡超额判定）
    val accountsById = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }

    // ---- 下拉刷新：轻量自绘指示器（保持既有行为）----
    val pullRefreshState = rememberPullToRefreshState(enabled = { !isRefreshing })

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pullRefreshState.isRefreshing) {
            pullRefreshState.endRefresh()
            toast.success("刷新成功")
        }
    }

    // ---- 导航/动作闭包 ----
    val openAccountDetail: (AccountUiModel) -> Unit = { account ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onOpenAccountDetail?.invoke(account.id)
    }
    val openPlanDetail: (SavingPlanUiModel) -> Unit = { plan ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onOpenPlanDetail?.invoke(plan.id)
    }
    val openPaycheckRun: () -> Unit = {
        onOpenPaycheckRun?.invoke() // 触觉反馈由 hero 入口胶囊自带，避免双重
    }
    val openAccountReconcile: () -> Unit = {
        onOpenAccountReconcile?.invoke()
    }
    val openAssetPanorama: () -> Unit = {
        onOpenAssetPanorama?.invoke()
    }
    // 打开新建计划表单：账户为空时不可建，给弱引导
    val openPlanCreate: () -> Unit = {
        if (uiState.accounts.isEmpty()) {
            toast.info("请先创建资金账户，再新建攒钱计划")
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            planFormEdit = null
            planPresetName = ""
            planPresetTargetCents = 0L
            planPresetColorHex = null
            planFormVisible = true
        }
    }
    // 打开新建计划表单（带预置心愿灵感参数）
    val openPlanCreateWithPreset: (String, Long, Long) -> Unit = { name, targetCents, colorHex ->
        if (uiState.accounts.isEmpty()) {
            toast.info("请先创建资金账户，再新建攒钱计划")
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            planFormEdit = null
            planPresetName = name
            planPresetTargetCents = targetCents
            planPresetColorHex = colorHex
            planFormVisible = true
        }
    }
    // 打开转账底包：方向由目标账户余额符号决定（负 → 还款：to=该账户；非负 → 转账：from=该账户）
    val openTransferFor: (AccountUiModel) -> Unit = { account ->
        if (account.balanceCents < 0L) {
            transferFromId = null
            transferToId = account.id
        } else {
            transferFromId = account.id
            transferToId = null
        }
        showTransferSheet = true
    }

    // 确认「跳过本期」：对待核对账户按各自周期期末静默
    val confirmSkipReconcile: () -> Unit = {
        showSkipConfirm = false
        viewModel.skipReconcileReminderFor(pendingAccounts.map { it.id })
        toast.info("已跳过本期核对提醒，可在账户页随时手动对账")
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 1. 净资产快照 Header（眼睛内联净资产行；发薪/核对入口胶囊在总资产行行尾）----
        AccountSnapshotHero(
            totalBalanceCents = uiState.totalBalanceCents,
            totalAssetCents = uiState.totalAssetCents,
            totalDebtCents = uiState.totalDebtCents,
            distributionSegments = uiState.distributionSegments,
            isPrivacyMode = uiState.isPrivacyMode,
            onTogglePrivacy = { viewModel.togglePrivacyMode() },
            onOpenPaycheckRun = onOpenPaycheckRun?.let { { openPaycheckRun() } },
            onOpenAccountReconcile = onOpenAccountReconcile?.let { { openAccountReconcile() } },
            onOpenAssetPanorama = onOpenAssetPanorama?.let { { openAssetPanorama() } }
        )

        // ---- 2. 账户清单区（下拉刷新手势作用域；待核对横幅为列表首项，随内容滚动）----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.accounts.isEmpty()) {
                // 空态：无账户时隐藏攒钱计划区块（无账户即无可建计划），下方可滚动保证下拉手势可达
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (pendingAccounts.isNotEmpty()) {
                        ReconcileReminderBanner(
                            pendingCount = pendingAccounts.size,
                            onClick = { showPendingSheet = true },
                            onDismiss = { showSkipConfirm = true },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        )
                    }
                    EmptyStateView(
                        title = "暂无资金账户",
                        description = "创建微信、支付宝、储蓄卡等账户，开始精细化资金管理",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        actionButtonText = "新建账户",
                        onActionClick = {
                            accountToEdit = null
                            showAddSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp) // 底部 96 避让悬浮导航 Dock
                ) {
                    // ---- 待核对提醒横条：列表首项，随滚动消失（不吸顶）----
                    if (pendingAccounts.isNotEmpty()) {
                        item(key = "reconcile_banner") {
                            ReconcileReminderBanner(
                                pendingCount = pendingAccounts.size,
                                onClick = { showPendingSheet = true },
                                onDismiss = { showSkipConfirm = true },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
                            )
                        }
                    }

                    // ---- 攒钱计划区块 ----
                    item(key = "plans_header") {
                        PlansSectionHeader(onNewPlanClick = openPlanCreate)
                    }
                    if (uiState.plans.isEmpty()) {
                        // 空计划态：灵感心愿卡片流（与有计划时的横排卡片同规格 152×108dp，0 像素跳版）
                        item(key = "plans_empty_row") {
                            PlansEmptyRow(
                                onNewPlanClick = openPlanCreate,
                                onSelectInspiration = { preset ->
                                    openPlanCreateWithPreset(
                                        preset.name,
                                        preset.targetAmountCents,
                                        preset.colorHex
                                    )
                                },
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    } else {
                        // 计划横排小卡（末尾无「新建」小卡，新建入口收敛到区块头按钮）
                        item(key = "plans_mini_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                items(uiState.plans, key = { it.id }) { plan ->
                                    PlanMiniCard(
                                        plan = plan,
                                        holderBalanceCents = accountsById[plan.holderAccountId]?.balanceCents,
                                        isDone = plan.targetAmountCents > 0L &&
                                            plan.earmarkedCents >= plan.targetAmountCents,
                                        isPrivacyMode = uiState.isPrivacyMode,
                                        onClick = { openPlanDetail(plan) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            planInMenu = plan
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ---- 资金账户区块：区块头（标题 + 「＋ 新建账户」）----
                    item(key = "accounts_section_header") {
                        AccountSectionHeader(
                            onAddClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                accountToEdit = null
                                showAddSheet = true
                            }
                        )
                    }

                    // ---- 账户分组流：组头 / 账户卡 ----
                    uiState.groups.forEach { group ->
                        item(key = "group_header_${group.label}") {
                            AccountGroupHeader(
                                groupLabel = group.label,
                                groupTotalCents = group.groupTotalCents,
                                isPrivacyMode = uiState.isPrivacyMode,
                                accentColorHex = group.accounts.firstOrNull()?.colorHex
                            )
                        }

                        items(
                            items = group.accounts,
                            key = { it.id }
                        ) { account ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                AccountItemCard(
                                    account = account,
                                    isPrivacyMode = uiState.isPrivacyMode,
                                    onClick = { openAccountDetail(account) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        accountInMenu = account
                                    },
                                    onQuickRepay = { repayTarget ->
                                        // 还款微通道：to 预填该负余额账户，from 交底包自动选首个正余额账户
                                        transferFromId = null
                                        transferToId = repayTarget.id
                                        showTransferSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 自绘轻量下拉刷新指示器（覆盖于清单/空态之上）
            if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) {
                YuanmanPullRefreshIndicator(
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }
        }
    }

    // ---- 弹层 1：账户操作面板（长按账户行升起；点击行则进账户详情页）----
    accountInMenu?.let { targetAccount ->
        AccountActionSheet(
            account = targetAccount,
            isPrivacyMode = uiState.isPrivacyMode,
            isDefaultExpense = targetAccount.name == uiState.defaultExpenseAccount,
            isDefaultIncome = targetAccount.name == uiState.defaultIncomeAccount,
            onDismiss = { accountInMenu = null },
            onTransfer = { account -> openTransferFor(account) },
            onReconcile = { account -> accountToReconcile = account },
            onEdit = { account -> accountToEdit = account },
            onDelete = { account -> accountToDelete = account },
            onToggleDefaultExpense = { isDefault ->
                viewModel.setDefaultExpenseAccount(if (isDefault) targetAccount.name else "")
            },
            onToggleDefaultIncome = { isDefault ->
                viewModel.setDefaultIncomeAccount(if (isDefault) targetAccount.name else "")
            }
        )
    }

    // ---- 弹层 2：计划快捷操作面板（计划小卡长按升起；点击小卡则进计划详情页）----
    planInMenu?.let { targetPlan ->
        val holder = accountsById[targetPlan.holderAccountId]
        PlanQuickActionSheet(
            plan = targetPlan,
            holderBalanceCents = holder?.balanceCents,
            depositMaxCents = availableToEarmarkFor(
                targetPlan.holderAccountId,
                holder?.balanceCents ?: 0L,
                uiState.plans
            ),
            isDone = targetPlan.targetAmountCents > 0L &&
                targetPlan.earmarkedCents >= targetPlan.targetAmountCents,
            isOverdrawn = holder != null && planIsOverdrawn(targetPlan, holder.balanceCents),
            isPrivacyMode = uiState.isPrivacyMode,
            onDismiss = { planInMenu = null },
            onDeposit = { planDepositTarget = targetPlan },
            onWithdraw = { planWithdrawTarget = targetPlan },
            onEdit = {
                planFormEdit = targetPlan
                planFormVisible = true
            },
            onDelete = { planToDelete = targetPlan },
            onOpenDetail = {
                planInMenu = null
                openPlanDetail(targetPlan)
            }
        )
    }

    // ---- 弹层：存一笔（长按面板进入；上限 = 账户可再圈余额）----
    planDepositTarget?.let { targetPlan ->
        PlanDepositSheet(
            plan = targetPlan,
            maxCents = availableToEarmarkFor(
                targetPlan.holderAccountId,
                accountsById[targetPlan.holderAccountId]?.balanceCents ?: 0L,
                uiState.plans
            ),
            isPrivacyMode = uiState.isPrivacyMode,
            onDismiss = { planDepositTarget = null },
            onConfirm = { cents ->
                viewModel.depositToPlan(targetPlan.id, cents)
                planDepositTarget = null
                toast.success("已存 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 弹层：取一笔（长按面板进入；上限 = 该计划已圈额）----
    planWithdrawTarget?.let { targetPlan ->
        PlanWithdrawSheet(
            plan = targetPlan,
            isPrivacyMode = uiState.isPrivacyMode,
            onDismiss = { planWithdrawTarget = null },
            onConfirm = { cents ->
                viewModel.withdrawFromPlan(targetPlan.id, cents)
                planWithdrawTarget = null
                toast.success("已取 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 弹窗：删除计划确认（长按面板进入）----
    planToDelete?.let { targetPlan ->
        ConfirmDeleteDialog(
            visible = true,
            title = "删除计划",
            message = "确定要删除攒钱计划「${targetPlan.name}」吗？删除后专款释放，攒钱/取出记录一并删除，不可撤销",
            onConfirm = {
                viewModel.deletePlan(targetPlan.id)
                planToDelete = null
                toast.success("计划已删除")
            },
            onDismiss = { planToDelete = null }
        )
    }

    // ---- 弹层 3：待核对账户底包（横幅点击升起，行尾「去对账」直达该账户对账框）----
    if (showPendingSheet && pendingAccounts.isNotEmpty()) {
        PendingReconcileSheet(
            pendingAccounts = pendingAccounts,
            onDismiss = { showPendingSheet = false },
            onReconcile = { account ->
                showPendingSheet = false
                accountToReconcile = account
            }
        )
    }

    // ---- 弹窗 4：「跳过本期核对」二次确认 ----
    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = { Text("跳过本期核对提醒？") },
            text = {
                Text(
                    "本期将不再提醒「${pendingAccounts.size} 个账户待核对」，下一期开始前不会再次打扰；" +
                        "你也可以随时进入账户页手动对账。"
                )
            },
            confirmButton = {
                TextButton(onClick = confirmSkipReconcile) {
                    Text("跳过本期")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ---- 弹层 5：月度资金对账对话框 ----
    accountToReconcile?.let { targetAccount ->
        AccountReconcileDialog(
            account = targetAccount,
            onDismiss = { accountToReconcile = null },
            onConfirmReconcile = { actualCents, applyCorrection ->
                viewModel.reconcileAccount(targetAccount.id, actualCents, applyCorrection)
                accountToReconcile = null
                toast.success(
                    if (applyCorrection) "已按实际余额校正期初基线" else "资金对账已记录"
                )
            }
        )
    }

    // ---- 弹层 6：快速转账 / 还款（支持方向预填）----
    if (showTransferSheet) {
        QuickTransferSheet(
            accounts = uiState.accounts,
            initialFromAccountId = transferFromId,
            initialToAccountId = transferToId,
            onDismiss = {
                showTransferSheet = false
                transferFromId = null
                transferToId = null
            },
            onConfirmTransfer = { fromId, toId, amountCents, remark ->
                viewModel.transfer(fromId, toId, amountCents, remark)
                showTransferSheet = false
                transferFromId = null
                transferToId = null
                toast.success("转账记录成功")
            }
        )
    }

    // ---- 弹层 7：新建 / 编辑账户 ----
    if (showAddSheet || accountToEdit != null) {
        AddEditAccountSheet(
            accountToEdit = accountToEdit,
            existingTypes = existingTypeLabels,
            onDismiss = {
                showAddSheet = false
                accountToEdit = null
            },
            onSave = { name, label, iconName, colorHex, balanceCents ->
                if (accountToEdit != null) {
                    val target = accountToEdit!!
                    val diff = balanceCents - target.balanceCents
                    val updated = target.copy(
                        name = name,
                        label = label,
                        iconName = iconName,
                        colorHex = colorHex,
                        openingBalanceCents = target.openingBalanceCents + diff,
                        balanceCents = balanceCents
                    )
                    viewModel.updateAccount(updated)
                    toast.success("账户「$name」已更新")
                } else {
                    viewModel.createAccount(name, label, iconName, colorHex, balanceCents)
                    toast.success("新账户「$name」创建成功")
                }
                showAddSheet = false
                accountToEdit = null
                // 从长按面板进入的编辑：保存成功后一并收起父面板（取消时面板保留）
                accountInMenu = null
            }
        )
    }

    // ---- 弹层 8：新建 / 编辑攒钱计划表单（头部新建 = 空；长按面板编辑 = 非空；支持预置心愿预填）----
    if (planFormVisible) {
        PlanFormSheet(
            planToEdit = planFormEdit,
            accounts = uiState.accounts,
            initialName = planPresetName,
            initialTargetAmountCents = planPresetTargetCents,
            initialColorHex = planPresetColorHex,
            onDismiss = {
                planFormVisible = false
                planFormEdit = null
                planPresetName = ""
                planPresetTargetCents = 0L
                planPresetColorHex = null
            },
            onSave = { name, targetCents, holderId, colorHex ->
                if (planFormEdit != null) {
                    viewModel.updatePlan(
                        planFormEdit!!.copy(
                            name = name,
                            targetAmountCents = targetCents,
                            holderAccountId = holderId,
                            colorHex = colorHex
                        )
                    )
                    toast.success("计划已更新")
                } else {
                    viewModel.createPlan(name, targetCents, holderId, colorHex)
                    toast.success("计划「$name」创建成功")
                }
                planFormVisible = false
                planFormEdit = null
                planPresetName = ""
                planPresetTargetCents = 0L
                planPresetColorHex = null
            }
        )
    }

    // ---- 弹窗 9：删除账户确认（含被计划占用拦截守卫）----
    ConfirmDeleteDialog(
        visible = accountToDelete != null,
        title = "删除账户",
        message = "确定要删除账户「${accountToDelete?.name}」吗？",
        onConfirm = {
            accountToDelete?.let { target ->
                // 守卫：被攒钱计划圈为专款账户时拒绝删除
                val occupying = uiState.plans.firstOrNull { it.holderAccountId == target.id }
                if (occupying != null) {
                    toast.info("「${target.name}」是攒钱计划「${occupying.name}」的专款账户，请先处理该计划")
                } else {
                    viewModel.deleteAccount(target.id)
                    toast.success("账户已删除")
                }
            }
            accountToDelete = null
        },
        onDismiss = { accountToDelete = null }
    )
}
