package com.yuanman

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 一个习惯。
 *
 * @param type     build = 培养习惯（每日打卡）；quit = 戒掉习惯（自动累计坚持天数）
 * @param startDate 开始日期 yyyy-MM-dd
 * @param checkins 打卡日期列表（build 用）
 * @param resets   破戒日期列表（quit 用，最近一次后重新累计）
 */
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val type: Type,
    val startDate: String,
    val checkins: List<String> = emptyList(),
    val resets: List<String> = emptyList()
) {

    enum class Type(val json: String) {
        BUILD("build"),
        QUIT("quit");

        companion object {
            fun fromJson(value: String): Type =
                if (value == QUIT.json) QUIT else BUILD
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("emoji", emoji)
        put("type", type.json)
        put("startDate", startDate)
        put("checkins", JSONArray().apply { checkins.forEach { put(it) } })
        put("resets", JSONArray().apply { resets.forEach { put(it) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): Habit {
            fun parseDates(arr: JSONArray?): List<String> {
                val out = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val d = arr.optString(i, "")
                        if (d.isNotEmpty()) out.add(d)
                    }
                }
                return out
            }

            return Habit(
                id = obj.getString("id"),
                name = obj.getString("name"),
                emoji = obj.optString("emoji", "✅"),
                type = Type.fromJson(obj.getString("type")),
                startDate = obj.getString("startDate"),
                checkins = parseDates(obj.optJSONArray("checkins")),
                resets = parseDates(obj.optJSONArray("resets"))
            )
        }
    }
}

/** 习惯的 emoji 候选（12 个）。 */
val habitEmojiCandidates = listOf(
    "💪", "📚", "📖", "🌅", "🏃", "🧘",
    "🥗", "💤", "🥤", "🍺", "🚬", "🌙"
)

/** build 类：以今天/昨天结尾的连续打卡天数。 */
internal fun Habit.buildStreak(today: String = DateUtils.today()): Int {
    val set = checkins.toSet()
    var day = today
    if (day !in set) {
        day = DateUtils.addDays(day, -1)
        if (day !in set) return 0
    }
    var streak = 0
    while (day in set) {
        streak++
        day = DateUtils.addDays(day, -1)
    }
    return streak
}

/** quit 类：已坚持天数 = 今天 - max(startDate, lastReset)。 */
internal fun Habit.quitDays(today: String = DateUtils.today()): Int {
    val base = resets.maxOrNull() ?: startDate
    return DateUtils.daysBetween(base, today).coerceAtLeast(0)
}

internal fun Habit.checkedOn(day: String): Boolean = day in checkins

/**
 * 习惯存储（filesDir/habits.json，原子写入）。
 * 结构：{"version":1,"habits":[{...}]}
 */
class HabitsStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val items = mutableListOf<Habit>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun all(): List<Habit> = synchronized(lock) {
        items.sortedBy { it.startDate }
    }

    fun add(h: Habit) = synchronized(lock) {
        items.add(h)
        saveLocked()
    }

    fun remove(id: String): Boolean = synchronized(lock) {
        val removed = items.removeAll { it.id == id }
        if (removed) saveLocked()
        removed
    }

    /** build 类打卡/撤销当天打卡，返回操作后是否已打卡。 */
    fun toggleCheckin(id: String, day: String = DateUtils.today()): Boolean =
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return false
            val habit = items[index]
            val checked = day in habit.checkins
            items[index] = habit.copy(
                checkins = if (checked) {
                    habit.checkins - day
                } else {
                    habit.checkins + day
                }
            )
            saveLocked()
            !checked
        }

    /** quit 类记录破戒：追加 reset 日期，坚持天数清零重计。 */
    fun resetHabit(id: String, day: String = DateUtils.today()): Boolean =
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return false
            val habit = items[index]
            items[index] = habit.copy(resets = (habit.resets + day).distinct())
            saveLocked()
            true
        }

    private fun loadLocked() {
        items.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("habits") ?: return
            for (i in 0 until arr.length()) {
                try {
                    items.add(Habit.fromJson(arr.getJSONObject(i)))
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
        root.put("habits", arr)
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "habits.json"

        @Volatile
        private var instance: HabitsStore? = null

        fun getInstance(context: Context): HabitsStore =
            instance ?: synchronized(this) {
                instance ?: HabitsStore(context.applicationContext).also { instance = it }
            }
    }
}
