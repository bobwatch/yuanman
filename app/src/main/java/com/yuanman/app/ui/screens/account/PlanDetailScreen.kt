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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
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
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.roundToInt

/** 超额警示琥珀 —— 与账户页警示同值 Color(0xFFFF9800) */
private val PlanDetailAmber = Color(0xFFFF9800)

/**
 * 攒钱计划详情 二级页（账户 Tab 计划小卡点击进入，v0.0.4 取代「攒钱计划管理」二级页）。
 *
 * 内容：
 *  - 顶栏右上角：编辑计划（名称 / 目标金额 / 专款账户 / 主题色）与删除计划（error）入口
 *  - 计划进度卡：名称 / 专款账户 / 已圈 / 目标（0=不设上限）/ 进度条 + 达成% / 超额警示
 *  - 操作行：[存一笔] [取一笔]（v0.0.4+ 由「再存一笔 / 取出一笔」简化命名）
 *  - 攒钱 / 取出记录卡：每笔一条（攒入/取出 + 金额 + 时间 + 来源说明），支持删除
 *    （删除攒入 → 已圈减少；删除取出 → 已圈加回）
 */
@Composable
fun PlanDetailScreen(
    viewModel: AccountViewModel,
    planId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    val plan = remember(uiState.plans, planId) {
        uiState.plans.firstOrNull { it.id == planId }
    }
    val accountsById = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }
    val privacy = uiState.isPrivacyMode

    // ---- 弹层状态 ----
    var showDeposit by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showEditForm by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<PlanEventUiModel?>(null) }
    var planToDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = plan?.name ?: "计划详情",
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
                    if (plan != null) {
                        // 编辑 / 删除计划入口收进顶栏右上角，替代原页面底部的「管理卡」
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showEditForm = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑计划",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                planToDelete = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除计划",
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
            if (plan == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyStateView(
                        title = "计划不存在或已删除",
                        description = "该攒钱计划可能已被删除，请返回账户页查看最新列表",
                        icon = Icons.Default.Savings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val holder = accountsById[plan.holderAccountId]
                val holderBalance = holder?.balanceCents
                val overdrawn = holderBalance != null && planIsOverdrawn(plan, holderBalance)
                val hasTarget = plan.targetAmountCents > 0L
                val done = hasTarget && plan.earmarkedCents >= plan.targetAmountCents
                val maxDeposit = availableToEarmarkFor(plan.holderAccountId, holderBalance ?: 0L, uiState.plans)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---- 进度卡 ----
                    PlanDetailProgressCard(
                        plan = plan,
                        holderName = holder?.name,
                        isOverdrawn = overdrawn,
                        shortfallCents = if (overdrawn) plan.earmarkedCents - holderBalance!! else 0L,
                        isDone = done,
                        isPrivacyMode = privacy
                    )

                    // ---- 操作行：存一笔 / 取一笔 ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDeposit = true
                            },
                            enabled = holder != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("存一笔", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showWithdraw = true
                            },
                            enabled = holder != null && plan.earmarkedCents > 0L,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "取一笔",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ---- 攒钱 / 取出记录 ----
                    PlanEventCard(
                        events = plan.events,
                        isPrivacyMode = privacy,
                        onDeleteEvent = { eventToDelete = it }
                    )
                }
            }
        }
    }

    // ---- 存一笔 ----
    if (showDeposit && plan != null) {
        PlanDepositSheet(
            plan = plan,
            maxCents = availableToEarmarkFor(plan.holderAccountId, accountsById[plan.holderAccountId]?.balanceCents ?: 0L, uiState.plans),
            isPrivacyMode = privacy,
            onDismiss = { showDeposit = false },
            onConfirm = { cents ->
                viewModel.depositToPlan(plan.id, cents)
                showDeposit = false
                toast.success("已存 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 取一笔 ----
    if (showWithdraw && plan != null) {
        PlanWithdrawSheet(
            plan = plan,
            isPrivacyMode = privacy,
            onDismiss = { showWithdraw = false },
            onConfirm = { cents ->
                viewModel.withdrawFromPlan(plan.id, cents)
                showWithdraw = false
                toast.success("已取 ¥" + MoneyUtils.centsToYuanString(cents, withGrouping = true))
            }
        )
    }

    // ---- 编辑计划（名称 / 目标 / 专款账户 / 颜色）----
    if (showEditForm && plan != null) {
        PlanFormSheet(
            planToEdit = plan,
            accounts = uiState.accounts,
            onDismiss = { showEditForm = false },
            onSave = { name, targetCents, holderId, colorHex ->
                viewModel.updatePlan(
                    plan.copy(
                        name = name,
                        targetAmountCents = targetCents,
                        holderAccountId = holderId,
                        colorHex = colorHex
                    )
                )
                showEditForm = false
                toast.success("计划已更新")
            }
        )
    }

    // ---- 删除单笔存/取记录确认 ----
    eventToDelete?.let { event ->
        val label = event.kind.actionLabel()
        val amountText = "¥" + MoneyUtils.centsToYuanString(event.amountCents, withGrouping = true)
        ConfirmDeleteDialog(
            visible = true,
            title = "删除记录",
            message = "确定删除这笔「$label $amountText · " +
                DateTimeUtils.formatDate(event.at) + "」的记录吗？删除后已圈金额会相应" +
                (if (event.kind == PlanEventKind.DEPOSIT) "减少" else "加回") + "。",
            onConfirm = {
                plan?.let { viewModel.deletePlanEvent(it.id, event.id) }
                eventToDelete = null
                toast.success("记录已删除")
            },
            onDismiss = { eventToDelete = null }
        )
    }

    // ---- 删除计划确认 ----
    if (planToDelete) {
        ConfirmDeleteDialog(
            visible = true,
            title = "删除计划",
            message = "确定要删除攒钱计划「${plan?.name}」吗？删除后专款释放，攒钱/取出记录一并删除，不可撤销",
            onConfirm = {
                plan?.let {
                    viewModel.deletePlan(it.id)
                    toast.success("计划已删除")
                    onBack()
                }
                planToDelete = false
            },
            onDismiss = { planToDelete = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 区块组件
// ---------------------------------------------------------------------------

/** 计划进度卡：彩点+名称(达标✓) / 专款账户 / 已圈·目标 / 进度条+达成% / 超额警示 */
@Composable
private fun PlanDetailProgressCard(
    plan: SavingPlanUiModel,
    holderName: String?,
    isOverdrawn: Boolean,
    shortfallCents: Long,
    isDone: Boolean,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val planColor = Color(plan.colorHex)
    val hasTarget = plan.targetAmountCents > 0L
    val percent = if (hasTarget) {
        ((plan.earmarkedCents.toDouble() / plan.targetAmountCents.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    } else 0
    val fraction = if (hasTarget) {
        (plan.earmarkedCents.toFloat() / plan.targetAmountCents.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            if (isOverdrawn) PlanDetailAmber.copy(alpha = 0.45f)
            else scheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 行1：彩点 + 名称 + 达标 ✓
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(planColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isDone && !isOverdrawn) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已达标",
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 行2：专款账户（+ 超额警示行）
            if (isOverdrawn) {
                Text(
                    text = "专款账户余额不足，已圈超出 ¥" +
                        (if (isPrivacyMode) "••••" else MoneyUtils.centsToYuanString(shortfallCents, withGrouping = true)) +
                        "，请补回",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = PlanDetailAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "专款账户 · " + (holderName ?: "账户已删除"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 行3：已圈大数字 + 目标
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1
                )
                if (hasTarget) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "/ 目标 " +
                            (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.targetAmountCents, withGrouping = true)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = scheme.outline,
                        maxLines = 1
                    )
                }
            }

            // 行4：进度条 + 达成%（不设限则只显示「未设上限」说明）
            if (hasTarget) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(scheme.surfaceVariant)
                    ) {
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxSize()
                                    .background(planColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = scheme.primary,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "未设上限 · 攒多少是多少",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

/** 攒钱 / 取出记录卡：每笔一行（攒入/取出 + 金额 + 时间 + 来源说明），行尾删除 */
@Composable
private fun PlanEventCard(
    events: List<PlanEventUiModel>,
    isPrivacyMode: Boolean,
    onDeleteEvent: (PlanEventUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val reversed = events.sortedByDescending { it.at }

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
                    text = "攒钱 / 取出记录",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (reversed.isEmpty()) "还没有流水" else "共 ${reversed.size} 笔",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = scheme.outline,
                    maxLines = 1
                )
            }

            if (reversed.isEmpty()) {
                Text(
                    text = "存一笔、取一笔，包括发薪分配自动攒入，都会记录在这里，可随时删除调整",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                reversed.forEachIndexed { index, event ->
                    if (index > 0) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    PlanEventRow(
                        event = event,
                        isPrivacyMode = isPrivacyMode,
                        onDelete = { onDeleteEvent(event) }
                    )
                }
            }
        }
    }
}

/** 单笔记录行：图标（攒入=＋ / 取出=－）+ 说明 + 时间 | 金额（+绿/−灰）+ 删除 */
@Composable
private fun PlanEventRow(
    event: PlanEventUiModel,
    isPrivacyMode: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDeposit = event.kind == PlanEventKind.DEPOSIT
    val mainColor = if (isDeposit) scheme.primary else scheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 前置圆形图标
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isDeposit) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDeposit) Icons.Default.Add else Icons.Default.Remove,
                contentDescription = null,
                tint = if (isDeposit) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            val noteText = event.note?.takeIf { it.isNotBlank() }
            Text(
                text = if (noteText != null) "${event.kind.actionLabel()} · $noteText" else event.kind.actionLabel(),
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
                text = DateTimeUtils.formatDateTime(event.at),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = scheme.outline,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPrivacyMode) {
                if (isDeposit) "+¥ ••••" else "-¥ ••••"
            } else {
                (if (isDeposit) "+" else "-") + "¥" +
                    MoneyUtils.centsToYuanString(event.amountCents, withGrouping = true)
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = mainColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除这笔记录",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 计划事件动作名（v0.0.4+ 简化命名：存一笔 / 取一笔） */
private fun PlanEventKind.actionLabel(): String = when (this) {
    PlanEventKind.DEPOSIT -> "存一笔"
    PlanEventKind.WITHDRAW -> "取一笔"
}
