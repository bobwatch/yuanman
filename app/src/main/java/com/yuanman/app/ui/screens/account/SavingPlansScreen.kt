@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.utils.MoneyUtils

/**
 * 攒钱计划 二级页（设计文档 saving-plans-and-paycheck-v0.3.md §2.2）
 *
 * 页面由 uiState 驱动（collectAsState，与 AccountScreen 同款）：
 *  - 区块A 总进度卡（Σ已圈 / Σ目标、总进度条、超额任一计划 → 琥珀警示句）
 *  - 区块B 发薪分配方案入口卡（→ SchemeEditorSheet，文件2 §2.5）
 *  - 区块C 计划纵向卡列表（点击 → PlanActionSheet 操作面板，文件2 §2.4）
 *  - 空计划态 → EmptyStateView；全部金额按 isPrivacyMode 掩码为 ¥ ••••
 * 状态栏 inset 由本页 Scaffold（contentWindowInsets = statusBars）统一处理，不自行画 inset。
 */
@Composable
fun SavingPlansScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    // ---- 弹层状态 ----
    var planToEdit by remember { mutableStateOf<SavingPlanUiModel?>(null) } // 非空 = 编辑该计划
    var showPlanForm by remember { mutableStateOf(false) }
    var panelPlan by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var showDeposit by remember { mutableStateOf(false) }
    var depositPlan by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var showWithdraw by remember { mutableStateOf(false) }
    var withdrawPlan by remember { mutableStateOf<SavingPlanUiModel?>(null) }
    var showSchemeEdit by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<SavingPlanUiModel?>(null) }

    val plans = uiState.plans
    val privacy = uiState.isPrivacyMode
    val accountsById = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }

    // 派生：Σ已圈 / Σ目标 / 是否存在超额计划（earmark > 其专款账户余额）
    val totalEarmarkedCents = plans.sumOf { it.earmarkedCents }
    val totalTargetCents = plans.sumOf { it.targetAmountCents }
    val anyOverdrawn = plans.any { plan ->
        val holder = accountsById[plan.holderAccountId]
        holder != null && planIsOverdrawn(plan, holder.balanceCents)
    }

    // 「新建」入口统一：清 planToEdit 开表单
    val openCreateForm: () -> Unit = {
        planToEdit = null
        showPlanForm = true
    }

    // ---- 页面骨架：Scaffold 承接状态栏 inset，规范头部 + 可滚动内容 ----
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            // 规范头部（与 CategoryManageScreen / AddEditRecordScreen 同式）：左上返回钮 + 靠左标题 + 右侧紧凑「新建」
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "攒钱计划",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 右侧「新建」文字钮保持紧凑（12.5sp Bold，圆角 10dp 点击区）
                    Text(
                        text = "新建",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = openCreateForm)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- 区块A 总进度卡（0 个计划时展示 0 额总览，见 §2.2 线框）----
                    TotalOverviewCard(
                        totalEarmarkedCents = totalEarmarkedCents,
                        totalTargetCents = totalTargetCents,
                        planCount = plans.size,
                        anyOverdrawn = anyOverdrawn,
                        isPrivacyMode = privacy,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // ---- 区块B 发薪分配方案入口卡 ----
                    val ruleCount = uiState.paycheckScheme.rules.size
                    val ruleSummary = if (ruleCount == 0) {
                        "还没有规则"
                    } else {
                        "$ruleCount 条规则 · " +
                            if (uiState.paycheckScheme.autoClearDebts) "剩余自动清欠" else "未自动清欠"
                    }
                    SchemeEntryCard(
                        mainText = "发薪分配方案",
                        subText = ruleSummary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSchemeEdit = true
                        }
                    )

                    if (plans.isEmpty()) {
                        // ---- 无计划空态 ----
                        Spacer(modifier = Modifier.height(14.dp))
                        EmptyStateView(
                            title = "还没有攒钱计划",
                            description = "创建一个目标，工资到手自动往里攒",
                            icon = Icons.Outlined.AccountBalanceWallet,
                            actionButtonText = "新建计划",
                            onActionClick = openCreateForm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // ---- 区块C 计划纵向卡列表 ----
                        plans.forEach { plan ->
                            val holder = accountsById[plan.holderAccountId]
                            PlanSummaryCard(
                                plan = plan,
                                holderName = holder?.name,
                                holderBalanceCents = holder?.balanceCents,
                                isPrivacyMode = privacy,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    panelPlan = plan
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp)) // 底部预留 24dp
                }
            }
        }
    }

    // ---- 弹层：计划操作面板 ----
    panelPlan?.let { plan ->
        val holder = accountsById[plan.holderAccountId]
        val overdrawn = holder != null && planIsOverdrawn(plan, holder.balanceCents)
        val done = plan.targetAmountCents > 0L && plan.earmarkedCents >= plan.targetAmountCents
        PlanActionSheet(
            plan = plan,
            holderName = holder?.name,
            isOverdrawn = overdrawn,
            isDone = done,
            isPrivacyMode = privacy,
            onDismiss = { panelPlan = null },
            onDeposit = {
                depositPlan = plan
                showDeposit = true
            },
            onWithdraw = {
                withdrawPlan = plan
                showWithdraw = true
            },
            onEdit = {
                planToEdit = plan
                showPlanForm = true
            },
            onDelete = { planToDelete = plan }
        )
    }

    // ---- 弹层：新建 / 编辑计划表单 ----
    if (showPlanForm) {
        PlanFormSheet(
            planToEdit = planToEdit,
            accounts = uiState.accounts,
            onDismiss = {
                showPlanForm = false
                planToEdit = null
            },
            onSave = { name, targetCents, holderId, colorHex ->
                if (planToEdit != null) {
                    val updated = planToEdit!!.copy(
                        name = name,
                        targetAmountCents = targetCents,
                        holderAccountId = holderId,
                        colorHex = colorHex
                    )
                    viewModel.updatePlan(updated)
                    toast.success("计划已更新")
                } else {
                    viewModel.createPlan(name, targetCents, holderId, colorHex)
                    toast.success("计划「$name」创建成功")
                }
                showPlanForm = false
                planToEdit = null
            }
        )
    }

    // ---- 弹层：再存一笔 ----
    if (showDeposit && depositPlan != null) {
        val target = depositPlan!!
        PlanDepositSheet(
            plan = target,
            maxCents = availableToEarmarkFor(
                target.holderAccountId,
                accountsById[target.holderAccountId]?.balanceCents ?: 0L,
                plans
            ),
            isPrivacyMode = privacy,
            onDismiss = {
                showDeposit = false
                depositPlan = null
            },
            onConfirm = { cents ->
                viewModel.depositToPlan(target.id, cents)
                showDeposit = false
                depositPlan = null
                toast.success("已再存 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 弹层：撤回专款 ----
    if (showWithdraw && withdrawPlan != null) {
        val target = withdrawPlan!!
        PlanWithdrawSheet(
            plan = target,
            isPrivacyMode = privacy,
            onDismiss = {
                showWithdraw = false
                withdrawPlan = null
            },
            onConfirm = { cents ->
                viewModel.withdrawFromPlan(target.id, cents)
                showWithdraw = false
                withdrawPlan = null
                toast.success("已撤回 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 弹层：发薪分配方案编辑（即改即存，关闭即生效）----
    if (showSchemeEdit) {
        SchemeEditorSheet(
            accounts = uiState.accounts,
            plans = uiState.plans,
            scheme = uiState.paycheckScheme,
            onDismiss = { showSchemeEdit = false },
            onSchemeChange = { edited -> viewModel.savePaycheckScheme(edited) }
        )
    }

    // ---- 弹窗：删除计划确认 ----
    ConfirmDeleteDialog(
        visible = planToDelete != null,
        title = "删除计划",
        message = "确定要删除攒钱计划「${planToDelete?.name}」吗？删除后专款释放。注意删除不可撤销",
        onConfirm = {
            planToDelete?.let {
                viewModel.deletePlan(it.id)
                toast.success("计划已删除")
            }
            planToDelete = null
        },
        onDismiss = { planToDelete = null }
    )
}

/** 超额警示琥珀 —— 与账户页警示同值 Color(0xFFFF9800) */
private val SavingPlanAmberColor = Color(0xFFFF9800)

// ---------------------------------------------------------------------------
// 区块A：总进度卡
// ---------------------------------------------------------------------------

/**
 * 区块A 总进度卡（§2.2）：Σ已圈 / Σ目标 / 总进度条 + 百分比 / 次级行；
 * 存在任一计划超额 → 主数字转琥珀 + 底部警示句。
 */
@Composable
private fun TotalOverviewCard(
    totalEarmarkedCents: Long,
    totalTargetCents: Long,
    planCount: Int,
    anyOverdrawn: Boolean,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val mainColor = if (anyOverdrawn) SavingPlanAmberColor else scheme.onSurface
    val earmarkedText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        "¥" + MoneyUtils.centsToYuanString(totalEarmarkedCents, withGrouping = true)
    }
    val hasTargets = totalTargetCents > 0L
    val progressFraction = if (hasTargets) {
        (totalEarmarkedCents.toFloat() / totalTargetCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressPercent = if (hasTargets) {
        (((totalEarmarkedCents * 100L) / totalTargetCents).coerceAtMost(100L)).toInt()
    } else {
        0
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 小标题
            Text(
                text = "专款总览",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = scheme.outline
            )
            // 主行：Σ已圈
            Text(
                text = "Σ已圈 $earmarkedText",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = mainColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 总进度条 + 百分比（仅 Σ目标 > 0）
            if (hasTargets) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlanThinBar(
                        fraction = progressFraction,
                        color = scheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            // 次级行
            Text(
                text = if (hasTargets) {
                    "目标 " +
                        (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(totalTargetCents, withGrouping = true)) +
                        " · $planCount 个计划"
                } else {
                    "$planCount 个计划 · 未设目标"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = scheme.outline
            )
            // 超额警示句
            if (anyOverdrawn) {
                Text(
                    text = "有计划的专款超过账户余额，请补回",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = SavingPlanAmberColor
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 区块B：发薪分配方案入口卡
// ---------------------------------------------------------------------------

/**
 * 区块B 发薪分配方案入口卡（§2.2 / §2.5）：主句 + 规则数副句 + ChevronRight；
 * 结构与 AccountHero.kt ReconcileReminderBanner 同族（Surface 可点行）。
 */
@Composable
private fun SchemeEntryCard(
    mainText: String,
    subText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
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
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "编辑发薪分配方案",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 区块C：计划纵向卡
// ---------------------------------------------------------------------------

/**
 * 区块C 计划纵向卡（§2.2）：色点 + 名称（达标 ✓）/ 专款账户副行 / 已圈·目标金额行 /
 * 进度条（不设限则无）/ 超额或余额不足警示行；点击 → PlanActionSheet。
 */
@Composable
private fun PlanSummaryCard(
    plan: SavingPlanUiModel,
    holderName: String?,
    holderBalanceCents: Long?,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val planColor = Color(plan.colorHex)

    val done = plan.targetAmountCents > 0L && plan.earmarkedCents >= plan.targetAmountCents
    val hasTarget = plan.targetAmountCents > 0L
    val overdrawn = holderBalanceCents != null && planIsOverdrawn(plan, holderBalanceCents)
    val overdrawnDiff = if (overdrawn) plan.earmarkedCents - holderBalanceCents!! else 0L

    val earmarkedText =
        if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true)
    val targetText =
        if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.targetAmountCents, withGrouping = true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 行1：色点 + 名称（达标主色 ✓）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlanColorDot(colorHex = plan.colorHex)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (done) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已达标",
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 行2：专款账户
            Text(
                text = if (holderName != null) "$holderName · 专款账户" else "账户已删除",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 行3：已圈 / 目标（target == 0 → 不设限口径）
            if (hasTarget) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已圈 $earmarkedText",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = scheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = " / 目标 $targetText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = scheme.outline,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "已圈 $earmarkedText（不设限）",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = scheme.outline,
                    maxLines = 1
                )
            }

            // 行4：进度条（填充 = 计划色；earmark 溢出 → 条满 + 琥珀「已超出目标」）
            if (hasTarget) {
                val fraction = (plan.earmarkedCents.toFloat() / plan.targetAmountCents.toFloat()).coerceAtLeast(0f)
                val overflow = plan.earmarkedCents > plan.targetAmountCents
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlanThinBar(
                        fraction = if (overflow) 1f else fraction,
                        color = planColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (overflow) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已超出目标",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = SavingPlanAmberColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // 行5：专款账户余额低于已圈 → 琥珀警示
            if (overdrawn) {
                Text(
                    text = if (isPrivacyMode) {
                        "余额低于专款 ¥ ••••，请补回"
                    } else {
                        "余额低于专款 ¥" + MoneyUtils.centsToYuanString(overdrawnDiff, withGrouping = true) + "，请补回"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = SavingPlanAmberColor,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 通用小件
// ---------------------------------------------------------------------------

/** 计划色圆点 */
@Composable
private fun PlanColorDot(colorHex: Long, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorHex))
    )
}

/** 4dp 细进度条：轨道 surfaceVariant，填充按 fraction 取色 */
@Composable
private fun PlanThinBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(fraction.coerceAtLeast(0.01f).coerceAtMost(1f))
                    .background(color)
            )
        }
    }
}
