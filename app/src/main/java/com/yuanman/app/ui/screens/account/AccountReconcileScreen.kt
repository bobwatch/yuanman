@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

/** 待核对警示琥珀 —— 与首页预算条同值 Color(0xFFFF9800) */
private val ReconcileAmberColor = Color(0xFFFF9800)

/** 对账状态 tone → 状态文案颜色（文案一律取共享口径，此处只做颜色映射） */
private fun reconcileToneColor(tone: ReconcileTone, scheme: ColorScheme): Color = when (tone) {
    ReconcileTone.FRESH -> scheme.primary
    ReconcileTone.NORMAL -> scheme.onSurfaceVariant
    ReconcileTone.OVERDUE -> ReconcileAmberColor
    ReconcileTone.NEVER -> scheme.outline
}

/**
 * 账户核对 二级页（hero「账户核对」入口 / 待核对提醒横条进入）。v0.0.4 重设计：
 *
 * 页面只回答一个问题：「这期有哪些账户该核对了？账面共多少？逐个点开核完即可」。
 *  - 顶部一行轻量「对账周期」入口（→ ReconcileCycleSheet，全局默认；账户自定义覆盖在详情页，
 *    覆盖优先级最高），不再占整卡
 *  - 聚合横幅（最前最显眼）：有待核对 → 琥珀横幅「X 个账户待核对 + 账面合计」；
 *    无 → 主色横幅「暂无到期账户」
 *  - 待核对清单紧跟横幅，整卡点击直达该账户对账框（无行内按钮），逾期越久排越前
 *  - 其余已核对账户收进可展开分组「近期已核对」，默认收起；展开后卡片降饱和弱化
 *  - 分组口径 = 共享 [AccountReconcileStatus.isPending]（OVERDUE/NEVER 待核对、
 *    FRESH/NORMAL 近期已核对）；「跳过本期提醒」属 hero 侧提醒抑制，本页仍按纯状态展示
 *  - 无账户 → 周期行 + EmptyStateView 引导
 */
