@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 账户页「攒钱计划」区块展示组件：
 * - PlansSectionHeader：区块标题头（左侧主色微条 + 标题，右侧「＋ 新建计划」胶囊按钮）。
 * - PlanMiniCard：计划微卡（152 × 108dp，r16，光晕色彩点、分层金额排版、6dp 精致进度条）。
 * - PlansEmptyRow：同构空态卡片流（152 × 108dp，与有数据时零跳版切换）：
 *     - 首卡 PlanGhostCreateCard：虚线幽灵卡，点击直接新建；
 *     - 随卡 PlanInspirationCard：灵感心愿卡（旅行、数码、应急等），点击一键带入预设。
 * - PlansEmptyHint：情绪文案轮播卡（向下兼容保留）。
 */

/** 超额警示琥珀（0xFFFF9800）—— 与待核对逾期 / 首页预算警示同值 */
private val OverdrawnWarnColor = Color(0xFFFF9800)

/**
 * 攒钱计划区块头：主色竖条（与账户分组组头同款强调符号）+ 「攒钱计划」 + 右侧文字钮「＋ 新建计划」。
 * 横向 20dp 内边距与组头（AccountGroupHeader）同列对齐；命中区仅按钮自身。
 */
@Composable
fun PlansSectionHeader(
    onNewPlanClick: () -> Unit,
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
                    .width(4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
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

        Surface(
            onClick = onNewPlanClick,
            shape = RoundedCornerShape(8.dp),
            color = colors.primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.26f))
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
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "新建计划",
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

// -----------------------------------------------------------------------------
// 1. 计划微卡（PlanMiniCard）—— 细节升级版
// -----------------------------------------------------------------------------

/**
 * 计划小卡（横向卡行，152 × 108dp、r16、0.5dp elevation、1dp 细描边）。
 *
 * 视觉层次升级：
 *  - 顶层注入 5% 计划色极微弱光晕，卡片更具专属辨识度；
 *  - 行1：色彩指示灯（带 13dp 柔光外环 + 6.5dp 实体核），计划名称（13sp SemiBold 单行省略），
 *         右侧达标显示「达成」精致微胶囊，未达标显示百分比小字；
 *  - 行2：已攒金额主读数：¥ 币符（12sp）与金额数字（20sp Bold）分层排版；
 *  - 行3：说明行：超额警示（琥珀）> 目标金额 > 未设目标上限；
 *  - 行4：精致进度条（6dp 标尺，倒角圆润裁切，超额/达标平滑过渡）。
 */
@Composable
fun PlanMiniCard(
    plan: SavingPlanUiModel,
    holderBalanceCents: Long?,
    isDone: Boolean,
    isPrivacyMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasTarget = plan.targetAmountCents > 0L
    val overdrawn = holderBalanceCents != null && planIsOverdrawn(plan, holderBalanceCents)
    val planColor = Color(plan.colorHex)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(
            1.dp,
            when {
                overdrawn -> OverdrawnWarnColor.copy(alpha = 0.55f)
                isDone -> planColor.copy(alpha = 0.45f)
                else -> colors.outlineVariant.copy(alpha = 0.38f)
            }
        ),
        modifier = modifier
            .width(152.dp)
            .height(108.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶端柔和微光晕氛围层
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(planColor.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 行1：指示灯 + 名称 + 达标/百分比
                PlanMiniTitleRow(plan = plan, isDone = isDone, isOverdrawn = overdrawn)
                Spacer(modifier = Modifier.height(5.dp))

                // 行2：主读数（¥ 币符 + 金额数字分层）
                PlanMiniAmountRow(plan = plan, isPrivacyMode = isPrivacyMode)
                Spacer(modifier = Modifier.height(2.dp))

                // 行3：说明行（超额警示 / 目标金额 / 未设上限）
                PlanMiniCaptionRow(
                    plan = plan,
                    overdrawn = overdrawn,
                    shortfallCents = (plan.earmarkedCents - (holderBalanceCents ?: 0L)).coerceAtLeast(0L),
                    isPrivacyMode = isPrivacyMode
                )

                Spacer(modifier = Modifier.weight(1f))

                // 行4：进度条（有目标时绘制 6dp 精致轨，无目标时弹性吸收）
                if (hasTarget) {
                    PlanMiniProgressBar(
                        fraction = progressFractionFor(plan),
                        accentColor = plan.colorHex,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** 小卡标题行：光晕彩点 + 名称；右端 = 达标「达成」微徽标 或 百分比文字 */
@Composable
private fun PlanMiniTitleRow(
    plan: SavingPlanUiModel,
    isDone: Boolean,
    isOverdrawn: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val planColor = Color(plan.colorHex)
    val hasTarget = plan.targetAmountCents > 0L
    val showDoneCheck = isDone && !isOverdrawn

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 双层光晕彩点（13dp 外环 + 6.5dp 实体核）
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(planColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.5.dp)
                    .clip(CircleShape)
                    .background(planColor)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = plan.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (hasTarget) {
            Spacer(modifier = Modifier.width(4.dp))
            if (showDoneCheck) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = planColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已达成",
                            tint = planColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "达成",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = planColor,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    text = "${percentOf(plan)}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = planColor,
                    maxLines = 1
                )
            }
        }
    }
}

/** 小卡进度比例（达标封顶 1f） */
private fun progressFractionFor(plan: SavingPlanUiModel): Float {
    if (plan.targetAmountCents <= 0L) return 0f
    return (plan.earmarkedCents.toFloat() / plan.targetAmountCents.toFloat()).coerceIn(0f, 1f)
}

/** 小卡达成百分比（0 ~ 100） */
private fun percentOf(plan: SavingPlanUiModel): Int {
    if (plan.targetAmountCents <= 0L) return 0
    return ((plan.earmarkedCents.toDouble() / plan.targetAmountCents.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

/** 说明行（行3）：超额警示（琥珀）> 目标金额 > 未设上限 */
@Composable
private fun PlanMiniCaptionRow(
    plan: SavingPlanUiModel,
    overdrawn: Boolean,
    shortfallCents: Long,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasTarget = plan.targetAmountCents > 0L

    val text: String
    val textColor: Color
    when {
        overdrawn -> {
            val shortfallStr = if (isPrivacyMode) "••••" else MoneyUtils.centsToCompactYuan(shortfallCents)
            text = "低于专款 ¥$shortfallStr"
            textColor = OverdrawnWarnColor
        }
        hasTarget -> {
            val targetStr = if (isPrivacyMode) "••••" else MoneyUtils.centsToCompactYuan(plan.targetAmountCents)
            text = "目标 ¥$targetStr"
            textColor = colors.onSurfaceVariant.copy(alpha = 0.75f)
        }
        else -> {
            text = "未设目标上限"
            textColor = colors.outline.copy(alpha = 0.75f)
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}

/** 小卡进度条：6dp 高、r3 轨道与裁剪，视觉更细致 */
@Composable
private fun PlanMiniProgressBar(
    fraction: Float,
    accentColor: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(accentColor))
            )
        }
    }
}

/**
 * 已攒金额（卡片主数字，行2）：
 * ¥ 币符与主数字分层排版，提升金融卡片阅读质感；隐私态统一掩码。
 */
@Composable
private fun PlanMiniAmountRow(
    plan: SavingPlanUiModel,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 2.dp, end = 2.dp)
        )
        Text(
            text = if (isPrivacyMode) "••••" else MoneyUtils.centsToCompactYuan(plan.earmarkedCents),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------
// 2. 空计划态灵感卡片流（PlansEmptyRow & 预设卡片）
// -----------------------------------------------------------------------------

/** 攒钱心愿灵感预设数据模型 */
data class PlanInspirationPreset(
    val emoji: String,
    val name: String,
    val targetAmountCents: Long,
    val colorHex: Long,
    val tagline: String
)

/** 经典心愿预设库（按生活高频场景精选 4 款） */
val DefaultPlanInspirations = listOf(
    PlanInspirationPreset(
        emoji = "🏝️",
        name = "旅行度假",
        targetAmountCents = 5_000_00L,
        colorHex = 0xFF0D9488L, // 湖海青
        tagline = "去看山和大海"
    ),
    PlanInspirationPreset(
        emoji = "📱",
        name = "数码换新",
        targetAmountCents = 8_000_00L,
        colorHex = 0xFF6366F1L, // 科技紫
        tagline = "给心仪装备蓄力"
    ),
    PlanInspirationPreset(
        emoji = "🛡️",
        name = "应急备用金",
        targetAmountCents = 20_000_00L,
        colorHex = 0xFFF59E0BL, // 暖阳金
        tagline = "积攒3~6月安全感"
    ),
    PlanInspirationPreset(
        emoji = "🎸",
        name = "兴趣成长",
        targetAmountCents = 3_000_00L,
        colorHex = 0xFFEC4899L, // 活力粉
        tagline = "把热爱变成专款"
    )
)

/**
 * 空态卡片流：与 PlanMiniCard 相同规格的 LazyRow（152 × 108dp，0 像素跳版）。
 * 首卡为「新建幽灵卡」，随卡为「灵感心愿预设卡」，点击直接一键开攒。
 */
@Composable
fun PlansEmptyRow(
    onNewPlanClick: () -> Unit,
    onSelectInspiration: (PlanInspirationPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        item(key = "empty_create_ghost") {
            PlanGhostCreateCard(onClick = onNewPlanClick)
        }
        items(DefaultPlanInspirations, key = { it.name }) { preset ->
            PlanInspirationCard(
                preset = preset,
                onClick = { onSelectInspiration(preset) }
            )
        }
    }
}

/**
 * 虚线幽灵卡：整卡可点，明确引导创建第一个攒钱计划。
 */
@Composable
fun PlanGhostCreateCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val strokeColor = colors.primary.copy(alpha = 0.35f)
    val cornerRadius = 16.dp

    Box(
        modifier = modifier
            .width(152.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.primary.copy(alpha = 0.04f))
            .drawBehind {
                val strokeWidth = 1.2.dp.toPx()
                val radius = cornerRadius.toPx()
                drawRoundRect(
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    ),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建计划",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "新建计划",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "开启愿望专款",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/**
 * 灵感心愿卡：带预置目标和色彩的卡片，点击一键唤起新建表单并预填参数。
 */
@Composable
fun PlanInspirationCard(
    preset: PlanInspirationPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val presetColor = Color(preset.colorHex)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .width(152.dp)
            .height(108.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶端主题色弱氛围层
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(presetColor.copy(alpha = 0.06f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 行1：Emoji + 名称 + 右侧微型「灵感」徽标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preset.emoji,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = presetColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "灵感",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = presetColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // 行2：预设目标金额
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "目标 ¥",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 1.dp, end = 1.dp)
                    )
                    Text(
                        text = MoneyUtils.centsToCompactYuan(preset.targetAmountCents),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.onSurface,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 行3：副标语
                Text(
                    text = preset.tagline,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp
                    ),
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                // 行4：引导提示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "一键开攒 ›",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = presetColor
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. 经典文案轮播卡（向下兼容保留）
// -----------------------------------------------------------------------------

/** 空计划态轮播文案：只讲情绪与期待；引导创建另起一行静态文案（新建入口在区块头） */
private val PlanEmptyQuotes = listOf(
    "把钱留给想做的事，日子会一点点变成想要的样子。",
    "给愿望开个专款账户，攒着攒着，它自己就会实现。",
    "每一个想去的远方，都值得从今天开始攒。",
    "为想要的生活提前存一点期待，未来的你会感谢现在。",
    "攒钱不是克制，是给未来的自己多一分说「可以」的底气。"
)

/**
 * 空计划态提示卡：情绪向文案自动轮播（约 4 秒一句，淡入淡出切换），
 * 下方固定一行创建引导；不放置可点的「＋ 新建」大入口 —— 新建按钮收敛在区块头，避免重复入口。
 */
@Composable
fun PlansEmptyHint(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var quoteIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            quoteIndex = (quoteIndex + 1) % PlanEmptyQuotes.size
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 固定预留两行高度：轮播句子长短不一也不引起列表跳动
            Crossfade(
                targetState = quoteIndex,
                label = "planEmptyQuote"
            ) { index ->
                Text(
                    text = PlanEmptyQuotes[index],
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 40.dp)
                )
            }
            // 引导创建：静态一行，指向上方区块头的新建按钮
            Text(
                text = "想好了目标？点右上角「＋ 新建计划」，开攒第一笔专款。",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = colors.primary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
