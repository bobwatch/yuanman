package com.yuanman.app.ui.screens.account

import org.json.JSONArray
import org.json.JSONObject

/**
 * 攒钱计划 & 发薪分配 —— 契约锚点（设计文档 saving-plans-and-paycheck-v0.3.md §1 / §5）
 *
 * 全部模型为纯数据；判定/引擎为纯函数，无 UI、无 VM、无主题依赖。
 * 纪律口径（E1-E7）：
 *  - 计划 = 对某个「专款账户」的虚拟圈定（earmark），不新增资金主体，账户余额与对账口径不变；
 *  - 单账户全部计划 earmark 之和 ≤ 账户余额；
 *  - 分配预览与执行共用同一纯引擎，输入相同输出必然相同。
 */

// ---------------------------------------------------------------------------
// 模型
// ---------------------------------------------------------------------------

data class SavingPlanUiModel(
    val id: Long,
    val name: String,               // 用户自定名称，如「旅行基金」
    val holderAccountId: Long,      // 专款账户（钱实际所在的账户 id）
    val targetAmountCents: Long,    // 目标；0 = 不设上限（UI 不画进度条/百分比）
    val earmarkedCents: Long,       // 已圈专款
    val colorHex: Long,
    val sortOrder: Int,
    val createdAt: Long
)

enum class PaycheckRuleKind {
    TO_ACCOUNT_FIXED,  // 固定额转入账户
    TO_ACCOUNT_PCT,    // 比例（基数=本次到手金额 S）
    TO_PLAN_FIXED,     // 固定额进攒钱计划
    TO_PLAN_PCT        // 比例进攒钱计划
}

data class PaycheckRuleUiModel(
    val kind: PaycheckRuleKind,
    val targetId: Long,             // 账户 id（TO_ACCOUNT_*）或计划 id（TO_PLAN_*）
    val amountCents: Long = 0L,     // FIXED 用
    val percentBps: Int = 0         // PCT 用，万分比（30% = 3000）
)

data class PaycheckSchemeUiModel(
    val rules: List<PaycheckRuleUiModel> = emptyList(),
    val autoClearDebts: Boolean = true // 「剩余自动清欠」开关
)

data class PaycheckLastRunUiModel(
    val at: Long? = null,
    val amountCents: Long? = null,
    val actionCount: Int? = null,     // 实际执行的转账/进计划笔数（不含留存）
    val remainingCents: Long? = null  // 留存来源账户金额
)

/** 本月收入候选（UI 供选择，不持久化）；支付方式匹配复用账户流水的同名启发式 */
data class IncomeCandidateUiModel(
    val recordId: Long,
    val amountCents: Long,
    val note: String,
    val method: String,
    val at: Long,
    val matchedAccountId: Long?,
    val matchedAccountName: String?
)

// ---------------------------------------------------------------------------
// 纯状态派生（供 UI 直接读取）
// ---------------------------------------------------------------------------

/** 某账户下全部计划的已圈和 */
fun earmarkTotalFor(holderAccountId: Long, plans: List<SavingPlanUiModel>): Long =
    plans.filter { it.holderAccountId == holderAccountId }.sumOf { it.earmarkedCents }

/** 该账户还可再圈的上限（账户余额 - 全部已圈，下限 0） */
fun availableToEarmarkFor(holderAccountId: Long, holderBalanceCents: Long, plans: List<SavingPlanUiModel>): Long =
    (holderBalanceCents - earmarkTotalFor(holderAccountId, plans)).coerceAtLeast(0L)

/** 计划超额（专款账户被花到低于已圈额）→ UI 琥珀警示 */
fun planIsOverdrawn(plan: SavingPlanUiModel, holderBalanceCents: Long): Boolean =
    holderBalanceCents < plan.earmarkedCents

// ---------------------------------------------------------------------------
// 分配引擎（E1-E7）：输入不变 → 输出不变；预览与执行共用
// ---------------------------------------------------------------------------

enum class PaycheckStepKind {
    TO_ACCOUNT,  // 转账户（含显式还款规则，同一语义）
    TO_PLAN,     // 进计划（earmark +=）
    CLEAR_DEBT,  // 剩余自动清欠（实际发生才出现）
    REMAIN       // 最终留存来源账户
}

