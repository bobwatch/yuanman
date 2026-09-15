@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 发薪分配 全新重构界面（v0.0.5）
 *
 * 现代化薪资智能分流控制台：
 *  1. 全景动线看板 (Hero Pipeline Card)：直观展示「工资到账 ➔ 规则分流 ➔ 智能清欠 ➔ 留存开销」全自动化链路；
 *  2. 动态发薪模拟测算器 (Paycheck Simulator)：可交互模拟任意薪资金额，动态显示多色分流比例条与留存试算；
 *  3. 立体步进式规则卡片群 (Step Pipeline Rule Cards)：按序展示分流步骤、目标账户/计划徽章与金额比例，支持快捷调整；
 *  4. 时间轴分账履历 (Timeline Execution Logs)：以时间轴节点清晰记录历次分账详情与卡内留存；
 *  5. 快速手动补分入口 (Manual Fallback)：为未自动分账的收入提供一键手动触发。
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
                    Column {
                        Text(
                            text = "发薪分配方案",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "薪资到账智能分流与自动储蓄",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.outline
                        )
                    }
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
                        .padding(top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ---- 1. 全景动线看板 (Hero Pipeline Card) ----
                    PaycheckHeroBanner(
                        enabled = uiState.paycheckAutoEnabled,
                        ruleCount = scheme.rules.size,
                        autoClearDebts = scheme.autoClearDebts,
                        onToggle = { viewModel.setPaycheckAutoEnabled(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 2. 交互式发薪测算模拟器 (Paycheck Simulator) ----
                    PaycheckSimulatorCard(
                        accounts = accounts,
                        plans = plans,
                        scheme = scheme,
                        isPrivacyMode = privacy,
                        onConfigScheme = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSchemeEdit = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 3. 立体步进式分流规则链路 (Rules Pipeline) ----
                    PaycheckRulesPipelineSection(
                        scheme = scheme,
                        accountsById = accounts.associateBy { it.id },
                        plansById = plans.associateBy { it.id },
                        onEdit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSchemeEdit = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 4. 时间轴分账执行履历 (Timeline History) ----
                    PaycheckTimelineHistorySection(
                        history = uiState.paycheckRunHistory,
                        isPrivacyMode = privacy,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 5. 手动补分通道入口 ----
                    PaycheckManualEntryCard(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
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
// 1. 全景动线看板组件 (Hero Pipeline Card)
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckHeroBanner(
    enabled: Boolean,
    ruleCount: Int,
    autoClearDebts: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            colors.primaryContainer.copy(alpha = 0.5f),
            colors.surfaceVariant.copy(alpha = 0.35f),
            colors.surface
        )
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "薪资智能分流引擎",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Status Dot
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (enabled) Color(0xFF10B981).copy(alpha = 0.15f) else colors.outlineVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (enabled) Color(0xFF10B981) else colors.outline)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (enabled) "运行中" else "已暂停",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enabled) Color(0xFF059669) else colors.outline
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (enabled) "记账保存「工资」后将自动按规则划转" else "已关闭自动分账，可在此手动试算与补分",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primaryContainer
                        )
                    )
                }

                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))

                // Pipeline Flow Diagram
                PaycheckPipelineDiagram(
                    ruleCount = ruleCount,
                    autoClearDebts = autoClearDebts
                )
            }
        }
    }
}

