package com.yuanman.app.ui.screens.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.AccountIconHelper
import com.yuanman.app.data.model.AccountPeriodComparison
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.utils.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 资产与账户统计分析大屏
 *
 * @param onTransferToAccount 去还款/划转到指定账户（由导航层注入：写入账户 Tab 入口的
 * SavedStateHandle 并返回，账户页消费后弹出转账底页——本页 VM 返回时即销毁，不能直接置位其弹窗开关）
 * @param onReconcile 立即对账（同上，由导航层注入并带回账户页消费）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountStatisticsScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    onTransferToAccount: (Long) -> Unit = {},
    onReconcile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val comparison = uiState.comparison
    val accounts = uiState.accounts
    val privacyMode = uiState.privacyMode
    val periodRecords = uiState.periodRecords
    val recentRecords = uiState.recentRecords

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "资产与账户统计",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 核心净资产与财务健康指标（隐私小眼睛位于“总净资产”后）
            NetWorthHealthCard(
                comparison = comparison,
                privacyMode = privacyMode,
                onTogglePrivacy = { viewModel.togglePrivacyMode() }
            )

            // 2. 还款日程（负债与信用账户待还清偿看板）
            CreditDebtRepaymentCard(
                accounts = accounts,
                liquidAssetCents = comparison.liquidAssetCents,
                privacyMode = privacyMode,
                // “去还款”交给导航层注入的回调：写入账户页入口 SavedStateHandle 后返回，
                // 由账户页（其 VM 仍在 back stack 中存活）消费并弹出转账底页
                onTransferToAccount = onTransferToAccount
            )

            // 3. 资金动向（近期收支脉搏与资金流动轨迹）
            CashflowPulseCard(
                periodRecords = periodRecords,
                recentRecords = recentRecords,
                currentPeriodName = comparison.currentPeriod.periodName,
                privacyMode = privacyMode
            )

            // 4. 支出结构（账户支出流向结构与生活角色画像）
            AccountSpendingStoriesCard(
                periodRecords = periodRecords,
                accounts = accounts,
                privacyMode = privacyMode
            )

            // 5. 资产配置结构全景
            AssetAllocationCard(
                comparison = comparison,
                accounts = accounts,
                privacyMode = privacyMode
            )

            // 6. 周期资产与净资产走势
            AssetTrendCard(
                comparison = comparison,
                privacyMode = privacyMode
            )

            // 7. 账户规模排行榜
            AccountRankingCard(
                accounts = accounts,
                totalAssetCents = comparison.totalAssetCents,
                privacyMode = privacyMode
            )

            // 8. 对账记录与健康状态
            ReconciliationHealthCard(
                comparison = comparison,
                // “立即对账”交给导航层注入的回调（写入账户页 SavedStateHandle 并返回，
                // 账户页消费后打开对账工作台），避免在本页 VM 上置位后随销毁丢失
                onReconcile = onReconcile
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 1. 核心净资产与财务状况卡片
 */
@Composable
private fun NetWorthHealthCard(
    comparison: AccountPeriodComparison,
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit
) {
    val netWorth = comparison.currentNetWorthCents
    val totalAsset = comparison.totalAssetCents
    val totalDebt = comparison.creditDebtCents
    val diff = comparison.netWorthDiffCents
    val hasDiff = comparison.historySnapshots.any { snapshot ->
        snapshot.periodKey == comparison.currentPeriod.prevPeriodKey
    }

    val debtRatio = if (totalAsset > 0L) {
        (totalDebt.toDouble() / totalAsset).coerceIn(0.0, 1.0)
    } else if (totalDebt > 0L) 1.0 else 0.0

    val (debtRatioText, debtRatioColor) = when {
        totalDebt == 0L -> Pair("0.0% · 极佳（无负债）", MaterialTheme.colorScheme.primary)
        debtRatio <= 0.25 -> Pair("${String.format("%.1f", debtRatio * 100)}% · 稳健（极低杠杆）", MaterialTheme.colorScheme.primary)
        debtRatio <= 0.50 -> Pair("${String.format("%.1f", debtRatio * 100)}% · 合理（安全区间）", MaterialTheme.colorScheme.secondary)
        else -> Pair("${String.format("%.1f", debtRatio * 100)}% · 偏高（建议关注还款压力）", MaterialTheme.colorScheme.error)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "总净资产",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (privacyMode) "显示金额" else "隐藏金额",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = comparison.currentPeriod.periodName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(netWorth)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (privacyMode) 28.sp else 32.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    // 大字体/超长金额时保持单行（省出右侧环比的空间），避免折行顶乱布局
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasDiff) {
                    val isPositive = diff >= 0
                    val sign = if (isPositive) "+" else "-"
                    val absDiff = if (diff < 0L) -diff else diff
                    val percentStr = comparison.netWorthDiffPercent?.let { "（${if (it >= 0f) "+" else ""}${String.format("%.1f", it)}%）" } ?: ""
                    Text(
                        text = if (privacyMode) "环比 --" else "$sign¥${MoneyUtils.centsToYuanString(absDiff)}$percentStr",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (privacyMode) MaterialTheme.colorScheme.outline else if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(12.dp))

            // 资产与负债指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "总资产",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(totalAsset)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "总负债（待还）",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(totalDebt)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (totalDebt > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 资产负债率指示胶囊
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = debtRatioColor.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(0.6.dp, debtRatioColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = debtRatioColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "资产负债率",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = debtRatioColor
                            )
                        )
                    }
                    Text(
                        text = if (privacyMode) "已隐藏" else debtRatioText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = debtRatioColor
                        )
                    )
                }
            }
        }
    }
}

