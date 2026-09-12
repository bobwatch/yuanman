package com.yuanman.app.ui.screens.panorama

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.CushionTone
import com.yuanman.app.data.model.SafetyCushionLevel
import com.yuanman.app.data.model.SafetyCushionResult
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.theme.BalanceColorDark
import com.yuanman.app.ui.theme.BalanceColorLight
import com.yuanman.app.ui.theme.IncomeColorDark
import com.yuanman.app.ui.theme.IncomeColorLight
import com.yuanman.app.utils.MoneyUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 资产全景专属主题自适应配色容器
 * 仅保留状态语义色（入账绿、警示琥珀、信息蓝），与深浅色主题自适应绑定；
 * 分布条/账户行的多色一律取自账户自身 colorHex，不再引入页面级调色板。
 */
@Immutable
data class PanoramaThemeColors(
    val isDark: Boolean,
    val incomePositive: Color,
    val warnAmber: Color,
    val infoBlue: Color
)

@Composable
fun rememberPanoramaThemeColors(): PanoramaThemeColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(isDark, scheme) {
        PanoramaThemeColors(
            isDark = isDark,
            incomePositive = if (isDark) IncomeColorDark else IncomeColorLight,
            warnAmber = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00),
            infoBlue = if (isDark) BalanceColorDark else BalanceColorLight
        )
    }
}

/** 资产全景页统一表面卡：与账户模块卡片 token 一致（r16 / 1dp 细描边 / 0.5dp 悬浮） */
@Composable
fun PanoramaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** 区块标题（分布/负债/安全垫/走势共用的二级标题档） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

// ============================================================================
// 1. 资本与负债中枢卡 (CapitalBalanceHeroCard)
//
// 彻底解决上一级净资产大字重复的问题：
// 净资产主读数收敛至 24sp Bold，释放顶部纵深；
// 对称呈现总资产、待还负债与负债率徽章；
// 底部配备资产/负债双色比例条。
// ============================================================================
@Composable
fun CapitalBalanceHeroCard(
    netWorthCents: Long,
    totalAssetCents: Long,
    totalDebtCents: Long,
    deltaCents: Long,
    deltaRatio: Float?,
    debtToAssetRatio: Float,
    isPrivacyMode: Boolean,
    onTogglePrivacy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberPanoramaThemeColors()
    val isNegative = netWorthCents < 0L
    val isDebt = totalDebtCents < 0L

    val netWorthText = when {
        isPrivacyMode -> "¥ ••••"
        isNegative -> "-¥" + MoneyUtils.centsToYuanString(-netWorthCents, withGrouping = true)
        else -> "¥" + MoneyUtils.centsToYuanString(netWorthCents, withGrouping = true)
    }
    val assetText = if (isPrivacyMode) {
        "¥ ••••"
    } else {
        "¥" + MoneyUtils.centsToYuanString(totalAssetCents, withGrouping = true)
    }
    val debtText = when {
        !isDebt -> "¥0.00"
        isPrivacyMode -> "¥ ••••"
        else -> "-¥" + MoneyUtils.centsToYuanString(-totalDebtCents, withGrouping = true)
    }

    PanoramaCard(modifier = modifier) {
        // 行 1：净资产标题 + 较上月环比微胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "净资产",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = scheme.onSurfaceVariant
                )
            }

            if (!isPrivacyMode && deltaCents != 0L) {
                val isUp = deltaCents > 0L
                val tint = if (isUp) colors.incomePositive else scheme.error
                val sign = if (isUp) "+" else "-"
                val ratioStr = deltaRatio?.let { " (${"%.1f".format(Locale.US, it * 100)}%)" } ?: ""
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tint.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                ) {
                    Text(
                        text = "较上月 $sign¥${MoneyUtils.centsToYuanString(abs(deltaCents), withGrouping = true)}$ratioStr",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = tint,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 行 2：主数字（收敛至 24sp，若极端位数则等比下调）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val integerLen = MoneyUtils
                .centsToYuanString(if (isNegative) -netWorthCents else netWorthCents, withGrouping = true)
                .substringBefore('.')
                .length
            val mainSp = when {
                integerLen >= 13 -> 18f
                integerLen >= 10 -> 21f
                else -> 24f
            }
            Text(
                text = netWorthText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = mainSp.sp
                ),
                color = if (!isPrivacyMode && isNegative) scheme.error else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        // 行 3：微细分割线
        HorizontalDivider(
            color = scheme.outlineVariant.copy(alpha = 0.25f),
            modifier = Modifier.padding(vertical = 2.dp)
        )

        // 行 4：总资产 / 待还负债对称仪表盘
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 左列：总资产（隐私小眼睛紧随金额之后，长金额自动截断）
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "总资产",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = scheme.outline
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = assetText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onTogglePrivacy != null) {
                        Spacer(modifier = Modifier.width(5.dp))
                        IconButton(
                            onClick = onTogglePrivacy,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPrivacyMode) "显示金额" else "隐藏金额",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 右列：待还负债与负债率
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "待还负债",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = scheme.outline
                    )
                    if (isDebt && debtToAssetRatio > 0f) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = scheme.error.copy(alpha = if (colors.isDark) 0.18f else 0.12f)
                        ) {
                            Text(
                                text = "负债率 ${"%.1f".format(Locale.US, debtToAssetRatio * 100)}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = scheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                if (isDebt) {
                    Text(
                        text = debtText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = if (!isPrivacyMode) scheme.error else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "无负债",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = scheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = scheme.primary
                        )
                    }
                }
            }
        }

        // 行 5：双色比例对比条（仅当总资产>0且存在负债时展示）
        if (totalAssetCents > 0L && isDebt) {
            val assetWeight = (1f - debtToAssetRatio).coerceIn(0.01f, 1f)
            val debtWeight = debtToAssetRatio.coerceIn(0.01f, 1f)
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .weight(assetWeight)
                        .fillMaxHeight()
                        .background(scheme.primary)
                )
                Box(
                    modifier = Modifier
                        .weight(debtWeight)
                        .fillMaxHeight()
                        .background(scheme.error)
                )
            }
        }
    }
}

