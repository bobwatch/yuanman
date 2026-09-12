package com.yuanman.app.ui.screens.panorama

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanman.app.ui.components.AppHeaderSurface

/**
 * 资产全景二级页 ——「我的钱整体处于什么状态」
 *
 * 页面架构按「三维递进」全景仪表盘组织：
 *  1. 资本与负债中枢（净资产主读数 24sp + 环比变动 + 资产负债对称仪表盘 + 负债率指示）
 *  2. 资产配置（按类别分组，默认展开组内子账户，点击可折叠）
 *  3. 资金安全垫（抗风险跑道月数 + 0-24月标尺 + 现金与月均开销说明）
 *  4. 净值走势（周期切换 + Canvas 手绘面积折线 + 横纵轴刻度 + 触摸滑动探针）
 *
 * 全局隐私眼睛紧贴总资产金额之后，点击直接切换全局持久化隐私态。
 */
@Composable
fun AssetPanoramaScreen(
    viewModel: AssetPanoramaViewModel,
    onBack: () -> Unit,
    onNavigateToAccount: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            AppHeaderSurface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "资产全景",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 资本与负债中枢（当前净资产、总资产、待还负债与负债率）
            item {
                CapitalBalanceHeroCard(
                    netWorthCents = uiState.netWorthCents,
                    totalAssetCents = uiState.totalAssetCents,
                    totalDebtCents = uiState.totalDebtCents,
                    deltaCents = uiState.monthOverMonthDeltaCents,
                    deltaRatio = uiState.monthOverMonthDeltaRatio,
                    debtToAssetRatio = uiState.debtToAssetRatio,
                    isPrivacyMode = uiState.isPrivacyMode,
                    onTogglePrivacy = { viewModel.togglePrivacyMode() }
                )
            }

            // 2. 资产配置（按类别分组，默认展开组内账户；未做自定义分组时也以「未分组」呈现全部账户）
            if (uiState.allocationGroups.isNotEmpty()) {
                item(key = "panorama_allocation") {
                    AssetAllocationCard(
                        allocationGroups = uiState.allocationGroups,
                        isPrivacyMode = uiState.isPrivacyMode,
                        onAccountClick = { accountId ->
                            onNavigateToAccount?.invoke(accountId)
                        }
                    )
                }
            }

            // 3. 资金安全垫（抗风险生存月数评估）
            item {
                SafetyCushionCard(
                    cushion = uiState.safetyCushion,
                    isPrivacyMode = uiState.isPrivacyMode
                )
            }

            // 4. 净资产历史演进曲线（趋势回顾 + 触摸探针）
            item {
                NetWorthTrendCard(
                    points = uiState.trendPoints,
                    selectedPeriod = uiState.selectedPeriod,
                    onSelectPeriod = { viewModel.selectTrendPeriod(it) },
                    isPrivacyMode = uiState.isPrivacyMode
                )
            }
        }
    }
}
