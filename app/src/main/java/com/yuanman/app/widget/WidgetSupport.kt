package com.yuanman.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.yuanman.app.MainActivity
import com.yuanman.app.YuanmanApplication
import com.yuanman.app.data.model.BudgetReviewCalculator
import com.yuanman.app.data.model.BudgetReviewData
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.navigation.Screen
import com.yuanman.app.utils.DateTimeUtils
import java.util.Calendar

data class WidgetSnapshot(
    val year: Int,
    val month: Int,
    val totalExpense: Long,
    val totalIncome: Long,
    val recordCount: Int,
    val privacyMode: Boolean,
    val budgetReview: BudgetReviewData
)

object WidgetSnapshotLoader {
    suspend fun load(context: Context): WidgetSnapshot {
        val app = context.applicationContext as YuanmanApplication
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val summary = app.database.recordDao().getWidgetMonthSummary(
            startTime = DateTimeUtils.getMonthStartTimestamp(year, month),
            endTime = DateTimeUtils.getMonthEndTimestamp(year, month)
        )
        val preferences = app.preferencesRepository.getWidgetPreferences(year, month)
        return WidgetSnapshot(
            year = year,
            month = month,
            totalExpense = summary.totalExpense,
            totalIncome = summary.totalIncome,
            recordCount = summary.recordCount,
            privacyMode = preferences.privacyMode,
            budgetReview = BudgetReviewCalculator.calculate(
                year = year,
                month = month,
                budgetCents = preferences.monthlyBudget,
                expenseCents = summary.totalExpense
            )
        )
    }
}

object WidgetNavigation {
    const val EXTRA_ROUTE = "com.yuanman.app.extra.WIDGET_ROUTE"

    fun quickExpense(context: Context): PendingIntent = activity(
        context = context,
        route = Screen.AddEditRecord.createRoute(type = RecordType.EXPENSE),
        requestCode = 201
    )

    fun quickIncome(context: Context): PendingIntent = activity(
        context = context,
        route = Screen.AddEditRecord.createRoute(type = RecordType.INCOME),
        requestCode = 202
    )

    fun statistics(context: Context): PendingIntent = activity(
        context = context,
        route = Screen.Statistics.route,
        requestCode = 203
    )

    fun home(context: Context): PendingIntent = activity(
        context = context,
        route = Screen.Home.route,
        requestCode = 204
    )

    private fun activity(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object WidgetUpdateManager {
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        listOf(
            ComponentName(appContext, QuickEntryWidgetProvider::class.java),
            ComponentName(appContext, BudgetReviewWidgetProvider::class.java)
        ).forEach { component ->
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                appContext.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        this.component = component
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }
}