/** 动线流程节点图示 */
@Composable
private fun PaycheckPipelineDiagram(
    ruleCount: Int,
    autoClearDebts: Boolean
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Node 1: 工资入账
        PipelineNodeItem(
            icon = Icons.Outlined.CreditCard,
            title = "工资入账",
            subtitle = "自动触发",
            color = colors.primary
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.outlineVariant,
            modifier = Modifier.size(14.dp)
        )

        // Node 2: 规则分流
        PipelineNodeItem(
            icon = Icons.Default.Tune,
            title = "规则分流",
            subtitle = if (ruleCount > 0) "${ruleCount}步执行" else "未配置",
            color = if (ruleCount > 0) Color(0xFF0284C7) else colors.outline
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.outlineVariant,
            modifier = Modifier.size(14.dp)
        )

        // Node 3: 智能清欠
        PipelineNodeItem(
            icon = Icons.Default.Shield,
            title = "智能清欠",
            subtitle = if (autoClearDebts) "结余清零" else "未开启",
            color = if (autoClearDebts) Color(0xFF8B5CF6) else colors.outline
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.outlineVariant,
            modifier = Modifier.size(14.dp)
        )

        // Node 4: 卡内留存
        PipelineNodeItem(
            icon = Icons.Default.Savings,
            title = "卡内留存",
            subtitle = "日常开销",
            color = Color(0xFF10B981)
        )
    }
}

@Composable
private fun PipelineNodeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 9.5.sp,
            color = colors.outline
        )
    }
}

