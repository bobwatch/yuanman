package com.yuanman

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 流水数据的存储层。
 *
 * 不使用任何数据库：所有数据保存在 App 私有目录的 JSON 文件中
 * （filesDir/transactions.json），结构：
 * {"version":2,"transactions":[{...}]}
 *
 * v2 相比 v1 新增 updatedAt / deleted（墓碑）字段，用于家庭同步合并；
 * 加载 v1 文件时自动补默认值，并立即以 v2 落盘完成迁移。
 *
 * 写入策略：内存列表 + 每次变更整体落盘（数据量小，简单可靠）。
 * 落盘为原子写入：先写 .tmp，旧文件复制为 .bak，再 rename 覆盖正式文件；
 * 主文件损坏时加载自动尝试从 .bak 恢复。
 */
class TransactionStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val bakFile = File(context.filesDir, "$FILE_NAME.bak")
    private val importBackupFile = File(context.filesDir, "backup_before_import.json")
    private val lock = Any()
    private val items = mutableListOf<Transaction>()

    /** 数据变更回调（桌面 Widget 刷新等），在调用线程同步触发。 */
    @Volatile
    var onChanged: (() -> Unit)? = null

    init {
        synchronized(lock) { loadLocked() }
    }

    /** 全部流水（不含墓碑），按时间倒序。 */
    fun all(): List<Transaction> = synchronized(lock) {
        items.filter { !it.deleted }.sortedByDescending { it.timestamp }
    }

    /** 含墓碑的完整数据（家庭同步交换用）。 */
    fun allIncludingTombstones(): List<Transaction> = synchronized(lock) {
        items.sortedByDescending { it.timestamp }
    }

    fun add(t: Transaction) = synchronized(lock) {
        val index = items.indexOfFirst { it.id == t.id }
        if (index >= 0) items[index] = t else items.add(t)
        saveLocked()
    }

    /** 更新一条记录（按 id 匹配），自动刷新 updatedAt 以便同步传播。 */
    fun update(t: Transaction): Boolean = synchronized(lock) {
        val index = items.indexOfFirst { it.id == t.id }
        if (index < 0) return false
        items[index] = t.copy(updatedAt = System.currentTimeMillis())
        saveLocked()
        true
    }

    /** 删除 = 打墓碑（记录保留用于同步合并，UI 不显示）。 */
    fun remove(id: String): Boolean = synchronized(lock) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return false
        items[index] = items[index].copy(
            deleted = true,
            updatedAt = System.currentTimeMillis()
        )
        saveLocked()
        true
    }

    /**
     * 合并远端账本（家庭同步）。同 id 取 updatedAt 大者（含墓碑）。
     *
     * @return 实际写入本地的条数（新增 + 覆盖更新）
     */
    fun merge(remote: List<Transaction>): Int = synchronized(lock) {
        var changed = 0
        remote.forEach { r ->
            val index = items.indexOfFirst { it.id == r.id }
            when {
                index < 0 -> {
                    items.add(r)
                    changed++
                }
                r.updatedAt > items[index].updatedAt -> {
                    items[index] = r
                    changed++
                }
            }
        }
        if (changed > 0) saveLocked()
        changed
    }

    /** 把当前 JSON 文件原样复制到 [dest]（用于导出备份）。 */
    fun exportTo(dest: File) = synchronized(lock) {
        if (!file.exists()) saveLocked()
        file.copyTo(dest, overwrite = true)
    }

    /**
     * 从 JSON 文本恢复全部数据（导入备份），兼容 v1 / v2。
     * 格式非法时返回 false 且不改动现有数据；
     * 覆盖前会把当前数据自动备份到 filesDir/backup_before_import.json。
     */
    fun importJson(content: String): Boolean {
        val parsed = parseTransactions(content) ?: return false
        synchronized(lock) {
            if (file.exists()) file.copyTo(importBackupFile, overwrite = true)
            items.clear()
            items.addAll(parsed)
            saveLocked()
        }
        return true
    }

    /**
     * 合并导入：不覆盖现有数据，按 id + updatedAt 规则合并（同 [merge]）。
     *
     * @return 合并进来的条数；格式非法返回 null
     */
    fun mergeJson(content: String): Int? {
        val parsed = parseTransactions(content) ?: return null
        return merge(parsed)
    }

    /** 解析备份 JSON（兼容 v1 / v2），格式非法返回 null。 */
    private fun parseTransactions(content: String): List<Transaction>? {
        val root = try {
            JSONObject(content)
        } catch (e: Exception) {
            return null
        }
        val version = root.optInt("version", -1)
        if (version != 1 && version != VERSION) return null
        val arr = root.optJSONArray("transactions") ?: return null

        val parsed = mutableListOf<Transaction>()
        try {
            for (i in 0 until arr.length()) {
                parsed.add(Transaction.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            return null
        }
        return parsed
    }

    private fun loadLocked() {
        items.clear()
        // 主文件损坏或缺失时尝试从 .bak 恢复
        val loaded = loadFrom(file) || run {
            items.clear()
            loadFrom(bakFile)
        }
        // 统一以当前版本落盘（完成 v1 → v2 迁移；bak 恢复时顺带修复主文件）
        if (loaded) saveLocked()
    }

    /** 从指定文件加载到 [items]，文件不存在或整体损坏时返回 false。 */
    private fun loadFrom(source: File): Boolean {
        if (!source.exists()) return false
        val loaded = mutableListOf<Transaction>()
        try {
            val root = JSONObject(source.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("transactions") ?: return false
            for (i in 0 until arr.length()) {
                try {
                    loaded.add(Transaction.fromJson(arr.getJSONObject(i)))
                } catch (e: Exception) {
                    // 跳过损坏的单条记录，不影响其余数据
                }
            }
        } catch (e: Exception) {
            return false
        }
        items.addAll(loaded)
        return true
    }

    /** 原子写入：先写 .tmp，备份旧文件为 .bak，再 rename 覆盖正式文件。 */
    private fun saveLocked() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("transactions", arr)
        val content = root.toString(2)

        tmpFile.writeText(content, Charsets.UTF_8)
        if (file.exists()) {
            file.copyTo(bakFile, overwrite = true)
        }
        if (!tmpFile.renameTo(file)) {
            // 个别文件系统 rename 不能覆盖目标时退化为复制
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
        onChanged?.invoke()
    }

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "transactions.json"

        @Volatile
        private var instance: TransactionStore? = null

        fun getInstance(context: Context): TransactionStore =
            instance ?: synchronized(this) {
                instance ?: TransactionStore(context.applicationContext).also { instance = it }
            }
    }
}
