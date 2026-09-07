package com.yuanman.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.roundToInt

/**
 * 账户页「攒钱计划」区块纯展示组件（设计文档 saving-plans-and-paycheck-v0.3.md §2.1 / §3 边界表
 * + redesign v0.2 §6.4–6.7）：
 * PlansSectionHeader（区块头，不带计划数量）/ PlanMiniCard（计划小卡）/
 * PlansGhostMiniCard（「+ 新建计划」幽灵小卡）/ PlansWideGhostCard（空计划态宽幽灵卡）。
 * 发薪分配入口已迁入账户页 hero「更多」菜单（§6.4），PaycheckEntryCard 随之移除。
 * 全部交互经回调上抛，本文件无 VM / 弹层 / 导航依赖；金额一律走
 * MoneyUtils.centsToYuanString(withGrouping = true) + 自拼 ¥ 前缀，隐私态统一「¥ ••••」掩码；
 * 只按空态 / 布尔 / 数值分支，不做任何 label/type 字符串内容判断（零类型语义）。
 */

/** 超额警示琥珀（0xFFFF9800）—— 与待核对逾期 / 首页预算警示同值，同目录惯例：文件私有字面量，不抽主题 token */
private val OverdrawnWarnColor = Color(0xFFFF9800)

/**
 * 攒钱计划区块头（§2.1 + redesign v0.2 §6.5/§6.7）：主色圆点 + 「攒钱计划」（不带数量）
 * + 右侧文字钮「管理 ›」。横向 20dp 内边距与组头（AccountGroupHeader）同列对齐。
 * 命中区仅按钮自身（高 32dp 文字钮，Surface 自带 ripple），不再横向铺满整行。
 */
@Composable
fun PlansSectionHeader(
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

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
                    .background(colors.primary)
            )
            Text(
                text = "攒钱计划",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = colors.onSurface
            )
        }

        // 「管理 ›」文字钮：32dp 高命中区，仅按钮自身可点（§6.7）。
        // 注意：内容行不能用 fillMaxWidth —— Row 里非 weight 子项会收到「剩余宽度」
        // 作为上限，fillMax 会把整颗钮拉成标题右侧的一长条，文字还会被水平居中到长条中央。
        Surface(
            onClick = onManageClick,
            shape = RoundedCornerShape(8.dp),
            color = colors.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.26f))
        ) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "管理 ›",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.primary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 计划小卡（§2.1 横向卡行，152 × 92、r16、0.5dp elevation、1dp 细描边，token 与 AccountItemCard 同风格）。
 * 自上而下：行1 彩点 + 名称 + 达标主色 ✓；
 * 中部行「已攒 ¥金额」+ 达成%（redesign v0.2 §6.6）——数据模型暂无按月入账流水，
 * 故「本期攒钱」取当前已攒（earmarkedCents 累计已圈）口径展示；金额走 MoneyUtils
 * 千分位两位，隐私态统一「¥ ••••」（达成% 为相对比例，隐私态一并省略以少泄密）；
 * 超额时（余额 < 已圈）标题下方琥珀警示行「低于专款 ¥差额」，整卡描边换琥珀；
 * 底部：目标 > 0 → 4dp 进度条 + 「目标 ¥金额」收底；目标 = 0（不设限）→ 无进度条，
 * 以「（无上限目标）」单行收底。
 * 超额判定与差额在组件内由 [holderBalanceCents] 推导（与二级页 PlanSummaryCard 同口径）。
 * 整卡点击 → onClick。
 */
@Composable
fun PlanMiniCard(
    plan: SavingPlanUiModel,
    holderBalanceCents: Long?,
    isDone: Boolean,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasTarget = plan.targetAmountCents > 0L
    val overdrawn = holderBalanceCents != null && planIsOverdrawn(plan, holderBalanceCents)
    val progressFraction =
        if (hasTarget) (plan.earmarkedCents.toFloat() / plan.targetAmountCents.toFloat()).coerceIn(0.06f, 1f)
        else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            if (overdrawn) OverdrawnWarnColor.copy(alpha = 0.45f)
            else colors.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .width(152.dp)
            .height(92.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            PlanMiniTitleRow(
                plan = plan,
                showDone = isDone && !overdrawn
            )
            if (overdrawn) {
                PlanMiniOverdrawnLine(
                    shortfallCents = (plan.earmarkedCents - (holderBalanceCents ?: 0L)).coerceAtLeast(0L),
                    isPrivacyMode = isPrivacyMode
                )
                // 超额行自带顶部 2dp 内距，其后仅留 1dp 呼吸
                Spacer(modifier = Modifier.height(1.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            // 中部行（§6.6）：本期攒钱 = 当前已攒金额（口径见 PlanMiniCard 注释）
            PlanMiniSavedLine(plan = plan, isPrivacyMode = isPrivacyMode)
            Spacer(modifier = Modifier.weight(1f))
            if (hasTarget) {
                PlanMiniProgressBar(fraction = progressFraction, accentColor = plan.colorHex)
                Spacer(modifier = Modifier.height(3.dp))
            }
            PlanMiniGoalLine(plan = plan, isPrivacyMode = isPrivacyMode)
        }
    }
}

/** 小卡行1：彩点 + 名称（weight 单行省略）+ 达标主色 ✓（超额时不显示 ✓，改走警示行） */
@Composable
private fun PlanMiniTitleRow(
    plan: SavingPlanUiModel,
    showDone: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(plan.colorHex))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = plan.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showDone) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/** 超额警示行（§3：余额低于专款 → 琥珀「低于专款 ¥差额」，金额隐私掩码） */
@Composable
private fun PlanMiniOverdrawnLine(
    shortfallCents: Long,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPrivacyMode) {
                "低于专款 ¥ ••••"
            } else {
                "低于专款 ¥" + MoneyUtils.centsToYuanString(shortfallCents, withGrouping = true)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = OverdrawnWarnColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 小卡中区进度条：4dp 高、r2 轨道 surfaceVariant，填充 = 计划彩点同色 */
@Composable
private fun PlanMiniProgressBar(
    fraction: Float,
    accentColor: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(Color(accentColor))
        )
    }
}

