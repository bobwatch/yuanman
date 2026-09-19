package com.yuanman.app.ui.navigation

import com.yuanman.app.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 桌面微件等外部入口传入的路由必须能被解析成二级页目标：
 * 解析失败会让「记一笔 / 看统计」入口失效甚至崩溃，所以逐一锁定支持的路由形态。
 */
class ExternalRouteParserTest {

    private fun assertRecordTarget(
        expectedRecordId: Long,
        expectedType: RecordType?,
        expectedCategoryId: Long,
        route: String
    ) {
        val target = parseExternalRoute(route)
        val record = target as? SecondaryScreen.AddEditRecord
        requireNotNull(record) { "路由未解析成记账页：$route" }
        assertEquals(expectedRecordId, record.recordId)
        assertEquals(expectedType, record.type)
        assertEquals(expectedCategoryId, record.categoryId)
    }

    @Test
    fun `支出快捷记账解析出支出类型`() {
        assertRecordTarget(0L, RecordType.EXPENSE, 0L, Screen.AddEditRecord.createRoute(type = RecordType.EXPENSE))
    }

    @Test
    fun `收入快捷记账解析出收入类型`() {
        assertRecordTarget(0L, RecordType.INCOME, 0L, Screen.AddEditRecord.createRoute(type = RecordType.INCOME))
    }

    @Test
    fun `带记录与分类参数的记账路由按数值解析`() {
        assertRecordTarget(
            42L,
            RecordType.EXPENSE,
            7L,
            Screen.AddEditRecord.createRoute(recordId = 42L, type = RecordType.EXPENSE, categoryId = 7L)
        )
    }

    @Test
    fun `统计与资产全景路由解析为对应二级页`() {
        assertEquals(SecondaryScreen.Statistics, parseExternalRoute(Screen.Statistics.route))
        assertEquals(SecondaryScreen.AssetPanorama, parseExternalRoute(Screen.AssetPanorama.route))
        assertEquals(SecondaryScreen.CategoryManage, parseExternalRoute(Screen.CategoryManage.route))
    }

    @Test
    fun `tab 路由不产生二级页目标`() {
        assertNull(parseExternalRoute(Screen.Home.route))
    }

    @Test
    fun `未知或畸形路由安全返回空`() {
        assertNull(parseExternalRoute("unknown_route"))
        assertNull(parseExternalRoute(""))
        assertNull(parseExternalRoute("statistics_extra"))
        // 参数缺失或非法时退化为默认值，而不是抛异常
        assertRecordTarget(0L, null, 0L, "add_edit_record")
        assertRecordTarget(0L, null, 0L, "add_edit_record?recordId=abc&type=&categoryId=")
    }
}
