package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuickEntryParserTest {
    @Test
    fun parsesDescriptionAmountAndCategory() {
        val categories = listOf(
            CategoryEntity(
                id = 1L,
                name = "餐饮美食",
                type = RecordType.EXPENSE.name,
                iconName = "food",
                colorHex = 0xFFFF5722L,
                tags = "早餐,午餐,奶茶咖啡"
            ),
            CategoryEntity(
                id = 2L,
                name = "交通出行",
                type = RecordType.EXPENSE.name,
                iconName = "traffic",
                colorHex = 0xFF2196F3L,
                tags = "地铁,公交"
            )
        )

        val result = QuickEntryParser.parse("奶茶 18", categories)

        assertNotNull(result)
        assertEquals("奶茶", result?.remark)
        assertEquals("18", result?.amountYuan?.toPlainString())
        assertEquals("餐饮美食", result?.category?.name)
    }
}