/**
 * 2. 资产配置结构全景
 */
@Composable
private fun AssetAllocationCard(
    comparison: AccountPeriodComparison,
    accounts: List<AccountEntity>,
    privacyMode: Boolean
) {
    val totalAsset = comparison.totalAssetCents
    // 没有任何资产与负债数据时不再整卡消失：仍渲染卡片骨架并给出一行占位文案
    val hasAssetData = totalAsset > 0L || comparison.creditDebtCents > 0L

    val liquidCents = comparison.liquidAssetCents
    val investCents = comparison.investmentAssetCents
    val otherCents = comparison.otherAssetCents
    val debtCents = comparison.creditDebtCents

    val liquidRatio = if (totalAsset > 0) (liquidCents.toFloat() / totalAsset).coerceIn(0f, 1f) else 0f
    val investRatio = if (totalAsset > 0) (investCents.toFloat() / totalAsset).coerceIn(0f, 1f) else 0f
    val otherRatio = if (totalAsset > 0) (otherCents.toFloat() / totalAsset).coerceIn(0f, 1f) else 0f

    val liquidCount = accounts.count { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.CHECKING }
    val investCount = accounts.count { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.INVESTMENT }
    val otherCount = accounts.count { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.ASSET }
    val debtCount = accounts.count { it.includeInNetWorth && AccountType.fromString(it.type).isLiability }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "资产配置结构",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasAssetData) {
                // 空态占位：沿用页面其他卡片的轻量空态样式
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "暂无资产数据，先添加账户和记账吧",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // 比例条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (liquidRatio > 0f) Box(modifier = Modifier.weight(liquidRatio).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    if (investRatio > 0f) Box(modifier = Modifier.weight(investRatio).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                    if (otherRatio > 0f) Box(modifier = Modifier.weight(otherRatio).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4个维度的详细说明
                AllocationRow(
                    title = "流动资金",
                    subtitle = "日常活期与备用金 · 建议覆盖3-6个月支出",
                    cents = liquidCents,
                    ratio = liquidRatio,
                    count = liquidCount,
                    color = MaterialTheme.colorScheme.primary,
                    privacyMode = privacyMode
                )

                Spacer(modifier = Modifier.height(10.dp))

                AllocationRow(
                    title = "投资理财",
                    subtitle = "基金、理财与股票 · 追求长期稳健增值",
                    cents = investCents,
                    ratio = investRatio,
                    count = investCount,
                    color = MaterialTheme.colorScheme.secondary,
                    privacyMode = privacyMode
                )

                Spacer(modifier = Modifier.height(10.dp))

                AllocationRow(
                    title = "其他资产",
                    subtitle = "公积金、押金与固定资产 · 长期财富储备",
                    cents = otherCents,
                    ratio = otherRatio,
                    count = otherCount,
                    color = MaterialTheme.colorScheme.tertiary,
                    privacyMode = privacyMode
                )

                if (debtCents > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AllocationRow(
                        title = "信用负债",
                        subtitle = "信用卡与消费信贷 · 待还账单",
                        cents = debtCents,
                        ratio = null,
                        count = debtCount,
                        color = MaterialTheme.colorScheme.error,
                        privacyMode = privacyMode
                    )
                }
            }
        }
    }
}

