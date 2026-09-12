@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yuanman.app.ui.screens.account

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.yuanman.app.ui.components.AppHeaderSurface
import com.yuanman.app.utils.MoneyUtils

/** 待核对警示琥珀色 —— 与首页预算条同值（HomeScreen.kt 0xFFFF9800），暂不抽主题扩展 token */
private val WarnAmberColor = Color(0xFFFF9800)

/**
 * 净资产快照卡。
 *
 * 布局（redesign v0.0.4 §hero，产品确认 B 方案）：
 *  - 行1：● 净资产标题（右上不再有三点菜单）；
 *  - 行2：净资产主数字 + 紧跟其后的小眼睛（隐私开关内联，长数字自动降字号防溢出）；
 *  - 行3：「总资产 ¥x」+ 行尾两个入口胶囊：发薪分配 / 账户核对（替代原 ⋮ 菜单项）；
 *  - 行4：待还负债（仅 ≠0 时出现，赤红绝对值，独立成行避免与入口拥挤）；
 *  - 行5：资金分布彩条（正余额账户 ≥2 时）。
 * 纯展示组件，无 VM 依赖，数据全部走参数。
 */
@Composable
fun AccountSnapshotHero(
    totalBalanceCents: Long,
    totalAssetCents: Long,
    totalDebtCents: Long,
    distributionSegments: List<DistributionSegment>,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onOpenPaycheckRun: (() -> Unit)? = null,
    onOpenAccountReconcile: (() -> Unit)? = null,
    onOpenAssetPanorama: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val heroShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomEnd = 22.dp,
        bottomStart = 22.dp
    )

    AppHeaderSurface(
        modifier = modifier,
        shape = heroShape,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)
        ) {
            // 行1：标题（主色圆点 + 净资产）+ 右侧 [资产全景 ›] 入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = onOpenAssetPanorama != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenAssetPanorama?.invoke()
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "净资产",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onOpenAssetPanorama != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenAssetPanorama()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "资产全景",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "进入资产全景",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 行2：主数字区（明文 / 隐私占位间 160ms 原位交叉淡化）+ 内联小眼睛
            Row(verticalAlignment = Alignment.Bottom) {
                Crossfade(
                    targetState = isPrivacyMode,
                    animationSpec = tween(durationMillis = 160),
                    label = "netAmountPrivacyCrossfade",
                    modifier = Modifier.weight(1f, fill = false)
                ) { privacy ->
                    if (privacy) {
                        Text(
                            text = "¥ ••••",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 35.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        NetAmountRow(cents = totalBalanceCents)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                PrivacyEyeButton(
                    isPrivacyMode = isPrivacyMode,
                    onToggle = onTogglePrivacy,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 行3：总资产 + 行尾入口胶囊（无注入回调则整组不渲染）
            AssetAndEntryRow(
                totalAssetCents = totalAssetCents,
                isPrivacyMode = isPrivacyMode,
                onOpenPaycheckRun = onOpenPaycheckRun,
                onOpenAccountReconcile = onOpenAccountReconcile
            )

            // 行4：待还负债独立成行（仅 ≠0），赤红绝对值呈现
            if (totalDebtCents < 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                DebtSummaryRow(debtCents = totalDebtCents, isPrivacyMode = isPrivacyMode)
            }

            // 行5：分布彩条，仅正余额账户 >= 2（分段 >= 2）时渲染，隐私态保留
            if (distributionSegments.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                DistributionBarRow(segments = distributionSegments)
            }
        }
    }
}

/** 隐私小眼睛：紧跟净资产金额的 26dp 命中圆区，图标随隐私态切换 */
@Composable
private fun PrivacyEyeButton(
    isPrivacyMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isPrivacyMode) "显示金额" else "隐藏金额",
            tint = colors.outline,
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * 行2 主数字（36sp 档基线；整数位数多时整体等比降档，保证与内联小眼睛同行不溢出）：
 * ¥ 前缀 + 整数 + 小数分段；负值整体赤红（-¥ 前缀），正值整数 onSurface、小数弱化。
 */
@Composable
private fun NetAmountRow(cents: Long) {
    val colors = MaterialTheme.colorScheme
    val isNegative = cents < 0L
    val absCents = if (isNegative) -cents else cents
    val parts = MoneyUtils.centsToYuanString(absCents, withGrouping = true).split(".")
    val integerPart = parts.getOrNull(0) ?: "0"
    val decimalPart = parts.getOrNull(1) ?: "00"

    // 整数位数 ≥8（约 ¥9,999 万）起降档，为内联小眼睛让位；基准 36sp
    val baseSp = when {
        integerPart.length >= 13 -> 20f
        integerPart.length >= 10 -> 25f
        integerPart.length >= 8 -> 29f
        else -> 36f
    }

    val mainColor = if (isNegative) colors.error else colors.onSurface
    val decimalColor = if (isNegative) colors.error else colors.onSurfaceVariant.copy(alpha = 0.85f)

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (isNegative) "-¥" else "¥",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (baseSp * 0.61f).sp
            ),
            color = mainColor,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        Text(
            text = integerPart,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = baseSp.sp,
                letterSpacing = (-0.4f).sp
            ),
            color = mainColor
        )
        Text(
            text = ".$decimalPart",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = (baseSp * 0.55f).sp
            ),
            color = decimalColor,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

/**
 * 行3：总资产（左）+ 发薪分配 / 账户核对两个入口胶囊（行尾）。
 * 左侧金额区占据弹性宽度，入口胶囊恒定贴在行右侧，不随金额长短漂移；
 * 金额极端过长时在区内省略（罕见场景），负债已移出本行（见 DebtSummaryRow）。
 */
@Composable
private fun AssetAndEntryRow(
    totalAssetCents: Long,
    isPrivacyMode: Boolean,
    onOpenPaycheckRun: (() -> Unit)?,
    onOpenAccountReconcile: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左组（含金额）吃掉所有弹性宽度：金额无论长短，胶囊始终锚定行右侧
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "总资产",
                style = labelStyle,
                color = colors.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPrivacyMode) {
                    "¥ ••••"
                } else {
                    "¥" + MoneyUtils.centsToYuanString(totalAssetCents, withGrouping = true)
                },
                style = valueStyle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onOpenPaycheckRun != null || onOpenAccountReconcile != null) {
            if (onOpenPaycheckRun != null) {
                HeroEntryPill(
                    text = "发薪分配",
                    icon = Icons.Default.Savings,
                    onClick = onOpenPaycheckRun
                )
            }
            if (onOpenAccountReconcile != null) {
                Spacer(modifier = Modifier.width(6.dp))
                HeroEntryPill(
                    text = "账户核对",
                    icon = Icons.Default.AccountBalanceWallet,
                    onClick = onOpenAccountReconcile
                )
            }
        }
    }
}

