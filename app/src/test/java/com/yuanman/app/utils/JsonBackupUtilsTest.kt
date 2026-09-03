package com.yuanman.app.utils

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.AccountSnapshotEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.repository.PreferenceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBackupUtilsTest {
    private val category = CategoryEntity(
        id = 1L,
        name = "餐饮美食",
        type = "EXPENSE",
        iconName = "food",
        colorHex = 0xFFFF5722L,
        syncId = "category:EXPENSE:餐饮美食",
        revision = 3L
    )
    private val record = RecordEntity(
        id = 2L,
        type = "EXPENSE",
        amount = 1_800L,
        categoryId = 1L,
        recordTime = 123L,
        remark = "奶茶",
        paymentMethod = "微信支付",
        syncId = "record-a",
        revision = 4L
    )
    private val account = AccountEntity(
        id = 10L,
        syncId = "account-a",
        name = "微信零钱",
        type = "CHECKING",
        balanceCents = 98_200L,
        initialBalanceCents = 100_000L,
        revision = 5L
    )
    private val snapshot = AccountSnapshotEntity(
        id = 11L,
        syncId = "snapshot-a",
        periodKey = "2026-08",
        periodType = "MONTH",
        totalAssetCents = 98_200L,
        netWorthCents = 98_200L,
        snapshotDataJson = "[{\"accountId\":10,\"actualBalanceCents\":98200}]",
        revision = 2L
    )

    @Test
    fun `version 3 backup round trips all data and verifies checksum`() {
        val preferences = PreferenceSnapshot(
            themeMode = "DARK",
            defaultRecordType = "EXPENSE",
            defaultPaymentMethod = "微信支付",
            monthlyBudgets = mapOf("2026-08" to 300_000L),
            legacyMonthlyBudget = 0L,
            privacyMode = true,
            hapticFeedbackEnabled = true,
            quickEntryEnabled = true,
            customTags = listOf("早餐", "奶茶"),
            pinnedTemplateKeys = setOf("template-a")
        )
        val learning = listOf(QuickEntryLearningEntity("EXPENSE", "奶茶", category.syncId, 2, 100L))

        val json = JsonBackupUtils.exportEntitiesToJsonString(
            categories = listOf(category),
            records = listOf(record.copy(accountId = account.id, targetAccountId = 99L, isAdjustment = true)),
            preferences = preferences,
            quickEntryLearning = learning,
            accounts = listOf(account),
            accountSnapshots = listOf(snapshot)
        )
        val restored = JsonBackupUtils.parseFromJsonString(json)

        assertTrue(restored.checksumVerified)
        assertEquals(3L, restored.categories.single().revision)
        assertEquals(4L, restored.records.single().revision)
        assertEquals(account.id, restored.records.single().accountId)
        assertEquals(99L, restored.records.single().targetAccountId)
        assertTrue(restored.records.single().isAdjustment)
        assertEquals(300_000L, restored.preferences?.monthlyBudgets?.get("2026-08"))
        assertEquals("奶茶", restored.quickEntryLearning.single().phrase)
        assertEquals(account, restored.accounts.single())
        assertEquals(snapshot, restored.accountSnapshots.single())
        assertTrue(restored.includesAccounts)
        assertEquals(1, JsonBackupUtils.preview(json).accountCount)
        assertEquals(1, JsonBackupUtils.preview(json).snapshotCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tampered backup is rejected`() {
        val json = JsonBackupUtils.exportEntitiesToJsonString(listOf(category), listOf(record))
            .replace("1800", "1900")
        JsonBackupUtils.parseFromJsonString(json)
    }

    @Test
    fun `legacy version 2 backup remains readable`() {
        val legacy = """{"version":"2.0","categories":[{"id":1,"name":"餐饮美食","type":"EXPENSE","iconName":"food","colorHex":1}],"records":[{"id":2,"type":"EXPENSE","amount":1800,"categoryId":1}]}"""
        val restored = JsonBackupUtils.parseFromJsonString(legacy)
        assertEquals("2.0", restored.version)
        assertEquals(1, restored.records.size)
        assertTrue(!restored.checksumVerified)
    }
}