@Composable
private fun AllocationRow(
    title: String,
    subtitle: String,
    cents: Long,
    ratio: Float?,
    count: Int,
    color: Color,
    privacyMode: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (ratio != null) {
                            Text(
                                text = "${(ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = color,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Text(
                            text = "（${count}个账户）",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }

            Text(
                text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(cents)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 3. 周期资产走势图卡片
 */
@Composable
private fun AssetTrendCard(
    comparison: AccountPeriodComparison,
    privacyMode: Boolean
) {
    val snapshots = comparison.historySnapshots.sortedBy { it.periodKey }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "历史资产走势",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (snapshots.isNotEmpty()) "共 ${snapshots.size} 个周期记录" else "暂无记录",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (privacyMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "隐私模式已开启，资产趋势图已隐藏",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (snapshots.size < 2) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "完成 2 次以上周期对账后，将自动绘制净资产成长走势",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val netWorthValues = snapshots.map { it.netWorthCents }
                val assetValues = snapshots.map { it.totalAssetCents }
                val debtValues = snapshots.map { it.totalDebtCents }
                val allValues = netWorthValues + assetValues + debtValues
                val minValue = allValues.minOrNull()?.toDouble() ?: 0.0
                val maxValue = allValues.maxOrNull()?.toDouble() ?: 0.0
                val valueRange = (maxValue - minValue).coerceAtLeast(1.0)
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                val primaryColor = MaterialTheme.colorScheme.primary
                val assetColor = MaterialTheme.colorScheme.tertiary
                val debtColor = MaterialTheme.colorScheme.error

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val paddingH = 8.dp.toPx()
                    val paddingV = 8.dp.toPx()
                    val chartW = (width - paddingH * 2).coerceAtLeast(1f)
                    val chartH = (height - paddingV * 2).coerceAtLeast(1f)

                    // 绘制 3 条水平参考线
                    repeat(3) { index ->
                        val y = paddingV + chartH * (index / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingH, y),
                            end = Offset(width - paddingH, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 曲线绘制辅助
                    fun drawSeries(values: List<Long>, color: Color) {
                        if (values.size < 2) return
                        val stepX = chartW / (values.size - 1)
                        val path = Path()
                        values.forEachIndexed { i, v ->
                            val x = paddingH + i * stepX
                            val normalized = ((v - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                            val y = paddingV + (1f - normalized) * chartH
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path = path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                        values.forEachIndexed { i, v ->
                            val x = paddingH + i * stepX
                            val normalized = ((v - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                            val y = paddingV + (1f - normalized) * chartH
                            drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, y))
                        }
                    }

                    drawSeries(assetValues, assetColor)
                    drawSeries(debtValues, debtColor)
                    drawSeries(netWorthValues, primaryColor)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X 轴标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = snapshots.first().periodKey,
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    )
                    Text(
                        text = snapshots.last().periodKey,
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 图例说明
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem("净资产", primaryColor)
                    LegendItem("总资产", assetColor)
                    LegendItem("总负债", debtColor)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
        )
    }
}

/**
 * 4. 账户规模排行榜
 */
@Composable
private fun AccountRankingCard(
    accounts: List<AccountEntity>,
    totalAssetCents: Long,
    privacyMode: Boolean
) {
    val assetAccounts = accounts
        .filter { !AccountType.fromString(it.type).isLiability }
        .sortedByDescending { it.balanceCents }

    val debtAccounts = accounts
        .filter { AccountType.fromString(it.type).isLiability && it.balanceCents > 0 }
        .sortedByDescending { it.balanceCents }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "账户资金规模排行",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (assetAccounts.isEmpty()) {
                Text(
                    text = "暂无资产账户",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            } else {
                assetAccounts.take(5).forEachIndexed { index, account ->
                    val ratio = if (totalAssetCents > 0L) {
                        (account.balanceCents.toFloat() / totalAssetCents).coerceIn(0f, 1f)
                    } else 0f
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    Icon(
                                        imageVector = AccountIconHelper.getIcon(account.icon),
                                        contentDescription = null,
                                        tint = accColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${(ratio * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 极简微进度条
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .background(accColor)
                                )
                            }
                        }
                    }
                }
            }

            if (debtAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "待还负债排行",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                debtAccounts.take(3).forEach { account ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (privacyMode) "****" else "待还 ¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. 对账记录与健康状态
 */
@Composable
private fun ReconciliationHealthCard(
    comparison: AccountPeriodComparison,
    onReconcile: () -> Unit
) {
    val isReconciled = comparison.hasReconciledInCurrentPeriod

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isReconciled) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isReconciled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isReconciled) "本周期已完成对账" else "本周期尚未对账",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (!isReconciled) {
                    Button(
                        onClick = onReconcile,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("立即对账", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isReconciled) {
                    "账面净资产与各外部账户真实余额已核对一致，快照数据已固化至周期档案。"
                } else {
                    "建议每月固定日期进行周期对账，自动对齐外部账户差异并生成连续资产走势快照。"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
            )
        }
    }
}

/**
 * 2. 还款日程（信用与待还账单清偿看板）
 */
@Composable
private fun CreditDebtRepaymentCard(
    accounts: List<AccountEntity>,
    liquidAssetCents: Long,
    privacyMode: Boolean,
    onTransferToAccount: (Long) -> Unit
) {
    val liabilityAccounts = accounts.filter { AccountType.fromString(it.type).isLiability }
    val debtAccounts = liabilityAccounts.filter { it.balanceCents > 0L }
    val totalDebtCents = debtAccounts.sumOf { it.balanceCents }
    val hasLiabilities = liabilityAccounts.isNotEmpty()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 顶栏标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = if (totalDebtCents > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "还款日程",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = if (privacyMode) "待还 ****" else if (totalDebtCents > 0L) "待还 ¥${MoneyUtils.centsToYuanString(totalDebtCents)}" else "无待还欠款",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (totalDebtCents > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasLiabilities) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "暂无信用或负债账户，名下资金均为纯资产，财务结构非常健康！",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (totalDebtCents == 0L) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "所有信用与借贷账户均已结清",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "当前无还款压力，合理利用信用卡免息期有助于打理现金流。",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                            )
                        }
                    }
                }
            } else {
                // 活期流动资金覆盖评估
                val isCovered = liquidAssetCents >= totalDebtCents
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCovered) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isCovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCovered) {
                                "活期储备充足（${if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(liquidAssetCents)}"}），可 100% 覆盖当前欠款"
                            } else {
                                "待还欠款超出活期储备，建议提前备足资金避免逾期"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCovered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 各负债账户项
                liabilityAccounts.forEach { acc ->
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(acc.colorHex.ifBlank { "#D32F2F" }))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.error
                    }

                    val dueMatch = Regex("(\\d{1,2})[号日]").find(acc.remark)?.value
                    val dueHint = if (dueMatch != null) "每月 $dueMatch 还款" else "信用卡 / 信用借贷"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(accColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AccountIconHelper.getIcon(acc.icon),
                                        contentDescription = null,
                                        tint = accColor,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    )
                                    Text(
                                        text = dueHint,
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = when {
                                            acc.balanceCents > 0L -> if (privacyMode) "****" else "待还 ¥${MoneyUtils.centsToYuanString(acc.balanceCents)}"
                                            acc.balanceCents == 0L -> "已结清"
                                            else -> if (privacyMode) "****" else "溢缴 ¥${MoneyUtils.centsToYuanString(-acc.balanceCents)}"
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                acc.balanceCents > 0L -> MaterialTheme.colorScheme.error
                                                acc.balanceCents == 0L -> MaterialTheme.colorScheme.outline
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (acc.balanceCents > 0L) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable { onTransferToAccount(acc.id) }
                                    ) {
                                        Text(
                                            text = "去还款",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
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
}

/**
 * 3. 资金动向（近期收支脉搏与资金流动轨迹）
 */
@Composable
private fun CashflowPulseCard(
    periodRecords: List<RecordWithCategory>,
    recentRecords: List<RecordWithCategory>,
    currentPeriodName: String,
    privacyMode: Boolean
) {
    val nonAdjPeriodRecords = periodRecords.filter { !it.record.isAdjustment }
    val totalIncome = nonAdjPeriodRecords.filter { it.record.type == "INCOME" }.sumOf { it.record.amount }
    val totalExpense = nonAdjPeriodRecords.filter { it.record.type == "EXPENSE" }.sumOf { it.record.amount }
    val netCashflow = totalIncome - totalExpense
    val isPositiveFlow = netCashflow >= 0L
    val flowSign = if (isPositiveFlow) "+" else "-"
    val absFlow = if (netCashflow < 0L) -netCashflow else netCashflow

    // 近期动态流 (前 5 笔)
    val displayRecent = recentRecords.take(5)
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapCalls,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "资金动向",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = currentPeriodName,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 本期现金流看板
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "本期总流入", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(totalIncome)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                            // 三列均分等宽，金额过长时省略号收尾，避免挤占相邻列
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "本期总流出", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(totalExpense)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(text = "净现金流", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (privacyMode) "****" else "$flowSign¥${MoneyUtils.centsToYuanString(absFlow)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPositiveFlow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 资金流动事件
            Text(
                text = "近期资金流动事件",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (displayRecent.isEmpty()) {
                Text(
                    text = "近期暂无资金收支记录",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                displayRecent.forEach { item ->
                    val r = item.record
                    val isInc = r.type == "INCOME"
                    val isTrans = r.type == "TRANSFER"
                    val timeStr = remember(r.recordTime) { dateFormat.format(Date(r.recordTime)) }
                    val accountTag = r.paymentMethod.ifBlank { "账户资金" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isInc) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isInc) Icons.Default.ArrowDownward else if (isTrans) Icons.Default.SwapHoriz else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isInc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.category?.name ?: if (isTrans) "账户转账" else "日常收支",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = accountTag,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (r.remark.isNotBlank()) "${r.remark} · $timeStr" else timeStr,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = if (privacyMode) "****" else "${if (isInc) "+" else "-"}¥${MoneyUtils.centsToYuanString(r.amount)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isInc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. 支出结构（账户支出流向结构与生活角色画像）
 */
@Composable
private fun AccountSpendingStoriesCard(
    periodRecords: List<RecordWithCategory>,
    accounts: List<AccountEntity>,
    privacyMode: Boolean
) {
    val expenseRecords = periodRecords.filter { it.record.type == "EXPENSE" && !it.record.isAdjustment }
    val totalExpense = expenseRecords.sumOf { it.record.amount }

    // 按分类聚合
    val categoryTotals = expenseRecords.groupBy { it.category?.name ?: "未分类" }
        .mapValues { entry -> entry.value.sumOf { it.record.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(4)

    // 建立 accountId -> account name 字典
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val accountExpenses = expenseRecords.groupBy { r ->
        r.record.accountId?.let { accountMap[it]?.name } ?: r.record.paymentMethod.ifBlank { "其他账户" }
    }
        .mapValues { entry ->
            val total = entry.value.sumOf { it.record.amount }
            val topCat = entry.value.groupBy { it.category?.name ?: "一般开支" }
                .maxByOrNull { it.value.sumOf { r -> r.record.amount } }?.key ?: "一般开支"
            Pair(total, topCat)
        }
        .toList()
        .sortedByDescending { it.second.first }
        .take(3)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "支出结构",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = if (privacyMode) "总支出 ****" else "总支出 ¥${MoneyUtils.centsToYuanString(totalExpense)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (expenseRecords.isEmpty() || totalExpense == 0L) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "本周期暂无支出消费，继续保持节流好习惯！",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // 1. 分类流向结构
                Text(
                    text = "主要支出领域",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                )

                Spacer(modifier = Modifier.height(6.dp))

                categoryTotals.forEach { (catName, amount) ->
                    val ratio = (amount.toDouble() / totalExpense).toFloat().coerceIn(0f, 1f)
                    val percentStr = String.format("%.1f", ratio * 100)

                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            )
                            Text(
                                text = if (privacyMode) "****（$percentStr%）" else "¥${MoneyUtils.centsToYuanString(amount)}（$percentStr%）",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 账户消费场景画像与故事
                Text(
                    text = "账户支出分布",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                )

                Spacer(modifier = Modifier.height(6.dp))

                accountExpenses.forEach { (accName, data) ->
                    val (amount, topCat) = data
                    val ratio = (amount.toDouble() / totalExpense).toFloat().coerceIn(0f, 1f)
                    val percentStr = String.format("%.0f", ratio * 100)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = accName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "承载 $percentStr% 支出",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "主攻「$topCat」领域消费，是该场景的主力通道",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                )
                            }

                            Text(
                                text = if (privacyMode) "****" else "¥${MoneyUtils.centsToYuanString(amount)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
