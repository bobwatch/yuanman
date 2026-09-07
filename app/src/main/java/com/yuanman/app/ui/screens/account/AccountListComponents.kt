package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.utils.MoneyUtils

/**
 * 账户清单纯展示组件（设计文档 §4.2.2 – 4.2.4 / redesign v0.2 §6.5/§6.8）：
 * AccountSectionHeader（资金账户区块头 + 「＋ 新建账户」）/ AccountGroupHeader（组头）/
 * AccountItemCard（账户行）。全部交互经回调上抛，本文件不引用 VM / 弹层 / 导航；
 * 只读 label 是否为空，不做任何类型内容判断。区块标题一律不带数量（§6.5），合计金额保留。
 */

/** 逾期警示琥珀，与首页预算条同值（设计文档 §5.1） */
private val ReconcileOverdueColor = Color(0xFFFF9800)

/** 对账状态 tone → 副行颜色（文案由共享的 AccountReconcileStatus 提供，此处只做颜色映射） */
private fun toneColor(tone: ReconcileTone, scheme: ColorScheme): Color = when (tone) {
    ReconcileTone.FRESH, ReconcileTone.NORMAL -> scheme.onSurfaceVariant
    ReconcileTone.OVERDUE -> ReconcileOverdueColor
    ReconcileTone.NEVER -> scheme.outline
}

/**
 * 类型分组组头（§4.2.2，redesign v0.2 §6.5：标题不带账户数量，仅保留组合计金额）
 *
 * @param accentColorHex 组内首个账户 colorHex；null 时竖条回退 outline 色
 */
@Composable
fun AccountGroupHeader(
    groupLabel: String,
    groupTotalCents: Long,
    isPrivacyMode: Boolean,
    accentColorHex: Long?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：4x12 圆角竖条 + 组名
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColorHex?.let { Color(it) } ?: scheme.outline)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // 组名：空 label 显示「未分组」并用 outline 弱化
            if (groupLabel.isBlank()) {
                Text(
                    text = "未分组",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = groupLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 右：组净值合计（隐私态仅掩码，负值赤红）
        val isNegative = groupTotalCents < 0L
        val totalText = if (isPrivacyMode) {
            "合计 ¥ ••••"
        } else {
            val absStr = MoneyUtils.centsToYuanString(
                if (isNegative) -groupTotalCents else groupTotalCents,
                withGrouping = true
            )
            "合计 ${if (isNegative) "-¥$absStr" else "¥$absStr"}"
        }
        Text(
            text = totalText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = when {
                isPrivacyMode -> scheme.onSurfaceVariant
                isNegative -> scheme.error
                else -> scheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 账户行卡（§4.2.3，瘦身版两行结构：名称+副行 | 余额+负余额还款钮）
 * 整卡点击 → 操作面板；负余额行内仅 [还款] 一个第二可点元素，长按/更多一律不做。
 */
@Composable
fun AccountItemCard(
    account: AccountUiModel,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    onQuickRepay: (AccountUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isNegative = account.balanceCents < 0L

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：账户类别图标
            CategoryIconView(
                iconName = account.iconName,
                colorHex = account.colorHex,
                size = 40.dp,
                iconSize = 20.dp
            )
            Spacer(modifier = Modifier.width(10.dp))

            // 中：名称 + 类型·状态副行
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 副行：label 非空先显示 label（outline），有状态再拼接「 · 」与状态（tone 映射色）
                val reconcile = accountReconcileStatus(account.lastReconciledAt)
                val subtitle = buildAnnotatedString {
                    if (account.label.isNotBlank()) {
                        withStyle(SpanStyle(color = scheme.outline)) { append(account.label) }
                        if (reconcile.text.isNotBlank()) {
                            append(" · ")
                        }
                    }
                    if (reconcile.text.isNotBlank()) {
                        withStyle(SpanStyle(color = toneColor(reconcile.tone, scheme))) {
                            append(reconcile.text)
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右：余额（负值赤红）+ 负余额账户的 [还款] 小钮
            Column(horizontalAlignment = Alignment.End) {
                val balanceText = if (isPrivacyMode) {
                    "¥ ••••"
                } else {
                    val absStr = MoneyUtils.centsToYuanString(
                        if (isNegative) -account.balanceCents else account.balanceCents,
                        withGrouping = true
                    )
                    if (isNegative) "-¥$absStr" else "¥$absStr"
                }
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (!isPrivacyMode && isNegative) scheme.error else scheme.onSurface,
                    maxLines = 1
                )

                if (isNegative) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        onClick = { onQuickRepay(account) },
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.secondary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, scheme.secondary.copy(alpha = 0.35f)),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = scheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "还款",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = scheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 资金账户区块头（redesign v0.2 §6.8：新建账户入口自列表底部上移）。
 *
 * 对齐 PlansSectionHeader 的风格与内边距：主色圆点 + 区块标题（不带数量，§6.5）
 * + 右侧紧凑「＋ 新建账户」文字钮（高 32dp，命中区仅按钮自身、不横向铺满整行）。
 * 标题与各分组组头并列时承担「资金账户」区块的统辖语义。
 */
@Composable
fun AccountSectionHeader(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
            Text(
                text = "资金账户",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 紧凑「＋ 新建账户」钮：Surface 自带 ripple，32dp 高命中区。
        // 内容行不能用 fillMaxWidth：Row 里非 weight 子项会抢占剩余宽度，
        // 把整颗钮拉成标题右侧一长条（与 PlansSectionHeader「管理」同源问题，已统一修复）
        Surface(
            onClick = onAddClick,
            shape = RoundedCornerShape(8.dp),
            color = scheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.26f))
        ) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "新建账户",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}
