package com.yuanman.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
        Index("type")
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String, // "EXPENSE" or "INCOME"
    val amount: Long, // 金额，以“分”存储，避免浮点数精度问题
    val categoryId: Long,
    val recordTime: Long, // 记录发生的时间戳（毫秒）
    val remark: String = "",
    val paymentMethod: String, // "微信支付"、"支付宝"、"银行卡"等
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
