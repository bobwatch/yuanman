@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import java.math.BigDecimal

/**
 * 攒钱计划 & 发薪分配 —— 计划快捷操作面板（长按）/ 计划表单（新建/编辑）/ 存一笔 / 取一笔 / 发薪方案编辑
 * （设计文档 saving-plans-and-paycheck-v0.3.md §2.4 / §2.5；
 *  动作名 v0.0.4+ 简化：「再存一笔 → 存一笔」「取出一笔（原撤回专款）→ 取一笔」；
 *  长按面板 = 账户 Tab 计划小卡长按升起，与账户行「点击详情 / 长按操作」同构）
 *
 * - 金额一律走 MoneyUtils 千分位；隐私掩码只针对「实时资金数字」（已圈 / 可用上限），
 *   表单输入框内为用户输入值、规则中的固定额 / 百分比为用户配置值，均不掩码；
 * - 计划表单 / 存、取 / 方案编辑均为「即改即存」风格，保存与持久化由页面层调用方完成。
 */

/** 超额警示琥珀 —— 与账户页警示同值 Color(0xFFFF9800) */
private val PlanWarnAmber = Color(0xFFFF9800)

/** 12 色盘 —— 与 AddEditAccountSheet（AccountForms.kt）取值一致 */
private val PlanPresetColors = listOf(
    0xFF059669L, // 翡翠翠绿
    0xFF0284C7L, // 晴空蔚蓝
    0xFFE53935L, // 鲜明赤红
    0xFFFF9800L, // 活力暖橙
    0xFF9C27B0L, // 优雅紫
    0xFFE91E63L, // 珊瑚粉
    0xFF009688L, // 墨玉青
    0xFF3F51B5L, // 靛青蓝
    0xFF795548L, // 暖棕
    0xFF607D8BL, // 极简灰
    0xFFFFB300L, // 晨曦金
    0xFF26A69AL  // 薄荷绿
)

// ---------------------------------------------------------------------------
// 通用小件
// ---------------------------------------------------------------------------

/** 计划色圆点（头部 / 列表行通用） */
@Composable
private fun PlanColorDot(colorHex: Long, size: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorHex))
    )
}

