package com.moneyhistory.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/** 心情档位（json 值 / emoji / 网格与图表配色；中文名走字符串资源）。 */
enum class Mood(val json: String, val emoji: String, val colorValue: Long) {
    GREAT("great", "😄", 0xFF4CAF50),
    GOOD("good", "🙂", 0xFF8BC34A),
    CALM("calm", "😐", 0xFF2AABEE),
    BAD("bad", "😔", 0xFF78909C),
    ANGRY("angry", "😠", 0xFFE53935);

    companion object {
        fun fromJson(value: String): Mood =
            entries.firstOrNull { it.json == value } ?: CALM
    }
}

/** 一天的心情记录。 */
data class MoodEntry(
    val mood: Mood,
    val note: String = ""
)

/** 连续记录心情的天数（以今天/昨天结尾）。 */
internal fun moodStreakOf(
    days: Set<String>,
    today: String = DateUtils.today()
): Int {
    var day = today
    if (day !in days) {
        day = DateUtils.addDays(day, -1)
        if (day !in days) return 0
    }
    var streak = 0
    while (day in days) {
        streak++
        day = DateUtils.addDays(day, -1)
    }
    return streak
}

/** 连续非生气天数（有记录且非生气；未记录会中断）。 */
internal fun consecutiveNonAngryDays(
    moods: Map<String, MoodEntry>,
    today: String = DateUtils.today()
): Int {
    fun ok(day: String) = moods[day]?.let { it.mood != Mood.ANGRY } == true
    var day = today
    if (!ok(day)) {
        day = DateUtils.addDays(day, -1)
        if (!ok(day)) return 0
    }
    var streak = 0
    while (ok(day)) {
        streak++
        day = DateUtils.addDays(day, -1)
    }
    return streak
}

/** 是否存在「佛系月」：某自然月记录 ≥10 天且 0 次生气。 */
internal fun hasCalmMonth(moods: Map<String, MoodEntry>): Boolean {
    val byMonth = moods.entries.groupBy { it.key.substring(0, 7) }
    return byMonth.values.any { entries ->
        entries.size >= 10 && entries.none { it.value.mood == Mood.ANGRY }
    }
}

/**
 * 心情存储（filesDir/mood.json，原子写入）。
 * 结构：{"version":1,"days":{"yyyy-MM-dd":{"mood":"great","note":"..."}}}
 */
class MoodStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
    private val lock = Any()
    private val days = mutableMapOf<String, MoodEntry>()

    init {
        synchronized(lock) { loadLocked() }
    }

    fun all(): Map<String, MoodEntry> = synchronized(lock) { days.toMap() }

    /** 记录/修改某天的心情（同一天可改）。 */
    fun set(day: String, mood: Mood, note: String) = synchronized(lock) {
        days[day] = MoodEntry(mood, note)
        saveLocked()
    }

    private fun loadLocked() {
        days.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val obj = root.optJSONObject("days") ?: return
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                try {
                    val entry = obj.getJSONObject(key)
                    days[key] = MoodEntry(
                        mood = Mood.fromJson(entry.getString("mood")),
                        note = entry.optString("note", "")
                    )
                } catch (e: Exception) {
                    // 跳过损坏的单条记录
                }
            }
        } catch (e: Exception) {
            // 文件损坏时从空开始
        }
    }

    private fun saveLocked() {
        val daysObj = JSONObject()
        days.forEach { (day, entry) ->
            daysObj.put(
                day,
                JSONObject().apply {
                    put("mood", entry.mood.json)
                    put("note", entry.note)
                }
            )
        }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("days", daysObj)
        tmpFile.writeText(root.toString(2), Charsets.UTF_8)
        if (!tmpFile.renameTo(file)) {
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "mood.json"

        @Volatile
        private var instance: MoodStore? = null

        fun getInstance(context: Context): MoodStore =
            instance ?: synchronized(this) {
                instance ?: MoodStore(context.applicationContext).also { instance = it }
            }
    }
}
