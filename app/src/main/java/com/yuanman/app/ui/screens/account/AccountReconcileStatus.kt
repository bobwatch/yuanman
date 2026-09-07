package com.yuanman.app.ui.screens.account

import java.util.Calendar

/**
 * 对账时效口径（设计文档 §7.1，页面横幅 / 账户行副行 / 操作面板 / 待核对底包共用）。
 * 纯数据判定，不含任何主题色 —— 颜色映射由各 UI 组件按语义角色自行落地。
 *
 * 判定基准：按自然月差（与旧 calculateReconcileTag 一致）：
 * - 最近核对月 == 当月        → FRESH    "本月已核对"
 * - 最近核对月 == 上月        → NORMAL   "上月已核对"
 * - 最近核对月早于上月         → OVERDUE  "N 天未对账"（自然日差，至少 1 天）
 * - lastReconciledAt == null  → NEVER    "从未对账"
 */
enum class ReconcileTone { FRESH, NORMAL, OVERDUE, NEVER }

data class AccountReconcileStatus(val text: String, val tone: ReconcileTone)

/** 逾期口径 = OVERDUE 或 NEVER（二者都进入页面横幅与「待核对账户」底包） */
fun AccountReconcileStatus.isPending(): Boolean =
    tone == ReconcileTone.OVERDUE || tone == ReconcileTone.NEVER

fun accountReconcileStatus(lastReconciledAt: Long?, now: Long = System.currentTimeMillis()): AccountReconcileStatus {
    if (lastReconciledAt == null) {
        return AccountReconcileStatus(text = "从未对账", tone = ReconcileTone.NEVER)
    }

    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val recCal = Calendar.getInstance().apply { timeInMillis = lastReconciledAt }
    val monthDiff = (nowCal.get(Calendar.YEAR) - recCal.get(Calendar.YEAR)) * 12 +
        (nowCal.get(Calendar.MONTH) - recCal.get(Calendar.MONTH))

    return when {
        // 时钟回拨等极端情况按已核对处理，避免误报"逾期"
        monthDiff <= 0 -> AccountReconcileStatus(text = "本月已核对", tone = ReconcileTone.FRESH)
        monthDiff == 1 -> AccountReconcileStatus(text = "上月已核对", tone = ReconcileTone.NORMAL)
        else -> {
            val days = ((now - lastReconciledAt) / 86_400_000L).coerceAtLeast(1L)
            AccountReconcileStatus(text = "$days 天未对账", tone = ReconcileTone.OVERDUE)
        }
    }
}