/** 面板 / 表单通用的「色点 + 名称」头部行，达标时行尾补主色对勾 */
@Composable
private fun PlanSheetHeaderRow(
    plan: SavingPlanUiModel,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlanColorDot(colorHex = plan.colorHex)
        Spacer(modifier = Modifier.width(10.dp))
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
        if (isDone) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已达标",
                tint = scheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 12 色盘色圈：28dp 圆角 r10，选中以 primary 描边 + 白色对勾 */
@Composable
private fun PlanColorPaletteRow(
    selectedColorHex: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlanPresetColors.forEach { colorVal ->
            val isSelected = selectedColorHex == colorVal
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(colorVal))
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(colorVal) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * 账户 FilterChip（专款账户单选用）：只展示 名称；选中态仅前置对勾（不带彩色圆点）。
 * 选专款账户不展示余额 —— 金额属于「要圈的钱」，避免用户误把余额当上限而困惑。
 */
@Composable
private fun AccountSelectChip(
    account: AccountUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        },
        label = {
            Text(
                text = account.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

// ---------------------------------------------------------------------------
// 计划快捷操作面板（账户 Tab 计划小卡长按升起）
// ---------------------------------------------------------------------------

/**
 * 计划快捷操作面板（账户 Tab 计划小卡长按升起）：
 *  顶部 = 宽版计划预览卡（彩点 + 名称 + 已圈/目标 + 进度条；达标 → 计划色描边 ✓，超额 → 琥珀警示），
 *         整卡点击 → 计划详情页（编辑/删除/存取记录在详情页同样可用）；
 *  中部 = 存一笔（↓）/ 取一笔（↑）主按钮，带状态禁用与原因提示；
 *  底部 = 编辑计划 / 删除计划（error 分区）入口。
 * 动作统一「haptic → 关闭面板 → 目标回调」（与 AccountActionPanels.kt runAction 同款时序）。
 */
@Composable
fun PlanQuickActionSheet(
    plan: SavingPlanUiModel,
    holderBalanceCents: Long?,
    depositMaxCents: Long,
    isDone: Boolean,
    isOverdrawn: Boolean,
    isPrivacyMode: Boolean,
    onDismiss: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val runAction: (() -> Unit) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDismiss()
        action()
    }

    // 状态感知：无专款账户 / 无可圈余额 → 存禁用；已圈 = 0 → 取禁用
    val canDeposit = holderBalanceCents != null && depositMaxCents > 0L
    val canWithdraw = plan.earmarkedCents > 0L
    val depositDisabledReason = when {
        canDeposit -> null
        holderBalanceCents == null -> "尚未设置专款账户，可在「详情」中修改"
        else -> "专款账户暂无可圈余额（已被圈满或超额）"
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- 宽版计划预览卡：点卡片 = 进详情 ----
            PlanSheetPreviewCard(
                plan = plan,
                holderBalanceCents = holderBalanceCents,
                isDone = isDone,
                isOverdrawn = isOverdrawn,
                isPrivacyMode = isPrivacyMode,
                onClick = { runAction(onOpenDetail) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ---- 主操作：存一笔 / 取一笔（↓ 存入、↑ 取出，语义化箭头）----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { runAction(onDeposit) },
                    enabled = canDeposit,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("存一笔", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
                OutlinedButton(
                    onClick = { runAction(onWithdraw) },
                    enabled = canWithdraw,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("取一笔", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
            // 禁用原因（取 = 0 时语义自明，仅存被禁用时给一行提示）
            if (depositDisabledReason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = depositDisabledReason,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.outline,
                    maxLines = 1
                )
            }

            HorizontalDivider(
                color = scheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ---- 管理入口：编辑 / 删除（与详情页顶栏同款动作，长按场景直达）----
            PlanQuickActionRow(
                title = "编辑计划",
                icon = Icons.Default.Edit,
                iconTint = scheme.onSurface,
                onClick = { runAction(onEdit) }
            )
            PlanQuickActionRow(
                title = "删除计划",
                icon = Icons.Default.Delete,
                iconTint = scheme.error,
                titleColor = scheme.error,
                onClick = { runAction(onDelete) }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 面板宽版计划预览卡：与计划小卡同语言（计划色/进度/达标✓/超额琥珀），点击进入详情 */
@Composable
private fun PlanSheetPreviewCard(
    plan: SavingPlanUiModel,
    holderBalanceCents: Long?,
    isDone: Boolean,
    isOverdrawn: Boolean,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val planColor = Color(plan.colorHex)
    val hasTarget = plan.targetAmountCents > 0L
    val shortfallCents = (plan.earmarkedCents - (holderBalanceCents ?: 0L)).coerceAtLeast(0L)
    val fraction = if (hasTarget) {
        (plan.earmarkedCents.toFloat() / plan.targetAmountCents.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(
            1.dp,
            when {
                isOverdrawn -> PlanWarnAmber.copy(alpha = 0.5f)
                isDone -> planColor.copy(alpha = 0.5f)
                else -> scheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(planColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isDone && !isOverdrawn) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已达标",
                        tint = planColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isPrivacyMode) "¥ ••••"
                    else "¥" + MoneyUtils.centsToCompactYuan(plan.earmarkedCents),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = scheme.onSurface,
                    maxLines = 1
                )
                if (isOverdrawn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPrivacyMode) "低于专款 ¥ ••••"
                        else "低于专款 ¥" + MoneyUtils.centsToCompactYuan(shortfallCents),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = PlanWarnAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else if (hasTarget) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPrivacyMode) "目标 ¥ ••••"
                        else "目标 ¥" + MoneyUtils.centsToCompactYuan(plan.targetAmountCents),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = scheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (hasTarget) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scheme.surfaceVariant)
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(planColor)
                        )
                    }
                }
            }
        }
    }
}

/** 面板操作行：24dp 前置图标 + 13.5sp Medium 标题（AccountActionPanels.kt 同款排版） */
@Composable
private fun PlanQuickActionRow(
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

// ---------------------------------------------------------------------------
// §2.4 新建 / 编辑计划表单
// ---------------------------------------------------------------------------

/**
 * 计划新建 / 编辑表单（设计文档 §2.4 编辑 sheet）：
 * 名称 / 目标金额（0 = 不设上限）/ 专款账户单选 chips / 12 色盘。
 * 保存禁用条件：名称 trim 为空或未选中账户。
 */
@Composable
fun PlanFormSheet(
    planToEdit: SavingPlanUiModel?,
    accounts: List<AccountUiModel>,
    onDismiss: () -> Unit,
    onSave: (name: String, targetAmountCents: Long, holderAccountId: Long, colorHex: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialTargetAmountCents: Long = 0L,
    initialColorHex: Long? = null
) {
    val scheme = MaterialTheme.colorScheme

    var name by remember(planToEdit, initialName) {
        mutableStateOf(planToEdit?.name ?: initialName)
    }
    var targetYuan by remember(planToEdit, initialTargetAmountCents) {
        mutableStateOf(
            if (planToEdit != null) {
                val bd = BigDecimal(planToEdit.targetAmountCents).divide(BigDecimal(100))
                bd.stripTrailingZeros().toPlainString()
            } else if (initialTargetAmountCents > 0L) {
                val bd = BigDecimal(initialTargetAmountCents).divide(BigDecimal(100))
                bd.stripTrailingZeros().toPlainString()
            } else "0"
        )
    }
    var selectedAccountId by remember(planToEdit) {
        mutableStateOf(
            if (planToEdit != null && accounts.any { it.id == planToEdit.holderAccountId }) {
                planToEdit.holderAccountId
            } else {
                accounts.firstOrNull()?.id ?: 0L
            }
        )
    }
    var colorHex by remember(planToEdit, initialColorHex) {
        mutableStateOf(planToEdit?.colorHex ?: (initialColorHex ?: 0xFF059669L))
    }

    val isEditing = planToEdit != null
    val nameOk = name.trim().isNotEmpty()
    val accountOk = selectedAccountId > 0L && accounts.any { it.id == selectedAccountId }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isEditing) "编辑计划" else "新建计划",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // 计划名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("计划名称 (如: 旅行基金、换手机)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 目标金额（0 = 不设上限）
            OutlinedTextField(
                value = targetYuan,
                onValueChange = { targetYuan = it },
                label = { Text("目标金额 (元，填 0 表示不设上限)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 专款账户单选
            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "专款账户",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            AccountSelectChip(
                                account = acc,
                                selected = acc.id == selectedAccountId,
                                onClick = { selectedAccountId = acc.id }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "请先创建账户，再新建攒钱计划",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = PlanWarnAmber
                )
            }

            // 12 色盘
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "主题色彩",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline
                )
                PlanColorPaletteRow(selectedColorHex = colorHex, onSelect = { colorHex = it })
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (nameOk && accountOk) {
                        val cents = try {
                            val bd = BigDecimal(targetYuan.trim())
                            bd.multiply(BigDecimal(100)).toLong()
                        } catch (e: Exception) {
                            0L
                        }
                        onSave(name.trim(), cents.coerceAtLeast(0L), selectedAccountId, colorHex)
                    }
                },
                enabled = nameOk && accountOk,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (isEditing) "保存修改" else "保存", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// §2.4 存一笔（金额输入 + 实时可用上限）
// ---------------------------------------------------------------------------

/**
 * 存一笔：金额输入 + 实时可用上限展示。
 * 上限由调用方以 availableToEarmarkFor 计算传入；超上限按钮禁用并红字提示。
 */
@Composable
fun PlanDepositSheet(
    plan: SavingPlanUiModel,
    maxCents: Long,
    isPrivacyMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var amountYuan by remember { mutableStateOf("") }
    val amountCents = MoneyUtils.parseYuanToCents(amountYuan)
    val overLimit = amountCents > maxCents

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlanSheetHeaderRow(plan = plan, isDone = false)
            Text(
                text = "已圈 " +
                    (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = scheme.outline
            )

            OutlinedTextField(
                value = amountYuan,
                onValueChange = { amountYuan = it },
                label = { Text("存入金额 (元)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (overLimit) {
                    "超过可存上限"
                } else {
                    "还可存 " +
                        (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(maxCents, withGrouping = true))
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (overLimit) scheme.error else scheme.onSurfaceVariant
            )

            Button(
                onClick = { onConfirm(amountCents) },
                enabled = amountCents > 0L && !overLimit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("存一笔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// §2.4 取一笔（上限 = 该计划已圈，可整额取出；原「撤回专款 / 取出一笔」简化命名）
// ---------------------------------------------------------------------------

/**
 * 取一笔：金额输入，上限 = 该计划已圈额；「全部取出」一键填整额。
 */
@Composable
fun PlanWithdrawSheet(
    plan: SavingPlanUiModel,
    isPrivacyMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var amountYuan by remember { mutableStateOf("") }
    val amountCents = MoneyUtils.parseYuanToCents(amountYuan)
    val overLimit = amountCents > plan.earmarkedCents

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlanSheetHeaderRow(plan = plan, isDone = false)
            Text(
                text = "已圈 " +
                    (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = scheme.outline
            )

            OutlinedTextField(
                value = amountYuan,
                onValueChange = { amountYuan = it },
                label = { Text("取出金额 (元)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (overLimit) {
                    "超过可取出上限"
                } else {
                    "可取出 " +
                        (if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true))
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (overLimit) scheme.error else scheme.onSurfaceVariant
            )

            // 整额取出辅助入口
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        amountYuan = MoneyUtils.centsToYuanString(plan.earmarkedCents)
                    },
                    enabled = plan.earmarkedCents > 0L,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "全部取出",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = scheme.primary
                    )
                }
            }

            Button(
                onClick = { onConfirm(amountCents) },
                enabled = amountCents > 0L && !overLimit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("取一笔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// §2.5 发薪分配方案编辑（即改即存）
// ---------------------------------------------------------------------------

/**
 * 发薪方案编辑 sheet（设计文档 §2.5）：
 * 顶部「剩余自动清欠」开关；规则列表（转=SwapHoriz / 攒=账户余额图标，行尾删除即生效）；
 * 底部两行添加器（固定金额 | 百分比 + 账户 / 计划 chips 单选）+ 内联输入行；
 * 每次变更即时 onSchemeChange（调用方直接持久化，即改即存，无需确认按钮）。
 */
@Composable
fun SchemeEditorSheet(
    accounts: List<AccountUiModel>,
    plans: List<SavingPlanUiModel>,
    scheme: PaycheckSchemeUiModel,
    onDismiss: () -> Unit,
    onSchemeChange: (PaycheckSchemeUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val schemeColors = MaterialTheme.colorScheme

    var isFixedAmount by remember { mutableStateOf(true) }
    var targetAccountId by remember { mutableStateOf<Long?>(null) }
    var targetPlanId by remember { mutableStateOf<Long?>(null) }
    var inputText by remember { mutableStateOf("") }

    val accountsById = remember(accounts) { accounts.associateBy { it.id } }
    val plansById = remember(plans) { plans.associateBy { it.id } }

    val targetChosen = targetAccountId != null || targetPlanId != null
    val fixedCents = MoneyUtils.parseYuanToCents(inputText)
    val percentValue = inputText.trim().toIntOrNull() ?: 0
    val percentOk = percentValue in 1..100
    val canAdd = if (isFixedAmount) fixedCents > 0L else percentOk

    // 目标显示名：账户名 / 计划名（目标已删除时给出兜底文案）
    fun targetDisplayName(kind: PaycheckRuleKind, targetId: Long): String {
        val isAccount = kind == PaycheckRuleKind.TO_ACCOUNT_FIXED || kind == PaycheckRuleKind.TO_ACCOUNT_PCT
        return if (isAccount) {
            accountsById[targetId]?.name ?: "已删除账户"
        } else {
            plansById[targetId]?.name ?: "已删除计划"
        }
    }

    fun addRule() {
        val isAccountTarget = targetAccountId != null
        val targetId = (targetAccountId ?: targetPlanId) ?: return
        val kind = when {
            isAccountTarget && isFixedAmount -> PaycheckRuleKind.TO_ACCOUNT_FIXED
            isAccountTarget && !isFixedAmount -> PaycheckRuleKind.TO_ACCOUNT_PCT
            !isAccountTarget && isFixedAmount -> PaycheckRuleKind.TO_PLAN_FIXED
            else -> PaycheckRuleKind.TO_PLAN_PCT
        }
        val rule = PaycheckRuleUiModel(
            kind = kind,
            targetId = targetId,
            amountCents = if (isFixedAmount) fixedCents else 0L,
            percentBps = if (isFixedAmount) 0 else percentValue * 100
        )
        onSchemeChange(scheme.copy(rules = scheme.rules + rule))
        inputText = ""
        targetAccountId = null
        targetPlanId = null
    }

    YuanmanModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 标题 + 执行顺序说明 ----
            Text(
                text = "发薪分配方案",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = schemeColors.onSurface
            )
            Text(
                text = "按顺序执行：先规则、后自动清欠、再留存",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = schemeColors.outline
            )

            // ---- 剩余自动清欠开关（即改即存）----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "剩余自动清欠",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = schemeColors.onSurface
                    )
                    Text(
                        text = "规则后如有剩余，自动还清负余额账户",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = schemeColors.outline
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = scheme.autoClearDebts,
                    onCheckedChange = { onSchemeChange(scheme.copy(autoClearDebts = it)) }
                )
            }

            HorizontalDivider(
                color = schemeColors.outlineVariant.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // ---- 规则列表 ----
            if (scheme.rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有规则：添加后，发薪时按列表顺序执行",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = schemeColors.outline
                    )
                }
            } else {
                Column {
                    scheme.rules.forEachIndexed { index, rule ->
                        if (index > 0) {
                            HorizontalDivider(color = schemeColors.outlineVariant.copy(alpha = 0.25f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isToAccount =
                                rule.kind == PaycheckRuleKind.TO_ACCOUNT_FIXED ||
                                    rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT
                            Icon(
                                imageVector = if (isToAccount) Icons.Default.SwapHoriz else Icons.Default.Savings,
                                contentDescription = null,
                                tint = if (isToAccount) schemeColors.secondary else schemeColors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val isPct = rule.kind == PaycheckRuleKind.TO_ACCOUNT_PCT ||
                                rule.kind == PaycheckRuleKind.TO_PLAN_PCT
                            val amountText = if (isPct) {
                                "${rule.percentBps / 100}%"
                            } else {
                                "¥" + MoneyUtils.centsToYuanString(rule.amountCents, withGrouping = true)
                            }
                            val verb = if (isToAccount) "转" else "攒"
                            Text(
                                text = "$verb $amountText → ${targetDisplayName(rule.kind, rule.targetId)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = schemeColors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除规则",
                                tint = schemeColors.error,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        onSchemeChange(
                                            scheme.copy(rules = scheme.rules.filterIndexed { i, _ -> i != index })
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = schemeColors.outlineVariant.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // ---- 添加器：固定金额 | 百分比 ----
            Text(
                text = "添加规则",
                style = MaterialTheme.typography.labelSmall,
                color = schemeColors.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isFixedAmount,
                    onClick = { isFixedAmount = true },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("固定金额") }
                )
                FilterChip(
                    selected = !isFixedAmount,
                    onClick = { isFixedAmount = false },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("百分比") }
                )
            }

            // ---- 添加器：目标单选（账户行 + 计划行，至多一个目标）----
            if (accounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = targetAccountId == acc.id && targetPlanId == null,
                            onClick = {
                                targetAccountId = acc.id
                                targetPlanId = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
            if (plans.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    plans.forEach { plan ->
                        FilterChip(
                            selected = targetPlanId == plan.id && targetAccountId == null,
                            onClick = {
                                targetPlanId = plan.id
                                targetAccountId = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text(plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            // ---- 内联输入行：选中目标后出现 ----
            if (targetChosen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newValue ->
                            inputText = if (isFixedAmount) {
                                newValue
                            } else {
                                newValue.filter { it.isDigit() }.take(3)
                            }
                        },
                        label = { Text(if (isFixedAmount) "金额 (元)" else "百分比 (%)") },
                        placeholder = { Text(if (isFixedAmount) "0.00" else "30") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isFixedAmount) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = { addRule() },
                        enabled = canAdd,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "添加",
                            fontWeight = FontWeight.Bold,
                            color = if (canAdd) schemeColors.primary else schemeColors.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
