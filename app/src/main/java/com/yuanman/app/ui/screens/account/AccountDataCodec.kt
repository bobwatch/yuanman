package com.yuanman.app.ui.screens.account

import org.json.JSONArray
import org.json.JSONObject

/**
 * 账户 JSON 编解码 + 当月流水富化 —— 共享契约（AccountViewModel 组装、记账后自动分账、
 * 发薪页手动补分共用同一实现，避免口径漂移）。
 *
 * 富化口径与设计文档一致：
 *  - 余额 = 期初（对账校正已并入 openingBalance）+ 当月入 - 当月出（回退账户已存 in/out 分量）；
 *  - 对账信息以 reconcileRecords 最新一条为准（兼容旧版无 records 时回退 lastReconciledAt 字段）；
 *  - reconcileStatus 按生效周期计算（账户 override ?: 全局）。
 */

/** 支付方式 → 账户命中启发式（记账流水按月聚合与本月的收入候选匹配共用，保证口径一致） */
internal fun methodMatchesAccount(methodRaw: String, account: AccountUiModel): Boolean {
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

internal fun findAccountForMethod(method: String, accounts: List<AccountUiModel>): AccountUiModel? =
    accounts.firstOrNull { methodMatchesAccount(method, it) }

/** 是否属于「工资/薪」类收入分类（触发自动分账的识别口径，v0.0.4.5）：
 *  分类名含 工资/薪 即命中，覆盖默认「工资」分类与用户自建的「月薪/薪资/薪酬」等；
 *  红包/理财/退款/兼职外快等其余收入不触发 */
internal fun isSalaryCategoryName(categoryName: String): Boolean {
    val name = categoryName.trim()
    return name.contains("工资") || name.contains("薪")
}

/**
 * 按当月流水富化账户（入/出/余额/对账派生字段）。
 * @param monthRecords 当月流水（RecordWithCategory）
 */
internal fun enrichAccountsForMonth(
    rawAccounts: List<AccountUiModel>,
    monthRecords: List<com.yuanman.app.data.local.entity.RecordWithCategory>,
    globalReconcileCycle: ReconcileCycle
): List<AccountUiModel> = rawAccounts.map { account ->
    var monthIn = 0L
    var monthOut = 0L

    monthRecords.forEach { recordWithCat ->
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

    val records = account.reconcileRecords.sortedByDescending { it.asOfDate }
    val latest = records.firstOrNull()
    val effectiveLastAt = latest?.asOfDate ?: account.lastReconciledAt
    val effectiveLastDiff = latest?.diffCents ?: account.lastReconciledDiffCents
    val cycle = effectiveCycleFor(account.reconcileCycleOverride, globalReconcileCycle)

    account.copy(
        inCents = finalIn,
        outCents = finalOut,
        balanceCents = effectiveBalance,
        reconcileRecords = records,
        lastReconciledAt = effectiveLastAt,
        lastReconciledDiffCents = effectiveLastDiff,
        reconcileStatus = accountReconcileStatus(effectiveLastAt, cycle)
    )
}

internal fun serializeAccountsJson(accounts: List<AccountUiModel>): String {
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
        if (acc.reconcileTipSkipUntil != null) {
            obj.put("reconcileTipSkipUntil", acc.reconcileTipSkipUntil)
        }
        if (acc.reconcileCycleOverride != null) {
            obj.put("reconcileCycleOverride", acc.reconcileCycleOverride.toJson())
        }
        putReconcileRecords(obj, acc.reconcileRecords)
        obj.put("sortOrder", acc.sortOrder)
        array.put(obj)
    }
    return array.toString()
}

fun parseAccountsJson(jsonStr: String?): List<AccountUiModel> {
    if (jsonStr.isNullOrBlank()) return emptyList()
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
                    sortOrder = obj.optInt("sortOrder", i + 1),
                    reconcileCycleOverride = ReconcileCycle.fromJson(obj.optJSONObject("reconcileCycleOverride")),
                    reconcileTipSkipUntil = if (obj.has("reconcileTipSkipUntil")) obj.getLong("reconcileTipSkipUntil") else null,
                    reconcileRecords = parseReconcileRecords(obj)
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}