/** 单步执行结果：金额为实际执行值（0 = 因约束跳过，note 说明原因） */
data class PaycheckStepUi(
    val kind: PaycheckStepKind,
    val targetId: Long?,            // TO_ACCOUNT/CLEAR_DEBT → 账户 id；TO_PLAN → 计划 id；REMAIN → null
    val amountCents: Long,
    val caption: String,            // 目标显示名（账户/计划名；REMAIN 为来源账户名）
    val note: String? = null        // "计划触顶" "自转跳过" "账户无可转余额" 等
)

data class PaycheckPlanResult(
    val steps: List<PaycheckStepUi>,
    val totalAllocatedCents: Long,  // Σ TO_ACCOUNT + TO_PLAN + CLEAR_DEBT（不含留存）
    val remainingCents: Long
)

/**
 * 纯引擎：按规则列表顺序执行。
 *  - FIXED/PCT 均按列表序，PCT 基数恒为 S（不是剩余）；
 *  - 每步实际额 = min(计划额, 剩余可分配池, 来源账户可用余额)，随执行实时扣减；
 *  - 进计划步（TO_PLAN）：专款账户 == 来源账户 → 自持圈定（钱不动，仅 earmark 增加，
 *    但消耗可分配池，防止后续规则重复分配）；专款账户 ≠ 来源账户 → 钱真实转入专款账户
 *    后再圈定（E2 口径：圈后 Σearmark ≤ 专款账户余额）；
 *  - 目标账户 == 来源账户的转账户规则 → 跳过（E3）；
 *  - 全部规则执行后，若 autoClearDebts 且仍有剩余 → 按 sortOrder 依次清负账户到 0（E4）；
 *  - 最终剩余留存来源账户（E5）。
 * 金额 0 的受限步骤仍留在 steps 中（note 说明原因），由 UI 降透明度呈现；
 * note 一律纯文案不含数值（金额只出现在步骤 amountCents 上）。
 */
