package com.yuanman.app.ui.screens.list

/**
 * 明细页 → 底部「记一笔」的跨层日期桥（v0.0.4.7）。
 *
 * 用户通常是在「账单明细」里按天筛选回看某一天，想顺手补记当天的账，
 * 因此底部导航的 + 在明细 Tab 且存在「天筛选」时，把该日期带入新增页（预填记录时间）。
 * 明细页在选中/清除某天时写入本桥；导航层读取后决定是否携带 recordTime。
 * 仅明细 Tab 使用：切到其它 Tab 后导航层不会再读它（旧值不影响首页等入口）。
 */
object RecordListAddBridge {
    /** 当前“天筛选”日期的当日零点毫秒；null = 未按天筛选 */
    @Volatile
    var selectedDayStartMillis: Long? = null
}
