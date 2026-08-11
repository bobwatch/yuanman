package com.moneyhistory.app

import org.json.JSONObject
import java.util.UUID

/**
 * 一条记账流水。
 *
 * @param amountCents 金额，单位：分（Long，避免浮点误差）
 * @param timestamp   记录时间，毫秒时间戳
 */
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: Type,
    val amountCents: Long,
    val category: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** 最后修改时间（毫秒），家庭同步合并时同 id 取大者。 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 墓碑标记：删除不真删，用于家庭同步；UI 层一律不显示。 */
    val deleted: Boolean = false
) {

    enum class Type(val json: String) {
        EXPENSE("expense"),
        INCOME("income");

        companion object {
            fun fromJson(value: String): Type = when (value) {
                EXPENSE.json -> EXPENSE
                INCOME.json -> INCOME
                else -> throw IllegalArgumentException("unknown type: $value")
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.json)
        put("amount", amountCents)
        put("category", category)
        put("note", note)
        put("timestamp", timestamp)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
    }

    companion object {
        fun fromJson(obj: JSONObject): Transaction {
            val timestamp = obj.getLong("timestamp")
            return Transaction(
                id = obj.getString("id"),
                type = Type.fromJson(obj.getString("type")),
                amountCents = obj.getLong("amount"),
                category = obj.getString("category"),
                note = obj.optString("note", ""),
                timestamp = timestamp,
                // v1 数据迁移：缺 updatedAt 用 timestamp，缺 deleted 视为 false
                updatedAt = obj.optLong("updatedAt", timestamp),
                deleted = obj.optBoolean("deleted", false)
            )
        }
    }
}
