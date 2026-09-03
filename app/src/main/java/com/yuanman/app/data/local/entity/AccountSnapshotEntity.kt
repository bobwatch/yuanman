package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "account_snapshots",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index("periodKey"),
        Index("periodType"),
        Index("reconciledAt")
    ]
)
data class AccountSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    val periodKey: String,
    val periodType: String, // MONTH, QUARTER, HALF_YEAR, YEAR
    val periodStartTimestamp: Long = 0L,
    val periodEndTimestamp: Long = 0L,
    val totalAssetCents: Long = 0L,
    val totalDebtCents: Long = 0L,
    val netWorthCents: Long = 0L,
    val snapshotDataJson: String = "{}",
    val reconciledAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val revision: Long = 0L,
    val deletedAt: Long? = null
)
