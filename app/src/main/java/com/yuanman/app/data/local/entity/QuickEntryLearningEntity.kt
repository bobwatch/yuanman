package com.yuanman.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** 闪电记账分类词条；sampleCount=0 表示系统预置词，>0 表示用户已使用/修正过。 */
@Entity(
    tableName = "quick_entry_learning",
    primaryKeys = ["type", "phrase", "categorySyncId"],
    indices = [
        Index(value = ["type", "phrase"]),
        Index(value = ["categorySyncId"])
    ]
)
data class QuickEntryLearningEntity(
    val type: String,
    val phrase: String,
    val categorySyncId: String,
    val sampleCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)
