package com.yuanman.app.data.model

enum class RecordType(val title: String) {
    EXPENSE("支出"),
    INCOME("收入");

    companion object {
        fun fromString(typeStr: String): RecordType {
            return try {
                valueOf(typeStr)
            } catch (e: Exception) {
                if (typeStr == "收入") INCOME else EXPENSE
            }
        }
    }
}
