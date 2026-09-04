package com.yuanman.app.ui.screens.accounts

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountPeriodComparison
import com.yuanman.app.data.model.AccountPeriodType
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.SwipeRevealDeleteItem
import com.yuanman.app.ui.components.YuanmanHeaderBackground
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAccountStats: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showPeriodSettings by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var openSwipeAccountId by remember { mutableStateOf<Long?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<AccountType?>(null) }
    var isReconcileBannerDismissed by rememberSaveable(uiState.comparison.currentPeriod.periodKey) {
        mutableStateOf(false)
    }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotBlank()) showSearch = true
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            if (msg.startsWith("转账失败") || msg.startsWith("发薪分配未执行")) {
                toast.error(msg)
            } else {
                toast.success(msg)
            }
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🌟 1. 顶部极简资产看板
            YuanmanAccountsHeader(
                comparison = uiState.comparison,
                periodType = uiState.periodType,
                privacyMode = uiState.privacyMode,
                showPeriodMenu = showPeriodMenu,
                onTogglePeriodMenu = { showPeriodMenu = it },
                onSelectPeriodType = {
                    viewModel.setPeriodType(it)
                    showPeriodMenu = false
                },
                onOpenPeriodSettings = {
                    showPeriodMenu = false
                    showPeriodSettings = true
                },
                onTogglePrivacy = { viewModel.togglePrivacyMode() },
                searchOpen = showSearch,
                onToggleSearch = {
                    if (showSearch) viewModel.setSearchQuery("")
                    showSearch = !showSearch
                },
                onAddAccount = { viewModel.openAddAccount() },
                onReconcile = { viewModel.openReconciliation() },
                onAllocate = { viewModel.openAllocation() },
                onNavigateToAccountStats = onNavigateToAccountStats
            )

            // 🌟 1.1 轻量未对账提示条（支持关闭，文案简化）
            if (!uiState.comparison.hasReconciledInCurrentPeriod && !isReconcileBannerDismissed) {
                val periodName = uiState.comparison.currentPeriod.periodName
                val monthMatch = Regex("(\\d+月)").find(periodName)
                val monthText = monthMatch?.value ?: periodName

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.openReconciliation() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "尚未完成${monthText}的对账",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "去对账 >",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                        }

                        IconButton(
                            onClick = { isReconcileBannerDismissed = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 🌟 2. 极简资产列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "account_search") {
                    AnimatedVisibility(
                        visible = showSearch,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("搜索账户名称或备注", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清除搜索",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                val accountsByType = uiState.accounts.groupBy { AccountType.fromString(it.type) }

                // 类别快速筛选胶囊（紧凑极简布局）
                if (uiState.accounts.isNotEmpty() && uiState.searchQuery.isBlank()) {
                    item(key = "category_filter_capsules") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            item {
                                val isSelected = selectedCategoryFilter == null
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedCategoryFilter = null }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "全部",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.5.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${uiState.accounts.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                            )
                                        )
                                    }
                                }
                            }
                            AccountType.values().forEach { type ->
                                val count = accountsByType[type]?.size ?: 0
                                if (count > 0 || selectedCategoryFilter == type) {
                                    item {
                                        val isSelected = selectedCategoryFilter == type
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    selectedCategoryFilter = if (isSelected) null else type
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = type.groupTitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.5.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "$count",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.5.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 按类别展示各账户卡片
                val typesToDisplay = if (selectedCategoryFilter != null) {
                    listOf(selectedCategoryFilter!!)
                } else {
                    AccountType.values().toList()
                }

                typesToDisplay.forEach { type ->
                    val groupAccounts = accountsByType[type].orEmpty()
                    if (groupAccounts.isNotEmpty()) {
                        // 分组合计沿用顶部总资产/总负债的“计入净资产”口径：不计入净资产的账户
                        // 照常列出，但余额不参与分组合计（避免与顶部净值汇总数字对不上）
                        val netWorthAccounts = groupAccounts.filter { it.includeInNetWorth }
                        val groupTotalCents = netWorthAccounts.sumOf {
                            if (type.isLiability && it.balanceCents < 0) -it.balanceCents else it.balanceCents
                        }
                        // 负债组存在溢缴（负余额）且没有任何待还（正余额）余额时，
                        // 合计全部来自溢缴 → 分组头应按“净溢缴”展示（非红色“待还”语义）
                        val netOverpaidOnly = type.isLiability &&
                            netWorthAccounts.any { it.balanceCents < 0L } &&
                            netWorthAccounts.none { it.balanceCents > 0L }

                        item(key = "header_${type.name}") {
                            CompactAccountGroupHeader(
                                type = type,
                                totalCents = groupTotalCents,
                                netOverpaidOnly = netOverpaidOnly,
                                count = groupAccounts.size,
                                privacyMode = uiState.privacyMode
                            )
                        }

                        items(groupAccounts, key = { "account_${it.id}" }) { account ->
                            SwipeRevealDeleteItem(
                                itemKey = account.id,
                                openKey = openSwipeAccountId,
                                onOpen = { openSwipeAccountId = it },
                                onDelete = { accountToDelete = account }
                            ) {
                                CompactAccountCard(
                                    account = account,
                                    privacyMode = uiState.privacyMode,
                                    onTransfer = { viewModel.openTransfer(fromId = account.id) },
                                    onClick = { viewModel.openAccountDetail(account) }
                                )
                            }
                        }
                    }
                }

                // 分类筛选选中了某分组，但该组被删空/本就没有账户时给轻量空态，避免列表区整块空白
                val selectedGroup = selectedCategoryFilter
                if (selectedGroup != null && uiState.accounts.isNotEmpty() &&
                    (accountsByType[selectedGroup] ?: emptyList()).isEmpty()
                ) {
                    item(key = "empty_group_${selectedGroup.name}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "「${selectedGroup.groupTitle}」分组下暂无账户，可切换其他分类查看",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.outline
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                // 已归档账户入口卡片
                if (uiState.archivedAccounts.isNotEmpty() && uiState.searchQuery.isBlank()) {
                    item(key = "archived_accounts_entry") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.openArchivedList() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Archive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "已归档账户",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "${uiState.archivedAccounts.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (uiState.accounts.isEmpty()) {
                    item(key = "empty_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyStateView(
                                title = if (uiState.searchQuery.isBlank()) "暂无账户" else "未找到匹配账户",
                                description = if (uiState.searchQuery.isBlank()) {
                                    "您可以一键初始化推荐账户体系，也可以手动新增自定义账户"
                                } else {
                                    "请尝试其他账户名称或备注关键词"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (uiState.searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.initializeDefaultAccounts() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("一键初始化推荐账户", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.openAddAccount() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("手动新增", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🌟 3. 彻底升级的沉浸式手势 BottomSheets

        // 账户账单详情底页
        if (uiState.selectedAccountForDetail != null) {
            AccountDetailSheet(
                account = uiState.selectedAccountForDetail!!,
                records = uiState.selectedAccountRecords,
                privacyMode = uiState.privacyMode,
                onDismiss = { viewModel.closeAccountDetail() },
                onTransfer = { acc ->
                    viewModel.openTransfer(fromId = acc.id)
                },
                onEdit = { acc ->
                    // 多层级底页交互：保留当前详情底页，并在上层滑出编辑底页；关闭编辑底页后自动返回详情底页
                    viewModel.openEditAccount(acc)
                },
                onArchiveToggle = { acc ->
                    viewModel.setAccountArchived(acc.id, !acc.isArchived)
                }
            )
        }

        // 新增 / 编辑账户底页
        if (uiState.isAddEditOpen) {
            AddEditAccountSheet(
                account = uiState.accountToEdit,
                isLoading = uiState.isLoading,
                onDismiss = { viewModel.closeAddEdit() },
                onSave = { name, type, balance, includeInNetWorth, icon, colorHex, remark, balanceAdjustmentRemark ->
                    viewModel.saveAccount(
                        name,
                        type,
                        balance,
                        includeInNetWorth,
                        icon,
                        colorHex,
                        remark,
                        balanceAdjustmentRemark
                    )
                }
            )
        }

        // 资金划转底页
        if (uiState.isTransferOpen) {
            TransferSheet(
                // Search only filters the list; transfer must still be able to choose any active account.
                accounts = uiState.allAccounts,
                initialFromAccountId = uiState.transferInitialFromId,
                initialToAccountId = uiState.transferInitialToId,
                isLoading = uiState.isLoading,
                privacyMode = uiState.privacyMode,
                onDismiss = { viewModel.closeTransfer() },
                onConfirmTransfer = { fromId, toId, amount, remark ->
                    viewModel.executeTransfer(fromId, toId, amount, remark)
                }
            )
        }

        // 周期对账工作台底页
        if (uiState.isReconciliationOpen) {
            ReconciliationSheet(
                periodInfo = uiState.comparison.currentPeriod,
                // Reconciliation and snapshot totals must never be limited by the list search query.
                accounts = uiState.allAccounts,
                isLoading = uiState.isLoading,
                privacyMode = uiState.privacyMode,
                onDismiss = { viewModel.closeReconciliation() },
                onConfirm = { items, autoAdj ->
                    viewModel.executeReconciliation(items, autoAdj)
                }
            )
        }

        // 收入分配底页
        if (uiState.isAllocationOpen) {
            IncomeAllocationSheet(
                // Allocation rules may target accounts that are not in the current search result.
                accounts = uiState.allAccounts,
                savedRules = uiState.incomeRules,
                isLoading = uiState.isLoading,
                privacyMode = uiState.privacyMode,
                onDismiss = { viewModel.closeAllocation() },
                onSaveRules = { rules ->
                    viewModel.saveIncomeRules(rules)
                },
                onExecuteAllocation = { sourceId, amount, results ->
                    viewModel.executeAllocation(sourceId, amount, results)
                }
            )
        }





        // 已归档账户管理底页
        if (uiState.isArchivedListOpen) {
            ArchivedAccountsSheet(
                archivedAccounts = uiState.archivedAccounts,
                privacyMode = uiState.privacyMode,
                onDismiss = { viewModel.closeArchivedList() },
                onUnarchive = { acc ->
                    viewModel.setAccountArchived(acc.id, false)
                },
                onSelectAccount = { acc ->
                    viewModel.closeArchivedList()
                    viewModel.openAccountDetail(acc)
                }
            )
        }

        // 财务周期偏好设置（周期跨度 + 每月起始日）底页
        if (showPeriodSettings) {
            PeriodSettingsSheet(
                currentPeriodType = uiState.periodType,
                currentStartDay = uiState.startDay,
                onDismiss = { showPeriodSettings = false },
                onSelectPeriodType = { type ->
                    // 沿用下拉菜单的切换逻辑：写偏好后由 VM 内部流驱动对比数据刷新
                    viewModel.setPeriodType(type)
                },
                onSelectStartDay = { day ->
                    viewModel.setStartDay(day)
                }
            )
        }

        ConfirmDeleteDialog(
            visible = accountToDelete != null,
            title = "删除账户",
            message = "确定要删除账户「${accountToDelete?.name.orEmpty()}」吗？关联的历史账单会保留，但该账户将不再出现在账户列表中。",
            onConfirm = {
                accountToDelete?.let { viewModel.deleteAccount(it.id) }
                accountToDelete = null
            },
            onDismiss = { accountToDelete = null }
        )
    }
}

/**
 * 顶部极简资产看板（极简风设计）
 */
@Composable
private fun YuanmanAccountsHeader(
    comparison: AccountPeriodComparison,
    periodType: AccountPeriodType,
    privacyMode: Boolean,
    showPeriodMenu: Boolean,
    onTogglePeriodMenu: (Boolean) -> Unit,
    onSelectPeriodType: (AccountPeriodType) -> Unit,
    onOpenPeriodSettings: () -> Unit,
    onTogglePrivacy: () -> Unit,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onAddAccount: () -> Unit,
    onReconcile: () -> Unit,
    onAllocate: () -> Unit,
    onNavigateToAccountStats: () -> Unit
) {
    val netWorth = comparison.currentNetWorthCents
    val diff = comparison.netWorthDiffCents
    val hasDiff = comparison.historySnapshots.any { snapshot ->
        snapshot.periodKey == comparison.currentPeriod.prevPeriodKey
    }

    YuanmanHeaderBackground {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // 顶行：标题 + 周期选择器 + 搜索 + 新增
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账户",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 周期切换下拉胶囊
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.clickable { onTogglePeriodMenu(true) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = periodType.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "切换周期",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = { onTogglePeriodMenu(false) }
                        ) {
                            AccountPeriodType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = type.title,
                                            fontWeight = if (type == periodType) FontWeight.Bold else FontWeight.Normal,
                                            color = if (type == periodType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = {
                                        if (type == periodType) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = { onSelectPeriodType(type) }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // 更多周期偏好（类型 + 每月起始日）：打开 PeriodSettingsSheet
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "周期设置…",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = onOpenPeriodSettings
                            )
                        }
                    }

                    // 搜索图标
                    IconButton(onClick = onToggleSearch, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchOpen) "关闭搜索" else "搜索账户",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // 新增账户
                    IconButton(onClick = onAddAccount, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加账户",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 净资产区域可点击卡片（点击跳转至账户统计页面）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigateToAccountStats() }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 净资产标签与隐私眼睛 + 右侧分析小入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "总净资产",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                            IconButton(
                                onClick = onTogglePrivacy,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (privacyMode) "显示金额" else "隐藏金额",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "统计与分析",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "查看资产统计",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 核心净资产数值
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(netWorth)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (privacyMode) 28.sp else 30.sp,
                                letterSpacing = if (privacyMode) 2.sp else (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        // 较上周期对比
                        if (hasDiff) {
                            val isPositive = diff >= 0
                            val sign = if (isPositive) "+" else "-"
                            val absDiff = if (diff < 0L) -diff else diff
                            val percentStr = comparison.netWorthDiffPercent?.let { "（${if (it >= 0f) "+" else ""}${String.format("%.1f", it)}%）" } ?: ""
                            Text(
                                text = if (privacyMode) "较上期 --" else "较上期 $sign¥${MoneyUtils.centsToYuanString(absDiff)}$percentStr",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (privacyMode) MaterialTheme.colorScheme.outline else if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 资产与负债副行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (privacyMode) "总资产 ****" else "总资产 ¥${MoneyUtils.centsToYuanString(comparison.totalAssetCents)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = if (privacyMode) "总负债 ****" else "总负债 ¥${MoneyUtils.centsToYuanString(comparison.creditDebtCents)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (!privacyMode && comparison.creditDebtCents > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // 极细资产配置微条（仅3dp）
                    if (comparison.totalAssetCents > 0L) {
                        val totalAsset = comparison.totalAssetCents.toFloat()
                        val liquidRatio = (comparison.liquidAssetCents / totalAsset).coerceIn(0f, 1f)
                        val investRatio = (comparison.investmentAssetCents / totalAsset).coerceIn(0f, 1f)
                        val otherRatio = (comparison.otherAssetCents / totalAsset).coerceIn(0f, 1f)

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            if (liquidRatio > 0f) {
                                Box(modifier = Modifier.weight(liquidRatio).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                            }
                            if (investRatio > 0f) {
                                Box(modifier = Modifier.weight(investRatio).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                            }
                            if (otherRatio > 0f) {
                                Box(modifier = Modifier.weight(otherRatio).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 极简快捷操作栏（周期对账、发薪分配）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinimalistActionBtn(
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    label = "周期对账",
                    badge = !comparison.hasReconciledInCurrentPeriod,
                    onClick = onReconcile,
                    modifier = Modifier.weight(1f)
                )
                MinimalistActionBtn(
                    icon = Icons.Outlined.PieChart,
                    label = "发薪分配",
                    onClick = onAllocate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 极简胶囊快捷操作按钮
 */
@Composable
private fun MinimalistActionBtn(
    icon: ImageVector,
    label: String,
    badge: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                if (badge) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * 分组标题栏
 * @param netOverpaidOnly 负债组无任何待还余额、合计全部来自溢缴时置为 true（展示为“净溢缴”，不再红色“待还”）
 */
@Composable
private fun CompactAccountGroupHeader(
    type: AccountType,
    totalCents: Long,
    netOverpaidOnly: Boolean = false,
    count: Int,
    privacyMode: Boolean
) {
    // 金额文案与配色：隐私 → 负债待还（红）→ 负债全溢缴（净溢缴，主题蓝）→ 普通分组
    val (totalText, totalColor) = when {
        privacyMode -> Pair("****", MaterialTheme.colorScheme.outline)
        netOverpaidOnly -> Pair("净溢缴 ¥${MoneyUtils.centsToYuanString(totalCents)}", MaterialTheme.colorScheme.primary)
        type.isLiability && totalCents > 0L -> Pair("待还 ¥${MoneyUtils.centsToYuanString(totalCents)}", MaterialTheme.colorScheme.error)
        else -> Pair("¥${MoneyUtils.centsToYuanString(totalCents)}", MaterialTheme.colorScheme.outline)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = type.groupTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "（$count）",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            )
        }

        Text(
            text = totalText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = totalColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 紧凑实用账户卡片（点击展开详情，右侧带快捷划转）
 */
@Composable
private fun CompactAccountCard(
    account: AccountEntity,
    privacyMode: Boolean,
    onTransfer: () -> Unit,
    onClick: () -> Unit
) {
    val accType = AccountType.fromString(account.type)
    val isLiquid = accType == AccountType.CHECKING
    val isLiability = accType.isLiability

    val accColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
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
            // 左侧：图标 + 账户名 + 备注
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AccountIconHelper.getIcon(account.icon),
                        contentDescription = account.name,
                        tint = accColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        if (!account.includeInNetWorth) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "不计净资产",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (account.remark.isNotBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = account.remark,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 右侧：金额与快捷操作
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 流动账户的极简快捷划转小药丸
                if (isLiquid) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.clickable { onTransfer() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "划转",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // 金额数值
                val (displayText, displayColor) = if (isLiability) {
                    when {
                        account.balanceCents > 0L -> Pair(
                            "待还 ¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                            MaterialTheme.colorScheme.error
                        )
                        account.balanceCents == 0L -> Pair(
                            "无欠款",
                            MaterialTheme.colorScheme.outline
                        )
                        else -> Pair(
                            "溢缴 ¥${MoneyUtils.centsToYuanString(-account.balanceCents)}",
                            MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    when {
                        account.balanceCents >= 0L -> Pair(
                            "¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                            MaterialTheme.colorScheme.onSurface
                        )
                        else -> Pair(
                            "-¥${MoneyUtils.centsToYuanString(-account.balanceCents)}",
                            MaterialTheme.colorScheme.error
                        )
                    }
                }

                Text(
                    text = if (privacyMode) "****" else displayText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = displayColor
                    )
                )
            }
        }
    }
}

/**
 * 已归档账户查看与恢复底页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedAccountsSheet(
    archivedAccounts: List<AccountEntity>,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
    onUnarchive: (AccountEntity) -> Unit,
    onSelectAccount: (AccountEntity) -> Unit
) {
    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "已归档账户（${archivedAccounts.size}）",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "归档账户不参与日常记账与汇总，可随时恢复",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp
                        )
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            if (archivedAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无已归档账户",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(archivedAccounts, key = { it.id }) { account ->
                        val accColor = try {
                            Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccount(account) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = if (privacyMode) "余额：****" else "余额：¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { onUnarchive(account) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Unarchive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("恢复", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
