package com.yuanman

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一笔存入/取出记录。 */
data class GoalDeposit(
    val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isWithdraw: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amount", amountCents)
        put("timestamp", timestamp)
        put("withdraw", isWithdraw)
    }

    companion object {
        fun fromJson(obj: JSONObject): GoalDeposit = GoalDeposit(
            id = obj.getString("id"),
            amountCents = obj.getLong("amount"),
            timestamp = obj.getLong("timestamp"),
            isWithdraw = obj.optBoolean("withdraw", false)
        )
    }
}

/** 一个攒钱目标。 */
data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val targetCents: Long,
    val savedCents: Long = 0,
    val deadlineMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deposits: List<GoalDeposit> = emptyList(),
    /** 已庆祝过的里程碑（25/50/75/100），防重复庆祝。 */
    val celebratedMilestones: List<Int> = emptyList()
) {
    val progress: Float
        get() = if (targetCents > 0) savedCents.toFloat() / targetCents else 0f

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("emoji", emoji)
        put("target", targetCents)
        put("saved", savedCents)
        if (deadlineMillis != null) put("deadline", deadlineMillis)
        put("createdAt", createdAt)
        put("milestones", JSONArray().apply { celebratedMilestones.forEach { put(it) } })
        put("deposits", JSONArray().apply { deposits.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): Goal {
            val milestonesArr = obj.optJSONArray("milestones")
            val milestones = mutableListOf<Int>()
            if (milestonesArr != null) {
                for (i in 0 until milestonesArr.length()) {
                    milestones.add(milestonesArr.getInt(i))
                }
            }
            val depositsArr = obj.optJSONArray("deposits")
            val deposits = mutableListOf<GoalDeposit>()
            if (depositsArr != null) {
                for (i in 0 until depositsArr.length()) {
                    try {
                        deposits.add(GoalDeposit.fromJson(depositsArr.getJSONObject(i)))
                    } catch (e: Exception) {
                        // 跳过损坏的单条记录
                    }
                }
            }
            return Goal(
                id = obj.getString("id"),
                name = obj.getString("name"),
                emoji = obj.optString("emoji", "🎯"),
                targetCents = obj.getLong("target"),
                savedCents = obj.optLong("saved", 0),
                deadlineMillis = if (obj.has("deadline")) obj.getLong("deadline") else null,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                deposits = deposits,
                celebratedMilestones = milestones
            )
        }
    }
}

/** 新建目标时的 emoji 候选（12 个）。 */
val goalEmojiCandidates = listOf(
    "✈️", "📱", "💻", "🚗", "🏠", "🎁",
    "🐱", "🎓", "💍", "📷", "🎮", "🛋"
)

/** 里程碑阈值。 */
val goalMilestones = listOf(25, 50, 75, 100)

private const val DAY_MILLIS = 86_400_000L

/** 日均存入速度（分/天）：近 30 天存入总额 ÷ 天数（目标创建不足 30 天按实际天数）。 */
internal fun Goal.dailySavingRate(now: Long = System.currentTimeMillis()): Long {
    val days = ((now - createdAt) / DAY_MILLIS + 1).coerceIn(1, 30)
    val since = now - 30L * DAY_MILLIS
    val sum = deposits
        .filter { !it.isWithdraw && it.timestamp >= since }
        .sumOf { it.amountCents }
    return sum / days
}

/**
 * 攒钱目标存储（filesDir/goals.json，原子写入）。
 * 结构：{"version":1,"goals":[{...}]}
 */
class SavingsStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val items = mutableListOf<Goal>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun all(): List<Goal> = synchronized(lock) {
        items.sortedBy { it.createdAt }
    }

    fun add(goal: Goal) = synchronized(lock) {
        items.add(goal)
        saveLocked()
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val removed = items.removeAll { it.id == id }
        if (removed) saveLocked()
        removed
    }

    /** 存入/取出：直接改 savedCents 并追加 deposit 记录，返回更新后的目标。 */
    fun deposit(goalId: String, amountCents: Long, isWithdraw: Boolean): Goal? =
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == goalId }
            if (index < 0) return null
            val goal = items[index]
            val newSaved = if (isWithdraw) {
                (goal.savedCents - amountCents).coerceAtLeast(0)
            } else {
                goal.savedCents + amountCents
            }
            val updated = goal.copy(
                savedCents = newSaved,
                deposits = goal.deposits + GoalDeposit(
                    amountCents = amountCents,
                    isWithdraw = isWithdraw
                )
            )
            items[index] = updated
            saveLocked()
            updated
        }

    /** 记录已庆祝的里程碑（防重复）。 */
    fun markCelebrated(goalId: String, milestone: Int): Unit = synchronized(lock) {
        val index = items.indexOfFirst { it.id == goalId }
        if (index < 0) return@synchronized
        val goal = items[index]
        if (milestone !in goal.celebratedMilestones) {
            items[index] = goal.copy(
                celebratedMilestones = goal.celebratedMilestones + milestone
            )
            saveLocked()
        }
    }

    private fun loadLocked() {
        items.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("goals") ?: return
            for (i in 0 until arr.length()) {
                try {
                    items.add(Goal.fromJson(arr.getJSONObject(i)))
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
        root.put("goals", arr)
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "goals.json"

        @Volatile
        private var instance: SavingsStore? = null

        fun getInstance(context: Context): SavingsStore =
            instance ?: synchronized(this) {
                instance ?: SavingsStore(context.applicationContext).also { instance = it }
            }
    }
}
