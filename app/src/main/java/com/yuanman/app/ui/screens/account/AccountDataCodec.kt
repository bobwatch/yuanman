package com.yuanman.app.ui.screens.account

import com.yuanman.app.ui.components.AccountIconHelper
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

/**
 * 支付方式 → 账户唯一匹配引擎（严禁一笔流水广播给多个账户）：
 * 1. 完全精确匹配（忽略大小写与首尾空格）：account.name == method
 * 2. 包含匹配：仅当全账户中【有且仅有1个账户】与支付方式存在包含关系时才判定命中；若有多个（如“招商银行卡”与“工商银行卡”），绝不任意命中或多扣
 * 3. 历史通用渠道兜底（如历史流水仅有“银行卡/微信/支付宝/现金”）：
 *    仅当全账户中该类型账户【唯一存在】时才归属；一旦存在2个或以上同类型账户，坚决不匹配，避免串账或重复扣除！
 */
internal fun findAccountForMethod(methodRaw: String, accounts: List<AccountUiModel>): AccountUiModel? {
    val method = methodRaw.trim()
    if (method.isBlank() || accounts.isEmpty()) return null

    // 1. 完全精确匹配
    val exact = accounts.firstOrNull { it.name.equals(method, ignoreCase = true) }
    if (exact != null) return exact

    // 2. 包含匹配（且全账户中唯一满足包含关系的账户）
    val containsMatches = accounts.filter {
        it.name.contains(method, ignoreCase = true) || method.contains(it.name, ignoreCase = true)
    }
    if (containsMatches.size == 1) return containsMatches.first()

    // 3. 通用渠道唯一兜底
    val typeMatches = accounts.filter { acc ->
        when {
            method.contains("微信") -> acc.name.contains("微信") || acc.iconName == "wechat"
            method.contains("支付宝") -> acc.name.contains("支付宝") || acc.iconName == "alipay"
            method.contains("现金") -> acc.name.contains("现金") || acc.iconName == "cash"
            (method.contains("卡") || method.contains("银行")) ->
                (acc.name.contains("行") || acc.name.contains("卡") || acc.iconName == "bank_card" || acc.iconName == "unionpay")
            else -> false
        }
    }
    if (typeMatches.size == 1) return typeMatches.first()

    return null
}

/** 
 * 支付方式是否归属于目标账户（支持传入上下文全账户进行消歧）：
 * 只有当目标账户是该流水在所有账户中的唯一匹配目标时才返回 true
 */
internal fun methodMatchesAccount(
    methodRaw: String,
    account: AccountUiModel,
    allAccounts: List<AccountUiModel> = listOf(account)
): Boolean {
    val method = methodRaw.trim()
    if (method.isBlank()) return false
    if (allAccounts.size > 1) {
        val matched = findAccountForMethod(method, allAccounts)
        return matched?.id == account.id
    }
    // 单账户无歧义上下文时，仅允许精确匹配或包含匹配，严禁宽泛的泛银行卡模糊扣款
    return method.equals(account.name, ignoreCase = true) ||
        (account.name.contains(method, ignoreCase = true) && !method.contains("卡") && !method.contains("银行")) ||
        method.contains(account.name, ignoreCase = true)
}

/** 是否属于「工资/薪」类收入分类（触发自动分账的识别口径，v0.0.4.5）：
 *  分类名含 工资/薪 即命中，覆盖默认「工资」分类与用户自建的「月薪/薪资/薪酬」等；
 *  红包/理财/退款/兼职外快等其余收入不触发 */
internal fun isSalaryCategoryName(categoryName: String): Boolean {
    val name = categoryName.trim()
    return name.contains("工资") || name.contains("薪")
}

/**
 * 按当月流水富化账户（入/出/余额/对账派生字段）。
 * 遵循「单流水唯一归属」原则，每笔流水至多扣除/记入一个账户，彻底杜绝多账户重复扣除。
 * @param monthRecords 当月流水（RecordWithCategory）
 */
internal fun enrichAccountsForMonth(
    rawAccounts: List<AccountUiModel>,
    monthRecords: List<com.yuanman.app.data.local.entity.RecordWithCategory>,
    globalReconcileCycle: ReconcileCycle
): List<AccountUiModel> {
    val inMap = mutableMapOf<Long, Long>()
    val outMap = mutableMapOf<Long, Long>()

    // 每笔流水通过唯一匹配引擎仅归属于一个账户
    monthRecords.forEach { recordWithCat ->
        val r = recordWithCat.record
        val matched = findAccountForMethod(r.paymentMethod, rawAccounts)
        if (matched != null) {
            if (r.type == "INCOME") {
                inMap[matched.id] = (inMap[matched.id] ?: 0L) + r.amount
            } else if (r.type == "EXPENSE") {
                outMap[matched.id] = (outMap[matched.id] ?: 0L) + r.amount
            }
        }
    }

    return rawAccounts.map { account ->
        val monthIn = inMap[account.id] ?: 0L
        val monthOut = outMap[account.id] ?: 0L

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
            val rawIconName = obj.optString("iconName", "wallet")
            result.add(
                AccountUiModel(
                    id = obj.optLong("id", i.toLong() + 1L),
                    name = obj.optString("name", "账户"),
                    label = obj.optString("label", ""),
                    // 已下架品牌（淘宝/百度）在读取时归一化，避免老账户渲染成空白图标
                    iconName = AccountIconHelper.RETIRED_BRAND_ICONS[rawIconName] ?: rawIconName,
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
