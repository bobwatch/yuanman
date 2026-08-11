package com.moneyhistory.app

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 用户自定义分类的持久化（filesDir/categories.json）。
 *
 * 预设分类见 [Categories]，不可删；自定义分类（「emoji 名称」格式）
 * 对支出/收入同时生效，可增可删。结构：{"version":1,"custom":["🐱 宠物"]}
 */
class CategoriesStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val custom = mutableListOf<String>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun customCategories(): List<String> = synchronized(lock) { custom.toList() }

    fun expenseCategories(): List<String> = Categories.expense + customCategories()

    fun incomeCategories(): List<String> = Categories.income + customCategories()

    /** 添加自定义分类；与预设/已有重复时返回 false。 */
    fun add(category: String): Boolean = synchronized(lock) {
        if (category in custom || category in Categories.expense ||
            category in Categories.income
        ) {
            return false
        }
        custom.add(category)
        saveLocked()
        true
    }

    fun remove(category: String): Boolean = synchronized(lock) {
        val removed = custom.remove(category)
        if (removed) saveLocked()
        removed
    }

    private fun loadLocked() {
        custom.clear()
        if (!file.exists()) return
        try {
            val root = org.json.JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("custom") ?: return
            for (i in 0 until arr.length()) {
                val c = arr.optString(i, "")
                if (c.isNotEmpty() && c !in custom) custom.add(c)
            }
        } catch (e: Exception) {
            // 文件损坏时从空列表开始
        }
    }

    private fun saveLocked() {
        val arr = JSONArray()
        custom.forEach { arr.put(it) }
        val root = org.json.JSONObject()
        root.put("version", VERSION)
        root.put("custom", arr)
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "categories.json"

        @Volatile
        private var instance: CategoriesStore? = null

        fun getInstance(context: Context): CategoriesStore =
            instance ?: synchronized(this) {
                instance ?: CategoriesStore(context.applicationContext).also { instance = it }
            }
    }
}
