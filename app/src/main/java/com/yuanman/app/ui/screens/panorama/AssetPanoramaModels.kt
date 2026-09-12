package com.yuanman.app.ui.screens.panorama

import com.yuanman.app.data.model.SafetyCushionResult

/**
 * 走势周期枚举
 */
enum class PanoramaTrendPeriod(val label: String, val monthsCount: Int) {
    HALF_YEAR("近6月", 6),
    ONE_YEAR("近1年", 12),
    ALL("全部", 36)
}

/**
 * 历史资产走势点
 */
data class NetWorthTrendPoint(
    val year: Int,
    val month: Int,
    val label: String,          // 如 "8月" 或 "24/08"
    val netWorthCents: Long,    // 净资产
    val totalAssetCents: Long,  // 正资产
    val totalDebtCents: Long    // 负债（负数）
)

/**
 * 单个账户在配置中的呈现
 */
data class AssetAccountItem(
    val id: Long,
    val name: String,
    val label: String,
    val iconName: String,
    val colorHex: Long,
    val balanceCents: Long,
    val ratio: Float            // 占所属分类或总资产的比例 (0.0 ~ 1.0)
)

/**
 * 资产配置结构分组
 */
data class AssetAllocationGroup(
    val label: String,           // 用户定义的类型标签，如「流动活期」「理财储蓄」
    val totalCents: Long,        // 组净值/总值
    val ratio: Float,            // 占总正资产的比例
    val accounts: List<AssetAccountItem>
)

/**
 * 资产全景页面 UI 状态
 */
data class AssetPanoramaUiState(
    val isLoading: Boolean = true,
    val isPrivacyMode: Boolean = false,
    // ---- 1. 净资产大盘 ----
    val netWorthCents: Long = 0L,
    val totalAssetCents: Long = 0L,
    val totalDebtCents: Long = 0L,              // 负数存储
    val monthOverMonthDeltaCents: Long = 0L,     // 环比增减额
    val monthOverMonthDeltaRatio: Float? = null, // 环比增减百分比
    val debtToAssetRatio: Float = 0f,            // 负债率 (0.0 ~ 1.0)
    // ---- 2. 净值演进走势 ----
    val selectedPeriod: PanoramaTrendPeriod = PanoramaTrendPeriod.HALF_YEAR,
    val trendPoints: List<NetWorthTrendPoint> = emptyList(),
    // ---- 3. 资产配置结构 ----
    val allocationGroups: List<AssetAllocationGroup> = emptyList(),
    // ---- 4. 资金安全垫 ----
    val safetyCushion: SafetyCushionResult = SafetyCushionResult.collecting()
)