/** 行4：待还负债（赤红绝对值；隐私态掩码） */
@Composable
private fun DebtSummaryRow(debtCents: Long, isPrivacyMode: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "待还负债",
            style = labelStyle,
            color = colors.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPrivacyMode) {
                "¥ ••••"
            } else {
                "¥" + MoneyUtils.centsToYuanString(-debtCents, withGrouping = true)
            },
            style = valueStyle,
            color = colors.error,
            maxLines = 1
        )
    }
}

/** hero 行尾入口胶囊：图标 + 短文案，surfaceVariant 底 + 细描边 */
@Composable
private fun HeroEntryPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.onSurface,
                maxLines = 1
            )
        }
    }
}

/** 行5：资金分布彩条。轨道 surfaceVariant，分段按传入顺序以 percentage 权重着色 */
@Composable
private fun DistributionBarRow(segments: List<DistributionSegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp) // 4dp 高、r3 轨道
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        segments.forEach { seg ->
            Box(
                modifier = Modifier
                    .weight(seg.percentage.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .background(Color(seg.colorHex))
            )
        }
    }
}

/**
 * 待核对提醒条（v0.0.4.7 样式/文案优化）：琥珀细描边 + 时钟底托图标 + 双行文案
 * （主句「N 个账户待核对」+ 副句「已到对账周期 · 点此核对实际余额」）。
 *
 * 出现条件（存在逾期 / 从未对账账户）由编排层决定，本组件只负责渲染；
 * 该条已随账户列表一起滚动（放入 LazyColumn 首个 item），不吸顶。
 * 行尾 (X) 关闭需二次确认「跳过本期核对」——确认逻辑在调用方（AccountScreen）完成。
 */
@Composable
fun ReconcileReminderBanner(
    pendingCount: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(12.dp),
        color = WarnAmberColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, WarnAmberColor.copy(alpha = 0.36f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时钟图标底托：弱琥珀底 + 琥珀时钟，表意“到时间了”
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WarnAmberColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = WarnAmberColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$pendingCount 个账户待核对",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "已到对账周期 · 点此核对实际余额",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            // 关闭 (X)：需调用方二次确认「跳过本期核对」
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭提醒",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
