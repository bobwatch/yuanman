package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: String, // "EXPENSE" or "INCOME"
    val iconName: String,
    val colorHex: Long,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val tags: String = "", // Comma-separated child tags
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
) {
    /**
     * 获取该分类专属的子标签列表
     */
    fun getTagList(): List<String> {
        val trimmed = tags.trim()
        return if (trimmed.isNotEmpty()) {
            trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            com.yuanman.app.data.model.CategoryIconHelper.getPresetRemarks(name)
        }
    }
}
