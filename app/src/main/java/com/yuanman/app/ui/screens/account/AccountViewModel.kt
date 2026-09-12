package com.yuanman.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
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
    val sortOrder: Int = 0,
    // ---- 对账周期 & 记录（v0.0.4+）：override 为空 = 跟随全局周期；skip 为「跳过本期提醒」截止 ----
    val reconcileCycleOverride: ReconcileCycle? = null,
    val reconcileTipSkipUntil: Long? = null,
    val reconcileRecords: List<ReconcileRecordUiModel> = emptyList(),
    // 派生（不持久化）：按生效周期计算的对账时效，组装阶段统一填充
    val reconcileStatus: AccountReconcileStatus =
        AccountReconcileStatus(text = "从未对账", tone = ReconcileTone.NEVER)
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
    val availableToEarmark: Map<Long, Long> = emptyMap(),              // 账户 → 可再圈上限（≥0）
    val globalReconcileCycle: ReconcileCycle = ReconcileCycle.DEFAULT, // 全局默认对账周期
    val defaultExpenseAccount: String = "",
    val defaultIncomeAccount: String = "",
    // ---- v0.0.4.5：工资到账自动分账 ----
    val paycheckAutoEnabled: Boolean = true,                // 自动分账总开关
    val appliedIncomeRecordIds: Set<Long> = emptySet(),     // 已分账过的收入记录（幂等）
    val paycheckRunHistory: List<PaycheckLastRunUiModel> = emptyList() // 历次分账记录（最新在前）
)

class AccountViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 手动补分/自动分账的一次性执行结果提示（页面 toast 消费后 clear）
    private val _paycheckNotice = MutableStateFlow<String?>(null)
    val paycheckNotice: StateFlow<String?> = _paycheckNotice.asStateFlow()

    fun clearPaycheckNotice() {
        _paycheckNotice.value = null
    }

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val currentYear = currentYearMonth.first
    private val currentMonth = currentYearMonth.second

    // 观察 DataStore 账户/计划/方案 JSON，结合当月流水实时计算余额与页面状态
    // 口径：余额 = 期初（对账校正已并入 openingBalance）+ 当月入 - 当月出
    // 注：期初 + 仅当月流水的口径存在跨月漂移的已知风险，本期不修正口径（见设计文档 §8.5）
    private val recordsOfMonth = recordRepository.getRecordsByMonth(currentYear, currentMonth)

    /** 当月流水（账户详情「收支记录明细」卡使用；过滤口径与账户聚合一致：按支付方式命中账户） */
    val monthRecordsFlow: StateFlow<List<RecordWithCategory>> = recordsOfMonth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AccountUiState> = combine(
        combine(preferencesRepository.accountsData, preferencesRepository.privacyMode) { a, p -> a to p },
        combine(recordsOfMonth, _isRefreshing) { r, ref -> r to ref },
        combine(preferencesRepository.paycheckSchemeData, preferencesRepository.paycheckLastRunData) { s, l -> s to l },
        combine(preferencesRepository.savingPlansData, preferencesRepository.reconcileCycleData) { p, c -> p to c },
        combine(
            combine(preferencesRepository.defaultExpenseAccount, preferencesRepository.defaultIncomeAccount) { e, i -> e to i },
            combine(
                preferencesRepository.paycheckAutoEnabled,
                preferencesRepository.paycheckAutoAppliedIds,
                preferencesRepository.paycheckRunHistoryData
            ) { enabled, appliedIds, history -> Triple(enabled, appliedIds, history) }
        ) { defAccounts, autoInfo -> defAccounts to autoInfo }
    ) { baseA, baseB, baseC, plansAndCycle, extraInfo ->
        val defAccounts = extraInfo.first
        val autoInfo = extraInfo.second
        assembleAccountUiState(
            accountsJson = baseA.first,
            privacyMode = baseA.second,
            monthRecords = baseB.first,
            refreshing = baseB.second,
            schemeJson = baseC.first,
            lastRunJson = baseC.second,
            plansJson = plansAndCycle.first,
            reconcileCycleJson = plansAndCycle.second,
            defaultExpenseAccount = defAccounts.first,
            defaultIncomeAccount = defAccounts.second,
            paycheckAutoEnabled = autoInfo.first,
            appliedIncomeIdsJson = autoInfo.second,
            runHistoryJson = autoInfo.third
        )
    }
        // JSON 全量解析与账户/计划聚合计算移出主线程；stateIn 收集仍回到主线程
        .flowOn(Dispatchers.Default)
        .stateIn(
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
        lastRunJson: String?,
        reconcileCycleJson: String?,
        defaultExpenseAccount: String,
        defaultIncomeAccount: String,
        paycheckAutoEnabled: Boolean,
        appliedIncomeIdsJson: String?,
        runHistoryJson: String?
    ): AccountUiState {
        val currentMonthRecords = monthRecords
        val globalReconcileCycle = runCatching {
            ReconcileCycle.fromJson(JSONObject(reconcileCycleJson))
        }.getOrNull() ?: ReconcileCycle.DEFAULT
        // 账户数据以用户实际创建为准：无数据（首启/清空）即为空列表，不再注入演示账户
        val rawAccounts = if (accountsJson.isNullOrBlank()) {
            emptyList()
        } else {
            parseAccountsJson(accountsJson)
        }

        // 按当月流水与支付方式匹配账户，聚合本月入账/出账并派生对账字段（共享口径见 AccountDataCodec.kt）
        val enrichedAccounts = enrichAccountsForMonth(rawAccounts, currentMonthRecords, globalReconcileCycle)

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

        // ---- 计划/方案解析 + 本月工资类收入候选 + 专款派生（v0.0.4.5：候选仅「工资/薪」类收入）----
        val plans = parseSavingPlans(plansJson).sortedBy { it.sortOrder }
        val scheme = parsePaycheckScheme(schemeJson)
        val lastRun = parsePaycheckLastRun(lastRunJson)
        val appliedIncomeRecordIds = parseAppliedIncomeIds(appliedIncomeIdsJson)
        val incomeCandidates = currentMonthRecords
            .filter { it.record.type == "INCOME" }
            .filter { rw -> rw.category?.name?.let(::isSalaryCategoryName) == true }
            .map { rw ->
                val r = rw.record
                val matched = findAccountForMethod(r.paymentMethod, sortedAccounts)
                IncomeCandidateUiModel(
                    recordId = r.id,
                    amountCents = r.amount,
                    note = r.remark,
                    method = r.paymentMethod,
                    at = r.recordTime,
                    categoryName = rw.category?.name.orEmpty(),
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
            availableToEarmark = availableToEarmark,
            globalReconcileCycle = globalReconcileCycle,
            defaultExpenseAccount = defaultExpenseAccount,
            defaultIncomeAccount = defaultIncomeAccount,
            paycheckAutoEnabled = paycheckAutoEnabled,
            appliedIncomeRecordIds = appliedIncomeRecordIds,
            paycheckRunHistory = parsePaycheckRunHistory(runHistoryJson)
        )
    }

    fun setDefaultExpenseAccount(accountName: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultExpenseAccount(accountName)
        }
    }

    fun setDefaultIncomeAccount(accountName: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultIncomeAccount(accountName)
        }
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
     * 每次对账都会追加一条「对账记录」（ReconcileRecordUiModel），账户最近核对
     * 时间/差额由最新一条记录派生；同时清除该账户的「跳过本期提醒」状态。
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

                val nextRecordId = (target.reconcileRecords.maxOfOrNull { it.id } ?: 0L) + 1L
                val newRecord = ReconcileRecordUiModel(
                    id = nextRecordId,
                    asOfDate = now,
                    actualBalanceCents = actualBalanceCents,
                    bookBalanceCents = book,
                    diffCents = diff,
                    corrected = applyCorrection && diff != 0L
                )

                currentAccounts[index] = target.copy(
                    openingBalanceCents = newOpening,
                    balanceCents = newBalance,
                    lastReconciledAt = now,
                    lastReconciledDiffCents = diff,
                    reconcileTipSkipUntil = null,
                    reconcileRecords = (target.reconcileRecords + newRecord)
                        .sortedByDescending { it.asOfDate }
                )
                persistAccounts(currentAccounts)
            }
        }
    }

    /**
     * 删除一条对账记录：账户最近核对时间/差额回退到剩余最新一条（无记录则回到「从未对账」）。
     * 只影响提醒口径与审计痕迹，不撤销当时已并入期初的校正。
     */
    fun deleteReconcileRecord(accountId: Long, recordId: Long) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == accountId }
            if (index < 0) return@launch
            val target = currentAccounts[index]
            val remaining = target.reconcileRecords.filterNot { it.id == recordId }
            val head = remaining.firstOrNull()
            currentAccounts[index] = target.copy(
                lastReconciledAt = head?.asOfDate,
                lastReconciledDiffCents = head?.diffCents,
                reconcileRecords = remaining
            )
            persistAccounts(currentAccounts)
        }
    }

    /** 设置全局默认对账周期（账户未自定义时生效） */
    fun setGlobalReconcileCycle(cycle: ReconcileCycle) {
        viewModelScope.launch {
            preferencesRepository.saveReconcileCycleData(cycle.toJson().toString())
        }
    }

    /** 设置账户自定义对账周期；cycle 为 null = 清除自定义、跟随全局 */
    fun setAccountReconcileCycleOverride(accountId: Long, cycle: ReconcileCycle?) {
        viewModelScope.launch {
            val currentAccounts = uiState.value.accounts.toMutableList()
            val index = currentAccounts.indexOfFirst { it.id == accountId }
            if (index >= 0) {
                currentAccounts[index] = currentAccounts[index].copy(reconcileCycleOverride = cycle)
                persistAccounts(currentAccounts)
            }
        }
    }

    /**
     * 跳过本期核对提醒：对指定的待核对账户静默到各自周期的期末（periodEndEpoch），
     * 下一期开始时若仍未对账，提醒恢复。对账成功会自动清除跳过状态。
     */
    fun skipReconcileReminderFor(accountIds: List<Long>) {
        if (accountIds.isEmpty()) return
        viewModelScope.launch {
            val s = uiState.value
            val global = s.globalReconcileCycle
            val now = System.currentTimeMillis()
            val currentAccounts = s.accounts.toMutableList()
            var changed = false
            currentAccounts.indices.forEach { i ->
                val acc = currentAccounts[i]
                if (acc.id in accountIds) {
                    val cycle = effectiveCycleFor(acc.reconcileCycleOverride, global)
                    currentAccounts[i] = acc.copy(reconcileTipSkipUntil = periodEndEpoch(cycle, now))
                    changed = true
                }
            }
            if (changed) persistAccounts(currentAccounts)
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

    /** 存一笔（虚拟专款，原名「再存一笔」）：上限 = 账户余额 - 该账户全部已圈（UI 依状态禁用，VM 双保险收敛）。追加 DEPOSIT 事件。 */
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
            plans[idx] = plan.copy(
                earmarkedCents = plan.earmarkedCents + actual,
                events = plan.events + PlanEventUiModel(
                    id = (plan.events.maxOfOrNull { it.id } ?: 0L) + 1L,
                    kind = PlanEventKind.DEPOSIT,
                    amountCents = actual,
                    at = System.currentTimeMillis()
                )
            )
            persistPlans(plans)
        }
    }

    /** 取一笔（原「撤回专款 / 取出一笔」简化命名）：上限 = 该计划已圈额，可全部取出。追加 WITHDRAW 事件。 */
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
            plans[idx] = plan.copy(
                earmarkedCents = plan.earmarkedCents - actual,
                events = plan.events + PlanEventUiModel(
                    id = (plan.events.maxOfOrNull { it.id } ?: 0L) + 1L,
                    kind = PlanEventKind.WITHDRAW,
                    amountCents = actual,
                    at = System.currentTimeMillis()
                )
            )
            persistPlans(plans)
        }
    }

    /**
     * 删除一条计划事件（攒钱/取出记录）：earmark 按相反方向回滚
     * （删除攒入 → 减额；删除取出 → 加回，若超出专款账户余额则进入既有超额警示态）。
     */
    fun deletePlanEvent(planId: Long, eventId: Long) {
        viewModelScope.launch {
            val s = uiState.value
            val idx = s.plans.indexOfFirst { it.id == planId }
            if (idx < 0) return@launch
            val plan = s.plans[idx]
            val event = plan.events.firstOrNull { it.id == eventId } ?: return@launch
            val adjust = when (event.kind) {
                PlanEventKind.DEPOSIT -> -event.amountCents
                PlanEventKind.WITHDRAW -> event.amountCents
            }
            val plans = s.plans.toMutableList()
            plans[idx] = plan.copy(
                earmarkedCents = (plan.earmarkedCents + adjust).coerceAtLeast(0L),
                events = plan.events.filterNot { it.id == eventId }
            )
            persistPlans(plans)
        }
    }

    fun savePaycheckScheme(scheme: PaycheckSchemeUiModel) {
        viewModelScope.launch {
            preferencesRepository.savePaycheckSchemeData(serializePaycheckScheme(scheme))
        }
    }

    /**
     * 手动补分（引擎 E1-E7 见 SavingPlanModels）：委托共享 PaycheckExecutor 落库
     * （自动/手动同一路径，不写收支流水、不影响对账基线）。
     * 执行结果写入 [paycheckNotice] 由页面 toast 展示。
     */
    fun executePaycheck(sourceAccountId: Long, amountCents: Long, recordId: Long? = null) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            val summary = paycheckExecutor.runManual(sourceAccountId, amountCents, recordId)
            _paycheckNotice.value = if (summary.executedCount > 0) {
                "已按发薪规则分账 ${summary.executedCount} 笔动作"
            } else {
                "按当前规则没有可执行的动作（可能是来源余额不足或规则为空）"
            }
        }
    }

    /** 工资到账自动分账总开关 */
    fun setPaycheckAutoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPaycheckAutoEnabled(enabled)
        }
    }

    private val paycheckExecutor: PaycheckExecutor by lazy {
        PaycheckExecutor(preferencesRepository, recordRepository)
    }

    private suspend fun persistPlans(plans: List<SavingPlanUiModel>) {
        preferencesRepository.saveSavingPlansData(serializeSavingPlans(plans))
    }

    private suspend fun persistAccounts(accounts: List<AccountUiModel>) {
        val json = serializeAccountsJson(accounts)
        preferencesRepository.saveAccountsData(json)
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
