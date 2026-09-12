package com.yuanman.app.ui.screens.panorama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.SafetyCushionCalculator
import com.yuanman.app.data.model.SafetyCushionResult
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar

private data class RawAccount(
    val id: Long,
    val name: String,
    val label: String,
    val iconName: String,
    val colorHex: Long,
    val openingBalanceCents: Long,
    val balanceCents: Long,
    val inCents: Long,
    val outCents: Long,
    val sortOrder: Int
)

class AssetPanoramaViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(PanoramaTrendPeriod.HALF_YEAR)
    val selectedPeriod: StateFlow<PanoramaTrendPeriod> = _selectedPeriod.asStateFlow()

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val currentYear = currentYearMonth.first
    private val currentMonth = currentYearMonth.second

    // 监听当月流水（与账户页保持口径一致）
    private val recordsOfMonth = recordRepository.getRecordsByMonth(currentYear, currentMonth)

    // 全量流水（用于回溯多月历史净值曲线与计算 90 天真实开销安全垫）
    private val allRecordsFlow = recordRepository.getAllRecords()

    val uiState: StateFlow<AssetPanoramaUiState> = combine(
        preferencesRepository.accountsData,
        preferencesRepository.privacyMode,
        recordsOfMonth,
        allRecordsFlow,
        _selectedPeriod
    ) { accountsJson, privacyMode, monthRecords, allRecords, period ->
        assembleUiState(
            accountsJson = accountsJson,
            isPrivacyMode = privacyMode,
            currentMonthRecords = monthRecords,
            allRecords = allRecords,
            period = period
        )
    }
        // 整库回溯计算（净值曲线/90天开销）与 JSON 解析移出主线程；stateIn 收集仍回到主线程
        .flowOn(Dispatchers.Default)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssetPanoramaUiState(isLoading = true)
    )

    fun togglePrivacyMode() {
        viewModelScope.launch {
            val current = uiState.value.isPrivacyMode
            preferencesRepository.setPrivacyMode(!current)
        }
    }

    fun selectTrendPeriod(period: PanoramaTrendPeriod) {
        _selectedPeriod.value = period
    }

    private fun assembleUiState(
        accountsJson: String?,
        isPrivacyMode: Boolean,
        currentMonthRecords: List<RecordWithCategory>,
        allRecords: List<RecordWithCategory>,
        period: PanoramaTrendPeriod
    ): AssetPanoramaUiState {
        val rawAccounts = parseAccounts(accountsJson)

        // 1. 结合当月流水丰富账户实时余额（与 AccountViewModel 相同的口径）
        val accounts = rawAccounts.map { acc ->
            var monthIn = 0L
            var monthOut = 0L
            currentMonthRecords.forEach { rw ->
                val r = rw.record
                if (methodMatchesAccount(r.paymentMethod, acc.name)) {
                    if (r.type == "INCOME") monthIn += r.amount
                    else if (r.type == "EXPENSE") monthOut += r.amount
                }
            }
            val finalIn = if (monthIn > 0L) monthIn else acc.inCents
            val finalOut = if (monthOut > 0L) monthOut else acc.outCents
            val effectiveBalance = acc.openingBalanceCents + finalIn - finalOut
            acc.copy(
                inCents = finalIn,
                outCents = finalOut,
                balanceCents = effectiveBalance
            )
        }

        // 2. 净资产大盘：总资产、待还负债、净值
        val positiveAccounts = accounts.filter { it.balanceCents > 0L }
        val negativeAccounts = accounts.filter { it.balanceCents < 0L }
        val totalAssetCents = positiveAccounts.sumOf { it.balanceCents }
        val totalDebtCents = negativeAccounts.sumOf { it.balanceCents } // 负数
        val netWorthCents = totalAssetCents + totalDebtCents

        // 负债率
        val debtRatio = if (totalAssetCents > 0L) {
            (-totalDebtCents).toFloat() / totalAssetCents.toFloat()
        } else {
            0f
        }

        // 环比上月末变动：
        // 本月当月累计的净变动 = 当月总入 - 当月总出
        val curMonthIn = currentMonthRecords.filter { it.record.type == "INCOME" }.sumOf { it.record.amount }
        val curMonthOut = currentMonthRecords.filter { it.record.type == "EXPENSE" }.sumOf { it.record.amount }
        val momDeltaCents = curMonthIn - curMonthOut
        val lastMonthNetWorth = netWorthCents - momDeltaCents
        val momRatio = if (lastMonthNetWorth > 0L) {
            momDeltaCents.toFloat() / lastMonthNetWorth.toFloat()
        } else {
            null
        }

        // 3. 资产配置结构（按 label 分组聚合）
        val allocationGroups = positiveAccounts
            .groupBy { it.label.trim().ifBlank { "未分组" } }
            .map { (groupLabel, groupAccounts) ->
                val groupTotal = groupAccounts.sumOf { it.balanceCents }
                val groupRatio = if (totalAssetCents > 0L) {
                    groupTotal.toFloat() / totalAssetCents.toFloat()
                } else 0f

                val items = groupAccounts.map { a ->
                    AssetAccountItem(
                        id = a.id,
                        name = a.name,
                        label = a.label,
                        iconName = a.iconName,
                        colorHex = a.colorHex,
                        balanceCents = a.balanceCents,
                        ratio = if (totalAssetCents > 0L) a.balanceCents.toFloat() / totalAssetCents.toFloat() else 0f
                    )
                }.sortedByDescending { it.balanceCents }

                AssetAllocationGroup(
                    label = groupLabel,
                    totalCents = groupTotal,
                    ratio = groupRatio,
                    accounts = items
                )
            }.sortedByDescending { it.totalCents }

        // 4. 资金安全垫评估
        // 活期/流动资产 = 正向资产中排除明显属于长期投资或定存的账户
        val liquidAssetCents = positiveAccounts
            .filterNot {
                val lb = it.label.trim()
                lb.contains("理财") || lb.contains("投资") || lb.contains("定期") || lb.contains("股票")
            }
            .sumOf { it.balanceCents }
            .coerceAtLeast(0L)

        // 近 90 天总开销
        val nowMs = System.currentTimeMillis()
        val ninetyDaysAgo = nowMs - 90L * 24 * 3600 * 1000L
        val recentRecords = allRecords.filter { it.record.recordTime >= ninetyDaysAgo }
        val threeMonthExpenses = recentRecords
            .filter { it.record.type == "EXPENSE" }
            .sumOf { it.record.amount }

        val earliestRecordTime = allRecords.minOfOrNull { it.record.recordTime } ?: nowMs
        val recordedDays = ((nowMs - earliestRecordTime) / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)

        val safetyCushion = SafetyCushionCalculator.calculate(
            liquidAssetCents = liquidAssetCents,
            currentDebtCents = totalDebtCents,
            recentExpensesCents = threeMonthExpenses,
            recordedDays = recordedDays
        )

        // 5. 历史净值曲线 points
        val trendPoints = calculateTrendPoints(
            currentNetWorth = netWorthCents,
            currentTotalAsset = totalAssetCents,
            currentTotalDebt = totalDebtCents,
            allRecords = allRecords,
            period = period
        )

        return AssetPanoramaUiState(
            isLoading = false,
            isPrivacyMode = isPrivacyMode,
            netWorthCents = netWorthCents,
            totalAssetCents = totalAssetCents,
            totalDebtCents = totalDebtCents,
            monthOverMonthDeltaCents = momDeltaCents,
            monthOverMonthDeltaRatio = momRatio,
            debtToAssetRatio = debtRatio,
            selectedPeriod = period,
            trendPoints = trendPoints,
            allocationGroups = allocationGroups,
            safetyCushion = safetyCushion
        )
    }

    private fun calculateTrendPoints(
        currentNetWorth: Long,
        currentTotalAsset: Long,
        currentTotalDebt: Long,
        allRecords: List<RecordWithCategory>,
        period: PanoramaTrendPeriod
    ): List<NetWorthTrendPoint> {
        val count = period.monthsCount
        val cal = Calendar.getInstance()
        val points = mutableListOf<NetWorthTrendPoint>()

        // 按照从当前月往前推 count 个月
        for (i in (count - 1) downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.MONTH, -i)
            }
            val year = targetCal.get(Calendar.YEAR)
            val month = targetCal.get(Calendar.MONTH) + 1

            // 该月月末时间戳
            val endOfMonth = DateTimeUtils.getMonthEndTimestamp(year, month)
            val isCurrentMonth = (i == 0)

            val netWorthAtMonthEnd = if (isCurrentMonth) {
                currentNetWorth
            } else {
                // 扣除在月末之后发生的所有净收益
                val netAfter = allRecords.filter { it.record.recordTime > endOfMonth }.sumOf { rw ->
                    when (rw.record.type) {
                        "INCOME" -> rw.record.amount
                        "EXPENSE" -> -rw.record.amount
                        else -> 0L
                    }
                }
                currentNetWorth - netAfter
            }

            val label = if (count > 12) {
                "${year % 100}/${month}月"
            } else {
                "${month}月"
            }

            points.add(
                NetWorthTrendPoint(
                    year = year,
                    month = month,
                    label = label,
                    netWorthCents = netWorthAtMonthEnd,
                    totalAssetCents = (netWorthAtMonthEnd - currentTotalDebt).coerceAtLeast(0L),
                    totalDebtCents = currentTotalDebt
                )
            )
        }
        return points
    }

    private fun parseAccounts(accountsJson: String?): List<RawAccount> {
        if (accountsJson.isNullOrBlank()) return emptyList()
        val list = mutableListOf<RawAccount>()
        runCatching {
            val array = JSONArray(accountsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RawAccount(
                        id = obj.optLong("id", i.toLong() + 1L),
                        name = obj.optString("name", "账户"),
                        label = obj.optString("label", ""),
                        iconName = obj.optString("iconName", "wallet"),
                        colorHex = obj.optLong("colorHex", 0xFF059669L),
                        openingBalanceCents = obj.optLong("openingBalanceCents", 0L),
                        balanceCents = obj.optLong("balanceCents", 0L),
                        inCents = obj.optLong("inCents", 0L),
                        outCents = obj.optLong("outCents", 0L),
                        sortOrder = obj.optInt("sortOrder", i + 1)
                    )
                )
            }
        }
        return list
    }

    private fun methodMatchesAccount(methodRaw: String, accountName: String): Boolean {
        val method = methodRaw.trim()
        return method.isNotBlank() && (
            method == accountName ||
                accountName.contains(method, ignoreCase = true) ||
                (method.contains("微信") && accountName.contains("微信")) ||
                (method.contains("支付宝") && accountName.contains("支付宝")) ||
                ((method.contains("卡") || method.contains("银行")) && (accountName.contains("行") || accountName.contains("卡"))) ||
                (method.contains("现金") && accountName.contains("现金"))
            )
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val recordRepository: RecordRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AssetPanoramaViewModel::class.java)) {
                return AssetPanoramaViewModel(preferencesRepository, recordRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
