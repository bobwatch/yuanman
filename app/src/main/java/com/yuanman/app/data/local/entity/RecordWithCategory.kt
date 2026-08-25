package com.yuanman.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class RecordWithCategory(
    @Embedded
    val record: RecordEntity,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity?
)