@Composable
fun AccountReconcileScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current
    val haptic = LocalHapticFeedback.current

    var accountToReconcile by remember { mutableStateOf<AccountUiModel?>(null) }
    var showCycleSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "账户核对",
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.accounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 全局周期设置与账户无关，空态下也允许预置（后续新建账户即生效）
                    GlobalCycleBar(
                        cycleLabel = uiState.globalReconcileCycle.label,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCycleSheet = true
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    EmptyStateView(
                        title = "暂无资金账户",
                        description = "创建微信、支付宝、储蓄卡等账户后，即可按周期核对实际余额",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val accounts = uiState.accounts
                // 分组口径：待核对 = OVERDUE/NEVER（isPending），其余 = FRESH/NORMAL（近期已核对）
                val pending = accounts.filter { it.reconcileStatus.isPending() }
                val calm = accounts.filterNot { it.reconcileStatus.isPending() }
                // 待核对排序：从未对账优先 → 逾期更久（上次核对更早）在前 → 稳定序兜底
                val pendingSorted = pending.sortedWith(
                    compareBy<AccountUiModel> {
                        if (it.reconcileStatus.tone == ReconcileTone.NEVER) 0 else 1
                    }.thenBy { it.lastReconciledAt ?: 0L }
                        .thenBy { it.sortOrder }
                )
                // 已核对组按账户原有顺序（与账户主页清单一致，便于对照）
                val calmSorted = calm.sortedBy { it.sortOrder }
                val pendingBookTotalCents = pendingSorted.sumOf { it.balanceCents }

                // 待核对全部清空时默认展开「近期已核对」清单；有待核对时默认收起
                var calmExpanded by remember(pending.isEmpty()) {
                    mutableStateOf(pending.isEmpty())
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- 轻量周期行（设置入口收敛为一行，非核心动作不再占卡）----
                    GlobalCycleBar(
                        cycleLabel = uiState.globalReconcileCycle.label,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCycleSheet = true
                        }
                    )

                    if (pendingSorted.isNotEmpty()) {
                        // ---- 聚合横幅：本期要核什么、涉及多少账面，一眼可见 ----
                        ReconcileTodoBand(
                            pendingCount = pendingSorted.size,
                            bookTotalCents = pendingBookTotalCents,
                            isPrivacyMode = uiState.isPrivacyMode,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ---- 待核对清单：整卡点击即开始该账户对账 ----
                        pendingSorted.forEach { account ->
                            ReconcileAccountCard(
                                account = account,
                                isPrivacyMode = uiState.isPrivacyMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    accountToReconcile = account
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ---- 其余近期已核对：弱化收纳（默认收起，展开降饱和）----
                        if (calmSorted.isNotEmpty()) {
                            CalmSectionHeader(
                                count = calmSorted.size,
                                expanded = calmExpanded,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    calmExpanded = !calmExpanded
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // ---- 无待核对：聚合横幅换主色「暂无到期」态 ----
                        AllCaughtUpBand(
                            accountCount = accounts.size,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (calmSorted.isNotEmpty()) {
                            CalmSectionHeader(
                                count = calmSorted.size,
                                expanded = calmExpanded,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    calmExpanded = !calmExpanded
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ---- 已核对清单（弱化呈现；整卡仍可点击随时提前核对）----
                    if (calmExpanded && calmSorted.isNotEmpty()) {
                        calmSorted.forEach { account ->
                            ReconcileAccountCard(
                                account = account,
                                isPrivacyMode = uiState.isPrivacyMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    accountToReconcile = account
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 周期 sheet：全局默认（账户自定义覆盖在各自账户详情页设置）----
    if (showCycleSheet) {
        ReconcileCycleSheet(
            title = "对账周期（全局默认）",
            current = uiState.globalReconcileCycle,
            globalCycle = uiState.globalReconcileCycle,
            allowFollowGlobal = false,
            onSelect = { cycle ->
                cycle?.let { viewModel.setGlobalReconcileCycle(it) }
            },
            onDismiss = { showCycleSheet = false }
        )
    }

    // ---- 对账对话框（整行点击直达）----
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
}

// ---------------------------------------------------------------------------
// 周期设置行 / 聚合横幅 / 分组头 / 账户行
// ---------------------------------------------------------------------------

/**
 * 全局对账周期设置行（轻量单行）：「对账周期」+ 当前档位 + ›。
 * 说明文字已裁掉：默认档位语义在横幅/展开清单里自明，覆盖规则入口在账户详情页。
 */
@Composable
private fun GlobalCycleBar(
    cycleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "对账周期",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = cycleLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "设置对账周期",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 待核对聚合横幅：琥珀大字「X 个账户待核对」+ 账面合计与唯一一句操作引导。
 * 整页的引导只保留这一句：点卡片输入真实余额核对（差额处理在对账框内自明）。
 */
@Composable
private fun ReconcileTodoBand(
    pendingCount: Int,
    bookTotalCents: Long,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val bookTotalText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        val absStr = MoneyUtils.centsToYuanString(
            if (bookTotalCents < 0L) -bookTotalCents else bookTotalCents,
            withGrouping = true
        )
        if (bookTotalCents < 0L) "-¥$absStr" else "¥$absStr"
    }

    val headline = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = ReconcileAmberColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        ) {
            append("$pendingCount")
        }
        append("  ")
        withStyle(
            SpanStyle(
                color = colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        ) {
            append("个账户待核对")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ReconcileAmberColor.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, ReconcileAmberColor.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = headline,
                maxLines = 1
            )
            Text(
                text = "账面合计 $bookTotalText · 点下方卡片核对，输入真实余额即可",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 全清聚合横幅：无到期账户时的收尾态（主色，弱化存在感但保留结论） */
@Composable
private fun AllCaughtUpBand(
    accountCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.primary.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "暂无到期账户",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "共 $accountCount 个账户均已在近期核对，到期后会回到「待核对」提醒",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 「近期已核对」分组头：点击展开/收起；行尾 › 随状态旋转 */
@Composable
private fun CalmSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "近期已核对",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count 个",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = colors.outline,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = if (expanded) "收起账户清单" else "展开账户清单",
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .size(15.dp)
                .rotate(if (expanded) 90f else 0f)
        )
    }
}

/**
 * 账户核对行卡：整卡点击 = 开始对账。图标 + 名称 + 「类型 · 对账状态」+
 * 右侧余额（隐私掩码，负值赤红）。仅「待核对」账户补一行上次核对摘要（帮助判断从哪期起核）；
 * 已核对账户整卡降饱和弱化。去掉了待核对角标、类型行装饰与「N 期记录」计数等非核心行。
 */
@Composable
private fun ReconcileAccountCard(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val reconcileStatus = account.reconcileStatus
    val isPending = reconcileStatus.isPending()
    val isNegative = account.balanceCents < 0L

    val balanceText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        val absStr = MoneyUtils.centsToYuanString(
            if (isNegative) -account.balanceCents else account.balanceCents,
            withGrouping = true
        )
        if (isNegative) "-¥$absStr" else "¥$absStr"
    }

    // 待核对账户补一行：上次核对时间与差额（从未对账/已核对账户无此行）
    val lastLine = if (isPending) {
        account.lastReconciledAt?.let { at ->
            val diffCents = account.lastReconciledDiffCents
            val diffPart = when {
                diffCents == null -> ""
                diffCents == 0L -> " · 无差异"
                isPrivacyMode -> " · 差额 ¥ ••••"
                diffCents > 0L -> " · 实际多出 ¥" + MoneyUtils.centsToYuanString(diffCents, withGrouping = true)
                else -> " · 实际缺少 ¥" + MoneyUtils.centsToYuanString(-diffCents, withGrouping = true)
            }
            "上次核对 " + DateTimeUtils.formatDate(at) + diffPart
        }
    } else {
        null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) colors.surface else colors.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = if (isPending) 0.35f else 0.2f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：账户类别图标
            CategoryIconView(
                iconName = account.iconName,
                colorHex = account.colorHex,
                size = 40.dp,
                iconSize = 20.dp
            )
            Spacer(modifier = Modifier.width(12.dp))

            // 中：名称 + 类型·对账状态（tone → 语义色）+ 上次核对摘要（仅待核对）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isPending) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                val statusText = buildAnnotatedString {
                    if (account.label.isNotBlank()) {
                        withStyle(SpanStyle(color = colors.outline)) { append(account.label) }
                        append(" · ")
                    }
                    withStyle(SpanStyle(color = reconcileToneColor(reconcileStatus.tone, colors))) {
                        append(reconcileStatus.text)
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (reconcileStatus.tone == ReconcileTone.OVERDUE) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (lastLine != null) {
                    Text(
                        text = lastLine,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 右：余额（整卡即对账入口，无需按钮/计数）
            Text(
                text = balanceText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (!isPrivacyMode && isNegative) colors.error else colors.onSurface,
                maxLines = 1
            )
        }
    }
}
