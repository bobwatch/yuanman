package com.yuanman.app.data.local.dao

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncDaoTest {
    @Test
    fun `repeated sync is idempotent and remaps category ids`() = runBlocking {
        val dao = FakeSyncDao(categories = mutableListOf(category(1L, "餐饮美食", "local-cat")))
        val remoteCategories = listOf(category(99L, "餐饮美食", "remote-cat"))
        val remoteRecords = listOf(record(42L, 99L, "record-a"))

        val first = dao.merge(remoteCategories, remoteRecords)
        val second = dao.merge(remoteCategories, remoteRecords)

        assertEquals(1, dao.categories.count { it.deletedAt == null })
        assertEquals(1, dao.records.size)
        assertEquals(1L, dao.records.single().categoryId)
        assertEquals(1, first.changedRecordCount)
        assertEquals(0, second.changedRecordCount)
    }

    @Test
    fun `newer edit updates same logical record instead of duplicating`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(category(1L, "餐饮美食", "cat-a")),
            records = mutableListOf(record(1L, 1L, "record-a", amount = 100L, updatedAt = 100L))
        )

        dao.merge(
            listOf(category(9L, "餐饮美食", "cat-a")),
            listOf(record(9L, 9L, "record-a", amount = 250L, updatedAt = 200L))
        )

        assertEquals(1, dao.records.size)
        assertEquals(250L, dao.records.single().amount)
        assertEquals(1L, dao.records.single().id)
    }

    @Test
    fun `newer tombstone propagates deletion without removing the row`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(category(1L, "餐饮美食", "cat-a")),
            records = mutableListOf(record(1L, 1L, "record-a", updatedAt = 100L))
        )

        dao.merge(
            listOf(category(9L, "餐饮美食", "cat-a")),
            listOf(record(9L, 9L, "record-a", updatedAt = 200L, deletedAt = 200L))
        )

        assertEquals(1, dao.records.size)
        assertEquals(200L, dao.records.single().deletedAt)
    }

    @Test
    fun `tombstone timestamp wins even when legacy payload omitted updatedAt`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(category(1L, "餐饮美食", "cat-a")),
            records = mutableListOf(record(1L, 1L, "record-a", updatedAt = 100L))
        )

        dao.merge(
            listOf(category(9L, "餐饮美食", "cat-a")),
            listOf(record(9L, 9L, "record-a", updatedAt = 100L, deletedAt = 300L))
        )

        assertEquals(300L, dao.records.single().deletedAt)
    }

    @Test
    fun `category rename follows stable identity`() = runBlocking {
        val dao = FakeSyncDao(categories = mutableListOf(category(1L, "旧名称", "cat-a", updatedAt = 100L)))

        dao.merge(
            listOf(category(7L, "新名称", "cat-a", updatedAt = 200L)),
            emptyList()
        )

        assertEquals(1, dao.categories.size)
        assertEquals("新名称", dao.categories.single().name)
    }

    @Test
    fun `legacy duplicate categories are tombstoned after links move`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(
                category(1L, "工资", "cat-a", type = "INCOME", updatedAt = 100L),
                category(2L, " 工资 ", "cat-b", type = "income", updatedAt = 200L)
            ),
            records = mutableListOf(record(3L, 2L, "record-a", type = "INCOME"))
        )

        dao.merge(emptyList(), emptyList())

        assertEquals(1, dao.categories.count { it.deletedAt == null })
        assertNotNull(dao.categories.single { it.id == 1L }.deletedAt)
        assertEquals(2L, dao.records.single().categoryId)
    }

    @Test
    fun `newer independent category revives tombstone instead of creating duplicate`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(
                category(1L, "餐饮美食", "local-cat", updatedAt = 100L)
                    .copy(deletedAt = 100L)
            )
        )

        dao.merge(
            listOf(category(99L, " 餐饮美食 ", "remote-cat", updatedAt = 200L)),
            emptyList()
        )

        assertEquals(1, dao.categories.size)
        assertEquals(1, dao.categories.count { it.deletedAt == null })
        assertEquals(1L, dao.categories.single().id)
    }

    @Test
    fun `remote data for a retired duplicate stays linked to active category`() = runBlocking {
        val dao = FakeSyncDao(
            categories = mutableListOf(
                category(1L, "工资", "cat-z", type = "INCOME", updatedAt = 200L),
                category(2L, "工资", "cat-a", type = "INCOME", updatedAt = 100L)
            )
        )

        dao.merge(
            listOf(category(9L, "工资", "cat-a", type = "INCOME", updatedAt = 300L)),
            listOf(record(10L, 9L, "record-a", type = "INCOME", updatedAt = 300L))
        )

        assertEquals(1, dao.categories.count { it.deletedAt == null })
        assertEquals(1L, dao.records.single().categoryId)
    }

    private fun category(
        id: Long,
        name: String,
        syncId: String,
        type: String = "EXPENSE",
        updatedAt: Long = 100L
    ) = CategoryEntity(
        id = id,
        name = name,
        type = type,
        iconName = "other",
        colorHex = 0L,
        createdAt = 100L,
        syncId = syncId,
        updatedAt = updatedAt
    )

    private fun record(
        id: Long,
        categoryId: Long,
        syncId: String,
        type: String = "EXPENSE",
        amount: Long = 1_000L,
        updatedAt: Long = 100L,
        deletedAt: Long? = null
    ) = RecordEntity(
        id = id,
        type = type,
        amount = amount,
        categoryId = categoryId,
        recordTime = 123L,
        remark = "测试",
        paymentMethod = "现金",
        createdAt = 100L,
        updatedAt = updatedAt,
        syncId = syncId,
        deletedAt = deletedAt
    )

    private class FakeSyncDao(
        val categories: MutableList<CategoryEntity> = mutableListOf(),
        val records: MutableList<RecordEntity> = mutableListOf()
    ) : SyncDao() {
        private var nextCategoryId = (categories.maxOfOrNull { it.id } ?: 0L) + 1L
        private var nextRecordId = (records.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun getAllCategories(): List<CategoryEntity> = categories.toList()
        override suspend fun getAllRecords(): List<RecordEntity> = records.toList()

        override suspend fun insertCategory(category: CategoryEntity): Long {
            val id = nextCategoryId++
            categories += category.copy(id = id)
            return id
        }

        override suspend fun insertRecord(record: RecordEntity): Long {
            val id = nextRecordId++
            records += record.copy(id = id)
            return id
        }

        override suspend fun updateCategory(category: CategoryEntity) {
            categories.replaceAll { if (it.id == category.id) category else it }
        }

        override suspend fun updateRecord(record: RecordEntity) {
            records.replaceAll { if (it.id == record.id) record else it }
        }

        override suspend fun updateRecordCategory(oldCategoryId: Long, newCategoryId: Long) {
            records.replaceAll { if (it.categoryId == oldCategoryId) it.copy(categoryId = newCategoryId) else it }
        }

        override suspend fun countActiveRecordsForCategory(categoryId: Long): Int =
            records.count { it.categoryId == categoryId && it.deletedAt == null }

        override suspend fun softDeleteCategory(categoryId: Long, deletedAt: Long) {
            categories.replaceAll {
                if (it.id == categoryId) it.copy(updatedAt = deletedAt, deletedAt = deletedAt) else it
            }
        }
    }
}