/**
 * 中部行「已攒」（10.5sp，§6.6）：前缀「已攒」outline + 金额 Bold onSurface（千分位两位）。
 * 有目标时同行动尾附「 · N%」达成率（primary，四舍五入取整、上限 100）；
 * 隐私态金额统一「¥ ••••」，达成率（相对比例）一并省略。金额经 MoneyUtils 千分位两位。
 */
@Composable
private fun PlanMiniSavedLine(
    plan: SavingPlanUiModel,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val baseStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp)
    val hasTarget = plan.targetAmountCents > 0L
    val amountText = "¥" + MoneyUtils.centsToYuanString(plan.earmarkedCents, withGrouping = true)
    val pct = if (hasTarget) {
        ((plan.earmarkedCents.toDouble() / plan.targetAmountCents.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    } else 0

    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.outline)) { append("已攒 ") }
        if (isPrivacyMode) {
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("¥ ••••") }
        } else {
            withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) {
                append(amountText)
            }
            if (hasTarget) {
                withStyle(SpanStyle(color = colors.primary)) { append(" · $pct%") }
            }
        }
    }

    Text(
        text = text,
        style = baseStyle,
        color = colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * 底部收行（10.5sp，§6.6）：有目标 → 「目标 ¥金额」（outline 前缀 + onSurfaceVariant 值，
 * 与上方进度条构成完整图例）；无目标 → 「（无上限目标）」outline 单行收底。
 * 隐私态金额位一律「¥ ••••」。金额经 MoneyUtils 千分位两位。
 */
@Composable
private fun PlanMiniGoalLine(
    plan: SavingPlanUiModel,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val baseStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp)
    val hasTarget = plan.targetAmountCents > 0L

    val text = if (hasTarget) {
        val targetText = "¥" + MoneyUtils.centsToYuanString(plan.targetAmountCents, withGrouping = true)
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.outline)) { append("目标 ") }
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                append(if (isPrivacyMode) "¥ ••••" else targetText)
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.outline)) { append("（无上限目标）") }
        }
    }

    Text(
        text = text,
        style = baseStyle,
        color = colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * 「+ 新建计划」幽灵小卡（§2.1）：152 × 92，同一套 drawBehind 虚线幽灵卡惯例
 * （RoundRect + dashPathEffect(8f, 6f)、1dp、outline alpha0.6）+ surfaceVariant alpha0.4 底；
 * 内容「+ 新建计划」居中。未画出虚线描边视为实现错误。
 */
@Composable
fun PlansGhostMiniCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dashColor = colors.outline.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .width(152.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val corner = (16.dp.toPx() - inset).coerceAtLeast(0f)
                val dashPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = inset,
                            top = inset,
                            right = size.width - inset,
                            bottom = size.height - inset,
                            radiusX = corner,
                            radiusY = corner
                        )
                    )
                }
                drawPath(
                    path = dashPath,
                    color = dashColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "新建计划",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * 一行宽「新建攒钱计划」幽灵卡（§2.1 空计划态 / §3 无账户弱提示）：
 * 全宽 52dp、r16、虚线描边 + surfaceVariant 0.4 底（同 drawBehind 虚线幽灵卡惯例）。
 * enabled=false 时整体降透明度并替换文案为「先创建账户」弱提示（§3：账户为空时 ghost 禁用）。
 */
@Composable
fun PlansWideGhostCard(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dashColor = colors.outline.copy(alpha = if (enabled) 0.6f else 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = if (enabled) 0.4f else 0.25f))
            .alpha(if (enabled) 1f else 0.6f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val corner = (16.dp.toPx() - inset).coerceAtLeast(0f)
                val dashPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = inset,
                            top = inset,
                            right = size.width - inset,
                            bottom = size.height - inset,
                            radiusX = corner,
                            radiusY = corner
                        )
                    )
                }
                drawPath(
                    path = dashPath,
                    color = dashColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (enabled) "新建攒钱计划" else "新建攒钱计划 · 先创建账户",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.onSurfaceVariant
            )
        }
    }
}
