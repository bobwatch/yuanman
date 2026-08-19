package com.moneyhistory.app

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 分类持久化（filesDir/categories.json）。
 *
 * v2 起支出/收入各存一份完整分类列表——默认分类只是首次创建时的种子，
 * 之后增删改都写回文件，不再区分「预设/自定义」。
 * 结构：{"version":2,"expense":["🍜 餐饮美食"],"income":["💰 工资"]}
 * v1 的 custom（对支出/收入同时生效）迁移时统一归入支出组。
 */
class CategoriesStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val expense = mutableListOf<String>()
    private val income = mutableListOf<String>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun expenseCategories(): List<String> = synchronized(lock) { expense.toList() }

    fun incomeCategories(): List<String> = synchronized(lock) { income.toList() }

    /** 添加分类；与本组或另一组已有重复时返回 false。 */
    fun add(category: String, isExpense: Boolean): Boolean = synchronized(lock) {
        if (category in expense || category in income) return false
        (if (isExpense) expense else income).add(category)
        saveLocked()
        true
    }

    fun remove(category: String, isExpense: Boolean): Boolean = synchronized(lock) {
        val removed = (if (isExpense) expense else income).remove(category)
        if (removed) saveLocked()
        removed
    }

    /**
     * 重命名分类（含 emoji 前缀，如「🐱 宠物」→「🐶 汪星人」）。
     * 与另一组已有重复、或原名不在该组时返回 false；原名即新名视为成功（幂等）。
     */
    fun rename(old: String, new: String, isExpense: Boolean): Boolean = synchronized(lock) {
        val list = if (isExpense) expense else income
        val index = list.indexOf(old)
        if (index < 0) return false
        if (old == new) return true
        if (new in expense || new in income) return false
        list[index] = new
        saveLocked()
        true
    }

    private fun loadLocked() {
        expense.clear()
        income.clear()
        if (!file.exists()) {
            // 首次启动：默认分类作种子，与用户改动一起持久化
            expense.addAll(Categories.expense)
            income.addAll(Categories.income)
            saveLocked()
            return
        }
        try {
            val root = org.json.JSONObject(file.readText(Charsets.UTF_8))
            if (root.optInt("version", 1) >= 2) {
                readArrayLocked(root.optJSONArray("expense"), expense, null)
                readArrayLocked(root.optJSONArray("income"), income, expense)
            } else {
                // v1 迁移：默认分类作种子，老自定义分类（对两组同时生效）归入支出组
                expense.addAll(Categories.expense)
                income.addAll(Categories.income)
                readArrayLocked(root.optJSONArray("custom"), expense, null)
            }
            // 兜底：缺数组/迁移后某组为空时回落种子，保证两组始终可用
            if (expense.isEmpty()) expense.addAll(Categories.expense)
            if (income.isEmpty()) income.addAll(Categories.income)
            saveLocked()
        } catch (e: Exception) {
            // 文件损坏时从默认分类开始
            expense.addAll(Categories.expense)
            income.addAll(Categories.income)
        }
    }

    /** 从 JSON 数组读入一组分类，跨组去重（分类字符串全局唯一）。 */
    private fun readArrayLocked(arr: JSONArray?, into: MutableList<String>, other: List<String>?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val c = arr.optString(i, "")
            if (c.isEmpty() || c in into) continue
            if (other != null && c in other) continue
            into.add(c)
        }
    }

    private fun saveLocked() {
        val root = org.json.JSONObject()
        root.put("version", VERSION)
        root.put("expense", JSONArray().apply { expense.forEach { put(it) } })
        root.put("income", JSONArray().apply { income.forEach { put(it) } })
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "categories.json"

        @Volatile
        private var instance: CategoriesStore? = null

        fun getInstance(context: Context): CategoriesStore =
            instance ?: synchronized(this) {
                instance ?: CategoriesStore(context.applicationContext).also { instance = it }
            }
    }
}