fun planPaycheckActions(
    amountCents: Long,
    sourceAccount: AccountUiModel,
    allAccounts: List<AccountUiModel>,
    allPlans: List<SavingPlanUiModel>,
    scheme: PaycheckSchemeUiModel
): PaycheckPlanResult {
    val byId = allAccounts.associateBy { it.id }.toMutableMap()
    val source = byId[sourceAccount.id] ?: sourceAccount
    val steps = mutableListOf<PaycheckStepUi>()
    var remaining = amountCents.coerceAtLeast(0L)

    // 本次执行中各账户新增 earmark（供同轮后续规则实时扣上限）
    val addedEarmarkByHolder = mutableMapOf<Long, Long>()

    val sourceBalance: () -> Long = { byId[source.id]?.balanceCents ?: 0L }

    // 从来源账户扣款：实际额 = min(计划额, 剩余可分配池, 来源可用余额)
    fun debitSource(x: Long): Long {
        val avail = sourceBalance().coerceAtLeast(0L).coerceAtMost(remaining)
        val actual = x.coerceIn(0L, avail)
        byId[source.id] = (byId[source.id] ?: source).copy(balanceCents = (byId[source.id] ?: source).balanceCents - actual)
        remaining -= actual
        return actual
    }

    // ---- 规则区 ----
    for (rule in scheme.rules) {
        val planned = when (rule.kind) {
            PaycheckRuleKind.TO_ACCOUNT_FIXED, PaycheckRuleKind.TO_PLAN_FIXED -> rule.amountCents.coerceAtLeast(0L)
            PaycheckRuleKind.TO_ACCOUNT_PCT, PaycheckRuleKind.TO_PLAN_PCT ->
                amountCents * rule.percentBps.coerceAtLeast(0) / 10_000L
        }
        if (planned <= 0L || remaining <= 0L) continue

        when (rule.kind) {
            PaycheckRuleKind.TO_ACCOUNT_FIXED, PaycheckRuleKind.TO_ACCOUNT_PCT -> {
                val target = byId[rule.targetId]
                if (target == null) continue
                if (target.id == source.id) {
                    steps += PaycheckStepUi(PaycheckStepKind.TO_ACCOUNT, target.id, 0L, target.name, "自转跳过")
                    continue
                }
                val actual = debitSource(planned)
                if (actual <= 0L) {
                    steps += PaycheckStepUi(PaycheckStepKind.TO_ACCOUNT, target.id, 0L, target.name, "来源资金不足")
                    continue
                }
                byId[target.id] = target.copy(balanceCents = target.balanceCents + actual)
                val note = if (actual < planned) "余额不足，部分转入" else null
                steps += PaycheckStepUi(PaycheckStepKind.TO_ACCOUNT, target.id, actual, target.name, note)
            }

            PaycheckRuleKind.TO_PLAN_FIXED, PaycheckRuleKind.TO_PLAN_PCT -> {
                val plan = allPlans.firstOrNull { it.id == rule.targetId }
                if (plan == null) continue
                val holder = byId[plan.holderAccountId]
                if (holder == null) continue

                // 专款账户当前全部 earmark（静态既有 + 本轮已新增）
                val staticOnHolder = allPlans
                    .filter { it.holderAccountId == holder.id }
                    .sumOf { it.earmarkedCents }
                val totalOnHolder = staticOnHolder + (addedEarmarkByHolder[holder.id] ?: 0L)
                val isSelfHold = holder.id == source.id

                // 圈定上限（E2/D5 同 depositToPlan 口径：Σearmark ≤ 账户余额）
                val slack = holder.balanceCents - totalOnHolder

                if (isSelfHold) {
                    // 自持：钱不动，仅 earmark 增加并消耗可分配池
                    if (slack <= 0L) {
                        steps += PaycheckStepUi(PaycheckStepKind.TO_PLAN, plan.id, 0L, plan.name, "已达可攒上限")
                        continue
                    }
                    val actual = planned.coerceAtMost(slack).coerceAtMost(remaining)
                    if (actual <= 0L) continue
                    remaining -= actual
                    addedEarmarkByHolder[holder.id] = totalOnHolder - staticOnHolder + actual
                    val note = if (actual < planned) "已达上限，部分攒入" else null
                    steps += PaycheckStepUi(PaycheckStepKind.TO_PLAN, plan.id, actual, plan.name, note)
                } else {
                    // 跨账户：先把钱真实转入专款账户再圈定
                    if (slack < 0L) {
                        // 专款账户既有超额（余额低于既有 earmark），先补回再执行本条
                        steps += PaycheckStepUi(PaycheckStepKind.TO_PLAN, plan.id, 0L, plan.name, "专款账户超额，先补回")
                        continue
                    }
                    val actual = debitSource(planned)
                    if (actual <= 0L) {
                        steps += PaycheckStepUi(PaycheckStepKind.TO_PLAN, plan.id, 0L, plan.name, "来源资金不足")
                        continue
                    }
                    byId[holder.id] = holder.copy(balanceCents = holder.balanceCents + actual)
                    addedEarmarkByHolder[holder.id] = totalOnHolder - staticOnHolder + actual
                    val note = if (actual < planned) "余额不足，部分攒入" else null
                    steps += PaycheckStepUi(PaycheckStepKind.TO_PLAN, plan.id, actual, plan.name, note)
                }
            }
        }
    }

    // ---- 剩余自动清欠（E4）：按账户 sortOrder，负余额转到 0 ----
    if (scheme.autoClearDebts && remaining > 0L) {
        val debtors = allAccounts
            .filter { it.id != source.id && it.balanceCents < 0L }
            .sortedBy { it.sortOrder }
        for (debtor in debtors) {
            if (remaining <= 0L) break
            val target = byId[debtor.id] ?: continue
            if (target.balanceCents >= 0L) continue
            val need = -target.balanceCents
            val actual = debitSource(need)
            if (actual <= 0L) continue
            byId[target.id] = target.copy(balanceCents = target.balanceCents + actual)
            steps += PaycheckStepUi(PaycheckStepKind.CLEAR_DEBT, target.id, actual, target.name)
        }
    }

    // ---- 留存（E5）----
    val stayed = remaining.coerceAtLeast(0L)
    if (stayed > 0L) {
        steps += PaycheckStepUi(PaycheckStepKind.REMAIN, null, stayed, source.name, "留存来源账户")
    }

    val allocated = steps.filter { it.kind != PaycheckStepKind.REMAIN }.sumOf { it.amountCents }
    return PaycheckPlanResult(
        // 保留受限 0 金额步骤（note 标注原因，UI 降透明度呈现）；无 note 的 0 金额步骤不产生
        steps = steps.filter { it.kind == PaycheckStepKind.REMAIN || it.amountCents > 0L || !it.note.isNullOrBlank() },
        totalAllocatedCents = allocated,
        remainingCents = stayed
    )
}

