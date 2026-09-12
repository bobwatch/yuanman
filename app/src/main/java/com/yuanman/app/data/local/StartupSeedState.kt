package com.yuanman.app.data.local

import android.content.Context

/**
 * 启动期一次性初始化（快捷记账学习词库播种 / 历史样本回填）的持久化门控。
 *
 * 这些初始化在代码上幂等，但每次都冷启动全量重跑的成本很高：
 * 播种会对每个内置分类的每个预置词做存在性查询（数千次），回填会全表读历史流水。
 * 这里用轻量的 SharedPreferences 标记（不走 DataStore，避免启动读盘成本）记录
 * 上次完成状态，把「每次启动全量跑」降为「仅在需要时跑」：
 *  - 播种：应用版本升级（词库随版本扩充）或分类数量变化（新增自定义分类）后重跑；
 *  - 回填：改为增量游标，只处理 updatedAt 晚于游标的新账单。
 * 用户主动清空学习数据（[invalidate]）时全部失效，下次启动按原语义重建。
 */
object StartupSeedState {
    private const val PREFS_NAME = "startup_seed_state"
    private const val KEY_SEED_VERSION = "learning_seed_version"
    private const val KEY_SEED_CATEGORY_COUNT = "learning_seed_category_count"
    private const val KEY_BACKFILL_CURSOR = "learning_backfill_cursor"

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastSeedVersion(context: Context): Int =
        prefs(context).getInt(KEY_SEED_VERSION, -1)

    fun lastSeedCategoryCount(context: Context): Int =
        prefs(context).getInt(KEY_SEED_CATEGORY_COUNT, -1)

    fun markSeeded(context: Context, version: Int, categoryCount: Int) {
        prefs(context).edit()
            .putInt(KEY_SEED_VERSION, version)
            .putInt(KEY_SEED_CATEGORY_COUNT, categoryCount)
            .apply()
    }

    fun backfillCursor(context: Context): Long =
        prefs(context).getLong(KEY_BACKFILL_CURSOR, 0L)

    fun markBackfillCursor(context: Context, cursor: Long) {
        prefs(context).edit().putLong(KEY_BACKFILL_CURSOR, cursor).apply()
    }

    /** 用户清空/重置学习数据后调用：清除门控，下次启动按原语义完整重建。 */
    fun invalidate(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
