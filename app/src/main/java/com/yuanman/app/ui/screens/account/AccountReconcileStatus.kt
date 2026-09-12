package com.yuanman.app.ui.screens.account

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 对账时效口径（纯数据判定，不含任何主题色 —— 颜色映射由各 UI 组件按语义角色自行落地）。
 *
 * 判定基准：账户各自持有「生效对账周期」（账户自定义覆盖 ?: 全局默认），周期由
 * 单位（周/月/季度/半年/年）× 数量组成，如「每 2 周」「每季度」；周/月单位数量为 1 时
 * 按自然周/自然月锚定（与旧按月口径一致），其余按滑动窗口计算：
 * - FRESH：距上次核对不足 1 个周期（月×1 = 同一自然月）→ 「本周期已核对」
 * - NORMAL：距上次核对超过 1 个周期但不足 2 个周期（月×1 = 上月）→ 「上周期已核对」
 * - OVERDUE：距上次核对 ≥ 2 个周期（月×1 = 早于上月）→ 「N 期未对账」
 * - NEVER：lastReconciledAt == null → 「从未对账」
 */
enum class ReconcileTone { FRESH, NORMAL, OVERDUE, NEVER }

data class AccountReconcileStatus(val text: String, val tone: ReconcileTone)

/** 逾期口径 = OVERDUE 或 NEVER（二者都进入页面横幅与「待核对账户」底包） */
fun AccountReconcileStatus.isPending(): Boolean =
    tone == ReconcileTone.OVERDUE || tone == ReconcileTone.NEVER

// ---------------------------------------------------------------------------
// 对账周期（全局默认 + 每账户自定义覆盖）
// ---------------------------------------------------------------------------

/** 周期单位；月族单位以自然月为锚（季=3月、半年=6月、年=12月），周独立按自然周滚动 */
enum class ReconcileCycleUnit(val label: String, val monthSpan: Int = 0) {
    WEEK("周"),
    MONTH("月", 1),
    QUARTER("季度", 3),
    HALF_YEAR("半年", 6),
    YEAR("年", 12);

