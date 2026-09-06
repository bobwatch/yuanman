package com.yuanman.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 对账记录：用户在任意时点把账户账面余额与真实余额核对后留下的审计痕迹。
 *
 * 纯提示/审计数据：绝不参与任何余额与统计计算（余额永远由 期初 + 流水 实时得出）。
 */
@Entity(
    tableName = "account_reconciliations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("accountId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class AccountReconciliationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: Long,
    /** 对账日（当日结束毫秒）；账面余额为截至该时点的流水合计。 */
    val asOfDate: Long,
    /** 用户输入的真实余额（分）。 */
    val actualBalanceCents: Long,
    /** 核对时点账面余额（期初 + 截至 asOfDate 的流水，分）。 */
    val bookBalanceCents: Long,
    /** 差额 = 实际 − 账面（可负）。 */
    val diffCents: Long,
    /** 差额是否已并入期初校正（对账日之后"按真实余额校正"的操作痕迹）。 */
    val corrected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)