// ---------------------------------------------------------------------------
// 2. 交互式发薪测算模拟器 (Paycheck Simulator)
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckSimulatorCard(
    accounts: List<AccountUiModel>,
    plans: List<SavingPlanUiModel>,
    scheme: PaycheckSchemeUiModel,
    isPrivacyMode: Boolean,
    onConfigScheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var simSalaryYuan by remember { mutableLongStateOf(12000L) }
    val presetAmounts = listOf(8000L, 12000L, 16000L, 25000L)

    val simSalaryCents = simSalaryYuan * 100L

    // 测算逻辑：创建虚拟充足源账户，使规则得以完整模拟
    val simSource = remember(accounts, simSalaryCents) {
        val firstAcc = accounts.firstOrNull { it.balanceCents > 0 } ?: accounts.first()
        firstAcc.copy(balanceCents = simSalaryCents * 3)
    }

    val simAccounts = remember(accounts, simSource) {
        accounts.map { if (it.id == simSource.id) simSource else it }
    }

    val simResult = remember(simSalaryCents, simSource, simAccounts, plans, scheme) {
        planPaycheckActions(
            amountCents = simSalaryCents,
            sourceAccount = simSource,
            allAccounts = simAccounts,
            allPlans = plans,
            scheme = scheme
        )
    }

    val allocatedCents = simResult.totalAllocatedCents
    val remainingCents = simResult.remainingCents
    val allocatedPct = if (simSalaryCents > 0) (allocatedCents * 100f / simSalaryCents).coerceIn(0f, 100f) else 0f
    val remainingPct = (100f - allocatedPct).coerceIn(0f, 100f)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "发薪动态测算器",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = colors.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "实时试算",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Quick Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "假设薪资:",
                    fontSize = 11.5.sp,
                    color = colors.outline,
                    modifier = Modifier.padding(end = 2.dp)
                )
                presetAmounts.forEach { amt ->
                    val isSelected = simSalaryYuan == amt
                    FilterChip(
                        selected = isSelected,
                        onClick = { simSalaryYuan = amt },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary,
                            containerColor = colors.surfaceVariant.copy(alpha = 0.45f),
                            labelColor = colors.onSurfaceVariant
                        ),
                        border = null,
                        label = {
                            Text(
                                text = "¥" + MoneyUtils.centsToCompactYuan(amt * 100L),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // Multi-segment Allocation Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (allocatedPct > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(allocatedPct)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF0284C7), colors.primary)
                                        )
                                    )
                            )
                        }
                        if (remainingPct > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(remainingPct)
                                    .fillMaxHeight()
                                    .background(Color(0xFF10B981).copy(alpha = 0.75f))
                            )
                        }
                    }
                }

                // Bar Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "自动划转/储蓄 ${allocatedPct.toInt()}%",
                        fontSize = 10.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "工资卡留存日常 ${remainingPct.toInt()}%",
                        fontSize = 10.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2 Large Metric Cards Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Allocated
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "计划分流总额",
                            fontSize = 10.5.sp,
                            color = colors.outline,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMode) PRIVACY_MASK else "¥" + MoneyUtils.centsToYuanString(allocatedCents, withGrouping = true),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${simResult.steps.count { it.kind != PaycheckStepKind.REMAIN && it.amountCents > 0 }} 项规则生效",
                            fontSize = 9.5.sp,
                            color = colors.outline
                        )
                    }
                }

                // Metric 2: Remaining
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "卡内留存可用",
                            fontSize = 10.5.sp,
                            color = colors.outline,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMode) PRIVACY_MASK else "¥" + MoneyUtils.centsToYuanString(remainingCents, withGrouping = true),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "作为本月安全开销",
                            fontSize = 9.5.sp,
                            color = colors.outline
                        )
                    }
                }
            }

            if (scheme.rules.isEmpty()) {
                Surface(
                    onClick = onConfigScheme,
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "当前未设规则，点此配置分流规则测算效果",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. 立体步进式分流规则链路 (Rules Pipeline)
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckRulesPipelineSection(
    scheme: PaycheckSchemeUiModel,
    accountsById: Map<Long, AccountUiModel>,
    plansById: Map<Long, SavingPlanUiModel>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasRules = scheme.rules.isNotEmpty()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "执行规则链路",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = if (hasRules) "${scheme.rules.size} 步" else "0 步",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Surface(
                onClick = onEdit,
                shape = RoundedCornerShape(10.dp),
                color = colors.primaryContainer.copy(alpha = 0.7f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasRules) "调整规则" else "+ 配置规则",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                }
            }
        }

        if (hasRules) {
            scheme.rules.forEachIndexed { index, rule ->
                PaycheckStepCard(
                    stepIndex = index + 1,
                    rule = rule,
                    accountsById = accountsById,
                    plansById = plansById
                )
            }

            // 守护兜底规则：自动清欠
            PaycheckSafeguardCard(autoClearDebts = scheme.autoClearDebts)
        } else {
            // 空状态引导卡
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colors.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "尚未建立发薪分配规则",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                        color = colors.onSurface
                    )
                    Text(
                        text = "设置规则后，工资入账将按设定次序自动转入储蓄卡、投入心愿计划，助你井井有条地管好每一分钱。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(text = "立即添加第一条分流规则", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 单步规则卡片：立体化展示 */
@Composable
private fun PaycheckStepCard(
    stepIndex: Int,
    rule: PaycheckRuleUiModel,
    accountsById: Map<Long, AccountUiModel>,
    plansById: Map<Long, SavingPlanUiModel>
) {
    val colors = MaterialTheme.colorScheme
    val isToAccount = rule.kind == PaycheckRuleKind.TO_ACCOUNT_FIXED || rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT
    val isPct = rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT || rule.kind == PaycheckRuleKind.TO_PLAN_PCT

    val targetAccount = if (isToAccount) accountsById[rule.targetId] else null
    val targetPlan = if (!isToAccount) plansById[rule.targetId] else null
    val targetName = targetAccount?.name ?: targetPlan?.name ?: "未知目标"

    val iconName = targetAccount?.iconName ?: "savings"
    val colorHex = targetAccount?.colorHex ?: targetPlan?.colorHex ?: 0xFF0284C7L

    val amountHighlight = if (isPct) {
        "${rule.percentBps / 100}%"
    } else {
        "¥" + MoneyUtils.centsToYuanString(rule.amountCents, withGrouping = true)
    }
    val typeSubtitle = if (isPct) "按到手薪资比例" else "固定额划转"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step Number Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = String.format("%02d", stepIndex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Target Icon
            CategoryIconView(
                iconName = iconName,
                colorHex = colorHex,
                size = 36.dp,
                iconSize = 18.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Middle: Name & Action Type
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isToAccount) "划转到" else "攒存至",
                        fontSize = 10.5.sp,
                        color = colors.outline,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = targetName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isToAccount) "资金账户" else "攒钱目标计划",
                    fontSize = 10.sp,
                    color = colors.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount / Percent Badge
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPct) Color(0xFF0284C7).copy(alpha = 0.12f) else colors.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = amountHighlight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPct) Color(0xFF0284C7) else colors.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = typeSubtitle,
                    fontSize = 9.sp,
                    color = colors.outline
                )
            }
        }
    }
}

