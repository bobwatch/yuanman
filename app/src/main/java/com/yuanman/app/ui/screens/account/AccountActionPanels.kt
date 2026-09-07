package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils

/**
 * 账户操作面板（设计文档 §4.3）+ 待核对账户底包（§4.5）。
 * 对账状态文案一律取共享 accountReconcileStatus(...)，此处只做颜色映射，不重复实现判定。
 */

// 待核对警示琥珀 —— 与首页预算条同值 Color(0xFFFF9800)（HomeScreen.kt）
private val ReconcileAmber = Color(0xFFFF9800)

/**
 * 账户操作面板：点击账户行后升起，含头部信息（图标 / 名称 / 类型 / 余额 / 本月出入 / 对账状态）
 * 与四项操作（转账还款 / 资金对账 / 编辑 / 删除，删除以分隔线隔开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountActionSheet(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    onDismiss: () -> Unit,
    onTransfer: (AccountUiModel) -> Unit,
    onReconcile: (AccountUiModel) -> Unit,
    onEdit: (AccountUiModel) -> Unit,
    onDelete: (AccountUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    // 统一动作序列：触觉反馈 -> 关闭底包 -> 交由页面层打开对应弹层
    val runAction: ((AccountUiModel) -> Unit) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDismiss()
        action(account)
    }

    // 头部余额：隐私遮蔽 / 负值赤红（error）/ 正值常规色，千分位
    val isNegative = account.balanceCents < 0L
    val balanceText = when {
        isPrivacyMode -> "¥ ••••"
        isNegative -> "-¥" + MoneyUtils.centsToYuanString(-account.balanceCents, withGrouping = true)
        else -> "¥" + MoneyUtils.centsToYuanString(account.balanceCents, withGrouping = true)
    }
    val balanceColor = when {
        isPrivacyMode || !isNegative -> scheme.onSurface
        else -> scheme.error
    }

    val inYuan = MoneyUtils.centsToYuanString(account.inCents)
    val outYuan = MoneyUtils.centsToYuanString(account.outCents)
    val reconcileStatus = accountReconcileStatus(account.lastReconciledAt)

    val infoStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- 头部行1：图标 + 名称/类型 + 余额 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconView(
                    iconName = account.iconName,
                    colorHex = account.colorHex,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 类型名可空：为空时不渲染该行
                    if (account.label.isNotBlank()) {
                        Text(
                            text = account.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = scheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = balanceColor,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 头部行2：本月入/出 + 对账时效 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "本月入 ¥$inYuan",
                    style = infoStyle,
                    color = scheme.outline
                )
                Text(text = "  ·  ", style = infoStyle, color = scheme.outline)
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "本月出 ¥$outYuan",
                    style = infoStyle,
                    color = scheme.outline
                )
                Text(text = "  ·  ", style = infoStyle, color = scheme.outline)
                when (reconcileStatus.tone) {
                    // 已核对：对勾主色
                    ReconcileTone.FRESH -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = reconcileStatus.text,
                            style = infoStyle,
                            color = scheme.primary
                        )
                    }
                    // 逾期 / 从未核对：琥珀警示
                    ReconcileTone.OVERDUE, ReconcileTone.NEVER -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ReconcileAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = reconcileStatus.text,
                            style = infoStyle,
                            color = ReconcileAmber
                        )
                    }
                    // 上月已核对：灰字无图标
                    ReconcileTone.NORMAL -> {
                        Text(
                            text = reconcileStatus.text,
                            style = infoStyle,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- 操作区 ----
            AccountActionRow(
                title = "转账 / 还款",
                icon = Icons.Default.SwapHoriz,
                iconTint = scheme.secondary,
                onClick = { runAction(onTransfer) }
            )
            AccountActionRow(
                title = "资金对账",
                icon = Icons.Default.AccountBalanceWallet,
                iconTint = scheme.primary,
                onClick = { runAction(onReconcile) }
            )
            AccountActionRow(
                title = "编辑账户",
                icon = Icons.Default.Edit,
                iconTint = scheme.onSurface,
                onClick = { runAction(onEdit) }
            )

            // 危险操作与常规操作分区
            HorizontalDivider(
                color = scheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            AccountActionRow(
                title = "删除账户",
                icon = Icons.Default.Delete,
                iconTint = scheme.error,
                titleColor = scheme.error,
                onClick = { runAction(onDelete) }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 面板操作行：24dp 前置图标 + 13.5sp Medium 标题，整行可点 */
@Composable
private fun AccountActionRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 待核对账户底包（设计文档 §4.5）：对账提醒横幅点击后升起，
 * 逐行列出逾期 / 从未对账的账户，行尾「去对账」直达该账户对账框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingReconcileSheet(
    pendingAccounts: List<AccountUiModel>,
    onDismiss: () -> Unit,
    onReconcile: (AccountUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- 标题区 ----
            Text(
                text = "资金对账",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = scheme.onSurface
            )
            Text(
                text = "选择账户核对实际余额，差额将并入期初基线",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = scheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 账户列表（逾期 = 琥珀；从未 = 灰 outline）----
            pendingAccounts.forEachIndexed { index, account ->
                if (index > 0) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                }

                val reconcileStatus = accountReconcileStatus(account.lastReconciledAt)
                val statusColor = if (reconcileStatus.tone == ReconcileTone.OVERDUE) {
                    ReconcileAmber
                } else {
                    scheme.outline
                }

                // 整行与「去对账」共享同一动作：反馈 -> 关底包 -> 打开该账户对账框
                val goReconcile = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                    onReconcile(account)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = goReconcile)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryIconView(
                        iconName = account.iconName,
                        colorHex = account.colorHex,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = reconcileStatus.text,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    // 去对账入口（surface 底即可，整行点击保证大命中区）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = goReconcile)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "去对账",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = scheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
