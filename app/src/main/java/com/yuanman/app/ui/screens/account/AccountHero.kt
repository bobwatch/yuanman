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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 净资产快照卡（设计文档 §3.2 / §4.1 + redesign v0.2 §6.1/§6.3）。
 *
 * 顶部 Header：用共享 AppHeaderSurface 包裹（首页式斜纹 + 柔光 + 1dp 细描边），
 * 贴边全宽、上两角 0、下两角 22dp，与首页顶部卡风格统一；本组件自身仍不含状态栏内边距逻辑以外
 * 的页面结构责任。
 * 内容为「标题行 / 净资产主数字 / 总资产·待还负债说明行 / 分布彩条」四行：
 *  - 标题行右侧为「更多」三点钮（仅当 [onOpenPaycheckRun] / [onOpenAccountReconcile] 任一非空时渲染），
 *    弹出小菜单：发薪分配 / 账户核对；
 *  - 隐私切换小眼睛从右上角移至行 3「总资产」金额右侧内联展示；
 *  - 彩条不配任何图例文字。纯展示组件，无 VM 依赖，数据全部走参数。
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
    modifier: Modifier = Modifier
) {
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
            // 行1：标题（主色圆点 + 净资产）与「更多」菜单钮
            HeroTitleRow(
                onOpenPaycheckRun = onOpenPaycheckRun,
                onOpenAccountReconcile = onOpenAccountReconcile
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 行2：主数字区，明文 / 隐私占位间 160ms 原位交叉淡化，避免跳版感
            Crossfade(
                targetState = isPrivacyMode,
                animationSpec = tween(durationMillis = 160),
                label = "netAmountPrivacyCrossfade"
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

            Spacer(modifier = Modifier.height(10.dp))

            // 行3：总资产 / 待还负债说明（负债为 0 时整行只剩总资产）；小眼睛内联在总资产金额后
            HeroSummaryRow(
                totalAssetCents = totalAssetCents,
                totalDebtCents = totalDebtCents,
                isPrivacyMode = isPrivacyMode,
                onTogglePrivacy = onTogglePrivacy
            )

            // 行4：分布彩条，仅正余额账户 >= 2（分段 >= 2）时渲染，隐私态保留、不渲染图例
            if (distributionSegments.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                DistributionBarRow(segments = distributionSegments)
            }
        }
    }
}

/**
 * 行1：净资产标题行 + 右侧 32dp「更多」圆钮（弹出 DropdownMenu）。
 * 菜单项按回调是否注入决定显示：onOpenPaycheckRun →「发薪分配」、onOpenAccountReconcile →「账户核对」；
 * 两者都为空时整颗按钮不渲染。点击带 haptic。
 */
@Composable
private fun HeroTitleRow(
    onOpenPaycheckRun: (() -> Unit)?,
    onOpenAccountReconcile: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
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
                text = "净资产",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = colors.onSurfaceVariant
            )
        }

        // 「更多」菜单：仅当至少一个动作被导航层注入时出现
        if (onOpenPaycheckRun != null || onOpenAccountReconcile != null) {
            Box {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        moreMenuExpanded = true
                    },
                    shape = CircleShape,
                    color = colors.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false }
                ) {
                    onOpenPaycheckRun?.let { openPaycheck ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "发薪分配",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                moreMenuExpanded = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                openPaycheck()
                            }
                        )
                    }
                    onOpenAccountReconcile?.let { openReconcile ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "账户核对",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                moreMenuExpanded = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                openReconcile()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行2：净资产主数字（36sp 档）分段排版：¥ 前缀 + 整数 + 小数。
 * 金额取绝对值展示，负号置于 ¥ 之前（"-¥12,345.67"）；
 * 负值前缀 / 整数 / 小数整体 error 赤红，正值整数 onSurface、小数 onSurfaceVariant。
 */
@Composable
private fun NetAmountRow(cents: Long) {
    val colors = MaterialTheme.colorScheme
    val isNegative = cents < 0L
    val absCents = if (isNegative) -cents else cents
    val parts = MoneyUtils.centsToYuanString(absCents, withGrouping = true).split(".")
    val integerPart = parts.getOrNull(0) ?: "0"
    val decimalPart = parts.getOrNull(1) ?: "00"

    val mainColor = if (isNegative) colors.error else colors.onSurface
    val decimalColor = if (isNegative) colors.error else colors.onSurfaceVariant.copy(alpha = 0.85f)

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (isNegative) "-¥" else "¥",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = mainColor,
            modifier = Modifier.padding(bottom = 3.dp) // 抬升以对齐主整数的基线
        )
        Text(
            text = integerPart,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = (-0.4f).sp
            ),
            color = mainColor
        )
        Text(
            text = ".$decimalPart",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            ),
            color = decimalColor,
            modifier = Modifier.padding(bottom = 3.dp) // 抬升以对齐主整数的基线
        )
    }
}

/**
 * 行3：总资产 + 待还负债说明行。负债以赤红绝对值呈现。
 * 隐私切换小眼睛内联在「总资产」金额右侧（24dp 命中区，图标随隐私态切换，点击带 haptic）；
 * 隐私态两值掩码均为「¥ ••••」。
 */
@Composable
private fun HeroSummaryRow(
    totalAssetCents: Long,
    totalDebtCents: Long,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasDebt = totalDebtCents < 0L
    val haptic = LocalHapticFeedback.current

    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
    val maskedValue = "¥ ••••"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "总资产",
            style = labelStyle,
            color = colors.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPrivacyMode) {
                maskedValue
            } else {
                "¥" + MoneyUtils.centsToYuanString(totalAssetCents, withGrouping = true)
            },
            style = valueStyle,
            color = colors.onSurface,
            maxLines = 1
        )
        // 内联小眼睛：紧跟总资产金额右侧，不再作为右上角独立按钮
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePrivacy()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isPrivacyMode) "显示金额" else "隐藏金额",
                tint = colors.outline,
                modifier = Modifier.size(14.dp)
            )
        }

        if (hasDebt) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "·",
                style = labelStyle,
                color = colors.outline
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "待还负债",
                style = labelStyle,
                color = colors.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPrivacyMode) {
                    maskedValue
                } else {
                    "¥" + MoneyUtils.centsToYuanString(-totalDebtCents, withGrouping = true)
                },
                style = valueStyle,
                color = colors.error,
                maxLines = 1
            )
        }
    }
}

/** 行4：资金分布彩条。轨道 surfaceVariant，分段按传入顺序以 percentage 权重着色 */
@Composable
private fun DistributionBarRow(segments: List<DistributionSegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp) // 文档 §4.1 行4 规格：4dp 高
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
 * 待核对提醒横条（redesign v0.2 §6.2：横幅 → 紧凑 warning 横条）。
 *
 * 出现条件（存在逾期 / 从未对账账户）由编排层决定，本组件只负责渲染。
 * 组件内部不设任何外边距，横向 16dp 边距与上下间距由调用方统一包裹。
 * 高约 32dp：琥珀警示色（alpha 淡化底 + 细描边）+ 单行主句；主体区域点击升起待核对底包，
 * 行尾 (X) 关闭提醒（本页面实例内不再显示，由调用方以本地状态承接）。
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
        shape = RoundedCornerShape(10.dp),
        color = WarnAmberColor.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, WarnAmberColor.copy(alpha = 0.32f)),
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = WarnAmberColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$pendingCount 个账户待核对",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 关闭 (X)：仅本地隐藏，不再弹出底包
            Box(
                modifier = Modifier
                    .size(26.dp)
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
