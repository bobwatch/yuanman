package com.yuanman.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.yuanman.app.R
import com.yuanman.app.data.model.BudgetReviewStatus
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class BudgetReviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetSnapshotLoader.load(context)
                appWidgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, buildViews(context, snapshot))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val review = snapshot.budgetReview
        val hidden = snapshot.privacyMode
        val hasBudget = review.budgetCents > 0L
        val statusText = when {
            hidden -> "隐私模式"
            !hasBudget -> "待设置"
            review.status == BudgetReviewStatus.OVER_BUDGET -> "已经超支"
            review.status == BudgetReviewStatus.SPENDING_FAST -> "支出偏快"
            review.status == BudgetReviewStatus.ON_TRACK -> "节奏稳健"
            else -> "本月复盘"
        }
        val remainingTitle = if (review.remainingCents >= 0L) "预算剩余" else "超支金额"
        val hint = when {
            hidden -> "打开沅满查看本月预算详情"
            !hasBudget -> "设置预算后自动分析每日可用与月末预计"
            review.remainingCents < 0L -> "已超出预算 ${MoneyUtils.formatCurrency(abs(review.remainingCents))}"
            review.remainingDays > 0 -> "剩余 ${review.remainingDays} 天 · 每天可用 ${MoneyUtils.formatCurrency(review.dailyAvailableCents)}"
            else -> "本月已结算 · 点击查看完整统计"
        }

        return RemoteViews(context.packageName, R.layout.widget_budget_review).apply {
            setTextViewText(R.id.widget_budget_month, "${snapshot.year}年${snapshot.month}月")
            setTextViewText(R.id.widget_budget_status, statusText)
            setTextViewText(
                R.id.widget_budget_expense,
                if (hidden) "¥ ••••" else MoneyUtils.formatCurrency(snapshot.totalExpense)
            )
            setTextViewText(R.id.widget_budget_remaining_label, remainingTitle)
            setTextViewText(
                R.id.widget_budget_remaining,
                when {
                    hidden -> "••••"
                    !hasBudget -> "未设置"
                    else -> MoneyUtils.formatCurrency(abs(review.remainingCents))
                }
            )
            setTextViewText(
                R.id.widget_budget_projection,
                when {
                    hidden -> "••••"
                    !hasBudget -> "--"
                    else -> MoneyUtils.formatCurrency(review.projectedExpenseCents)
                }
            )
            setTextViewText(R.id.widget_budget_hint, hint)
            setProgressBar(
                R.id.widget_budget_progress,
                100,
                if (hidden || !hasBudget) 0 else (review.usedPercent * 100).toInt().coerceIn(0, 100),
                false
            )
            setOnClickPendingIntent(R.id.widget_budget_container, WidgetNavigation.statistics(context))
            setOnClickPendingIntent(R.id.widget_budget_expense_action, WidgetNavigation.quickExpense(context))
            setOnClickPendingIntent(R.id.widget_budget_income_action, WidgetNavigation.quickIncome(context))
        }
    }
}
