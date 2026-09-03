package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("categoryId"),
        Index("recordTime"),
        Index("type"),
        Index("splitGroupId"),
        Index("accountId"),
        Index("targetAccountId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String, // "EXPENSE", "INCOME", or "TRANSFER"
    val amount: Long, // 金额，以“分”存储，避免浮点数精度问题
    val categoryId: Long,
    val recordTime: Long, // 记录发生的时间戳（毫秒）
    val remark: String = "",
    val paymentMethod: String = "", // "微信支付"、"支付宝"、"银行卡"等
    val accountId: Long? = null, // 关联的账户 ID
    val targetAccountId: Long? = null, // 若为转账(TRANSFER)，则为转入账户 ID
    @ColumnInfo(defaultValue = "0")
    val isAdjustment: Boolean = false, // 是否为对账平账记录
    val splitGroupId: String? = null,
    val splitIndex: Int? = null,
    val splitTotal: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val revision: Long = 0L,
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    val deletedAt: Long? = null
)