/** 兼容旧命名 */
@Composable
@Deprecated("Use CapitalBalanceHeroCard instead", ReplaceWith("CapitalBalanceHeroCard"))
fun NetWorthOverviewCard(
    netWorthCents: Long,
    totalAssetCents: Long,
    totalDebtCents: Long,
    deltaCents: Long,
    deltaRatio: Float?,
    isPrivacyMode: Boolean,
    onTogglePrivacy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val debtRatio = if (totalAssetCents > 0L && totalDebtCents < 0L) {
        (-totalDebtCents).toFloat() / totalAssetCents.toFloat()
    } else 0f
    CapitalBalanceHeroCard(
        netWorthCents = netWorthCents,
        totalAssetCents = totalAssetCents,
        totalDebtCents = totalDebtCents,
        deltaCents = deltaCents,
        deltaRatio = deltaRatio,
        debtToAssetRatio = debtRatio,
        isPrivacyMode = isPrivacyMode,
        onTogglePrivacy = onTogglePrivacy,
        modifier = modifier
    )
}

// ============================================================================
// 2. 资产配置卡（按类别分组，默认展开组内子账户）
//
// 资产明细收敛为单卡：按类别分组聚合展示全部正资产账户（未自定义分组时
// 全部归入「未分组」呈现，不再额外提供账户排行卡）。
// ============================================================================