// ---------------------------------------------------------------------------
// JSON 序列化（与账户 JSON 同为 org.json 风格，存入 PreferencesRepository）
// ---------------------------------------------------------------------------

fun serializeSavingPlans(plans: List<SavingPlanUiModel>): String {
    val array = JSONArray()
    plans.forEach { p ->
        array.put(
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("holderAccountId", p.holderAccountId)
                .put("targetAmountCents", p.targetAmountCents)
                .put("earmarkedCents", p.earmarkedCents)
                .put("colorHex", p.colorHex)
                .put("sortOrder", p.sortOrder)
                .put("createdAt", p.createdAt)
        )
    }
    return array.toString()
}

fun parseSavingPlans(json: String?): List<SavingPlanUiModel> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SavingPlanUiModel(
                id = o.optLong("id", i.toLong() + 1L),
                name = o.optString("name", "攒钱计划"),
                holderAccountId = o.optLong("holderAccountId", 0L),
                targetAmountCents = o.optLong("targetAmountCents", 0L),
                earmarkedCents = o.optLong("earmarkedCents", 0L),
                colorHex = o.optLong("colorHex", 0xFF059669L),
                sortOrder = o.optInt("sortOrder", i + 1),
                createdAt = o.optLong("createdAt", 0L)
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

fun serializePaycheckScheme(scheme: PaycheckSchemeUiModel): String =
    JSONObject()
        .put("autoClearDebts", scheme.autoClearDebts)
        .put(
            "rules",
            JSONArray().apply {
                scheme.rules.forEach { r ->
                    put(
                        JSONObject()
                            .put("kind", r.kind.name)
                            .put("targetId", r.targetId)
                            .put("amountCents", r.amountCents)
                            .put("percentBps", r.percentBps)
                    )
                }
            }
        )
        .toString()

fun parsePaycheckScheme(json: String?): PaycheckSchemeUiModel {
    if (json.isNullOrBlank()) return PaycheckSchemeUiModel()
    return try {
        val o = JSONObject(json)
        val rulesArray = o.optJSONArray("rules") ?: JSONArray()
        val rules = (0 until rulesArray.length()).mapNotNull { i ->
            val r = rulesArray.getJSONObject(i)
            val kind = runCatching { PaycheckRuleKind.valueOf(r.optString("kind", "")) }.getOrNull()
                ?: return@mapNotNull null
            PaycheckRuleUiModel(
                kind = kind,
                targetId = r.optLong("targetId", 0L),
                amountCents = r.optLong("amountCents", 0L),
                percentBps = r.optInt("percentBps", 0)
            )
        }
        PaycheckSchemeUiModel(rules = rules, autoClearDebts = o.optBoolean("autoClearDebts", true))
    } catch (e: Exception) {
        e.printStackTrace()
        PaycheckSchemeUiModel()
    }
}

fun serializePaycheckLastRun(run: PaycheckLastRunUiModel): String =
    JSONObject()
        .put("at", run.at ?: JSONObject.NULL)
        .put("amountCents", run.amountCents ?: JSONObject.NULL)
        .put("actionCount", run.actionCount ?: JSONObject.NULL)
        .put("remainingCents", run.remainingCents ?: JSONObject.NULL)
        .toString()

fun parsePaycheckLastRun(json: String?): PaycheckLastRunUiModel {
    if (json.isNullOrBlank()) return PaycheckLastRunUiModel()
    return try {
        val o = JSONObject(json)
        PaycheckLastRunUiModel(
            at = if (o.has("at") && !o.isNull("at")) o.getLong("at") else null,
            amountCents = if (o.has("amountCents") && !o.isNull("amountCents")) o.getLong("amountCents") else null,
            actionCount = if (o.has("actionCount") && !o.isNull("actionCount")) o.getInt("actionCount") else null,
            remainingCents = if (o.has("remainingCents") && !o.isNull("remainingCents")) o.getLong("remainingCents") else null
        )
    } catch (e: Exception) {
        e.printStackTrace()
        PaycheckLastRunUiModel()
    }
}
