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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.SheetTitle
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 发薪分配 二级页。
 *
 * 四段：自动分账开关与动线、分账试算、分配规则清单、执行记录；末尾是手动补分入口。
 * 引擎语义见 [planPaycheckActions]。
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
                        text = "发薪分配方案",
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
                        autoEnabled = uiState.paycheckAutoEnabled,
                        onConfigScheme = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSchemeEdit = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---- 3. 立体步进式分流规则链路 (Rules Pipeline) ----
                    PaycheckRulesPipelineSection(
                        scheme = scheme,
                        enabled = uiState.paycheckAutoEnabled,
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
// 0. 共用：可横滑芯片行 / 图例
// ---------------------------------------------------------------------------

/** 四个区块标题统一字号，避免「卡片内标题」与「卡片外标题」两套层级 */
private val SECTION_TITLE_SIZE = 14.sp

/** 规则本次未生效的警示琥珀 —— 与项目其余警示位同值 */
private val WARN_AMBER = Color(0xFFFF9800)

/**
 * 可横滑的芯片行。
 *
 * 行内只放芯片，标签一律由调用方摆在行外——否则横滑会把标签一起推走，只剩残缺笔画。
 * 行尾在未滑到末端时叠一层与卡片底色同色的渐隐，提示「后面还有内容可滑」。
 */
@Composable
private fun ScrollableChipRow(
    fadeColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val canScrollForward by remember {
        derivedStateOf { scrollState.value < scrollState.maxValue }
    }
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (canScrollForward) {
                val fadeWidth = 32.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                        startX = size.width - fadeWidth,
                        endX = size.width
                    )
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** 占比条图例：圆点颜色与对应分段一致 */
@Composable
private fun BarLegend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// 1. 自动分账开关 + 动线看板
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
                                text = "自动分账",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = SECTION_TITLE_SIZE
                                ),
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Status Dot
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (enabled) colors.primaryContainer else colors.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (enabled) colors.primary else colors.outline)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (enabled) "运行中" else "已暂停",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enabled) colors.onPrimaryContainer else colors.outline
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (enabled) "保存「工资」收入后，按规则自动划转" else "已关闭自动分账，可手动试算与补分",
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
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            checkedBorderColor = colors.primary,
                            uncheckedThumbColor = colors.outline,
                            uncheckedTrackColor = colors.surfaceVariant,
                            uncheckedBorderColor = colors.outline
                        )
                    )
                }

                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))

                // Pipeline Flow Diagram
                PaycheckPipelineDiagram(
                    enabled = enabled,
                    ruleCount = ruleCount,
                    autoClearDebts = autoClearDebts
                )
            }
        }
    }
}

