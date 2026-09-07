@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
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

/** 待核对警示琥珀 —— 与首页预算条同值 Color(0xFFFF9800)（HomeScreen.kt / AccountHero.kt） */
private val ReconcileAmberColor = Color(0xFFFF9800)

/** 对账状态 tone → 状态文案颜色（判定文案一律取共享 accountReconcileStatus，此处只做颜色映射） */
private fun reconcileToneColor(tone: ReconcileTone, scheme: ColorScheme): Color = when (tone) {
    ReconcileTone.FRESH -> scheme.primary
    ReconcileTone.NORMAL -> scheme.onSurfaceVariant
    ReconcileTone.OVERDUE -> ReconcileAmberColor
    ReconcileTone.NEVER -> scheme.outline
}

/**
 * 账户核对 二级页
 *
 * 逐账户展示：名称 / 类型 / 余额（隐私掩码，负值赤红）/ 最近核对时间与差额 /
 * 待核对状态标识（复用共享判定 accountReconcileStatus 与既有 AccountReconcileDialog）。
 *  - 顶部规范头部（返回 + 「账户核对」）
 *  - 引导文案卡：按月与实际余额对账，差额一键自动校正期初基线
 *  - 行尾「对账」按钮 → 复用 AccountReconcileDialog → viewModel.reconcileAccount → toast
 *  - 无账户 → EmptyStateView 引导；本页不新建 viewModel
 *
 * 状态栏 inset 由本页 Scaffold（contentWindowInsets = statusBars）统一处理，不自行画 inset。
 */
@Composable
fun AccountReconcileScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current

    // ---- 弹层状态：正在对账的账户 ----
    var accountToReconcile by remember { mutableStateOf<AccountUiModel?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            // 规范头部（与 CategoryManageScreen / AddEditRecordScreen 同式）：左上返回钮 + 靠左标题
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
                // ---- 无账户空态 ----
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyStateView(
                        title = "暂无资金账户",
                        description = "创建微信、支付宝、储蓄卡等账户后，即可按月核对实际余额",
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
                        .padding(top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ---- 引导文案行 ----
                    ReconcileGuideRow(modifier = Modifier.fillMaxWidth())

                    // ---- 账户清单：逐行展示 + 「对账」入口 ----
                    uiState.accounts.forEach { account ->
                        ReconcileAccountRowCard(
                            account = account,
                            isPrivacyMode = uiState.isPrivacyMode,
                            onReconcile = { accountToReconcile = account },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // ---- 月度资金对账对话框（复用既有 AccountReconcileDialog）----
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
// 引导行 / 账户行
// ---------------------------------------------------------------------------

/** 页面顶部引导：按月与实际余额对账，差额一键校正期初基线 */
@Composable
private fun ReconcileGuideRow(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "按月与实际余额核对一次；差额可一键自动校正期初基线，不产生假流水",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 账户核对行卡：图标 + 名称（待核对徽标）+ 类型·对账状态 + 上次核对时间与差额 |
 * 右侧余额（隐私掩码，负值赤红）+「对账」按钮。
 */
@Composable
private fun ReconcileAccountRowCard(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val reconcileStatus = accountReconcileStatus(account.lastReconciledAt)
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

    // 上次核对时间与差额（从未对账时无此行）
    val lastLine = account.lastReconciledAt?.let { at ->
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
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
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

            // 中：名称 / 类型·状态 / 上次核对
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 行1：名称 + 待核对徽标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPending) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ReconcileAmberColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "待核对",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = ReconcileAmberColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                // 行2：类型 · 对账状态（tone → 语义色；与 AccountItemCard 同款分段着色）
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
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // 行3：上次核对时间与差额（无记录时不渲染）
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

            // 右：余额 + 「对账」入口
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (!isPrivacyMode && isNegative) colors.error else colors.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReconcile()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primary.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, colors.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.height(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "对账",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}
