@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 发薪分配 二级页（v0.0.4.5：预分配规则形态）。
 *
 * 产品模型：**规则提前配置好，工资到账即自动分配**——
 *  1. 自动分账开关卡：保存「工资」类收入后自动按规则执行（默认开，可关）；
 *  2. 分配规则卡：即改即存（SchemeEditorSheet），规则为空时自动分账仅做「剩余自动清欠」；
 *  3. 分账记录卡：历次自动/手动执行摘要（金额 / 笔数 / 来源 / 留存）；
 *  4. 手动补分：处理未自动分账的工资（支付方式没对上账户、或自动开关关闭期间记的账）。
 *
 * 执行统一走共享 PaycheckExecutor（与记账保存后的自动触发同一路径），
 * 预览/执行口径 E1-E7 见 SavingPlanModels。
 */
@Composable
fun PaycheckRunScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    val accounts = uiState.accounts
    val plans = uiState.plans
    val scheme = uiState.paycheckScheme
    val privacy = uiState.isPrivacyMode
    val appliedIds = uiState.appliedIncomeRecordIds

    // ---- 一次性执行结果 toast ----
    val notice by viewModel.paycheckNotice.collectAsState()
    LaunchedEffect(notice) {
        notice?.let { msg ->
            if (msg.startsWith("按当前规则")) toast.info(msg) else toast.success(msg)
            viewModel.clearPaycheckNotice()
        }
    }

    var showSchemeEdit by remember { mutableStateOf(false) }
    var showManualSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "发薪分配",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            if (accounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyStateView(
                        title = "请先创建资金账户",
                        description = "创建账户后，保存「工资」类收入即可按规则自动分账",
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
                        .padding(top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---- 1. 自动分账开关 ----
                    AutoSplitToggleCard(
                        enabled = uiState.paycheckAutoEnabled,
                        onToggle = { viewModel.setPaycheckAutoEnabled(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 2. 分配规则（即改即存）----
                    PaycheckRuleCard(
                        scheme = scheme,
                        accountsById = accounts.associateBy { it.id },
                        plansById = plans.associateBy { it.id },
                        onEdit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSchemeEdit = true
                        }
                    )

                    // ---- 3. 分账记录 ----
                    PaycheckHistoryCard(
                        history = uiState.paycheckRunHistory,
                        isPrivacyMode = privacy
                    )

                    // ---- 4. 手动补分（处理没自动分掉的工资）----
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualSheet = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "手动补分（处理未自动分账的工资）",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.primary
                        )
                    }
                    Text(
                        text = "一般无需手动操作：保存「工资」入账后已自动按规则分账。这里用于支付方式没对上账户等特殊情况。",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.outline
                    )
                }
            }
        }
    }

    // ---- 方案编辑（即改即存）----
    if (showSchemeEdit) {
        SchemeEditorSheet(
            accounts = accounts,
            plans = plans,
            scheme = scheme,
            onDismiss = { showSchemeEdit = false },
            onSchemeChange = { edited -> viewModel.savePaycheckScheme(edited) }
        )
    }

    // ---- 手动补分 sheet ----
    if (showManualSheet) {
        PaycheckManualSheet(
            candidates = uiState.incomeCandidates.filterNot { it.recordId in appliedIds },
            accounts = accounts,
            isPrivacyMode = privacy,
            onDismiss = { showManualSheet = false },
            onConfirm = { sourceId, amountCents, recordId ->
                viewModel.executePaycheck(sourceId, amountCents, recordId)
                showManualSheet = false
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 区块组件
// ---------------------------------------------------------------------------

/** 自动分账开关卡：工资到账自动按规则执行 */
@Composable
private fun AutoSplitToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Savings,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "工资到账自动分账",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = colors.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) {
                        "保存「工资」类收入后，自动按下方的分配规则分到各账户与计划"
                    } else {
                        "已关闭：保存工资不会自动分账，可在下方手动补分"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** 分配规则卡：规则列表摘要 + 调整入口；空规则给「去设置」 */
@Composable
private fun PaycheckRuleCard(
    scheme: PaycheckSchemeUiModel,
    accountsById: Map<Long, AccountUiModel>,
    plansById: Map<Long, SavingPlanUiModel>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasRules = scheme.rules.isNotEmpty()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
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
                    text = "分配规则",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Text(
                        text = if (hasRules) "调整规则" else "去设置",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primary
                    )
                }
            }

            when {
                hasRules -> {
                    scheme.rules.forEachIndexed { index, rule ->
                        if (index > 0) {
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))
                        }
                        PaycheckRuleLine(
                            rule = rule,
                            accountsById = accountsById,
                            plansById = plansById
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))
                    Text(
                        text = if (scheme.autoClearDebts) {
                            "执行后如有剩余，自动还清负余额账户"
                        } else {
                            "剩余自动清欠已关闭"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.outline,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                else -> {
                    Text(
                        text = "规则 = 工资到账后依次执行的动作，比如：先转 ¥5,000 到储蓄卡、再按 30% 攒进「旅行基金」。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.outline,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                    Text(
                        text = if (scheme.autoClearDebts) {
                            "未设置规则时：自动分账只做「还清负余额账户」，剩余留在工资账户"
                        } else {
                            "未设置规则，且剩余自动清欠已关闭：工资保存后不会产生任何分账"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.outline,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/** 单条规则描述行（转/攒 + 金额或百分比 → 目标） */
@Composable
private fun PaycheckRuleLine(
    rule: PaycheckRuleUiModel,
    accountsById: Map<Long, AccountUiModel>,
    plansById: Map<Long, SavingPlanUiModel>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isToAccount =
        rule.kind == PaycheckRuleKind.TO_ACCOUNT_FIXED || rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT
    val isPct = rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT || rule.kind == PaycheckRuleKind.TO_PLAN_PCT
    val amountText = if (isPct) {
        "${rule.percentBps / 100}%"
    } else {
        "¥" + MoneyUtils.centsToYuanString(rule.amountCents, withGrouping = true)
    }
    val targetName = if (isToAccount) {
        accountsById[rule.targetId]?.name ?: "已删除账户"
    } else {
        plansById[rule.targetId]?.name ?: "已删除计划"
    }
    val verb = if (isToAccount) "转" else "攒"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isToAccount) Icons.Default.SwapHoriz else Icons.Default.Savings,
            contentDescription = null,
            tint = if (isToAccount) colors.secondary else colors.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$verb $amountText → $targetName",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 分账记录卡：历次自动/手动执行摘要 */
@Composable
private fun PaycheckHistoryCard(
    history: List<PaycheckLastRunUiModel>,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
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
                    text = "分账记录",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (history.isEmpty()) "还没有执行过" else "共 ${history.size} 次",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.outline,
                    maxLines = 1
                )
            }

            if (history.isEmpty()) {
                Text(
                    text = "保存「工资」类收入自动分账后，结果会记录在这里（自动/手动都算）",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                history.forEachIndexed { index, run ->
                    if (index > 0) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))
                    }
                    PaycheckHistoryRow(run = run, isPrivacyMode = isPrivacyMode)
                }
            }
        }
    }
}

/** 单条执行记录：时间 · 金额 · N 笔动作 + 自动/手动徽标 + 来源/留存副行 */
@Composable
private fun PaycheckHistoryRow(
    run: PaycheckLastRunUiModel,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val amountText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        "¥" + MoneyUtils.centsToYuanString(run.amountCents ?: 0L, withGrouping = true)
    }
    val remainText = run.remainingCents?.let { remaining ->
        if (remaining > 0L) {
            " · 留卡 " + (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(remaining, withGrouping = true))
        } else {
            ""
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$amountText · ${run.actionCount ?: 0} 笔动作",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateTimeUtils.formatDateTime(run.at ?: 0L) + remainText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (run.auto) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "自动",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "手动",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 手动补分 sheet
// ---------------------------------------------------------------------------

/** 隐私掩码文案 */
private const val PRIVACY_MASK = "¥ ••••"

private fun displayMoney(cents: Long, isPrivacyMode: Boolean): String =
    if (isPrivacyMode) PRIVACY_MASK else MoneyUtils.formatCurrency(cents)

private fun parseManualYuanToCents(yuanInput: String): Long {
    if (yuanInput.isBlank()) return 0L
    return try {
        val bd = BigDecimal(yuanInput.trim())
        bd.multiply(BigDecimal(100)).toLong()
    } catch (e: Exception) {
        0L
    }
}

/**
 * 手动补分 sheet：列出本月尚未自动分账的「工资」入账（未匹配的需选来源账户），
 * 或手填一笔没记账的工资金额；确认后走共享 PaycheckExecutor。
 */
@Composable
private fun PaycheckManualSheet(
    candidates: List<IncomeCandidateUiModel>,
    accounts: List<AccountUiModel>,
    isPrivacyMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (sourceAccountId: Long, amountCents: Long, recordId: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    var selectedRecordId by remember { mutableStateOf<Long?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var pickedSourceId by remember { mutableStateOf<Long?>(null) }

    val selectedCandidate = candidates.firstOrNull { it.recordId == selectedRecordId }
    val manualAmountCents = parseManualYuanToCents(manualInput)
    val amountCents = selectedCandidate?.amountCents ?: manualAmountCents

    val matchedSourceId = selectedCandidate?.matchedAccountId
    val sourceAccount = accounts.firstOrNull { it.id == (matchedSourceId ?: pickedSourceId) }
    val showSourcePick = amountCents > 0L && (matchedSourceId == null || sourceAccount == null)
    val positiveAccounts = remember(accounts) { accounts.filter { it.balanceCents > 0L } }
    val canConfirm = amountCents > 0L && sourceAccount != null

    val runManual: () -> Unit = {
        if (canConfirm) {
            onConfirm(sourceAccount!!.id, amountCents, selectedRecordId)
        }
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "手动补分",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )
            Text(
                text = "按当前分配规则执行一次；用于没被自动分账的工资（未匹配账户 / 自动开关关闭期间）。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.outline
            )

            // ---- 待处理工资（本月未自动分账）----
            if (candidates.isEmpty()) {
                Text(
                    text = "本月没有待处理的工资入账（都已自动分账，或本月还没有工资记录）",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.outline,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Text(
                    text = "选一笔本月工资",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = colors.onSurfaceVariant
                )
                candidates.forEachIndexed { index, candidate ->
                    if (index > 0) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    }
                    ManualCandidateRow(
                        candidate = candidate,
                        isSelected = candidate.recordId == selectedRecordId,
                        isPrivacyMode = isPrivacyMode,
                        onClick = {
                            selectedRecordId = candidate.recordId
                            manualInput = ""
                            pickedSourceId = candidate.matchedAccountId
                        }
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))

            // ---- 或手填一笔（没记账的工资）----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "或手填到手金额",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (selectedRecordId != null) {
                    TextButton(onClick = { selectedRecordId = null }) {
                        Text(
                            text = "改用金额",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primary
                        )
                    }
                }
            }
            OutlinedTextField(
                value = manualInput,
                onValueChange = {
                    manualInput = it
                    if (it.isNotBlank()) selectedRecordId = null
                },
                label = { Text("金额 (元)") },
                placeholder = { Text("例如 12000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- 来源账户（候选未匹配 / 手填时需要）----
            if (showSourcePick) {
                Text(
                    text = "这笔钱在哪个账户",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = colors.onSurfaceVariant
                )
                if (positiveAccounts.isEmpty()) {
                    Text(
                        text = "没有余额为正的账户，暂时无法分账",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.error,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        positiveAccounts.forEach { acc ->
                            FilterChip(
                                selected = acc.id == pickedSourceId,
                                onClick = { pickedSourceId = acc.id },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        text = acc.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ---- 确认行 ----
            val readyHint = when {
                amountCents <= 0L -> "先选一笔工资或填好金额"
                sourceAccount == null -> "选一下这笔钱在哪个账户"
                else -> "将用「${sourceAccount.name}」按规则分账 ${displayMoney(amountCents, isPrivacyMode)}"
            }
            Button(
                onClick = runManual,
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "按规则分账 " + displayMoney(amountCents.coerceAtLeast(0L), isPrivacyMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                text = readyHint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (canConfirm) colors.outline else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/** 手动补分候选行：工资日期 · 金额 · 匹配状态；未匹配提示需选来源 */
@Composable
private fun ManualCandidateRow(
    candidate: IncomeCandidateUiModel,
    isSelected: Boolean,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val matched = candidate.matchedAccountId != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayMoney(candidate.amountCents, isPrivacyMode),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateTimeUtils.formatMonthDay(candidate.at) + " · " +
                    (if (matched) "${candidate.matchedAccountName} · 工资入账" else "未匹配到账户，需选来源"),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (matched) colors.outline else colors.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
