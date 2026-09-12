package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    /** 用户自定义类型标签（如"储蓄""信用"）。纯展示/分组用途，代码永不读取其值做分支或计算。 */
    val label: String = "",
    val iconName: String = "wallet",
    val colorHex: Long = 0xFF607D8BL,
    /** 期初余额（分，可正可负）。余额 = 期初 + 流水，任何对账校正都只并入此值。 */
    val openingBalanceCents: Long = 0L,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)