/** 资产配置卡：配置分布彩条 + 分类分组清单（默认展开，点击组头可折叠） */
@Composable
fun AssetAllocationCard(
    allocationGroups: List<AssetAllocationGroup>,
    isPrivacyMode: Boolean,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    PanoramaCard(modifier = modifier) {
        SectionTitle(text = "资产配置")

        if (allocationGroups.isEmpty()) {
            Text(
                text = "暂无正资产账户",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline
            )
        } else {
            // 配置分布彩条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                allocationGroups.forEach { grp ->
                    if (grp.ratio > 0f) {
                        val segColor = grp.accounts.firstOrNull()?.colorHex?.let { Color(it) } ?: scheme.primary
                        Box(
                            modifier = Modifier
                                .weight(grp.ratio.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(segColor)
                        )
                    }
                }
            }

            // 分组清单（点击展开/收起组内子账户）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allocationGroups.forEach { grp ->
                    val isExpanded = expandedGroups[grp.label] ?: true
                    val barColor = grp.accounts.firstOrNull()?.colorHex?.let { Color(it) } ?: scheme.primary

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expandedGroups[grp.label] = !isExpanded }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = grp.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = scheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "· ${grp.accounts.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = scheme.outline
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(grp.totalCents, withGrouping = true),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = scheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(grp.ratio * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = scheme.outline
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = scheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 组内子账户列表
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                grp.accounts.forEach { acc ->
                                    CategorySubAccountRow(
                                        account = acc,
                                        isPrivacyMode = isPrivacyMode,
                                        onClick = { onAccountClick(acc.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类展开子账户行 */
@Composable
private fun CategorySubAccountRow(
    account: AssetAccountItem,
    isPrivacyMode: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val percent = (account.ratio * 100).roundToInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconView(
            iconName = account.iconName,
            colorHex = account.colorHex,
            size = 28.dp,
            iconSize = 14.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isPrivacyMode) "¥ ••••" else "¥" + MoneyUtils.centsToYuanString(account.balanceCents, withGrouping = true),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "占比 $percent%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = scheme.outline
            )
        }
    }
}

// ============================================================================
// 3. 资金安全垫卡 (SafetyCushionCard)
//
// 保持克制与专业：
// 标尺轨道厚度收敛至 6dp（原 8dp）；
// 净现金与月均开销说明行严格隐私掩码；
// 透支与数据积累态优雅自适应。
// ============================================================================
@Composable
fun SafetyCushionCard(
    cushion: SafetyCushionResult,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberPanoramaThemeColors()

    val badgeColor = when (cushion.level.tone) {
        CushionTone.DANGER -> scheme.error
        CushionTone.WARNING -> colors.warnAmber
        CushionTone.POSITIVE -> colors.incomePositive
        CushionTone.FRESH -> scheme.primary
        CushionTone.INFO -> colors.infoBlue
        CushionTone.MUTED -> scheme.outline
    }
    val isDeficit = cushion.level == SafetyCushionLevel.DEFICIT
    val isCollecting = cushion.level == SafetyCushionLevel.COLLECTING
    val showScale = !isDeficit && !isCollecting

    val mainText = when {
        isDeficit -> "透支超载状态"
        isCollecting -> "数据持续积累中"
        else -> "净现金可支撑生活约 ${"%.1f".format(Locale.US, cushion.runwayMonths)} 个月"
    }

    PanoramaCard(modifier = modifier) {
        // 顶行：标题 + 等级胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(text = "资金安全垫")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeColor.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = cushion.level.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = badgeColor
                    )
                }
            }
        }

        // 核心结论主句（18sp Bold）
        Text(
            text = mainText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = scheme.onSurface
        )

        if (showScale) {
            val liquidStr = if (isPrivacyMode) "••••" else "¥" + MoneyUtils.centsToYuanString(cushion.netLiquidCents.coerceAtLeast(0L), withGrouping = true)
            val burnStr = if (isPrivacyMode) "••••" else "¥" + MoneyUtils.centsToYuanString(cushion.monthlyBurnCents, withGrouping = true)
            Text(
                text = "净可用现金 $liquidStr · 参考月均支出 $burnStr",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = scheme.outline
            )

            // 标尺：0 ~ 24 个月，精炼 6dp 轨道
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(scheme.surfaceVariant.copy(alpha = if (colors.isDark) 0.5f else 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(cushion.progressPercent)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(badgeColor)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0", fontSize = 9.5.sp, color = scheme.outline)
                    Text(text = "3月", fontSize = 9.5.sp, color = scheme.outline)
                    Text(text = "6月", fontSize = 9.5.sp, color = scheme.outline)
                    Text(text = "12月", fontSize = 9.5.sp, color = scheme.outline)
                    Text(text = "24月+", fontSize = 9.5.sp, color = scheme.outline)
                }
            }
        }

        // 洞察说明行
        Text(
            text = cushion.insightMessage,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            color = scheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// 4. 净资产走势卡 (NetWorthTrendCard)
//
// 跃升至第 3 屏位；
// 顶行集成周期累计变动 / 手指滑动探针实时月份净值；
// Canvas 支持触摸探针交互（虚线对齐 + 焦点高亮）。
// ============================================================================
@Composable
fun NetWorthTrendCard(
    points: List<NetWorthTrendPoint>,
    selectedPeriod: PanoramaTrendPeriod,
    onSelectPeriod: (PanoramaTrendPeriod) -> Unit,
    isPrivacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberPanoramaThemeColors()
    var selectedPointIndex by remember(points, selectedPeriod) { mutableStateOf<Int?>(null) }

    PanoramaCard(modifier = modifier) {
        // 顶行：标题与周期切换 chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(text = "净值走势")

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PanoramaTrendPeriod.values().forEach { period ->
                    val isSelected = selectedPeriod == period
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectPeriod(period) }
                    ) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 次行：探针动态指示 或 期间变动小计
        val activePoint = selectedPointIndex?.let { points.getOrNull(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activePoint != null) {
                Text(
                    text = "${activePoint.label} 净资产",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = scheme.primary
                )
                val nwStr = if (isPrivacyMode) {
                    "¥ ••••"
                } else if (activePoint.netWorthCents < 0) {
                    "-¥" + MoneyUtils.centsToYuanString(-activePoint.netWorthCents, withGrouping = true)
                } else {
                    "¥" + MoneyUtils.centsToYuanString(activePoint.netWorthCents, withGrouping = true)
                }
                Text(
                    text = nwStr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = if (!isPrivacyMode && activePoint.netWorthCents < 0) scheme.error else scheme.primary
                )
            } else if (points.size >= 2) {
                val delta = points.last().netWorthCents - points.first().netWorthCents
                val isUp = delta >= 0
                val sign = if (isUp) "+" else "-"
                val tint = if (isUp) colors.incomePositive else scheme.error
                Text(
                    text = "期间累计变动",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = scheme.outline
                )
                Text(
                    text = if (isPrivacyMode) "¥ ••••" else "$sign¥${MoneyUtils.centsToYuanString(abs(delta), withGrouping = true)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = tint
                )
            }
        }

        // Canvas 折线图与探针手势区域
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无充足走势数据", color = scheme.outline, fontSize = 12.sp)
            }
        } else {
            val lineColor = scheme.primary
            val dotCenterColor = scheme.surface
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = if (colors.isDark) 0.35f else 0.22f),
                    scheme.primary.copy(alpha = 0.02f)
                )
            )

            val minVal = points.minOf { it.netWorthCents }
            val maxVal = points.maxOf { it.netWorthCents }
            val valueSpan = (maxVal - minVal).coerceAtLeast(1L)

            // 左侧纵轴刻度区宽度（Canvas 与手势换算共用同一值）
            val axisGutterDp = 46.dp
            val axisTextPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

            // 纵轴刻度：步长取 1/2/5×10ⁿ 中最接近 span/4 的值，得到 3~5 条网格
            val tickRawStep = (maxVal - minVal) / 4
            var tickMagnitude = 1L
            while (tickMagnitude * 10 <= tickRawStep) tickMagnitude *= 10
            var axisTickStep = 1L
            for (mult in longArrayOf(1L, 2L, 5L, 10L)) {
                if (mult * tickMagnitude >= tickRawStep) {
                    axisTickStep = mult * tickMagnitude
                    break
                }
            }
            val axisTicks = buildList {
                var t = minVal
                while (t <= maxVal) {
                    add(t)
                    t += axisTickStep
                }
            }.toMutableList()
            // 顶部余量超过半步长时补一条 max 网格，避免折线在末格悬空
            if (axisTicks.last() < maxVal && (maxVal - axisTicks.last()) * 2 > axisTickStep) {
                axisTicks.add(maxVal)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val stepX = if (points.size > 1) (size.width.toFloat() - axisGutterDp.toPx()) / (points.size - 1) else size.width.toFloat()
                                val idx = ((offset.x - axisGutterDp.toPx()) / stepX).roundToInt().coerceIn(0, points.lastIndex)
                                selectedPointIndex = idx
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val stepX = if (points.size > 1) (size.width.toFloat() - axisGutterDp.toPx()) / (points.size - 1) else size.width.toFloat()
                                val idx = ((change.position.x - axisGutterDp.toPx()) / stepX).roundToInt().coerceIn(0, points.lastIndex)
                                selectedPointIndex = idx
                            },
                            onDragEnd = { selectedPointIndex = null },
                            onDragCancel = { selectedPointIndex = null }
                        )
                    }
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { offset ->
                                val stepX = if (points.size > 1) (size.width.toFloat() - axisGutterDp.toPx()) / (points.size - 1) else size.width.toFloat()
                                val idx = ((offset.x - axisGutterDp.toPx()) / stepX).roundToInt().coerceIn(0, points.lastIndex)
                                selectedPointIndex = idx
                                tryAwaitRelease()
                                selectedPointIndex = null
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // 左侧预留纵轴刻度区，绘图区整体右移
                    val axisLeft = axisGutterDp.toPx()
                    val paddingBottom = 26f
                    val paddingTop = 16f
                    val plotWidth = w - axisLeft
                    val plotHeight = h - paddingTop - paddingBottom
                    val stepX = if (points.size > 1) plotWidth / (points.size - 1) else plotWidth

                    val path = Path()
                    val fillPath = Path()

                    val pointCoords = points.mapIndexed { index, point ->
                        val x = axisLeft + index * stepX
                        val normalized = (point.netWorthCents - minVal).toFloat() / valueSpan.toFloat()
                        val y = paddingTop + (1f - normalized) * plotHeight
                        Offset(x, y)
                    }

                    pointCoords.forEachIndexed { i, pt ->
                        if (i == 0) {
                            path.moveTo(pt.x, pt.y)
                            fillPath.moveTo(pt.x, h - paddingBottom)
                            fillPath.lineTo(pt.x, pt.y)
                        } else {
                            path.lineTo(pt.x, pt.y)
                            fillPath.lineTo(pt.x, pt.y)
                        }
                    }

                    if (pointCoords.isNotEmpty()) {
                        val last = pointCoords.last()
                        fillPath.lineTo(last.x, h - paddingBottom)
                        fillPath.close()

                        // 渐变面积填充
                        drawPath(fillPath, gradientBrush)

                        // 折线
                        drawPath(
                            path,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 绘制探针参考虚线
                        selectedPointIndex?.let { selIdx ->
                            if (selIdx in pointCoords.indices) {
                                val probePt = pointCoords[selIdx]
                                drawLine(
                                    color = lineColor.copy(alpha = 0.45f),
                                    start = Offset(probePt.x, paddingTop),
                                    end = Offset(probePt.x, h - paddingBottom),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                            }
                        }

                        // 绘制节点圆心（外圈 primary，内圈 surface）
                        pointCoords.forEachIndexed { idx, pt ->
                            val isSelected = (idx == selectedPointIndex)
                            val radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx()
                            drawCircle(
                                color = dotCenterColor,
                                radius = radius,
                                center = pt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = radius,
                                center = pt,
                                style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.5.dp.toPx())
                            )
                        }
                    }

                    // —— 纵轴虚线网格 + 刻度值 ——
                    axisTextPaint.color = scheme.outline.toArgb()
                    axisTextPaint.textSize = 9.5.sp.toPx()
                    axisTicks.forEach { tick ->
                        val normalized = (tick - minVal).toFloat() / valueSpan.toFloat()
                        val y = paddingTop + (1f - normalized) * plotHeight
                        drawLine(
                            color = scheme.outline.copy(alpha = 0.16f),
                            start = Offset(axisLeft, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        if (!isPrivacyMode) {
                            axisTextPaint.textAlign = android.graphics.Paint.Align.RIGHT
                            val fm = axisTextPaint.fontMetrics
                            val baseline = y - (fm.ascent + fm.descent) / 2f
                            drawContext.canvas.nativeCanvas.drawText(
                                formatAxisValueLabel(tick),
                                axisLeft - 6.dp.toPx(),
                                baseline,
                                axisTextPaint
                            )
                        }
                    }

                    // —— 横轴月份刻度：对齐数据点实际位置，点过密时隔点显示 ——
                    axisTextPaint.textAlign = android.graphics.Paint.Align.CENTER
                    points.forEachIndexed { index, point ->
                        val show = when {
                            points.size <= 6 -> true
                            points.size <= 12 -> index % 2 == 0 || index == points.lastIndex
                            else -> index % 4 == 0 || index == points.lastIndex
                        }
                        if (show) {
                            val cx = pointCoords[index].x
                            val halfWidth = axisTextPaint.measureText(point.label) / 2f
                            val minTextX = halfWidth + 2f
                            val textX = cx.coerceIn(minTextX, (w - halfWidth - 2f).coerceAtLeast(minTextX))
                            drawContext.canvas.nativeCanvas.drawText(
                                point.label,
                                textX,
                                h - 6.dp.toPx(),
                                axisTextPaint
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 走势图纵轴刻度值格式：大额以 亿/万 缩写（保留 1 位小数并去掉 .0 尾巴），
 * 小额直接以整数元显示；与卡片顶部的金额文案口径一致。
 */
private fun formatAxisValueLabel(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val c = abs(cents)
    val body = when {
        c >= 10_000_000_000L -> formatAxisUnit(c, 10_000_000_000L, "亿")
        c >= 1_000_000L -> formatAxisUnit(c, 1_000_000L, "万")
        else -> (c / 100L).toString()
    }
    return sign + body
}

private fun formatAxisUnit(cents: Long, unitCents: Long, unitLabel: String): String {
    val tenths = cents * 10 / unitCents
    val integerPart = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0L) {
        "${integerPart}${unitLabel}"
    } else {
        "${integerPart}.${fraction}${unitLabel}"
    }
}