/** 智能清欠守护卡片 */
@Composable
private fun PaycheckSafeguardCard(autoClearDebts: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (autoClearDebts) Color(0xFF8B5CF6).copy(alpha = 0.06f) else colors.surface
        ),
        border = BorderStroke(
            1.dp,
            if (autoClearDebts) Color(0xFF8B5CF6).copy(alpha = 0.25f) else colors.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (autoClearDebts) Color(0xFF8B5CF6).copy(alpha = 0.14f) else colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (autoClearDebts) Color(0xFF8B5CF6) else colors.outline,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "兜底守护 · 智能清欠",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = if (autoClearDebts) "规则执行后如有结余，自动填平信用卡等负余额账户" else "结余清欠已关闭，剩余金额将全部留存工资账户",
                    fontSize = 10.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (autoClearDebts) Color(0xFF8B5CF6).copy(alpha = 0.15f) else colors.surfaceVariant
            ) {
                Text(
                    text = if (autoClearDebts) "已守护" else "已停用",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (autoClearDebts) Color(0xFF7C3AED) else colors.outline,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. 时间轴分账执行履历 (Timeline History)
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckTimelineHistorySection(
    history: List<PaycheckLastRunUiModel>,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "分账执行履历",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = colors.onSurface
                )
            }
            Text(
                text = if (history.isEmpty()) "暂无记录" else "共 ${history.size} 次",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.outline
            )
        }

        if (history.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "尚未有分账执行记录",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "保存「工资」类入账或在下方手动补分后，完整执行痕迹将在此处呈现",
                        fontSize = 10.5.sp,
                        color = colors.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEachIndexed { index, run ->
                    PaycheckTimelineCard(
                        run = run,
                        isPrivacyMode = isPrivacyMode,
                        isLatest = index == 0
                    )
                }
            }
        }
    }
}

/** 时间轴单条记录卡片 */
@Composable
private fun PaycheckTimelineCard(
    run: PaycheckLastRunUiModel,
    isPrivacyMode: Boolean,
    isLatest: Boolean
) {
    val colors = MaterialTheme.colorScheme

    val amountText = if (isPrivacyMode) PRIVACY_MASK else "¥" + MoneyUtils.centsToYuanString(run.amountCents ?: 0L, withGrouping = true)
    val remainText = run.remainingCents?.let { remaining ->
        if (remaining > 0L) {
            " · 留存 " + (if (isPrivacyMode) PRIVACY_MASK else "¥" + MoneyUtils.centsToYuanString(remaining, withGrouping = true))
        } else ""
    } ?: ""

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) colors.primaryContainer.copy(alpha = 0.2f) else colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            if (isLatest) colors.primary.copy(alpha = 0.25f) else colors.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timeline Node Circle
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (run.auto) Color(0xFF10B981) else Color(0xFF0284C7))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = DateTimeUtils.formatDateTime(run.at ?: 0L),
                        fontSize = 11.sp,
                        color = colors.outline,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (run.auto) Color(0xFF10B981).copy(alpha = 0.12f) else colors.surfaceVariant
                    ) {
                        Text(
                            text = if (run.auto) "自动执行" else "手动补分",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (run.auto) Color(0xFF059669) else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amountText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${run.actionCount ?: 0} 笔分流动作$remainText",
                        fontSize = 11.5.sp,
                        color = colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!run.sourceAccountName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "来源: ${run.sourceAccountName}",
                        fontSize = 10.sp,
                        color = colors.outline
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. 手动补分入口卡片
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckManualEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
        modifier = modifier.height(50.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "手动补分（处理未自动分账的工资）",
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = colors.primary
            )
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
