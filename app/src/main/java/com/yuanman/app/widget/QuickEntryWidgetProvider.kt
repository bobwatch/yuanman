package com.yuanman.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.yuanman.app.R
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickEntryWidgetProvider : AppWidgetProvider() {
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
        return RemoteViews(context.packageName, R.layout.widget_quick_entry).apply {
            setTextViewText(R.id.widget_quick_month, "${snapshot.month}月 · ${snapshot.recordCount}笔")
            setTextViewText(
                R.id.widget_quick_amount,
                if (snapshot.privacyMode) "¥ ••••" else MoneyUtils.formatCurrency(snapshot.totalExpense)
            )
            setOnClickPendingIntent(R.id.widget_quick_container, WidgetNavigation.home(context))
            setOnClickPendingIntent(R.id.widget_quick_expense, WidgetNavigation.quickExpense(context))
            setOnClickPendingIntent(R.id.widget_quick_income, WidgetNavigation.quickIncome(context))
        }
    }
}
