package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index("type"),
        Index("isArchived"),
        Index("deletedAt")
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // CHECKING, INVESTMENT, CREDIT, ASSET
    val balanceCents: Long = 0L,
    val initialBalanceCents: Long = 0L,
    val currency: String = "CNY",
    val includeInNetWorth: Boolean = true,
    val icon: String = "",
    val colorHex: String = "",
    val remark: String = "",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val revision: Long = 0L,
    val deletedAt: Long? = null
)
