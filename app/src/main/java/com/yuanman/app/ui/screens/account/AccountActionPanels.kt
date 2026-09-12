package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
 * 账户操作面板：长按账户行后升起，包含账户卡片摘要、高频操作卡片、默认收支账户设定及删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountActionSheet(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    isDefaultExpense: Boolean,
    isDefaultIncome: Boolean,
    onDismiss: () -> Unit,
    onTransfer: (AccountUiModel) -> Unit,
    onReconcile: (AccountUiModel) -> Unit,
    onEdit: (AccountUiModel) -> Unit,
    onDelete: (AccountUiModel) -> Unit,
    onToggleDefaultExpense: (Boolean) -> Unit,
    onToggleDefaultIncome: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val runAction: ((AccountUiModel) -> Unit) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDismiss()
        action(account)
    }

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
    val reconcileStatus = account.reconcileStatus

    val infoStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- 1. 账户信息卡片（大图标、名称、标签、余额、月度收支与对账时效）----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryIconView(
                            iconName = account.iconName,
                            colorHex = account.colorHex,
                            size = 46.dp,
                            iconSize = 24.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isDefaultExpense) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = scheme.primary.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, scheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "默认支出",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = scheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isDefaultIncome) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF059669).copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, Color(0xFF059669).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "默认收入",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF059669),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (account.label.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = account.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                    color = scheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = balanceText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = balanceColor,
                                maxLines = 1
                            )
                            Text(
                                text = "账户余额",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = scheme.outline.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

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
                        Spacer(modifier = Modifier.weight(1f))
                        when (reconcileStatus.tone) {
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
                            ReconcileTone.NORMAL -> {
                                Text(
                                    text = reconcileStatus.text,
                                    style = infoStyle,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---- 2. 高频操作按钮行（资金对账 / 转账还款 / 编辑账户）----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccountActionTile(
                    title = "资金对账",
                    subtitle = "校准实盘",
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = scheme.primary,
                    containerColor = scheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f),
                    onClick = { runAction(onReconcile) }
                )
                AccountActionTile(
                    title = "转账还款",
                    subtitle = "资金流转",
                    icon = Icons.Default.SwapHoriz,
                    iconTint = scheme.secondary,
                    containerColor = scheme.secondaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f),
                    onClick = { runAction(onTransfer) }
                )
                AccountActionTile(
                    title = "编辑账户",
                    subtitle = "名称图标",
                    icon = Icons.Default.Edit,
                    iconTint = scheme.onSurfaceVariant,
                    containerColor = scheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // 编辑走「子表单叠在面板之上」：不先关闭父面板，
                        // 取消编辑可回到面板继续其它操作（保存成功后才由页面层一并收起）
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEdit(account)
                    }
                )
            }

            // ---- 3. 默认收支账户配置卡片 ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    AccountDefaultToggleRow(
                        title = "设为默认支出账户",
                        subtitle = "记账与闪电记账优先以此账户扣款",
                        icon = Icons.Default.Star,
                        iconTint = scheme.primary,
                        checked = isDefaultExpense,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleDefaultExpense(checked)
                        }
                    )
                    HorizontalDivider(
                        color = scheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                    AccountDefaultToggleRow(
                        title = "设为默认收入账户",
                        subtitle = "记录收入流水时优先归入此账户",
                        icon = Icons.Default.Star,
                        iconTint = Color(0xFF059669),
                        checked = isDefaultIncome,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleDefaultIncome(checked)
                        }
                    )
                }
            }

            // ---- 4. 危险区：删除账户 ----
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.errorContainer.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { runAction(onDelete) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = scheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "删除账户",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = scheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** 账户操作卡片（资金对账 / 转账 / 编辑） */
@Composable
private fun AccountActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

/** 默认账户开关行 */
@Composable
private fun AccountDefaultToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconTint
            )
        )
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

                val reconcileStatus = account.reconcileStatus
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
