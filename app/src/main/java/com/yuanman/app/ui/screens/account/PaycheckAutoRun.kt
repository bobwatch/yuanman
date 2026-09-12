package com.yuanman.app.ui.screens.account

import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.first

/**
 * 发薪分账共享执行器（v0.0.4.5：发薪分配 = 预置规则，工资到账自动执行）。
 *
 * 三条入口共用同一份落库逻辑，口径与 AccountViewModel 页面组装一致：
 *  1. [onSalaryRecordSaved]  记账保存「工资」类收入后自动触发（AddEditRecordViewModel）；
 *  2. [runManual]            发薪分配页「手动补分」兜底（未自动匹配 / 自动被关）；
 *  3. AccountViewModel.executePaycheck 委托 [runManual]。
 *
 * 执行语义（E1-E7 同引擎 planPaycheckActions）：
 *  账户间转账（in/out）+ 计划 earmark 累加并追加「攒入 · 发薪分配」事件 + lastRun / 历史落库；
 *  不写收支流水、不影响对账基线。已执行的收入记录 id 持久化，防止同一笔工资重复分账。
 */
class PaycheckExecutor(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository
) {

    /** 单次执行结果摘要（供 UI 提示语） */
    data class RunSummary(
        val executedCount: Int = 0,
        val amountCents: Long = 0L,
        val remainingCents: Long = 0L,
        val sourceAccountName: String? = null
    )

    /** 工资到账自动执行：返回 null = 无动作 / 未触发；非空 = 给记账页的成功或提示文案 */
    suspend fun onSalaryRecordSaved(
        recordId: Long,
        amountCents: Long,
        paymentMethod: String
    ): String? {
        // 1) 总开关关闭 → 静默跳过（不标记，之后可在发薪分配页手动补分）
        if (!preferencesRepository.paycheckAutoEnabled.first()) return null
        // 2) 幂等守卫：同一笔工资不重复执行
        if (recordId in appliedIncomeIds()) return null

        val base = loadBase()
        // 3) 未匹配到入账账户 → 无法确定来源，提示用户（不标记，留手动入口）
        val source = findAccountForMethod(paymentMethod, base.accounts)
            ?: return "已记录。这笔工资未匹配到入账账户，未自动分账，可到「发薪分配」页手动处理。"

        val summary = applyOnce(base, source.id, amountCents, recordId = recordId, auto = true)
        preferencesRepository.addPaycheckAutoAppliedId(recordId)
        return if (summary.executedCount > 0) {
            "已按发薪规则自动分账（${summary.executedCount} 笔动作）"
        } else {
            null // 规则为空或没有可执行动作：安静完成（已标记，避免每次保存重复提示）
        }
    }

    /** 手动补分：返回执行摘要（自动=false，可由规则页 / VM 兜底入口调用） */
    suspend fun runManual(
        sourceAccountId: Long,
        amountCents: Long,
        recordId: Long? = null
    ): RunSummary {
        val base = loadBase()
        val source = base.accounts.firstOrNull { it.id == sourceAccountId }
            ?: return RunSummary()
        val summary = applyOnce(base, source.id, amountCents, recordId = recordId, auto = false)
        if (recordId != null) {
            preferencesRepository.addPaycheckAutoAppliedId(recordId)
        }
        return summary
    }

    /** 已自动/手动执行过的收入记录 id */
    suspend fun appliedIncomeIds(): Set<Long> =
        parseAppliedIncomeIds(preferencesRepository.paycheckAutoAppliedIds.first())

    // ------------------------------------------------------------------
    // 内部：装载基础数据 + 单次执行
    // ------------------------------------------------------------------

    private data class Base(
        val accounts: List<AccountUiModel>, // 富化 + sortOrder 升序
        val plans: List<SavingPlanUiModel>,
        val scheme: PaycheckSchemeUiModel
    )

    private suspend fun loadBase(): Base {
        val (year, month) = DateTimeUtils.getCurrentYearMonth()
        val accountsJson = preferencesRepository.accountsData.first()
        val rawAccounts = parseAccountsJson(accountsJson)
        val monthRecords: List<RecordWithCategory> =
            recordRepository.getRecordsByMonth(year, month).first()
        val globalCycle = runCatching {
            ReconcileCycle.fromJson(org.json.JSONObject(preferencesRepository.reconcileCycleData.first()))
        }.getOrNull() ?: ReconcileCycle.DEFAULT
        val accounts = enrichAccountsForMonth(rawAccounts, monthRecords, globalCycle)
            .sortedBy { it.sortOrder }
        val plans = parseSavingPlans(preferencesRepository.savingPlansData.first())
            .sortedBy { it.sortOrder }
        val scheme = parsePaycheckScheme(preferencesRepository.paycheckSchemeData.first())
        return Base(accounts, plans, scheme)
    }

    private suspend fun applyOnce(
        base: Base,
        sourceAccountId: Long,
        amountCents: Long,
        recordId: Long?,
        auto: Boolean
    ): RunSummary {
        val source = base.accounts.firstOrNull { it.id == sourceAccountId }
            ?: return RunSummary()
        val result = planPaycheckActions(amountCents, source, base.accounts, base.plans, base.scheme)

        // 1) 账户转账步（含自动清欠 / 跨账户攒入专款）——与 transfer() 相同 in/out 语义
        val accountById = base.accounts.associateBy { it.id }.toMutableMap()
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
                    val plan = base.plans.firstOrNull { it.id == step.targetId } ?: return@forEach
                    if (plan.holderAccountId == source.id) return@forEach // 自持：仅 earmark
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
        val accountsAfter = base.accounts.map { accountById[it.id] ?: it }

        // 2) 进计划步 → earmark 累加 + 「攒入 · 发薪分配」事件
        var plansAfter = base.plans
        val nowMs = System.currentTimeMillis()
        result.steps
            .filter { it.kind == PaycheckStepKind.TO_PLAN && it.amountCents > 0L }
            .forEach { step ->
                val idx = plansAfter.indexOfFirst { it.id == step.targetId }
                if (idx >= 0) {
                    val mutable = plansAfter.toMutableList()
                    val plan = mutable[idx]
                    mutable[idx] = plan.copy(
                        earmarkedCents = plan.earmarkedCents + step.amountCents,
                        events = plan.events + PlanEventUiModel(
                            id = (plan.events.maxOfOrNull { it.id } ?: 0L) + 1L,
                            kind = PlanEventKind.DEPOSIT,
                            amountCents = step.amountCents,
                            at = nowMs,
                            note = "发薪分配"
                        )
                    )
                    plansAfter = mutable
                }
            }

        val executedCount = result.steps.count {
            it.kind != PaycheckStepKind.REMAIN && it.amountCents > 0L
        }
        if (executedCount > 0) {
            val run = PaycheckLastRunUiModel(
                at = nowMs,
                amountCents = amountCents,
                actionCount = executedCount,
                remainingCents = result.remainingCents,
                recordId = recordId,
                sourceAccountId = source.id,
                sourceAccountName = source.name,
                auto = auto
            )
            preferencesRepository.saveAccountsData(serializeAccountsJson(accountsAfter))
            preferencesRepository.saveSavingPlansData(serializeSavingPlans(plansAfter))
            preferencesRepository.savePaycheckLastRunData(serializePaycheckLastRun(run))
            val history = parsePaycheckRunHistory(preferencesRepository.paycheckRunHistoryData.first())
                .toMutableList()
            history.removeAll { it.at != null && run.at != null && it.at == run.at && it.recordId == run.recordId }
            history.add(0, run)
            preferencesRepository.savePaycheckRunHistory(serializePaycheckRunHistory(history))
        }
        return RunSummary(
            executedCount = executedCount,
            amountCents = amountCents,
            remainingCents = result.remainingCents,
            sourceAccountName = source.name
        )
    }
}