/** 资金流向节点图示 */
@Composable
private fun PaycheckPipelineDiagram(
    enabled: Boolean,
    ruleCount: Int,
    autoClearDebts: Boolean
) {
    val colors = MaterialTheme.colorScheme

    // 总开关关闭时整条动线都不会跑：整体降透明度，并把两处描述运行状态的副标题改成「已暂停」，
    // 否则暂停后仍写「N步执行」「结余清零」，是在陈述不成立的事实。
    // 配色一律取主题语义色（secondary 蓝 / tertiary 靛），深浅色模式下自动跟随。
    val ruleColor = if (enabled && ruleCount > 0) colors.secondary else colors.outline
    val clearColor = if (enabled && autoClearDebts) colors.tertiary else colors.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
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
            subtitle = when {
                !enabled -> "已暂停"
                ruleCount > 0 -> "${ruleCount}步执行"
                else -> "未配置"
            },
            color = ruleColor
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.outlineVariant,
            modifier = Modifier.size(14.dp)
        )

        // Node 3: 结余清欠
        PipelineNodeItem(
            icon = Icons.Default.Shield,
            title = "结余清欠",
            subtitle = when {
                !enabled -> "已暂停"
                autoClearDebts -> "结余清零"
                else -> "未开启"
            },
            color = clearColor
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
            subtitle = "留在卡内",
            color = colors.primary
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
// 2. 分账试算
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckSimulatorCard(
    accounts: List<AccountUiModel>,
    plans: List<SavingPlanUiModel>,
    scheme: PaycheckSchemeUiModel,
    isPrivacyMode: Boolean,
    autoEnabled: Boolean,
    onConfigScheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    // 薪资：给一个常见数额起步，点金额可改成任意值（预设档位覆盖不了真实到手金额）
    var simSalaryCents by remember { mutableLongStateOf(1_200_000L) }
    var isEditingSalary by remember { mutableStateOf(false) }
    var salaryInput by remember { mutableStateOf("") }
    val salaryFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun finishSalaryEditing() {
        isEditingSalary = false
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(isEditingSalary) {
        if (isEditingSalary) {
            salaryFocusRequester.requestFocus()
            keyboard?.show()
        }
    }


    // 转账户类规则的目标账户：工资若「入账」到这些账户，规则会被判自转跳过。
    val ruleTargetAccountIds = remember(scheme.rules) {
        scheme.rules
            .filter {
                it.kind == PaycheckRuleKind.TO_ACCOUNT_FIXED ||
                    it.kind == PaycheckRuleKind.TO_ACCOUNT_PCT
            }
            .map { it.targetId }
            .toSet()
    }

    // 默认入账账户：优先挑「有余额且不是规则目标」的账户，避免一进页面就撞上自转跳过。
    val defaultSourceAccount = remember(accounts, ruleTargetAccountIds) {
        accounts.firstOrNull { it.balanceCents > 0L && it.id !in ruleTargetAccountIds }
            ?: accounts.firstOrNull { it.balanceCents > 0L }
            ?: accounts.first()
    }

    var simSourceId by remember { mutableLongStateOf(defaultSourceAccount.id) }
    val simSourceAccount = accounts.firstOrNull { it.id == simSourceId } ?: defaultSourceAccount

    // 测算逻辑：创建虚拟充足源账户，使规则得以完整模拟
    val simSource = remember(simSourceAccount, simSalaryCents) {
        simSourceAccount.copy(balanceCents = simSalaryCents * 3)
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
    // 色条按展示用的整数百分比绘制，避免出现「图例写 0%、条上却有一丝细缝」
    val allocatedPctInt = allocatedPct.toInt()
    val remainingPctInt = 100 - allocatedPctInt

    // 金额为 0 且带 note 的步骤 = 因约束没生效，原因必须露出来，否则「0 条规则生效」无从解释。
    val skippedSteps = simResult.steps.filter {
        it.kind != PaycheckStepKind.REMAIN && it.amountCents <= 0L && !it.note.isNullOrBlank()
    }
    val hasSelfTransferSkip = skippedSteps.any { it.note == "自转跳过" }

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "分账试算",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = SECTION_TITLE_SIZE
                    ),
                    color = colors.onSurface
                )
            }

            if (!autoEnabled) {
                Text(
                    text = "自动分账已关闭，以下是手动补分时的试算结果",
                    fontSize = 10.sp,
                    color = colors.outline
                )
            }

            // 入账账户：试算用的来源账户，换成规则目标账户会触发「自转跳过」
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "入账账户",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.outline
                )
                ScrollableChipRow(fadeColor = colors.surface) {
                    accounts.forEach { acc ->
                        val isPicked = acc.id == simSourceAccount.id
                        FilterChip(
                            selected = isPicked,
                            onClick = { simSourceId = acc.id },
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
                                    text = acc.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            // 薪资
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "薪资",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.outline
                    )
                    if (isEditingSalary) {
                        TextButton(onClick = { finishSalaryEditing() }) {
                            Text(
                                text = "完成",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                    } else {
                        Surface(
                            onClick = {
                                salaryInput = MoneyUtils.centsToPlainYuan(simSalaryCents)
                                isEditingSalary = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.primary.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "¥" + MoneyUtils.centsToCompactYuan(simSalaryCents),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "自定义薪资",
                                    tint = colors.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
                if (isEditingSalary) {
                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = { raw ->
                            val cleaned = MoneyUtils.sanitizeYuanInput(raw)
                            salaryInput = cleaned
                            simSalaryCents = MoneyUtils.yuanInputToCents(cleaned)
                        },
                        placeholder = { Text("到手金额 (元)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(salaryFocusRequester)
                    )
                }
            }

            // 分流占比条：划转段取主色、留存段取中性灰，图例圆点与段同色
            val remainBarColor = colors.outline.copy(alpha = 0.45f)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (allocatedPctInt > 0) {
                        Box(
                            modifier = Modifier
                                .weight(allocatedPctInt.toFloat())
                                .fillMaxHeight()
                                .background(colors.primary)
                        )
                    }
                    if (remainingPctInt > 0) {
                        Box(
                            modifier = Modifier
                                .weight(remainingPctInt.toFloat())
                                .fillMaxHeight()
                                .background(remainBarColor)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BarLegend(
                        color = colors.primary,
                        text = "划转 $allocatedPctInt%"
                    )
                    BarLegend(
                        color = remainBarColor,
                        text = "留存 $remainingPctInt%"
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
                            text = "划转合计",
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
                            text = "${simResult.steps.count { it.kind != PaycheckStepKind.REMAIN && it.amountCents > 0 }} 条规则生效",
                            fontSize = 9.5.sp,
                            color = colors.outline
                        )
                    }
                }

                // Metric 2: Remaining
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.22f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "留存可用",
                            fontSize = 10.5.sp,
                            color = colors.outline,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMode) PRIVACY_MASK else "¥" + MoneyUtils.centsToYuanString(remainingCents, withGrouping = true),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "留在工资卡",
                            fontSize = 9.5.sp,
                            color = colors.outline
                        )
                    }
                }
            }

            // 未生效的规则必须把原因说出来：只显示「0 条规则生效」而藏起 note,
            // 用户会以为自己的规则丢失了。
            if (skippedSteps.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WARN_AMBER.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, WARN_AMBER.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = WARN_AMBER,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${skippedSteps.size} 条规则本次未生效",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WARN_AMBER
                            )
                        }
                        skippedSteps.forEach { step ->
                            Text(
                                text = "· ${step.caption}：${step.note}",
                                fontSize = 10.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                        if (hasSelfTransferSkip) {
                            Text(
                                text = "换一个「入账账户」即可让该规则生效",
                                fontSize = 10.sp,
                                color = WARN_AMBER
                            )
                        }
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
                            text = "还没有规则，点此添加",
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
// 3. 分配规则清单
// ---------------------------------------------------------------------------

@Composable
private fun PaycheckRulesPipelineSection(
    scheme: PaycheckSchemeUiModel,
    enabled: Boolean,
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
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "分配规则",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = SECTION_TITLE_SIZE
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
                // 「N 步」是配置事实，但总开关关闭时必须说明它不会自动跑
                if (!enabled) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "已暂停",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.outline,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
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
            PaycheckSafeguardCard(autoClearDebts = scheme.autoClearDebts, enabled = enabled)
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
                        text = "还没有分配规则",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                        color = colors.onSurface
                    )
                    Text(
                        text = "设置后，工资入账会按顺序自动划转到对应账户或攒钱计划。",
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
                        Text(text = "添加规则", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 单条规则卡片 */
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
                    text = if (isToAccount) "转入账户" else "攒钱目标计划",
                    fontSize = 10.sp,
                    color = colors.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount / Percent Badge
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPct) colors.secondaryContainer else colors.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = amountHighlight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPct) colors.onSecondaryContainer else colors.primary,
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

/** 结余清欠卡片 */
@Composable
private fun PaycheckSafeguardCard(autoClearDebts: Boolean, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    // 总开关关闭时清欠同样不会执行，卡片不该继续显示「已守护」
    val active = enabled && autoClearDebts

    val badgeText = when {
        !enabled -> "已暂停"
        autoClearDebts -> "已守护"
        else -> "已停用"
    }
    val description = when {
        !enabled -> "总开关已关闭，规则执行后不会自动清欠"
        autoClearDebts -> "规则执行后如有结余，自动填平信用卡等负余额账户"
        else -> "结余清欠已关闭，剩余金额将全部留存工资账户"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.tertiaryContainer.copy(alpha = 0.45f) else colors.surface
        ),
        border = BorderStroke(
            1.dp,
            if (active) colors.tertiary.copy(alpha = 0.28f) else colors.outlineVariant.copy(alpha = 0.35f)
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
                    .background(if (active) colors.tertiary.copy(alpha = 0.14f) else colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (active) colors.tertiary else colors.outline,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "结余清欠",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = colors.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (active) colors.tertiary.copy(alpha = 0.16f) else colors.surfaceVariant
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) colors.tertiary else colors.outline,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. 执行记录
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
                    text = "执行记录",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = SECTION_TITLE_SIZE
                    ),
                    color = colors.onSurface
                )
            }
            // 空态说明交给下方卡片，避免同一件事在标题行和卡片里各说一遍
            if (history.isNotEmpty()) {
                Text(
                    text = "共 ${history.size} 次",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.outline
                )
            }
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
                        text = "还没有执行记录",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "保存「工资」收入或手动补分后，记录会显示在这里",
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

/** 单条执行记录卡片 */
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
                    .background(if (run.auto) colors.primary else colors.secondary)
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
                        color = if (run.auto) colors.primaryContainer else colors.surfaceVariant
                    ) {
                        Text(
                            text = if (run.auto) "自动执行" else "手动补分",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (run.auto) colors.onPrimaryContainer else colors.onSurfaceVariant,
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
                        text = "${run.actionCount ?: 0} 笔划转$remainText",
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
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
                    text = "手动补分",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = colors.primary
                )
            }
        }
        Text(
            text = "用于没被自动分账的工资：选一笔本月工资或手填金额，按当前规则执行一次",
            fontSize = 10.5.sp,
            color = colors.outline,
            textAlign = TextAlign.Center
        )
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
            SheetTitle(
                title = "手动补分",
                subtitle = "按当前规则执行一次，用于没被自动分账的工资。"
            )

            // ---- 待处理工资（本月未自动分账）----
            if (candidates.isEmpty()) {
                Text(
                    text = "本月没有待处理的工资",
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
