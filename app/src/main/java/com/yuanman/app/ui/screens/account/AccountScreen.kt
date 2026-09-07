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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * 沅满记账 · 账户 Tab（设计文档 account-tab-redesign-v0.2 + saving-plans-and-paycheck-v0.3）
 *
 * 编排层职责（设计文档 §9 文件映射 / v0.3 §2.1 / redesign v0.2 §6）：
 * 1. 顶部资产快照 Header（AccountSnapshotHero，自身处理状态栏内边距）：右上「更多」菜单
 *    承载 发薪分配 / 账户核对 两个二级入口（经 onOpenPaycheckRun / onOpenAccountReconcile 注入，
 *    未注入则菜单不出现）；隐私小眼睛内联于总资产金额后。
 * 2. 待核对提醒：仅当存在 OVERDUE / NEVER 账户时出现（§4.5）的 32dp 紧凑横条，
 *    点击升起待核对底包，行尾 (X) 可在本页面实例内关闭（rememberSaveable 本地状态，不持久化）
 * 3. 滚动区顶部 = 攒钱计划区块（区块头 + 横向小卡行 / 空计划宽幽灵卡；点击均经 onOpenSavingPlans
 *    跳二级页「攒钱计划」）；发薪分配入口已迁入右上角「更多」菜单（redesign v0.2 §6.4）
 * 4. 资金账户区块：区块头（「资金账户」+ 右侧「＋ 新建账户」钮，新建入口自列表底部上移）
 *    + 分组账户清单（组头：首账户色竖条 + 组合计 / 账户卡）
 * 5. 交互闭环：账户卡 → 操作面板 → 转账（按余额符号定方向）/ 对账 / 编辑 / 删除
 *    （删除被计划圈为专款账户时拦截，v0.3 §3 边界表）
 *
 * 本文件不含任何类型内容判断：label 只透传字符串，还款由余额符号驱动（§11 决策日志 8/9）。
 */
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onOpenSavingPlans: (() -> Unit)? = null,
    onOpenPaycheckRun: (() -> Unit)? = null,
    onOpenAccountReconcile: (() -> Unit)? = null
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
    var accountInMenu by remember { mutableStateOf<AccountUiModel?>(null) }
    var showPendingSheet by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountUiModel?>(null) }
    // 待核对横条关闭态：本页面实例 / 会话内不再显示（不持久化，见 redesign v0.2 §6.2）
    var reconcileReminderDismissed by rememberSaveable { mutableStateOf(false) }

    // ---- 派生数据（判定口径只出自共享 accountReconcileStatus）----
    val pendingAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { accountReconcileStatus(it.lastReconciledAt).isPending() }
    }
    // 表单「已用类型」快捷 chips 数据源：全账户既有类型字符串，按账户列表首次出现排序
    val existingTypeLabels = remember(uiState.accounts) {
        uiState.accounts.map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    // 专款账户余额查询表（计划卡超额判定）
    val accountsById = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }

    // ---- 下拉刷新：与旧版一致的轻量自绘指示器 ----
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

    // 打开攒钱计划二级页（导航层注入；未注入时不响应）
    val openSavingPlans: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onOpenSavingPlans?.invoke()
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

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 1. 净资产快照 Header（AppHeaderSurface 底纹，内部处理状态栏内边距；
        //       右上「更多」菜单入口经 onOpenPaycheckRun / onOpenAccountReconcile 注入）----
        AccountSnapshotHero(
            totalBalanceCents = uiState.totalBalanceCents,
            totalAssetCents = uiState.totalAssetCents,
            totalDebtCents = uiState.totalDebtCents,
            distributionSegments = uiState.distributionSegments,
            isPrivacyMode = uiState.isPrivacyMode,
            onTogglePrivacy = { viewModel.togglePrivacyMode() },
            onOpenPaycheckRun = onOpenPaycheckRun,
            onOpenAccountReconcile = onOpenAccountReconcile
        )

        // ---- 2. 待核对提醒横条（32dp 紧凑条；横向 16dp 边距由本层统一包裹；(X) 本地关闭）----
        if (pendingAccounts.isNotEmpty() && !reconcileReminderDismissed) {
            ReconcileReminderBanner(
                pendingCount = pendingAccounts.size,
                onClick = { showPendingSheet = true },
                onDismiss = { reconcileReminderDismissed = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
        }

        // ---- 3. 账户清单区（下拉刷新手势作用域）----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.accounts.isEmpty()) {
                // 空态：顶部保留计划区块（弱提示禁用 ghost，§3），下方可滚动保证下拉手势可达
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    PlansSectionHeader(
                        onManageClick = openSavingPlans
                    )
                    PlansWideGhostCard(
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                    )
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
                    // ---- v0.3 区块：攒钱计划（发薪分配入口已迁入 hero「更多」菜单，§6.4）----
                    item(key = "plans_header") {
                        PlansSectionHeader(
                            onManageClick = openSavingPlans
                        )
                    }
                    if (uiState.plans.isEmpty()) {
                        // 空计划态：一行宽幽灵卡 → 二级页内新建（§2.1）
                        item(key = "plans_wide_ghost") {
                            PlansWideGhostCard(
                                enabled = true,
                                onClick = openSavingPlans,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                            )
                        }
                    } else {
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
                                        onClick = openSavingPlans
                                    )
                                }
                                item(key = "plans_ghost_mini") {
                                    PlansGhostMiniCard(onClick = openSavingPlans)
                                }
                            }
                        }
                    }

                    // ---- 资金账户区块：区块头（标题 + 「＋ 新建账户」，入口自列表底部上移 §6.8）----
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
                        // 分组组头：组名（空 label 显示「未分组」）+ 组合计；竖条取组内首账户色
                        item(key = "group_header_${group.label}") {
                            AccountGroupHeader(
                                groupLabel = group.label,
                                groupTotalCents = group.groupTotalCents,
                                isPrivacyMode = uiState.isPrivacyMode,
                                accentColorHex = group.accounts.firstOrNull()?.colorHex
                            )
                        }

                        // 组内账户卡
                        items(
                            items = group.accounts,
                            key = { it.id }
                        ) { account ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                AccountItemCard(
                                    account = account,
                                    isPrivacyMode = uiState.isPrivacyMode,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    // ---- 弹层 1：账户操作面板（账户卡点击升起）----
    accountInMenu?.let { targetAccount ->
        AccountActionSheet(
            account = targetAccount,
            isPrivacyMode = uiState.isPrivacyMode,
            onDismiss = { accountInMenu = null },
            onTransfer = { account -> openTransferFor(account) },
            onReconcile = { account -> accountToReconcile = account },
            onEdit = { account -> accountToEdit = account },
            onDelete = { account -> accountToDelete = account }
        )
    }

    // ---- 弹层 2：待核对账户底包（横幅点击升起，行尾「去对账」直达该账户对账框）----
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

    // ---- 弹层 3：月度资金对账对话框 ----
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

    // ---- 弹层 4：快速转账 / 还款（支持方向预填）----
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

    // ---- 弹层 5：新建 / 编辑账户 ----
    if (showAddSheet || accountToEdit != null) {
        AddEditAccountSheet(
            accountToEdit = accountToEdit,
            existingTypes = existingTypeLabels,
            onDismiss = {
                showAddSheet = false
                accountToEdit = null
            },
            onSave = { name, label, iconName, colorHex, openingCents ->
                if (accountToEdit != null) {
                    val updated = accountToEdit!!.copy(
                        name = name,
                        label = label,
                        iconName = iconName,
                        colorHex = colorHex,
                        openingBalanceCents = openingCents
                    )
                    viewModel.updateAccount(updated)
                    toast.success("账户「$name」已更新")
                } else {
                    viewModel.createAccount(name, label, iconName, colorHex, openingCents)
                    toast.success("新账户「$name」创建成功")
                }
                showAddSheet = false
                accountToEdit = null
            }
        )
    }

    // ---- 弹窗 6：删除确认 ----
    ConfirmDeleteDialog(
        visible = accountToDelete != null,
        title = "删除账户",
        message = "确定要删除账户「${accountToDelete?.name}」吗？",
        onConfirm = {
            accountToDelete?.let { target ->
                // 守卫：被攒钱计划圈为专款账户时拒绝删除（设计文档 §3 边界表）
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
