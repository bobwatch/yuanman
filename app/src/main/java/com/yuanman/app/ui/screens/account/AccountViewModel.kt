package com.yuanman.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AccountUiModel(
    val id: Long,
    val name: String,
    val label: String, // 账户类型：用户自定义字符串，可为空（空即未设置类型），代码不读其内容做分支
    val iconName: String,
    val colorHex: Long,
    val openingBalanceCents: Long,
    val balanceCents: Long,
    val inCents: Long = 0L,
    val outCents: Long = 0L,
    val lastReconciledAt: Long? = null,
    val lastReconciledDiffCents: Long? = null,
    val sortOrder: Int = 0
)

data class AccountGroupUiModel(
    val label: String,
    val accounts: List<AccountUiModel>,
    val groupTotalCents: Long
)

data class DistributionSegment(
    val label: String,
    val amountCents: Long,
    val colorHex: Long,
    val percentage: Float
)

data class AccountUiState(
    val accounts: List<AccountUiModel> = emptyList(),
    val groups: List<AccountGroupUiModel> = emptyList(),
    val totalBalanceCents: Long = 0L, // 净资产 = 总资产 - 待还负债
    val totalAssetCents: Long = 0L,   // 正向资金池总计
    val totalDebtCents: Long = 0L,    // 待还负债池总计（以负数存储）
    val distributionSegments: List<DistributionSegment> = emptyList(),
    val monthInCents: Long = 0L,
    val monthOutCents: Long = 0L,
    val monthNetCents: Long = 0L,
    val isPrivacyMode: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    // ---- v0.3：攒钱计划 & 发薪分配 ----
    val plans: List<SavingPlanUiModel> = emptyList(),
    val paycheckScheme: PaycheckSchemeUiModel = PaycheckSchemeUiModel(),
    val paycheckLastRun: PaycheckLastRunUiModel = PaycheckLastRunUiModel(),
    val incomeCandidates: List<IncomeCandidateUiModel> = emptyList(), // 本月收入记录 + 匹配账户
    val holderEarmarkTotal: Map<Long, Long> = emptyMap(),             // 账户 → 全部计划已圈和
    val availableToEarmark: Map<Long, Long> = emptyMap()              // 账户 → 可再圈上限（≥0）
)

/**
 * 支付方式 → 账户命中启发式（v0.2 内联逻辑原样抽取，语义零变化；
 * 账户流水的按月聚合与本月的收入候选匹配共用，保证口径一致）。
 */
private fun methodMatchesAccount(methodRaw: String, account: AccountUiModel): Boolean {
    val method = methodRaw.trim()
    return method.isNotBlank() && (
        method == account.name ||
            account.name.contains(method, ignoreCase = true) ||
            (method.contains("微信") && account.name.contains("微信")) ||
            (method.contains("支付宝") && account.name.contains("支付宝")) ||
            ((method.contains("卡") || method.contains("银行")) && (account.name.contains("行") || account.name.contains("卡"))) ||
            (method.contains("现金") && account.name.contains("现金"))
        )
}

private fun findAccountForMethod(method: String, accounts: List<AccountUiModel>): AccountUiModel? =
    accounts.firstOrNull { methodMatchesAccount(method, it) }

class AccountViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val currentYear = currentYearMonth.first
    private val currentMonth = currentYearMonth.second

    // 观察 DataStore 账户/计划/方案 JSON，结合当月流水实时计算余额与页面状态
    // 口径：余额 = 期初（对账校正已并入 openingBalance）+ 当月入 - 当月出
    // 注：期初 + 仅当月流水的口径存在跨月漂移的已知风险，本期不修正口径（见设计文档 §8.5）
    private val recordsOfMonth = recordRepository.getRecordsByMonth(currentYear, currentMonth)

    val uiState: StateFlow<AccountUiState> = combine(
        combine(preferencesRepository.accountsData, preferencesRepository.privacyMode) { a, p -> a to p },
        combine(recordsOfMonth, _isRefreshing) { r, ref -> r to ref },
        combine(preferencesRepository.paycheckSchemeData, preferencesRepository.paycheckLastRunData) { s, l -> s to l },
        preferencesRepository.savingPlansData
    ) { baseA, baseB, baseC, plansJson ->
        assembleAccountUiState(
            accountsJson = baseA.first,
            privacyMode = baseA.second,
            monthRecords = baseB.first,
            refreshing = baseB.second,
            schemeJson = baseC.first,
            lastRunJson = baseC.second,
            plansJson = plansJson
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountUiState(isLoading = true)
    )

    private fun assembleAccountUiState(
        accountsJson: String?,
        privacyMode: Boolean,
        monthRecords: List<RecordWithCategory>,
        refreshing: Boolean,
        plansJson: String?,
        schemeJson: String?,
        lastRunJson: String?
    ): AccountUiState {
        val currentMonthRecords = monthRecords
        val rawAccounts = if (accountsJson.isNullOrBlank()) {
            getDefaultSeedAccounts()
        } else {
            parseAccountsJson(accountsJson).ifEmpty { getDefaultSeedAccounts() }
        }

        // 按当月流水与支付方式匹配账户，聚合本月入账/出账
        val enrichedAccounts = rawAccounts.map { account ->
            var monthIn = 0L
            var monthOut = 0L

            currentMonthRecords.forEach { recordWithCat ->
                val r = recordWithCat.record
                val matches = methodMatchesAccount(r.paymentMethod, account)

                if (matches) {
                    if (r.type == "INCOME") {
                        monthIn += r.amount
                    } else if (r.type == "EXPENSE") {
                        monthOut += r.amount
                    }
                }
            }

            // 当月无该账户流水时，回退保留账户自身已存的入/出分量
            val finalIn = if (monthIn > 0L) monthIn else account.inCents
            val finalOut = if (monthOut > 0L) monthOut else account.outCents
            val effectiveBalance = account.openingBalanceCents + finalIn - finalOut

            account.copy(
                inCents = finalIn,
                outCents = finalOut,
                balanceCents = effectiveBalance
            )
        }

        // 统一按 sortOrder 升序排一次，保证列表 / 分布彩条 / 类型 chips 的呈现顺序一致且稳定
        val sortedAccounts = enrichedAccounts.sortedBy { it.sortOrder }

        // 分组：label 仅作字符串透传，trim 后为空则归入 key ""（「未分组」展示由 UI 决定）
        val groups = sortedAccounts
            .groupBy { it.label.trim() }
            .map { (key, members) ->
                AccountGroupUiModel(
                    label = key,
                    accounts = members, // 列表已全局按 sortOrder 升序，组内即同序
                    groupTotalCents = members.sumOf { it.balanceCents }
                )
            }
            .sortedWith(
                // 组间按组内最小 sortOrder 升序；空 key（未分组）恒居最末；并列时按组名升序兜底
                compareBy<AccountGroupUiModel> { group ->
                    if (group.label.isEmpty()) Int.MAX_VALUE else group.accounts.minOf { it.sortOrder }
                }.thenBy { it.label }
            )

        // 净资产拆解：正余额合计为总资产，负余额合计为待还负债（符号即语义）
        val positiveAccounts = sortedAccounts.filter { it.balanceCents > 0L }
        val totalAsset = positiveAccounts.sumOf { it.balanceCents }
        val totalDebt = sortedAccounts.filter { it.balanceCents < 0L }.sumOf { it.balanceCents }
        val totalBalance = totalAsset + totalDebt

        // 分布彩条：正余额账户逐账户生成一段，label 取账户名，顺序与页面清单一致；无正资产则不显示
        val distributionSegments = if (totalAsset > 0L) {
            positiveAccounts.map { acc ->
                DistributionSegment(
                    label = acc.name,
                    amountCents = acc.balanceCents,
                    colorHex = acc.colorHex,
                    percentage = acc.balanceCents.toFloat() / totalAsset.toFloat()
                )
            }
        } else {
            emptyList()
        }

        // ---- v0.3：计划/方案解析 + 本月收入候选 + 专款派生（UI 直接读取，口径见设计文档 §1）----
        val plans = parseSavingPlans(plansJson).sortedBy { it.sortOrder }
        val scheme = parsePaycheckScheme(schemeJson)
        val lastRun = parsePaycheckLastRun(lastRunJson)
        val incomeCandidates = currentMonthRecords
            .filter { it.record.type == "INCOME" }
            .map { rw ->
                val r = rw.record
                val matched = findAccountForMethod(r.paymentMethod, sortedAccounts)
                IncomeCandidateUiModel(
                    recordId = r.id,
                    amountCents = r.amount,
                    note = r.remark,
                    method = r.paymentMethod,
                    at = r.recordTime,
                    matchedAccountId = matched?.id,
                    matchedAccountName = matched?.name
                )
            }
            .sortedByDescending { it.at }
        val holderEarmarkTotal = plans.groupBy { it.holderAccountId }
            .mapValues { (_, ps) -> ps.sumOf { it.earmarkedCents } }
        val availableToEarmark = sortedAccounts.associate { acc ->
            acc.id to (acc.balanceCents - (holderEarmarkTotal[acc.id] ?: 0L)).coerceAtLeast(0L)
        }

        val totalIn = sortedAccounts.sumOf { it.inCents }
        val totalOut = sortedAccounts.sumOf { it.outCents }

        return AccountUiState(
            accounts = sortedAccounts,
            groups = groups,
            totalBalanceCents = totalBalance,
            totalAssetCents = totalAsset,
            totalDebtCents = totalDebt,
            distributionSegments = distributionSegments,
            monthInCents = totalIn,
            monthOutCents = totalOut,
            monthNetCents = totalIn - totalOut,
            isPrivacyMode = privacyMode,
            isLoading = false,
            isRefreshing = refreshing,
            plans = plans,
            paycheckScheme = scheme,
            paycheckLastRun = lastRun,
            incomeCandidates = incomeCandidates,
            holderEarmarkTotal = holderEarmarkTotal,
            availableToEarmark = availableToEarmark
        )
    }

    fun togglePrivacyMode() {
        viewModelScope.launch {
            preferencesRepository.togglePrivacyMode()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            kotlinx.coroutines.delay(350)
            _isRefreshing.value = false
        }
    }

    fun createAccount(
        name: String,
        label: String,
        iconName: String,
        colorHex: Long,
        openingBalanceCents: Long
    ) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val newId = (currentAccounts.maxOfOrNull { it.id } ?: 0L) + 1L
            val newAccount = AccountUiModel(
                id = newId,
                name = name.trim(),
                label = label.trim(), // 原样存储，允许为空串，不做任何默认类型替换
                iconName = iconName,
                colorHex = colorHex,
                openingBalanceCents = openingBalanceCents,
                balanceCents = openingBalanceCents,
                sortOrder = currentAccounts.size + 1
            )
            currentAccounts.add(newAccount)
            persistAccounts(currentAccounts)
        }
    }

    fun updateAccount(account: AccountUiModel) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == account.id }
            if (index >= 0) {
                currentAccounts[index] = account
                persistAccounts(currentAccounts)
            }
        }
    }

    /**
     * 资金对账：
     * 账面 = 期初 + 当月入 - 当月出，差额 = 实盘 - 账面。
     * applyCorrection 为 true 且差额非 0 时，将差额并入期初（openingBalance），
     * 后续流水自动平移基线，账面与实盘对齐，不产生假流水。
     */
    fun reconcileAccount(
        accountId: Long,
        actualBalanceCents: Long,
        applyCorrection: Boolean
    ) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == accountId }
            if (index >= 0) {
                val target = currentAccounts[index]
                val book = target.balanceCents
                val diff = actualBalanceCents - book
                val now = System.currentTimeMillis()

                val newOpening = if (applyCorrection && diff != 0L) {
                    target.openingBalanceCents + diff
                } else {
                    target.openingBalanceCents
                }

                val newBalance = if (applyCorrection) actualBalanceCents else book

                currentAccounts[index] = target.copy(
                    openingBalanceCents = newOpening,
                    balanceCents = newBalance,
                    lastReconciledAt = now,
                    lastReconciledDiffCents = diff
                )
                persistAccounts(currentAccounts)
            }
        }
    }

    /**
     * 快速转账：转出账户出账，转入账户入账
     */
    fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        remark: String = ""
    ) {
        if (fromAccountId == toAccountId || amountCents <= 0L) return
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val fromIndex = currentAccounts.indexOfFirst { it.id == fromAccountId }
            val toIndex = currentAccounts.indexOfFirst { it.id == toAccountId }

            if (fromIndex >= 0 && toIndex >= 0) {
                val fromAcc = currentAccounts[fromIndex]
                val toAcc = currentAccounts[toIndex]

                currentAccounts[fromIndex] = fromAcc.copy(
                    outCents = fromAcc.outCents + amountCents,
                    balanceCents = fromAcc.balanceCents - amountCents
                )
                currentAccounts[toIndex] = toAcc.copy(
                    inCents = toAcc.inCents + amountCents,
                    balanceCents = toAcc.balanceCents + amountCents
                )
                persistAccounts(currentAccounts)
            }
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            currentAccounts.removeAll { it.id == accountId }
            persistAccounts(currentAccounts)
        }
    }

    // ================= v0.3：攒钱计划 & 发薪分配（设计文档 saving-plans-and-paycheck-v0.3.md） =================

    fun createPlan(name: String, targetAmountCents: Long, holderAccountId: Long, colorHex: Long) {
        viewModelScope.launch {
            val plans = uiState.value.plans.toMutableList()
            val newId = (plans.maxOfOrNull { it.id } ?: 0L) + 1L
            plans.add(
                SavingPlanUiModel(
                    id = newId,
                    name = name.trim(),
                    holderAccountId = holderAccountId,
                    targetAmountCents = targetAmountCents.coerceAtLeast(0L),
                    earmarkedCents = 0L,
                    colorHex = colorHex,
                    sortOrder = plans.size + 1,
                    createdAt = System.currentTimeMillis()
                )
            )
            persistPlans(plans)
        }
    }

    /** 编辑计划；若新专款账户容纳不下既有已圈额，则收敛已圈到可圈上限（D5①，不静默丢失超额用途） */
    fun updatePlan(plan: SavingPlanUiModel) {
        viewModelScope.launch {
            val s = uiState.value
            val idx = s.plans.indexOfFirst { it.id == plan.id }
            if (idx < 0) return@launch
            val plans = s.plans.toMutableList()
            val holder = s.accounts.firstOrNull { it.id == plan.holderAccountId }
            var earmarked = plan.earmarkedCents
            if (holder != null) {
                val others = s.plans
                    .filter { it.holderAccountId == holder.id && it.id != plan.id }
                    .sumOf { it.earmarkedCents }
                val cap = (holder.balanceCents - others).coerceAtLeast(0L)
                earmarked = earmarked.coerceAtMost(cap)
            }
            plans[idx] = plan.copy(
                name = plan.name.trim(),
                targetAmountCents = plan.targetAmountCents.coerceAtLeast(0L),
                earmarkedCents = earmarked
            )
            persistPlans(plans)
        }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            persistPlans(uiState.value.plans.filterNot { it.id == planId })
        }
    }

    /** 再存一笔（虚拟专款）：上限 = 账户余额 - 该账户全部已圈（UI 依状态禁用，VM 双保险收敛） */
    fun depositToPlan(planId: Long, amountCents: Long) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            val s = uiState.value
            val idx = s.plans.indexOfFirst { it.id == planId }
            if (idx < 0) return@launch
            val plan = s.plans[idx]
            val holder = s.accounts.firstOrNull { it.id == plan.holderAccountId } ?: return@launch
            val others = s.plans
                .filter { it.holderAccountId == holder.id && it.id != plan.id }
                .sumOf { it.earmarkedCents }
            val cap = (holder.balanceCents - others).coerceAtLeast(0L)
            val actual = amountCents.coerceAtMost(cap)
            if (actual <= 0L) return@launch
            val plans = s.plans.toMutableList()
            plans[idx] = plan.copy(earmarkedCents = plan.earmarkedCents + actual)
            persistPlans(plans)
        }
    }

    /** 撤回专款：上限 = 该计划已圈额，可全部撤回 */
    fun withdrawFromPlan(planId: Long, amountCents: Long) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            val s = uiState.value
            val idx = s.plans.indexOfFirst { it.id == planId }
            if (idx < 0) return@launch
            val plan = s.plans[idx]
            val actual = amountCents.coerceAtMost(plan.earmarkedCents)
            if (actual <= 0L) return@launch
            val plans = s.plans.toMutableList()
            plans[idx] = plan.copy(earmarkedCents = plan.earmarkedCents - actual)
            persistPlans(plans)
        }
    }

    fun savePaycheckScheme(scheme: PaycheckSchemeUiModel) {
        viewModelScope.launch {
            preferencesRepository.savePaycheckSchemeData(serializePaycheckScheme(scheme))
        }
    }

    /**
     * 发薪分配执行（引擎 E1-E7）：预览与执行共用同一纯函数 planPaycheckActions。
     * 执行 = 账户间转账（in/out 语义，与手动转账一致）+ 计划 earmark 累加 + lastRun 落库；
     * 不写收支流水、不影响对账基线。单协程顺序写三个 DataStore 键。
     */
    fun executePaycheck(sourceAccountId: Long, amountCents: Long) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            val s = uiState.value
            val source = s.accounts.firstOrNull { it.id == sourceAccountId } ?: return@launch
            val result = planPaycheckActions(amountCents, source, s.accounts, s.plans, s.paycheckScheme)

            // 1) 转账户步（含自动清欠）→ 与 transfer() 相同的 in/out 语义；
            //    TO_PLAN 跨账户专款同样真实转入专款账户，自持专款（专款账户==来源账户）不动钱
            var accounts = s.accounts
            val accountById = accounts.associateBy { it.id }.toMutableMap()
            result.steps.forEach { step ->
                if (step.amountCents <= 0L) return@forEach
                when (step.kind) {
                    PaycheckStepKind.TO_ACCOUNT, PaycheckStepKind.CLEAR_DEBT -> {
                        val from = accountById[source.id] ?: return@forEach
                        val to = accountById[step.targetId] ?: return@forEach
                        accountById[source.id] = from.copy(
                            outCents = from.outCents + step.amountCents,
                            balanceCents = from.balanceCents - step.amountCents
                        )
                        accountById[to.id] = to.copy(
                            inCents = to.inCents + step.amountCents,
                            balanceCents = to.balanceCents + step.amountCents
                        )
                    }
                    PaycheckStepKind.TO_PLAN -> {
                        val plan = s.plans.firstOrNull { it.id == step.targetId } ?: return@forEach
                        if (plan.holderAccountId == source.id) return@forEach // 自持：仅 earmark，不动钱
                        val from = accountById[source.id] ?: return@forEach
                        val holder = accountById[plan.holderAccountId] ?: return@forEach
                        accountById[source.id] = from.copy(
                            outCents = from.outCents + step.amountCents,
                            balanceCents = from.balanceCents - step.amountCents
                        )
                        accountById[holder.id] = holder.copy(
                            inCents = holder.inCents + step.amountCents,
                            balanceCents = holder.balanceCents + step.amountCents
                        )
                    }
                    PaycheckStepKind.REMAIN -> Unit
                }
            }
            accounts = accounts.map { accountById[it.id] ?: it }

            // 2) 进计划步 → earmark 累加
            var plans = s.plans
            result.steps
                .filter { it.kind == PaycheckStepKind.TO_PLAN && it.amountCents > 0L }
                .forEach { step ->
                    val idx = plans.indexOfFirst { it.id == step.targetId }
                    if (idx >= 0) {
                        val mutable = plans.toMutableList()
                        mutable[idx] = mutable[idx].copy(
                            earmarkedCents = mutable[idx].earmarkedCents + step.amountCents
                        )
                        plans = mutable
                    }
                }

            persistAccounts(accounts)
            persistPlans(plans)
            val run = PaycheckLastRunUiModel(
                at = System.currentTimeMillis(),
                amountCents = amountCents,
                actionCount = result.steps.count {
                    it.kind != PaycheckStepKind.REMAIN && it.amountCents > 0L
                },
                remainingCents = result.remainingCents
            )
            preferencesRepository.savePaycheckLastRunData(serializePaycheckLastRun(run))
        }
    }

    private suspend fun persistPlans(plans: List<SavingPlanUiModel>) {
        preferencesRepository.saveSavingPlansData(serializeSavingPlans(plans))
    }

    private suspend fun persistAccounts(accounts: List<AccountUiModel>) {
        val json = serializeAccountsJson(accounts)
        preferencesRepository.saveAccountsData(json)
    }

    companion object {
        // 首次启动的示例账户：仅为演示数据，label 等字段只是用户可自由改写/删除的示例字符串
        fun getDefaultSeedAccounts(): List<AccountUiModel> {
            val now = System.currentTimeMillis()
            return listOf(
                AccountUiModel(
                    id = 1L,
                    name = "微信支付",
                    label = "流动活期",
                    iconName = "wallet",
                    colorHex = 0xFF059669L,
                    openingBalanceCents = 328000L,
                    balanceCents = 328000L,
                    inCents = 240000L,
                    outCents = 185000L,
                    lastReconciledAt = now,
                    lastReconciledDiffCents = 0L,
                    sortOrder = 1
                ),
                AccountUiModel(
                    id = 2L,
                    name = "支付宝",
                    label = "流动活期",
                    iconName = "part_time",
                    colorHex = 0xFF0284C7L,
                    openingBalanceCents = 896000L,
                    balanceCents = 896000L,
                    inCents = 560000L,
                    outCents = 320000L,
                    lastReconciledAt = now - 86400000L,
                    lastReconciledDiffCents = 0L,
                    sortOrder = 2
                ),
                AccountUiModel(
                    id = 3L,
                    name = "招商银行储蓄卡",
                    label = "储备资金",
                    iconName = "bank",
                    colorHex = 0xFFE53935L,
                    openingBalanceCents = 5200000L,
                    balanceCents = 5200000L,
                    inCents = 1500000L,
                    outCents = 240000L,
                    lastReconciledAt = now - 172800000L,
                    lastReconciledDiffCents = 0L,
                    sortOrder = 3
                ),
                AccountUiModel(
                    id = 4L,
                    name = "花呗 / 信用卡",
                    label = "信用借贷",
                    iconName = "bonus",
                    colorHex = 0xFFFF9800L,
                    openingBalanceCents = -158000L,
                    balanceCents = -158000L,
                    inCents = 0L,
                    outCents = 158000L,
                    lastReconciledAt = null,
                    lastReconciledDiffCents = null,
                    sortOrder = 4
                ),
                AccountUiModel(
                    id = 5L,
                    name = "现钞零钱",
                    label = "现钞零钱",
                    iconName = "savings",
                    colorHex = 0xFF607D8BL,
                    openingBalanceCents = 65000L,
                    balanceCents = 65000L,
                    inCents = 0L,
                    outCents = 8000L,
                    lastReconciledAt = null,
                    lastReconciledDiffCents = null,
                    sortOrder = 5
                )
            )
        }

        private fun serializeAccountsJson(accounts: List<AccountUiModel>): String {
            val array = JSONArray()
            accounts.forEach { acc ->
                val obj = JSONObject()
                obj.put("id", acc.id)
                obj.put("name", acc.name)
                obj.put("label", acc.label)
                obj.put("iconName", acc.iconName)
                obj.put("colorHex", acc.colorHex)
                obj.put("openingBalanceCents", acc.openingBalanceCents)
                obj.put("balanceCents", acc.balanceCents)
                obj.put("inCents", acc.inCents)
                obj.put("outCents", acc.outCents)
                if (acc.lastReconciledAt != null) {
                    obj.put("lastReconciledAt", acc.lastReconciledAt)
                }
                if (acc.lastReconciledDiffCents != null) {
                    obj.put("lastReconciledDiffCents", acc.lastReconciledDiffCents)
                }
                obj.put("sortOrder", acc.sortOrder)
                array.put(obj)
            }
            return array.toString()
        }

        private fun parseAccountsJson(jsonStr: String): List<AccountUiModel> {
            val result = mutableListOf<AccountUiModel>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        AccountUiModel(
                            id = obj.optLong("id", i.toLong() + 1L),
                            name = obj.optString("name", "账户"),
                            label = obj.optString("label", ""),
                            iconName = obj.optString("iconName", "wallet"),
                            colorHex = obj.optLong("colorHex", 0xFF059669L),
                            openingBalanceCents = obj.optLong("openingBalanceCents", 0L),
                            balanceCents = obj.optLong("balanceCents", 0L),
                            inCents = obj.optLong("inCents", 0L),
                            outCents = obj.optLong("outCents", 0L),
                            lastReconciledAt = if (obj.has("lastReconciledAt")) obj.getLong("lastReconciledAt") else null,
                            lastReconciledDiffCents = if (obj.has("lastReconciledDiffCents")) obj.getLong("lastReconciledDiffCents") else null,
                            sortOrder = obj.optInt("sortOrder", i + 1)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val recordRepository: RecordRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
                return AccountViewModel(preferencesRepository, recordRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
