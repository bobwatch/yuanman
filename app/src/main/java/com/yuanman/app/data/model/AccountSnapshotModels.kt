package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity

/**
 * 周期对账项目项
 */
data class AccountReconciliationItem(
    val account: AccountEntity,
    val bookBalanceCents: Long,
    val actualBalanceCents: Long,
    val diffCents: Long = actualBalanceCents - bookBalanceCents,
    val isIncluded: Boolean = true
)

/**
 * 周期资产快照概览
 */
data class PeriodSnapshotSummary(
    val periodKey: String,
    val periodName: String,
    val periodType: AccountPeriodType,
    val totalAssetCents: Long,
    val totalDebtCents: Long,
    val netWorthCents: Long,
    val reconciledAt: Long
)

/**
 * 跨周期资产对比汇总
 */
data class AccountPeriodComparison(
    val currentPeriod: PeriodInfo,
    val currentNetWorthCents: Long = 0L,
    val prevNetWorthCents: Long = 0L,
    val netWorthDiffCents: Long = currentNetWorthCents - prevNetWorthCents,
    val netWorthDiffPercent: Float? = if (prevNetWorthCents > 0L) {
        (currentNetWorthCents - prevNetWorthCents).toFloat() / prevNetWorthCents * 100f
    } else null,
    val totalAssetCents: Long = 0L,
    val prevTotalAssetCents: Long = 0L,
    val totalDebtCents: Long = 0L,
    val prevTotalDebtCents: Long = 0L,
    val liquidAssetCents: Long = 0L,
    val investmentAssetCents: Long = 0L,
    val creditDebtCents: Long = 0L,
    val otherAssetCents: Long = 0L,
    val hasReconciledInCurrentPeriod: Boolean = false,
    val historySnapshots: List<PeriodSnapshotSummary> = emptyList()
)
