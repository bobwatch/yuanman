package com.moneyhistory.app.widget

import android.app.AlarmManager
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
 * 跨天自动刷新：每天零点触发 [ACTION_MIDNIGHT] 重算「今日支出」，
 * 开机后由 BOOT_COMPLETED 重排下一次零点闹钟（不依赖 30 分钟周期兜底）。
 */
class SpendingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
        scheduleMidnight(context)
    }

    override fun onEnabled(context: Context) {
        // 首个 Widget 添加到桌面：排好零点刷新
        scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        cancelMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 零点闹钟：重算「今日支出」，并排好下一个零点
            ACTION_MIDNIGHT -> {
                updateAll(
                    context,
                    AppWidgetManager.getInstance(context),
                    AppWidgetManager.getInstance(context)
                        .getAppWidgetIds(ComponentName(context, SpendingWidgetProvider::class.java))
                )
                scheduleMidnight(context)
            }
            // 开机重排（Widget 仍在桌面上，但闹钟随重启丢失）
            Intent.ACTION_BOOT_COMPLETED -> scheduleMidnight(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {

        private const val ACTION_MIDNIGHT = "com.moneyhistory.app.action.MIDNIGHT"
        private const val REQUEST_MIDNIGHT = 2

        /** 流水变更后调用：刷新全部 Widget 实例。 */
        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SpendingWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) updateAll(context, manager, ids)
        }

        /** 排下一个零点（本地时间）的闹钟；setWindow 不需要精确闹钟权限。 */
        private fun scheduleMidnight(context: Context) {
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_MIDNIGHT,
                Intent(context, SpendingWidgetProvider::class.java)
                    .setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // 窗口 ±10 分钟：零点前后稍晚刷新无碍，免去 SCHEDULE_EXACT_ALARM
            alarm.setWindow(AlarmManager.RTC, nextMidnight.timeInMillis, 10 * 60 * 1000L, pi)
        }

        private fun cancelMidnight(context: Context) {
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_MIDNIGHT,
                Intent(context, SpendingWidgetProvider::class.java)
                    .setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pi)
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
