package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        assertTrue((result?.confidence ?: 0f) > 0.7f)

        val resultKuai = QuickEntryParser.parse("咖啡15块", categories)
        assertNotNull(resultKuai)
        assertEquals("咖啡", resultKuai?.remark)
        assertEquals("15", resultKuai?.amountYuan?.toPlainString())
        assertEquals("餐饮美食", resultKuai?.category?.name)

        val resultKuaiQian = QuickEntryParser.parse("咖啡 15块钱", categories)
        assertNotNull(resultKuaiQian)
        assertEquals("咖啡", resultKuaiQian?.remark)
        assertEquals("15", resultKuaiQian?.amountYuan?.toPlainString())

        val resultSymbol = QuickEntryParser.parse("¥25.5 地铁", categories)
        assertNotNull(resultSymbol)
        assertEquals("地铁", resultSymbol?.remark)
        assertEquals("25.5", resultSymbol?.amountYuan?.toPlainString())
        assertEquals("交通出行", resultSymbol?.category?.name)
    }

    @Test
    fun learnedPhraseWinsAndRaisesConfidence() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L, syncId = "food"),
            CategoryEntity(id = 2L, name = "交通出行", type = RecordType.EXPENSE.name, iconName = "traffic", colorHex = 2L, syncId = "traffic")
        )

        val applied = QuickEntryParser.parse(
            "公司班车 20",
            categories,
            listOf(
                QuickEntryLearningEntity(
                    type = RecordType.EXPENSE.name,
                    phrase = "公司班车",
                    categorySyncId = "traffic",
                    sampleCount = 4
                )
            )
        )
        assertEquals("交通出行", applied?.category?.name)
        assertTrue((applied?.confidence ?: 0f) > 0.7f)
    }

    @Test
    fun userRecordRuleOverridesPresetRuleOfSamePhrase() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L, syncId = "food"),
            CategoryEntity(id = 2L, name = "人情往来", type = RecordType.EXPENSE.name, iconName = "gift", colorHex = 2L, syncId = "gift")
        )
        val presetRule = QuickEntryLearningEntity(
            type = RecordType.EXPENSE.name,
            phrase = "奶茶",
            categorySyncId = "food",
            sampleCount = 0,
            lastUsedAt = 0L
        )
        val userRule = QuickEntryLearningEntity(
            type = RecordType.EXPENSE.name,
            phrase = "奶茶",
            categorySyncId = "gift",
            sampleCount = 3,
            lastUsedAt = 1000L
        )

        // 无论预置规则排在用户规则之前还是之后，用户记账内容都应覆盖同名预置规则。
        val presetFirst = QuickEntryParser.parse("奶茶 20", categories, listOf(presetRule, userRule))
        assertEquals("人情往来", presetFirst?.category?.name)

        val userFirst = QuickEntryParser.parse("奶茶 20", categories, listOf(userRule, presetRule))
        assertEquals("人情往来", userFirst?.category?.name)
        assertTrue((userFirst?.confidence ?: 0f) > 0.7f)
    }

    @Test
    fun newestUserRuleWinsWhenSampleCountsTie() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L, syncId = "food"),
            CategoryEntity(id = 2L, name = "超市买菜", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 2L, syncId = "market")
        )
        val older = QuickEntryLearningEntity(
            type = RecordType.EXPENSE.name,
            phrase = "水果",
            categorySyncId = "food",
            sampleCount = 1,
            lastUsedAt = 100L
        )
        val newer = QuickEntryLearningEntity(
            type = RecordType.EXPENSE.name,
            phrase = "水果",
            categorySyncId = "market",
            sampleCount = 1,
            lastUsedAt = 500L
        )

        val applied = QuickEntryParser.parse("水果 15", categories, listOf(older, newer))
        assertEquals("超市买菜", applied?.category?.name)
    }

    @Test
    fun presetRuleStillUsedWhenUserHasNoRuleForPhrase() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L, syncId = "food"),
            CategoryEntity(id = 2L, name = "人情往来", type = RecordType.EXPENSE.name, iconName = "gift", colorHex = 2L, syncId = "gift")
        )
        val presetRule = QuickEntryLearningEntity(
            type = RecordType.EXPENSE.name,
            phrase = "奶茶",
            categorySyncId = "food",
            sampleCount = 0,
            lastUsedAt = 0L
        )

        val applied = QuickEntryParser.parse("奶茶 20", categories, listOf(presetRule))
        assertEquals("餐饮美食", applied?.category?.name)
    }

    @Test
    fun fuzzyAliasMatchesCommonTypo() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L),
            CategoryEntity(id = 2L, name = "交通出行", type = RecordType.EXPENSE.name, iconName = "traffic", colorHex = 2L)
        )
        val result = QuickEntryParser.parse("咖非 22", categories)
        assertEquals("餐饮美食", result?.category?.name)
    }

    @Test
    fun parsesPaymentMethodAndRemovesItFromRemark() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "餐饮美食", type = RecordType.EXPENSE.name, iconName = "food", colorHex = 1L)
        )

        val wechat = QuickEntryParser.parse("午餐 18 微信支付", categories)
        assertEquals("午餐", wechat?.remark)
        assertEquals("微信支付", wechat?.paymentMethod)

        val alipay = QuickEntryParser.parse("用支付宝买咖啡 25", categories)
        assertEquals("买咖啡", alipay?.remark)
        assertEquals("支付宝", alipay?.paymentMethod)
    }

    @Test
    fun exposesSystemLearningPhrasesForSettings() {
        val category = CategoryEntity(
            id = 1L,
            name = "餐饮美食",
            type = RecordType.EXPENSE.name,
            iconName = "food",
            colorHex = 1L
        )

        val phrases = QuickEntryParser.defaultLearningPhrases(category)

        assertTrue(phrases.contains("咖啡店"))
        assertTrue(phrases.contains("下午茶"))
        assertTrue(phrases.all { it.length >= 2 })
    }
}
