package com.yuanman.app.utils

import com.yuanman.app.data.local.entity.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CSV 导入去重逻辑的单元测试：
 * 指纹 = (类型, 金额, 分类名, 备注, 支付方式) + 分钟时间桶（桶号差 ≤ 1 视为同一分钟窗口，容忍约 ±90 秒）。
 */
class CsvImportUtilsTest {

    private val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /** 构造 2026-01-01 当天 HH:mm:ss 的时间戳（毫秒）。 */
    private fun timeMillis(hour: Int, minute: Int, second: Int): Long {
        val text = String.format(Locale.US, "2026-01-01 %02d:%02d:%02d", hour, minute, second)
        return parser.parse(text)!!.time
    }

    /** 构造一个常规的去重指纹。 */
    private fun key(
        type: String = "EXPENSE",
        amountCents: Long = 1_800L,
        categoryName: String = "餐饮美食",
        remark: String = "午饭",
        paymentMethod: String = "微信支付"
    ): CsvImportUtils.DedupKey = CsvImportUtils.makeDedupKey(type, amountCents, categoryName, remark, paymentMethod)

    // ---------------- 分钟桶 ----------------

    @Test
    fun `minute bucket groups timestamps by minute and ignores seconds`() {
        assertEquals(
            CsvImportUtils.minuteBucketOf(timeMillis(8, 30, 0)),
            CsvImportUtils.minuteBucketOf(timeMillis(8, 30, 59))
        )
        assertEquals(
            CsvImportUtils.minuteBucketOf(timeMillis(8, 30, 0)) + 1L,
            CsvImportUtils.minuteBucketOf(timeMillis(8, 31, 0))
        )
    }

    @Test
    fun `bucket difference within one is treated as same minute window`() {
        assertTrue(CsvImportUtils.isSameMinuteWindow(100L, 100L))
        assertTrue(CsvImportUtils.isSameMinuteWindow(100L, 101L))
        assertTrue(CsvImportUtils.isSameMinuteWindow(101L, 100L))
        assertFalse(CsvImportUtils.isSameMinuteWindow(100L, 102L))
    }

    @Test
    fun `records about ninety seconds apart share the window while farther ones do not`() {
        val t1 = timeMillis(8, 30, 0)
        val t2 = timeMillis(8, 31, 29) // 相差 89 秒
        val t3 = timeMillis(8, 32, 30) // 与 t1 相差 150 秒

        assertTrue(
            CsvImportUtils.isSameMinuteWindow(
                CsvImportUtils.minuteBucketOf(t1),
                CsvImportUtils.minuteBucketOf(t2)
            )
        )
        assertFalse(
            CsvImportUtils.isSameMinuteWindow(
                CsvImportUtils.minuteBucketOf(t1),
                CsvImportUtils.minuteBucketOf(t3)
            )
        )
    }

    // ---------------- 库内记录去重 ----------------

    @Test
    fun `csv row matching existing record with same fingerprint and bucket is duplicate`() {
        val existing = mapOf(key() to setOf(100L))
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 100L, existing, mutableMapOf()))
    }

    @Test
    fun `existing record in adjacent minute bucket is duplicate within tolerance`() {
        val existing = mapOf(key() to setOf(100L))
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 99L, existing, mutableMapOf()))
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 101L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(), 98L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(), 102L, existing, mutableMapOf()))
    }

    @Test
    fun `any fingerprint field difference means not duplicate`() {
        val existing = mapOf(key() to setOf(100L))
        assertFalse(CsvImportUtils.isDuplicateRow(key(type = "INCOME"), 100L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(amountCents = 1_900L), 100L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(categoryName = "交通出行"), 100L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(remark = "晚饭"), 100L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(paymentMethod = "支付宝"), 100L, existing, mutableMapOf()))
    }

    @Test
    fun `existing index may hold multiple buckets for same fingerprint`() {
        val existing = mapOf(key() to setOf(100L, 200L))
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 199L, existing, mutableMapOf()))
        assertFalse(CsvImportUtils.isDuplicateRow(key(), 150L, existing, mutableMapOf()))
    }

    // ---------------- 指纹归一化 ----------------

    @Test
    fun `record fingerprint normalizes empty payment method and remark whitespace`() {
        val record = RecordEntity(
            type = "EXPENSE",
            amount = 2_500L,
            categoryId = 1L,
            recordTime = 0L,
            remark = "  咖啡  ",
            paymentMethod = "",
            syncId = "record-1"
        )
        val fromRecord = CsvImportUtils.dedupKeyOf(record, categoryName = "餐饮美食")
        val fromCsv = CsvImportUtils.makeDedupKey("EXPENSE", 2_500L, "餐饮美食", "咖啡", "")
        assertEquals(fromCsv, fromRecord)
    }

    @Test
    fun `record without category falls back to empty category name`() {
        val record = RecordEntity(
            type = "EXPENSE",
            amount = 500L,
            categoryId = 1L,
            recordTime = 0L,
            remark = "",
            paymentMethod = "现金",
            syncId = "record-2"
        )
        assertEquals("", CsvImportUtils.dedupKeyOf(record, null).categoryName)
        assertEquals("现金", CsvImportUtils.dedupKeyOf(record, null).paymentMethod)
    }

    // ---------------- 文件内部重复 ----------------

    @Test
    fun `previous row matched in same file suppresses following adjacent duplicate rows`() {
        val existing = mapOf(key() to setOf(100L))
        val fileMatched = mutableMapOf<CsvImportUtils.DedupKey, MutableSet<Long>>()

        // 前两行与同一库内记录重复：一行落在窗口边缘桶 99，一行落在桶 101，均被跳过并记录
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 99L, existing, fileMatched))
        fileMatched.getOrPut(key()) { mutableSetOf() }.add(99L)
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 101L, existing, fileMatched))
        fileMatched.getOrPut(key()) { mutableSetOf() }.add(101L)

        // 第三行桶 102：库内窗口(100±1)不直接命中，但前序重复行已覆盖桶 101 → 仍视为文件内重复
        assertTrue(CsvImportUtils.isDuplicateRow(key(), 102L, existing, fileMatched))

        // 与已匹配行相差超过一分钟窗口的行不应被误伤
        assertFalse(CsvImportUtils.isDuplicateRow(key(), 104L, existing, fileMatched))
    }

    // ---------------- 结果文案 ----------------

    @Test
    fun `import message keeps original phrasing when no duplicates`() {
        assertEquals("成功导入 3 笔账单", CsvImportUtils.buildImportMessage(3, 0, 0))
        assertEquals("成功导入 3 笔账单，跳过 2 笔无效数据", CsvImportUtils.buildImportMessage(3, 2, 0))
        assertEquals("成功导入 0 笔账单", CsvImportUtils.buildImportMessage(0, 0, 0))
    }

    @Test
    fun `import message reports duplicate count when present`() {
        assertEquals(
            "成功导入 3 笔账单，跳过 4 笔重复账单",
            CsvImportUtils.buildImportMessage(3, 0, 4)
        )
        assertEquals(
            "成功导入 3 笔账单，跳过 2 笔无效数据，跳过 4 笔重复账单",
            CsvImportUtils.buildImportMessage(3, 2, 4)
        )
    }
}
