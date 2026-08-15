package com.moneyhistory.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * 一条周期账单。
 *
 * @param nextDue 下一期到期时间（毫秒），到期后逐期自动补记流水并推进
 */
data class RecurringExpense(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val category: String,
    val note: String = "",
    val cycle: Cycle,
    val nextDue: Long,
    val createdAt: Long = System.currentTimeMillis()
) {

    enum class Cycle(val json: String) {
        WEEKLY("weekly"),
        MONTHLY("monthly"),
        YEARLY("yearly");

        companion object {
            fun fromJson(value: String): Cycle =
                entries.firstOrNull { it.json == value } ?: MONTHLY
        }
    }

    /** 推进到下一期（保持当天时分不变）。 */
    fun advanced(): RecurringExpense {
        val cal = Calendar.getInstance().apply { timeInMillis = nextDue }
        when (cycle) {
            Cycle.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
            Cycle.MONTHLY -> cal.add(Calendar.MONTH, 1)
            Cycle.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return copy(nextDue = cal.timeInMillis)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amount", amountCents)
        put("category", category)
        put("note", note)
        put("cycle", cycle.json)
        put("nextDue", nextDue)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): RecurringExpense = RecurringExpense(
            id = obj.getString("id"),
            amountCents = obj.getLong("amount"),
            category = obj.getString("category"),
            note = obj.optString("note", ""),
            cycle = Cycle.fromJson(obj.getString("cycle")),
            nextDue = obj.getLong("nextDue"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

/**
 * 周期账单存储（filesDir/recurring.json）。
 * 结构：{"version":1,"recurring":[{...}]}
 */
class RecurringStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val items = mutableListOf<RecurringExpense>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun all(): List<RecurringExpense> = synchronized(lock) {
        items.sortedBy { it.nextDue }
    }

    fun add(r: RecurringExpense) = synchronized(lock) {
        items.add(r)
        saveLocked()
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val removed = items.removeAll { it.id == id }
        if (removed) saveLocked()
        removed
    }

    /**
     * 结算到期账单：对每条 nextDue <= now 的账单逐期补记支出流水，
     * 并把 nextDue 推进到未来。静默完成，返回补记的总笔数。
     * [defaultNote] 为账单无备注时写入流水的默认备注（本地化文案由调用方传入）。
     */
    fun settle(
        store: TransactionStore,
        now: Long = System.currentTimeMillis(),
        defaultNote: String = ""
    ): Int =
        synchronized(lock) {
            var count = 0
            for (i in items.indices) {
                var cur = items[i]
                while (cur.nextDue <= now) {
                    store.add(
                        Transaction(
                            type = Transaction.Type.EXPENSE,
                            amountCents = cur.amountCents,
                            category = cur.category,
                            note = cur.note.ifEmpty { defaultNote },
                            timestamp = cur.nextDue
                        )
                    )
                    count++
                    cur = cur.advanced()
                }
                items[i] = cur
            }
            if (count > 0) saveLocked()
            count
        }

    private fun loadLocked() {
        items.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("recurring") ?: return
            for (i in 0 until arr.length()) {
                try {
                    items.add(RecurringExpense.fromJson(arr.getJSONObject(i)))
                } catch (e: Exception) {
                    // 跳过损坏的单条记录
                }
            }
        } catch (e: Exception) {
            // 文件损坏时从空列表开始
        }
    }

    private fun saveLocked() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("recurring", arr)
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "recurring.json"

        @Volatile
        private var instance: RecurringStore? = null

        fun getInstance(context: Context): RecurringStore =
            instance ?: synchronized(this) {
                instance ?: RecurringStore(context.applicationContext).also { instance = it }
            }
    }
}
