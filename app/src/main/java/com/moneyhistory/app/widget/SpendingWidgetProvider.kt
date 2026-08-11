package com.moneyhistory.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneyhistory.app.MainActivity
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.TransactionStore
import java.util.Calendar

/**
 * 桌面 Widget：今日支出 + 本月支出。
 *
 * 框架自带 RemoteViews 实现（零第三方依赖）；
 * 数据直接读 [TransactionStore]，流水变更后由 [notifyChanged] 主动刷新。
 */
class SpendingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        /** 流水变更后调用：刷新全部 Widget 实例。 */
        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SpendingWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) updateAll(context, manager, ids)
        }

        private fun updateAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH)
            val day = now.get(Calendar.DAY_OF_MONTH)
            val cal = Calendar.getInstance()

            var todayExpense = 0L
            var monthExpense = 0L
            TransactionStore.getInstance(context).all().forEach { t ->
                if (t.type != Transaction.Type.EXPENSE) return@forEach
                cal.timeInMillis = t.timestamp
                if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                    monthExpense += t.amountCents
                    if (cal.get(Calendar.DAY_OF_MONTH) == day) {
                        todayExpense += t.amountCents
                    }
                }
            }

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_spending)
                views.setTextViewText(
                    R.id.widget_today,
                    context.getString(
                        R.string.widget_today,
                        MoneyUtils.formatCents(todayExpense)
                    )
                )
                views.setTextViewText(
                    R.id.widget_month,
                    context.getString(
                        R.string.widget_month,
                        MoneyUtils.formatCents(monthExpense)
                    )
                )

                // 点击整体 → 打开首页
                val openIntent = Intent(context, MainActivity::class.java)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                // 点击 + → 直达记账 BottomSheet
                val addIntent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_ADD)
                views.setOnClickPendingIntent(
                    R.id.widget_add,
                    PendingIntent.getActivity(
                        context, 1, addIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