    companion object {
        fun fromName(name: String?): ReconcileCycleUnit? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * 对账周期 = 数量 × 单位。如 count=1/MONTH「每月」、count=2/WEEK「每 2 周」。
 * 数量可选任意正数：月族会在数量=3/6/12… 等整倍数时自动「升位」展示为更大单位
 * （每 6 个月 → 每半年、每 12 个月 → 每年），语义等价、存储归一为最小粒度。
 */
data class ReconcileCycle(
    val count: Int = 1,
    val unit: ReconcileCycleUnit = ReconcileCycleUnit.MONTH
) {
    /** 月族总月数（周为 0，不可与月族混比） */
    val totalMonths: Int get() = unit.monthSpan * count

    val label: String
        get() = if (unit == ReconcileCycleUnit.WEEK) {
            if (count == 1) "每周" else "每 $count 周"
        } else {
            when {
                count == 1 -> when (unit) {
                    ReconcileCycleUnit.MONTH -> "每月"
                    ReconcileCycleUnit.QUARTER -> "每季度"
                    ReconcileCycleUnit.HALF_YEAR -> "每半年"
                    else -> "每年"
                }
                unit == ReconcileCycleUnit.MONTH -> "每 $count 个月"
                unit == ReconcileCycleUnit.QUARTER -> "每 $count 季度"
                unit == ReconcileCycleUnit.HALF_YEAR -> "每 $count 个半年"
                else -> "每 $count 年"
            }
        }

    fun toJson(): JSONObject = JSONObject().put("count", count).put("unit", unit.name)

    companion object {
        /** 全局默认：每月 */
        val DEFAULT = ReconcileCycle(1, ReconcileCycleUnit.MONTH)

        fun fromJson(o: JSONObject?): ReconcileCycle? {
            if (o == null) return null
            val unit = ReconcileCycleUnit.fromName(o.optString("unit")) ?: return null
            return ReconcileCycle(count = o.optInt("count", 1).coerceAtLeast(1), unit = unit)
        }
    }
}

/** 账户生效周期 = 账户自定义覆盖 ?: 全局默认 */
fun effectiveCycleFor(accountOverride: ReconcileCycle?, global: ReconcileCycle): ReconcileCycle =
    accountOverride ?: global

// ---------------------------------------------------------------------------
// 每期对账记录（对账动作的审计痕迹，纯记录不参与余额计算）
// ---------------------------------------------------------------------------

data class ReconcileRecordUiModel(
    val id: Long,
    /** 对账日；账面余额为截至该时点的流水合计。 */
    val asOfDate: Long,
    /** 用户输入的真实余额（分）。 */
    val actualBalanceCents: Long,
    /** 核对时点账面余额（期初 + 截至 asOfDate 的流水，分）。 */
    val bookBalanceCents: Long,
    /** 差额 = 实际 − 账面（可负）。 */
    val diffCents: Long,
    /** 差额是否已并入期初校正（对账日之后「按真实余额校正」的操作痕迹）。 */
    val corrected: Boolean
)

/** 写入账户 JSON 的 records 字段；列表按 asOfDate 降序存放（最新在前） */
fun putReconcileRecords(o: JSONObject, records: List<ReconcileRecordUiModel>) {
    if (records.isEmpty()) return
    val array = JSONArray()
    records.sortedByDescending { it.asOfDate }.forEach { r ->
        array.put(
            JSONObject()
                .put("id", r.id)
                .put("asOfDate", r.asOfDate)
                .put("actualBalanceCents", r.actualBalanceCents)
                .put("bookBalanceCents", r.bookBalanceCents)
                .put("diffCents", r.diffCents)
                .put("corrected", r.corrected)
        )
    }
    o.put("reconcileRecords", array)
}

fun parseReconcileRecords(o: JSONObject): List<ReconcileRecordUiModel> {
    val array = o.optJSONArray("reconcileRecords") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val r = array.getJSONObject(i)
        runCatching {
            ReconcileRecordUiModel(
                id = r.optLong("id", i.toLong() + 1L),
                asOfDate = r.optLong("asOfDate", 0L),
                actualBalanceCents = r.optLong("actualBalanceCents", 0L),
                bookBalanceCents = r.optLong("bookBalanceCents", 0L),
                diffCents = r.optLong("diffCents", 0L),
                corrected = r.optBoolean("corrected", false)
            )
        }.getOrNull()
    }
        .sortedByDescending { it.asOfDate }
}

// ---------------------------------------------------------------------------
// 周期化状态判定
// ---------------------------------------------------------------------------

/** 月族周期的自然月差（与旧按月口径一致）；时钟回拨等极端情况返回 0 */
private fun monthsBetweenMs(last: Long, now: Long): Int {
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val recCal = Calendar.getInstance().apply { timeInMillis = last }
    val diff = (nowCal.get(Calendar.YEAR) - recCal.get(Calendar.YEAR)) * 12 +
        (nowCal.get(Calendar.MONTH) - recCal.get(Calendar.MONTH))
    return diff.coerceAtLeast(0)
}

/** 周周期：以 7 天为桶边界求桶差（两时间戳同偏移，桶差不受时区影响） */
private fun weeksBetweenMs(last: Long, now: Long): Int =
    ((now / (86_400_000L * 7L)) - (last / (86_400_000L * 7L))).toInt()

/**
 * 按周期判定对账状态。
 * 月族：d = 自然月差；周：d = 自然周差。
 * FRESH: d < K；NORMAL: K ≤ d < 2K；OVERDUE: d ≥ 2K（错过 ≥1 整期后提醒）；
 * NEVER: 从未对账。
 */
fun accountReconcileStatus(
    lastReconciledAt: Long?,
    cycle: ReconcileCycle = ReconcileCycle.DEFAULT,
    now: Long = System.currentTimeMillis()
): AccountReconcileStatus {
    if (lastReconciledAt == null) {
        return AccountReconcileStatus(text = "从未对账", tone = ReconcileTone.NEVER)
    }

    val d = if (cycle.unit == ReconcileCycleUnit.WEEK) {
        weeksBetweenMs(lastReconciledAt, now)
    } else {
        monthsBetweenMs(lastReconciledAt, now)
    }
    val k = if (cycle.unit == ReconcileCycleUnit.WEEK) cycle.count else cycle.totalMonths
    if (k <= 0) return AccountReconcileStatus(text = "本期已核对", tone = ReconcileTone.FRESH)

    return when {
        d < k -> {
            val word = if (cycle.unit == ReconcileCycleUnit.WEEK && cycle.count == 1) "本周"
            else if (cycle.unit == ReconcileCycleUnit.MONTH && cycle.count == 1) "本月"
            else "本期"
            AccountReconcileStatus(text = "${word}已核对", tone = ReconcileTone.FRESH)
        }
        d < 2 * k -> {
            val word = if (cycle.unit == ReconcileCycleUnit.WEEK && cycle.count == 1) "上周"
            else if (cycle.unit == ReconcileCycleUnit.MONTH && cycle.count == 1) "上月"
            else "上期"
            AccountReconcileStatus(text = "${word}已核对", tone = ReconcileTone.NORMAL)
        }
        else -> {
            val missed = (d / k).coerceAtLeast(2)
            AccountReconcileStatus(text = "$missed 期未对账", tone = ReconcileTone.OVERDUE)
        }
    }
}

/** 跳过提醒的截止时刻 = 当前所在周期的期末（回到下期起始后提醒恢复） */
fun periodEndEpoch(cycle: ReconcileCycle, now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    return when (cycle.unit) {
        ReconcileCycleUnit.WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.add(Calendar.DAY_OF_MONTH, 7 * cycle.count)
            cal.timeInMillis
        }
        ReconcileCycleUnit.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, cycle.count)
            cal.timeInMillis
        }
        ReconcileCycleUnit.QUARTER -> {
            val quarterStartMonth = (cal.get(Calendar.MONTH) / 3) * 3
            cal.set(Calendar.MONTH, quarterStartMonth)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 3 * cycle.count)
            cal.timeInMillis
        }
        ReconcileCycleUnit.HALF_YEAR -> {
            val halfStartMonth = (cal.get(Calendar.MONTH) / 6) * 6
            cal.set(Calendar.MONTH, halfStartMonth)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 6 * cycle.count)
            cal.timeInMillis
        }
        ReconcileCycleUnit.YEAR -> {
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.YEAR, cycle.count)
            cal.timeInMillis
        }
    }
}
